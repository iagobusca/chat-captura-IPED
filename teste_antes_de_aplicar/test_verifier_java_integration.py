import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys as _sys
if str(ROOT / "teste_antes_de_aplicar") not in _sys.path:
    _sys.path.insert(0, str(ROOT / "teste_antes_de_aplicar"))
from test_env_paths import find_jdk_tool
JAVA = find_jdk_tool("javac.exe", (8,), ("JAVA8_HOME",)).with_name("java.exe")
JAVA11 = find_jdk_tool("javac.exe", (26, 25, 24, 23, 22, 21, 17, 11), ("JAVA26_HOME", "JAVA21_HOME", "JAVA11_HOME")).with_name("java.exe")
CLASSES = ROOT / "tmp" / "verifier-html-v1"
PLUGIN_TEST_CLASSES = ROOT / "tmp" / "plugin-integrity-tests"
VERIFIER_JAR = ROOT / "verificador-integridade" / "integridade-verifier.jar"
PLUGIN_JAR = ROOT / "instalar_captura_chat_zap" / "iped-viewers-impl-4.3.1.jar"
JSON_JAR = ROOT / "instalar_captura_chat_zap" / "dependencies" / "json-20260719.jar"

def sha(text): return hashlib.sha256(text.encode("utf-8")).hexdigest()
def field(value):
    value = "" if value is None else str(value)
    return f"{len(value)}:{value}\n"
def message_hash(record):
    fields = [record["domId"], record["order"], record["timestamp"], record["sender"],
              record["direction"], record["type"], record["duplicateOrdinal"], record["text"], record["classes"]]
    text = "html-message-v1-message\n" + "".join(field(x) for x in fields)
    for a in record["attachments"]:
        text += "".join(field(a[x]) for x in ("path", "mime", "size", "sha256"))
    return sha(text)
def root_hash(records, html_sha=None, annotations_sha="", reconciliation_sha=""):
    if html_sha is None:
        html_sha = HTML_SHA
    canonical = "html-message-v1-root\n" + field(html_sha) + field(annotations_sha) + field(reconciliation_sha)
    canonical += field(sha("conversation")) + field("0" * 64) + field("source.html") + field("1") + field("text/html") + field("") + field("false")
    canonical += field("4.3.1") + field("test") + field("101") + field("101")
    return sha(canonical + "".join(field(r["key"]) + field(r["messageHash"]) for r in records))

FIXTURE_CONVERSATION = sha("conversation")
FIXTURE_KEY = sha(FIXTURE_CONVERSATION + "\n101\n10:00\nAna\nincoming\nimage\n0")
DEFAULT_ATTACHMENT = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082")
DEFAULT_ATTACHMENT_SHA = hashlib.sha256(DEFAULT_ATTACHMENT).hexdigest()
HTML = '<!doctype html><div class="linha" data-capture-message-key="' + FIXTURE_KEY + '"><img src="attachments/' + DEFAULT_ATTACHMENT_SHA + '.png"></div>'
HTML_SHA = hashlib.sha256(HTML.encode("utf-8")).hexdigest()
STOP_MEDIA_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();"
LEGACY_STOP_MEDIA_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}window.addEventListener('hashchange',function(){if(location.hash.indexOf('#preview-')!==0)stop();});document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-close'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-close'))stop();});})();"
MODERN_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-Pq7vIvwsQpte7OdNyOYYE9cKIEPmIFR7E/G7kB1JaJI='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"
LEGACY_MODERN_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-sKvM90tshBmGWhuFsz3phQvtsDNpZr1gyyGXQe4mJDE='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"

