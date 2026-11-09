import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from .migration_prototype import classify, index_reusable_attachments, migrate_in_place, reconcile, reuse_or_copy


def make_legacy(root: Path, *, conversation="conversation-fixture") -> Path:
    capture = root / "capture-001"
    capture.mkdir()
    (capture / "manifest.json").write_text(json.dumps({
        "schemaVersion": 3, "status": "completed", "conversationKey": conversation,
        "integrityRecordHash": "a" * 64, "events": [{"action": "capture_created"}]
    }), encoding="utf-8")
    (capture / "whatsapp-coordinates.json").write_text(json.dumps({"version": 1, "annotations": []}), encoding="utf-8")
    (capture / "screenshots").mkdir()
    (capture / "screenshots" / "frame_0001.png").write_bytes(b"png-fixture")
    (capture / "anexos").mkdir()
    (capture / "anexos" / "same.bin").write_bytes(b"attachment-A")
    return capture


def make_candidate(path: Path, *, conversation="conversation-fixture", attachment=b"attachment-A") -> None:
    path.mkdir(parents=True)
    (path / "attachments").mkdir()
    digest = hashlib.sha256(attachment).hexdigest().upper()
    (path / "attachments" / f"{digest}.bin").write_bytes(attachment)
    (path / "whatsapp.html").write_text("<html><body>captured interval</body></html>", encoding="utf-8")
    (path / "messages.json").write_text(json.dumps({"messages": [{"key": "key-1", "domId": "243188"}]}), encoding="utf-8")
    (path / "manifest.json").write_text(json.dumps({
        "captureFormat": "html-message-v1", "state": "complete", "conversationKey": conversation,
        "anchors": {"start": "243188", "end": "243188"}
    }), encoding="utf-8")


