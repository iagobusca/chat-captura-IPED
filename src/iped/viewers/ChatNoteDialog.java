package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import iped.viewers.localization.Messages;

/**
 * Popup de nota do analista sobre uma mensagem.
 *
 * Nunca deve ser aberto a partir da thread JavaFX: o chamador agenda em
 * {@code SwingUtilities.invokeLater}.
 */
class ChatNoteDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextArea noteArea = new JTextArea();
    private final JTextField authorField = new JTextField(20);
    private final JButton removeButton = new JButton(Messages.getString("ChatNoteDialog.RemoveNote")); //$NON-NLS-1$
    private boolean confirmed;

    ChatNoteDialog(Window owner, String initialNote, String initialAuthor, String messagePreview) {
        super(owner, Messages.getString("ChatNoteDialog.Title"), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(6, 6));
        setPreferredSize(new Dimension(560, 420));

        JPanel form = new JPanel(new GridLayout(3, 1, 4, 4));
        JPanel previewPanel = new JPanel(new BorderLayout(4, 4));
        previewPanel.add(new JLabel(Messages.getString("ChatNoteDialog.Preview")), BorderLayout.NORTH); //$NON-NLS-1$
        JTextArea preview = new JTextArea(messagePreview == null ? "" : messagePreview); //$NON-NLS-1$
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setOpaque(false);
        preview.setRows(3);
        previewPanel.add(new JScrollPane(preview), BorderLayout.CENTER);
        form.add(previewPanel);

        noteArea.setText(initialNote == null ? "" : initialNote); //$NON-NLS-1$
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        JPanel notePanel = new JPanel(new BorderLayout(4, 4));
        notePanel.add(new JLabel(Messages.getString("ChatNoteDialog.Message")), BorderLayout.NORTH); //$NON-NLS-1$
        notePanel.add(new JScrollPane(noteArea), BorderLayout.CENTER);
        form.add(notePanel);

        JPanel authorPanel = new JPanel(new BorderLayout(4, 4));
        authorPanel.add(new JLabel(Messages.getString("ChatNoteDialog.Author")), BorderLayout.WEST); //$NON-NLS-1$
        authorField.setText(initialAuthor == null ? "" : initialAuthor); //$NON-NLS-1$
        authorPanel.add(authorField, BorderLayout.CENTER);
        form.add(authorPanel);
        add(form, BorderLayout.CENTER);

        boolean hadNote = initialNote != null && !initialNote.trim().isEmpty();
        removeButton.setEnabled(hadNote);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton save = new JButton(Messages.getString("ChatNoteDialog.Save")); //$NON-NLS-1$
        JButton cancel = new JButton(Messages.getString("ChatNoteDialog.Cancel")); //$NON-NLS-1$
        actions.add(removeButton);
        actions.add(cancel);
        actions.add(save);
        add(actions, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(save);
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        save.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        removeButton.addActionListener(e -> {
            confirmed = true;
            noteArea.setText(""); //$NON-NLS-1$
            dispose();
        });

        // JDialog ignora setPreferredSize: sem pack/setSize a janela abre em 0x0.
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, getWidth()), Math.max(MIN_HEIGHT, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 640;

    boolean isConfirmed() {
        return confirmed;
    }

    String getNoteText() {
        return noteArea.getText() == null ? "" : noteArea.getText().trim(); //$NON-NLS-1$
    }

    String getNoteAuthor() {
        return authorField.getText() == null ? "" : authorField.getText().trim(); //$NON-NLS-1$
    }
}