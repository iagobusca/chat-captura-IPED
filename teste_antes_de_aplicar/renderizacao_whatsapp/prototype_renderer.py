"""Isolated HTML-message viewer prototype; never imported by production code."""
from __future__ import annotations

import base64
import hashlib
import html
import json
import re
import shutil
import urllib.parse
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
DROP = {"script", "form", "iframe", "object", "embed", "link", "base", "input", "button", "select", "textarea", "svg", "math"}
ALLOWED = {"div", "span", "p", "br", "a", "img", "audio", "video", "source", "b", "strong", "i", "em", "small", "time", "hr"}
DATA_IMAGE = re.compile(r"(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\+xml));base64,([a-z0-9+/=\r\n]+)$")
RESOURCE_HASH = re.compile(r"(?i)(?:sha-256|hash):([0-9a-f]{64})")
CSS_URL = re.compile(r"(?is)url\(\s*(['\"]?)(.*?)\1\s*\)")
MAX_CSS_IMAGE = 8 * 1024 * 1024
MAX_CSS_IMAGES_TOTAL = 16 * 1024 * 1024
MAX_CSS_BYTES = 2 * 1024 * 1024
MAX_SVG_NODES = 4096
MAX_SVG_BYTES = 512 * 1024
STOP_MEDIA_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();"
SCRIPT_CSP_HASH = base64.b64encode(hashlib.sha256(STOP_MEDIA_SCRIPT.encode("utf-8")).digest()).decode("ascii")
CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-" + SCRIPT_CSP_HASH + "'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"


class Text:
    def __init__(self, value: str):
        self.value = value


class Node:
    def __init__(self, tag: str, attrs=()):
        self.tag = tag.lower()
        self.attrs = {str(k).lower(): (v or "") for k, v in attrs if k}
        self.children = []

    def text_content(self) -> str:
        return "".join(c.value if isinstance(c, Text) else c.text_content() for c in self.children)


class TreeParser(HTMLParser):
    def __init__(self, source: str):
        super().__init__(convert_charrefs=True)
        self.root = Node("#root")
        self.stack = [self.root]
        self.feed(source)
        self.close()

    def handle_starttag(self, tag, attrs):
        node = Node(tag, attrs)
        self.stack[-1].children.append(node)
        if tag.lower() not in VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.stack[-1].children.append(Node(tag, attrs))

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag.lower():
                self.stack = self.stack[:i]
                return

    def handle_data(self, data):
        self.stack[-1].children.append(Text(data))

    def handle_entityref(self, name):
        self.stack[-1].children.append(Text("&" + name + ";"))

    def handle_charref(self, name):
        self.stack[-1].children.append(Text("&#" + name + ";"))


def walk(node: Node):
    for child in node.children:
        if isinstance(child, Node):
            yield child
            yield from walk(child)


def find_all(node: Node, predicate):
    return [n for n in walk(node) if predicate(n)]


def class_tokens(node: Node):
    return set(node.attrs.get("class", "").split())


def serialize(node: Node) -> str:
    attrs = "".join(' ' + k + '="' + html.escape(v, quote=True) + '"' for k, v in sorted(node.attrs.items()))
    if node.tag in VOID:
        return "<" + node.tag + attrs + ">"
    body = "".join(html.escape(c.value, quote=False) if isinstance(c, Text) else serialize(c) for c in node.children)
    return "<" + node.tag + attrs + ">" + body + "</" + node.tag + ">"


def validate_svg(raw: bytes) -> None:
    if len(raw) > MAX_SVG_BYTES:
        raise ValueError("CSS SVG exceeds byte limit")
    if re.search(rb"<!\s*(?:DOCTYPE|ENTITY)", raw, re.I):
        raise ValueError("CSS SVG declarations are not allowed")
    root = ET.fromstring(raw)
    if root.tag.rsplit("}", 1)[-1].lower() != "svg":
        raise ValueError("CSS SVG root is not svg")
    nodes = list(root.iter())
    if len(nodes) > MAX_SVG_NODES:
        raise ValueError("CSS SVG exceeds node limit")
    for node in nodes:
        name = node.tag.rsplit("}", 1)[-1].lower()
        if name in {"script", "foreignobject", "iframe", "object", "embed"}:
            raise ValueError("active element in CSS SVG")
        for key, value in node.attrib.items():
            local = key.rsplit("}", 1)[-1].lower()
            if local.startswith("on"):
                raise ValueError("event attribute in CSS SVG")
            if local in {"href", "src"} and not value.startswith("#"):
                raise ValueError("external reference in CSS SVG")
            if re.search(r"(?i)url\s*\(\s*(?:https?:|file:|javascript:|//)", value):
                raise ValueError("external CSS URL in SVG")