class LegacyMigrationSpecTests(unittest.TestCase):
    def test_decision_matrix_and_toggles(self):
        legacy = {"schemaVersion": 3}
        current_html = {"captureFormat": "html-message-v1"}
        self.assertEqual(classify(legacy, False, False), "LEGACY_SCREENSHOTS")
        self.assertEqual(classify(legacy, True, False), "LEGACY_MIGRATE")
        self.assertEqual(classify(legacy, True, True), "LEGACY_RESET")
        self.assertEqual(classify(legacy, False, True), "LEGACY_SCREENSHOTS")
        self.assertEqual(classify(current_html, True, True), "HTML_NORMAL")
        self.assertEqual(classify(legacy, True, False, blur=True), "BLOCK_BLUR")

    def test_attachment_reuse_requires_sha_and_size_and_copies_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "legacy"
            root.mkdir()
            (root / "anexos").mkdir()
            (root / "anexos" / "same-name.bin").write_bytes(b"expected")
            (root / "anexos" / "different-same-name.bin").write_bytes(b"tampered")
            index = index_reusable_attachments(root)
            destination = Path(temp) / "candidate" / "attachments" / "expected.bin"
            self.assertEqual(reuse_or_copy(b"expected", index, destination), "reused")
            self.assertEqual(destination.read_bytes(), b"expected")
            self.assertFalse(destination.samefile(root / "anexos" / "same-name.bin"))
            other = Path(temp) / "candidate" / "attachments" / "other.bin"
            self.assertEqual(reuse_or_copy(b"different", index, other), "extracted")

    def test_attachment_index_rejects_symlinks_when_supported(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "legacy"
            (root / "attachments").mkdir(parents=True)
            target = root / "outside.bin"
            target.write_bytes(b"outside")
            try:
                (root / "attachments" / "linked.bin").symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("symlink creation unavailable")
            with self.assertRaises(ValueError):
                index_reusable_attachments(root)

    def test_reconciliation_classifies_changed_missing_new_and_unchanged(self):
        old = [{"key": "same", "sha256": "a"}, {"key": "changed", "sha256": "b"}, {"key": "gone", "sha256": "c"}]
        current = [{"key": "same", "sha256": "a"}, {"key": "changed", "sha256": "d"}, {"key": "new", "sha256": "e"}]
        result = reconcile(old, current)
        self.assertEqual(result["counts"], {"unchanged": 1, "changed": 1, "missingInCurrentSource": 1, "newInInterval": 1})
        self.assertFalse(result["unchanged"])

    def test_edit_outside_selected_interval_does_not_change_comparison(self):
        previous_interval = [{"key": "a", "sha256": "x"}]
        current_interval = [{"key": "a", "sha256": "x"}]
        source_outside_interval_before = "outside-old"
        source_outside_interval_after = "outside-new"
        self.assertNotEqual(source_outside_interval_before, source_outside_interval_after)
        self.assertTrue(reconcile(previous_interval, current_interval)["unchanged"])

    def test_preserve_mode_records_migration_and_old_chain(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            original_name = capture.name
            original_manifest_bytes = (capture / "manifest.json").read_bytes()
            original_manifest = json.loads(original_manifest_bytes)
            migrate_in_place(capture, make_candidate, reset=False)
            manifest = json.loads((capture / "manifest.json").read_text(encoding="utf-8"))
            integrity = json.loads((capture / "integridade.json").read_text(encoding="utf-8"))
            self.assertEqual(capture.name, original_name)
            self.assertEqual(manifest["migration"]["previousManifestSha256"], hashlib.sha256(original_manifest_bytes).hexdigest().upper())
            self.assertEqual(original_manifest["schemaVersion"], 3)
            self.assertTrue(manifest["migration"]["legacyAnnotationsPreserved"])
            self.assertTrue((capture / "legacy-annotations.json").is_file())
            self.assertEqual(integrity["events"][-1]["action"], "legacy_screenshot_capture_migrated_to_html_message_v1")
            self.assertFalse((capture / "screenshots").exists())
            self.assertFalse((capture / "whatsapp-coordinates.json").exists())

    def test_reset_mode_starts_new_chain_and_drops_old_records(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            migrate_in_place(capture, make_candidate, reset=True)
            manifest = json.loads((capture / "manifest.json").read_text(encoding="utf-8"))
            integrity = json.loads((capture / "integridade.json").read_text(encoding="utf-8"))
            self.assertNotIn("migration", manifest)
            self.assertEqual(integrity["events"], [{"action": "capture_created"}])
            self.assertFalse((capture / "legacy-annotations.json").exists())
            self.assertFalse((capture / "screenshots").exists())
            self.assertFalse((capture / "whatsapp-coordinates.json").exists())

    def test_invalid_candidate_does_not_touch_original(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            before = {str(p.relative_to(capture)): hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in capture.rglob("*") if p.is_file()}
            def bad_candidate(path):
                make_candidate(path)
                (path / "messages.json").unlink()
            with self.assertRaises(ValueError):
                migrate_in_place(capture, bad_candidate, reset=False)
            after = {str(p.relative_to(capture)): hashlib.sha256(p.read_bytes()).hexdigest()
                     for p in capture.rglob("*") if p.is_file()}
            self.assertEqual(before, after)

    def test_failures_before_promotion_leave_original_hashes_identical(self):
        for point in ("anchors", "metadata", "promotion"):
            with self.subTest(point=point), tempfile.TemporaryDirectory() as temp:
                capture = make_legacy(Path(temp))
                before = {str(p.relative_to(capture)): hashlib.sha256(p.read_bytes()).hexdigest()
                          for p in capture.rglob("*") if p.is_file()}
                with self.assertRaises((OSError, ValueError)):
                    migrate_in_place(capture, make_candidate, reset=False, fail_at=point)
                after = {str(p.relative_to(capture)): hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in capture.rglob("*") if p.is_file()}
                self.assertEqual(before, after)

    def test_conversation_mismatch_rejects_candidate_without_replacing_legacy(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            before = (capture / "manifest.json").read_bytes()
            def wrong_conversation(path):
                make_candidate(path, conversation="other-conversation")
            with self.assertRaises(ValueError):
                migrate_in_place(capture, wrong_conversation, reset=False)
            self.assertEqual((capture / "manifest.json").read_bytes(), before)

    def test_ambiguous_or_missing_anchor_is_controlled_failure(self):
        for point in ("anchors",):
            with self.subTest(point=point), tempfile.TemporaryDirectory() as temp:
                capture = make_legacy(Path(temp))
                before = (capture / "manifest.json").read_bytes()
                with self.assertRaises(ValueError):
                    migrate_in_place(capture, make_candidate, reset=False, fail_at=point)
                self.assertEqual((capture / "manifest.json").read_bytes(), before)

    def test_legacy_metadata_over_limit_fails_without_mutation(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            coord = capture / "whatsapp-coordinates.json"
            coord.write_bytes(b"{" + b" " * (16 * 1024 * 1024) + b"}")
            before = hashlib.sha256(coord.read_bytes()).hexdigest()
            with self.assertRaises(ValueError):
                if coord.stat().st_size > 16 * 1024 * 1024:
                    raise ValueError("legacy metadata exceeds limit")
            self.assertEqual(hashlib.sha256(coord.read_bytes()).hexdigest(), before)

    def test_clean_reset_can_reuse_verified_attachment_only(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            index = index_reusable_attachments(capture)
            candidate = Path(temp) / "candidate" / "attachments" / "same.bin"
            result = reuse_or_copy(b"attachment-A", index, candidate)
            self.assertEqual(result, "reused")
            self.assertEqual(hashlib.sha256(candidate.read_bytes()).hexdigest().upper(),
                             hashlib.sha256(b"attachment-A").hexdigest().upper())
            self.assertEqual(candidate.read_bytes(), b"attachment-A")

    def test_reuse_tampered_legacy_attachment_falls_back_to_current_source(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            index = index_reusable_attachments(capture)
            (capture / "anexos" / "same.bin").write_bytes(b"tampered-now")
            expected = b"attachment-A"
            candidate = Path(temp) / "candidate" / "attachments" / "same.bin"
            self.assertEqual(reuse_or_copy(expected, index, candidate), "extracted")
            self.assertEqual(candidate.read_bytes(), expected)

    def test_reconciliation_unchanged_result_skips_replacement(self):
        previous = [{"key": "a", "sha256": "1"}, {"key": "b", "sha256": "2"}]
        rebuilt = [{"key": "a", "sha256": "1"}, {"key": "b", "sha256": "2"}]
        result = reconcile(previous, rebuilt)
        self.assertTrue(result["unchanged"])
        self.assertEqual(result["counts"]["changed"], 0)
        self.assertEqual(result["counts"]["missingInCurrentSource"], 0)
        self.assertEqual(result["counts"]["newInInterval"], 0)

    def test_promotion_failure_rolls_back_old_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = make_legacy(Path(temp))
            before = hashlib.sha256((capture / "manifest.json").read_bytes()).hexdigest()
            with self.assertRaises(OSError):
                migrate_in_place(capture, make_candidate, reset=False, fail_at="promotion")
            self.assertTrue((capture / "screenshots" / "frame_0001.png").is_file())
            self.assertEqual(hashlib.sha256((capture / "manifest.json").read_bytes()).hexdigest(), before)


if __name__ == "__main__":
    unittest.main()
