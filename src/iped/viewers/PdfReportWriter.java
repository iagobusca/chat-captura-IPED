/*
 * Chat Captura IPED - Gerador Nativo de Relatorio Tecnico em PDF (Apache PDFBox 2.0.27)
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Generates {@code laudo/report-plugin.pdf} directly with Apache PDFBox 2.0.x shipped in IPED 4.3.1.
 * Handles A4 automatic pagination, line wrapping for 64-char SHA-256 hashes, Unicode TTF fonts
 * with safe WinAnsi fallback, and optional merging with {@code laudo/report-origem.pdf}.
 */
final class PdfReportWriter {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_X = 44f;
    private static final float MARGIN_TOP = 48f;
    private static final float MARGIN_BOTTOM = 52f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2f * MARGIN_X);
    private static final long MAX_OUTPUT_PDF_BYTES = 64L * 1024L * 1024L;

    private static final Color COLOR_NAVY = new Color(16, 43, 53);
    private static final Color COLOR_TEAL = new Color(11, 107, 93);
    private static final Color COLOR_MUTED = new Color(85, 98, 106);
    private static final Color COLOR_TEXT = new Color(25, 32, 36);
    private static final Color COLOR_LINE = new Color(205, 214, 219);
    private static final Color COLOR_BG_SUBTLE = new Color(243, 246, 248);

    private PdfReportWriter() {
    }

    static byte[] buildPluginReportPdfBytes(
            CustodyCaseRecord record,
            List<CaptureAuditLogManager.AuditLogEntry> auditLog,
            boolean logChainValid,
            String outputRootDisplay,
            String rootHash) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Laudo / Relatorio Tecnico Forense - Chat Captura IPED"); //$NON-NLS-1$
            info.setAuthor(nonBlank(record.analyzedBy, "Chat Captura IPED")); //$NON-NLS-1$
            info.setCreator("Chat Captura IPED " + nonBlank(record.pluginVersion, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION)); //$NON-NLS-1$
            info.setSubject("Relatorio Tecnico, Cadeia de Custodia, Hashes SHA-256 e Log de Capturas"); //$NON-NLS-1$

            FontSet fonts = loadFonts(doc);
            PdfCanvas canvas = new PdfCanvas(doc, fonts);

            // Header Banner
            canvas.drawHeaderBanner(
                    "LAUDO / RELAT\u00d3RIO T\u00c9CNICO DE EXTRA\u00c7\u00c3O E CUST\u00d3DIA", //$NON-NLS-1$
                    "Chat Captura IPED v" + nonBlank(record.pluginVersion, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION) //$NON-NLS-1$
                            + "  |  IPED " + nonBlank(record.ipedVersion, "4.3.1") //$NON-NLS-1$ //$NON-NLS-2$
                            + "  |  Gerado em: " + CustodyCaseRecord.nowLocalTimestamp()); //$NON-NLS-1$

            // 1. Vinculo com report.pdf original
            canvas.sectionTitle("1. V\u00ednculo com o Relat\u00f3rio Original (report.pdf) e Escopo"); //$NON-NLS-1$
            if (record.originReportPdfPresent) {
                canvas.keyValue("Status do report.pdf original", //$NON-NLS-1$
                        "Localizado e lido (" + nonBlank(record.originReportPdfParseStatus, "ok") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                canvas.keyValue("Modo de composi\u00e7\u00e3o", //$NON-NLS-1$
                        "Dados existentes absorvidos do report.pdf original + itens faltantes complementados por este relat\u00f3rio do plugin."); //$NON-NLS-1$
                canvas.keyValueMono("SHA-256 do report.pdf original", nonBlank(record.originReportPdfSha256, "-")); //$NON-NLS-1$ //$NON-NLS-2$
                if (!record.originReportPdfMd5.isEmpty()) {
                    canvas.keyValueMono("MD5 do report.pdf original (informativo)", record.originReportPdfMd5); //$NON-NLS-1$
                }
                canvas.keyValue("C\u00f3pia intacta preservada", //$NON-NLS-1$
                        record.originReportPdfCopied
                                ? "Sim -> laudo/" + CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE //$NON-NLS-1$
                                        + " (SHA-256 verificado igual ao original)" //$NON-NLS-1$
                                : "N\u00e3o copiada nesta execu\u00e7\u00e3o"); //$NON-NLS-1$
            } else {
                canvas.keyValue("Status do report.pdf original", //$NON-NLS-1$
                        "N\u00e3o fornecido / n\u00e3o localizado na origem"); //$NON-NLS-1$
                canvas.keyValue("Modo de composi\u00e7\u00e3o", //$NON-NLS-1$
                        "Relat\u00f3rio gerado exclusivamente pelo Plugin Chat Captura IPED."); //$NON-NLS-1$
            }
            canvas.keyValue("Pasta raiz do caso", nonBlank(outputRootDisplay, "-")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueMono("Hash geral do conjunto (rootHash)", nonBlank(rootHash, "-")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.spacer(6f);

            // 2. Identificacao do Dispositivo
            canvas.sectionTitle("2. Identifica\u00e7\u00e3o do Dispositivo"); //$NON-NLS-1$
            canvas.keyValueWithOrigin("Fabricante", record.deviceManufacturer, record.provenanceTag("deviceManufacturer", record.deviceManufacturer)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("Modelo", record.deviceModel, record.provenanceTag("deviceModel", record.deviceModel)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("IMEI", record.deviceImei, record.provenanceTag("deviceImei", record.deviceImei)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("N\u00famero de s\u00e9rie", record.deviceSerialNumber, record.provenanceTag("deviceSerialNumber", record.deviceSerialNumber)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("Vers\u00e3o do SO", record.deviceOsVersion, record.provenanceTag("deviceOsVersion", record.deviceOsVersion)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("Vers\u00e3o do WhatsApp", record.whatsappVersion, record.provenanceTag("whatsappVersion", record.whatsappVersion)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("C\u00f3digo da coleta", record.collectionCode, record.provenanceTag("collectionCode", record.collectionCode)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValueWithOrigin("Ferramenta de extr. original", record.originExtractionTool, record.provenanceTag("originExtractionTool", record.originExtractionTool)); //$NON-NLS-1$ //$NON-NLS-2$
            if (!record.originStartExtraction.isEmpty() || !record.originEndExtraction.isEmpty()) {
                canvas.keyValue("Janela da extra\u00e7\u00e3o original", //$NON-NLS-1$
                        nonBlank(record.originStartExtraction, "-") + "  at\u00e9  " + nonBlank(record.originEndExtraction, "-")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            canvas.spacer(6f);

            // 3. Cadeia de Custodia
            canvas.sectionTitle("3. Cadeia de Cust\u00f3dia"); //$NON-NLS-1$
            canvas.keyValue("Quem coletou", nonBlank(record.collectedBy, "N\u00e3o informado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Quem analisou", nonBlank(record.analyzedBy, "N\u00e3o informado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Quando (coleta)", nonBlank(record.collectedAt, "N\u00e3o informado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Quando (an\u00e1lise)", nonBlank(record.analyzedAt, "N\u00e3o informado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Onde (local)", nonBlank(record.custodyLocation, "N\u00e3o informado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Como foi armazenado", record.formattedStorageDescription()); //$NON-NLS-1$
            canvas.spacer(6f);

            // 4. Ferramenta e Parametros
            canvas.sectionTitle("4. Ferramenta e Par\u00e2metros de Extra\u00e7\u00e3o"); //$NON-NLS-1$
            canvas.keyValue("Ferramenta forense", "IPED (Indexador e Processador de Evid\u00eancias Digitais) v" + nonBlank(record.ipedVersion, "4.3.1")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            canvas.keyValue("Plugin utilizado", "Chat Captura IPED v" + nonBlank(record.pluginVersion, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION)); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Configura\u00e7\u00e3o de extra\u00e7\u00e3o", nonBlank(record.extractionConfig, "-")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Filtros aplicados no IPED", nonBlank(record.appliedFilters, "Nenhum filtro adicional declarado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Pasta de origem vinculada", nonBlank(record.originFolderName, "N\u00e3o vinculada")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.keyValue("Caso IPED pai", nonBlank(record.parentIpedFolderName, "N\u00e3o vinculado")); //$NON-NLS-1$ //$NON-NLS-2$
            canvas.spacer(6f);

            // 5. Hashes dos Arquivos Originais
            canvas.sectionTitle("5. Hashes dos Arquivos Originais (Origem Custodiada e Fonte IPED)"); //$NON-NLS-1$
            boolean anyOriginHash = false;
            if (!record.originReportPdfSha256.isEmpty()) {
                canvas.keyValueMono("report.pdf (SHA-256)", record.originReportPdfSha256); //$NON-NLS-1$
                anyOriginHash = true;
            }
            if (!record.originHashsTxtSha256.isEmpty()) {
                canvas.keyValueMono("Hashs.txt (SHA-256)", record.originHashsTxtSha256); //$NON-NLS-1$
                anyOriginHash = true;
            }
            if (!record.originHashsAvillaSha256.isEmpty()) {
                canvas.keyValueMono("Hashs.avilla (SHA-256)", record.originHashsAvillaSha256); //$NON-NLS-1$
                anyOriginHash = true;
            }
            if (!record.originMsgstoreSha256.isEmpty()) {
                canvas.keyValueMono("msgstore.db [" + nonBlank(record.originMsgstorePath, "origem") + "]", record.originMsgstoreSha256); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                anyOriginHash = true;
            }
            if (!record.originWaDbSha256.isEmpty()) {
                canvas.keyValueMono("wa.db (SHA-256)", record.originWaDbSha256); //$NON-NLS-1$
                anyOriginHash = true;
            }
            if (!record.originKeySha256.isEmpty()) {
                canvas.keyValueMono("Chave key (SHA-256)", record.originKeySha256); //$NON-NLS-1$
                anyOriginHash = true;
            }
            if (!anyOriginHash) {
                canvas.paragraph("Nenhum arquivo de origem Avilla custodiado vinculado a este caso."); //$NON-NLS-1$
            }
            canvas.spacer(6f);

            // 6. Capturas e Hashes dos Arquivos Exportados
            canvas.sectionTitle("6. Capturas Realizadas e Hashes SHA-256 dos Arquivos Exportados"); //$NON-NLS-1$
            if (record.knownCaptures.isEmpty()) {
                canvas.paragraph("Nenhuma captura registrada na pasta de sa\u00edda at\u00e9 o momento."); //$NON-NLS-1$
            } else {
                int idx = 1;
                for (Map.Entry<String, CustodyCaseRecord.CaptureSnapshot> e : record.knownCaptures.entrySet()) {
                    CustodyCaseRecord.CaptureSnapshot snap = e.getValue();
                    canvas.subHeading(idx++ + ". " + snap.folder + " (" + nonBlank(snap.format, "captura") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    canvas.keyValue("Conversa / Item fonte", nonBlank(snap.sourceItemName, snap.title)); //$NON-NLS-1$
                    if (!snap.sourceItemPath.isEmpty()) {
                        canvas.keyValueMono("Caminho fonte IPED", snap.sourceItemPath); //$NON-NLS-1$
                    }
                    if (!snap.sourceItemSha256.isEmpty()) {
                        canvas.keyValueMono("Hash original do item (SHA-256)", snap.sourceItemSha256); //$NON-NLS-1$
                    } else if (!snap.sourceItemHash.isEmpty()) {
                        canvas.keyValueMono("Hash original do item (IPED)", snap.sourceItemHash); //$NON-NLS-1$
                    }
                    if (!snap.sourceDbSha256.isEmpty()) {
                        canvas.keyValueMono("Banco aberto / vinculado (msgstore.db)", snap.sourceDbSha256); //$NON-NLS-1$
                    }
                    canvas.keyValue("Intervalo selecionado (\u00e2ncoras)", //$NON-NLS-1$
                            nonBlank(snap.startAnchor, "-") + "  ->  " + nonBlank(snap.endAnchor, "-")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    canvas.keyValue("Contagem por tipo", snap.counts.toSummaryLine("OK") //$NON-NLS-1$ //$NON-NLS-2$
                            + "  |  Total arquivos=" + snap.fileCount + "  (" + snap.totalBytes + " bytes)"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    canvas.keyValue("Censura / Anota\u00e7\u00f5es / N3", //$NON-NLS-1$
                            "Blur=" + (snap.censorshipEnabled ? ("SIM (" + snap.censorshipBlur + "%, sim " + snap.censorshipSimilarity + "%, msgs=" + snap.censorshipMatchedCount + ")") : "N\u00c3O") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                                    + "  |  Anota\u00e7\u00f5es=" + (snap.annotationsEnabled ? "SIM" : "N\u00c3O") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                    + (snap.n3Status.isEmpty() ? "" : "  |  N3=" + snap.n3Status)); //$NON-NLS-1$ //$NON-NLS-2$
                    canvas.keyValueMono("SHA-256 whatsapp.html", nonBlank(snap.htmlSha256, "-")); //$NON-NLS-1$ //$NON-NLS-2$
                    if (!snap.messagesSha256.isEmpty()) {
                        canvas.keyValueMono("SHA-256 messages.json", snap.messagesSha256); //$NON-NLS-1$
                    }
                    if (!snap.manifestSha256.isEmpty()) {
                        canvas.keyValueMono("SHA-256 manifest.json", snap.manifestSha256); //$NON-NLS-1$
                    }
                    if (!snap.integritySha256.isEmpty()) {
                        canvas.keyValueMono("SHA-256 integridade.json", snap.integritySha256); //$NON-NLS-1$
                    }
                    if (!snap.originBindingSha256.isEmpty()) {
                        canvas.keyValueMono("SHA-256 origin-binding.json", snap.originBindingSha256); //$NON-NLS-1$
                    }
                    if (!snap.captureRootHash.isEmpty()) {
                        canvas.keyValueMono("captureRootHash", snap.captureRootHash); //$NON-NLS-1$
                    }
                    if (!snap.contentRootHash.isEmpty()) {
                        canvas.keyValueMono("contentRootHash", snap.contentRootHash); //$NON-NLS-1$
                    }
                    canvas.keyValueMono("folderHash (pasta completa)", nonBlank(snap.folderHash, "-")); //$NON-NLS-1$ //$NON-NLS-2$
                    canvas.spacer(4f);
                }
            }
            canvas.spacer(6f);

            // 7. Passo a Passo Cronologico da Extracao
            canvas.sectionTitle("7. Passo a Passo Cronol\u00f3gico da Extra\u00e7\u00e3o (Data e Hor\u00e1rio de Cada Etapa)"); //$NON-NLS-1$
            if (record.extractionSteps.isEmpty()) {
                canvas.paragraph("Nenhuma etapa cronol\u00f3gica registrada ainda."); //$NON-NLS-1$
            } else {
                for (CustodyCaseRecord.ExtractionStep step : record.extractionSteps) {
                    String prefix = "[" + step.timestampLocal + "] [" + step.operation + "]" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + (step.captureFolder.isEmpty() ? "" : " (" + step.captureFolder + ")") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + " " + step.stage + ": " + step.detail; //$NON-NLS-1$ //$NON-NLS-2$
                    canvas.bulletMonoOrText(prefix, false);
                }
            }
            canvas.spacer(6f);

            // 8. Log Encadeado de Auditoria de Capturas
            canvas.sectionTitle("8. Log Audit\u00e1vel de Capturas (START / RE-EXTRAIR / ADICIONADO / DELETADO)"); //$NON-NLS-1$
            canvas.keyValue("Integridade da cadeia SHA-256 do log", //$NON-NLS-1$
                    logChainValid ? "CONFERE (" + auditLog.size() + " registro(s) encadeados)" : "ATENC\u00c3O: CADEIA DIVERGENTE"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (auditLog.isEmpty()) {
                canvas.paragraph("Nenhum evento registrado em registro-capturas.jsonl."); //$NON-NLS-1$
            } else {
                for (CaptureAuditLogManager.AuditLogEntry entry : auditLog) {
                    canvas.bulletMonoOrText(entry.toReadableLine(), true);
                }
            }

            canvas.finishWithPageNumbers();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    static void generatePluginReportPdf(
            Path outputRoot,
            Path targetPdf,
            CustodyCaseRecord record,
            List<CaptureAuditLogManager.AuditLogEntry> auditLog,
            boolean logChainValid,
            String rootHash) throws IOException {
        byte[] bytes = buildPluginReportPdfBytes(
                record,
                auditLog,
                logChainValid,
                outputRoot.toAbsolutePath().normalize().toString(),
                rootHash);
        CaptureIntegrity.writeBytesAtomically(outputRoot, targetPdf, bytes, MAX_OUTPUT_PDF_BYTES);
    }

    static void mergeUnifiedPdf(Path outputRoot, Path pluginPdf, Path originPdf, Path targetUnifiedPdf) throws IOException {
        if (pluginPdf == null || originPdf == null || targetUnifiedPdf == null) {
            return;
        }
        if (!CaptureIntegrity.isSafeRegularFile(pluginPdf) || !CaptureIntegrity.isSafeRegularFile(originPdf)) {
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.addSource(pluginPdf.toFile());
        merger.addSource(originPdf.toFile());
        merger.setDestinationStream(baos);
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        byte[] merged = baos.toByteArray();
        if (merged.length > MAX_OUTPUT_PDF_BYTES) {
            throw new IOException("PDF unificado excede o limite de 64 MiB."); //$NON-NLS-1$
        }
        CaptureIntegrity.writeBytesAtomically(outputRoot, targetUnifiedPdf, merged, MAX_OUTPUT_PDF_BYTES);
    }

    private static final class FontSet {
        final PDFont regular;
        final PDFont bold;
        final PDFont mono;
        final boolean unicodeRegular;
        final boolean unicodeBold;
        final boolean unicodeMono;

        FontSet(PDFont regular, boolean unicodeRegular, PDFont bold, boolean unicodeBold, PDFont mono, boolean unicodeMono) {
            this.regular = regular;
            this.unicodeRegular = unicodeRegular;
            this.bold = bold;
            this.unicodeBold = unicodeBold;
            this.mono = mono;
            this.unicodeMono = unicodeMono;
        }
    }

    private static FontSet loadFonts(PDDocument doc) {
        PDFont reg = loadSystemTtf(doc,
                "C:\\Windows\\Fonts\\arial.ttf", //$NON-NLS-1$
                "C:\\Windows\\Fonts\\segoeui.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"); //$NON-NLS-1$
        PDFont bold = loadSystemTtf(doc,
                "C:\\Windows\\Fonts\\arialbd.ttf", //$NON-NLS-1$
                "C:\\Windows\\Fonts\\segoeuib.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"); //$NON-NLS-1$
        PDFont mono = loadSystemTtf(doc,
                "C:\\Windows\\Fonts\\consola.ttf", //$NON-NLS-1$
                "C:\\Windows\\Fonts\\cour.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", //$NON-NLS-1$
                "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf"); //$NON-NLS-1$
        return new FontSet(
                reg != null ? reg : PDType1Font.HELVETICA, reg != null,
                bold != null ? bold : PDType1Font.HELVETICA_BOLD, bold != null,
                mono != null ? mono : PDType1Font.COURIER, mono != null);
    }

    private static PDFont loadSystemTtf(PDDocument doc, String... candidates) {
        for (String pathStr : candidates) {
            try {
                Path p = Paths.get(pathStr);
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    File f = p.toFile();
                    return PDType0Font.load(doc, f);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class PdfCanvas {
        private final PDDocument doc;
        private final FontSet fonts;
        private PDPageContentStream cs;
        private float y;

        PdfCanvas(PDDocument doc, FontSet fonts) throws IOException {
            this.doc = doc;
            this.fonts = fonts;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM) {
                newPage();
            }
        }

        void spacer(float h) throws IOException {
            y -= h;
            if (y < MARGIN_BOTTOM) {
                newPage();
            }
        }

        void drawHeaderBanner(String title, String subtitle) throws IOException {
            float bannerHeight = 52f;
            ensureSpace(bannerHeight + 12f);
            float bottomY = y - bannerHeight;
            cs.setNonStrokingColor(COLOR_NAVY);
            cs.addRect(MARGIN_X, bottomY, CONTENT_WIDTH, bannerHeight);
            cs.fill();

            cs.beginText();
            cs.setNonStrokingColor(Color.WHITE);
            cs.setFont(fonts.bold, 13f);
            cs.newLineAtOffset(MARGIN_X + 12f, y - 22f);
            cs.showText(safeText(title, fonts.bold, fonts.unicodeBold));
            cs.endText();

            cs.beginText();
            cs.setNonStrokingColor(new Color(210, 232, 238));
            cs.setFont(fonts.regular, 8.5f);
            cs.newLineAtOffset(MARGIN_X + 12f, y - 39f);
            cs.showText(safeText(subtitle, fonts.regular, fonts.unicodeRegular));
            cs.endText();

            y = bottomY - 14f;
        }

        void sectionTitle(String title) throws IOException {
            ensureSpace(26f);
            cs.setNonStrokingColor(COLOR_BG_SUBTLE);
            cs.addRect(MARGIN_X, y - 16f, CONTENT_WIDTH, 18f);
            cs.fill();

            cs.setNonStrokingColor(COLOR_TEAL);
            cs.addRect(MARGIN_X, y - 16f, 3.5f, 18f);
            cs.fill();

            cs.beginText();
            cs.setNonStrokingColor(COLOR_NAVY);
            cs.setFont(fonts.bold, 10.5f);
            cs.newLineAtOffset(MARGIN_X + 8f, y - 11.5f);
            cs.showText(safeText(title, fonts.bold, fonts.unicodeBold));
            cs.endText();

            y -= 24f;
        }

        void subHeading(String text) throws IOException {
            ensureSpace(18f);
            cs.setStrokingColor(COLOR_LINE);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGIN_X, y + 3f);
            cs.lineTo(MARGIN_X + CONTENT_WIDTH, y + 3f);
            cs.stroke();

            List<String> lines = wrapText(text, fonts.bold, fonts.unicodeBold, 9.5f, CONTENT_WIDTH);
            for (String ln : lines) {
                ensureSpace(13f);
                cs.beginText();
                cs.setNonStrokingColor(COLOR_TEAL);
                cs.setFont(fonts.bold, 9.5f);
                cs.newLineAtOffset(MARGIN_X, y - 9f);
                cs.showText(ln);
                cs.endText();
                y -= 13f;
            }
        }

        void keyValue(String key, String value) throws IOException {
            writeKeyValueInternal(key, nonBlank(value, "-"), null, false); //$NON-NLS-1$
        }

        void keyValueMono(String key, String value) throws IOException {
            writeKeyValueInternal(key, nonBlank(value, "-"), null, true); //$NON-NLS-1$
        }

        void keyValueWithOrigin(String key, String value, String originNote) throws IOException {
            writeKeyValueInternal(key, nonBlank(value, "N\u00e3o informado"), originNote, false); //$NON-NLS-1$
        }

        private void writeKeyValueInternal(String key, String value, String originNote, boolean monoValue) throws IOException {
            float labelW = 148f;
            float valX = MARGIN_X + labelW + 6f;
            float valW = CONTENT_WIDTH - labelW - 6f;

            PDFont vFont = monoValue ? fonts.mono : fonts.regular;
            boolean vUni = monoValue ? fonts.unicodeMono : fonts.unicodeRegular;
            float vSize = monoValue ? 7.8f : 8.8f;
            float lineH = monoValue ? 10.5f : 12f;

            List<String> keyLines = wrapText(key + ":", fonts.bold, fonts.unicodeBold, 8.6f, labelW); //$NON-NLS-1$
            List<String> valLines = wrapText(value, vFont, vUni, vSize, valW);
            List<String> noteLines = (originNote == null || originNote.isBlank())
                    ? java.util.Collections.emptyList()
                    : wrapText("[" + originNote + "]", fonts.regular, fonts.unicodeRegular, 7.4f, valW); //$NON-NLS-1$ //$NON-NLS-2$

            int rows = Math.max(keyLines.size(), valLines.size());
            float totalH = (rows * lineH) + (noteLines.size() * 9.5f) + 3f;
            ensureSpace(Math.min(totalH, 60f));

            float startY = y;
            for (int i = 0; i < rows; i++) {
                ensureSpace(lineH);
                if (i == 0) startY = y;
                if (i < keyLines.size()) {
                    cs.beginText();
                    cs.setNonStrokingColor(COLOR_MUTED);
                    cs.setFont(fonts.bold, 8.6f);
                    cs.newLineAtOffset(MARGIN_X + 2f, y - 9f);
                    cs.showText(keyLines.get(i));
                    cs.endText();
                }
                if (i < valLines.size()) {
                    cs.beginText();
                    cs.setNonStrokingColor(COLOR_TEXT);
                    cs.setFont(vFont, vSize);
                    cs.newLineAtOffset(valX, y - 9f);
                    cs.showText(valLines.get(i));
                    cs.endText();
                }
                y -= lineH;
            }
            for (String nl : noteLines) {
                ensureSpace(9.5f);
                cs.beginText();
                cs.setNonStrokingColor(COLOR_MUTED);
                cs.setFont(fonts.regular, 7.4f);
                cs.newLineAtOffset(valX, y - 7.5f);
                cs.showText(nl);
                cs.endText();
                y -= 9.5f;
            }
            if (startY == y) {
                y -= lineH;
            }
            y -= 2f;
        }

        void paragraph(String text) throws IOException {
            List<String> lines = wrapText(text, fonts.regular, fonts.unicodeRegular, 8.8f, CONTENT_WIDTH);
            for (String ln : lines) {
                ensureSpace(12f);
                cs.beginText();
                cs.setNonStrokingColor(COLOR_TEXT);
                cs.setFont(fonts.regular, 8.8f);
                cs.newLineAtOffset(MARGIN_X + 2f, y - 9f);
                cs.showText(ln);
                cs.endText();
                y -= 12f;
            }
            y -= 2f;
        }

        void bulletMonoOrText(String text, boolean mono) throws IOException {
            PDFont f = mono ? fonts.mono : fonts.regular;
            boolean u = mono ? fonts.unicodeMono : fonts.unicodeRegular;
            float size = mono ? 7.4f : 8.2f;
            float lh = mono ? 10f : 11f;
            List<String> lines = wrapText("- " + text, f, u, size, CONTENT_WIDTH - 4f); //$NON-NLS-1$
            for (String ln : lines) {
                ensureSpace(lh);
                cs.beginText();
                cs.setNonStrokingColor(COLOR_TEXT);
                cs.setFont(f, size);
                cs.newLineAtOffset(MARGIN_X + 4f, y - 8f);
                cs.showText(ln);
                cs.endText();
                y -= lh;
            }
            y -= 2f;
        }

        void finishWithPageNumbers() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(COLOR_LINE);
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN_X, 34f);
                    footer.lineTo(MARGIN_X + CONTENT_WIDTH, 34f);
                    footer.stroke();

                    String left = safeText("Chat Captura IPED - Relatorio Tecnico Forense (somente leitura / SHA-256)", //$NON-NLS-1$
                            fonts.regular, fonts.unicodeRegular);
                    String right = safeText("Pagina " + (i + 1) + " de " + total, fonts.regular, fonts.unicodeRegular); //$NON-NLS-1$ //$NON-NLS-2$
                    float rightW = stringWidthSafe(fonts.regular, 8f, right);

                    footer.beginText();
                    footer.setNonStrokingColor(COLOR_MUTED);
                    footer.setFont(fonts.regular, 8f);
                    footer.newLineAtOffset(MARGIN_X, 22f);
                    footer.showText(left);
                    footer.endText();

                    footer.beginText();
                    footer.setNonStrokingColor(COLOR_MUTED);
                    footer.setFont(fonts.regular, 8f);
                    footer.newLineAtOffset(MARGIN_X + CONTENT_WIDTH - rightW, 22f);
                    footer.showText(right);
                    footer.endText();
                }
            }
        }
    }

    private static List<String> wrapText(String raw, PDFont font, boolean unicodeFont, float fontSize, float maxWidth) {
        List<String> out = new ArrayList<>();
        String sanitized = safeText(raw, font, unicodeFont);
        String[] paragraphs = sanitized.split("\\r?\\n"); //$NON-NLS-1$
        for (String para : paragraphs) {
            if (para.isEmpty()) {
                out.add(""); //$NON-NLS-1$
                continue;
            }
            String[] words = para.split(" "); //$NON-NLS-1$
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                String candidate = line.length() == 0 ? w : line + " " + w; //$NON-NLS-1$
                if (stringWidthSafe(font, fontSize, candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    // Word itself wider than maxWidth (e.g. 64-char SHA-256 or long path): hard-break by chars
                    if (stringWidthSafe(font, fontSize, w) > maxWidth) {
                        StringBuilder chunk = new StringBuilder();
                        for (int ci = 0; ci < w.length(); ci++) {
                            char ch = w.charAt(ci);
                            if (chunk.length() > 0 && stringWidthSafe(font, fontSize, chunk.toString() + ch) > maxWidth) {
                                out.add(chunk.toString());
                                chunk.setLength(0);
                            }
                            chunk.append(ch);
                        }
                        if (chunk.length() > 0) {
                            line.append(chunk);
                        }
                    } else {
                        line.append(w);
                    }
                }
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
        }
        if (out.isEmpty()) {
            out.add("-"); //$NON-NLS-1$
        }
        return out;
    }

    private static float stringWidthSafe(PDFont font, float fontSize, String text) {
        try {
            return (font.getStringWidth(text) / 1000f) * fontSize;
        } catch (Exception e) {
            return text.length() * (fontSize * 0.55f);
        }
    }

    static String safeText(String raw, PDFont font, boolean unicodeFont) {
        if (raw == null || raw.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\t', ' ')
                .replace('\u00A0', ' ')
                .replace('\u2014', '-')
                .replace('\u2013', '-')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"');
        StringBuilder sb = new StringBuilder(nfc.length());
        for (int i = 0; i < nfc.length(); i++) {
            char ch = nfc.charAt(i);
            if (ch == '\r' || ch == '\n') {
                sb.append(ch);
                continue;
            }
            if (ch < 0x20 || ch == 0x7f || Character.isSurrogate(ch)) {
                continue;
            }
            String s = String.valueOf(ch);
            if (canEncode(font, s, unicodeFont)) {
                sb.append(ch);
            } else {
                String stripped = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", ""); //$NON-NLS-1$ //$NON-NLS-2$
                if (!stripped.isEmpty() && canEncode(font, stripped, unicodeFont)) {
                    sb.append(stripped);
                } else {
                    sb.append('?');
                }
            }
        }
        return sb.toString();
    }

    private static boolean canEncode(PDFont font, String s, boolean unicodeFont) {
        if (!unicodeFont && s.length() == 1 && s.charAt(0) > 255) {
            return false;
        }
        try {
            font.encode(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String nonBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }
}