def sanitize_css(css: str) -> str:
    if len(css.encode("utf-8")) > MAX_CSS_BYTES:
        raise ValueError("CSS exceeds byte limit")
    if re.search(r"(?i)@import|expression\s*\(", css):
        raise ValueError("active CSS construct is not allowed")
    total = 0
    for match in CSS_URL.finditer(css):
        value = match.group(2).strip().strip("\"'").strip()
        parsed = DATA_IMAGE.fullmatch(value)
        if not parsed:
            raise ValueError("CSS URL is not a permitted embedded image")
        mime = parsed.group(1).lower()
        try:
            data = base64.b64decode(re.sub(r"\s+", "", parsed.group(2)), validate=True)
        except Exception as exc:
            raise ValueError("invalid CSS image base64") from exc
        if not data or len(data) > MAX_CSS_IMAGE:
            raise ValueError("CSS image exceeds byte limit")
        total += len(data)
        if total > MAX_CSS_IMAGES_TOTAL:
            raise ValueError("CSS images exceed aggregate byte limit")
        if mime == "image/svg+xml":
            validate_svg(data)
    return re.sub(r"#topbar\b", "#capture-topbar", re.sub(r"#conversation\b", "#capture-conversation", css))


def decode_data_image(uri: str) -> tuple[str, bytes]:
    match = DATA_IMAGE.fullmatch(uri.strip())
    if not match:
        raise ValueError("message data URI is not an allowed image")
    mime = match.group(1).lower()
    if mime == "image/jpg":
        mime = "image/jpeg"
    data = base64.b64decode(re.sub(r"\s+", "", match.group(2)), validate=True)
    if not data or len(data) > MAX_CSS_IMAGE:
        raise ValueError("message image exceeds byte limit")
    if mime == "image/svg+xml":
        validate_svg(data)
    return mime, data


def safe_label(value: str, fallback: str) -> str:
    value = re.sub(r"[\x00-\x1f\x7f]", "", value or "")
    value = re.sub(r"[\\/:*?\"<>|]", "_", value).strip().strip(".")
    return value[:180] or fallback


def sanitize_node(node: Node, attachments_by_hash: dict[str, dict], linked: dict[str, str], allow_unlisted_media: bool = False) -> str:
    if node.tag in DROP:
        return ""
    if node.tag not in ALLOWED:
        return "".join(sanitize_node(c, attachments_by_hash, linked, allow_unlisted_media) if isinstance(c, Node) else html.escape(c.value, quote=False) for c in node.children)

    attrs = {}
    for key, value in node.attrs.items():
        if key.startswith("on") or key in {"style", "srcset", "srcdoc", "action", "formaction", "poster", "href"}:
            continue
        elif key == "id":
            if value == "capture-topbar":
                attrs["id"] = value
        if key == "class" and re.fullmatch(r"[A-Za-z0-9 _-]{0,256}", value):
            attrs[key] = value
        elif key in {"alt", "title"}:
            attrs[key] = value[:512]
        elif key.startswith("aria-") and re.fullmatch(r"[A-Za-z0-9 _-]{0,128}", value):
            attrs[key] = value

    if node.tag == "a":
        sha_match = RESOURCE_HASH.search(node.attrs.get("onclick", ""))
        if sha_match:
            digest = sha_match.group(1).lower()
            if digest not in attachments_by_hash:
                if not allow_unlisted_media:
                    raise ValueError("selected message attachment is missing from its manifest")
            else:
                linked[digest] = " ".join(node.text_content().split())[:180]
                attrs["href"] = "#preview-" + digest
                attrs["class"] = (attrs.get("class", "") + " capture-media-link").strip()
        else:
            original_href = node.attrs.get("href", "")
            if original_href and not original_href.startswith("#"):
                # Source links without a verifiable attachment relation are not navigable in a capture.
                pass

    if node.tag in {"img", "audio", "video", "source"}:
        src = node.attrs.get("src", "")
        if src.startswith("data:image/"):
            mime, data = decode_data_image(src)
            digest = hashlib.sha256(data).hexdigest()
            record = attachments_by_hash.get(digest)
            if record is None:
                if allow_unlisted_media:
                    return ""
                raise ValueError("embedded message image is not represented in messages.json")
            if int(record.get("size", -1)) != len(data) or record.get("mime", "").lower() not in {mime, "image/jpg" if mime == "image/jpeg" else mime}:
                raise ValueError("embedded message image metadata diverges")
            attrs["src"] = record["path"]
        elif src:
            raise ValueError("message resource must be resolved to a capture-local attachment")
        if node.tag in {"audio", "video"}:
            attrs["controls"] = ""
            attrs["preload"] = "none"

    serialized_attrs = "".join(' ' + k + '="' + html.escape(v, quote=True) + '"' for k, v in sorted(attrs.items()))
    if node.tag in VOID:
        return "<" + node.tag + serialized_attrs + ">"
    body = "".join(sanitize_node(c, attachments_by_hash, linked, allow_unlisted_media) if isinstance(c, Node) else html.escape(c.value, quote=False) for c in node.children)
    return "<" + node.tag + serialized_attrs + ">" + body + "</" + node.tag + ">"


