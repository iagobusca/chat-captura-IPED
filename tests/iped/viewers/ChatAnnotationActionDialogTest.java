package iped.viewers;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

public final class ChatAnnotationActionDialogTest {
    private ChatAnnotationActionDialogTest() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("ChatAnnotationActionDialogTest: SKIP (headless)");
            return;
        }
        testNegativeConfirmationPreservesNoteAndLinks();
        testPositiveConfirmationRemovesSelectedItem();
        System.out.println("ChatAnnotationActionDialogTest: PASS");
    }

    private static ChatAnnotation sampleAnnotation() {
        ChatAnnotation annotation = new ChatAnnotation("msg-001");
        annotation.noteText = "Nota investigativa preservada";
        annotation.noteAuthor = "Perito";
        LinkedChatRef ref = new LinkedChatRef("ref-1", "Grupo Suspeito");
        ref.captureFolder = "captura-chat-20261113";
        annotation.addLink(ref);
        return annotation;
    }

    private static void testNegativeConfirmationPreservesNoteAndLinks() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ChatAnnotationActionDialog dialog = new ChatAnnotationActionDialog(
                    null, sampleAnnotation(), "Mensagem de teste", key -> false);
            try {
                dialog.noteRemoveButton.doClick();
                check(!dialog.isNoteRemoved(), "noteRemoved deve permanecer false quando usuario nega");
                check("Nota investigativa preservada".equals(dialog.noteArea.getText()),
                        "Texto da nota deve ser preservado apos recusa");
                check(dialog.noteViewButton.isEnabled(), "Botao Ver nota deve permanecer habilitado");
                check(dialog.noteRemoveButton.isEnabled(), "Botao Excluir nota deve permanecer habilitado");

                dialog.linksRemoveButton.doClick();
                check(!dialog.isLinksRemoved(), "linksRemoved deve permanecer false quando usuario nega");
                check(!dialog.linksArea.getText().isEmpty(), "Lista de chats deve ser preservada apos recusa");
                check(dialog.linksViewButton.isEnabled(), "Botao Ver chats deve permanecer habilitado");
                check(dialog.linksRemoveButton.isEnabled(), "Botao Excluir chats deve permanecer habilitado");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                dialog.dispose();
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    private static void testPositiveConfirmationRemovesSelectedItem() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ChatAnnotationActionDialog dialog = new ChatAnnotationActionDialog(
                    null, sampleAnnotation(), "Mensagem de teste", key -> true);
            try {
                dialog.noteRemoveButton.doClick();
                check(dialog.isNoteRemoved(), "noteRemoved deve ser true apos confirmacao");
                check(dialog.noteArea.getText().isEmpty(), "Texto da nota deve ser limpo apos confirmacao");
                check(!dialog.noteViewButton.isEnabled(), "Botao Ver nota deve ser desabilitado");
                check(!dialog.noteRemoveButton.isEnabled(), "Botao Excluir nota deve ser desabilitado");

                check(!dialog.isLinksRemoved(), "linksRemoved nao deve mudar ao excluir apenas a nota");
                dialog.linksRemoveButton.doClick();
                check(dialog.isLinksRemoved(), "linksRemoved deve ser true apos confirmacao");
                check(dialog.linksArea.getText().isEmpty(), "Lista de chats deve ser limpa apos confirmacao");
                check(!dialog.linksViewButton.isEnabled(), "Botao Ver chats deve ser desabilitado");
                check(!dialog.linksRemoveButton.isEnabled(), "Botao Excluir chats deve ser desabilitado");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                dialog.dispose();
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
