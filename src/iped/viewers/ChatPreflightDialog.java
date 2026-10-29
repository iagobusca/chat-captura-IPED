package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import iped.viewers.localization.Messages;

/**
 * Confirmacao do Start com as contagens de Notas e Links.
 *
 * Confirmar descarta o que esta fora do intervalo. Cancelar nao altera nada.
 *
 * Nunca deve ser aberto a partir da thread JavaFX.
 */
class ChatPreflightDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JCheckBox toggle = new JCheckBox();
    private boolean confirmed;

    ChatPreflightDialog(Window owner, AnnotationPreflight preflight, String rangeLabel, String pendingLinkLabel) {
        super(owner, Messages.getString("ChatCapturePreflight.Title"), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(6, 6));
        setPreferredSize(new Dimension(620, 540));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel(Messages.getString("ChatCapturePreflight.Title")); //$NON-NLS-1$
        title.setForeground(new Color(0x8B, 0x1A, 0x1A));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(6, 1, 2, 2));
        form.add(row(Messages.getString("ChatCapturePreflight.NotesCount"), preflight.notesCount())); //$NON-NLS-1$
        form.add(row(Messages.getString("ChatCapturePreflight.LinksCount"), preflight.linkedMessagesCount())); //$NON-NLS-1$
        form.add(row(Messages.getString("ChatCapturePreflight.ChatsCount"), preflight.contactsCount())); //$NON-NLS-1$
        form.add(row(Messages.getString("ChatCapturePreflight.InRange"), preflight.inRangeCount())); //$NON-NLS-1$
        form.add(row(Messages.getString("ChatCapturePreflight.OutOfRange"), preflight.outOfRangeCount())); //$NON-NLS-1$
        form.add(row(Messages.getString("ChatCapturePreflight.Orphans"), preflight.orphanCount())); //$NON-NLS-1$

        JPanel counts = new JPanel(new BorderLayout(4, 4));
        counts.add(form, BorderLayout.NORTH);
        counts.add(new JScrollPane(buildDetail(preflight, rangeLabel, pendingLinkLabel)), BorderLayout.CENTER);

        toggle.setText(Messages.getString("ChatCapturePanel.IncludeAnnotations")); //$NON-NLS-1$
        toggle.setSelected(true);
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        togglePanel.add(toggle);

        JButton start = new JButton(Messages.getString("ChatCapturePreflight.Start")); //$NON-NLS-1$
        JButton cancel = new JButton(Messages.getString("ChatCapturePreflight.Cancel")); //$NON-NLS-1$
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        actions.add(cancel);
        actions.add(start);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(togglePanel, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(counts, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        getRootPane().setDefaultButton(start);
        start.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        // JDialog ignora setPreferredSize: sem pack/setSize a janela abre em 0x0.
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, getWidth()), Math.max(MIN_HEIGHT, getHeight()));
        setLocationRelativeTo(getOwner());
    }

    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 640;

    private static JTextArea buildDetail(AnnotationPreflight preflight, String rangeLabel, String pendingLinkLabel) {
        StringBuilder detail = new StringBuilder();
        if (rangeLabel != null && !rangeLabel.trim().isEmpty()) {
            detail.append(rangeLabel.trim()).append('\n'); //$NON-NLS-1$
        }
        if (pendingLinkLabel != null && !pendingLinkLabel.trim().isEmpty()) {
            detail.append(Messages.getString("ChatCaptureLink.PendingLink")) //$NON-NLS-1$
                    .append(' ').append(pendingLinkLabel.trim()).append('\n'); //$NON-NLS-1$
        }
        if (preflight.hasWarning()) {
            detail.append(Messages.getString("ChatCapturePreflight.WarnOutOfRange")).append("\n\n"); //$NON-NLS-1$
            for (AnnotationPreflight.Item item : preflight.getOutOfRange()) {
                detail.append("msg ").append(item.messageId) //$NON-NLS-1$
                        .append(" [out] ") //$NON-NLS-1$
                        .append(item.summary).append('\n'); //$NON-NLS-1$
            }
        } else if (detail.length() == 0) {
            detail.append(Messages.getString("ChatCapturePreflight.InRange")) //$NON-NLS-1$
                    .append(": ").append(preflight.inRangeCount()).append('\n'); //$NON-NLS-1$
        }
        JTextArea area = new JTextArea(detail.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JPanel row(String label, int value) {
        JPanel panel = new JPanel(new BorderLayout(6, 2));
        JLabel left = new JLabel(label);
        JLabel right = new JLabel(Integer.toString(value));
        right.setFont(right.getFont().deriveFont(Font.BOLD));
        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        panel.setOpaque(false);
        return panel;
    }

    boolean isConfirmed() {
        return confirmed;
    }

    boolean isIncludeEnabled() {
        return toggle.isSelected();
    }
}