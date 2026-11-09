import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEWER = (ROOT / "src/iped/viewers/HtmlLinkViewer.java").read_text(encoding="utf-8")
VERIFIER = (ROOT / "verificador-integridade/src/IntegrityVerifier.java").read_text(encoding="utf-8")
MESSAGES_EN = (ROOT / "instalar_captura_chat_zap/iped-viewer-messages.properties").read_text(encoding="utf-8")
MESSAGES_PT = (ROOT / "instalar_captura_chat_zap/iped-viewer-messages_pt_BR.properties").read_text(encoding="utf-8")


class ProductionMigrationContractTests(unittest.TestCase):
    def test_both_toggles_are_persistent_and_dependent(self):
        self.assertIn("recaptureLegacyMigrationToggle", VIEWER)
        self.assertIn("recaptureLegacyResetToggle", VIEWER)
        self.assertIn("migrateLegacyToHtml = recaptureLegacyMigrationToggle != null && recaptureLegacyMigrationToggle.isSelected()", VIEWER)
        self.assertIn("resetLegacyRecords = recaptureLegacyResetToggle != null && recaptureLegacyResetToggle.isSelected()", VIEWER)
        self.assertIn("if (!enabled) recaptureLegacyResetToggle.setSelected(false)", VIEWER)

    def test_worker_reapplies_options_after_manifest_reload(self):
        reload_pos = VIEWER.index("loadRecaptureJobs(outputRoot, java.util.Collections.singletonList(captureIds.get(i)))")
        migrate_pos = VIEWER.index("job.migrateLegacyToHtml = migrateLegacyToHtml && job.legacyCapture", reload_pos)
        reset_pos = VIEWER.index("job.resetLegacyRecords = resetLegacyRecords && job.migrateLegacyToHtml", migrate_pos)
        self.assertLess(reload_pos, migrate_pos)
        self.assertLess(migrate_pos, reset_pos)

    def test_migration_bypasses_legacy_skip_and_uses_html_generator(self):
        self.assertIn("!job.migrateLegacyToHtml && tryMigrateOrSkipCapture(job", VIEWER)
        self.assertIn("return htmlMessageCapture || (legacyCapture && migrateLegacyToHtml);", VIEWER)
        self.assertIn("activeRecaptureJob.targetHtmlMessageCapture()", VIEWER)

    def test_candidate_is_validated_before_old_folder_is_renamed(self):
        validate_pos = VIEWER.index("validateHtmlCaptureCandidate(job, result.captureDir)")
        old_rename_pos = VIEWER.index("Files.move(job.folderPath, oldFolder)", validate_pos)
        self.assertLess(validate_pos, old_rename_pos)
        self.assertIn("As âncoras HTML não correspondem", VIEWER)
        self.assertIn("A conversa da candidata HTML diverge", VIEWER)
        self.assertIn("O hash raiz da candidata", VIEWER)

    def test_clean_mode_omits_history_and_reuses_only_verified_files(self):
        self.assertIn("if (!job.resetLegacyRecords)", VIEWER)
        self.assertIn("if (job.resetLegacyRecords)", VIEWER)
        self.assertIn("Files.isSymbolicLink(candidate.path)", VIEWER)
        self.assertIn("Files.size(candidate.path) != declaredLength", VIEWER)
        self.assertIn("CaptureIntegrity.sha256(candidate.path).equalsIgnoreCase(expectedSha256)", VIEWER)
        self.assertIn("CaptureIntegrity.sha256(temporary).equalsIgnoreCase(expectedSha256)", VIEWER)
        self.assertIn("StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS", VIEWER)

    def test_verifier_checks_migration_manifest_file_and_integrity_inventory(self):
        self.assertIn("verifyHistory(c,integrity)", VERIFIER)
        self.assertIn("validateMigrationIntegrity(c,format,integrity)", VERIFIER)
        self.assertIn("migration.json não está vinculado ao inventário", VERIFIER)
        self.assertIn("legacy-annotations.json não está vinculado à cadeia", VERIFIER)

    def test_ui_strings_are_available_in_both_bundles(self):
        for key in ("MigrateLegacyToHtml", "ResetLegacyRecords", "MigrateLegacyBlurUnsupported", "MigrateExtracting"):
            self.assertIn("ChatCapturePanel." + key + "=", MESSAGES_EN)
            self.assertIn("ChatCapturePanel." + key + "=", MESSAGES_PT)


if __name__ == "__main__":
    unittest.main(verbosity=2)
