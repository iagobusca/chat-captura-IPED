package iped.viewers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class SafeNameTest {
    private SafeNameTest() {
    }

    public static void main(String[] args) throws Exception {
        testRejectedNamesReturnEmpty();
        testSanitizationRules();
        testRealFilesystemCreationInGroupDir();
        testCopyFileCreateNewFailsIfExists();
        System.out.println("SafeNameTest: PASS");
    }

    private static void testRejectedNamesReturnEmpty() {
        String[] rejected = new String[] { null, "", "   ", ".", "..", "...", ". . " };
        for (String input : rejected) {
            String result = HtmlLinkViewer.sanitizeFileName(input);
            if (!result.isEmpty()) {
                throw new AssertionError("Expected empty string for input [" + input + "], got [" + result + "]");
            }
        }
    }

    private static void testSanitizationRules() {
        assertEquals("_CON", HtmlLinkViewer.sanitizeFileName("CON"), "CON");
        assertEquals("_con", HtmlLinkViewer.sanitizeFileName("con"), "con");
        assertEquals("_NUL.txt", HtmlLinkViewer.sanitizeFileName("NUL.txt"), "NUL.txt");
        assertEquals("_LPT1.pdf", HtmlLinkViewer.sanitizeFileName("LPT1.pdf"), "LPT1.pdf");
        assertEquals("_COM9.tar.gz", HtmlLinkViewer.sanitizeFileName("COM9.tar.gz"), "COM9.tar.gz");
        assertEquals("CONSOLE.txt", HtmlLinkViewer.sanitizeFileName("CONSOLE.txt"), "CONSOLE.txt");
        assertEquals("file", HtmlLinkViewer.sanitizeFileName("file. "), "trailing dot/space");
        assertEquals("a_b.txt", HtmlLinkViewer.sanitizeFileName("a\nb.txt"), "newline control char");
        assertEquals("a_b.txt", HtmlLinkViewer.sanitizeFileName("a\0b.txt"), "null control char");
        assertEquals("x", HtmlLinkViewer.sanitizeFileName("..\\..\\x"), "relative backslash traversal");
        assertEquals("path", HtmlLinkViewer.sanitizeFileName("C:\\evil\\path"), "Windows drive path");
        assertEquals("report.pdf", HtmlLinkViewer.sanitizeFileName("sub/dir/report.pdf"), "Unix path");

        String longPdf = "a".repeat(296) + ".pdf";
        String sanitizedPdf = HtmlLinkViewer.sanitizeFileName(longPdf);
        if (sanitizedPdf.length() > 180 || !sanitizedPdf.endsWith(".pdf")) {
            throw new AssertionError("Unexpected truncated pdf name: len=" + sanitizedPdf.length() + " val=" + sanitizedPdf);
        }

        String longNoExt = "b".repeat(300);
        String sanitizedNoExt = HtmlLinkViewer.sanitizeFileName(longNoExt);
        if (sanitizedNoExt.length() != 180) {
            throw new AssertionError("Unexpected truncated name length: " + sanitizedNoExt.length());
        }
    }

    private static void testRealFilesystemCreationInGroupDir() throws Exception {
        Path root = Files.createTempDirectory("safe-name-test");
        try {
            Path groupDir = root.resolve("anexos").resolve("outros").toAbsolutePath().normalize();
            Files.createDirectories(groupDir);

            String[] inputs = new String[] {
                ".", "..", "CON", "con", "NUL.txt", "LPT1.pdf", "file. ",
                "a\nb.txt", "a\0b.txt", "..\\..\\x", "C:\\evil\\path", "",
                "a".repeat(296) + ".pdf", "b".repeat(300)
            };
            int index = 0;
            for (String raw : inputs) {
                index++;
                String candidate = HtmlLinkViewer.sanitizeFileName(raw);
                if (candidate.isEmpty()) {
                    candidate = "fallback_" + index;
                }
                Path resolved = CaptureIntegrity.resolveContained(groupDir, candidate);
                if (!resolved.getParent().equals(groupDir)) {
                    throw new AssertionError("Escaped groupDir: " + resolved);
                }
                if (!Files.exists(resolved)) {
                    Files.createFile(resolved);
                }
                if (!Files.isRegularFile(resolved)) {
                    throw new AssertionError("Created path is not a regular file: " + resolved);
                }
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void testCopyFileCreateNewFailsIfExists() throws Exception {
        Path root = Files.createTempDirectory("create-new-test");
        try {
            Path source = root.resolve("source.bin");
            Path target = root.resolve("target.bin");
            Files.write(source, "new-content".getBytes(StandardCharsets.UTF_8));

            HtmlLinkViewer.copyFileCreateNew(source, target);
            String first = Files.readString(target, StandardCharsets.UTF_8);
            if (!"new-content".equals(first)) {
                throw new AssertionError("Unexpected initial copy content: " + first);
            }

            Files.write(source, "overwrite-attempt".getBytes(StandardCharsets.UTF_8));
            boolean rejected = false;
            try {
                HtmlLinkViewer.copyFileCreateNew(source, target);
            } catch (FileAlreadyExistsException expected) {
                rejected = true;
            }
            if (!rejected) {
                throw new AssertionError("Expected FileAlreadyExistsException on duplicate target");
            }
            String after = Files.readString(target, StandardCharsets.UTF_8);
            if (!"new-content".equals(after)) {
                throw new AssertionError("Existing target was modified: " + after);
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Mismatch for " + label + ": expected [" + expected + "], got [" + actual + "]");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
