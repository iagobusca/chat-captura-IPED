package iped.viewers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureIntegrityTest {
    private CaptureIntegrityTest() {
    }

    public static void main(String[] args) throws Exception {
        signaturesAreDeterministicAndExcludeAuxiliaryTranscript();
        signaturesChangeWhenIdentityFieldsChange();
        inventoryDetectsMissingAndModifiedFiles();
        unsafePathsAreRejected();
        frameSignaturesTrackSequenceAndCompleteness();
        auditLogIsExcludedFromArtifactInventory();
        System.out.println("CaptureIntegrityTest: PASS");
    }

    private static void signaturesAreDeterministicAndExcludeAuxiliaryTranscript() throws Exception {
        JSONObject first = CaptureIntegrity.messageSignature("m1", "message", "texto original",
                "2026-10-30 12:00:00", 1, Collections.<String>emptyList(), "transcricao A");
        JSONObject second = CaptureIntegrity.messageSignature("m1", "message", "texto original",
                "2026-10-30 12:00:00", 1, Collections.<String>emptyList(), "transcricao B");
        equal(first.getString("identitySha256"), second.getString("identitySha256"), "transcription is auxiliary");
        equal("auxiliary-ai-transcription", first.getJSONObject("transcription").getString("kind"), "transcription label");
        check(first.getBoolean("complete"), "text message signature should be complete");
    }

    private static void signaturesChangeWhenIdentityFieldsChange() throws Exception {
        JSONObject first = CaptureIntegrity.messageSignature("m1", "message", "texto", "2026-10-30 12:00:00",
                1, Collections.<String>emptyList(), null);
        JSONObject reordered = CaptureIntegrity.messageSignature("m1", "message", "texto", "2026-10-30 12:00:00",
                2, Collections.<String>emptyList(), null);
        check(!first.getString("identitySha256").equals(reordered.getString("identitySha256")),
                "message position must affect identity signature");
        JSONObject audio = CaptureIntegrity.messageSignature("a1", "audio", "", "2026-10-30 12:00:01",
                3, Collections.<String>emptyList(), null);
        check(!audio.getBoolean("complete"), "audio without original media hash must be incomplete");
    }

    private static void inventoryDetectsMissingAndModifiedFiles() throws Exception {
        Path root = Files.createTempDirectory("capture-integrity-test");
        try {
            Path same = root.resolve("same.txt");
            Path changed = root.resolve("changed.txt");
            Path missing = root.resolve("missing.txt");
            Files.write(same, "same".getBytes(StandardCharsets.UTF_8));
            Files.write(changed, "before".getBytes(StandardCharsets.UTF_8));
            Files.write(missing, "gone".getBytes(StandardCharsets.UTF_8));
            JSONArray inventory = CaptureIntegrity.artifactInventory(root);
            Files.write(changed, "after".getBytes(StandardCharsets.UTF_8));
            Files.delete(missing);
            JSONArray results = CaptureIntegrity.compareInventory(root, inventory);
            boolean match = false;
            boolean mismatch = false;
            boolean absent = false;
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                String status = result.getString("status");
                match |= "match".equals(status);
                mismatch |= "mismatch".equals(status);
                absent |= "missing".equals(status);
            }
            check(match && mismatch && absent, "inventory should distinguish match, mismatch, and missing");
        } finally {
            deleteTree(root);
        }
    }

    private static void frameSignaturesTrackSequenceAndCompleteness() throws Exception {
        JSONArray messages = new JSONArray().put(CaptureIntegrity.messageSignature("m1", "message", "hello", "", 0,
                Collections.<String>emptyList(), null));
        JSONObject first = CaptureIntegrity.frameSignature(1, messages);
        JSONObject second = CaptureIntegrity.frameSignature(2, messages);
        check(first.getBoolean("complete"), "frame with complete message signature should be complete");
        check(!first.getString("identitySha256").equals(second.getString("identitySha256")),
                "frame sequence must contribute to the frame signature");
        JSONArray incompleteMessage = new JSONArray().put(CaptureIntegrity.messageSignature("a1", "audio", "", "", 0,
                Collections.<String>emptyList(), null));
        check(!CaptureIntegrity.frameSignature(1, incompleteMessage).getBoolean("complete"),
                "frame with unverified audio must be incomplete");
    }

    private static void auditLogIsExcludedFromArtifactInventory() throws Exception {
        Path root = Files.createTempDirectory("capture-audit-test");
        try {
            Files.write(root.resolve("report.html"), "report".getBytes(StandardCharsets.UTF_8));
            CaptureIntegrity.appendAudit(root, new JSONObject().put("action", "test"));
            JSONArray inventory = CaptureIntegrity.artifactInventory(root);
            equal(1, inventory.length(), "audit log must not create a self-referential artifact entry");
            check(Files.readString(root.resolve(CaptureIntegrity.AUDIT_FILE), StandardCharsets.UTF_8).contains("test"),
                    "audit record should be persisted");
        } finally {
            deleteTree(root);
        }
    }
    private static void unsafePathsAreRejected() throws Exception {
        Path root = Files.createTempDirectory("capture-path-test");
        try {
            boolean rejected = false;
            try {
                CaptureIntegrity.resolveContained(root, "../outside.txt");
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected, "path traversal must be rejected");
            rejected = false;
            try {
                CaptureIntegrity.resolveContained(root, "sub/../inside.txt");
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected, "parent components must be rejected even when normalized inside root");
        } finally {
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