def modal_for_attachment(record: dict, label: str) -> str:
    digest = record["sha256"].lower()
    path = record["path"]
    mime = record["mime"].lower()
    safe_name = safe_label(label, Path(path).name)
    title = "Imagem" if mime.startswith("image/") else "PDF" if mime == "application/pdf" else "Áudio" if mime.startswith("audio/") else "Vídeo" if mime.startswith("video/") else "Arquivo"
    safe_path = html.escape(path, quote=True)
    safe_title = html.escape(title, quote=True)
    safe_filename = html.escape(safe_name, quote=True)
    content = ""
    if mime.startswith("image/"):
        content = '<img class="capture-preview-image" src="' + safe_path + '" alt="' + safe_filename + '">'
    elif mime == "application/pdf":
        content = '<iframe class="capture-preview-pdf" title="Pré-visualização do PDF" src="' + safe_path + '" sandbox=""></iframe><p class="capture-preview-fallback">Se o PDF não aparecer neste painel, use Abrir em nova aba ou Baixar.</p>'
    elif mime.startswith("audio/"):
        content = '<audio controls preload="none" src="' + safe_path + '"></audio>'
    elif mime.startswith("video/"):
        content = '<video controls preload="none" src="' + safe_path + '"></video>'
    else:
        content = '<p class="capture-file-details">' + safe_filename + ' · ' + html.escape(mime) + ' · ' + str(int(record["size"])) + ' bytes · SHA-256 ' + html.escape(digest) + '</p>'
    buttons = '<nav class="capture-preview-actions"><a class="capture-button" href="' + safe_path + '" download="' + safe_filename + '">Baixar</a><a class="capture-button" href="' + safe_path + '" target="_blank" rel="noopener noreferrer">Abrir em nova aba</a><a class="capture-button capture-close" href="#">Fechar</a></nav>'
    return '<section class="capture-modal" id="preview-' + digest + '" role="dialog" aria-modal="true" aria-labelledby="title-' + digest + '"><div class="capture-modal-card"><header class="capture-modal-header"><h2 id="title-' + digest + '">' + safe_title + '</h2><a class="capture-close-icon" href="#" aria-label="Fechar">×</a></header><div class="capture-modal-body">' + content + '</div>' + buttons + '</div></section>'


