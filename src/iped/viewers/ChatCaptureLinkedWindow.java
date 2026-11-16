package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.viewers.api.AttachmentSearcher;
import iped.viewers.localization.Messages;

/**
 * Janela de captura de um chat vinculado.
 *
 * Reaproveita a mesma estrutura da captura normal: o chat abre no visualizador
 * HTML e ao lado fica o {@link ChatCapturePanel}, com pasta de destino, nome custom,
 * pasta de imagens para blur, similaridade, nivel de blur, Start/Stop e Re-Extrair.
 * O menu de botao direito do chat tambem e o mesmo, com adicionar inicio, adicionar
 * final e limpar marcacoes.
 *
 * Nunca deve ser aberta a partir da thread JavaFX.
 */
class ChatCaptureLinkedWindow extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatCaptureLinkedWindow.class);

    static final int MIN_WIDTH = 640;
    static final int MIN_HEIGHT = 640;

    private final HtmlLinkViewer linkedViewer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Thread loadThread;

    ChatCaptureLinkedWindow(Window owner, AttachmentSearcher attachSearcher, IItem linkedItem, String chatTitle,
            File destination, String folderName) {
        super(owner, Messages.getString("ChatCaptureLink.WindowTitle") + " - " + safe(chatTitle), //$NON-NLS-1$
                ModalityType.MODELESS);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        linkedViewer = new HtmlLinkViewer(attachSearcher);
        // Sem escolha de pasta: a saida vai sempre para a pasta "vinculado" da origem.
        linkedViewer.getChatCapturePanel().setLinkedCaptureMode(destination, folderName);
        // Sem Notas/Links aqui: a anotacao pertence a mensagem de origem.
        linkedViewer.setLinkedCaptureViewer();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, linkedViewer.getPanel(),
                linkedViewer.getChatCapturePanel());
        split.setResizeWeight(0.78);
        split.setDividerLocation(820);
        split.setContinuousLayout(true);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(ChatCapturePanel.plainLabel(Messages.getString("ChatCaptureLink.WindowHint")), BorderLayout.WEST); //$NON-NLS-1$
        JButton close = new JButton(Messages.getString("ChatNoteDialog.Close")); //$NON-NLS-1$
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        close.addActionListener(e -> dispose());

        enforceMinimumSize();
        setLocationRelativeTo(getOwner());
        // A janela precisa estar na tela antes do JFXPanel criar o WebView.
        setVisible(true);
        loadLinkedChat(linkedItem);
    }

    private void enforceMinimumSize() {
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, 1180), Math.max(MIN_HEIGHT, 760));
    }

    /**
     * Carrega o chat fora da thread de interface e sem travar: {@code loadFile} agenda
     * o trabalho no EDT, que precisa estar livre para executa-lo.
     */
    private void loadLinkedChat(IItem linkedItem) {
        if (linkedItem == null || closed.get()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(300);
                if (closed.get() || Thread.currentThread().isInterrupted()) {
                    return;
                }
                linkedViewer.loadFile(linkedItem, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!closed.get()) {
                    LOGGER.warn("Unable to load linked chat in capture window", e); //$NON-NLS-1$
                }
            }
        }, "IPED-chat-linked-load"); //$NON-NLS-1$
        thread.setDaemon(true);
        loadThread = thread;
        thread.start();
    }

    @Override
    public void dispose() {
        closed.set(true);
        Thread thread = loadThread;
        loadThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        try {
            if (linkedViewer != null) {
                linkedViewer.dispose();
            }
        } finally {
            super.dispose();
        }
    }

    HtmlLinkViewer getLinkedViewer() {
        return linkedViewer;
    }

    private static String safe(String value) {
        return value == null ? "" : value; //$NON-NLS-1$
    }
}