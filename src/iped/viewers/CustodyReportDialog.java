/*
 * Chat Captura IPED - Janela Enxuta de Cadastro e Conferencia de Laudo Tecnico e Cadeia de Custodia
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * Compact modal Swing dialog (10 essential fields) shown after the verifier notice on
 * Start / Re-Extrair (and on demand via "Dados do Laudo...").
 * <p>
 * Origin {@code report.pdf} is copied bit-for-bit automatically when present; IPED/blur
 * filter parameters and {@code relatorio/caseinformation.htm} metadata are derived
 * automatically from these fields and the active capture configuration.
 */
final class CustodyReportDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int MIN_WIDTH = 660;
    private static final int MIN_HEIGHT = 430;

    private final CustodyCaseRecord record;
    private final Path originFolder;
    private boolean confirmed;

    // Origin PDF bar
    private final JLabel originPdfStatusLabel = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JCheckBox unifiedPdfCheck = ChatCapturePanel.plainCheckBox(
            "Gerar tamb\u00e9m PDF unificado (laudo/report-completo-unificado.pdf)", false); //$NON-NLS-1$

    // Section 1: Device Identification (4 fields)
    private final JTextField modelField = new JTextField(26);
    private final JTextField imeiField = new JTextField(26);
    private final JTextField serialField = new JTextField(26);
    private final JTextField osVersionField = new JTextField(26);

    // Provenance badges
    private final JLabel badgeModel = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeImei = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeSerial = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeOs = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$

    // Section 2: Chain of Custody (6 fields: 5 fixed + 1 dynamic storage detail)
    private final JTextField collectedByField = new JTextField(26);
    private final JTextField analyzedByField = new JTextField(26);
    private final JTextField collectedAtField = new JTextField(26);
    private final JTextField locationField = new JTextField(26);
    private final JComboBox<CustodyCaseRecord.StorageType> storageCombo = new JComboBox<>(
            CustodyCaseRecord.StorageType.values());
    private final JLabel storageDetailLabel = ChatCapturePanel.plainLabel("Especificar armazenamento:"); //$NON-NLS-1$
    private final JTextField storageDetailField = new JTextField(26);
    private final JTextField caseNumberField = new JTextField(26);

    private CustodyCaseRecord.StorageType lastSelectedStorage = CustodyCaseRecord.StorageType.PASTA;
    private String pendingSealRef = ""; //$NON-NLS-1$
    private String pendingCustomStorage = ""; //$NON-NLS-1$

    CustodyReportDialog(
            Window owner,
            CustodyCaseRecord record,
            Path originFolder,
            String operationLabel,
            boolean standaloneEditMode) {
        super(owner,
                standaloneEditMode
                        ? "Dados do Laudo T\u00e9cnico e Cadeia de Cust\u00f3dia" //$NON-NLS-1$
                        : "Confer\u00eancia do Laudo e Cadeia de Cust\u00f3dia (" + operationLabel + ")", //$NON-NLS-1$ //$NON-NLS-2$
                ModalityType.APPLICATION_MODAL);
        this.record = record == null ? new CustodyCaseRecord() : record;
        this.originFolder = originFolder;

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel header = new JPanel(new BorderLayout(4, 2));
        JLabel title = ChatCapturePanel.plainLabel(
                standaloneEditMode
                        ? "Dados do Laudo T\u00e9cnico e Cadeia de Cust\u00f3dia" //$NON-NLS-1$
                        : "Confer\u00eancia pr\u00e9-" + operationLabel + " \u2014 Laudo e Cadeia de Cust\u00f3dia"); //$NON-NLS-1$ //$NON-NLS-2$
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(new Color(0x10, 0x2B, 0x35));
        JLabel subtitle = ChatCapturePanel.plainLabel(
                "Pr\u00e9-preenchido com os dados salvos e com o report.pdf da origem. Par\u00e2metros do IPED, filtro blur e relatorio.htm s\u00e3o preenchidos automaticamente."); //$NON-NLS-1$
        subtitle.setForeground(new Color(0x4A, 0x5A, 0x62));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        storageCombo.setRenderer(new PlainComboRenderer());

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;

        // --- Origem (report.pdf) ---
        JPanel originBar = new JPanel(new BorderLayout(8, 2));
        originBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD0, 0xDC, 0xE2)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        originBar.add(originPdfStatusLabel, BorderLayout.CENTER);
        JButton reloadPdfBtn = new JButton("Recarregar do report.pdf"); //$NON-NLS-1$
        reloadPdfBtn.setEnabled(originFolder != null);
        reloadPdfBtn.addActionListener(e -> reloadFromOriginPdf());
        originBar.add(reloadPdfBtn, BorderLayout.EAST);
        row = addFullWidth(form, row, originBar);
        row = addFullWidth(form, row, unifiedPdfCheck);

        // --- Secao 1: Identificacao do Dispositivo (4 campos) ---
        row = addSectionHeader(form, row, "1. Identifica\u00e7\u00e3o do Dispositivo (report.pdf + complementa\u00e7\u00e3o)"); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Dispositivo / Modelo:", modelField, badgeModel); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "IMEI:", imeiField, badgeImei); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "N\u00famero de s\u00e9rie:", serialField, badgeSerial); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Vers\u00e3o do SO:", osVersionField, badgeOs); //$NON-NLS-1$

        // --- Secao 2: Cadeia de Custodia (6 campos) ---
        row = addSectionHeader(form, row, "2. Cadeia de Cust\u00f3dia (Chain Of Custody)"); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quem coletou:", collectedByField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quem analisou:", analyzedByField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quando (coleta):", collectedAtField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Onde (local):", locationField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Como foi armazenado:", storageCombo); //$NON-NLS-1$
        row = addLabeledComponent(form, row, storageDetailLabel, storageDetailField);
        row = addSimpleField(form, row, "N\u00ba do caso / Laudo:", caseNumberField); //$NON-NLS-1$

        storageCombo.addActionListener(e -> onStorageTypeChanged());

        loadRecordIntoFields();

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JButton cancelBtn = new JButton(standaloneEditMode ? "Fechar" : "Cancelar"); //$NON-NLS-1$ //$NON-NLS-2$
        JButton confirmBtn = new JButton(standaloneEditMode
                ? "Salvar e Gerar Laudo (HTML + PDF)" //$NON-NLS-1$
                : "Confirmar e Continuar"); //$NON-NLS-1$
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actions.add(cancelBtn);
        actions.add(confirmBtn);
        add(actions, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(confirmBtn);
        confirmBtn.addActionListener(e -> {
            applyFieldsToRecord();
            confirmed = true;
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(Math.max(MIN_WIDTH, Math.min(760, getWidth() + 24)),
                Math.max(MIN_HEIGHT, Math.min(540, getHeight() + 14)));
        setLocationRelativeTo(owner);
    }

    private void loadRecordIntoFields() {
        if (record.originReportPdfPresent) {
            String shortSha = record.originReportPdfSha256.length() > 16
                    ? record.originReportPdfSha256.substring(0, 16) + "..." //$NON-NLS-1$
                    : record.originReportPdfSha256;
            originPdfStatusLabel.setText("report.pdf localizado (SHA-256: " + shortSha + ") \u2014 ser\u00e1 copiado intacto para laudo/report.pdf."); //$NON-NLS-1$ //$NON-NLS-2$
            originPdfStatusLabel.setForeground(new Color(0x0A, 0x5D, 0x3B));
        } else {
            originPdfStatusLabel.setText("Sem report.pdf na origem \u2014 ser\u00e1 gerado apenas o laudo do plugin (HTML + PDF)."); //$NON-NLS-1$
            originPdfStatusLabel.setForeground(new Color(0x7A, 0x56, 0x00));
        }
        unifiedPdfCheck.setEnabled(record.originReportPdfPresent);
        unifiedPdfCheck.setSelected(record.generateUnifiedPdf && record.originReportPdfPresent);

        modelField.setText(combineDeviceModel(record.deviceManufacturer, record.deviceModel));
        imeiField.setText(record.deviceImei);
        serialField.setText(record.deviceSerialNumber);
        osVersionField.setText(record.deviceOsVersion);

        collectedByField.setText(record.collectedBy);
        analyzedByField.setText(record.analyzedBy);
        collectedAtField.setText(record.collectedAt);
        locationField.setText(record.custodyLocation);
        caseNumberField.setText(record.caseNumber);

        pendingSealRef = record.storageSealReference == null ? "" : record.storageSealReference; //$NON-NLS-1$
        pendingCustomStorage = record.storageCustomText == null ? "" : record.storageCustomText; //$NON-NLS-1$
        lastSelectedStorage = record.storageType == null ? CustodyCaseRecord.StorageType.PASTA : record.storageType;
        storageCombo.setSelectedItem(lastSelectedStorage);

        refreshProvenanceBadges();
        updateStorageControls();
    }

    private static String combineDeviceModel(String manufacturer, String model) {
        String man = manufacturer == null ? "" : manufacturer.trim(); //$NON-NLS-1$
        String mod = model == null ? "" : model.trim(); //$NON-NLS-1$
        if (man.isEmpty()) {
            return mod;
        }
        if (mod.isEmpty()) {
            return man;
        }
        if (mod.toLowerCase(java.util.Locale.ROOT).contains(man.toLowerCase(java.util.Locale.ROOT))) {
            return mod;
        }
        return man + " " + mod; //$NON-NLS-1$
    }

    private void refreshProvenanceBadges() {
        boolean modelFromPdf = record.fieldsExtractedFromOriginPdf.contains("deviceModel") //$NON-NLS-1$
                || record.fieldsExtractedFromOriginPdf.contains("deviceManufacturer"); //$NON-NLS-1$
        setBadge(badgeModel, modelFromPdf, modelField.getText());
        setBadge(badgeImei, record.fieldsExtractedFromOriginPdf.contains("deviceImei"), imeiField.getText()); //$NON-NLS-1$
        setBadge(badgeSerial, record.fieldsExtractedFromOriginPdf.contains("deviceSerialNumber"), serialField.getText()); //$NON-NLS-1$
        setBadge(badgeOs, record.fieldsExtractedFromOriginPdf.contains("deviceOsVersion"), osVersionField.getText()); //$NON-NLS-1$
    }

    private void setBadge(JLabel badge, boolean fromOriginPdf, String value) {
        if (record.originReportPdfPresent && fromOriginPdf) {
            badge.setText("[report.pdf]"); //$NON-NLS-1$
            badge.setForeground(new Color(0x0A, 0x5D, 0x3B));
        } else if (value == null || value.isBlank()) {
            badge.setText(record.originReportPdfPresent ? "[faltando no PDF]" : "[opcional]"); //$NON-NLS-1$ //$NON-NLS-2$
            badge.setForeground(new Color(0x8B, 0x45, 0x00));
        } else {
            badge.setText("[salvo/plugin]"); //$NON-NLS-1$
            badge.setForeground(new Color(0x17, 0x47, 0x59));
        }
    }

    private void reloadFromOriginPdf() {
        if (originFolder == null) {
            return;
        }
        try {
            applyFieldsToRecord();
            OriginCustodyBinder.OriginReportPreview preview = OriginCustodyBinder.readDeviceAndCustodyPreview(originFolder);
            boolean overwrite = true;
            if (hasConflictingEditedValues(preview)) {
                JLabel msg = ChatCapturePanel.plainLabel(
                        "Alguns campos j\u00e1 est\u00e3o preenchidos com valores diferentes do report.pdf. Deseja preencher apenas os campos vazios ou sobrescrever tudo?"); //$NON-NLS-1$
                Object[] opts = new Object[] { "Preencher apenas vazios", "Sobrescrever tudo", "Cancelar" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                int choice = JOptionPane.showOptionDialog(this, msg, "Recarregar do report.pdf", //$NON-NLS-1$
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
                if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                    return;
                }
                overwrite = (choice == 1);
            }
            record.mergeFromOriginPreview(preview, overwrite);
            loadRecordIntoFields();
        } catch (Exception ex) {
            originPdfStatusLabel.setText("Erro ao ler report.pdf: " + ex.getMessage()); //$NON-NLS-1$
            originPdfStatusLabel.setForeground(new Color(0x8B, 0x00, 0x00));
        }
    }

    private boolean hasConflictingEditedValues(OriginCustodyBinder.OriginReportPreview p) {
        if (p == null || !p.reportPdfPresent) {
            return false;
        }
        return differs(record.deviceModel, combineDeviceModel(p.manufacturer, p.model))
                || differs(record.deviceSerialNumber, p.serialNumber)
                || differs(record.deviceOsVersion, p.osVersion)
                || differs(record.caseNumber, p.caseNumber);
    }

    private static boolean differs(String existing, String incoming) {
        return existing != null && !existing.isBlank()
                && incoming != null && !incoming.isBlank()
                && !existing.trim().equals(incoming.trim());
    }

    private void onStorageTypeChanged() {
        if (lastSelectedStorage == CustodyCaseRecord.StorageType.MIDIA_LACRADA) {
            pendingSealRef = storageDetailField.getText();
        } else if (lastSelectedStorage == CustodyCaseRecord.StorageType.CUSTOM) {
            pendingCustomStorage = storageDetailField.getText();
        }
        CustodyCaseRecord.StorageType selected = (CustodyCaseRecord.StorageType) storageCombo.getSelectedItem();
        lastSelectedStorage = selected == null ? CustodyCaseRecord.StorageType.PASTA : selected;
        updateStorageControls();
    }

    private void updateStorageControls() {
        CustodyCaseRecord.StorageType selected = (CustodyCaseRecord.StorageType) storageCombo.getSelectedItem();
        if (selected == CustodyCaseRecord.StorageType.MIDIA_LACRADA) {
            storageDetailLabel.setText("N\u00ba do Lacre:"); //$NON-NLS-1$
            storageDetailLabel.setEnabled(true);
            storageDetailField.setEnabled(true);
            storageDetailField.setEditable(true);
            storageDetailField.setText(pendingSealRef);
        } else if (selected == CustodyCaseRecord.StorageType.CUSTOM) {
            storageDetailLabel.setText("Especificar armazenamento (Custom):"); //$NON-NLS-1$
            storageDetailLabel.setEnabled(true);
            storageDetailField.setEnabled(true);
            storageDetailField.setEditable(true);
            storageDetailField.setText(pendingCustomStorage);
        } else {
            storageDetailLabel.setText("Detalhe (Lacre / Custom):"); //$NON-NLS-1$
            storageDetailLabel.setEnabled(false);
            storageDetailField.setEnabled(false);
            storageDetailField.setEditable(false);
            storageDetailField.setText(""); //$NON-NLS-1$
        }
    }

    private void applyFieldsToRecord() {
        record.copyOriginReportPdf = record.originReportPdfPresent;
        record.generateUnifiedPdf = unifiedPdfCheck.isEnabled() && unifiedPdfCheck.isSelected();

        record.deviceModel = CustodyCaseRecord.clamp(modelField.getText(), 512);
        record.deviceManufacturer = ""; //$NON-NLS-1$
        record.deviceImei = CustodyCaseRecord.clamp(imeiField.getText(), 512);
        record.deviceSerialNumber = CustodyCaseRecord.clamp(serialField.getText(), 512);
        record.deviceOsVersion = CustodyCaseRecord.clamp(osVersionField.getText(), 512);

        record.collectedBy = CustodyCaseRecord.clamp(collectedByField.getText(), 512);
        record.analyzedBy = CustodyCaseRecord.clamp(analyzedByField.getText(), 512);
        record.collectedAt = CustodyCaseRecord.clamp(collectedAtField.getText(), 512);
        record.analyzedAt = CustodyCaseRecord.nowLocalTimestamp();
        record.custodyLocation = CustodyCaseRecord.clamp(locationField.getText(), 512);
        record.caseNumber = CustodyCaseRecord.clamp(caseNumberField.getText(), 512);

        CustodyCaseRecord.StorageType st = (CustodyCaseRecord.StorageType) storageCombo.getSelectedItem();
        record.storageType = st == null ? CustodyCaseRecord.StorageType.PASTA : st;
        if (record.storageType == CustodyCaseRecord.StorageType.MIDIA_LACRADA) {
            pendingSealRef = storageDetailField.getText();
        } else if (record.storageType == CustodyCaseRecord.StorageType.CUSTOM) {
            pendingCustomStorage = storageDetailField.getText();
        }
        record.storageSealReference = CustodyCaseRecord.clamp(pendingSealRef, 512);
        record.storageCustomText = CustodyCaseRecord.clamp(pendingCustomStorage, 512);
    }

    boolean isConfirmed() {
        return confirmed;
    }

    JComboBox<CustodyCaseRecord.StorageType> getStorageComboForTest() {
        return storageCombo;
    }

    JTextField getStorageCustomFieldForTest() {
        return storageDetailField;
    }

    private static int addSectionHeader(JPanel form, int row, String text) {
        JLabel lbl = ChatCapturePanel.plainLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        lbl.setForeground(new Color(0x0B, 0x6B, 0x5D));
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD0, 0xDC, 0xE2)),
                BorderFactory.createEmptyBorder(8, 0, 3, 0)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 2, 4, 2);
        form.add(lbl, gbc);
        return row + 1;
    }

    private static int addFullWidth(JPanel form, int row, JComponent comp) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 2, 3, 2);
        form.add(comp, gbc);
        return row + 1;
    }

    private static int addFieldWithBadge(JPanel form, int row, String label, JComponent field, JLabel badge) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 6);
        form.add(ChatCapturePanel.plainLabel(label), gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 6);
        form.add(field, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 2);
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 11f));
        form.add(badge, gbc);
        return row + 1;
    }

    private static int addSimpleField(JPanel form, int row, String label, JComponent field) {
        return addLabeledComponent(form, row, ChatCapturePanel.plainLabel(label), field);
    }

    private static int addLabeledComponent(JPanel form, int row, JLabel labelComp, JComponent field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 6);
        form.add(labelComp, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 2);
        form.add(field, gbc);
        return row + 1;
    }

    private static final class PlainComboRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        PlainComboRenderer() {
            putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (c instanceof JComponent) {
                ((JComponent) c).putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
            }
            return c;
        }
    }
}
