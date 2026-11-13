package iped.viewers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
            testImagePreValidationAndJavaDecode(root);
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

    private static void testImagePreValidationAndJavaDecode(Path root) throws Exception {
        // 1. Over-32 MiB rejection (bytes and File)
        byte[] oversized = new byte[HtmlLinkViewer.SensitiveImageMatcher.MAX_SENSITIVE_IMAGE_BYTES + 1];
        byte[] pngMagic = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        System.arraycopy(pngMagic, 0, oversized, 0, pngMagic.length);
        if (HtmlLinkViewer.SensitiveImageMatcher.detectSupportedImageFormat(oversized) != null
                || HtmlLinkViewer.SensitiveImageMatcher.readValidatedDimensions(oversized) != null
                || HtmlLinkViewer.SensitiveImageMatcher.decodeToGrayMat(oversized) != null) {
            throw new AssertionError("Expected >32 MiB payload to be rejected before decoding");
        }
        Path oversizedFile = root.resolve("oversized.png");
        Files.write(oversizedFile, oversized);
        try {
            if (HtmlLinkViewer.SensitiveImageMatcher.readBoundedImageBytes(oversizedFile.toFile()) != null) {
                throw new AssertionError("Expected >32 MiB file to be rejected by readBoundedImageBytes");
            }
        } finally {
            Files.deleteIfExists(oversizedFile);
        }

        // 2. Magic-byte allowlist rejects fake .png containing JPEG2000 / TIFF / SVG bytes
        byte[] jp2Box = new byte[] { 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, (byte) 0x87, 0x0A };
        byte[] jp2Codestream = new byte[] { (byte) 0xFF, 0x4F, (byte) 0xFF, 0x51, 0x00, 0x2F };
        byte[] tiffLe = new byte[] { 0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00 };
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte[] blocked : new byte[][] { jp2Box, jp2Codestream, tiffLe, svg }) {
            if (HtmlLinkViewer.SensitiveImageMatcher.detectSupportedImageFormat(blocked) != null
                    || HtmlLinkViewer.SensitiveImageMatcher.readValidatedDimensions(blocked) != null
                    || HtmlLinkViewer.SensitiveImageMatcher.decodeToGrayMat(blocked) != null) {
                throw new AssertionError("Unsupported format signature must be rejected before OpenCV");
            }
        }
        Path fakePng = root.resolve("fake-jp2.png");
        Files.write(fakePng, jp2Box);

        // 3. Dimension (>8192) and pixel (>33,554,432) caps reject decompression bombs before full decode
        byte[] tooWidePng = encodeTestImage(new BufferedImage(9000, 1, BufferedImage.TYPE_BYTE_GRAY), "png");
        if (HtmlLinkViewer.SensitiveImageMatcher.detectSupportedImageFormat(tooWidePng)
                != HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.PNG
                || HtmlLinkViewer.SensitiveImageMatcher.readValidatedDimensions(tooWidePng) != null
                || HtmlLinkViewer.SensitiveImageMatcher.decodeToGrayMat(tooWidePng) != null) {
            throw new AssertionError("Expected >8192px PNG to be rejected by dimension pre-validation");
        }
        // WebP VP8X claiming 5000x8000 = 40M pixels (>33,554,432)
        byte[] vp8xTooManyPixels = new byte[30];
        vp8xTooManyPixels[0] = 'R'; vp8xTooManyPixels[1] = 'I'; vp8xTooManyPixels[2] = 'F'; vp8xTooManyPixels[3] = 'F';
        vp8xTooManyPixels[8] = 'W'; vp8xTooManyPixels[9] = 'E'; vp8xTooManyPixels[10] = 'B'; vp8xTooManyPixels[11] = 'P';
        vp8xTooManyPixels[12] = 'V'; vp8xTooManyPixels[13] = 'P'; vp8xTooManyPixels[14] = '8'; vp8xTooManyPixels[15] = 'X';
        int wMinus1 = 5000 - 1;
        int hMinus1 = 8000 - 1;
        vp8xTooManyPixels[24] = (byte) (wMinus1 & 0xFF);
        vp8xTooManyPixels[25] = (byte) ((wMinus1 >> 8) & 0xFF);
        vp8xTooManyPixels[26] = (byte) ((wMinus1 >> 16) & 0xFF);
        vp8xTooManyPixels[27] = (byte) (hMinus1 & 0xFF);
        vp8xTooManyPixels[28] = (byte) ((hMinus1 >> 8) & 0xFF);
        vp8xTooManyPixels[29] = (byte) ((hMinus1 >> 16) & 0xFF);
        if (HtmlLinkViewer.SensitiveImageMatcher.detectSupportedImageFormat(vp8xTooManyPixels)
                != HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.WEBP
                || HtmlLinkViewer.SensitiveImageMatcher.readValidatedDimensions(vp8xTooManyPixels) != null
                || HtmlLinkViewer.SensitiveImageMatcher.decodeToGrayMat(vp8xTooManyPixels) != null) {
            throw new AssertionError("Expected >33.5MP WebP VP8X to be rejected before Imgcodecs.imdecode");
        }

        // 4. Valid PNG/JPEG/BMP/GIF pure-Java TYPE_BYTE_GRAY -> CV_8UC1 Mat conversion + ORB matching
        BufferedImage pattern = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pattern.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 160, 120);
            g.setColor(Color.BLACK);
            for (int x = 8; x < 152; x += 14) {
                for (int y = 8; y < 112; y += 14) {
                    if (((x + y) & 1) == 0) {
                        g.fillRect(x, y, 7, 7);
                    } else {
                        g.drawOval(x, y, 8, 8);
                    }
                }
            }
            g.drawLine(0, 0, 159, 119);
            g.drawLine(159, 0, 0, 119);
        } finally {
            g.dispose();
        }

        String[] formats = new String[] { "png", "jpeg", "bmp", "gif" };
        HtmlLinkViewer.SensitiveImageMatcher.ImageFormat[] expected = new HtmlLinkViewer.SensitiveImageMatcher.ImageFormat[] {
                HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.PNG,
                HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.JPEG,
                HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.BMP,
                HtmlLinkViewer.SensitiveImageMatcher.ImageFormat.GIF
        };
        byte[] pngBytes = null;
        byte[] bmpBytes = null;
        for (int i = 0; i < formats.length; i++) {
            byte[] encoded = encodeTestImage(pattern, formats[i]);
            if (HtmlLinkViewer.SensitiveImageMatcher.detectSupportedImageFormat(encoded) != expected[i]) {
                throw new AssertionError("Unexpected ImageFormat detected for " + formats[i]);
            }
            int[] dims = HtmlLinkViewer.SensitiveImageMatcher.readValidatedDimensions(encoded);
            if (dims == null || dims[0] != 160 || dims[1] != 120) {
                throw new AssertionError("Unexpected dimensions for " + formats[i]);
            }
            Mat grayMat = HtmlLinkViewer.SensitiveImageMatcher.decodeToGrayMat(encoded);
            if (grayMat == null || grayMat.empty() || grayMat.type() != CvType.CV_8UC1
                    || grayMat.channels() != 1 || grayMat.cols() != 160 || grayMat.rows() != 120) {
                if (grayMat != null) {
                    grayMat.release();
                }
                throw new AssertionError("Expected pure-Java CV_8UC1 160x120 Mat for " + formats[i]);
            }
            grayMat.release();
            if (i == 0) {
                pngBytes = encoded;
            } else if (i == 2) {
                bmpBytes = encoded;
            }
        }

        Path safeRefDir = Files.createDirectories(root.resolve("p11-refs"));
        Files.write(safeRefDir.resolve("ref.png"), pngBytes);
        Files.copy(fakePng, safeRefDir.resolve("FakeIgnored.png"));
        try (HtmlLinkViewer.SensitiveImageMatcher matcher = new HtmlLinkViewer.SensitiveImageMatcher(safeRefDir, 75)) {
            if (matcher.referenceImageCount() != 1 || matcher.referenceDescriptorCount() != 1) {
                throw new AssertionError("Expected only the valid PNG reference to load, got count="
                        + matcher.referenceImageCount() + " desc=" + matcher.referenceDescriptorCount());
            }
            if (!matcher.matches(bmpBytes)) {
                throw new AssertionError("Expected valid BMP candidate decoded via Java ImageIO to match PNG reference");
            }
            if (matcher.matches(fakePng.toFile()) || matcher.matches(jp2Box) || matcher.matches(tooWidePng)) {
                throw new AssertionError("Invalid or oversized candidate must not match");
            }
        }
    }

    private static byte[] encodeTestImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, baos)) {
            throw new IOException("No ImageIO writer for " + format);
        }
        return baos.toByteArray();
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
