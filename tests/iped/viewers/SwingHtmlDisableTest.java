package iped.viewers;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.Collections;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

/** Unit tests for S4 Swing html.disable guards and tooltip/HTML entity escaping. */
public final class SwingHtmlDisableTest {

    public static void main(String[] args) throws Exception {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", LinkedChatPickerDialog.escapeHtml("&<>\"'"),
                "LinkedChatPickerDialog.escapeHtml must escape & < > \" '");
        assertEquals("&amp;&lt;&gt;&quot;&#39;", ChatCapturePanel.html("&<>\"'"),
                "ChatCapturePanel.html must escape & < > \" '");

        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyChatCapturePanel();
                verifyChatAnnotationActionDialog();
                verifyLinkedChatPickerDialog();
                verifySavedChatPickerDialog();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("SwingHtmlDisableTest: PASS");
    }

    private static void verifyChatCapturePanel() throws Exception {
        ChatCapturePanel panel = new ChatCapturePanel(null);
        Field linkedDestField = ChatCapturePanel.class.getDeclaredField("linkedDestinationLabel");
        linkedDestField.setAccessible(true);
        Object linkedDest = linkedDestField.get(panel);
        verifyNonHtmlLabelsDisabled(panel, linkedDest);

        Field searchListField = ChatCapturePanel.class.getDeclaredField("searchList");
        searchListField.setAccessible(true);
        @SuppressWarnings("unchecked")
        JList<ChatSearchHit> searchList = (JList<ChatSearchHit>) searchListField.get(panel);
        ListCellRenderer<? super ChatSearchHit> renderer = searchList.getCellRenderer();

        ChatSearchHit plainHit = new ChatSearchHit("id1", "", "<html><img src=x>");
        Component compPlain = renderer.getListCellRendererComponent(searchList, plainHit, 0, false, false);
        if (compPlain instanceof javax.swing.JComponent) {
            javax.swing.JComponent jc = (javax.swing.JComponent) compPlain;
            assertTrue(Boolean.TRUE.equals(jc.getClientProperty("html.disable")),
                    "SearchHitRenderer without type label must set html.disable=true");
            assertEquals("<html>&lt;html&gt;&lt;img src=x&gt;</html>", jc.getToolTipText(),
                    "SearchHitRenderer tooltip must escape HTML characters");
        }

        ChatSearchHit typedHit = new ChatSearchHit("id2", "image", "a&b'c\"d<e>f");
        Component compTyped = renderer.getListCellRendererComponent(searchList, typedHit, 1, false, false);
        if (compTyped instanceof javax.swing.JComponent) {
            javax.swing.JComponent jc = (javax.swing.JComponent) compTyped;
            assertTrue(!Boolean.TRUE.equals(jc.getClientProperty("html.disable")),
                    "SearchHitRenderer with typed HTML label enables escaped HTML rendering");
            assertEquals("<html>a&amp;b&#39;c&quot;d&lt;e&gt;f</html>", jc.getToolTipText(),
                    "SearchHitRenderer tooltip must escape all 5 HTML special characters");
        }
    }

    private static void verifyChatAnnotationActionDialog() {
        ChatAnnotation ann = new ChatAnnotation("m1");
        ann.noteAuthor = "<html><b>x</b>";
        ann.noteText = "ok";
        ChatAnnotationActionDialog dialog = new ChatAnnotationActionDialog(null, ann, "<html><script>alert(1)</script>");
        try {
            verifyNonHtmlLabelsDisabled(dialog.getContentPane(), null);
        } finally {
            dialog.dispose();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void verifyLinkedChatPickerDialog() throws Exception {
        LinkedChatPickerDialog dialog = new LinkedChatPickerDialog(null, Collections.emptyList(), "Chat <A>", null);
        try {
            verifyNonHtmlLabelsDisabled(dialog.getContentPane(), null);
            Field availField = LinkedChatPickerDialog.class.getDeclaredField("availableList");
            availField.setAccessible(true);
            JList avail = (JList) availField.get(dialog);
            Component c = avail.getCellRenderer().getListCellRendererComponent(avail, null, 0, false, false);
            assertTrue(c instanceof JCheckBox && Boolean.TRUE.equals(((JCheckBox) c).getClientProperty("html.disable")),
                    "LinkedChatPickerDialog CheckBoxRenderer must disable HTML rendering");
        } finally {
            dialog.dispose();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void verifySavedChatPickerDialog() throws Exception {
        SavedChatPickerDialog dialog = new SavedChatPickerDialog(null, null);
        try {
            verifyNonHtmlLabelsDisabled(dialog.getContentPane(), null);
            Field listField = SavedChatPickerDialog.class.getDeclaredField("list");
            listField.setAccessible(true);
            JList list = (JList) listField.get(dialog);
            Component c = list.getCellRenderer().getListCellRendererComponent(list, "<html><b>bad</b>", 0, false, false);
            assertTrue(c instanceof javax.swing.JComponent
                    && Boolean.TRUE.equals(((javax.swing.JComponent) c).getClientProperty("html.disable")),
                    "SavedChatPickerDialog list renderer must disable HTML rendering");
        } finally {
            dialog.dispose();
        }
    }

    private static void verifyNonHtmlLabelsDisabled(Container root, Object intentionalHtmlComponent) {
        for (Component child : root.getComponents()) {
            if (child != intentionalHtmlComponent && (child instanceof JLabel || child instanceof JCheckBox)) {
                javax.swing.JComponent jc = (javax.swing.JComponent) child;
                assertTrue(Boolean.TRUE.equals(jc.getClientProperty("html.disable")),
                        "Component missing html.disable=true: " + jc.getClass().getSimpleName() + " text=" + child);
            }
            if (child instanceof Container) {
                verifyNonHtmlLabelsDisabled((Container) child, intentionalHtmlComponent);
            }
        }
    }

    private static void assertEquals(String expected, String actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + ": expected <" + expected + "> got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
