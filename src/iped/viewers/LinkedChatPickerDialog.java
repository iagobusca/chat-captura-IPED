package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.function.Supplier;

import iped.viewers.localization.Messages;

/**
 * Popup de vinculacao de CHATS a uma mensagem.
 *
 * Lista os chats do caso (sob demanda, por consulta ao indice) e permite entrada
 * manual. O que se vincula e a conversa, nao uma pessoa.
 *
 * Nunca deve ser aberto a partir da thread JavaFX.
 */
class LinkedChatPickerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Item da lista, desenhado como checkbox para selecao multipla. */
    private static class Row {
        final LinkedChatRef chat;

        Row(LinkedChatRef chat) {
            this.chat = chat;
        }

        @Override
        public String toString() {
            return chat.title;
        }
    }

    private final DefaultListModel<Row> availableModel = new DefaultListModel<>();
    private final DefaultListModel<Row> selectedModel = new DefaultListModel<>();
    private final JList<Row> availableList = new JList<>(availableModel);
    private final JList<Row> selectedList = new JList<>(selectedModel);
    private final JTextField searchField = new JTextField();
    private final JTextField manualField = new JTextField();
    private final JButton openChatButton = new JButton(Messages.getString("ChatCaptureLink.OpenChat")); //$NON-NLS-1$
    private final JButton loadCaseButton = new JButton(Messages.getString("ChatCaptureLink.LoadCase")); //$NON-NLS-1$
    private final JLabel caseStatus = plainLabel(" ");
