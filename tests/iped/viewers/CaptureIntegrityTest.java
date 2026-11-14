package iped.viewers;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureIntegrityTest {
    private CaptureIntegrityTest() {
    }

    public static void main(String[] args) throws Exception {
        try {
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
            htmlCaptureReadsAllowUpTo64MiBWhileDefaultRemains16MiB();
            sanitizeMessageFragmentStripsDangerousHtmlAndPreservesTokenText();
            pixelBombPngIsRejectedByBoundedReaderAndThumbnailCreation();
            failedMigrationBeforeBackupPreservesOriginalWhatsappHtml();
            integrityRecordUsesCanonicalFormatV1();
            maliciousCoordinatesRejectedBeforeBackupAndHtmlWrite();
            legacyWhatsappHtmlHasCspNoDoubleEscapeAndNoopener();
            attachmentHandlerOpenAndSetSelectedAreNullSafeAndEscaped();
            canonicalJsonEquivalenceAndVerifierHtmlEscaping();
            System.out.println("CaptureIntegrityTest: PASS");
            javafx.application.Platform.exit();
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                javafx.application.Platform.exit();
            } catch (Throwable ignored) {
            }
            System.exit(1);
        }
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

            boolean rejectedMkdir = false;
            try {
                CaptureIntegrity.createDirectoriesSafe(cap, junction.resolve("nested"));
            } catch (java.io.IOException expected) {
                rejectedMkdir = true;
            }
            check(rejectedMkdir, "createDirectoriesSafe must reject junctions below root");
            check(!Files.exists(outside.resolve("nested")), "createDirectoriesSafe must not create inside junction target");

            boolean rejectedWrite = false;
            try {
                CaptureIntegrity.writeAtomically(cap, junction.resolve("p4.json"), "{}", CaptureIntegrity.MAX_JSON_BYTES);
            } catch (java.io.IOException expected) {
                rejectedWrite = true;
            }
            check(rejectedWrite, "writeAtomically must reject junctions below root");
            check(!Files.exists(outside.resolve("p4.json")), "writeAtomically must not write inside junction target");

            boolean rejectedAudit = false;
            try {
                CaptureIntegrity.appendAudit(junction, new JSONObject().put("action", "evil"));
            } catch (java.io.IOException expected) {
                rejectedAudit = true;
            }
            check(rejectedAudit, "appendAudit must reject junction root");
            check(!Files.exists(outside.resolve(CaptureIntegrity.AUDIT_FILE)),
                    "appendAudit must not write inside junction target");
        } finally {
            Files.deleteIfExists(junction);
            deleteTree(work);
        }
    }

    private static void htmlCaptureReadsAllowUpTo64MiBWhileDefaultRemains16MiB() throws Exception {
        Path root = Files.createTempDirectory("capture-integrity-p3-");
        try {
            String largePayload = "{\"payload\":\"" + repeat('Z', 17 * 1024 * 1024) + "\"}";
            Path over16 = root.resolve("messages-17m.json");
            Files.write(over16, largePayload.getBytes(StandardCharsets.UTF_8));

            JSONObject readOk = new JSONObject(
                    CaptureIntegrity.readJsonUtf8Limited(over16, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES));
            equal(17 * 1024 * 1024, readOk.getString("payload").length(), "17 MiB JSON read with 64 MiB limit");

            boolean rejectedByDefault = false;
            try {
                CaptureIntegrity.readJsonUtf8Limited(over16);
            } catch (java.io.IOException expected) {
                rejectedByDefault = true;
            }
            check(rejectedByDefault, "17 MiB JSON must still fail under default 16 MiB metadata cap");

            Path over64 = root.resolve("messages-65m.json");
            try (java.io.OutputStream out = Files.newOutputStream(over64)) {
                out.write("{\"payload\":\"".getBytes(StandardCharsets.UTF_8));
                byte[] chunk = new byte[1024 * 1024];
                java.util.Arrays.fill(chunk, (byte) 'Q');
                for (int i = 0; i < 64; i++) {
                    out.write(chunk);
                }
                out.write("\"}".getBytes(StandardCharsets.UTF_8));
            }
            boolean rejectedOver64 = false;
            try {
                CaptureIntegrity.readJsonUtf8Limited(over64, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES);
            } catch (java.io.IOException expected) {
                rejectedOver64 = true;
            }
            check(rejectedOver64, "> 64 MiB JSON must still fail under MAX_HTML_CAPTURE_BYTES");
        } finally {
            deleteTree(root);
        }
    }

    private static void sanitizeMessageFragmentStripsDangerousHtmlAndPreservesTokenText() {
        String sha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        String relPath = "attachments/" + sha + ".png";
        Map<String, String[]> tokenResources = new HashMap<>();
        tokenResources.put("r0", new String[] { sha, "foto.png", relPath });

        String raw = "<div onclick=\"alert(1)\"><template><img src=\"https://evil.invalid/t.png\"></template>"
                + "<svg onload=alert(1)><script>alert(1)</script></svg>"
                + "<img/onerror=\"alert(2)\" src=\"https://evil.invalid/x.png\" alt=\"ext\">"
                + "<p>Texto com data-capture-link-token=\"r0\" literal</p>"
                + "<a class=\"other\" data-capture-link-token=\"r0\">abrir</a>"
                + "<img data-capture-src-token=\"r0\" alt=\"ok\"></div>";
        String sanitized = HtmlLinkViewer.sanitizeCaptureMessageFragment(
                raw, tokenResources, Collections.emptyMap(), "msg-key-1");

        check(!sanitized.contains("<template") && !sanitized.contains("<svg") && !sanitized.contains("<script"),
                "unsafe subtrees must be stripped: " + sanitized);
        check(!sanitized.contains("onclick") && !sanitized.contains("onerror") && !sanitized.contains("https://"),
                "event handlers and external src must be stripped: " + sanitized);
        check(sanitized.contains("Texto com data-capture-link-token=\"r0\" literal"),
                "literal text containing token marker must remain untouched: " + sanitized);
        check(sanitized.contains("href=\"#preview-" + sha + "\" class=\"capture-media-link\" title=\"foto.png\""),
                "link token attribute must resolve to capture preview link: " + sanitized);
        check(sanitized.contains("<img src=\"" + relPath + "\" alt=\"ok\">"),
                "src token attribute must resolve to contained relative path: " + sanitized);
        check(sanitized.contains("data-capture-message-key=\"msg-key-1\""),
                "root message element must receive data-capture-message-key: " + sanitized);

        Map<String, String> skippedDataUriMap = new HashMap<>();
        String skippedDataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        skippedDataUriMap.put(skippedDataUri, "");
        String rawWithSkipped = "<div class=\"linha\">"
                + "<a data-capture-link-token=\"r1\"><img data-capture-src-token=\"r1\" alt=\"skipped-token\">VID-20251205-WA0003.mp4</a>"
                + "<img src=\"" + skippedDataUri + "\" alt=\"skipped-inline\">"
                + "</div>";
        String sanitizedSkipped = HtmlLinkViewer.sanitizeCaptureMessageFragment(
                rawWithSkipped, tokenResources, skippedDataUriMap, "msg-key-2");
        check(!sanitizedSkipped.contains("#preview-") && !sanitizedSkipped.contains("<img"),
                "skipped oversized/missing attachment tokens and inline data URIs must not leave broken img or preview links: " + sanitizedSkipped);
        check(sanitizedSkipped.contains("VID-20251205-WA0003.mp4"),
                "message text/label for skipped attachment must still be preserved: " + sanitizedSkipped);
    }

    private static void pixelBombPngIsRejectedByBoundedReaderAndThumbnailCreation() throws Exception {
        Path root = Files.createTempDirectory("capture-pixel-bomb-");
        try {
            Path shots = Files.createDirectories(root.resolve("screenshots"));
            Path bomb = shots.resolve("bomb.png");
            byte[] ihdrPayload = ByteBuffer.allocate(13)
                    .putInt(20_000)
                    .putInt(20_000)
                    .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0)
                    .array();
            byte[] pngBomb = new byte[8 + 4 + 4 + 13 + 4];
            System.arraycopy(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, 0, pngBomb, 0, 8);
            ByteBuffer bb = ByteBuffer.wrap(pngBomb, 8, pngBomb.length - 8);
            bb.putInt(13);
            bb.put("IHDR".getBytes(StandardCharsets.US_ASCII));
            bb.put(ihdrPayload);
            bb.putInt(0);
            Files.write(bomb, pngBomb);

            check(HtmlLinkViewer.SensitiveImageMatcher.readBoundedImageFile(bomb) == null,
                    "20000x20000 PNG pixel bomb must return null before pixel allocation");

            Path validImg = shots.resolve("ok.png");
            BufferedImage small = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(small, "png", validImg.toFile());
            check(HtmlLinkViewer.SensitiveImageMatcher.readBoundedImageFile(validImg) != null,
                    "valid 16x16 PNG must still decode via readBoundedImageFile");

            Map<String, HtmlLinkViewer.ExportedFile> exported = new HashMap<>();
            HtmlLinkViewer.ExportedFile bombExport = new HtmlLinkViewer.ExportedFile();
            bombExport.relativePath = "screenshots/bomb.png";
            bombExport.sha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
            exported.put(bombExport.relativePath, bombExport);

            HtmlLinkViewer.ExportedFile okExport = new HtmlLinkViewer.ExportedFile();
            okExport.relativePath = "screenshots/ok.png";
            okExport.sha256 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
            exported.put(okExport.relativePath, okExport);

            HtmlLinkViewer viewer = createHeadlessViewer();
            viewer.createReportThumbnails(root, exported);
            check(bombExport.thumbRelativePath == null, "pixel bomb image must not receive thumbnail");
            check(okExport.thumbRelativePath != null && Files.isRegularFile(root.resolve(okExport.thumbRelativePath)),
                    "valid image alongside pixel bomb must still receive thumbnail");
        } finally {
            deleteTree(root);
        }
    }

    private static void failedMigrationBeforeBackupPreservesOriginalWhatsappHtml() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path root = Files.createTempDirectory("capture-c1-migration-");
        Path junction = root.resolve("cap-junction");
        try {
            Path realCap = Files.createDirectories(root.resolve("real-cap"));
            Path origHtml = realCap.resolve("whatsapp.html");
            String sentinel = "<html><body>ORIGINAL_HTML</body></html>";
            Files.writeString(origHtml, sentinel, StandardCharsets.UTF_8);
            Files.writeString(realCap.resolve("manifest.json"), "{\"version\":2}", StandardCharsets.UTF_8);
            Files.writeString(realCap.resolve("whatsapp-coordinates.json"),
                    "{\"frames\":[{\"sequence\":1,\"image\":\"screenshots/frame1.png\",\"width\":100,\"height\":100,\"blocks\":[]}]}",
                    StandardCharsets.UTF_8);

            Process p = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                    junction.toAbsolutePath().toString(), realCap.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            if (p.waitFor() != 0) {
                throw new AssertionError("mklink /J failed");
            }

            HtmlLinkViewer.RecaptureJob job = new HtmlLinkViewer.RecaptureJob();
            job.folderPath = junction;
            job.folder = junction.getFileName().toString();
            HtmlLinkViewer viewer = createHeadlessViewer();
            boolean threw = false;
            try {
                viewer.regenerateWhatsAppHtmlFromSavedCoordinates(job);
            } catch (java.io.IOException expected) {
                threw = true;
            }
            check(threw, "migration through NTFS junction must fail before backup");
            equal(sentinel, Files.readString(origHtml, StandardCharsets.UTF_8),
                    "whatsapp.html must remain intact when failure occurs before backup");
        } finally {
            Files.deleteIfExists(junction);
            deleteTree(root);
        }
    }

    private static HtmlLinkViewer createHeadlessViewer() throws Exception {
        final HtmlLinkViewer[] holder = new HtmlLinkViewer[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            new javafx.embed.swing.JFXPanel();
            holder[0] = new HtmlLinkViewer(null);
        });
        return holder[0];
    }

    private static void integrityRecordUsesCanonicalFormatV1() throws Exception {
        Path root = Files.createTempDirectory("capture-canonical-v1-");
        try {
            Path shots = Files.createDirectories(root.resolve("screenshots"));
            Files.writeString(shots.resolve("frame_0001.png"), "pngdata", StandardCharsets.UTF_8);
            JSONObject record = CaptureIntegrity.integrityRecord(root, null, "capture-created", "2.6.6-beta");
            JSONArray events = record.getJSONArray("events");
            equal(1, events.length(), "initial integrity record must have 1 event");
            JSONObject ev = events.getJSONObject(0);
            equal("canonical-v1", ev.getString("recordHashFormat"), "event must declare canonical-v1 format");
            JSONObject evWithoutHash = new JSONObject(ev.toString());
            String recordedHash = evWithoutHash.remove("recordHash").toString();
            String expected = CaptureIntegrity.sha256Text(CaptureIntegrity.canonicalJson(evWithoutHash));
            equal(expected, recordedHash, "recordHash must match canonicalJson SHA-256");
        } finally {
            deleteTree(root);
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void maliciousCoordinatesRejectedBeforeBackupAndHtmlWrite() throws Exception {
        Path root = Files.createTempDirectory("capture-p02-coords-");
        try {
            String originalSentinel = "<html><body>ORIGINAL_SENTINEL</body></html>";
            Files.writeString(root.resolve("whatsapp.html"), originalSentinel, StandardCharsets.UTF_8);
            Files.writeString(root.resolve("manifest.json"), "{\"version\":2}", StandardCharsets.UTF_8);

            String[] badPayloads = new String[] {
                    "{\"frames\":[{\"sequence\":1,\"image\":\"frames/f1.png\",\"width\":100,\"height\":100,"
                            + "\"blocks\":[{\"id\":\"m1\",\"type\":\"file\",\"x\":0,\"y\":0,\"width\":10,\"height\":10,"
                            + "\"file\":\"javascript:alert(1)\",\"text\":\"x\"}]}]}",
                    "{\"frames\":[{\"sequence\":1,\"image\":\"frames/f1.png\",\"width\":100,\"height\":100,\"blocks\":[]}],"
                            + "\"annotations\":{\"enabled\":true,\"items\":[{\"messageId\":\"m1\",\"links\":[{\"chatRefId\":\"ok1\",\"title\":\"Chat\","
                            + "\"captureFolder\":\"../../evil\",\"hasCapture\":true}]}]}}",
                    "{\"frames\":[{\"sequence\":1,\"image\":\"frames/f1.png\",\"width\":100,\"height\":100,\"blocks\":[]}],"
                            + "\"annotations\":{\"enabled\":true,\"items\":[{\"messageId\":\"m1\",\"links\":[{\"chatRefId\":\"sub/dir\",\"title\":\"Chat\","
                            + "\"captureFolder\":\"Cap1\",\"hasCapture\":true}]}]}}"
            };

            HtmlLinkViewer viewer = createHeadlessViewer();
            java.lang.reflect.Method writeMethod = HtmlLinkViewer.class.getDeclaredMethod(
                    "writeWhatsAppHtml", Path.class, Path.class, String.class);
            writeMethod.setAccessible(true);

            for (String payload : badPayloads) {
                Files.writeString(root.resolve("whatsapp-coordinates.json"), payload, StandardCharsets.UTF_8);
                HtmlLinkViewer.RecaptureJob job = new HtmlLinkViewer.RecaptureJob();
                job.folderPath = root;
                job.folder = root.getFileName().toString();
                boolean rejectedMigration = false;
                try {
                    viewer.regenerateWhatsAppHtmlFromSavedCoordinates(job);
                } catch (java.io.IOException expected) {
                    rejectedMigration = true;
                }
                check(rejectedMigration, "malicious coordinates must fail migration before backup");
                equal(originalSentinel, Files.readString(root.resolve("whatsapp.html"), StandardCharsets.UTF_8),
                        "original whatsapp.html must remain intact after rejected coordinates");

                boolean rejectedWrite = false;
                try {
                    writeMethod.invoke(viewer, root, root.resolve("direct.html"), payload);
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    if (expected.getCause() instanceof java.io.IOException) {
                        rejectedWrite = true;
                    } else {
                        throw expected;
                    }
                }
                check(rejectedWrite, "direct writeWhatsAppHtml must also reject malicious coordinates");
                check(!Files.exists(root.resolve("direct.html")), "rejected writeWhatsAppHtml must not emit file");
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void legacyWhatsappHtmlHasCspNoDoubleEscapeAndNoopener() throws Exception {
        Path root = Files.createTempDirectory("capture-p02-p03-html-");
        try {
            String coords = "{\"frames\":[{\"sequence\":1,\"image\":\"frames/f1.png\",\"width\":100,\"height\":100,"
                    + "\"blocks\":[{\"id\":\"m1\",\"type\":\"file\",\"x\":0,\"y\":0,\"width\":10,\"height\":10,\"file\":\"files/doc.pdf\",\"text\":\"Doc\"}]}],"
                    + "\"annotations\":{\"enabled\":true,\"items\":[{\"messageId\":\"m1\",\"links\":[{\"chatRefId\":\"ref1\",\"title\":\"Chat A&B\","
                    + "\"captureFolder\":\"Chat A&B-2026\",\"hasCapture\":true,\"excerptFile\":\"texto.txt\",\"frames\":[]}]}]}}";
            HtmlLinkViewer viewer = createHeadlessViewer();
            java.lang.reflect.Method writeMethod = HtmlLinkViewer.class.getDeclaredMethod(
                    "writeWhatsAppHtml", Path.class, Path.class, String.class);
            writeMethod.setAccessible(true);
            Path htmlFile = root.resolve("whatsapp.html");
            writeMethod.invoke(viewer, root, htmlFile, coords);

            String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
            check(html.contains("<meta http-equiv=\"Content-Security-Policy\""), "whatsapp.html must declare CSP meta");
            check(html.contains("script-src 'sha256-"), "whatsapp.html CSP must restrict script-src by SHA-256");
            check(html.contains("rel=\"noopener noreferrer\""), "target=_blank links in whatsapp.html must include noopener noreferrer");
            check(!html.contains("onclick=\""), "whatsapp.html must not contain inline onclick= attributes");
            check(html.contains("data-avatar=\"data:image/png;base64,"), "AVATAR must be stored in data-avatar attribute");
            check(html.contains("function safeRelPath(p)"), "whatsapp.html must validate dynamic paths with safeRelPath");
            check(!html.contains("'../'+esc(l.captureFolder)"), "excerptHtml must not double-escape base path");
            check(html.contains("var cap=safeRelPath(l.captureFolder)") && html.contains("esc(base+ex)"),
                    "excerptHtml must validate raw base path and escape once");

            // Auxiliary HTML outputs (index.html, relatorio.htm raiz e relatorio.htm da captura)
            Path capDir = Files.createDirectories(root.resolve("cap1"));
            java.lang.reflect.Method interactiveMethod = HtmlLinkViewer.class.getDeclaredMethod(
                    "writeInteractiveCaptureReport", Path.class, Path.class, String.class, java.util.List.class, Map.class);
            interactiveMethod.setAccessible(true);
            Path capReport = capDir.resolve("relatorio.htm");
            interactiveMethod.invoke(viewer, capDir, capReport, "OK", Collections.emptyList(), Collections.emptyMap());
            String capReportHtml = Files.readString(capReport, StandardCharsets.UTF_8);
            check(capReportHtml.contains("<meta http-equiv=\"Content-Security-Policy\"")
                    && capReportHtml.contains("script-src 'sha256-"),
                    "capture relatorio.htm must declare SHA-256 CSP");
            check(!capReportHtml.contains("onclick=\""), "capture relatorio.htm must not contain inline onclick=");

            HtmlLinkViewer.CaptureRootEntry entry = new HtmlLinkViewer.CaptureRootEntry();
            entry.folder = "cap1";
            entry.title = "Chat 1";
            entry.chatId = "123";
            entry.createdAt = "2026-11-13";
            entry.chronologicalSortKey = "2026-11-13";
            entry.folderHash = repeat('a', 64);
            entry.status = "OK";
            entry.linksWhatsapp = "cap1/whatsapp.html";
            entry.linksReport = "cap1/relatorio.htm";
            entry.linksText = "cap1/texto.txt";
            entry.linksManifest = "cap1/manifest.json";

            java.lang.reflect.Method rootIndexMethod = HtmlLinkViewer.class.getDeclaredMethod(
                    "writeCaptureRootIndexHtml", Path.class, Path.class, String.class, java.util.List.class);
            rootIndexMethod.setAccessible(true);
            Path rootIndex = root.resolve("index.html");
            rootIndexMethod.invoke(viewer, rootIndex, root, repeat('b', 64), Collections.singletonList(entry));
            String rootIndexHtml = Files.readString(rootIndex, StandardCharsets.UTF_8);
            check(rootIndexHtml.contains("<meta http-equiv=\"Content-Security-Policy\"")
                    && rootIndexHtml.contains("script-src 'sha256-"),
                    "root index.html must declare SHA-256 CSP");
            check(!rootIndexHtml.contains("onclick=\""), "root index.html must not contain inline onclick=");

            java.lang.reflect.Method rootReportMethod = HtmlLinkViewer.class.getDeclaredMethod(
                    "writeCaptureRootReport", Path.class, Path.class, String.class, java.util.List.class);
            rootReportMethod.setAccessible(true);
            Path rootReport = root.resolve("relatorio.htm");
            rootReportMethod.invoke(viewer, rootReport, root, repeat('b', 64), Collections.singletonList(entry));
            String rootReportHtml = Files.readString(rootReport, StandardCharsets.UTF_8);
            check(rootReportHtml.contains("<meta http-equiv=\"Content-Security-Policy\"")
                    && rootReportHtml.contains("default-src 'none'"),
                    "root relatorio.htm must declare CSP meta");
            check(!rootReportHtml.contains("onclick=\""), "root relatorio.htm must not contain inline onclick=");
        } finally {
            deleteTree(root);
        }
    }

    private static void attachmentHandlerOpenAndSetSelectedAreNullSafeAndEscaped() throws Exception {
        final String trickyHash = "h\"+alert(1)+\\";
        iped.viewers.api.AttachmentSearcher stubSearcher = (iped.viewers.api.AttachmentSearcher) java.lang.reflect.Proxy.newProxyInstance(
                iped.viewers.api.AttachmentSearcher.class.getClassLoader(),
                new Class<?>[] { iped.viewers.api.AttachmentSearcher.class },
                (proxy, method, args) -> {
                    if ("getHash".equals(method.getName())) {
                        return args != null && args.length > 0 && args[0] != null ? trickyHash : null;
                    }
                    if ("isChecked".equals(method.getName()) || "openItem".equals(method.getName())) {
                        return false;
                    }
                    if ("getItems".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    if ("escapeQuery".equals(method.getName())) {
                        return args != null && args.length > 0 && args[0] != null ? args[0].toString() : "";
                    }
                    return null;
                });

        final HtmlLinkViewer[] holder = new HtmlLinkViewer[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            new javafx.embed.swing.JFXPanel();
            holder[0] = new HtmlLinkViewer(stubSearcher);
        });
        HtmlLinkViewer viewer = holder[0];

        java.lang.reflect.Method jsMethod = HtmlLinkViewer.class.getDeclaredMethod("js", String.class);
        jsMethod.setAccessible(true);
        equal("\"h\\\"+alert(1)+\\\\\"", jsMethod.invoke(viewer, trickyHash),
                "js() helper must escape quotes and backslashes for setSelected");

        java.awt.event.AWTEventListener autoCloseDialog = event -> {
            if (event instanceof java.awt.event.WindowEvent
                    && event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                    && event.getSource() instanceof javax.swing.JDialog) {
                ((java.awt.Window) event.getSource()).dispose();
            }
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(autoCloseDialog, java.awt.AWTEvent.WINDOW_EVENT_MASK);
        try {
            HtmlLinkViewer.AttachmentHandler handler = viewer.new AttachmentHandler();
            handler.open(null);
            handler.open("   ");
            handler.open("hash:nao-encontrado");

            java.lang.reflect.Field mediaField = HtmlLinkViewer.class.getDeclaredField("mediaHashesInView");
            mediaField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<String> mediaHashes = (java.util.Set<String>) mediaField.get(viewer);
            mediaHashes.add(trickyHash);

            viewer.setSelected(null, true);

            iped.data.IItemId itemId = (iped.data.IItemId) java.lang.reflect.Proxy.newProxyInstance(
                    iped.data.IItemId.class.getClassLoader(),
                    new Class<?>[] { iped.data.IItemId.class },
                    (proxy, method, args) -> "compareTo".equals(method.getName()) ? 0 : 1);
            viewer.setSelected(itemId, true);

            java.util.concurrent.CountDownLatch fxLatch = new java.util.concurrent.CountDownLatch(1);
            javafx.application.Platform.runLater(fxLatch::countDown);
            check(fxLatch.await(5, java.util.concurrent.TimeUnit.SECONDS), "FX thread should flush setSelected script");
        } finally {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(autoCloseDialog);
        }
    }

    private static void canonicalJsonEquivalenceAndVerifierHtmlEscaping() throws Exception {
        JSONObject left = new JSONObject();
        left.put("sourceType", "legacy");
        left.put("legacyAnnotationsPreserved", true);
        left.put("migratedAt", "2026-11-14T09:00:00Z");

        JSONObject rightSameOrderDiff = new JSONObject();
        rightSameOrderDiff.put("migratedAt", "2026-11-14T09:00:00Z");
        rightSameOrderDiff.put("legacyAnnotationsPreserved", true);
        rightSameOrderDiff.put("sourceType", "legacy");

        JSONObject modified = new JSONObject(rightSameOrderDiff.toString());
        modified.put("legacyAnnotationsPreserved", false);

        equal(CaptureIntegrity.canonicalJson(left), CaptureIntegrity.canonicalJson(rightSameOrderDiff),
                "canonicalJson must normalize key order for equivalent migration records");
        check(!CaptureIntegrity.canonicalJson(left).equals(CaptureIntegrity.canonicalJson(modified)),
                "canonicalJson must detect changed migration field values");

        equal("&lt;img src=x onerror=&#39;alert(1)&#39;&gt;&amp;&quot;",
                VerifierIntegrity.htmlEscape("<img src=x onerror='alert(1)'>&\""),
                "VerifierIntegrity.htmlEscape must escape all HTML metacharacters");
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