def validate_page(page_path: Path, attachment_records: list[dict], attachment_root: Path) -> dict:
    source = page_path.read_text(encoding="utf-8")
    tree = TreeParser(source)
    nodes = list(walk(tree.root))
    ids = [n.attrs["id"] for n in nodes if n.attrs.get("id")]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate HTML IDs")
    ids_set = set(ids)
    records = {}
    for record in attachment_records:
        rel = record.get("path", "")
        pure = Path(rel)
        if not rel or "\\" in rel or pure.is_absolute() or any(part in {"", ".", ".."} for part in rel.split("/")):
            raise ValueError("manifest attachment path is not normalized and relative")
        if rel in records or not re.fullmatch(r"(?i)[0-9a-f]{64}", record.get("sha256", "")):
            raise ValueError("manifest attachment identity is duplicated or invalid")
        records[rel] = record
    resources = set()
    metas = [n for n in nodes if n.tag == "meta"]
    charset_ok = sum(1 for n in metas if n.attrs == {"charset": "utf-8"}) == 1
    csp_ok = sum(1 for n in metas if n.attrs.get("http-equiv", "").lower() == "content-security-policy" and n.attrs.get("content") == CSP) == 1
    if not charset_ok or not csp_ok or len(metas) != 2:
        raise ValueError("HTML meta elements do not match the strict charset/CSP allowlist")
    scripts = [n for n in nodes if n.tag == "script"]
    if len(scripts) != 1 or scripts[0].attrs or scripts[0].text_content() != STOP_MEDIA_SCRIPT:
        raise ValueError("HTML script is not the exact trusted media-stop script")
    if any(n.tag in {"object", "embed", "form", "base", "link", "svg", "math"} for n in nodes):
        raise ValueError("HTML contains an active or unapproved element")
    for node in nodes:
        if any(k.startswith("on") or k in {"style", "srcdoc", "formaction", "action"} for k in node.attrs):
            raise ValueError("HTML contains an active/unfiltered attribute")
        if node.tag == "iframe":
            if set(node.attrs) - {"class", "title", "src", "sandbox"} or node.attrs.get("sandbox") != "" or not node.attrs.get("src", "").lower().endswith(".pdf") or records.get(node.attrs.get("src", ""), {}).get("mime") != "application/pdf":
                raise ValueError("iframe is not an isolated local PDF preview")
        for attr in ("src", "href", "poster", "action", "formaction"):
            value = node.attrs.get(attr)
            if value is None:
                continue
            if attr == "href" and value.startswith("#"):
                target = value[1:]
                if target and target not in ids_set:
                    raise ValueError("HTML fragment link has no unique target")
                continue
            if value not in records:
                raise ValueError("HTML contains a remote, absolute, or unmanifested resource")
            resources.add(value)
            if node.tag == "img" and attr == "src" and not records[value].get("mime", "").lower().startswith("image/"):
                raise ValueError("image source MIME differs from manifest")
            if node.tag in {"audio", "video", "source"} and attr == "src":
                prefix = "audio/" if node.tag == "audio" else "video/" if node.tag == "video" else ("audio/" if records[value].get("mime", "").lower().startswith("audio/") else "video/")
                if not records[value].get("mime", "").lower().startswith(prefix):
                    raise ValueError("media source MIME differs from element or manifest")
            if node.tag == "iframe" and attr == "src" and records[value].get("mime") != "application/pdf":
                raise ValueError("PDF frame source MIME differs from manifest")
        if node.attrs.get("target") is not None:
            if node.attrs["target"] != "_blank" or not {"noopener", "noreferrer"}.issubset(set(node.attrs.get("rel", "").lower().split())):
                raise ValueError("new-tab link is missing safe target attributes")
    if resources != set(records):
        raise ValueError("HTML resource set differs from message attachment manifest")
    styles = [n.text_content() for n in nodes if n.tag == "style"]
    if len(styles) < 2:
        raise ValueError("HTML capture is missing source theme or viewer styles")
    for css in styles:
        sanitize_css(css)
    for path, record in records.items():
        item = attachment_root / Path(path)
        if any(part.is_symlink() for part in [item, *item.parents] if part != attachment_root.parent):
            raise ValueError("manifest attachment path includes a symbolic link")
        if not item.is_file() or item.stat().st_size != int(record["size"]):
            raise ValueError("manifest attachment is absent or has a size mismatch")
        digest = hashlib.sha256(item.read_bytes()).hexdigest()
        if digest.lower() != record["sha256"].lower():
            raise ValueError("manifest attachment hash mismatch")
    return {"metaAllowed": True, "resourceCount": len(resources), "messageKeyCount": len([n for n in nodes if "data-capture-message-key" in n.attrs]), "scriptCount": 1, "scriptHash": SCRIPT_CSP_HASH}