private final List<LinkedChatRef> allAvailable = new ArrayList<>();
    private final Supplier<List<LinkedChatRef>> caseChatLoader;
    private boolean confirmed;
    private LinkedChatRef openChatTarget;

    LinkedChatPickerDialog(Window owner, List<LinkedChatRef> alreadyLinked, String currentChatTitle,
            Supplier<List<LinkedChatRef>> caseChatLoader) {
        super(owner, Messages.getString("ChatCaptureLink.Title"), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(6, 6));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        final JButton addButton = new JButton(Messages.getString("ChatCaptureLink.AddToMessage")); //$NON-NLS-1$
        getRootPane().setDefaultButton(addButton);
        this.caseChatLoader = caseChatLoader;

        if (currentChatTitle != null && !currentChatTitle.trim().isEmpty()) {
            allAvailable.add(new LinkedChatRef(ChatAnnotationStore.chatRefIdOf(currentChatTitle, ""),
                    currentChatTitle.trim()));
        }

        JPanel left = new JPanel(new BorderLayout(4, 4));
        JPanel leftTop = new JPanel(new GridLayout(3, 1, 4, 4));
        leftTop.add(plainLabel(Messages.getString("ChatCaptureLink.Available"))); //$NON-NLS-1$
        JPanel searchRow = new JPanel(new BorderLayout(4, 4));
        searchRow.add(plainLabel(Messages.getString("ChatCaptureLink.Search")), BorderLayout.WEST); //$NON-NLS-1$
        searchRow.add(searchField, BorderLayout.CENTER);
        leftTop.add(searchRow);
        JPanel caseRow = new JPanel(new BorderLayout(4, 4));
        caseRow.add(loadCaseButton, BorderLayout.WEST);
        caseRow.add(caseStatus, BorderLayout.CENTER);
        leftTop.add(caseRow);
        left.add(leftTop, BorderLayout.NORTH);

        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        availableList.setCellRenderer(new CheckBoxRenderer());
        availableList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = availableList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    toggleRow(availableList.getModel().getElementAt(index));
                }
            }
        });
        left.add(new JScrollPane(availableList), BorderLayout.CENTER);

        JPanel manualRow = new JPanel(new BorderLayout(4, 4));
        manualField.setToolTipText(Messages.getString("ChatCaptureLink.ManualTip")); //$NON-NLS-1$
        manualRow.add(manualField, BorderLayout.CENTER);
        JButton manualAdd = new JButton(Messages.getString("ChatCaptureLink.Add")); //$NON-NLS-1$
        manualRow.add(manualAdd, BorderLayout.EAST);
        JPanel manualPanel = new JPanel(new BorderLayout(4, 4));
        manualPanel.add(manualRow, BorderLayout.CENTER);
        manualPanel.add(plainLabel(Messages.getString("ChatCaptureLink.ManualTip")), BorderLayout.SOUTH); //$NON-NLS-1$
        left.add(manualPanel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(plainLabel(Messages.getString("ChatCaptureLink.Selected")), BorderLayout.NORTH); //$NON-NLS-1$
        selectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectedList.setCellRenderer(new ChatRenderer());
        selectedList.addListSelectionListener(e -> openChatButton.setEnabled(selectedModel.size() == 1));
        right.add(new JScrollPane(selectedList), BorderLayout.CENTER);

        JButton removeOne = new JButton("-"); //$NON-NLS-1$
        JButton removeAll = new JButton(Messages.getString("ChatCaptureLink.ClearAll")); //$NON-NLS-1$
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        rightActions.add(removeOne);
        rightActions.add(removeAll);
        right.add(rightActions, BorderLayout.SOUTH);

        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 8));
        lists.add(left);
        lists.add(right);
        add(lists, BorderLayout.CENTER);

        JButton cancel = new JButton(Messages.getString("ChatNoteDialog.Cancel")); //$NON-NLS-1$
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        actions.add(openChatButton);
        actions.add(cancel);
        actions.add(addButton);
        add(actions, BorderLayout.SOUTH);

        if (alreadyLinked != null) {
            for (LinkedChatRef chat : alreadyLinked) {
                Row row = findOrCreateRow(chat);
                row.chat.sourceName = mergeNonBlank(row.chat.sourceName, chat.sourceName);
                row.chat.chatKey = mergeNonBlank(row.chat.chatKey, chat.chatKey);
                row.chat.origin = mergeNonBlank(row.chat.origin, chat.origin);
                selectedModel.addElement(row);
            }
        }
        rebuildAvailable();
        openChatButton.setEnabled(selectedModel.size() == 1);
        loadCaseButton.setEnabled(caseChatLoader != null);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildAvailable();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildAvailable();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildAvailable();
            }
        });
        manualAdd.addActionListener(e -> addManualChat());
        loadCaseButton.addActionListener(e -> loadCaseChatsAsync());
        addButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        openChatButton.addActionListener(e -> {
            Row row = selectedList.getSelectedValue();
            if (row != null) {
                confirmed = true;
                openChatTarget = row.chat.copy();
                dispose();
            }
        });
        removeOne.addActionListener(e -> {
            int index = selectedList.getSelectedIndex();
            if (index >= 0) {
                selectedModel.remove(index);
                openChatButton.setEnabled(selectedModel.size() == 1);
            }
        });
        removeAll.addActionListener(e -> {
            selectedModel.clear();
            openChatButton.setEnabled(false);
        });

        enforceMinimumSize();
    }

    /**
     * JDialog ignora setPreferredSize: sem pack/setSize a janela abre em 0x0.
     * O dialogo do plugin nunca deve ser menor que 640x640.
     */
    void enforceMinimumSize() {
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, getWidth()), Math.max(MIN_HEIGHT, getHeight()));
        setLocationRelativeTo(getOwner());
    }

    static final int MIN_WIDTH = 640;
    static final int MIN_HEIGHT = 640;

    /**
 * Carrega os chats do caso sob demanda, fora da thread de interface, porque a
 * consulta ao indice pode demorar. A lista volta para a tela na thread de Swing.
 */
    private void loadCaseChatsAsync() {
        if (caseChatLoader == null) {
            caseStatus.setText(Messages.getString("ChatCaptureLink.LoadUnavailable")); //$NON-NLS-1$
            return;
        }
        loadCaseButton.setEnabled(false);
        caseStatus.setText(Messages.getString("ChatCaptureLink.Loading")); //$NON-NLS-1$
        new Thread(() -> {
            List<LinkedChatRef> chats = null;
            String problem = null;
            try {
                chats = caseChatLoader.get();
            } catch (Throwable t) {
                problem = t.toString();
            }
            final List<LinkedChatRef> loaded = chats;
            final String error = problem;
            SwingUtilities.invokeLater(() -> {
                loadCaseButton.setEnabled(true);
                if (error != null) {
                    caseStatus.setText(Messages.getString("ChatCaptureLink.LoadFailed") + " " + error); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                if (loaded == null || loaded.isEmpty()) {
                    caseStatus.setText(Messages.getString("ChatCaptureLink.NoCaseChats")); //$NON-NLS-1$
                    return;
                }
                applyCaseChats(loaded);
            });
        }, "IPED-chat-case-list").start(); //$NON-NLS-1$
    }

    private void applyCaseChats(List<LinkedChatRef> chats) {
        int added = 0;
        for (LinkedChatRef chat : chats) {
            if (chat.title.isEmpty()) {
                continue;
            }
            boolean exists = false;
            for (LinkedChatRef known : allAvailable) {
                if (known.title.equalsIgnoreCase(chat.title)) {
                    // preserva o item ja resolvido para abrir sem nova consulta
                    if (known.item == null && chat.item != null) {
                        known.item = chat.item;
                        known.chatKey = chat.chatKey;
                        known.sourceName = chat.sourceName;
                    }
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                allAvailable.add(chat.copy());
                added++;
            }
        }
        caseStatus.setText(Messages.getString("ChatCaptureLink.CaseChatsLoaded") + " " + added); //$NON-NLS-1$
        rebuildAvailable();
    }

    private void rebuildAvailable() {
        String term = ChatAnnotationStore.normalizeKey(searchField.getText());
        availableModel.clear();
        for (LinkedChatRef chat : allAvailable) {
            boolean matches = term.isEmpty() || ChatAnnotationStore.normalizeKey(chat.title).contains(term);
            if (matches) {
                availableModel.addElement(new Row(chat.copy()));
            }
        }
    }

    private Row findOrCreateRow(LinkedChatRef chat) {
        for (int i = 0; i < availableModel.size(); i++) {
            Row row = availableModel.getElementAt(i);
            if (row.chat.title.equalsIgnoreCase(chat.title)) {
                return row;
            }
        }
        Row row = new Row(chat.copy());
        availableModel.addElement(row);
        return row;
    }

    private boolean isLinked(LinkedChatRef chat) {
        for (int i = 0; i < selectedModel.size(); i++) {
            if (selectedModel.getElementAt(i).chat.title.equalsIgnoreCase(chat.title)) {
                return true;
            }
        }
        return false;
    }

    private void toggleRow(Row row) {
        if (row == null) {
            return;
        }
        if (isLinked(row.chat)) {
            for (int i = 0; i < selectedModel.size(); i++) {
                if (selectedModel.getElementAt(i).chat.title.equalsIgnoreCase(row.chat.title)) {
                    selectedModel.remove(i);
                    break;
                }
            }
        } else {
            selectedModel.addElement(row);
        }
        availableList.repaint();
        selectedList.repaint();
        openChatButton.setEnabled(selectedModel.size() == 1);
    }

    private void addManualChat() {
        String title = manualField.getText() == null ? "" : manualField.getText().trim(); //$NON-NLS-1$
        if (title.isEmpty()) {
            return;
        }
        LinkedChatRef chat = new LinkedChatRef(ChatAnnotationStore.chatRefIdOf(title, ""), title);
        chat.origin = ChatAnnotationStore.ORIGIN_MANUAL;
        if (!isLinked(chat)) {
            selectedModel.addElement(findOrCreateRow(chat));
        }
        manualField.setText(""); //$NON-NLS-1$
        rebuildAvailable();
        openChatButton.setEnabled(selectedModel.size() == 1);
    }

    private static String mergeNonBlank(String base, String value) {
        return base == null || base.trim().isEmpty() ? value : base;
    }

    boolean isConfirmed() {
        return confirmed;
    }

    LinkedChatRef getOpenChatTarget() {
        return openChatTarget;
    }

    List<LinkedChatRef> getSelectedChats() {
        List<LinkedChatRef> out = new ArrayList<>();
        for (int i = 0; i < selectedModel.size(); i++) {
            out.add(selectedModel.getElementAt(i).chat.copy());
        }
        return out;
    }

    private class CheckBoxRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private final JCheckBox box = new JCheckBox();

        CheckBoxRenderer() {
            box.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            Row row = value instanceof Row ? (Row) value : null;
            if (row == null) {
                box.setText(""); //$NON-NLS-1$
                box.setSelected(false);
            } else {
                box.setText(mainText(row.chat));
                box.setSelected(isLinked(row.chat));
            }
            box.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
            box.setOpaque(true);
            box.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            box.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            box.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return box;
        }
    }

    private class ChatRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            Row row = value instanceof Row ? (Row) value : null;
            String text = row == null ? "" : chatDetailHtml(row.chat); //$NON-NLS-1$
            putClientProperty("html.disable", text.startsWith("<html>") ? Boolean.FALSE : Boolean.TRUE); //$NON-NLS-1$
            if (text.startsWith("<html>")) {
                setText(text);
            } else {
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            if (selected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
    }

    /** Uma linha: nome do chat e, abaixo, os nomes de quem participa. */
    private static String chatDetailHtml(LinkedChatRef chat) {
        StringBuilder out = new StringBuilder("<html>"); //$NON-NLS-1$
        out.append("<b>").append(escapeHtml(chat.title)).append("</b>"); //$NON-NLS-1$ //$NON-NLS-2$
        if (chat.participants != null && !chat.participants.trim().isEmpty()) {
            out.append("<br><span style=\"font-size:11px\">"); //$NON-NLS-1$ //$NON-NLS-2$
            out.append(escapeHtml(Messages.getString("ChatCaptureLink.Participants") + " " + chat.participants)); //$NON-NLS-1$ //$NON-NLS-2$
            out.append("</span>"); //$NON-NLS-1$
        }
        if (chat.sourceName != null && !chat.sourceName.trim().isEmpty()) {
            out.append("<br><span style=\"font-size:10px;opacity:.7\">"); //$NON-NLS-1$ //$NON-NLS-2$
            out.append(escapeHtml(chat.sourceName));
            out.append("</span>"); //$NON-NLS-1$
        }
        if (ChatAnnotationStore.ORIGIN_MANUAL.equals(chat.origin)) {
            out.append("<br><i>").append(escapeHtml(Messages.getString("ChatCaptureLink.FromManual"))).append("</i>"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        out.append("</html>"); //$NON-NLS-1$
        return out.toString();
    }

    /** Uma linha da lista de disponiveis: nome e participantes. */
    private static String mainText(LinkedChatRef chat) {
        if (chat.participants == null || chat.participants.trim().isEmpty()) {
            return chat.title;
        }
        return chat.title + "   (" + chat.participants + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JLabel plainLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        return label;
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}