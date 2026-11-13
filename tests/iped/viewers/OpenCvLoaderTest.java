package iped.viewers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public final class OpenCvLoaderTest {
    private OpenCvLoaderTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected path to valid opencv_java4120.dll");
        }
        Path validSourceDll = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(validSourceDll)) {
            throw new IllegalStateException("Missing source DLL for test: " + validSourceDll);
        }

        Path root = Files.createTempDirectory("opencv-loader-test");
        String originalUserDir = System.getProperty("user.dir");
        try {
            testValidPluginsDllAccepted(root, validSourceDll);
            testValidRootDllAccepted(root, validSourceDll);
            testTamperedDllRejected(root);
            testUserDirDllIgnored(root, validSourceDll);
            testMatcherCloseAndMatRelease(root, validSourceDll);
            System.out.println("OpenCvLoaderTest: PASS");
        } finally {
            System.setProperty("user.dir", originalUserDir);
            deleteTree(root);
        }
    }

    private static void testMatcherCloseAndMatRelease(Path root, Path validSourceDll) throws Exception {
        Path iped = root.resolve("iped-matcher-lifecycle");
        Path libJar = iped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Path pluginsDll = iped.resolve("plugins").resolve("opencv_java4120.dll");
        Files.createDirectories(libJar.getParent());
        Files.createDirectories(pluginsDll.getParent());
        Files.writeString(libJar, "jar-placeholder");
        Files.copy(validSourceDll, pluginsDll);
        HtmlLinkViewer.SensitiveImageMatcher.loadOpenCv(libJar);

        Path refDir = Files.createDirectories(root.resolve("refs"));
        Path refPng = refDir.resolve("ref.png");
        writeTexturedPng(refPng);
        byte[] refBytes = Files.readAllBytes(refPng);

        try (HtmlLinkViewer.SensitiveImageMatcher matcher = new HtmlLinkViewer.SensitiveImageMatcher(refDir, 80)) {
            if (!matcher.hasReferences() || matcher.referenceDescriptorCount() < 1) {
                throw new AssertionError("Expected ORB reference descriptors to load");
            }
            Mat callerGray = new Mat(64, 64, CvType.CV_8UC1);
            try {
                Mat callerDesc = matcher.descriptorsOf(callerGray);
                if (callerDesc != null) {
                    callerDesc.release();
                }
                if (callerGray.empty()) {
                    throw new AssertionError("Caller single-channel Mat must not be released by descriptorsOf");
                }
            } finally {
                callerGray.release();
            }
            for (int i = 0; i < 20; i++) {
                if (!matcher.matches(refPng.toFile()) || !matcher.matches(refBytes)) {
                    throw new AssertionError("Expected reference image to match itself cleanly across repeated calls");
                }
            }
            matcher.close();
            matcher.close(); // idempotent
            if (matcher.referenceDescriptorCount() != 0 || matcher.matches(refBytes)) {
                throw new AssertionError("Closed SensitiveImageMatcher must clear descriptors and not match afterward");
            }
        }
    }

    private static void writeTexturedPng(Path path) throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 128, 128);
            g.setColor(Color.BLACK);
            for (int y = 8; y < 120; y += 10) {
                for (int x = 8; x < 120; x += 10) {
                    if (((x + y) / 10) % 2 == 0) {
                        g.fillRect(x, y, 6, 6);
                    }
                }
                g.drawLine(0, y, 127, 127 - y);
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", path.toFile());
    }

    private static void testValidPluginsDllAccepted(Path root, Path validSourceDll) throws Exception {
        Path iped = root.resolve("iped-valid-plugins");
        Path libJar = iped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Path pluginsDll = iped.resolve("plugins").resolve("opencv_java4120.dll");
        Files.createDirectories(libJar.getParent());
        Files.createDirectories(pluginsDll.getParent());
        Files.writeString(libJar, "jar-placeholder");
        Files.copy(validSourceDll, pluginsDll);

        List<Path> candidates = HtmlLinkViewer.openCvCandidatePaths(libJar);
        equal(2, candidates.size(), "candidate count");
        equal(pluginsDll.toAbsolutePath().normalize(), candidates.get(0), "plugins candidate");
        equal(iped.resolve("opencv_java4120.dll").toAbsolutePath().normalize(), candidates.get(1), "root candidate");

        Path resolved = HtmlLinkViewer.resolveVerifiedOpenCvDll(libJar);
        equal(pluginsDll.toRealPath(), resolved, "resolved plugins DLL");
    }

    private static void testValidRootDllAccepted(Path root, Path validSourceDll) throws Exception {
        Path iped = root.resolve("iped-valid-root");
        Path libJar = iped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Path rootDll = iped.resolve("opencv_java4120.dll");
        Files.createDirectories(libJar.getParent());
        Files.writeString(libJar, "jar-placeholder");
        Files.copy(validSourceDll, rootDll);

        Path resolved = HtmlLinkViewer.resolveVerifiedOpenCvDll(libJar);
        equal(rootDll.toRealPath(), resolved, "resolved root DLL");
    }

    private static void testTamperedDllRejected(Path root) throws Exception {
        Path iped = root.resolve("iped-tampered");
        Path libJar = iped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Path pluginsDll = iped.resolve("plugins").resolve("opencv_java4120.dll");
        Files.createDirectories(libJar.getParent());
        Files.createDirectories(pluginsDll.getParent());
        Files.writeString(libJar, "jar-placeholder");
        Files.writeString(pluginsDll, "not-a-valid-opencv-dll");

        try {
            HtmlLinkViewer.resolveVerifiedOpenCvDll(libJar);
            throw new AssertionError("Tampered DLL was accepted");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("SHA-256")) {
                throw new AssertionError("Expected SHA-256 rejection, got: " + expected.getMessage());
            }
        }
    }

    private static void testUserDirDllIgnored(Path root, Path validSourceDll) throws Exception {
        Path iped = root.resolve("iped-empty");
        Path libJar = iped.resolve("lib").resolve("iped-viewers-impl-4.3.1.jar");
        Files.createDirectories(libJar.getParent());
        Files.writeString(libJar, "jar-placeholder");

        Path attackerDir = root.resolve("attacker-cwd");
        Files.createDirectories(attackerDir.resolve("plugins"));
        Files.copy(validSourceDll, attackerDir.resolve("opencv_java4120.dll"));
        Files.copy(validSourceDll, attackerDir.resolve("plugins").resolve("opencv_java4120.dll"));
        System.setProperty("user.dir", attackerDir.toString());

        try {
            HtmlLinkViewer.resolveVerifiedOpenCvDll(libJar);
            throw new AssertionError("DLL in user.dir should be ignored");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("nao encontrada")) {
                throw new AssertionError("Unexpected failure when ignoring user.dir: " + expected.getMessage());
            }
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
