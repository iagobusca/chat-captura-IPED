package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import iped.viewers.localization.Messages;

public class ChatCapturePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    static JLabel plainLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        return label;
    }

    static JCheckBox plainCheckBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        return box;
    }

    static JComponent plainMessageComponent(String text) {
        String safe = text == null ? "" : text; //$NON-NLS-1$
        if (!safe.contains("\n") && safe.length() <= 140) { //$NON-NLS-1$
            return plainLabel(safe);
        }
        javax.swing.JTextArea area = new javax.swing.JTextArea(safe);
        area.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setColumns(48);
        int lines = 1;
        for (int i = 0; i < safe.length(); i++) {
            if (safe.charAt(i) == '\n') lines++;
        }
        area.setRows(Math.min(18, Math.max(2, lines + (safe.length() / 64))));
        if (lines <= 8 && safe.length() <= 500) {
            return area;
        }
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(540, 260));
        return scroll;
    }

    private final HtmlLinkViewer viewer;
    private final JTextField outputFolder = new JTextField();
    private final JCheckBox customNameToggle = plainCheckBox("Nome custom", false); //$NON-NLS-1$
    private final JTextField customFolderName = new JTextField();
    private final JTextField sensitiveImagesFolder = new JTextField();
    private final JTextField originFolderField = new JTextField();
    private final JTextField parentIpedFolderField = new JTextField();
    private final JComboBox<String> sensitiveSimilarity = new JComboBox<>(new String[] { "90%", "75%", "50%" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private final JComboBox<String> sensitiveBlurLevel = new JComboBox<>(new String[] { "90%", "60%", "30%", "20%" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private final JButton chooseFolder = new JButton(Messages.getString("ChatCapturePanel.SelectFolder")); //$NON-NLS-1$
    private final JButton chooseSensitiveFolder = new JButton("..."); //$NON-NLS-1$
    private final JButton chooseOriginFolder = new JButton("..."); //$NON-NLS-1$
    private final JButton chooseParentIpedFolder = new JButton("..."); //$NON-NLS-1$
    private final JButton startStop = new JButton(Messages.getString("ChatCapturePanel.Start")); //$NON-NLS-1$
    private final JButton recapture = new JButton("Re-Extrair"); //$NON-NLS-1$
    private final JButton annotateSaved = new JButton("Anotar chat salvo..."); //$NON-NLS-1$
    private final JButton saveAnnotations = new JButton("Salvar Trecho-Notas"); //$NON-NLS-1$
    private final JCheckBox annotationsToggle = plainCheckBox(Messages.getString("ChatCapturePanel.IncludeAnnotations"), true); //$NON-NLS-1$
    private final JLabel status = plainLabel(Messages.getString("ChatCapturePanel.MarkMessages")); //$NON-NLS-1$
    private final JLabel frames = plainLabel("0"); //$NON-NLS-1$
    private final JLabel attachments = plainLabel("0"); //$NON-NLS-1$
    private final JLabel notesCounter = plainLabel("0"); //$NON-NLS-1$
    private final JLabel linksCounter = plainLabel("0"); //$NON-NLS-1$
    private final JLabel outOfRangeCounter = plainLabel("0"); //$NON-NLS-1$
    private final JLabel recaptureBatchLabel = plainLabel(" "); //$NON-NLS-1$
    private final JLabel recaptureStepLabel = plainLabel(" "); //$NON-NLS-1$
    private final JProgressBar recaptureBatchProgress = new JProgressBar(0, 100);
    private final JProgressBar recaptureStepProgress = new JProgressBar(0, 100);
    private JPanel linkedDestinationRow;
    private JLabel linkedDestinationLabel;
    private JPanel outputRow;
    private JPanel countersPanel;
    private JPanel linksCell;
    private JLabel notesLabel;
    private boolean linkedCaptureMode;
    private String linkedCustomName;

    private final JTextField searchField = new JTextField();
    private final JLabel searchStatus = plainLabel(" "); //$NON-NLS-1$
    private final JButton searchPrevious = new JButton(Messages.getString("ChatSearch.Previous")); //$NON-NLS-1$
    private final JButton searchNext = new JButton(Messages.getString("ChatSearch.Next")); //$NON-NLS-1$
    private final JButton searchClear = new JButton(Messages.getString("ChatSearch.Clear")); //$NON-NLS-1$
    private final DefaultListModel<ChatSearchHit> searchModel = new DefaultListModel<>();
    private final JList<ChatSearchHit> searchList = new JList<>(searchModel);
    private final Timer searchDebounce;
    private int searchResultTotal;

    public ChatCapturePanel(HtmlLinkViewer viewer) {
        super(new BorderLayout(4, 6));
        this.viewer = viewer;
        searchDebounce = new Timer(450, e -> viewer.searchChat(searchField.getText()));
        searchDebounce.setRepeats(false);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(290, 320));

        JPanel form = new JPanel(new BorderLayout(4, 4));
        outputRow = new JPanel(new BorderLayout(4, 4));
        outputRow.add(plainLabel(Messages.getString("ChatCapturePanel.OutputFolder")), BorderLayout.NORTH); //$NON-NLS-1$
        outputRow.add(outputFolder, BorderLayout.CENTER);
        outputRow.add(chooseFolder, BorderLayout.EAST);
        // linha exibida apenas na captura de chat vinculado
        linkedDestinationRow = new JPanel(new BorderLayout(4, 4));
        linkedDestinationRow.add(plainLabel(Messages.getString("ChatCapturePanel.LinkedDestination")), BorderLayout.NORTH); //$NON-NLS-1$
        linkedDestinationLabel = new JLabel(" ");
        linkedDestinationRow.add(linkedDestinationLabel, BorderLayout.CENTER);
        linkedDestinationRow.setVisible(false);

        JPanel customNameRow = new JPanel(new BorderLayout(4, 4));
        customFolderName.setEnabled(false);
        customFolderName.setToolTipText("Ex.: organizacao_nome ou organizacao_nome-{timestamp}"); //$NON-NLS-1$
        customNameRow.add(customNameToggle, BorderLayout.NORTH);
        customNameRow.add(customFolderName, BorderLayout.CENTER);

        JPanel sensitiveRow = new JPanel(new BorderLayout(4, 4));
        sensitiveRow.add(plainLabel("Pasta imagens blur"), BorderLayout.NORTH); //$NON-NLS-1$
        sensitiveImagesFolder.setToolTipText("Opcional: imagens de referencia para borrar thumbnails semelhantes"); //$NON-NLS-1$
        sensitiveRow.add(sensitiveImagesFolder, BorderLayout.CENTER);
        sensitiveRow.add(chooseSensitiveFolder, BorderLayout.EAST);

        JPanel originRow = new JPanel(new BorderLayout(4, 4));
        originRow.add(plainLabel(Messages.getString("ChatCapturePanel.OriginFolder")), BorderLayout.NORTH); //$NON-NLS-1$
        originFolderField.setToolTipText(Messages.getString("ChatCapturePanel.OriginFolderHint")); //$NON-NLS-1$
        originRow.add(originFolderField, BorderLayout.CENTER);
        originRow.add(chooseOriginFolder, BorderLayout.EAST);

        JPanel parentIpedRow = new JPanel(new BorderLayout(4, 4));
        parentIpedRow.add(plainLabel(Messages.getString("ChatCapturePanel.ParentIpedFolder")), BorderLayout.NORTH); //$NON-NLS-1$
        parentIpedFolderField.setToolTipText(Messages.getString("ChatCapturePanel.ParentIpedFolderHint")); //$NON-NLS-1$
        parentIpedRow.add(parentIpedFolderField, BorderLayout.CENTER);
        parentIpedRow.add(chooseParentIpedFolder, BorderLayout.EAST);

        loadSavedOriginPreferences();

        JPanel outputArea = new JPanel(new BorderLayout(4, 4));
        outputArea.add(outputRow, BorderLayout.NORTH);
        outputArea.add(linkedDestinationRow, BorderLayout.SOUTH);
        form.add(outputArea, BorderLayout.NORTH);
        JPanel similarityRow = new JPanel(new BorderLayout(4, 4));
        sensitiveSimilarity.setToolTipText("Quanto maior, menos imagens diferentes serao borradas"); //$NON-NLS-1$
        similarityRow.add(plainLabel("Similaridade blur"), BorderLayout.NORTH); //$NON-NLS-1$
        similarityRow.add(sensitiveSimilarity, BorderLayout.CENTER);

        JPanel blurLevelRow = new JPanel(new BorderLayout(4, 4));
        sensitiveBlurLevel.setToolTipText("Nivel de intensidade do blur aplicado nas imagens censuradas"); //$NON-NLS-1$
        blurLevelRow.add(plainLabel("Blur censura"), BorderLayout.NORTH); //$NON-NLS-1$
        blurLevelRow.add(sensitiveBlurLevel, BorderLayout.CENTER);

        JPanel options = new JPanel(new GridLayout(7, 1, 4, 4));
        options.add(customNameRow);
        options.add(sensitiveRow);
        options.add(similarityRow);
        options.add(blurLevelRow);
        options.add(originRow);
        options.add(parentIpedRow);
        annotationsToggle.setToolTipText(Messages.getString("ChatCapturePanel.IncludeAnnotations")); //$NON-NLS-1$
        options.add(annotationsToggle);
        form.add(options, BorderLayout.SOUTH);

        JPanel counters = new JPanel(new GridLayout(2, 3, 4, 2));
        counters.add(plainLabel(Messages.getString("ChatCapturePanel.Frames"))); //$NON-NLS-1$
        counters.add(frames);
        counters.add(plainLabel(Messages.getString("ChatCapturePanel.Attachments"))); //$NON-NLS-1$
        counters.add(attachments);
        notesLabel = plainLabel(Messages.getString("ChatCapturePanel.Notes")); //$NON-NLS-1$
        counters.add(notesLabel);
        counters.add(notesCounter);
        linksCell = new JPanel(new BorderLayout(2, 0));
        linksCell.add(plainLabel(Messages.getString("ChatCapturePanel.Links")), BorderLayout.WEST); //$NON-NLS-1$
        linksCell.add(linksCounter, BorderLayout.CENTER);
        linksCell.add(plainLabel(Messages.getString("ChatCapturePanel.OutOfRange")), BorderLayout.EAST); //$NON-NLS-1$
        linksCell.add(outOfRangeCounter, BorderLayout.SOUTH);
        counters.add(linksCell);
        counters.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        countersPanel = counters;

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
        actions.add(startStop);
        actions.add(recapture);
        actions.add(annotateSaved);
        actions.add(saveAnnotations);

        recaptureBatchProgress.setStringPainted(true);
        recaptureStepProgress.setStringPainted(true);
        JPanel progress = new JPanel(new GridLayout(4, 1, 2, 2));
        progress.add(recaptureBatchLabel);
        progress.add(recaptureBatchProgress);
        progress.add(recaptureStepLabel);
        progress.add(recaptureStepProgress);
        setRecaptureProgressVisible(false);

        bottom.add(actions, BorderLayout.WEST);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(progress, BorderLayout.SOUTH);

        add(form, BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        chooseFolder.addActionListener(e -> selectFolder());
        chooseSensitiveFolder.addActionListener(e -> selectSensitiveFolder());
        chooseOriginFolder.addActionListener(e -> selectDirectoryInto(originFolderField));
        chooseParentIpedFolder.addActionListener(e -> selectDirectoryInto(parentIpedFolderField));
        customNameToggle.addActionListener(e -> customFolderName.setEnabled(customNameToggle.isSelected()));
        startStop.addActionListener(e -> {
            if (viewer != null && viewer.isChatCaptureRunning()) {
                viewer.stopChatCapture();
            } else {
                File destination = resolveValidOutputFolder(outputFolder.getText());
                if (destination == null) {
                    if (viewer != null) {
                        viewer.reportMissingOutputFolder();
                    }
                    return;
                }
                if (viewer != null) {
                    saveOriginPreferences();
                    viewer.startChatCaptureFromUi(this, destination, getEffectiveCustomName(),
                            getSensitiveImagesFolder(), getSensitiveSimilarityPercent(), getSensitiveBlurPercent(),
                            getOriginFolder(), getParentIpedFolder());
                }
            }
        });
        recapture.addActionListener(e -> {
            File destination = resolveValidOutputFolder(outputFolder.getText());
            if (destination == null) {
                if (viewer != null) {
                    viewer.reportMissingOutputFolder();
                }
                return;
            }
            if (viewer != null) {
                viewer.startRecaptureFlowFromUi(this, destination, getSensitiveImagesFolder(),
                        getSensitiveSimilarityPercent(), getSensitiveBlurPercent());
            }
        });
        annotateSaved.addActionListener(e -> viewer.annotateSavedChat());
        saveAnnotations.addActionListener(e -> viewer.saveAnnotations());
    }

    static File resolveValidOutputFolder(String folderText) {
        if (folderText == null) {
            return null;
        }
        String trimmed = folderText.trim();
        return trimmed.isEmpty() ? null : new File(trimmed);
    }

    JButton getStartStopButtonForTest() {
        return startStop;
    }

    JButton getRecaptureButtonForTest() {
        return recapture;
    }

    public void setOutputFolder(java.io.File folder) {
        if (folder != null) {
            outputFolder.setText(folder.getAbsolutePath());
        }
    }

    /**
     * Centro do painel: os contadores da captura em cima e a lista de resultados
     * da pesquisa abaixo. A busca fica visivel tambem na janela de chat vinculado,
     * onde {@link #hideAnnotationsUi()} so remove os controles de anotacao.
     */
    private JComponent buildCenter() {
        JPanel searchArea = new JPanel(new BorderLayout(4, 4));
        searchArea.setBorder(BorderFactory.createTitledBorder(Messages.getString("ChatSearch.Title"))); //$NON-NLS-1$

        searchField.setToolTipText(Messages.getString("ChatSearch.Hint")); //$NON-NLS-1$
        searchField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSearch();
            }
        });

        JPanel searchButtons = new JPanel(new GridLayout(1, 3, 2, 2));
        searchButtons.add(searchPrevious);
        searchButtons.add(searchNext);
        searchButtons.add(searchClear);

        JPanel searchTop = new JPanel(new BorderLayout(4, 4));
        searchTop.add(searchField, BorderLayout.CENTER);
        searchTop.add(searchButtons, BorderLayout.EAST);
        searchTop.add(searchStatus, BorderLayout.SOUTH);

        searchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchList.setVisibleRowCount(8);
        searchList.setCellRenderer(new SearchHitRenderer());
        searchList.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && searchList.getSelectedValue() != null) {
                    viewer.gotoChatSearchHit(searchList.getSelectedIndex());
                }
            }
        });
        searchList.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && searchList.getSelectedValue() != null) {
                    e.consume();
                    viewer.gotoChatSearchHit(searchList.getSelectedIndex());
                }
            }
        });

        searchArea.add(searchTop, BorderLayout.NORTH);
        searchArea.add(new JScrollPane(searchList), BorderLayout.CENTER);
        searchArea.setPreferredSize(new Dimension(290, 240));
        updateSearchButtons();

        searchPrevious.addActionListener(e -> viewer.previousChatSearchHit());
        searchNext.addActionListener(e -> viewer.nextChatSearchHit());
        searchClear.addActionListener(e -> clearSearch());
        searchField.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    searchDebounce.stop();
                    viewer.searchChat(searchField.getText());
                    if (searchList.getModel().getSize() > 0) {
                        searchList.setSelectedIndex(0);
                        viewer.gotoChatSearchHit(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    clearSearch();
                }
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, countersPanel, searchArea);
        split.setResizeWeight(0.0);
        split.setContinuousLayout(true);
        split.setBorder(null);
        // 0.25 mantem os contadores compactos e da o resto para os resultados
        split.setDividerLocation(0.25);
        return split;
    }

    private void scheduleSearch() {
        searchDebounce.restart();
        if (searchField.getText().trim().isEmpty()) {
            searchDebounce.stop();
            viewer.searchChat(""); //$NON-NLS-1$
        }
    }

    private void clearSearch() {
        searchDebounce.stop();
        searchField.setText(""); //$NON-NLS-1$
        viewer.searchChat(""); //$NON-NLS-1$
    }

    /** Recebe os resultados vindos da thread de busca. */
    void setSearchResults(String query, int total, List<ChatSearchHit> hits) {
        SwingUtilities.invokeLater(() -> {
            searchResultTotal = total;
            searchModel.clear();
            for (ChatSearchHit hit : hits) {
                searchModel.addElement(hit);
            }
            if (query == null || query.trim().isEmpty()) {
                searchStatus.setText(" "); //$NON-NLS-1$
            } else if (total == 0) {
                searchStatus.setText(Messages.getString("ChatSearch.NoResults")); //$NON-NLS-1$
            } else if (total > hits.size()) {
                searchStatus.setText(Messages.getString("ChatSearch.Truncated") + " " + total); //$NON-NLS-1$
            } else {
                searchStatus.setText(Messages.getString("ChatSearch.Results") + " " + total); //$NON-NLS-1$
            }
            updateSearchButtons();
        });
    }

    /** Destaca na lista o resultado para onde o chat foi rolado. */
    void setCurrentSearchHit(int index, int size) {
        SwingUtilities.invokeLater(() -> {
            if (index >= 0 && index < searchModel.getSize()) {
                searchList.setSelectedIndex(index);
                searchList.ensureIndexIsVisible(index);
            }
            updateSearchButtons();
        });
    }

    private void updateSearchButtons() {
        boolean hasResults = searchModel.getSize() > 0;
        searchPrevious.setEnabled(hasResults);
        searchNext.setEnabled(hasResults);
        searchClear.setEnabled(hasResults || !searchField.getText().trim().isEmpty());
    }

    /** Renderiza o resultado com o tipo em negrito e o trecho normal. */
    private static class SearchHitRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        SearchHitRenderer() {
            putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
            setToolTipText(null);
            java.awt.Component component = super.getListCellRendererComponent(list,
                    value instanceof ChatSearchHit ? "" : value, index, selected, focused); //$NON-NLS-1$
            if (value instanceof ChatSearchHit) {
                ChatSearchHit hit = (ChatSearchHit) value;
                String label = ChatSearchHit.typeLabel(hit.type);
                boolean hasLabel = !label.isEmpty();
                putClientProperty("html.disable", hasLabel ? Boolean.FALSE : Boolean.TRUE); //$NON-NLS-1$
                setText(hasLabel ? "<html><b>" + escape(label) + "</b> " + escape(hit.snippet) + "</html>" : hit.snippet); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                setToolTipText(hit.snippet == null ? null : "<html>" + escape(hit.snippet) + "</html>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return component;
        }

        private static String escape(String value) {
            return html(value);
        }
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!outputFolder.getText().trim().isEmpty()) {
            chooser.setCurrentDirectory(new File(outputFolder.getText()));
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputFolder.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void selectSensitiveFolder() {
        selectDirectoryInto(sensitiveImagesFolder);
    }

    private void selectDirectoryInto(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!field.getText().trim().isEmpty()) {
            chooser.setCurrentDirectory(new File(field.getText()));
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadSavedOriginPreferences() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ChatCapturePanel.class);
            String lastOrigin = prefs.get("originFolder", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String lastParent = prefs.get("parentIpedFolder", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (!lastOrigin.isBlank()) originFolderField.setText(lastOrigin);
            if (!lastParent.isBlank()) parentIpedFolderField.setText(lastParent);
        } catch (RuntimeException ignored) {
        }
    }

    private void saveOriginPreferences() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ChatCapturePanel.class);
            prefs.put("originFolder", originFolderField.getText() == null ? "" : originFolderField.getText().trim()); //$NON-NLS-1$ //$NON-NLS-2$
            prefs.put("parentIpedFolder", parentIpedFolderField.getText() == null ? "" : parentIpedFolderField.getText().trim()); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (RuntimeException ignored) {
        }
    }

    public File getOriginFolder() {
        String path = originFolderField.getText() == null ? "" : originFolderField.getText().trim(); //$NON-NLS-1$
        return path.isEmpty() ? null : new File(path);
    }

    public File getParentIpedFolder() {
        String path = parentIpedFolderField.getText() == null ? "" : parentIpedFolderField.getText().trim(); //$NON-NLS-1$
        return path.isEmpty() ? null : new File(path);
    }

    private File getSensitiveImagesFolder() {
        String path = sensitiveImagesFolder.getText().trim();
        return path.isEmpty() ? null : new File(path);
    }

    private int getSensitiveSimilarityPercent() {
        Object selected = sensitiveSimilarity.getSelectedItem();
        String value = selected == null ? "90" : selected.toString().replace("%", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    private int getSensitiveBlurPercent() {
        Object selected = sensitiveBlurLevel.getSelectedItem();
        String value = selected == null ? "90" : selected.toString().replace("%", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    public void setCaptureRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            startStop.setText(Messages.getString(running ? "ChatCapturePanel.Stop" : "ChatCapturePanel.Start")); //$NON-NLS-1$ //$NON-NLS-2$
            recapture.setEnabled(!running);
            annotationsToggle.setEnabled(!running);
            chooseOriginFolder.setEnabled(!running);
            chooseParentIpedFolder.setEnabled(!running);
        });
    }

    public String getOutputFolderText() {
        return outputFolder.getText() == null ? "" : outputFolder.getText().trim(); //$NON-NLS-1$
    }

    /**
     * Modo captura de chat vinculado: nao ha escolha de pasta nem nome custom. A saida
     * vai sempre para a pasta {@code vinculado} dentro da pasta do conteudo capturado
     * de origem, em uma subpasta que identifica o chat e a mensagem.
     */
    public void setLinkedCaptureMode(File destination, String folderName) {
        linkedCaptureMode = true;
        outputFolder.setText(destination == null ? "" : destination.getAbsolutePath()); //$NON-NLS-1$
        outputRow.setVisible(false);
        customNameToggle.setVisible(false);
        customFolderName.setVisible(false);
        linkedCustomName = folderName;
        linkedDestinationRow.setVisible(true);
        linkedDestinationLabel.setText("<html>" + html(destination == null ? "" : destination.getAbsolutePath()) //$NON-NLS-1$ //$NON-NLS-2$
                + "<br><small>" + html(Messages.getString("ChatCapturePanel.LinkedDestinationHint")) + "</small></html>"); //$NON-NLS-1$ //$NON-NLS-2$
        revalidate();
        repaint();
    }

    /**
 * Esconde o controle de Notas e Links na captura de chat vinculado.
 *
 * Nao faz sentido anotar ou vincular de novo dentro do chat que ja foi vinculado:
 * a anotacao pertence a mensagem de origem, e o manifesto desta captura ja
 * registra o linkedFrom.
 */
    public void hideAnnotationsUi() {
        annotationsToggle.setVisible(false);
        notesLabel.setVisible(false);
        notesCounter.setVisible(false);
        linksCell.setVisible(false);
        annotateSaved.setVisible(false);
        saveAnnotations.setVisible(false);
        // sobram apenas Imagens e Arquivos
        countersPanel.setLayout(new GridLayout(1, 4, 4, 2));
        setAnnotationsEnabled(false);
        revalidate();
        repaint();
    }

    static String html(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Nome custom usado quando a captura e de chat vinculado. */
    public String getEffectiveCustomName() {
        return linkedCaptureMode ? linkedCustomName
                : (customNameToggle.isSelected() ? customFolderName.getText() : null);
    }

    public boolean isAnnotationsEnabled() {
        return annotationsToggle.isSelected();
    }

    public void setAnnotationsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> annotationsToggle.setSelected(enabled));
    }

    public void updateAnnotationCounters(int notes, int links, int outOfRange) {
        SwingUtilities.invokeLater(() -> {
            notesCounter.setText(Integer.toString(notes));
            linksCounter.setText(Integer.toString(links));
            outOfRangeCounter.setText(Integer.toString(outOfRange));
            outOfRangeCounter.setForeground(outOfRange > 0 ? new java.awt.Color(0xC6, 0x28, 0x28) : outOfRangeCounter.getForeground());
        });
    }

    public void setStatus(String messageKey) {
        SwingUtilities.invokeLater(() -> status.setText(Messages.getString(messageKey)));
    }

    public void setStatusText(String message) {
        SwingUtilities.invokeLater(() -> status.setText(message));
    }

    public void setRecaptureProgress(String message, int batchCurrent, int batchTotal, int stepCurrent, int stepTotal,
            boolean stepIndeterminate) {
        SwingUtilities.invokeLater(() -> {
            setRecaptureProgressVisible(true);
            status.setText(message);
            recaptureBatchLabel.setText("Re-Extrair: chat " + batchCurrent + " de " + Math.max(1, batchTotal)); //$NON-NLS-1$ //$NON-NLS-2$
            recaptureBatchProgress.setMaximum(Math.max(1, batchTotal));
            recaptureBatchProgress.setValue(Math.min(Math.max(0, batchCurrent), Math.max(1, batchTotal)));
            recaptureBatchProgress.setString(batchCurrent + "/" + Math.max(1, batchTotal)); //$NON-NLS-1$
            recaptureStepLabel.setText(message);
            recaptureStepProgress.setIndeterminate(stepIndeterminate);
            recaptureStepProgress.setMaximum(Math.max(1, stepTotal));
            recaptureStepProgress.setValue(Math.min(Math.max(0, stepCurrent), Math.max(1, stepTotal)));
            recaptureStepProgress.setString(stepIndeterminate ? "aguarde..." : stepCurrent + "/" + Math.max(1, stepTotal)); //$NON-NLS-1$ //$NON-NLS-2$
        });
    }

    public void clearRecaptureProgress() {
        SwingUtilities.invokeLater(() -> {
            recaptureStepProgress.setIndeterminate(false);
            recaptureBatchProgress.setValue(0);
            recaptureStepProgress.setValue(0);
            recaptureBatchLabel.setText(" "); //$NON-NLS-1$
            recaptureStepLabel.setText(" "); //$NON-NLS-1$
            setRecaptureProgressVisible(false);
        });
    }

    public void updateCounters(int frameCount, int attachmentCount) {
        SwingUtilities.invokeLater(() -> {
            frames.setText(Integer.toString(frameCount));
            attachments.setText(Integer.toString(attachmentCount));
        });
    }

    private void setRecaptureProgressVisible(boolean visible) {
        recaptureBatchLabel.setVisible(visible);
        recaptureStepLabel.setVisible(visible);
        recaptureBatchProgress.setVisible(visible);
        recaptureStepProgress.setVisible(visible);
    }
}
