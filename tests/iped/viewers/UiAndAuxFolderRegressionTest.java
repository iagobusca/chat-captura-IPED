package iped.viewers;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

public class UiAndAuxFolderRegressionTest {

    public static void main(String[] args) throws Exception {
        testRecaptureAuxiliaryFolderDetection();
        testOutputFolderResolution();
        testEmptyOutputFolderDoesNotInstallVerifierOnRecaptureOrStart();
        testRecapturePaginationExcludesInvalidCaptures();
        testSidebarScrollEnablesAccessToAllControlsInCompactHeight();
        testRecaptureChatReuseAndLoadGenerationSync();
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

    private static void testSidebarScrollEnablesAccessToAllControlsInCompactHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChatCapturePanel panel = new ChatCapturePanel(null);
            JScrollPane scroll = panel.getSidebarScrollPaneForTest();
            assertNotNull(scroll, "Sidebar JScrollPane must exist");
            assertTrue(scroll.getVerticalScrollBarPolicy() == JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    "Sidebar vertical scrollbar must be AS_NEEDED");
            assertTrue(scroll.getHorizontalScrollBarPolicy() == JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                    "Sidebar horizontal scrollbar must be NEVER");
            assertTrue(scroll.getVerticalScrollBar().getUnitIncrement() >= 16,
                    "Sidebar vertical unit increment must support smooth mouse-wheel scroll");

            Component view = scroll.getViewport().getView();
            assertNotNull(view, "Sidebar viewport view must exist");
            assertTrue(view instanceof Scrollable, "Sidebar viewport view must implement Scrollable");
            Scrollable scrollable = (Scrollable) view;
            assertTrue(scrollable.getScrollableTracksViewportWidth(),
                    "Sidebar view must track viewport width to avoid horizontal clipping");

            Component output = panel.getOutputFolderFieldForTest();
            Component startBtn = panel.getStartStopButtonForTest();
            Component recaptureBtn = panel.getRecaptureButtonForTest();
            assertTrue(SwingUtilities.isDescendingFrom(output, view),
                    "Output folder input must live inside scrollable sidebar view");
            assertTrue(SwingUtilities.isDescendingFrom(startBtn, view),
                    "Start button must live inside scrollable sidebar view");
            assertTrue(SwingUtilities.isDescendingFrom(recaptureBtn, view),
                    "Recapture button must live inside scrollable sidebar view");

            // Compact dock height (e.g. 290x380): view keeps full preferred height and scrolls vertically
            panel.setSize(290, 380);
            layoutRecursively(panel);
            assertTrue(!scrollable.getScrollableTracksViewportHeight(),
                    "Compact height must allow vertical scrolling instead of squishing content");
            assertTrue(view.getHeight() > 380,
                    "Scrollable sidebar view height (" + view.getHeight() + ") must exceed compact viewport height (380)");
            assertTrue(output.getWidth() > 0 && output.getHeight() > 0,
                    "Output folder input must have positive bounds");
            assertTrue(startBtn.getWidth() > 0 && startBtn.getHeight() > 0,
                    "Start button must remain laid out with positive bounds");
            assertTrue(recaptureBtn.getWidth() > 0 && recaptureBtn.getHeight() > 0,
                    "Recapture button must remain laid out with positive bounds");
            java.awt.Point startPos = SwingUtilities.convertPoint(startBtn.getParent(), startBtn.getLocation(), view);
            assertTrue(startPos.y + startBtn.getHeight() <= view.getHeight(),
                    "Start button must remain fully reachable within scrollable view bounds");

            // Tall dock height (e.g. 290x1200): view stretches cleanly to fill viewport
            panel.setSize(290, 1200);
            layoutRecursively(panel);
            assertTrue(scrollable.getScrollableTracksViewportHeight(),
                    "Tall viewport must track height so search list stretches cleanly");
        });
    }

    private static void testRecaptureChatReuseAndLoadGenerationSync() {
        // Same chat already loaded and idle -> must reuse without triggering redundant FileProcessor reload
        assertTrue(HtmlLinkViewer.shouldReuseLoadedRecaptureChat(true, 4L, 4L, true),
                "Loaded matching chat with settled generation and ready DOM must be reused");
        assertTrue(!HtmlLinkViewer.shouldReuseLoadedRecaptureChat(true, 4L, -1L, true),
                "In-flight reload (loadedGen=-1) must NOT be reused prematurely");
        assertTrue(!HtmlLinkViewer.shouldReuseLoadedRecaptureChat(true, 5L, 4L, true),
                "Queued reload (loadedGen < currentGen) must NOT be reused prematurely");
        assertTrue(!HtmlLinkViewer.shouldReuseLoadedRecaptureChat(true, 4L, 4L, false),
                "Unready DOM must NOT be reused");
        assertTrue(!HtmlLinkViewer.shouldReuseLoadedRecaptureChat(false, 4L, 4L, true),
                "Different chat must NOT be reused");
        assertTrue(!HtmlLinkViewer.shouldReuseLoadedRecaptureChat(true, 0L, 0L, true),
                "Zero load generation must NOT be reused");

        // Opening a different chat (or reloading) must wait strictly for a newer generation to finish SUCCEEDED
        assertTrue(!HtmlLinkViewer.isRecaptureLoadCompleted(true, 4L, 4L, 4L, true),
                "Stale generation (currentGen == beforeGen) must NOT satisfy new load completion");
        assertTrue(!HtmlLinkViewer.isRecaptureLoadCompleted(true, 4L, 5L, -1L, true),
                "New load still running (loadedGen=-1) must NOT satisfy completion");
        assertTrue(!HtmlLinkViewer.isRecaptureLoadCompleted(true, 4L, 5L, 4L, true),
                "Previous generation SUCCEEDED (loadedGen=4 < currentGen=5) must NOT satisfy completion");
        assertTrue(!HtmlLinkViewer.isRecaptureLoadCompleted(true, 4L, 5L, 5L, false),
                "DOM not ready must NOT satisfy completion");
        assertTrue(!HtmlLinkViewer.isRecaptureLoadCompleted(false, 4L, 5L, 5L, true),
                "Mismatched chat must NOT satisfy completion");
        assertTrue(HtmlLinkViewer.isRecaptureLoadCompleted(true, 4L, 5L, 5L, true),
                "Settled matching new generation with ready DOM must satisfy completion");
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutRecursively((Container) child);
            }
        }
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
