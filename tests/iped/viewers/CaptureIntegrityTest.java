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
        longPathsAreRejected();
        deeplyNestedJsonIsRejected();
        oversizedMetadataPreservesPreviousFile();
        frameSignaturesTrackSequenceAndCompleteness();
        auditLogIsExcludedFromArtifactInventory();
        junctionsAndReparsePointsInsideCaptureAreRejected();
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
            for (String unsafe : new String[] { root.resolve("outside.txt").toString(), "C:\\outside.txt", "\\\\server\\share\\outside.txt", "." }) {
                rejected = false;
                try {
                    CaptureIntegrity.resolveContained(root, unsafe);
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                check(rejected, "absolute, UNC, drive, and ambiguous dot paths must be rejected: " + unsafe);
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void longPathsAreRejected() throws Exception {
        Path root = Files.createTempDirectory("capture-long-path-test");
        try {
            boolean rejected = false;
            try {
                CaptureIntegrity.resolveContained(root, repeat('a', CaptureIntegrity.MAX_RELATIVE_PATH_CHARS + 1));
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected, "relative paths over the configured limit must be rejected");
        } finally {
            deleteTree(root);
        }
    }

    private static void deeplyNestedJsonIsRejected() throws Exception {
        Path root = Files.createTempDirectory("capture-json-depth-test");
        Path json = root.resolve("manifest.json");
        try {
            String nested = repeat('[', 65) + "0" + repeat(']', 65);
            Files.write(json, ("{\"nested\":" + nested + "}").getBytes(StandardCharsets.UTF_8));
            boolean rejected = false;
            try {
                CaptureIntegrity.readJsonUtf8Limited(json);
            } catch (java.io.IOException expected) {
                rejected = expected.getMessage().contains("depth");
            }
            check(rejected, "JSON nesting beyond the configured limit must fail before parsing");
        } finally {
            deleteTree(root);
        }
    }

    private static void oversizedMetadataPreservesPreviousFile() throws Exception {
        Path root = Files.createTempDirectory("capture-metadata-limit-test");
        Path target = root.resolve("integridade.json");
        try {
            Files.write(target, "previous-record".getBytes(StandardCharsets.UTF_8));
            boolean rejected = false;
            try {
                CaptureIntegrity.writeAtomically(root, target,
                        repeat('x', (int) CaptureIntegrity.MAX_JSON_BYTES + 1));
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected, "metadata beyond the configured limit must be rejected");
            equal("previous-record", new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                    "rejected metadata must preserve the previous file");
        } finally {
            deleteTree(root);
        }
    }

    private static void junctionsAndReparsePointsInsideCaptureAreRejected() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path work = Files.createTempDirectory("capture-junction-test");
        Path outside = Files.createDirectories(work.resolve("outside"));
        Files.write(outside.resolve("secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
        Path cap = Files.createDirectories(work.resolve("cap"));
        Files.write(cap.resolve("manifest.json"), "{}".getBytes(StandardCharsets.UTF_8));
        Path junction = cap.resolve("linkdir");
        Process p = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                junction.toAbsolutePath().toString(), outside.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        if (p.waitFor() != 0) {
            throw new AssertionError("mklink /J failed in CaptureIntegrityTest");
        }
        try {
            CaptureIntegrity.ensureNoSymbolicLinkComponents(cap, cap);
            boolean rejectedResolve = false;
            try {
                CaptureIntegrity.resolveContained(cap, "linkdir/secret.txt");
            } catch (java.io.IOException expected) {
                rejectedResolve = true;
            }
            check(rejectedResolve, "resolveContained must reject paths traversing an NTFS junction");

            boolean rejectedList = false;
            try {
                CaptureIntegrity.listRegularFilesSafe(cap, 100);
            } catch (java.io.IOException expected) {
                rejectedList = true;
            }
            check(rejectedList, "listRegularFilesSafe must reject an NTFS junction inside capture root");

            boolean rejectedOut = false;
            try {
                CaptureIntegrity.ensureNoSymbolicLinkComponents(cap, junction);
            } catch (java.io.IOException expected) {
                rejectedOut = true;
            }
            check(rejectedOut, "ensureNoSymbolicLinkComponents must reject junctions below root");
        } finally {
            Files.deleteIfExists(junction);
            deleteTree(work);
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
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
