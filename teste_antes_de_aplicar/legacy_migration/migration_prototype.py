"""Executable filesystem prototype for SPEC_MIGRACAO_REEXTRACAO_LEGADA_HTML.

This is deliberately isolated from IPED and only operates on temporary test fixtures.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(128 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def classify(manifest: dict, migrate: bool, reset: bool, blur: bool = False) -> str:
    is_html = manifest.get("captureFormat") == "html-message-v1"
    if is_html:
        return "HTML_NORMAL"
    if reset and not migrate:
        reset = False
    if migrate and blur:
        return "BLOCK_BLUR"
    if migrate:
        return "LEGACY_RESET" if reset else "LEGACY_MIGRATE"
    return "LEGACY_SCREENSHOTS"


def index_reusable_attachments(root: Path, max_files: int = 25000,
                               max_file_bytes: int = 8 * 1024 * 1024,
                               max_total_bytes: int = 64 * 1024 * 1024) -> dict[tuple[str, int], list[Path]]:
    """Index only regular files under legacy attachment directories; reject links."""
    index: dict[tuple[str, int], list[Path]] = {}
    total = 0
    count = 0
    root = root.resolve(strict=True)
    for dirname in ("attachments", "anexos"):
        base = root / dirname
        if not base.exists():
            continue
        if base.is_symlink() or not base.is_dir():
            raise ValueError("unsafe attachment directory")
        for path in base.rglob("*"):
            if path.is_symlink():
                raise ValueError("attachment symlink")
            if not path.is_file():
                continue
            resolved = path.resolve(strict=True)
            if root not in resolved.parents:
                raise ValueError("attachment escaped capture root")
            size = path.stat().st_size
            count += 1
            total += size
            if count > max_files or size > max_file_bytes or total > max_total_bytes:
                raise ValueError("attachment budget exceeded")
            digest = sha256(path)
            index.setdefault((digest, size), []).append(path)
    return index


def reuse_or_copy(expected_bytes: bytes, index: dict[tuple[str, int], list[Path]], destination: Path) -> str:
    """Copy only after source hash/size and the copied destination both match."""
    expected_hash = hashlib.sha256(expected_bytes).hexdigest().upper()
    candidate = next(iter(index.get((expected_hash, len(expected_bytes)), [])), None)
    if candidate is not None and sha256(candidate) == expected_hash and candidate.stat().st_size == len(expected_bytes):
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(candidate, destination)
        if destination.is_symlink() or destination.stat().st_size != len(expected_bytes) or sha256(destination) != expected_hash:
            destination.unlink(missing_ok=True)
            raise ValueError("copied attachment failed verification")
        return "reused"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(expected_bytes)
    if sha256(destination) != expected_hash:
        raise ValueError("fresh attachment failed verification")
    return "extracted"


def reconcile(old_messages: list[dict], current_interval: list[dict]) -> dict:
    """Compare only interval records, so edits outside the range are irrelevant."""
    old = {item["key"]: item for item in old_messages}
    current = {item["key"]: item for item in current_interval}
    entries = []
    counts = {"unchanged": 0, "changed": 0, "missingInCurrentSource": 0, "newInInterval": 0}
    for key, item in current.items():
        before = old.get(key)
        if before is None:
            state = "new_in_interval"
        elif before["sha256"] == item["sha256"]:
            state = "unchanged"
        else:
            state = "changed"
        counts[{"unchanged": "unchanged", "changed": "changed", "new_in_interval": "newInInterval"}[state]] += 1
        entries.append({"key": key, "state": state})
    for key in old.keys() - current.keys():
        counts["missingInCurrentSource"] += 1
        entries.append({"key": key, "state": "missing_in_current_source"})
    return {"entries": entries, "counts": counts,
            "unchanged": not any(counts[name] for name in ("changed", "missingInCurrentSource", "newInInterval"))}


def validate_candidate(candidate: Path, expected_conversation: str) -> None:
    required = ("manifest.json", "messages.json", "whatsapp.html")
    if any(not (candidate / name).is_file() for name in required):
        raise ValueError("candidate artifact missing")
    manifest_path = candidate / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("captureFormat") != "html-message-v1" or manifest.get("state") != "complete":
        raise ValueError("candidate format/state invalid")
    if manifest.get("conversationKey") != expected_conversation:
        raise ValueError("conversation mismatch")
    rows = json.loads((candidate / "messages.json").read_text(encoding="utf-8")).get("messages")
    if not rows or len({row["key"] for row in rows}) != len(rows):
        raise ValueError("messages missing/duplicated")
    for path in candidate.rglob("*"):
        if path.is_symlink():
            raise ValueError("candidate symlink")
        if path.is_file():
            path.resolve(strict=True).relative_to(candidate.resolve(strict=True))


def migrate_in_place(capture: Path, builder, *, reset: bool, fail_at: str | None = None) -> None:
    """Stage, validate, and promote a candidate; source remains intact on failure."""
    capture = capture.resolve(strict=True)
    with tempfile.TemporaryDirectory(prefix="legacy-migration-test-") as temp_dir:
        temp_parent = Path(temp_dir)
        staged = temp_parent / "candidate"
        backup = temp_parent / "old"
        old_manifest = json.loads((capture / "manifest.json").read_text(encoding="utf-8"))
        old_manifest_hash = sha256(capture / "manifest.json")
        builder(staged)
        if fail_at == "anchors":
            raise ValueError("anchor resolution failed")
        validate_candidate(staged, "conversation-fixture")
        if fail_at == "metadata":
            raise OSError("metadata write failed")
        new_manifest = json.loads((staged / "manifest.json").read_text(encoding="utf-8"))
        if reset:
            new_manifest.pop("migration", None)
            (staged / "legacy-annotations.json").unlink(missing_ok=True)
            integrity = {"events": [{"action": "capture_created"}]}
        else:
            new_manifest["migration"] = {
                "schemaVersion": 1,
                "action": "legacy_screenshot_capture_migrated_to_html_message_v1",
                "sourceFormat": "legacy-screenshot",
                "previousManifestSha256": old_manifest_hash,
                "previousIntegrityRecordHash": old_manifest.get("integrityRecordHash", ""),
                "anchorsRecoveredFrom": "manifest-and-whatsapp-coordinates",
                "legacyAnnotationsPreserved": (capture / "whatsapp-coordinates.json").exists(),
            }
            if (capture / "whatsapp-coordinates.json").exists():
                (staged / "legacy-annotations.json").write_bytes((capture / "whatsapp-coordinates.json").read_bytes())
            integrity = {"events": old_manifest.get("events", []) + [
                {"action": "legacy_screenshot_capture_migrated_to_html_message_v1"}]}
        (staged / "manifest.json").write_text(json.dumps(new_manifest), encoding="utf-8")
        (staged / "integridade.json").write_text(json.dumps(integrity), encoding="utf-8")
        if fail_at == "promotion":
            raise OSError("promotion failed")
        # Transactional sibling names in production; the fixture uses an isolated temp parent.
        shutil.move(str(capture), str(backup))
        try:
            shutil.move(str(staged), str(capture))
        except OSError:
            shutil.move(str(backup), str(capture))
            raise
        shutil.rmtree(backup)

