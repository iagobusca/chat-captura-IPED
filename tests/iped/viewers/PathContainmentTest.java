package iped.viewers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Regression tests for S2 path containment and NOFOLLOW_LINKS/reparse-point read guards. */
public final class PathContainmentTest {

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("path-containment-test");
        try {
            testLinkedFramesContainmentAndJunctions(temp);
            testCountRegularFilesRejectsJunction(temp);
            testSavedChatPickerSkipsJunctionCapture(temp);
            System.out.println("PathContainmentTest: PASS");
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void testLinkedFramesContainmentAndJunctions(Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("linked-frames/output"));
        Path outside = Files.createDirectories(temp.resolve("linked-frames/outside"));
        Path outsideScreens = Files.createDirectories(outside.resolve("screenshots"));
        Files.write(outsideScreens.resolve("0001.png"), new byte[] { 1, 2, 3 });

        Path capOk = Files.createDirectories(root.resolve("cap-ok/screenshots"));
        Files.write(capOk.resolve("0002.png"), new byte[] { 4, 5, 6 });

        List<String> valid = HtmlLinkViewer.safeLinkedFramePaths(root, "cap-ok");
        assertTrue(valid.size() == 1 && "screenshots/0002.png".equals(valid.get(0)),
                "Valid contained capture must list safe screenshot: " + valid);

        assertTrue(HtmlLinkViewer.safeLinkedFramePaths(root, "../outside").isEmpty(),
                "Traversal captureFolder must be rejected");
        assertTrue(HtmlLinkViewer.safeLinkedFramePaths(root, outside.toAbsolutePath().toString()).isEmpty(),
                "Absolute captureFolder must be rejected");

        if (isWindows()) {
            Path capJunction = root.resolve("cap-junction");
            createJunction(capJunction, outside);
            assertTrue(HtmlLinkViewer.safeLinkedFramePaths(root, "cap-junction").isEmpty(),
                    "Junction captureFolder must be ignored");

            Path capWithJunctionScreens = Files.createDirectories(root.resolve("cap-screens-junction"));
            createJunction(capWithJunctionScreens.resolve("screenshots"), outsideScreens);
            assertTrue(HtmlLinkViewer.safeLinkedFramePaths(root, "cap-screens-junction").isEmpty(),
                    "Junction screenshots subfolder must be ignored");

            Path rootJunction = temp.resolve("linked-frames/output-junction");
            createJunction(rootJunction, root);
            assertTrue(HtmlLinkViewer.safeLinkedFramePaths(rootJunction, "cap-ok").isEmpty(),
                    "Junction outputRoot must be ignored");
        }
    }

    private static void testCountRegularFilesRejectsJunction(Path temp) throws Exception {
        Path normal = Files.createDirectories(temp.resolve("count-test/normal/screenshots"));
        Files.write(normal.resolve("0001.png"), new byte[] { 1, 2, 3 });
        assertTrue(HtmlLinkViewer.countRegularFiles(normal) == 1,
                "Normal screenshots folder must count 1 regular file");
        assertTrue(HtmlLinkViewer.countRegularFiles(temp.resolve("count-test/missing")) == 0,
                "Missing folder must return 0");

        if (isWindows()) {
            Path outside = Files.createDirectories(temp.resolve("count-test/outside"));
            Files.write(outside.resolve("secret.png"), new byte[] { 9, 9, 9 });

            Path screensWithInnerJunction = Files.createDirectories(temp.resolve("count-test/inner/screenshots"));
            createJunction(screensWithInnerJunction.resolve("linkdir"), outside);
            boolean innerRejected = false;
            try {
                HtmlLinkViewer.countRegularFiles(screensWithInnerJunction);
            } catch (IOException expected) {
                innerRejected = true;
            }
            assertTrue(innerRejected, "countRegularFiles must throw when subdirectory inside screenshots is a junction");

            Path junctionScreenshots = temp.resolve("count-test/direct-junction");
            createJunction(junctionScreenshots, outside);
            boolean directRejected = false;
            try {
                HtmlLinkViewer.countRegularFiles(junctionScreenshots);
            } catch (IOException expected) {
                directRejected = true;
            }
            assertTrue(directRejected, "countRegularFiles must throw when folder itself is a junction");
        }
    }

    private static void testSavedChatPickerSkipsJunctionCapture(Path temp) throws Exception {
        Path pickerRoot = Files.createDirectories(temp.resolve("picker/root"));
        Path outside = Files.createDirectories(temp.resolve("picker/outside-cap"));
        Files.write(outside.resolve("manifest.json"), "{\"title\":\"OutsideSecret\"}".getBytes(StandardCharsets.UTF_8));

        Path validCap = Files.createDirectories(pickerRoot.resolve("cap-valid"));
        Files.write(validCap.resolve("manifest.json"), "{\"title\":\"ValidChat\"}".getBytes(StandardCharsets.UTF_8));

        if (isWindows()) {
            createJunction(pickerRoot.resolve("cap-junction"), outside);
            Path pickerRootJunction = temp.resolve("picker/root-junction");
            createJunction(pickerRootJunction, pickerRoot);
            List<String[]> fromJunctionRoot = SavedChatPickerDialog.scanSafeCaptureEntries(pickerRootJunction.toFile());
            assertTrue(fromJunctionRoot.isEmpty(), "Junction outputRoot must return empty entries in SavedChatPickerDialog");
        }

        List<String[]> entries = SavedChatPickerDialog.scanSafeCaptureEntries(pickerRoot.toFile());
        assertTrue(entries.size() == 1 && "cap-valid".equals(entries.get(0)[0]),
                "SavedChatPickerDialog must include only cap-valid and skip junction captures");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void createJunction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("mklink /J failed: " + new String(output, StandardCharsets.UTF_8));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteQuietly(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                        java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (CaptureIntegrity.isLinkOrReparse(dir)) {
                        Files.deleteIfExists(dir);
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
