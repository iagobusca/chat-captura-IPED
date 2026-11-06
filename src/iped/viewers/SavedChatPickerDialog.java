package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import iped.viewers.localization.Messages;

class SavedChatPickerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final List<String> folders = new ArrayList<>();
    private boolean confirmed;
    private String selectedFolder;

    SavedChatPickerDialog(Window owner, java.io.File outputRoot) {
        super(owner, "Anotar chat salvo", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(6, 6));
        ((javax.swing.JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel hint = new JLabel("Selecione um chat salvo para anotar:");
        add(hint, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JButton ok = new JButton("Abrir para anotar");
        JButton cancel = new JButton(Messages.getString("ChatNoteDialog.Cancel"));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        actions.add(cancel);
        actions.add(ok);
        add(actions, BorderLayout.SOUTH);

        loadFolders(outputRoot);

        ok.addActionListener(e -> {
            int index = list.getSelectedIndex();
            if (index >= 0) {
                confirmed = true;
                selectedFolder = folders.get(index);
                dispose();
            }
        });
        cancel.addActionListener(e -> dispose());

        pack();
        setMinimumSize(new Dimension(480, 320));
        setSize(Math.max(480, getWidth()), Math.max(320, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void loadFolders(java.io.File outputRoot) {
        if (outputRoot == null || !outputRoot.isDirectory()) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(outputRoot.toPath())) {
            stream.filter(Files::isDirectory).forEach(path -> {
                Path manifest = path.resolve("manifest.json");
                if (!Files.isRegularFile(manifest)) {
                    return;
                }
                String title = "";
                String sourceName = "";
                try {
                    String content = CaptureIntegrity.readUtf8Limited(manifest);
                    title = ChatAnnotationStore.field(content, "title");
                    if (title.isEmpty()) {
                        title = ChatAnnotationStore.field(content, "chatTitle");
                    }
                    sourceName = ChatAnnotationStore.field(content, "sourceName");
                    String sourceMetadata = ChatAnnotationStore.extractObject(content, "sourceMetadata");
                    if (sourceMetadata != null) {
                        if (title.isEmpty()) {
                            title = ChatAnnotationStore.field(sourceMetadata, "nome");
                        }
                        if (sourceName.isEmpty()) {
                            sourceName = ChatAnnotationStore.field(sourceMetadata, "nome");
                        }
                    }
                } catch (Exception e) {
                    // ignora pasta invalida
                }
                String label = path.getFileName().toString();
                if (!title.isEmpty()) {
                    label = label + " - " + title;
                }
                if (!sourceName.isEmpty()) {
                    label = label + " (" + sourceName + ")";
                }
                folders.add(path.getFileName().toString());
                model.addElement(label);
            });
        } catch (Exception e) {
            // ignora erros de leitura
        }
        SwingUtilities.invokeLater(() -> {
            if (model.getSize() > 0) {
                list.setSelectedIndex(0);
            }
        });
    }

    boolean isConfirmed() {
        return confirmed;
    }

    String getSelectedFolder() {
        return selectedFolder;
    }
}
