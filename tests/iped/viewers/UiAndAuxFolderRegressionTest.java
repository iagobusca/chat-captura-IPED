package iped.viewers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.SwingUtilities;

public class UiAndAuxFolderRegressionTest {

    public static void main(String[] args) throws Exception {
        testRecaptureAuxiliaryFolderDetection();
        testOutputFolderResolution();
        testEmptyOutputFolderDoesNotInstallVerifierOnRecaptureOrStart();
        testRecapturePaginationExcludesInvalidCaptures();
        System.out.println("UiAndAuxFolderRegressionTest: PASS");
    }

    private static void testRecaptureAuxiliaryFolderDetection() {
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get(".iped-reextract-trash")), "trash folder");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("_backup_integridade_20261113")), "integrity backup");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("cap1.__old_20261113")), "old recapture folder");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("cap1.__reextract_tmp_1")), "reextract tmp");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("cap1.__reextract_failed_1")), "reextract failed");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get(".__capture_staging_deadbeef")), "staging folder");
        assertTrue(HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("cap1.__migration_html_backup")), "migration backup");

        assertTrue(!HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("captura-chat-20261107-134436")), "normal capture");
        assertTrue(!HtmlLinkViewer.isRecaptureAuxiliaryFolder(Paths.get("vinculado")), "vinculado handled separately");
        assertTrue(!HtmlLinkViewer.isRecaptureAuxiliaryFolder(null), "null path");
    }

    private static void testOutputFolderResolution() {
        assertNull(ChatCapturePanel.resolveValidOutputFolder(null), "null outputFolder");
        assertNull(ChatCapturePanel.resolveValidOutputFolder(""), "empty outputFolder");
        assertNull(ChatCapturePanel.resolveValidOutputFolder("   \t\r\n "), "blank outputFolder");
        File valid = ChatCapturePanel.resolveValidOutputFolder("  C:\\temp\\capturas  ");
        assertNotNull(valid, "trimmed valid folder");
        assertEqual(new File("C:\\temp\\capturas").getPath(), valid.getPath(), "trimmed folder path");
    }

    private static void testEmptyOutputFolderDoesNotInstallVerifierOnRecaptureOrStart() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path jarTarget = cwd.resolve("integridade-verifier.jar");
        Path exeTarget = cwd.resolve("integridade-verifier.exe");
        boolean hadJarBefore = Files.exists(jarTarget);
        boolean hadExeBefore = Files.exists(exeTarget);

        SwingUtilities.invokeAndWait(() -> {
            ChatCapturePanel panel = new ChatCapturePanel(null);
            panel.getRecaptureButtonForTest().doClick();
            panel.getStartStopButtonForTest().doClick();
        });

        assertTrue(Files.exists(jarTarget) == hadJarBefore,
                "Empty outputFolder must never drop verifier JAR into current working directory");
        assertTrue(Files.exists(exeTarget) == hadExeBefore,
                "Empty outputFolder must never drop verifier EXE into current working directory");
    }

    private static void testRecapturePaginationExcludesInvalidCaptures() {
        assertTrue(!HtmlLinkViewer.shouldSelectRecaptureCheckBox(false, true, true),
                "Invalid capture checkbox must never render selected even when bulk-all is active");
        assertTrue(HtmlLinkViewer.shouldSelectRecaptureCheckBox(true, true, false),
                "Valid capture checkbox must render selected when bulk-all is active");
        assertTrue(!HtmlLinkViewer.shouldSelectRecaptureCheckBox(true, false, false),
                "Valid unselected capture must not render selected");

        java.util.List<String> all = java.util.Arrays.asList("cap-1", "cap-invalid-1", "cap-2", "cap-invalid-2");
        java.util.Set<String> knownInvalid = new java.util.HashSet<>(java.util.Collections.singletonList("cap-invalid-1"));
        java.util.List<String> collected = HtmlLinkViewer.collectSelectedRecaptureFolders(
                all, java.util.Collections.emptySet(), knownInvalid, true);
        assertTrue(!collected.contains("cap-invalid-1"),
                "Known invalid capture must never be included in selected folders");
        assertTrue(collected.contains("cap-1") && collected.contains("cap-2"),
                "Valid captures must be retained in bulk selection");
    }

    private static void assertEqual(String expected, String actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + ": expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertNull(Object val, String msg) {
        if (val != null) {
            throw new AssertionError(msg + ": expected null but got " + val);
        }
    }

    private static void assertNotNull(Object val, String msg) {
        if (val == null) {
            throw new AssertionError(msg + ": expected non-null");
        }
    }
}
