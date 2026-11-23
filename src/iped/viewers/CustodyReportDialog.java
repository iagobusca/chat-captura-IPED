/*
 * Chat Captura IPED - Janela de Cadastro e Conferencia de Laudo Tecnico e Cadeia de Custodia
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
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Modal Swing dialog shown after the verifier notice on Start / Re-Extrair (and on demand via
 * "Dados do Laudo..."). Displays pre-filled data from {@code laudo/custodia-caso.json} and
 * {@code origin/report.pdf}, allowing verification and completion of device info, chain of
 * custody (with {@code [Pasta, Midia lacrada, Custom]} selector), and IPED parameters.
 */
final class CustodyReportDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int MIN_WIDTH = 720;
    private static final int MIN_HEIGHT = 640;

    private final CustodyCaseRecord record;
    private final Path originFolder;
    private boolean confirmed;

    // Origin PDF block
    private final JLabel originPdfStatusLabel = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JCheckBox copyOriginPdfCheck = ChatCapturePanel.plainCheckBox(
            "Copiar report.pdf da origem sem altera\u00e7\u00e3o para laudo/report.pdf (validando SHA-256)", true); //$NON-NLS-1$
    private final JCheckBox unifiedPdfCheck = ChatCapturePanel.plainCheckBox(
            "Gerar tamb\u00e9m PDF unificado (laudo/report-completo-unificado.pdf: report do plugin + report.pdf original)", false); //$NON-NLS-1$

    // Device block
    private final JTextField manufacturerField = new JTextField(28);
    private final JTextField modelField = new JTextField(28);
    private final JTextField imeiField = new JTextField(28);
    private final JTextField serialField = new JTextField(28);
    private final JTextField osVersionField = new JTextField(28);
    private final JTextField whatsappVersionField = new JTextField(28);
    private final JTextField collectionCodeField = new JTextField(28);
    private final JTextField originToolField = new JTextField(28);

    // Chain of Custody block
    private final JTextField collectedByField = new JTextField(28);
    private final JTextField analyzedByField = new JTextField(28);
    private final JTextField collectedAtField = new JTextField(28);
    private final JTextField analyzedAtField = new JTextField(28);
    private final JTextField locationField = new JTextField(28);
    private final JComboBox<CustodyCaseRecord.StorageType> storageCombo = new JComboBox<>(
            CustodyCaseRecord.StorageType.values());
    private final JTextField storageCustomField = new JTextField(28);
    private final JTextField sealRefField = new JTextField(28);
    private final JLabel storageCustomLabel = ChatCapturePanel.plainLabel("Especificar armazenamento (Custom):"); //$NON-NLS-1$
    private final JTextField storageResponsibleField = new JTextField(28);
    private final JTextField caseNumberField = new JTextField(28);
    private final JTextField legalAuthorizationField = new JTextField(28);
    private final JTextField victimsField = new JTextField(28);
    private final JTextField suspectsField = new JTextField(28);
    private final JTextField destinationField = new JTextField(28);

    // HTML Report Metadata block (relatorio.htm / caseinformation.htm)
    private final JTextField orgHeaderField = new JTextField(28);
    private final JTextField reportNumberField = new JTextField(28);
    private final JTextField reportDateField = new JTextField(28);
    private final JTextField reportTitleField = new JTextField(28);
    private final JTextArea signatoriesArea = new JTextArea(2, 28);
    private final JTextField requestRefField = new JTextField(28);
    private final JTextField protocolNumberField = new JTextField(28);
    private final JTextField protocolDateField = new JTextField(28);
    private final JTextArea examinedMaterialArea = new JTextArea(2, 28);

    // Tool & parameters block
    private final JTextField extractionConfigField = new JTextField(28);
    private final JTextArea appliedFiltersArea = new JTextArea(3, 28);

    // Provenance badges
    private final JLabel badgeManufacturer = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeModel = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeImei = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeSerial = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeOs = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeWhatsapp = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeCollection = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$
    private final JLabel badgeTool = ChatCapturePanel.plainLabel(""); //$NON-NLS-1$

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
                        ? "Cadastro e Confer\u00eancia \u2014 Laudo T\u00e9cnico (HTML + PDF) e Cadeia de Cust\u00f3dia" //$NON-NLS-1$
                        : "Confer\u00eancia pr\u00e9-" + operationLabel + " \u2014 Dados do Laudo T\u00e9cnico e Cadeia de Cust\u00f3dia"); //$NON-NLS-1$ //$NON-NLS-2$
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(new Color(0x10, 0x2B, 0x35));
        JLabel subtitle = ChatCapturePanel.plainLabel(
                "Campos pr\u00e9-preenchidos com os dados salvos na pasta e com o report.pdf da origem (quando dispon\u00edvel). Confira ou complemente o que faltar."); //$NON-NLS-1$
        subtitle.setForeground(new Color(0x4A, 0x5A, 0x62));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        appliedFiltersArea.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        appliedFiltersArea.setLineWrap(true);
        appliedFiltersArea.setWrapStyleWord(true);
        signatoriesArea.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        signatoriesArea.setLineWrap(true);
        signatoriesArea.setWrapStyleWord(true);
        examinedMaterialArea.putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
        examinedMaterialArea.setLineWrap(true);
        examinedMaterialArea.setWrapStyleWord(true);
        storageCombo.setRenderer(new PlainComboRenderer());

        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;

        // --- Secao 1: report.pdf original ---
        row = addSectionHeader(form, row, "1. Origem e Relat\u00f3rio Original (report.pdf)"); //$NON-NLS-1$
        JPanel originBar = new JPanel(new BorderLayout(6, 4));
        originBar.add(originPdfStatusLabel, BorderLayout.CENTER);
        JButton reloadPdfBtn = new JButton("Recarregar do report.pdf"); //$NON-NLS-1$
        reloadPdfBtn.setEnabled(originFolder != null);
        reloadPdfBtn.addActionListener(e -> reloadFromOriginPdf());
        originBar.add(reloadPdfBtn, BorderLayout.EAST);
        row = addFullWidth(form, row, originBar);
        row = addFullWidth(form, row, copyOriginPdfCheck);
        row = addFullWidth(form, row, unifiedPdfCheck);

        // --- Secao 2: Identificacao do Dispositivo ---
        row = addSectionHeader(form, row, "2. Identifica\u00e7\u00e3o do Dispositivo (lido do report.pdf + complementa\u00e7\u00e3o)"); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Fabricante:", manufacturerField, badgeManufacturer); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Modelo / Collected Device:", modelField, badgeModel); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "IMEI:", imeiField, badgeImei); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "N\u00famero de s\u00e9rie:", serialField, badgeSerial); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Vers\u00e3o do SO:", osVersionField, badgeOs); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Vers\u00e3o do WhatsApp:", whatsappVersionField, badgeWhatsapp); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "C\u00f3digo da coleta:", collectionCodeField, badgeCollection); //$NON-NLS-1$
        row = addFieldWithBadge(form, row, "Ferramenta extr. original:", originToolField, badgeTool); //$NON-NLS-1$

        // --- Secao 3: Cadeia de Custodia (Chain Of Custody) ---
        row = addSectionHeader(form, row, "3. Cadeia de Cust\u00f3dia (Chain Of Custody)"); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quem coletou:", collectedByField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quem analisou:", analyzedByField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quando (coleta):", collectedAtField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Quando (an\u00e1lise):", analyzedAtField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Onde (local):", locationField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Como foi armazenado:", storageCombo); //$NON-NLS-1$
        row = addLabeledComponent(form, row, storageCustomLabel, storageCustomField);
        row = addSimpleField(form, row, "N\u00ba do Lacre / Refer\u00eancia:", sealRefField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Respons\u00e1vel pelo armazenamento:", storageResponsibleField); //$NON-NLS-1$
        row = addSimpleField(form, row, "N\u00ba do caso (Case number):", caseNumberField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Autoriza\u00e7\u00e3o Legal:", legalAuthorizationField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Nome completo das v\u00edtimas:", victimsField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Nome completo dos suspeitos:", suspectsField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Destino da evid\u00eancia:", destinationField); //$NON-NLS-1$

        // --- Secao 4: Dados do Relatorio HTML (relatorio.htm) ---
        row = addSectionHeader(form, row, "4. Dados do Relat\u00f3rio HTML (relatorio.htm / caseinformation.htm)"); //$NON-NLS-1$
        row = addSimpleField(form, row, "\u00d3rg\u00e3o / Cabe\u00e7alho:", orgHeaderField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Laudo n\u00ba:", reportNumberField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Data do laudo:", reportDateField); //$NON-NLS-1$
        row = addSimpleField(form, row, "T\u00edtulo:", reportTitleField); //$NON-NLS-1$
        JScrollPane sigScroll = new JScrollPane(signatoriesArea);
        sigScroll.setPreferredSize(new Dimension(360, 48));
        row = addSimpleField(form, row, "Signat\u00e1rio(s):", sigScroll); //$NON-NLS-1$
        row = addSimpleField(form, row, "Solicita\u00e7\u00e3o:", requestRefField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Protocolo Criminal\u00edstica n\u00ba:", protocolNumberField); //$NON-NLS-1$
        row = addSimpleField(form, row, "Data do Protocolo:", protocolDateField); //$NON-NLS-1$
        JScrollPane matScroll = new JScrollPane(examinedMaterialArea);
        matScroll.setPreferredSize(new Dimension(360, 48));
        row = addSimpleField(form, row, "Material examinado:", matScroll); //$NON-NLS-1$

        // --- Secao 5: Ferramenta e Parametros no IPED ---
        row = addSectionHeader(form, row, "5. Ferramenta IPED (4.3.1) e Par\u00e2metros / Filtros"); //$NON-NLS-1$
        row = addSimpleField(form, row, "Configura\u00e7\u00e3o de extra\u00e7\u00e3o:", extractionConfigField); //$NON-NLS-1$
        JScrollPane filterScroll = new JScrollPane(appliedFiltersArea);
        filterScroll.setPreferredSize(new Dimension(360, 64));
        row = addSimpleField(form, row, "Filtros aplicados no IPED:", filterScroll); //$NON-NLS-1$

        storageCombo.addActionListener(e -> updateStorageControls());
        copyOriginPdfCheck.addActionListener(e -> updatePdfControls());

        loadRecordIntoFields();

        JScrollPane scroll = new JScrollPane(form);
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
        setSize(Math.max(MIN_WIDTH, Math.min(840, getWidth() + 24)), Math.max(MIN_HEIGHT, Math.min(760, getHeight() + 12)));
        setLocationRelativeTo(owner);
    }

    private void loadRecordIntoFields() {
        if (record.originReportPdfPresent) {
            String shortSha = record.originReportPdfSha256.length() > 16
                    ? record.originReportPdfSha256.substring(0, 16) + "..." //$NON-NLS-1$
                    : record.originReportPdfSha256;
            originPdfStatusLabel.setText("report.pdf localizado na origem (SHA-256: " + shortSha + ") \u2014 campos faltantes ser\u00e3o complementados no report do plugin."); //$NON-NLS-1$ //$NON-NLS-2$
            originPdfStatusLabel.setForeground(new Color(0x0A, 0x5D, 0x3B));
        } else {
            originPdfStatusLabel.setText("Sem report.pdf na origem \u2014 ser\u00e1 gerado apenas o report pr\u00f3prio do plugin (HTML + PDF)."); //$NON-NLS-1$
            originPdfStatusLabel.setForeground(new Color(0x7A, 0x56, 0x00));
        }
        copyOriginPdfCheck.setSelected(record.copyOriginReportPdf && record.originReportPdfPresent);
        copyOriginPdfCheck.setEnabled(record.originReportPdfPresent);
        unifiedPdfCheck.setSelected(record.generateUnifiedPdf && record.originReportPdfPresent);

        manufacturerField.setText(record.deviceManufacturer);
        modelField.setText(record.deviceModel);
        imeiField.setText(record.deviceImei);
        serialField.setText(record.deviceSerialNumber);
        osVersionField.setText(record.deviceOsVersion);
        whatsappVersionField.setText(record.whatsappVersion);
        collectionCodeField.setText(record.collectionCode);
        originToolField.setText(record.originExtractionTool);

        collectedByField.setText(record.collectedBy);
        analyzedByField.setText(record.analyzedBy);
        collectedAtField.setText(record.collectedAt);
        analyzedAtField.setText(record.analyzedAt.isBlank() ? CustodyCaseRecord.nowLocalTimestamp() : record.analyzedAt);
        locationField.setText(record.custodyLocation);
        storageCombo.setSelectedItem(record.storageType == null ? CustodyCaseRecord.StorageType.PASTA : record.storageType);
        storageCustomField.setText(record.storageCustomText);
        sealRefField.setText(record.storageSealReference);
        storageResponsibleField.setText(record.storageResponsible);
        caseNumberField.setText(record.caseNumber);
        legalAuthorizationField.setText(record.legalAuthorization);
        victimsField.setText(record.victimsFullName);
        suspectsField.setText(record.suspectsFullName);
        destinationField.setText(record.evidenceDestination);

        orgHeaderField.setText(record.organizationHeader);
        reportNumberField.setText(record.reportNumber);
        reportDateField.setText(record.reportDate);
        reportTitleField.setText(record.reportTitle);
        signatoriesArea.setText(record.signatories);
        requestRefField.setText(record.requestReference);
        protocolNumberField.setText(record.forensicProtocolNumber);
        protocolDateField.setText(record.forensicProtocolDate);
        examinedMaterialArea.setText(record.examinedMaterial);

        extractionConfigField.setText(record.extractionConfig);
        appliedFiltersArea.setText(record.appliedFilters);

        refreshProvenanceBadges();
        updateStorageControls();
        updatePdfControls();
    }

    private void refreshProvenanceBadges() {
        setBadge(badgeManufacturer, "deviceManufacturer", manufacturerField.getText()); //$NON-NLS-1$
        setBadge(badgeModel, "deviceModel", modelField.getText()); //$NON-NLS-1$
        setBadge(badgeImei, "deviceImei", imeiField.getText()); //$NON-NLS-1$
        setBadge(badgeSerial, "deviceSerialNumber", serialField.getText()); //$NON-NLS-1$
        setBadge(badgeOs, "deviceOsVersion", osVersionField.getText()); //$NON-NLS-1$
        setBadge(badgeWhatsapp, "whatsappVersion", whatsappVersionField.getText()); //$NON-NLS-1$
        setBadge(badgeCollection, "collectionCode", collectionCodeField.getText()); //$NON-NLS-1$
        setBadge(badgeTool, "originExtractionTool", originToolField.getText()); //$NON-NLS-1$
    }

    private void setBadge(JLabel badge, String key, String value) {
        if (record.originReportPdfPresent && record.fieldsExtractedFromOriginPdf.contains(key)) {
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
        return differs(record.deviceModel, p.model)
                || differs(record.deviceSerialNumber, p.serialNumber)
                || differs(record.deviceOsVersion, p.osVersion)
                || differs(record.collectionCode, p.collectionCode)
                || differs(record.caseNumber, p.caseNumber)
                || differs(record.legalAuthorization, p.legalAuthorization)
                || differs(record.victimsFullName, p.victimsFullName)
                || differs(record.suspectsFullName, p.suspectsFullName)
                || differs(record.evidenceDestination, p.evidenceDestination);
    }

    private static boolean differs(String existing, String incoming) {
        return existing != null && !existing.isBlank()
                && incoming != null && !incoming.isBlank()
                && !existing.trim().equals(incoming.trim());
    }

    private void updateStorageControls() {
        CustodyCaseRecord.StorageType selected = (CustodyCaseRecord.StorageType) storageCombo.getSelectedItem();
        boolean isCustom = selected == CustodyCaseRecord.StorageType.CUSTOM;
        storageCustomLabel.setEnabled(isCustom);
        storageCustomField.setEnabled(isCustom);
        storageCustomField.setEditable(isCustom);
    }

    private void updatePdfControls() {
        boolean canUnify = record.originReportPdfPresent && copyOriginPdfCheck.isSelected();
        unifiedPdfCheck.setEnabled(canUnify);
        if (!canUnify) {
            unifiedPdfCheck.setSelected(false);
        }
    }

    private void applyFieldsToRecord() {
        record.copyOriginReportPdf = copyOriginPdfCheck.isEnabled() && copyOriginPdfCheck.isSelected();
        record.generateUnifiedPdf = unifiedPdfCheck.isEnabled() && unifiedPdfCheck.isSelected();

        record.deviceManufacturer = CustodyCaseRecord.clamp(manufacturerField.getText(), 512);
        record.deviceModel = CustodyCaseRecord.clamp(modelField.getText(), 512);
        record.deviceImei = CustodyCaseRecord.clamp(imeiField.getText(), 512);
        record.deviceSerialNumber = CustodyCaseRecord.clamp(serialField.getText(), 512);
        record.deviceOsVersion = CustodyCaseRecord.clamp(osVersionField.getText(), 512);
        record.whatsappVersion = CustodyCaseRecord.clamp(whatsappVersionField.getText(), 512);
        record.collectionCode = CustodyCaseRecord.clamp(collectionCodeField.getText(), 512);
        record.originExtractionTool = CustodyCaseRecord.clamp(originToolField.getText(), 512);

        record.collectedBy = CustodyCaseRecord.clamp(collectedByField.getText(), 512);
        record.analyzedBy = CustodyCaseRecord.clamp(analyzedByField.getText(), 512);
        record.collectedAt = CustodyCaseRecord.clamp(collectedAtField.getText(), 512);
        record.analyzedAt = CustodyCaseRecord.clamp(analyzedAtField.getText(), 512);
        record.custodyLocation = CustodyCaseRecord.clamp(locationField.getText(), 512);
        CustodyCaseRecord.StorageType st = (CustodyCaseRecord.StorageType) storageCombo.getSelectedItem();
        record.storageType = st == null ? CustodyCaseRecord.StorageType.PASTA : st;
        record.storageCustomText = CustodyCaseRecord.clamp(storageCustomField.getText(), 512);
        record.storageSealReference = CustodyCaseRecord.clamp(sealRefField.getText(), 512);
        record.storageResponsible = CustodyCaseRecord.clamp(storageResponsibleField.getText(), 512);
        record.caseNumber = CustodyCaseRecord.clamp(caseNumberField.getText(), 512);
        record.legalAuthorization = CustodyCaseRecord.clamp(legalAuthorizationField.getText(), 512);
        record.victimsFullName = CustodyCaseRecord.clamp(victimsField.getText(), 512);
        record.suspectsFullName = CustodyCaseRecord.clamp(suspectsField.getText(), 512);
        record.evidenceDestination = CustodyCaseRecord.clamp(destinationField.getText(), 512);

        record.organizationHeader = CustodyCaseRecord.clamp(orgHeaderField.getText(), 512);
        record.reportNumber = CustodyCaseRecord.clamp(reportNumberField.getText(), 512);
        record.reportDate = CustodyCaseRecord.clamp(reportDateField.getText(), 512);
        record.reportTitle = CustodyCaseRecord.clamp(reportTitleField.getText(), 512);
        record.signatories = CustodyCaseRecord.clamp(signatoriesArea.getText(), 4096);
        record.requestReference = CustodyCaseRecord.clamp(requestRefField.getText(), 512);
        record.forensicProtocolNumber = CustodyCaseRecord.clamp(protocolNumberField.getText(), 512);
        record.forensicProtocolDate = CustodyCaseRecord.clamp(protocolDateField.getText(), 512);
        record.examinedMaterial = CustodyCaseRecord.clamp(examinedMaterialArea.getText(), 4096);

        record.extractionConfig = CustodyCaseRecord.clamp(extractionConfigField.getText(), 4096);
        record.appliedFilters = CustodyCaseRecord.clamp(appliedFiltersArea.getText(), 4096);
    }

    boolean isConfirmed() {
        return confirmed;
    }

    JComboBox<CustodyCaseRecord.StorageType> getStorageComboForTest() {
        return storageCombo;
    }

    JTextField getStorageCustomFieldForTest() {
        return storageCustomField;
    }

    private static int addSectionHeader(JPanel panel, int row, String title) {
        JLabel lbl = ChatCapturePanel.plainLabel(title);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11.5f));
        lbl.setForeground(new Color(0x0B, 0x6B, 0x5D));
        lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xCC, 0xD8, 0xDD)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(row == 0 ? 2 : 10, 2, 4, 2);
        panel.add(lbl, gbc);
        return row + 1;
    }

    private static int addFullWidth(JPanel panel, int row, JComponent comp) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        panel.add(comp, gbc);
        return row + 1;
    }

    private static int addSimpleField(JPanel panel, int row, String label, JComponent field) {
        return addLabeledComponent(panel, row, ChatCapturePanel.plainLabel(label), field);
    }

    private static int addLabeledComponent(JPanel panel, int row, JLabel label, JComponent field) {
        GridBagConstraints g1 = new GridBagConstraints();
        g1.gridx = 0;
        g1.gridy = row;
        g1.anchor = GridBagConstraints.WEST;
        g1.insets = new Insets(2, 4, 2, 6);
        panel.add(label, g1);

        GridBagConstraints g2 = new GridBagConstraints();
        g2.gridx = 1;
        g2.gridy = row;
        g2.gridwidth = 2;
        g2.weightx = 1.0;
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.insets = new Insets(2, 2, 2, 4);
        panel.add(field, g2);
        return row + 1;
    }

    private static int addFieldWithBadge(JPanel panel, int row, String label, JTextField field, JLabel badge) {
        GridBagConstraints g1 = new GridBagConstraints();
        g1.gridx = 0;
        g1.gridy = row;
        g1.anchor = GridBagConstraints.WEST;
        g1.insets = new Insets(2, 4, 2, 6);
        panel.add(ChatCapturePanel.plainLabel(label), g1);

        GridBagConstraints g2 = new GridBagConstraints();
        g2.gridx = 1;
        g2.gridy = row;
        g2.weightx = 1.0;
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.insets = new Insets(2, 2, 2, 6);
        panel.add(field, g2);

        GridBagConstraints g3 = new GridBagConstraints();
        g3.gridx = 2;
        g3.gridy = row;
        g3.anchor = GridBagConstraints.WEST;
        g3.insets = new Insets(2, 2, 2, 4);
        panel.add(badge, g3);
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
            putClientProperty("html.disable", Boolean.TRUE); //$NON-NLS-1$
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }
}
