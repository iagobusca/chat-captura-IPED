import re
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
        self.assertIn("hasSelectableNew |= job.valid && job.htmlMessageCapture", VIEWER)
        self.assertIn("boolean resetAvailable = newAvailable || (legacyAvailable && migrateLegacyToHtml)", VIEWER)

    def test_worker_reapplies_options_after_manifest_reload(self):
        reload_pos = VIEWER.index("loadRecaptureJobs(outputRoot, java.util.Collections.singletonList(captureIds.get(i)))")
        migrate_pos = VIEWER.index("job.migrateLegacyToHtml = migrateLegacyToHtml && job.legacyCapture", reload_pos)
        reset_pos = VIEWER.index("job.resetLegacyRecords = resetLegacyRecords && job.targetHtmlMessageCapture()", migrate_pos)
        self.assertLess(reload_pos, migrate_pos)
        self.assertLess(migrate_pos, reset_pos)

    def test_migration_bypasses_legacy_skip_and_uses_html_generator(self):
        self.assertIn("!job.resetLegacyRecords && !job.migrateLegacyToHtml && tryMigrateOrSkipCapture(job", VIEWER)
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
        self.assertIn("CaptureIntegrity.safeRegularFileSize(candidate.path) != declaredLength", VIEWER)
        self.assertIn("CaptureIntegrity.sha256(candidate.path).equalsIgnoreCase(expectedSha256)", VIEWER)
        self.assertIn("CaptureIntegrity.sha256(temporary).equalsIgnoreCase(expectedSha256)", VIEWER)
        self.assertIn("StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS", VIEWER)

    def test_verifier_checks_migration_manifest_file_and_integrity_inventory(self):
        self.assertIn("verifyHistory(c,integrity)", VERIFIER)
        self.assertIn("validateMigrationIntegrity(c,format,integrity)", VERIFIER)
        self.assertIn("migration.json não está vinculado ao inventário", VERIFIER)
        self.assertIn("legacy-annotations.json não está vinculado à cadeia", VERIFIER)

    def test_clean_mode_deletes_root_integrity_reports_without_creating_backups(self):
        self.assertNotIn("archiveStaleRootIntegrityReports", VIEWER)
        self.assertNotIn('resolve("_backup_integridade_"', VIEWER)
        self.assertIn("if (resetLegacyRecords && replaced > 0) deleteRootIntegrityReportsIfExists(outputRoot);", VIEWER)
        self.assertIn('Path reportsDir = outputRoot.resolve("integridade");', VIEWER)
        self.assertIn('Path reportHtml = outputRoot.resolve("integridade.html");', VIEWER)
        self.assertIn("deleteDirectoryIfExists(reportsDir)", VIEWER)
        self.assertIn("Files.deleteIfExists(reportHtml)", VIEWER)
        self.assertIn('name.startsWith("_backup_integridade_")', VIEWER)

    def test_ui_strings_are_available_in_both_bundles(self):
        for key in ("MigrateLegacyToHtml", "ResetLegacyRecords", "MigrateLegacyNoReferenceWarning", "CensorshipReferenceUnavailable", "CensorshipWouldBeLost", "MigrateExtracting"):
            self.assertIn("ChatCapturePanel." + key + "=", MESSAGES_EN)
            self.assertIn("ChatCapturePanel." + key + "=", MESSAGES_PT)

    def test_all_chat_localization_keys_exist_in_both_bundles(self):
        keys_en = {m.group(1) for m in re.finditer(r"^([^#=\r\n]+?)=", MESSAGES_EN, re.MULTILINE)}
        keys_pt = {m.group(1) for m in re.finditer(r"^([^#=\r\n]+?)=", MESSAGES_PT, re.MULTILINE)}
        referenced = set()
        for java_file in (ROOT / "src").rglob("*.java"):
            content = java_file.read_text(encoding="utf-8")
            for match in re.finditer(r'"((?:Chat[A-Za-z0-9_]*|MenuClass|HtmlLinkViewer)\.[A-Za-z0-9_.]+)"', content):
                referenced.add(match.group(1))
        self.assertGreater(len(referenced), 100)
        self.assertEqual(sorted(referenced - keys_en), [])
        self.assertEqual(sorted(referenced - keys_pt), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
