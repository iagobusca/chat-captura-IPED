from html.parser import HTMLParser
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_env_paths import find_sample_html


class LinhaParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.message_ids = []
        self.date_markers = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        classes = values.get("class", "").split()
        if tag.lower() == "div" and "linha" in classes:
            element_id = values.get("id", "")
            if re.fullmatch(r"[0-9]+", element_id):
                self.message_ids.append(element_id)
            if values.get("data-iped-capture-date") is not None and element_id:
                self.date_markers.append(element_id)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


repo = Path(__file__).resolve().parents[1]
source = (repo / "src/iped/viewers/HtmlLinkViewer.java").read_text(encoding="utf-8")
panel = (repo / "src/iped/viewers/ChatCapturePanel.java").read_text(encoding="utf-8")
resolver = (repo / "src/iped/viewers/CaptureBoundaryResolver.java").read_text(encoding="utf-8")
localization_script = (repo / "instalar_captura_chat_zap/install-localization.ps1").read_text(encoding="utf-8")

require("if (!isDate) menu.getItems().add(end);" in source,
        "date context menu must omit the end-marker action")
require("CaptureBoundaryResolver.Role.END" in source and "end_date_not_supported" in resolver,
        "date end must be rejected by the shared boundary resolver")
require("viewer.startChatCaptureFromUi(" in panel,
        "start capture UI click must route off-EDT through startChatCaptureFromUi")
require("if (!validateCaptureStart(outputFolder)) return;" in source,
        "invalid boundaries must stop before verifier installation")
require(source.index("if (!validateCaptureStart(outputFolder)) return;")
        < source.index("VerifierIntegrity.installVerifierFiles(outputPath);"),
        "boundary validation must run before verifier installation")
require(".__capture_staging_" in source and "promoteCaptureDirectory(captureDir, finalCaptureDir)" in source,
        "normal capture must be staged and promoted only after completion")
require("ZipArchiveMode]::Update" not in localization_script and "OpenRead($Jar)" in localization_script,
        "language installation must not rewrite the compiled JAR in place")
internal = source.split("private CaptureRunResult runChatCaptureInternal", 1)[1].split(
    "private void promoteCaptureDirectory", 1)[0]
require("safeRebuildCaptureRootIndex" not in internal,
        "capture worker must not rewrite output indexes before commit")
require("start_marker_ambiguous" in source and "end_marker_ambiguous" in source
        and "marker_order_invalid" in source,
        "HTML worker must report distinct missing, duplicate, and order failures")
for bundle_name in ("iped-viewer-messages.properties", "iped-viewer-messages_pt_BR.properties"):
    bundle = (repo / "instalar_captura_chat_zap" / bundle_name).read_text(encoding="utf-8")
    for key in ("EndDateNotAllowed", "StartDateNoMessage", "MarkerNotMessage", "MarkerAmbiguous",
                "MarkerMissing", "InvalidRangeOrder", "RangeReady"):
        require(f"ChatCapturePanel.{key}=" in bundle, f"{bundle_name} lacks localized key {key}")

fixture = find_sample_html()
if not fixture.is_file():
    raise FileNotFoundError(f"IPED 4.3.1 reference HTML not found: {fixture}")
parser = LinhaParser()
parser.feed(fixture.read_text(encoding="utf-8", errors="replace"))
require("243188" in parser.message_ids and "243189" in parser.message_ids,
        "reference HTML must expose the expected numeric div.linha message IDs")
require(len(parser.message_ids) == len(set(parser.message_ids)),
        "reference HTML numeric message IDs must be unique")
print(f"MarkerBoundaryContractTest: PASS ({len(parser.message_ids)} unique IPED 4.3.1 messages)")
