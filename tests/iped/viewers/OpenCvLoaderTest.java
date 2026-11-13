package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
            System.out.println("OpenCvLoaderTest: PASS");
        } finally {
            System.setProperty("user.dir", originalUserDir);
            deleteTree(root);
        }
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