MODAL_CSS = """
.capture-modal{display:none;position:fixed;inset:0;z-index:9999;align-items:center;justify-content:center;padding:18px;background:rgba(0,0,0,.72)}
.capture-modal:target{display:flex}
.capture-modal-card{box-sizing:border-box;width:min(900px,96vw);max-height:94vh;overflow:auto;background:#f7f4ef;border-radius:10px;box-shadow:0 12px 38px rgba(0,0,0,.45);font:14px Arial,sans-serif;color:#202c33}
.capture-modal-header{display:flex;align-items:center;justify-content:space-between;padding:10px 16px;background:#075e54;color:#fff}
.capture-modal-header h2{margin:0;font-size:16px}.capture-close-icon{font-size:26px;color:#fff;text-decoration:none;padding:0 6px}
.capture-modal-body{padding:14px;max-height:75vh;text-align:center}.capture-preview-image{display:block;max-width:100%;max-height:68vh;margin:auto;object-fit:contain}
.capture-preview-pdf{display:block;width:100%;height:68vh;border:0;background:#fff}.capture-modal-body audio{width:min(100%,550px)}.capture-modal-body video{max-width:100%;max-height:68vh}
.capture-preview-fallback,.capture-file-details{overflow-wrap:anywhere;color:#53636c}.capture-preview-actions{display:flex;flex-wrap:wrap;gap:8px;padding:10px 14px 14px}
.capture-button{display:inline-block;padding:8px 12px;border-radius:5px;background:#128c7e;color:#fff;text-decoration:none}.capture-button:hover{background:#075e54}.capture-close{background:#52636a}
.capture-media-link{cursor:pointer}.capture-file-card{margin:6px 0;padding:8px 10px;border-radius:6px;background:#f4f0e8;color:#263238;overflow-wrap:anywhere}
""".strip()


def build_capture(source_path: Path, capture_path: Path, output_path: Path, start_id: str, end_id: str) -> dict:
    source = source_path.read_text(encoding="utf-8")
    parsed = TreeParser(source)
    raw_style = [n.text_content() for n in walk(parsed.root) if n.tag == "style"]
    if len(raw_style) != 1:
        raise ValueError("IPED 4.3.1 source must have one inline stylesheet for this prototype")
    css = sanitize_css(raw_style[0])
    conversation = next((n for n in walk(parsed.root) if n.attrs.get("id") == "conversation"), None)
    topbar = next((n for n in walk(parsed.root) if n.attrs.get("id") == "topbar"), None)
    if conversation is None or topbar is None:
        raise ValueError("IPED chat shell is incomplete")
    message_nodes = find_all(conversation, lambda n: n.tag == "div" and "linha" in class_tokens(n) and n.attrs.get("id", "").isdigit())
    ids = [n.attrs["id"] for n in message_nodes]
    if len(ids) != len(set(ids)) or ids.count(start_id) != 1 or ids.count(end_id) != 1:
        raise ValueError("message IDs are missing or ambiguous")
    start_index, end_index = ids.index(start_id), ids.index(end_id)
    if start_index > end_index:
        raise ValueError("capture anchors are reversed")
    records_doc = json.loads((capture_path / "messages.json").read_text(encoding="utf-8"))
    records = {str(row["domId"]): row for row in records_doc["messages"]}
    expected_ids = ids[start_index:end_index + 1]
    if not set(expected_ids).issubset(records):
        raise ValueError("selected HTML range differs from the capture manifest")
    attachments_by_hash = {}
    selected_records = [records[mid] for mid in expected_ids]
    for message in selected_records:
        for attachment in message.get("attachments", []):
            digest = attachment["sha256"].lower()
            item = dict(attachment)
            item["sha256"] = digest
            attachments_by_hash[digest] = item

    output_path.mkdir(parents=True, exist_ok=True)
    for item in attachments_by_hash.values():
        source_file = capture_path / Path(item["path"])
        if not source_file.is_file() or source_file.stat().st_size != int(item["size"]):
            raise ValueError("capture fixture attachment is missing or has a size mismatch")
        destination = output_path / Path(item["path"])
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_file, destination)

    linked = {}
    fragments = []
    for message_index, dom_id in enumerate(expected_ids):
        node = next(n for n in message_nodes if n.attrs.get("id") == dom_id)
        fragment = sanitize_node(node, attachments_by_hash, linked)
        key = hashlib.sha256((dom_id + "|" + str(message_index)).encode("utf-8")).hexdigest()
        fragment = re.sub(r"^<div(?=[ >])", '<div data-capture-message-key="' + key + '"', fragment, count=1)
        fragments.append(fragment)

    # Include only the directly preceding date separator as visual context.
    all_children = [x for x in conversation.children if isinstance(x, Node)]
    first_node = next(n for n in message_nodes if n.attrs.get("id") == start_id)
    try:
        first_position = all_children.index(first_node)
    except ValueError:
        first_position = -1
    if first_position > 0:
        before = all_children[first_position - 1]
        if "linha" in class_tokens(before) and not before.attrs.get("id", "").isdigit() and re.search(r"\b\d{4}-\d{2}-\d{2}\b", before.text_content()):
            date_fragment = sanitize_node(before, attachments_by_hash, linked)
            fragments.insert(0, date_fragment)

    # Turn only source links with a verified attachment hash into local previews.
    preview_items = []
    for digest, label in linked.items():
        record = attachments_by_hash.get(digest)
        if record is None:
            raise ValueError("preview link is absent from the selected message manifest")
        preview_items.append(modal_for_attachment(record, label))
    root = json.loads((capture_path / "manifest.json").read_text(encoding="utf-8"))
    title = html.escape(str(root.get("sourceMetadata", {}).get("name", "Conversa WhatsApp"))[:180])
    header = sanitize_node(topbar, attachments_by_hash, {}, allow_unlisted_media=True)
    # Give only the copied shell a capture-local selector used by the preserved theme.
    header = re.sub(r'^<div(?=[ >])', '<div id="capture-topbar"', header, count=1)
    page = ('<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
            '<meta http-equiv="Content-Security-Policy" content="' + html.escape(CSP, quote=True) + '">'
            '<title>WhatsApp Chat - ' + title + '</title><style>' + css + '</style><style>' + MODAL_CSS + '</style></head><body>'
            + header + '<div id="capture-conversation">' + "".join(fragments) + '</div>' + "".join(preview_items) + '<script>' + STOP_MEDIA_SCRIPT + '</script></body></html>')
    html_path = output_path / "whatsapp.html"
    html_path.write_text(page, encoding="utf-8", newline="")
    return {"html": html_path, "selectedIds": expected_ids, "renderedKeys": len(re.findall(r'data-capture-message-key="[0-9a-f]{64}"', page)),
            "attachmentPaths": sorted(item["path"] for item in attachments_by_hash.values()), "linkedPreviewCount": len(preview_items),
            "pageBytes": html_path.stat().st_size, "cssBytes": len(css.encode("utf-8"))}


