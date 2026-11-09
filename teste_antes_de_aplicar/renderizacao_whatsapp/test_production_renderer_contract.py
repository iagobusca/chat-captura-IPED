from __future__ import annotations

import base64
import hashlib
import re
import unittest
import zipfile
from pathlib import Path


REPO = Path(__file__).resolve().parents[2]
PLUGIN_SOURCE = (REPO / "src/iped/viewers/HtmlLinkViewer.java").read_text(encoding="utf-8")
VERIFIER_SOURCE = (REPO / "verificador-integridade/src/IntegrityVerifier.java").read_text(encoding="utf-8")
PLUGIN_JAR = REPO / "instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar"


def java_string(source: str, name: str) -> str:
    match = re.search(rf"{name}\s*=\s*\"([^\"]*)\"", source)
    if not match:
        raise AssertionError(f"Java constant {name} not found")
    return match.group(1)


class ProductionRendererContract(unittest.TestCase):
    def test_plugin_and_verifier_use_identical_fixed_script_and_csp(self) -> None:
        plugin_script = java_string(PLUGIN_SOURCE, "CAPTURE_MEDIA_STOP_SCRIPT")
        verifier_script = java_string(VERIFIER_SOURCE, "CAPTURE_MEDIA_STOP_SCRIPT")
        plugin_csp = java_string(PLUGIN_SOURCE, "CAPTURE_CSP")
        verifier_csp = java_string(VERIFIER_SOURCE, "CAPTURE_CSP")
        self.assertEqual(plugin_script, verifier_script)
        self.assertEqual(plugin_csp, verifier_csp)
        self.assertIn("m.pause();m.currentTime=0", plugin_script)
        script_hash = base64.b64encode(hashlib.sha256(plugin_script.encode()).digest()).decode()
        self.assertIn(f"script-src 'sha256-{script_hash}'", plugin_csp)
        self.assertIn("connect-src 'none'", plugin_csp)

    def test_production_page_keeps_theme_and_only_selected_rows(self) -> None:
        capture_start = PLUGIN_SOURCE.index("private CaptureRunResult runHtmlMessageCapture")
        capture_end = PLUGIN_SOURCE.index("private static String sanitizeCaptureThemeCss", capture_start)
        generator = PLUGIN_SOURCE[capture_start:capture_end]
        self.assertIn('put("presentationVersion", 2)', generator)
        self.assertIn("sanitizeCaptureThemeCss", generator)
        self.assertIn("validateCaptureHeaderHtml", generator)
        self.assertIn("validateCaptureSeparatorHtml", generator)
        self.assertIn("themeCss", generator)
        self.assertIn("headerHtml", generator)
        self.assertIn("separatorBefore", generator)
        self.assertIn("captureViewerCss()", generator)
        self.assertIn("renderCaptureAttachmentModal", generator)
        self.assertIn("page.append(fragment", generator)

    def test_attachment_modal_actions_match_the_specification(self) -> None:
        start = PLUGIN_SOURCE.index("private String renderCaptureAttachmentModal")
        end = PLUGIN_SOURCE.index("private String linkInlineCaptureImages", start)
        modal = PLUGIN_SOURCE[start:end]
        self.assertIn("capture-preview-image", modal)
        self.assertIn("capture-preview-pdf", modal)
        self.assertIn('sandbox=\\\"\\\"', modal)
        self.assertIn('controls preload=\\\"none\\\"', modal)
        self.assertIn('download=\\\"', modal)
        self.assertIn('target=\\\"_blank\\\" rel=\\\"noopener noreferrer\\\"', modal)
        self.assertIn("Fechar", modal)

    def test_compiled_plugin_jar_contains_the_updated_renderer(self) -> None:
        with zipfile.ZipFile(PLUGIN_JAR) as jar:
            manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
            self.assertIn("Implementation-Version: 2.5.5-beta", manifest)
            self.assertIn("iped/viewers/HtmlLinkViewer.class", jar.namelist())
            self.assertIn("iped/viewers/VerifierIntegrity.class", jar.namelist())


if __name__ == "__main__":
    unittest.main(verbosity=2)
