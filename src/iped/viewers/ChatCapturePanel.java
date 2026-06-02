package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import iped.viewers.localization.Messages;

public class ChatCapturePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final HtmlLinkViewer viewer;
    private final JTextField outputFolder = new JTextField();
    private final JCheckBox customNameToggle = new JCheckBox("Nome custom"); //$NON-NLS-1$
    private final JTextField customFolderName = new JTextField();
    private final JTextField sensitiveImagesFolder = new JTextField();
    private final JComboBox<String> sensitiveSimilarity = new JComboBox<>(new String[] { "90%", "75%", "50%" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private final JComboBox<String> sensitiveBlurLevel = new JComboBox<>(new String[] { "90%", "60%", "30%", "20%" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private final JButton chooseFolder = new JButton(Messages.getString("ChatCapturePanel.SelectFolder")); //$NON-NLS-1$
    private final JButton chooseSensitiveFolder = new JButton("..."); //$NON-NLS-1$
    private final JButton startStop = new JButton(Messages.getString("ChatCapturePanel.Start")); //$NON-NLS-1$
    private final JButton recapture = new JButton("Re-Extrair"); //$NON-NLS-1$
    private final JLabel status = new JLabel(Messages.getString("ChatCapturePanel.MarkMessages")); //$NON-NLS-1$
    private final JLabel frames = new JLabel("0"); //$NON-NLS-1$
    private final JLabel attachments = new JLabel("0"); //$NON-NLS-1$
    private final JLabel recaptureBatchLabel = new JLabel(" "); //$NON-NLS-1$
    private final JLabel recaptureStepLabel = new JLabel(" "); //$NON-NLS-1$
    private final JProgressBar recaptureBatchProgress = new JProgressBar(0, 100);
    private final JProgressBar recaptureStepProgress = new JProgressBar(0, 100);

    public ChatCapturePanel(HtmlLinkViewer viewer) {
        super(new BorderLayout(4, 6));
        this.viewer = viewer;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(260, 250));

        JPanel form = new JPanel(new BorderLayout(4, 4));
        JPanel outputRow = new JPanel(new BorderLayout(4, 4));
        outputRow.add(new JLabel(Messages.getString("ChatCapturePanel.OutputFolder")), BorderLayout.NORTH); //$NON-NLS-1$
        outputRow.add(outputFolder, BorderLayout.CENTER);
        outputRow.add(chooseFolder, BorderLayout.EAST);

        JPanel customNameRow = new JPanel(new BorderLayout(4, 4));
        customFolderName.setEnabled(false);
        customFolderName.setToolTipText("Ex.: organizacao_nome ou organizacao_nome-{timestamp}"); //$NON-NLS-1$
        customNameRow.add(customNameToggle, BorderLayout.NORTH);
        customNameRow.add(customFolderName, BorderLayout.CENTER);

        JPanel sensitiveRow = new JPanel(new BorderLayout(4, 4));
        sensitiveRow.add(new JLabel("Pasta imagens blur"), BorderLayout.NORTH); //$NON-NLS-1$
        sensitiveImagesFolder.setToolTipText("Opcional: imagens de referencia para borrar thumbnails semelhantes"); //$NON-NLS-1$
        sensitiveRow.add(sensitiveImagesFolder, BorderLayout.CENTER);
        sensitiveRow.add(chooseSensitiveFolder, BorderLayout.EAST);

        form.add(outputRow, BorderLayout.NORTH);
        JPanel similarityRow = new JPanel(new BorderLayout(4, 4));
        sensitiveSimilarity.setToolTipText("Quanto maior, menos imagens diferentes serao borradas"); //$NON-NLS-1$
        similarityRow.add(new JLabel("Similaridade blur"), BorderLayout.NORTH); //$NON-NLS-1$
        similarityRow.add(sensitiveSimilarity, BorderLayout.CENTER);

        JPanel blurLevelRow = new JPanel(new BorderLayout(4, 4));
        sensitiveBlurLevel.setToolTipText("Nivel de intensidade do blur aplicado nas imagens censuradas"); //$NON-NLS-1$
        blurLevelRow.add(new JLabel("Blur censura"), BorderLayout.NORTH); //$NON-NLS-1$
        blurLevelRow.add(sensitiveBlurLevel, BorderLayout.CENTER);

        JPanel options = new JPanel(new GridLayout(4, 1, 4, 4));
        options.add(customNameRow);
        options.add(sensitiveRow);
        options.add(similarityRow);
        options.add(blurLevelRow);
        form.add(options, BorderLayout.SOUTH);

        JPanel counters = new JPanel(new BorderLayout(4, 4));
        counters.add(new JLabel(Messages.getString("ChatCapturePanel.Frames")), BorderLayout.WEST); //$NON-NLS-1$
        counters.add(frames, BorderLayout.CENTER);
        counters.add(new JLabel(Messages.getString("ChatCapturePanel.Attachments")), BorderLayout.EAST); //$NON-NLS-1$

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JPanel actions = new JPanel(new GridLayout(1, 2, 4, 4));
        actions.add(startStop);
        actions.add(recapture);

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
        add(counters, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        chooseFolder.addActionListener(e -> selectFolder());
        chooseSensitiveFolder.addActionListener(e -> selectSensitiveFolder());
        customNameToggle.addActionListener(e -> customFolderName.setEnabled(customNameToggle.isSelected()));
        startStop.addActionListener(e -> {
            if (viewer.isChatCaptureRunning()) {
                viewer.stopChatCapture();
            } else {
                String customName = customNameToggle.isSelected() ? customFolderName.getText() : null;
                viewer.startChatCapture(new File(outputFolder.getText()), customName, getSensitiveImagesFolder(),
                        getSensitiveSimilarityPercent(), getSensitiveBlurPercent());
            }
        });
        recapture.addActionListener(e -> viewer.showRecaptureDialog(new File(outputFolder.getText()), getSensitiveImagesFolder(),
                getSensitiveSimilarityPercent(), getSensitiveBlurPercent()));
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
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!sensitiveImagesFolder.getText().trim().isEmpty()) {
            chooser.setCurrentDirectory(new File(sensitiveImagesFolder.getText()));
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            sensitiveImagesFolder.setText(chooser.getSelectedFile().getAbsolutePath());
        }
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
