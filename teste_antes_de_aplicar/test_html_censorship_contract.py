import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VIEWER = (ROOT / "src/iped/viewers/HtmlLinkViewer.java").read_text(encoding="utf-8")
CENSORSHIP = (ROOT / "src/iped/viewers/CaptureCensorship.java").read_text(encoding="utf-8")
MESSAGES_EN = (ROOT / "instalar_captura_chat_zap/iped-viewer-messages.properties").read_text(encoding="latin-1")
MESSAGES_PT = (ROOT / "instalar_captura_chat_zap/iped-viewer-messages_pt_BR.properties").read_text(encoding="latin-1")


class HtmlCensorshipContractTest(unittest.TestCase):
    def test_legacy_blocking_messages_are_removed(self) -> None:
        self.assertNotIn("ainda não é aplicada ao formato html-message-v1", VIEWER)
        self.assertNotIn("MigrateLegacyBlurUnsupported", VIEWER)
        self.assertNotIn("ChatCapturePanel.MigrateLegacyBlurUnsupported=", MESSAGES_EN)
        self.assertNotIn("ChatCapturePanel.MigrateLegacyBlurUnsupported=", MESSAGES_PT)

    def test_html_capture_integrates_censorship_in_both_media_and_resources_loops(self) -> None:
        capture_start = VIEWER.index("private CaptureRunResult runHtmlMessageCapture")
        capture_end = VIEWER.index("private static String sanitizeCaptureThemeCss", capture_start)
        generator = VIEWER[capture_start:capture_end]
        self.assertEqual(2, generator.count("isCensoredAttachment(censor, attachment, relative, staging, censorVerdicts)"))
        self.assertIn("CaptureCensorship.mark(fragment, messageCensoredSha)", generator)
        self.assertIn("page.insert(viewerCssEnd, CaptureCensorship.css(censorBlurPx))", generator)
        self.assertIn('manifest.put("censorship", CaptureCensorship.manifestRecord(', generator)
        self.assertEqual(1, len(re.findall(r"<script>", generator)))
        self.assertEqual(2, len(re.findall(r"<style>", generator)))

    def test_root_and_message_hash_formulas_exclude_censorship(self) -> None:
        msg_start = VIEWER.index("private String htmlMessageHash(")
        msg_end = VIEWER.index("private String htmlCaptureRootHash(", msg_start)
        root_end = VIEWER.index("private static class DecodedHtmlAttachment", msg_end)
        self.assertNotIn("censorship", VIEWER[msg_start:msg_end])
        self.assertNotIn("censorship", VIEWER[msg_end:root_end])

    def test_recapture_checks_presentation_and_prevents_silent_downgrade(self) -> None:
        self.assertIn("isHtmlPresentationUnchanged(job, result.captureDir)", VIEWER)
        self.assertIn("recapture_censorship_presentation_changed", VIEWER)
        self.assertIn("ChatCapturePanel.CensorshipWouldBeLost", VIEWER)
        self.assertIn("ChatCapturePanel.CensorshipReferenceUnavailable", VIEWER)
        self.assertIn("ChatCapturePanel.MigrateLegacyNoReferenceWarning", VIEWER)

    def test_censorship_helper_uses_css_only_without_scripts(self) -> None:
        self.assertIn("data-capture-censored", CENSORSHIP)
        self.assertIn("capture-censored", CENSORSHIP)
        self.assertNotIn("<script", CENSORSHIP.lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