def build_media_fixture(output_path: Path) -> Path:
    """Controlled attachment fixture used to test viewer interactions without real chat data."""
    output_path.mkdir(parents=True, exist_ok=True)
    assets = output_path / "attachments"
    assets.mkdir(exist_ok=True)
    # Valid 1x1 PNG and WAV; PDF/Office/file bytes are deliberately minimal viewer fixtures.
    files = {
        "image.png": base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/WioAAAAASUVORK5CYII="),
        "sample.pdf": b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n%%EOF",
        "sample.docx": b"PK\x03\x04fixture-docx",
        "sample.bin": b"fixture binary content",
        "sample.wav": b"RIFF\x26\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00\x40\x1f\x00\x00\x80\x3e\x00\x00\x02\x00\x10\x00data\x02\x00\x00\x00\x00\x00",
        "sample.webm": b"\x1a\x45\xdf\xa3fixture-webm",
    }
    mime = {"image.png":"image/png", "sample.pdf":"application/pdf", "sample.docx":"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "sample.bin":"application/octet-stream", "sample.wav":"audio/wav", "sample.webm":"video/webm"}
    attachments = []
    for name, data in files.items():
        (assets / name).write_bytes(data)
        attachments.append({"name":name,"path":"attachments/" + name,"mime":mime[name],"size":len(data),"sha256":hashlib.sha256(data).hexdigest()})
    media = []
    for item in attachments:
        digest = item["sha256"]
        safe_path = html.escape(item["path"], quote=True)
        safe_name = html.escape(item["name"], quote=True)
        media.append('<article class="linha outgoing"><div class="bbr"><div class="outgoing to">'
                     '<a class="capture-media-link" href="#preview-' + digest + '">' + safe_name + '</a></div></div></article>')
    pages = []
    for item in attachments:
        # modal_for_attachment uses only path/mime/size/hash fields from this record.
        pages.append(modal_for_attachment(item, item["name"]))
    fixture = ('<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
               '<meta http-equiv="Content-Security-Policy" content="' + html.escape(CSP, quote=True) + '">'
               '<title>Media viewer fixtures</title>'
               '<style>.linha{margin:8px}.outgoing{background:#dff8d8;padding:8px;border-radius:8px}</style>'
               '<style>' + MODAL_CSS + '</style></head><body data-capture-test-fixture="true"><main>' + "".join(media) + '</main>' + "".join(pages) + '<script>' + STOP_MEDIA_SCRIPT + '</script></body></html>')
    out = output_path / "media-fixture.html"
    out.write_text(fixture, encoding="utf-8", newline="")
    return out