class JavaVerifierIntegration(unittest.TestCase):
    def create_capture(self, root, attachment=None, mime="image/png", extension="png", message_type="image", modern=False, legacy_media_script=False):
        cap = Path(root) / "capture"
        attachment = DEFAULT_ATTACHMENT if attachment is None else attachment
        ah = hashlib.sha256(attachment).hexdigest()
        (cap / "attachments").mkdir(parents=True)
        (cap / "attachments" / f"{ah}.{extension}").write_bytes(attachment)
        key = sha(FIXTURE_CONVERSATION + f"\n101\n10:00\nAna\nincoming\n{message_type}\n0")
        if modern:
            page_csp = LEGACY_MODERN_CSP if legacy_media_script else MODERN_CSP
            page_script = LEGACY_STOP_MEDIA_SCRIPT if legacy_media_script else STOP_MEDIA_SCRIPT
            rel = "attachments/" + ah + "." + extension
            modal_content = '<img src="' + rel + '">' if mime.startswith("image/") else ('<iframe src="' + rel + '" sandbox=""></iframe>' if mime == "application/pdf" else ('<audio controls preload="none" src="' + rel + '"></audio>' if mime.startswith("audio/") else ('<video controls preload="none" src="' + rel + '"></video>' if mime.startswith("video/") else '<p>Arquivo</p>')))
            html = ('<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="' + page_csp + '">'
                    '<title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style>'
                    '<style>.capture-modal{display:none}.capture-modal:target{display:flex}</style></head><body>'
                    '<div id="capture-topbar">Conversa</div><div id="capture-conversation"><div class="linha" data-capture-message-key="' + key + '"><a href="#preview-' + ah + '">Arquivo</a></div></div>'
                    '<section class="capture-modal" id="preview-' + ah + '">' + modal_content + '<a href="' + rel + '" download="imagem.png">Baixar</a>'
                    '<a href="' + rel + '" target="_blank" rel="noopener noreferrer">Abrir</a><a href="#" class="capture-close">Fechar</a></section>'
                    '<script>' + page_script + '</script></body></html>')
        else:
            html = '<!doctype html><div class="linha" data-capture-message-key="' + key + '">'
            if mime.startswith("image/"):
                html += '<img src="attachments/' + ah + '.' + extension + '">'
            else:
                html += '<a href="attachments/' + ah + '.' + extension + '" download>Baixar anexo</a>'
            html += '</div>'
        html_sha = hashlib.sha256(html.encode("utf-8")).hexdigest()
        record = {"domId":"101", "order":0, "timestamp":"10:00", "sender":"Ana", "direction":"incoming",
                  "type":message_type, "duplicateOrdinal":0, "text":"olá", "classes":"linha incoming",
                  "attachments":[{"path":"attachments/" + ah + '.' + extension, "mime":mime, "size":len(attachment), "sha256":ah}]}
        conversation = FIXTURE_CONVERSATION
        record["key"] = key
        record["messageHash"] = message_hash(record)
        manifest = {"schemaVersion":1, "captureFormat":"html-message-v1", "pluginVersion":"test", "ipedVersion":"4.3.1",
                    "status":"completed", "sourceFingerprint":"0" * 64, "conversationKey":conversation,
                    "sourceMetadata":{"name":"source.html","size":1,"type":"text/html","itemHash":"","deleted":False},
                    "anchors":{"start":"101","end":"101"}, "messageCount":1, "state":"complete",
                    "messageHashes":[{"domId":record["domId"],"key":record["key"],"messageHash":record["messageHash"]}],
                    "messagesFile":"messages.json", "htmlSha256":html_sha, "annotationsSha256":"", "reconciliationSha256":"", "captureRootHash":root_hash([record], html_sha=html_sha)}
        if modern:
            manifest["presentationVersion"] = 2
        (cap / "messages.json").write_text(json.dumps({"messages":[record]}), encoding="utf-8")
        (cap / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        (cap / "whatsapp.html").write_text(html, encoding="utf-8")
        return cap

    def verify(self, root):
        self.assertTrue(CLASSES.is_dir(), "compile the verifier classes before this integration test")
        return subprocess.run([str(JAVA), "-cp", str(CLASSES) + ";" + str(VERIFIER_JAR), "IntegrityVerifier", "--verify-only", str(root)], text=True, capture_output=True)

    def compare_public_manifest(self, cap, reference):
        path = cap.parent / "public-reference.json"
        path.write_text(json.dumps(reference), encoding="utf-8")
        return subprocess.run([str(JAVA), "-cp", str(CLASSES) + ";" + str(VERIFIER_JAR), "PublicManifestComparisonHarness",
                               str(cap / "manifest.json"), str(path)], text=True, capture_output=True)

    def test_valid_html_message_capture(self):
        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root)
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_migration_metadata_and_artifact_are_accepted_and_hash_chained(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            migration = {
                "schemaVersion": 1,
                "action": "legacy_screenshot_capture_migrated_to_html_message_v1",
                "sourceFormat": "legacy-screenshot",
                "previousManifestSha256": "a" * 64,
                "anchorsRecoveredFrom": "manifest-and-whatsapp-coordinates",
                "legacyAnnotationsPreserved": False,
            }
            migration_path = cap / "migration.json"
            migration_path.write_text(json.dumps(migration), encoding="utf-8")
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest.update({"migration": migration, "migrationFile": "migration.json",
                             "migrationSha256": hashlib.sha256(migration_path.read_bytes()).hexdigest()})
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            harness_cp = ";".join(map(str, (PLUGIN_TEST_CLASSES, PLUGIN_JAR, JSON_JAR, VERIFIER_JAR)))
            generated = subprocess.run([str(JAVA11), "-cp", harness_cp,
                                        "iped.viewers.MigrationIntegrityHistoryHarness", str(cap)],
                                       text=True, capture_output=True)
            self.assertEqual(generated.returncode, 0, generated.stdout + generated.stderr)
            valid = self.verify(root)
            self.assertEqual(valid.returncode, 0, valid.stdout + valid.stderr)
            self.assertIn("consistente com registro local", valid.stdout + valid.stderr)

            tampered_migration = {**migration, "previousManifestSha256": "b" * 64}
            migration_path.write_text(json.dumps(tampered_migration), encoding="utf-8")
            manifest["migration"] = tampered_migration
            manifest["migrationSha256"] = hashlib.sha256(migration_path.read_bytes()).hexdigest()
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            tampered = self.verify(root)
            self.assertIn("migration.json", tampered.stdout + tampered.stderr)
            self.assertNotIn("consistente com registro local", tampered.stdout + tampered.stderr)

    def test_migrated_capture_without_local_integrity_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            migration = {
                "schemaVersion": 1,
                "action": "legacy_screenshot_capture_migrated_to_html_message_v1",
                "sourceFormat": "legacy-screenshot",
                "previousManifestSha256": "a" * 64,
                "anchorsRecoveredFrom": "manifest-and-whatsapp-coordinates",
                "legacyAnnotationsPreserved": False,
            }
            migration_path = cap / "migration.json"
            migration_path.write_text(json.dumps(migration), encoding="utf-8")
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest.update({"migration": migration, "migrationFile": "migration.json",
                             "migrationSha256": hashlib.sha256(migration_path.read_bytes()).hexdigest()})
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("precisa de uma cadeia local de integridade", result.stdout + result.stderr)

    def test_unmarked_migration_artifacts_without_local_integrity_are_rejected(self):
        for artifact in ("migration.json", "legacy-annotations.json"):
            with self.subTest(artifact=artifact), tempfile.TemporaryDirectory() as root:
                cap = self.create_capture(root)
                (cap / artifact).write_text("{}", encoding="utf-8")
                result = self.verify(root)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("Artefato legado de migração sem metadados no manifesto.", result.stdout + result.stderr)

    def test_unmarked_migration_artifacts_with_local_integrity_are_rejected(self):
        for artifact in ("migration.json", "legacy-annotations.json"):
            with self.subTest(artifact=artifact), tempfile.TemporaryDirectory() as root:
                cap = self.create_capture(root)
                (cap / artifact).write_text("{}", encoding="utf-8")
                harness_cp = ";".join(map(str, (PLUGIN_TEST_CLASSES, PLUGIN_JAR, JSON_JAR, VERIFIER_JAR)))
                generated = subprocess.run([str(JAVA11), "-cp", harness_cp,
                                            "iped.viewers.MigrationIntegrityHistoryHarness", str(cap)],
                                           text=True, capture_output=True)
                self.assertEqual(generated.returncode, 0, generated.stdout + generated.stderr)
                result = self.verify(root)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("Artefato legado de migração sem metadados no manifesto.", result.stdout + result.stderr)

    def test_valid_whatsapp_theme_and_fixed_media_stop_script(self):
        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root, modern=True)
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_fixed_script_csp_and_pdf_sandbox_are_enforced(self):
        for mutation in ("script", "csp", "newtab", "script_csp_pair"):
            with tempfile.TemporaryDirectory() as root:
                cap = self.create_capture(root, modern=True)
                source = (cap / "whatsapp.html").read_text(encoding="utf-8")
                if mutation == "script":
                    bad = source.replace(STOP_MEDIA_SCRIPT, STOP_MEDIA_SCRIPT + "alert(1);")
                elif mutation == "csp":
                    bad = source.replace("connect-src 'none'", "connect-src https:")
                elif mutation == "script_csp_pair":
                    bad = source.replace(MODERN_CSP, LEGACY_MODERN_CSP)
                else:
                    bad = source.replace('rel="noopener noreferrer"', 'rel="opener"')
                self.rehash_html(cap, bad)
                result = self.verify(root)
                self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)

        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root, modern=True, legacy_media_script=True)
            legacy = self.verify(root)
            self.assertEqual(legacy.returncode, 0, legacy.stdout + legacy.stderr)

        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root, b"%PDF-1.7\nfixture", "application/pdf", "pdf", "document", modern=True)
            good = self.verify(root)
            self.assertEqual(good.returncode, 0, good.stdout + good.stderr)
            source = (cap / "whatsapp.html").read_text(encoding="utf-8")
            self.rehash_html(cap, source.replace('sandbox=""', 'sandbox="allow-scripts"'))
            bad = self.verify(root)
            self.assertNotEqual(bad.returncode, 0, bad.stdout + bad.stderr)

    def rehash_html(self, cap, page):
        manifest_path = cap / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["htmlSha256"] = hashlib.sha256(page.encode("utf-8")).hexdigest()
        record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
        manifest["captureRootHash"] = root_hash([record], html_sha=manifest["htmlSha256"])
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        (cap / "whatsapp.html").write_text(page, encoding="utf-8")

    def test_valid_pdf_attachment(self):
        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root, b"%PDF-1.7\nfixture", "application/pdf", "pdf", "document")
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_valid_download_only_unknown_file(self):
        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root, b"opaque bytes", "application/octet-stream", "bin", "file")
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_built_jar_runs_on_java8(self):
        with tempfile.TemporaryDirectory() as root:
            self.create_capture(root)
            result = subprocess.run([str(JAVA), "-jar", str(VERIFIER_JAR), "--verify-only", root], text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_public_message_manifest_reports_matching_messages(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            reference = json.loads((cap / "manifest.json").read_text(encoding="utf-8"))
            result = self.compare_public_manifest(cap, reference)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("Integras: 1", result.stdout)

    def test_public_message_manifest_reports_content_divergence(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            reference = json.loads((cap / "manifest.json").read_text(encoding="utf-8"))
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            reference["messageHashes"][0]["messageHash"] = "f" * 64
            reference["captureRootHash"] = root_hash([{"key":record["key"],"messageHash":"f" * 64}])
            result = self.compare_public_manifest(cap, reference)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("divergentes: 1", result.stdout)

    def test_tampered_attachment_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            next((cap / "attachments").iterdir()).write_bytes(b"tampered")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Anexo divergente", result.stdout + result.stderr)

    def test_tampered_html_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            (cap / "whatsapp.html").write_text(HTML + "alterado", encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Hash do HTML", result.stdout + result.stderr)

    def test_active_html_is_rejected_even_when_hash_is_recomputed(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            active = '<!doctype html><script>alert(1)</script>'
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["htmlSha256"] = hashlib.sha256(active.encode("utf-8")).hexdigest()
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            manifest["captureRootHash"] = root_hash([record], html_sha=manifest["htmlSha256"])
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            (cap / "whatsapp.html").write_text(active, encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("conteúdo ativo", result.stdout + result.stderr)

    def test_message_key_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            messages_path = cap / "messages.json"
            messages = json.loads(messages_path.read_text(encoding="utf-8"))
            messages["messages"][0]["key"] = "f" * 64
            record = messages["messages"][0]
            record["messageHash"] = message_hash(record)
            messages_path.write_text(json.dumps(messages), encoding="utf-8")
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["captureRootHash"] = root_hash([record])
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Chave de identidade divergente", result.stdout + result.stderr)

    def test_fake_attachment_mime_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            messages_path = cap / "messages.json"
            messages = json.loads(messages_path.read_text(encoding="utf-8"))
            record = messages["messages"][0]
            record["attachments"][0]["mime"] = "image/jpeg"
            record["messageHash"] = message_hash(record)
            messages_path.write_text(json.dumps(messages), encoding="utf-8")
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["captureRootHash"] = root_hash([record])
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("MIME incompatível", result.stdout + result.stderr)

    def test_html_must_render_exactly_manifest_messages(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            html = '<!doctype html><div class="linha">olá<img src="attachments/' + DEFAULT_ATTACHMENT_SHA + '.png"></div>'
            html_sha = hashlib.sha256(html.encode("utf-8")).hexdigest()
            (cap / "whatsapp.html").write_text(html, encoding="utf-8")
            manifest["htmlSha256"] = html_sha
            manifest["captureRootHash"] = root_hash([record], html_sha=html_sha)
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("HTML não corresponde exatamente", result.stdout + result.stderr)

    def test_valid_message_reconciliation_is_accepted(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            reconciliation = {"schemaVersion":1, "captureFormat":"html-message-v1", "previousCaptureRootHash":sha("previous"),
                              "currentSourceFingerprint":manifest["sourceFingerprint"], "entries":[
                                  {"key":record["key"], "domId":record["domId"], "state":"unchanged",
                                   "previousMessageHash":record["messageHash"], "currentMessageHash":record["messageHash"]}],
                              "counts":{"unchanged":1,"changed":0,"missingInCurrentSource":0,"newInInterval":0}}
            payload = json.dumps(reconciliation, ensure_ascii=False).encode("utf-8")
            (cap / "message-reconciliation.json").write_bytes(payload)
            reconciliation_sha = hashlib.sha256(payload).hexdigest()
            manifest.update({"reconciliationFile":"message-reconciliation.json", "reconciliationSha256":reconciliation_sha,
                             "captureRootHash":root_hash([record], reconciliation_sha=reconciliation_sha)})
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_tampered_message_reconciliation_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root)
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            reconciliation = {"schemaVersion":1, "captureFormat":"html-message-v1", "previousCaptureRootHash":sha("previous"),
                              "currentSourceFingerprint":manifest["sourceFingerprint"], "entries":[
                                  {"key":record["key"], "domId":record["domId"], "state":"unchanged",
                                   "previousMessageHash":record["messageHash"], "currentMessageHash":record["messageHash"]}],
                              "counts":{"unchanged":1,"changed":0,"missingInCurrentSource":0,"newInInterval":0}}
            payload = json.dumps(reconciliation, ensure_ascii=False).encode("utf-8")
            (cap / "message-reconciliation.json").write_bytes(payload)
            reconciliation_sha = hashlib.sha256(payload).hexdigest()
            manifest.update({"reconciliationFile":"message-reconciliation.json", "reconciliationSha256":reconciliation_sha,
                             "captureRootHash":root_hash([record], reconciliation_sha=reconciliation_sha)})
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            (cap / "message-reconciliation.json").write_text("{}", encoding="utf-8")
            result = self.verify(root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("reconciliação", result.stdout + result.stderr)

    def test_censored_html_message_capture_is_accepted_and_tampering_detected(self):
        with tempfile.TemporaryDirectory() as root:
            cap = self.create_capture(root, modern=True)
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            record = json.loads((cap / "messages.json").read_text(encoding="utf-8"))["messages"][0]
            ah = DEFAULT_ATTACHMENT_SHA
            rel = "attachments/" + ah + ".png"
            key = record["key"]
            censored_css = (
                ".capture-modal{display:none}.capture-modal:target{display:flex}"
                ".capture-censored{position:relative;display:inline-block;overflow:hidden;border-radius:6px;vertical-align:top}"
                ".capture-censored img{display:block;transform:scale(1.06)}"
                ".capture-censored img,img[data-capture-censored]{-webkit-filter:blur(22px) saturate(.25) brightness(.55);filter:blur(22px) saturate(.25) brightness(.55)}"
                ".capture-censored::after{content:\"Censurado - clique para ampliar\";position:absolute;left:0;right:0;bottom:0;z-index:1;box-sizing:border-box;padding:3px 6px;background:rgba(0,0,0,.68);color:#fff;font:600 11px/1.3 Arial,sans-serif;text-align:center;pointer-events:none}"
            )
            html = (
                '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="' + MODERN_CSP + '">'
                '<title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style>'
                '<style>' + censored_css + '</style></head><body>'
                '<div id="capture-topbar">Conversa</div><div id="capture-conversation"><div class="linha" data-capture-message-key="' + key + '">'
                '<a href="#preview-' + ah + '" class="capture-media-link capture-censored" data-capture-censored="1" title="imagem.png">'
                '<img data-capture-censored="1" class="thumb" src="' + rel + '"></a></div></div>'
                '<section class="capture-modal" id="preview-' + ah + '"><img class="capture-preview-image" src="' + rel + '" alt="imagem.png">'
                '<a href="' + rel + '" download="imagem.png">Baixar</a>'
                '<a href="' + rel + '" target="_blank" rel="noopener noreferrer">Abrir</a><a href="#" class="capture-close">Fechar</a></section>'
                '<script>' + STOP_MEDIA_SCRIPT + '</script></body></html>'
            )
            html_sha = hashlib.sha256(html.encode("utf-8")).hexdigest()
            (cap / "whatsapp.html").write_text(html, encoding="utf-8")
            manifest["htmlSha256"] = html_sha
            manifest["captureRootHash"] = root_hash([record], html_sha=html_sha)
            manifest["censorship"] = {
                "schemaVersion": 1,
                "mode": "css-blur",
                "source": "reference-folder",
                "similarityPercent": 75,
                "levelPercent": 90,
                "cssPixels": 22,
                "referenceImageCount": 1,
                "censoredImageCount": 1,
                "censoredMessageCount": 1,
                "images": [{"sha256": ah, "messageKeys": [key]}],
                "presentationDigest": sha(f"censorship-presentation-v1\n22\n{ah}:{key},\n"),
                "notice": "Supressão visual por CSS; os bytes originais permanecem em attachments/ e abrem no modal.",
            }
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = self.verify(root)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

            # Removing the censorship class from whatsapp.html must be detected by the Verifier
            tampered_html = html.replace('class="capture-media-link capture-censored"', 'class="capture-media-link"')
            (cap / "whatsapp.html").write_text(tampered_html, encoding="utf-8")
            tampered_result = self.verify(root)
            self.assertNotEqual(tampered_result.returncode, 0)

    def test_public_history_dialog_clean_layout(self):
        source = (ROOT / "verificador-integridade" / "src" / "IntegrityVerifier.java").read_text(encoding="utf-8")
        self.assertNotIn("Manifesto JSON público de uma captura html-message-v1:", source)
        self.assertNotIn("Comparar manifesto por mensagem", source)
        self.assertNotIn("Copiar hash calculado", source)
        self.assertNotIn("ou copie o hash calculado", source)
        self.assertIn("1. Hash SHA-256 público (de historico-capturas.txt):", source)
        self.assertIn("2. Histórico de capturas completo (historico-capturas.txt):", source)
        self.assertIn("Copiar histórico completo público", source)
        self.assertIn("SHA-256 do histórico completo público", source)
        self.assertIn("Continuar para o resumo", source)

    def test_v140_gui_flow_and_html_report_behavior(self):
        source = (ROOT / "verificador-integridade" / "src" / "IntegrityVerifier.java").read_text(encoding="utf-8")
        # Modo Público pula escolha de capturas e usa loading único
        public_branch_start = source.index("if (mode == 0) {")
        summary_call = source.index("showSummaryDialog(", public_branch_start)
        gui_body = source[public_branch_start:summary_call]
        self.assertNotIn("chooseCaptures", gui_body.split("} else {")[1])
        self.assertIn("runVerificationWithProgress(root, selected", gui_body)
        self.assertIn("runVerificationWithProgress(root, null", gui_body)

        # Janela Resultado simplificada (sem Hash geral do conjunto, SHA-256 completo e Copiar lista de hashes)
        summary_start = source.index("private static void showSummaryDialog(")
        summary_end = source.index("private static JPanel hashValueRow(", summary_start)
        summary_body = source[summary_start:summary_end]
        self.assertNotIn("Hash geral do conjunto", summary_body)
        self.assertNotIn("SHA-256 do histórico completo\"", summary_body)
        self.assertNotIn("Copiar lista de hashes", summary_body)
        self.assertIn("SHA-256 do histórico completo público", summary_body)

        # AlwaysOnTop + EDT centralizados em chooseCaptures e showTopMessage
        self.assertIn("applyWindowDefaults(dialog);", source)
        self.assertNotIn("JOptionPane.showOptionDialog(", source)
        self.assertNotIn("JOptionPane.showMessageDialog(", source)

        # Gera HTML real com 2 capturas (mesmo timestamp para testar ausência de colisão anonimizada + UTF-8) e executa o JS no Node
        with tempfile.TemporaryDirectory() as td:
            import json as _json
            root = Path(td)
            cap_root = root / "html_eval"
            cap_root.mkdir()
            for cname, content_txt in (
                ("João_São_Paulo-20261113-190000", "Conteúdo A — café"),
                ("Maria_Cotia-20261113-190000", "Conteúdo B — pão"),
            ):
                cdir = cap_root / cname
                cdir.mkdir()
                data = content_txt.encode("utf-8")
                (cdir / "arquivo.txt").write_bytes(data)
                art = [{"path": "arquivo.txt", "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}]
                (cdir / "manifest.json").write_text(_json.dumps({"artifacts": art}), encoding="utf-8")
                (cdir / "integridade.json").write_text(_json.dumps({"artifacts": art}), encoding="utf-8")

            export = subprocess.run(
                [str(JAVA), "-jar", str(VERIFIER_JAR), "--export-history", str(cap_root)],
                capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
            )
            self.assertEqual(export.returncode, 0, export.stdout + export.stderr)
            full_hist = (cap_root / "integridade" / "historico-capturas.txt").read_text(encoding="utf-8")

            harness_dir = root / "html_harness"
            harness_dir.mkdir()
            harness_java = harness_dir / "HtmlGenHarness.java"
            harness_java.write_text(
                """
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class HtmlGenHarness {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        List<IntegrityVerifier.Capture> caps = IntegrityVerifier.discover(root);
        Method v = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
        v.setAccessible(true);
        for (IntegrityVerifier.Capture c : caps) v.invoke(null, c);
        Method sh = IntegrityVerifier.class.getDeclaredMethod("setHash", List.class);
        sh.setAccessible(true);
        String rh = (String) sh.invoke(null, caps);
        String fullHist = new String(Files.readAllBytes(root.resolve("integridade/historico-capturas.txt")), StandardCharsets.UTF_8);
        String pubHist = IntegrityVerifier.publicHistoryReport(caps, rh);
        Method wr = IntegrityVerifier.class.getDeclaredMethod("writeReports", Path.class, List.class, String.class, String.class, String.class);
        wr.setAccessible(true);
        wr.invoke(null, root, caps, rh, "2026-11-13T19:00:00Z", fullHist);
        Files.write(root.resolve("pub-history.txt"), pubHist.getBytes(StandardCharsets.UTF_8));
        System.out.println("FULL_SHA=" + sha(fullHist));
        System.out.println("PUB_SHA=" + sha(pubHist));
    }
    private static String sha(String s) throws Exception {
        String n = s.replace("\\r\\n", "\\n").replace("\\r", "\\n").replaceAll("\\n+$", "") + "\\n";
        byte[] d = MessageDigest.getInstance("SHA-256").digest(n.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : d) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
}
                """,
                encoding="utf-8",
            )
            javac = JAVA.with_name("javac.exe")
            cp = f"{VERIFIER_JAR};{harness_dir}"
            comp = subprocess.run([str(javac), "-encoding", "UTF-8", "-cp", str(VERIFIER_JAR), "-d", str(harness_dir), str(harness_java)], capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.assertEqual(comp.returncode, 0, comp.stdout + comp.stderr)
            gen = subprocess.run([str(JAVA), "-cp", cp, "HtmlGenHarness", str(cap_root)], capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.assertEqual(gen.returncode, 0, gen.stdout + gen.stderr)
            full_sha = [line.split("=", 1)[1] for line in gen.stdout.splitlines() if line.startswith("FULL_SHA=")][0]
            pub_sha = [line.split("=", 1)[1] for line in gen.stdout.splitlines() if line.startswith("PUB_SHA=")][0]
            html = (cap_root / "integridade.html").read_text(encoding="utf-8")
            self.assertIn("Hash geral Público:", html)
            self.assertNotIn("Comparar hash desta captura", html)
            self.assertNotIn("Hash geral das capturas selecionadas", html)

            node_script = r"""
const fs = require('fs');
const path = process.argv[1];
const html = fs.readFileSync(path + '/integridade.html', 'utf8');
const fullHist = fs.readFileSync(path + '/integridade/historico-capturas.txt', 'utf8');
const pubHist = fs.readFileSync(path + '/pub-history.txt', 'utf8');
const fullSha = process.argv[2], pubSha = process.argv[3];

const mScript = html.match(/<script>([\s\S]*?)<\/script>/);
if (!mScript) throw new Error('missing script');
const rows = [];
const rowRe = /data-capture="([^"]*)"\s+data-block-full="([^"]*)"\s+data-block-public="([^"]*)"/g;
let rm;
while ((rm = rowRe.exec(html)) !== null) {
  const cmpSpan = { className: 'cmp', textContent: 'Não comparado' };
  rows.push({
    attrs: { 'data-capture': rm[1], 'data-block-full': rm[2], 'data-block-public': rm[3] },
    getAttribute(k) { return this.attrs[k]; },
    querySelector(s) { return s === '.cmp' ? cmpSpan : null; },
    cmp: cmpSpan,
  });
}
if (rows.length !== 2) throw new Error('expected 2 rows, got ' + rows.length);

const elements = {
  publicHash: { value: '' },
  comparison: { className: '', textContent: '' },
  historyInput: { value: '' },
  historyResult: { className: '', textContent: '' },
};
const document = {
  getElementById(id) { return elements[id]; },
  querySelectorAll(sel) {
    if (sel === '.capture-row') return rows;
    if (sel === '.capture-row .cmp') return rows.map(r => r.cmp);
    return [];
  },
  createElement() { return { value: '', select(){}, remove(){} }; },
  body: { appendChild(){} },
  execCommand() {},
};
eval(mScript[1]);

function assert(cond, msg) { if (!cond) throw new Error(msg); }

elements.publicHash.value = pubSha; compareHash();
assert(elements.comparison.className === 'match', 'pubSha should match');
elements.publicHash.value = fullSha; compareHash();
assert(elements.comparison.className === 'match', 'fullSha should match');
elements.publicHash.value = '0'.repeat(64); compareHash();
assert(elements.comparison.className === 'diverge', 'zero hash should diverge');

elements.historyInput.value = fullHist; compareHistory();
assert(elements.historyResult.className === 'match', 'full history overall match: ' + elements.historyResult.textContent);
assert(rows.every(r => r.cmp.textContent === 'OK' && r.cmp.className === 'cmp match'), 'all rows OK for full history');

elements.historyInput.value = pubHist; compareHistory();
assert(elements.historyResult.className === 'match', 'public history overall match: ' + elements.historyResult.textContent);
assert(rows.every(r => r.cmp.textContent === 'OK' && r.cmp.className === 'cmp match'), 'all rows OK for anonymized history');

// Omit second capture block -> first OK, second FALHA
const cutIdx = fullHist.indexOf('\nCAPTURA|Maria_Cotia-20261113-190000');
const partialHist = fullHist.slice(0, cutIdx) + '\nNota|fim\n';
elements.historyInput.value = partialHist; compareHistory();
assert(rows[0].cmp.textContent === 'OK' && rows[0].cmp.className === 'cmp match', 'present capture OK');
assert(rows[1].cmp.textContent === 'FALHA' && rows[1].cmp.className === 'cmp diverge', 'missing capture FALHA');
assert(elements.historyResult.className === 'diverge', 'partial history overall diverge');

// Tamper byte inside first capture block -> first FALHA, second OK
const tamperedHist = fullHist.replace('ARQUIVO|arquivo.txt|', 'ARQUIVO|adulterado.txt|');
elements.historyInput.value = tamperedHist; compareHistory();
assert(rows[0].cmp.textContent === 'FALHA' && rows[0].cmp.className === 'cmp diverge', 'tampered capture FALHA');
assert(rows[1].cmp.textContent === 'OK' && rows[1].cmp.className === 'cmp match', 'untampered capture still OK');

console.log('NODE_HTML_JS_OK');
"""
            node_run = subprocess.run(["node", "-e", node_script, str(cap_root), full_sha, pub_sha], capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.assertEqual(node_run.returncode, 0, node_run.stdout + node_run.stderr)
            self.assertIn("NODE_HTML_JS_OK", node_run.stdout)

    def test_public_anonymized_history_export_and_dual_verification(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            cap_name = "Clebia_mae_mateus_sabe_que_ele_vai_me_ligar-20261026-190158"
            cap = self.create_capture(root)
            cap.rename(root / cap_name)

            export = subprocess.run(
                [str(JAVA), "-jar", str(VERIFIER_JAR), "--export-history", str(root)],
                capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
            )
            self.assertEqual(export.returncode, 0, export.stdout + export.stderr)
            self.assertIn("SHA-256_PUBLICO=", export.stdout)
            self.assertIn("SHA-256_HISTORICO_PUBLICO=", export.stdout)

            full_history = (root / "integridade" / "historico-capturas.txt").read_text(encoding="utf-8")
            self.assertIn(f"CAPTURA|{cap_name}", full_history)
            anon_history = full_history.replace(f"CAPTURA|{cap_name}", "CAPTURA|*-20261026-190158")
            full_sha = sha(full_history.replace("\r\n", "\n").rstrip("\n") + "\n")
            anon_sha = sha(anon_history.replace("\r\n", "\n").rstrip("\n") + "\n")
            self.assertIn(f"SHA-256_PUBLICO={full_sha}", export.stdout)
            self.assertIn(f"SHA-256_HISTORICO_PUBLICO={anon_sha}", export.stdout)

            harness_dir = Path(td) / "harness"
            harness_dir.mkdir()
            harness_java = harness_dir / "PublicAnonHarness.java"
            harness_java.write_text(
                """
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PublicAnonHarness {
    public static void main(String[] args) throws Exception {
        Method anon = IntegrityVerifier.class.getDeclaredMethod("anonymizeCaptureName", String.class);
        Method matchHist = IntegrityVerifier.class.getDeclaredMethod("classifyPublicHistoryMatch", String.class, String.class, String.class);
        Method matchHash = IntegrityVerifier.class.getDeclaredMethod("classifyPublicHashMatch", String.class, String.class, String.class);
        String full = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        String pub = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
        System.out.println("ANON1=" + anon.invoke(null, "Clebia_mae_mateus_sabe_que_ele_vai_me_ligar-20261026-190158"));
        System.out.println("ANON2=" + anon.invoke(null, "investimaneto_nexum_RS-10900-20260527-170217"));
        System.out.println("ANON3=" + anon.invoke(null, "sem_data_captura"));
        System.out.println("H_FULL=" + matchHist.invoke(null, full, full, pub));
        System.out.println("H_PUB=" + matchHist.invoke(null, pub, full, pub));
        System.out.println("H_BAD_PREFIX=" + matchHist.invoke(null, pub.replace("CAPTURA|*", "CAPTURA|Fake"), full, pub));
        System.out.println("H_BAD_TS=" + matchHist.invoke(null, pub.replace("*-20261026-190158", "*-20261026-190159"), full, pub));
        System.out.println("K_FULL=" + matchHash.invoke(null, args[2], args[2], args[3]));
        System.out.println("K_PUB=" + matchHash.invoke(null, args[3], args[2], args[3]));
        System.out.println("K_BAD=" + matchHash.invoke(null, "0000000000000000000000000000000000000000000000000000000000000000", args[2], args[3]));
    }
}
                """,
                encoding="utf-8",
            )
            javac = JAVA.with_name("javac.exe")
            cp = f"{VERIFIER_JAR};{harness_dir}"
            comp = subprocess.run([str(javac), "-encoding", "UTF-8", "-cp", str(VERIFIER_JAR), "-d", str(harness_dir), str(harness_java)], capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.assertEqual(comp.returncode, 0, comp.stdout + comp.stderr)
            pub_file = root / "pub.txt"
            pub_file.write_text(anon_history, encoding="utf-8")
            run = subprocess.run(
                [str(JAVA), "-cp", cp, "PublicAnonHarness", str(root / "integridade" / "historico-capturas.txt"), str(pub_file), full_sha, anon_sha],
                capture_output=True, text=True, encoding="utf-8", errors="replace",
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertIn("ANON1=*-20261026-190158", run.stdout)
            self.assertIn("ANON2=*-20260527-170217", run.stdout)
            self.assertIn("ANON3=*", run.stdout)
            self.assertIn("H_FULL=MATCH_FULL", run.stdout)
            self.assertIn("H_PUB=MATCH_PUBLIC", run.stdout)
            self.assertIn("H_BAD_PREFIX=DIVERGE", run.stdout)
            self.assertIn("H_BAD_TS=DIVERGE", run.stdout)
            self.assertIn("K_FULL=MATCH_FULL", run.stdout)
            self.assertIn("K_PUB=MATCH_PUBLIC", run.stdout)
            self.assertIn("K_BAD=DIVERGE", run.stdout)

    def test_cli_flags_and_headless_error_paths(self):
        self.assertTrue(CLASSES.is_dir(), "compile the verifier classes before this integration test")
        base_cmd = [str(JAVA), "-cp", f"{CLASSES};{VERIFIER_JAR}", "IntegrityVerifier"]
        targets = [base_cmd]
        exe_override = os.environ.get("TEST_VERIFIER_EXE", "").strip()
        if exe_override:
            targets.append([exe_override])
        for prefix in targets:
            ver = subprocess.run(prefix + ["--version"], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
            self.assertEqual(ver.returncode, 0, ver.stdout + ver.stderr)
            self.assertIn("Verificador de Integridade 1.5.4", ver.stdout)

            hlp = subprocess.run(prefix + ["--help"], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
            self.assertEqual(hlp.returncode, 0, hlp.stdout + hlp.stderr)
            self.assertIn("--verify-only", hlp.stdout)

            st = subprocess.run(prefix + ["--self-test"], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
            self.assertEqual(st.returncode, 0, st.stdout + st.stderr)
            self.assertIn("SELF_TEST_OK 1.5.4", st.stdout)

            bad = subprocess.run(prefix + ["--nao-existe"], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
            self.assertNotEqual(bad.returncode, 0)
            self.assertNotIn("NoSuchFileException", bad.stderr)

            with tempfile.TemporaryDirectory() as empty_dir:
                empty = subprocess.run(prefix + ["--verify-only", empty_dir], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
                self.assertNotEqual(empty.returncode, 0)
                self.assertIn("Nenhuma captura", empty.stderr)

    def test_verify_only_rejects_extra_file_in_legacy_capture(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cap = root / "Legacy_Chat-20261113-120000"
            cap.mkdir()
            data = b"conteudo-legado-integro"
            (cap / "texto.txt").write_bytes(data)
            sha = hashlib.sha256(data).hexdigest()
            root_hash = hashlib.sha256(f"texto.txt|{len(data)}|{sha}\n".encode("utf-8")).hexdigest()
            artifacts = [{"path": "texto.txt", "size": len(data), "sha256": sha}]
            event = {
                "sequence": 1,
                "timestampUtc": "2026-11-13T12:00:00Z",
                "action": "capture_created",
                "previousRecordHash": "",
                "contentRootHash": root_hash,
                "changes": [{"action": "added", "path": "texto.txt", "sha256": sha}],
            }
            event["recordHashFormat"] = "canonical-v1"
            event["recordHash"] = hashlib.sha256(json.dumps(event, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")).hexdigest()
            record = {
                "schemaVersion": 2,
                "contentRootHash": root_hash,
                "artifacts": artifacts,
                "events": [event],
            }
            (cap / "integridade.json").write_text(json.dumps(record), encoding="utf-8")
            (cap / "manifest.json").write_text(json.dumps({"artifacts": artifacts}), encoding="utf-8")

            clean = self.verify(root)
            self.assertEqual(clean.returncode, 0, clean.stdout + clean.stderr)

            (cap / "extra-nao-declarado.txt").write_bytes(b"intruso")
            dirty = self.verify(root)
            self.assertNotEqual(dirty.returncode, 0, "Legacy capture with undeclared extra file must fail --verify-only")
            self.assertIn("arquivos extras na captura: 1", dirty.stdout)

    def test_verify_only_rejects_html_capture_root_hash_tamper_and_schema_downgrade(self):
        harness_cp = ";".join(map(str, (PLUGIN_TEST_CLASSES, PLUGIN_JAR, JSON_JAR, VERIFIER_JAR)))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cap = self.create_capture(root, modern=True)
            generated = subprocess.run([str(JAVA11), "-cp", harness_cp,
                                        "iped.viewers.MigrationIntegrityHistoryHarness", str(cap)],
                                       text=True, capture_output=True)
            self.assertEqual(generated.returncode, 0, generated.stdout + generated.stderr)

            clean = self.verify(root)
            self.assertEqual(clean.returncode, 0, clean.stdout + clean.stderr)

            # Even if integridade.json is regenerated over tampered manifest.json, captureRootHash mismatch must fail CLI
            manifest_path = cap / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["captureRootHash"] = "f" * 64
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            regenerated = subprocess.run([str(JAVA11), "-cp", harness_cp,
                                          "iped.viewers.MigrationIntegrityHistoryHarness", str(cap)],
                                         text=True, capture_output=True)
            self.assertEqual(regenerated.returncode, 0, regenerated.stdout + regenerated.stderr)
            tampered = self.verify(root)
            self.assertNotEqual(tampered.returncode, 0, "Tampered captureRootHash must fail --verify-only even with consistent integridade.json")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cap = self.create_capture(root, modern=True)
            generated = subprocess.run([str(JAVA11), "-cp", harness_cp,
                                        "iped.viewers.MigrationIntegrityHistoryHarness", str(cap)],
                                       text=True, capture_output=True)
            self.assertEqual(generated.returncode, 0, generated.stdout + generated.stderr)
            integrity_path = cap / "integridade.json"
            integrity = json.loads(integrity_path.read_text(encoding="utf-8"))
            integrity["schemaVersion"] = 1
            integrity_path.write_text(json.dumps(integrity), encoding="utf-8")
            downgraded = self.verify(root)
            self.assertNotEqual(downgraded.returncode, 0, "html-message-v1 must reject integridade.json schemaVersion < 2")

    def test_verifier_icon_ignores_cwd_file_and_loads_from_classpath(self):
        with tempfile.TemporaryDirectory() as tmp:
            cwd = Path(tmp)
            probe_classes = cwd / "probe_classes"
            probe_classes.mkdir()
            (cwd / "verificador-icon.png").write_bytes(b"not-a-valid-icon-cwd")
            (cwd / "imagem doc").mkdir()
            (cwd / "imagem doc" / "verificador-icon.png").write_bytes(b"not-a-valid-icon-sub")
            probe_src = cwd / "IconProbe.java"
            probe_src.write_text(
                "import java.awt.Image;\n"
                "import java.lang.reflect.Method;\n"
                "import javax.swing.ImageIcon;\n"
                "public class IconProbe {\n"
                "  public static void main(String[] args) throws Exception {\n"
                "    Method m = IntegrityVerifier.class.getDeclaredMethod(\"verifierIcon\");\n"
                "    m.setAccessible(true);\n"
                "    Image img = (Image) m.invoke(null);\n"
                "    if (img == null) { System.out.println(\"NULL\"); return; }\n"
                "    ImageIcon ii = new ImageIcon(img);\n"
                "    System.out.println(\"LOADED:\" + ii.getIconWidth() + \"x\" + ii.getIconHeight());\n"
                "  }\n"
                "}\n",
                encoding="utf-8",
            )
            javac = JAVA.parent / "javac.exe"
            c1 = subprocess.run([str(javac), "-encoding", "UTF-8", "-cp", str(CLASSES), "-d", str(probe_classes), str(probe_src)],
                                capture_output=True, text=True)
            self.assertEqual(c1.returncode, 0, c1.stdout + c1.stderr)
            r_loose = subprocess.run([str(JAVA), "-cp", f"{probe_classes};{CLASSES};{VERIFIER_JAR.parent / 'dependencies' / '*'}", "IconProbe"],
                                     cwd=str(cwd), capture_output=True, text=True, timeout=20)
            self.assertEqual(r_loose.returncode, 0, r_loose.stdout + r_loose.stderr)
            self.assertEqual(r_loose.stdout.strip(), "NULL", "verifierIcon() must ignore CWD files")

            r_jar = subprocess.run([str(JAVA), "-cp", f"{probe_classes};{VERIFIER_JAR}", "IconProbe"],
                                   cwd=str(cwd), capture_output=True, text=True, timeout=20)
            self.assertEqual(r_jar.returncode, 0, r_jar.stdout + r_jar.stderr)
            out = r_jar.stdout.strip()
            self.assertTrue(out.startswith("LOADED:") and not out.endswith(":-1x-1"),
                            f"verifierIcon() must load embedded PNG from classpath resource: {out}")

    def test_origin_audit_first_screen_and_html_export_contract(self):
        source = (ROOT / "verificador-integridade" / "src" / "IntegrityVerifier.java").read_text(encoding="utf-8")
        cm_start = source.index("private static int chooseMode(")
        cm_end = source.index("private static Path applicationFolder(", cm_start)
        cm_body = source[cm_start:cm_end]
        self.assertIn("Integridade Local", cm_body)
        self.assertIn("Integridade Público", cm_body)
        self.assertIn("Auditoria de origem...", cm_body)

        rg_start = source.index("private static void runGuiFlow(")
        rg_end = source.index("if (mode == 0) {", rg_start)
        rg_body = source[rg_start:rg_end]
        self.assertIn("if (mode == 2) {", rg_body)
        self.assertIn("runVerificationWithProgress(root, null", rg_body)
        self.assertIn("showOriginAuditWorkspace(", rg_body)

        ws_start = source.index("private static void openOriginAuditWorkspaceWindow(")
        ws_end = source.index("static String buildOriginAuditHtml(", ws_start)
        ws_body = source[ws_start:ws_end]
        for expected in (
            "Pasta de origem:",
            "Pasta do caso (opcional):",
            "Validar Auditoria",
            "Exportar Auditoria",
            "Auditoria_relatorio.htm",
            "Copiar conteúdo",
            'reportArea.putClientProperty("html.disable", Boolean.TRUE)',
            "precheckOriginAuditFolders(",
            "showOriginPrecheckDialog(",
            "runOriginAuditWithProgressDialog(",
        ):
            self.assertIn(expected, ws_body)

        # Botao de auditoria existe apenas na tela inicial (nao na janela Resultado)
        summary_start = source.index("private static void showSummaryDialog(")
        summary_end = source.index("private static JPanel hashValueRow(", summary_start)
        self.assertNotIn("Auditoria de origem", source[summary_start:summary_end])

        # Janela de conferencia das pastas e janela de carregamento
        self.assertIn("Começar Auditoria", source)
        self.assertIn("Conferência das pastas", source)
        self.assertIn("Executando Auditoria de origem", source)
        self.assertIn("Tempo decorrido", source)

if __name__ == "__main__":
    unittest.main(verbosity=2)
