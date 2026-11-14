from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(REPO / "teste_antes_de_aplicar"))
import prototype_renderer as proto
from test_env_paths import find_iped_jjs, find_sample_capture, find_sample_html

SOURCE = find_sample_html()
CAPTURE = find_sample_capture()
JJS = find_iped_jjs()


def run_webview(page: Path, report: Path, image: Path | None = None, scripts: bool = False) -> dict:
    args = [str(JJS), "-fx", "--add-modules", "javafx.web", str(HERE / "probe_webview.js"), "--", str(page), str(report)]
    if image:
        args.extend([str(image), "enable-js" if scripts else "disable-js"])
    elif scripts:
        args.extend(["", "enable-js"])
    result = subprocess.run(args, capture_output=True, text=True, timeout=90)
    if result.returncode or "WEBVIEW_TEST_EXIT=0" not in result.stdout:
        raise AssertionError("IPED JavaFX WebView probe failed: " + result.stdout + result.stderr)
    return json.loads(report.read_text(encoding="utf-8"))


def media_records(root: Path) -> list[dict]:
    files = root / "attachments"
    mime_by_name = {
        "image.png": "image/png", "sample.pdf": "application/pdf",
        "sample.docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "sample.bin": "application/octet-stream", "sample.wav": "audio/wav", "sample.webm": "video/webm",
    }
    rows = []
    for name, mime in mime_by_name.items():
        path = files / name
        data = path.read_bytes()
        rows.append({"name": name, "path": "attachments/" + name, "mime": mime,
                     "size": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    return rows


class RenderWhatsappSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SOURCE.is_file():
            raise unittest.SkipTest("IPED 4.3.1 source HTML fixture is unavailable")
        if not JJS.is_file():
            raise unittest.SkipTest("IPED 4.3.1 JavaFX WebView runtime is unavailable")

    def test_A_visual_contract_on_iped_431_webview(self):
        with tempfile.TemporaryDirectory(prefix="whatsapp-A-") as temp:
            report, screenshot = Path(temp) / "source.json", Path(temp) / "source.png"
            actual = run_webview(SOURCE, report, screenshot)
            self.assertEqual(actual["mode"], "source")
            self.assertEqual(actual["idsCount"], 17)
            self.assertEqual(actual["styleBytes"], 88129)
            self.assertEqual(actual["cssUrlKinds"]["remote"], 0)
            self.assertEqual(actual["cssUrlKinds"]["relative"], 0)
            self.assertTrue(actual["topbar"])
            self.assertTrue(actual["conversation"])
            self.assertTrue(screenshot.is_file() and screenshot.stat().st_size > 1000)
            self.assertEqual(len(actual["svgRules"]), 4)
            self.assertTrue(all(rule["safe"] for rule in actual["svgRules"]))
            self.assertTrue(any(row.get("id") == "243189" and row["found"] for row in actual["rows"]))

    def test_B_selected_range_is_standalone_and_deterministic(self):
        self.assertTrue(CAPTURE.is_dir(), "reference capture fixture unavailable")
        with tempfile.TemporaryDirectory(prefix="whatsapp-B-") as temp:
            root = Path(temp)
            out1, out2 = root / "first", root / "second"
            result1 = proto.build_capture(SOURCE, CAPTURE, out1, "243188", "243190")
            result2 = proto.build_capture(SOURCE, CAPTURE, out2, "243188", "243190")
            rows = json.loads((CAPTURE / "messages.json").read_text(encoding="utf-8"))["messages"]
            expected = [row["domId"] for row in rows if row["domId"] in {"243188", "243189", "243190"}]
            self.assertEqual(result1["selectedIds"], expected)
            self.assertEqual(result1["selectedIds"], result2["selectedIds"])
            self.assertEqual(result1["pageBytes"], result2["pageBytes"])
            self.assertEqual(hashlib.sha256(result1["html"].read_bytes()).digest(), hashlib.sha256(result2["html"].read_bytes()).digest())
            records = [att for row in rows if row["domId"] in expected for att in row.get("attachments", [])]
            checked = proto.validate_page(result1["html"], records, out1)
            self.assertEqual(checked["messageKeyCount"], 3)
            self.assertEqual(checked["scriptHash"], proto.SCRIPT_CSP_HASH)
            with self.assertRaises(ValueError):
                proto.build_capture(SOURCE, CAPTURE, root / "bad", "243190", "243188")
            report, screenshot = root / "capture.json", root / "capture.png"
            actual = run_webview(result1["html"], report, screenshot, scripts=True)
            self.assertEqual(actual["captureMessageCount"], 3)
            self.assertTrue(actual["originalMessageIdsRemoved"])
            self.assertTrue(actual["viewerTest"]["visibleAfterOpen"])
            self.assertTrue(actual["viewerTest"]["hiddenAfterClose"])
            self.assertTrue(actual["viewerTest"]["imageLoaded"])
            self.assertTrue(actual["viewerTest"]["hasDownload"])
            self.assertTrue(actual["viewerTest"]["newTabSafe"])
            self.assertTrue(actual["viewerTest"]["scrollRestored"])
            self.assertTrue(actual["topbar"])
            self.assertTrue(screenshot.is_file() and screenshot.stat().st_size > 1000)

    def test_C_all_attachment_types_actions_and_close_media(self):
        with tempfile.TemporaryDirectory(prefix="whatsapp-C-") as temp:
            root = Path(temp)
            page = proto.build_media_fixture(root)
            records = media_records(root)
            result = proto.validate_page(page, records, root)
            self.assertEqual(result["resourceCount"], 6)
            report = root / "media.json"
            actual = run_webview(page, report, scripts=True)
            rows = actual["attachmentTests"]
            self.assertEqual(len(rows), 6)
            for row in rows:
                with self.subTest(href=row["href"]):
                    self.assertTrue(row["targetExists"])
                    self.assertTrue(row["visibleAfterOpen"])
                    self.assertTrue(row["hiddenAfterClose"])
                    self.assertTrue(row["scrollRestored"])
                    self.assertTrue(row["hasDownload"])
                    self.assertTrue(row["newTabSafe"])
            image, pdf, doc, generic, audio, video = rows
            self.assertTrue(image["hasImage"] and image["imageLoaded"])
            self.assertTrue(pdf["hasPdfFrame"] and pdf["pdfSandbox"] == "")
            self.assertFalse(audio["audioAutoplay"] or video["videoAutoplay"])
            self.assertTrue(audio["audioControls"] and video["videoControls"])
            self.assertEqual(audio["audioPreload"], "none")
            self.assertEqual(video["videoPreload"], "none")
            self.assertTrue(audio["mediaStarted"] and audio["audioPausedAfterClose"])
            self.assertTrue(video["mediaStarted"] and video["videoPausedAfterClose"])
            self.assertTrue(doc["hasDownload"])

    def test_D_active_content_csp_paths_and_tampering_rejected(self):
        for css in [
            "@import url(https://example.invalid/a.css)",
            "a{background:url(https://example.invalid/a.png)}",
            "a{background:url(file:///secret)}", "a{width:expression(alert(1))}",
            "a{background:url(data:image/png;base64,@@@@)}",
            'a{background:url(data:image/svg+xml;base64,' + __import__("base64").b64encode(b'<svg xmlns="http://www.w3.org/2000/svg"><script/></svg>').decode() + ')}',
            'a{background:url(data:image/svg+xml;base64,' + __import__("base64").b64encode(b'<svg xmlns="http://www.w3.org/2000/svg"><g onclick="x()"/></svg>').decode() + ')}',
            'a{background:url(data:image/svg+xml;base64,' + __import__("base64").b64encode(b'<svg xmlns="http://www.w3.org/2000/svg"><image href="https://x"/></svg>').decode() + ')}',
            'a{background:url(data:image/svg+xml;base64,' + __import__("base64").b64encode(b'<svg xmlns="http://www.w3.org/2000/svg"><foreignObject/></svg>').decode() + ')}',
        ]:
            with self.subTest(css=css[:40]), self.assertRaises(ValueError):
                proto.sanitize_css(css)
        with tempfile.TemporaryDirectory(prefix="whatsapp-D-") as temp:
            root = Path(temp)
            page = proto.build_media_fixture(root)
            records = media_records(root)
            original = page.read_text(encoding="utf-8")
            mutations = [
                original.replace("</head>", '<meta http-equiv="refresh" content="0;url=https://invalid"></head>'),
                original.replace("connect-src &#x27;none&#x27;", "connect-src https:"),
                original.replace('sandbox=""', 'sandbox="allow-scripts allow-same-origin"'),
                original.replace('rel="noopener noreferrer"', 'rel="opener"'),
                original.replace('href="#preview-', 'href="#missing-'),
                original.replace(proto.STOP_MEDIA_SCRIPT, proto.STOP_MEDIA_SCRIPT + "alert(1);"),
            ]
            for index, changed in enumerate(mutations):
                bad = root / ("mutated-" + str(index) + ".html")
                bad.write_text(changed, encoding="utf-8")
                with self.subTest(mutation=index), self.assertRaises(ValueError):
                    proto.validate_page(bad, records, root)
            wrong_mime = [dict(row) for row in records]
            wrong_mime[0]["mime"] = "application/pdf"
            with self.assertRaises(ValueError):
                proto.validate_page(page, wrong_mime, root)
            with self.assertRaises(ValueError):
                proto.validate_page(page, [dict(records[0], path="../outside.png")], root)
            target = root / records[0]["path"]
            target.write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                proto.validate_page(page, records, root)

    def test_E_local_file_render_and_no_external_dependencies(self):
        with tempfile.TemporaryDirectory(prefix="whatsapp-E-") as temp:
            root = Path(temp)
            page = proto.build_media_fixture(root)
            records = media_records(root)
            proto.validate_page(page, records, root)
            source = page.read_text(encoding="utf-8")
            self.assertNotRegex(source, r"(?i)(?:https?:)?//[^/\s]+")
            self.assertNotRegex(source, r"(?i)(?:src|href)\s*=\s*['\"](?:file:|javascript:|https?:)")
            report = root / "offline.json"
            actual = run_webview(page, report, scripts=True)
            self.assertEqual(len(actual["attachmentTests"]), 6)
            self.assertEqual(actual["csp"], proto.CSP)

    def test_F_legacy_baseline_suite_passes_without_rewriting_fixtures(self):
        runner = REPO / "teste_antes_de_aplicar" / "run_all_tests.ps1"
        result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(runner)],
                                cwd=str(REPO), capture_output=True, text=True, timeout=240)
        if result.returncode:
            self.fail("Existing legacy/compatibility suite failed:\n" + result.stdout[-10000:] + result.stderr[-10000:])
        self.assertTrue((result.stdout + result.stderr).strip())


if __name__ == "__main__":
    unittest.main(verbosity=2)
