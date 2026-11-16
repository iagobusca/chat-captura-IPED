package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import iped.viewers.localization.Messages;

/**
 * Acoes disponiveis sobre a anotacao de uma mensagem: ver ou apagar a nota e
 * os chats vinculados.
 *
 * Aberto pelo clique no icone [nota]/[link] exibido no chat do visualizador.
 * Nunca deve ser aberto a partir da thread JavaFX.
 */
class ChatAnnotationActionDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 640;

    private final ChatAnnotation annotation;
    private final Predicate<String> confirmPrompt;
    private boolean noteRemoved;
    private boolean linksRemoved;
    JButton noteViewButton;
    JButton noteRemoveButton;
    JTextArea noteArea;
    JButton linksViewButton;
    JButton linksRemoveButton;
    JTextArea linksArea;

    ChatAnnotationActionDialog(Window owner, ChatAnnotation annotation, String messagePreview) {
        this(owner, annotation, messagePreview, null);
    }

    ChatAnnotationActionDialog(Window owner, ChatAnnotation annotation, String messagePreview,
            Predicate<String> confirmPrompt) {
        super(owner, Messages.getString("ChatAnnotationActions.Title"), ModalityType.APPLICATION_MODAL);
        this.annotation = annotation;
        this.confirmPrompt = confirmPrompt;
        setLayout(new BorderLayout(8, 8));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel center = new JPanel(new GridLayout(2, 1, 8, 8));
        center.add(buildNotePanel());
        center.add(buildLinksPanel());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton close = new JButton(Messages.getString("ChatNoteDialog.Close")); //$NON-NLS-1$
        bottom.add(close);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        JLabel previewLabel = new JLabel(messagePreview == null ? " " : shortText(messagePreview)); //$NON-NLS-1$ //$NON-NLS-2$
        previewLabel.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        add(previewLabel, BorderLayout.NORTH);

        getRootPane().setDefaultButton(close);
        close.addActionListener(e -> dispose());

        // JDialog ignora setPreferredSize: sem pack/setSize a janela abre em 0x0.
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, getWidth()), Math.max(MIN_HEIGHT, getHeight()));
        setLocationRelativeTo(getOwner());
    }

    private JPanel buildNotePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.getString("ChatAnnotationActions.Notes"))); //$NON-NLS-1$
        JTextArea area = new JTextArea(annotation.hasNote() ? annotation.noteText : ""); //$NON-NLS-1$
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        if (annotation.hasNote()) {
            JPanel meta = new JPanel(new BorderLayout(4, 4));
            JLabel authorLabel = new JLabel(Messages.getString("ChatAnnotationActions.Author") + " " //$NON-NLS-1$
                    + (annotation.noteAuthor.isEmpty() ? "-" : annotation.noteAuthor)); //$NON-NLS-1$
            authorLabel.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
            meta.add(authorLabel, BorderLayout.WEST);
            panel.add(meta, BorderLayout.NORTH);
        }
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton view = new JButton(Messages.getString("ChatAnnotationActions.View")); //$NON-NLS-1$
        JButton remove = new JButton(Messages.getString("ChatAnnotationActions.Delete")); //$NON-NLS-1$
        this.noteArea = area;
        this.noteViewButton = view;
        this.noteRemoveButton = remove;
        view.setEnabled(annotation.hasNote());
        remove.setEnabled(annotation.hasNote());
        view.addActionListener(e -> showNote(area.getText()));
        remove.addActionListener(e -> {
            if (confirmAndClear("ChatAnnotationActions.ConfirmDeleteNote", view, remove, area)) { //$NON-NLS-1$
                noteRemoved = true;
            }
        });
        actions.add(view);
        actions.add(remove);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildLinksPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(Messages.getString("ChatAnnotationActions.LinkedChats"))); //$NON-NLS-1$
        StringBuilder text = new StringBuilder();
        for (LinkedChatRef link : annotation.links) {
            if (text.length() > 0) {
                text.append('\n'); //$NON-NLS-1$
            }
            text.append(link.title);
            if (!link.captureFolder.isEmpty()) {
                text.append("  ->  ").append(link.captureFolder); //$NON-NLS-1$
            }
        }
        JTextArea area = new JTextArea(text.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton view = new JButton(Messages.getString("ChatAnnotationActions.View")); //$NON-NLS-1$
        JButton remove = new JButton(Messages.getString("ChatAnnotationActions.Delete")); //$NON-NLS-1$
        this.linksArea = area;
        this.linksViewButton = view;
        this.linksRemoveButton = remove;
        boolean hasLinks = annotation.hasLinks();
        view.setEnabled(hasLinks);
        remove.setEnabled(hasLinks);
        view.addActionListener(e -> showLinkedChats());
        remove.addActionListener(e -> {
            if (confirmAndClear("ChatAnnotationActions.ConfirmDeleteChats", view, remove, area)) { //$NON-NLS-1$
                linksRemoved = true;
            }
        });
        actions.add(view);
        actions.add(remove);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private boolean confirmAndClear(String confirmKey, JButton view, JButton remove, JTextArea area) {
        if (!confirmedDelete(confirmKey)) {
            return false;
        }
        view.setEnabled(false);
        remove.setEnabled(false);
        area.setText(""); //$NON-NLS-1$
        return true;
    }

    private boolean confirmedDelete(String confirmKey) {
        if (confirmPrompt != null) {
            return confirmPrompt.test(confirmKey);
        }
        return javax.swing.JOptionPane.showConfirmDialog(this,
                ChatCapturePanel.plainMessageComponent(Messages.getString(confirmKey)),
                Messages.getString("ChatAnnotationActions.Delete"), //$NON-NLS-1$
                javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION;
    }

    private void showNote(String text) {
        javax.swing.JOptionPane.showMessageDialog(this, ChatCapturePanel.plainMessageComponent(text),
                Messages.getString("ChatAnnotationActions.Notes"), //$NON-NLS-1$
                javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    private void showLinkedChats() {
        StringBuilder text = new StringBuilder();
        for (LinkedChatRef link : annotation.links) {
            if (text.length() > 0) {
                text.append("\n\n"); //$NON-NLS-1$
            }
            text.append(Messages.getString("ChatCaptureLink.Title")).append(": ").append(link.title).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
            text.append(Messages.getString("ChatCaptureLink.OpenChat")).append(": "); //$NON-NLS-1$
            text.append(link.hasChat() ? Messages.getString("ChatAnnotationActions.Available") //$NON-NLS-1$
                    : Messages.getString("ChatAnnotationActions.OpenedByName")); //$NON-NLS-1$
            if (link.hasCapture()) {
                text.append('\n').append(Messages.getString("ChatAnnotationActions.Capture")).append(": ") //$NON-NLS-1$
                        .append(link.captureFolder);
            }
        }
        javax.swing.JOptionPane.showMessageDialog(this, ChatCapturePanel.plainMessageComponent(text.toString()),
                Messages.getString("ChatAnnotationActions.LinkedChats"), javax.swing.JOptionPane.PLAIN_MESSAGE); //$NON-NLS-1$
    }

    private static String shortText(String value) {
        String text = value.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        return text.length() > 160 ? text.substring(0, 160) + "..." : text; //$NON-NLS-1$
    }

    boolean isNoteRemoved() {
        return noteRemoved;
    }

    boolean isLinksRemoved() {
        return linksRemoved;
    }
}