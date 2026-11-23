/*
 * Chat Captura IPED - Gerador de Laudo Tecnico (HTML + PDF + Copia Verificada de report.pdf)
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates generation of {@code <outputRoot>/laudo/}:
 * <ul>
 *   <li>{@code report-origem.pdf}: verified bit-for-bit copy of origin {@code report.pdf} (when present)</li>
 *   <li>{@code report-plugin.pdf}: native PDFBox technical report merging origin data + plugin additions</li>
 *   <li>{@code report-completo-unificado.pdf}: optional merged PDF (plugin report + origin report.pdf)</li>
 *   <li>{@code laudo-tecnico.html}: offline CSP-hardened HTML report ready for printing</li>
 *   <li>{@code custodia-caso.json}: structured custody &amp; capture metadata</li>
 *   <li>{@code laudo-hashes.txt}: SHA-256 manifest of all files inside {@code laudo/}</li>
 * </ul>
 */
final class TechnicalReportGenerator {

    private static final long MAX_ORIGIN_PDF_BYTES = 64L * 1024L * 1024L;

    private TechnicalReportGenerator() {
    }

    static final class GenerationResult {
        Path laudoDir;
        Path htmlReport;
        Path pluginPdf;
        Path originPdfCopy;
        Path unifiedPdf;
        Path custodyJson;
        Path hashesTxt;
        boolean originPdfCopied;
        boolean pdfGenerated;
        String pdfError = ""; //$NON-NLS-1$
    }

    static GenerationResult generateAll(
            Path outputRoot,
            CustodyCaseRecord record,
            Path originFolder,
            String rootHash) throws IOException {
        if (outputRoot == null || record == null) {
            throw new IOException("Parametros invalidos para gerar laudo tecnico."); //$NON-NLS-1$
        }
        Path normRoot = OriginCustodyBinder.validateSafeDirectory(outputRoot, "pasta destino do laudo"); //$NON-NLS-1$
        Path laudoDir = CaptureIntegrity.resolveContained(normRoot, CustodyCaseRecord.LAUDO_DIR_NAME);
        CaptureIntegrity.createDirectoriesSafe(normRoot, laudoDir);

        GenerationResult res = new GenerationResult();
        res.laudoDir = laudoDir;

        // 1. Copy original report.pdf intact (bit-for-bit, unmodified) to laudo/report.pdf with SHA-256 verification
        Path copiedOriginPdf = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE);
        Path legacyCopiedOriginPdf = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.LEGACY_REPORT_ORIGIN_PDF_FILE);
        if (originFolder != null && record.copyOriginReportPdf) {
            OriginCustodyBinder.OriginReportPreview preview = OriginCustodyBinder.readDeviceAndCustodyPreview(originFolder);
            record.mergeFromOriginPreview(preview, false);
            if (preview.reportPdfPresent && preview.reportPdfPath != null) {
                copyOriginReportPdfVerified(normRoot, preview.reportPdfPath, copiedOriginPdf, record);
                res.originPdfCopy = copiedOriginPdf;
                res.originPdfCopied = true;
            }
        } else if (CaptureIntegrity.isSafeRegularFile(copiedOriginPdf)) {
            record.originReportPdfCopied = true;
            record.originReportPdfCopiedSha256 = CaptureIntegrity.sha256(copiedOriginPdf);
            res.originPdfCopy = copiedOriginPdf;
            res.originPdfCopied = true;
        } else if (CaptureIntegrity.isSafeRegularFile(legacyCopiedOriginPdf)) {
            record.originReportPdfCopied = true;
            record.originReportPdfCopiedSha256 = CaptureIntegrity.sha256(legacyCopiedOriginPdf);
            res.originPdfCopy = legacyCopiedOriginPdf;
            res.originPdfCopied = true;
        }

        // 2. Ensure laudo/Log.txt is initialized/verified and read auxiliary audit log
        CaptureAuditLogManager.ChainedLogSnapshot chainedLogSnap = CaptureAuditLogManager.ensureChainedLogInitialized(
                normRoot, record, CaptureAuditLogManager.defaultRecoveryPrompt);
        List<CaptureAuditLogManager.AuditLogEntry> auditLog = CaptureAuditLogManager.readLogEntries(normRoot);
        boolean logChainValid = CaptureAuditLogManager.verifyAuditLogChain(auditLog);

        // 3. Persist custodia-caso.json
        record.save(normRoot);
        res.custodyJson = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.CUSTODY_JSON_FILE);

        // 4. Write laudo-tecnico.html
        res.htmlReport = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.REPORT_HTML_FILE);
        String htmlContent = buildHtmlReport(normRoot, record, auditLog, logChainValid, chainedLogSnap, rootHash);
        CaptureIntegrity.writeAtomically(normRoot, res.htmlReport, htmlContent, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES);

        // 5. Generate report-plugin.pdf directly via PDFBox (embedding /Info anchor for Log.txt)
        res.pluginPdf = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE);
        try {
            PdfReportWriter.generatePluginReportPdf(normRoot, res.pluginPdf, record, auditLog, logChainValid, chainedLogSnap, rootHash);
            res.pdfGenerated = true;
            if (record.generateUnifiedPdf && res.originPdfCopied && res.originPdfCopy != null) {
                res.unifiedPdf = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE);
                PdfReportWriter.mergeUnifiedPdf(normRoot, res.pluginPdf, res.originPdfCopy, res.unifiedPdf);
            }
        } catch (Throwable pdfErr) {
            res.pdfGenerated = false;
            res.pdfError = pdfErr.getMessage() == null ? pdfErr.getClass().getSimpleName() : pdfErr.getMessage();
        }

        // 6. Write laudo-hashes.txt with SHA-256 of every generated artifact in laudo/
        res.hashesTxt = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.LAUDO_HASHES_TXT_FILE);
        writeLaudoHashesFile(normRoot, laudoDir, res.hashesTxt, rootHash);
        return res;
    }

    static void copyOriginReportPdfVerified(Path outputRoot, Path sourcePdf, Path destPdf, CustodyCaseRecord record)
            throws IOException {
        if (sourcePdf == null || !Files.exists(sourcePdf, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (CaptureIntegrity.isLinkOrReparse(sourcePdf) || !Files.isRegularFile(sourcePdf, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("report.pdf da origem invalido (link simbolico ou juncao): " + sourcePdf); //$NON-NLS-1$
        }
        long size = CaptureIntegrity.safeRegularFileSize(sourcePdf);
        if (size <= 0 || size > MAX_ORIGIN_PDF_BYTES) {
            throw new IOException("report.pdf da origem vazio ou excede limite de 64 MiB: " + size); //$NON-NLS-1$
        }
        String srcSha = CaptureIntegrity.sha256(sourcePdf);
        byte[] bytes = Files.readAllBytes(sourcePdf);
        CaptureIntegrity.writeBytesAtomically(outputRoot, destPdf, bytes, MAX_ORIGIN_PDF_BYTES);
        String dstSha = CaptureIntegrity.sha256(destPdf);
        if (!srcSha.equalsIgnoreCase(dstSha)) {
            Files.deleteIfExists(destPdf);
            throw new IOException("Falha na verificacao SHA-256 apos copia de report.pdf!"); //$NON-NLS-1$
        }
        record.originReportPdfPresent = true;
        record.originReportPdfCopied = true;
        record.originReportPdfSha256 = srcSha;
        record.originReportPdfCopiedSha256 = dstSha;
        record.addStep("LAUDO", "", "Copia verificada de report.pdf", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Copiado report.pdf para laudo/" + CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE + " | SHA-256=" + dstSha); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void writeLaudoHashesFile(Path outputRoot, Path laudoDir, Path hashesPath, String rootHash)
            throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        files.put(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE, laudoDir.resolve(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE));
        files.put(CustodyCaseRecord.LEGACY_REPORT_ORIGIN_PDF_FILE, laudoDir.resolve(CustodyCaseRecord.LEGACY_REPORT_ORIGIN_PDF_FILE));
        files.put(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE, laudoDir.resolve(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE));
        files.put(CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE, laudoDir.resolve(CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE));
        files.put(CustodyCaseRecord.REPORT_HTML_FILE, laudoDir.resolve(CustodyCaseRecord.REPORT_HTML_FILE));
        files.put(CustodyCaseRecord.CUSTODY_JSON_FILE, laudoDir.resolve(CustodyCaseRecord.CUSTODY_JSON_FILE));
        files.put(CustodyCaseRecord.LOG_TXT_FILE, laudoDir.resolve(CustodyCaseRecord.LOG_TXT_FILE));
        files.put(CustodyCaseRecord.AUDIT_LOG_JSONL_FILE, laudoDir.resolve(CustodyCaseRecord.AUDIT_LOG_JSONL_FILE));
        files.put(CustodyCaseRecord.AUDIT_LOG_TXT_FILE, laudoDir.resolve(CustodyCaseRecord.AUDIT_LOG_TXT_FILE));

        StringBuilder sb = new StringBuilder();
        sb.append("LAUDO_HASHES_SHA256_V1\n"); //$NON-NLS-1$
        sb.append("GERADO_EM|").append(CustodyCaseRecord.nowLocalTimestamp()).append('\n'); //$NON-NLS-1$
        if (rootHash != null && !rootHash.isBlank()) {
            sb.append("ROOT_HASH_CAPTURAS|").append(rootHash.trim()).append('\n'); //$NON-NLS-1$
        }
        sb.append('\n');
        for (Map.Entry<String, Path> e : files.entrySet()) {
            Path p = e.getValue();
            if (CaptureIntegrity.isSafeRegularFile(p)) {
                long sz = CaptureIntegrity.safeRegularFileSize(p);
                String sha = CaptureIntegrity.sha256(p);
                sb.append("ARQUIVO|laudo/").append(e.getKey()).append('|').append(sz).append('|').append(sha).append('\n'); //$NON-NLS-1$
            }
        }
        CaptureIntegrity.writeAtomically(outputRoot, hashesPath, sb.toString(), CaptureIntegrity.MAX_JSON_BYTES);
    }

    static String buildHtmlReport(
            Path outputRoot,
            CustodyCaseRecord record,
            List<CaptureAuditLogManager.AuditLogEntry> auditLog,
            boolean logChainValid,
            CaptureAuditLogManager.ChainedLogSnapshot chainedLogSnap,
            String rootHash) {
        StringBuilder h = new StringBuilder(16384);
        h.append("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">"); //$NON-NLS-1$
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"); //$NON-NLS-1$
        h.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'\">"); //$NON-NLS-1$
        h.append("<title>Laudo / Relat\u00f3rio T\u00e9cnico - Chat Captura IPED</title>"); //$NON-NLS-1$
        h.append("<style>"); //$NON-NLS-1$
        h.append("body{margin:0;font-family:'Segoe UI',Arial,sans-serif;background:#f3f5f7;color:#1d272c;line-height:1.45}"); //$NON-NLS-1$
        h.append(".hero{background:#102b35;color:#fff;padding:24px 32px}.hero h1{margin:0 0 6px;font-size:22px}.hero p{margin:3px 0;color:#d6e7ec;font-size:13px}"); //$NON-NLS-1$
        h.append(".actions{margin-top:12px}.btn{display:inline-block;margin-right:8px;margin-top:6px;padding:7px 12px;border-radius:5px;background:#0b6b5d;color:#fff;text-decoration:none;font-size:13px;font-weight:600}.btn.alt{background:#3c5058}"); //$NON-NLS-1$
        h.append(".wrap{max-width:1120px;margin:20px auto;padding:0 18px 40px}"); //$NON-NLS-1$
        h.append(".card{background:#fff;border:1px solid #d5dfe4;border-radius:8px;padding:16px 20px;margin-bottom:16px;box-shadow:0 1px 4px rgba(0,0,0,.04)}"); //$NON-NLS-1$
        h.append("h2{margin:0 0 12px;font-size:16px;color:#102b35;border-left:4px solid #0b6b5d;padding-left:10px}"); //$NON-NLS-1$
        h.append("h3{margin:14px 0 8px;font-size:14px;color:#0b6b5d}"); //$NON-NLS-1$
        h.append("table{width:100%;border-collapse:collapse;font-size:13px;margin-top:6px}th,td{border:1px solid #d5dfe4;padding:6px 8px;text-align:left;vertical-align:top}th{background:#edf2f5;color:#102b35}"); //$NON-NLS-1$
        h.append(".kv th{width:240px;font-weight:600}.mono{font-family:Consolas,'Courier New',monospace;font-size:11.5px;word-break:break-all}"); //$NON-NLS-1$
        h.append(".badge{display:inline-block;padding:2px 7px;border-radius:10px;font-size:11px;font-weight:600}"); //$NON-NLS-1$
        h.append(".b-ok{background:#dff4ea;color:#0a5d3b}.b-warn{background:#fff3cd;color:#7a5600}.b-bad{background:#fde2e2;color:#8b0000}.b-src{background:#e7f0f5;color:#174759}"); //$NON-NLS-1$
        h.append("@media print{body{background:#fff}.actions{display:none}.card{box-shadow:none;break-inside:avoid}}"); //$NON-NLS-1$
        h.append("</style></head><body>"); //$NON-NLS-1$

        h.append("<header class=\"hero\"><h1>Laudo / Relat\u00f3rio T\u00e9cnico de Extra\u00e7\u00e3o e Cust\u00f3dia</h1>"); //$NON-NLS-1$
        h.append("<p><b>Ferramenta:</b> IPED ").append(esc(nonBlank(record.ipedVersion, "4.3.1"))) //$NON-NLS-1$ //$NON-NLS-2$
                .append(" &middot; <b>Plugin:</b> Chat Captura IPED ").append(esc(nonBlank(record.pluginVersion, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION))) //$NON-NLS-1$
                .append(" &middot; <b>Atualizado em:</b> ").append(esc(nonBlank(record.updatedAtLocal, CustodyCaseRecord.nowLocalTimestamp()))).append("</p>"); //$NON-NLS-1$ //$NON-NLS-2$
        h.append("<p><b>Pasta raiz:</b> <span class=\"mono\">").append(esc(outputRoot.toAbsolutePath().normalize().toString())).append("</span></p>"); //$NON-NLS-1$ //$NON-NLS-2$
        if (rootHash != null && !rootHash.isBlank()) {
            h.append("<p><b>Hash geral do conjunto (rootHash):</b> <span class=\"mono\">").append(esc(rootHash)).append("</span></p>"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        h.append("<div class=\"actions\">"); //$NON-NLS-1$
        h.append("<a class=\"btn\" href=\"").append(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE).append("\">Abrir Report do Plugin (PDF)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
        if (record.originReportPdfCopied) {
            h.append("<a class=\"btn alt\" href=\"").append(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE).append("\">Abrir report.pdf Original</a>"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (record.generateUnifiedPdf && record.originReportPdfCopied) {
            h.append("<a class=\"btn alt\" href=\"").append(CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE).append("\">Abrir PDF Unificado</a>"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        h.append("<a class=\"btn alt\" href=\"").append(CustodyCaseRecord.LOG_TXT_FILE).append("\">Log.txt Cronol\u00f3gico</a>"); //$NON-NLS-1$ //$NON-NLS-2$
        h.append("<a class=\"btn alt\" href=\"").append(CustodyCaseRecord.LAUDO_HASHES_TXT_FILE).append("\">Hashes do Laudo (.txt)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
        h.append("<a class=\"btn alt\" href=\"../index.html\">Voltar ao \u00cdndice</a>"); //$NON-NLS-1$
        h.append("</div></header><main class=\"wrap\">"); //$NON-NLS-1$

        // Secao 1: report.pdf original
        h.append("<section class=\"card\"><h2>1. V\u00ednculo com o Relat\u00f3rio Original (report.pdf)</h2><table class=\"kv\">"); //$NON-NLS-1$
        if (record.originReportPdfPresent) {
            row(h, "Relat\u00f3rio original", "<span class=\"badge b-ok\">Lido e integrado</span> &nbsp; Dados do report.pdf absorvidos + itens faltantes complementados pelo plugin."); //$NON-NLS-1$ //$NON-NLS-2$
            rowMono(h, "SHA-256 report.pdf original", nonBlank(record.originReportPdfSha256, "-")); //$NON-NLS-1$ //$NON-NLS-2$
            if (record.originReportPdfCopied) {
                row(h, "C\u00f3pia intacta no caso", "<a href=\"" + CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE + "\">laudo/" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE + "</a> &nbsp; <span class=\"badge b-ok\">SHA-256 id\u00eantico ao original</span>"); //$NON-NLS-1$
            }
        } else {
            row(h, "Relat\u00f3rio original", "<span class=\"badge b-warn\">N\u00e3o fornecido / n\u00e3o localizado</span> &nbsp; Gerado exclusivamente o relat\u00f3rio do plugin."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        h.append("</table></section>"); //$NON-NLS-1$

        // Secao 2: Dispositivo
        h.append("<section class=\"card\"><h2>2. Identifica\u00e7\u00e3o do Dispositivo</h2><table><thead><tr><th>Campo</th><th>Valor</th><th>Proced\u00eancia</th></tr></thead><tbody>"); //$NON-NLS-1$
        deviceRow(h, "Fabricante", record.deviceManufacturer, record.provenanceTag("deviceManufacturer", record.deviceManufacturer)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "Modelo", record.deviceModel, record.provenanceTag("deviceModel", record.deviceModel)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "IMEI", record.deviceImei, record.provenanceTag("deviceImei", record.deviceImei)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "N\u00famero de s\u00e9rie", record.deviceSerialNumber, record.provenanceTag("deviceSerialNumber", record.deviceSerialNumber)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "Vers\u00e3o do SO", record.deviceOsVersion, record.provenanceTag("deviceOsVersion", record.deviceOsVersion)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "Vers\u00e3o do WhatsApp", record.whatsappVersion, record.provenanceTag("whatsappVersion", record.whatsappVersion)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "C\u00f3digo da coleta", record.collectionCode, record.provenanceTag("collectionCode", record.collectionCode)); //$NON-NLS-1$ //$NON-NLS-2$
        deviceRow(h, "Ferramenta da extra\u00e7\u00e3o original", record.originExtractionTool, record.provenanceTag("originExtractionTool", record.originExtractionTool)); //$NON-NLS-1$ //$NON-NLS-2$
        if (!record.originStartExtraction.isEmpty() || !record.originEndExtraction.isEmpty()) {
            deviceRow(h, "Per\u00edodo da extra\u00e7\u00e3o original", //$NON-NLS-1$
                    nonBlank(record.originStartExtraction, "-") + " at\u00e9 " + nonBlank(record.originEndExtraction, "-"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "Lido do report.pdf original"); //$NON-NLS-1$
        }
        h.append("</tbody></table></section>"); //$NON-NLS-1$

        // Secao 3: Cadeia de Custodia
        h.append("<section class=\"card\"><h2>3. Cadeia de Cust\u00f3dia (Chain Of Custody)</h2><table class=\"kv\">"); //$NON-NLS-1$
        if (!record.caseNumber.isBlank()) row(h, "N\u00ba do caso (Case number)", esc(record.caseNumber)); //$NON-NLS-1$
        if (!record.legalAuthorization.isBlank()) row(h, "Autoriza\u00e7\u00e3o Legal", esc(record.legalAuthorization)); //$NON-NLS-1$
        if (!record.victimsFullName.isBlank()) row(h, "Nome completo das v\u00edtimas", esc(record.victimsFullName)); //$NON-NLS-1$
        if (!record.suspectsFullName.isBlank()) row(h, "Nome completo dos suspeitos", esc(record.suspectsFullName)); //$NON-NLS-1$
        if (!record.evidenceDestination.isBlank()) row(h, "Destino da evid\u00eancia", esc(record.evidenceDestination)); //$NON-NLS-1$
        if (!record.storageSealReference.isBlank()) row(h, "N\u00ba do Lacre / Refer\u00eancia", esc(record.storageSealReference)); //$NON-NLS-1$
        if (!record.storageResponsible.isBlank()) row(h, "Respons\u00e1vel pelo armazenamento", esc(record.storageResponsible)); //$NON-NLS-1$
        row(h, "Quem coletou", esc(nonBlank(record.collectedBy, "N\u00e3o informado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Quem analisou", esc(nonBlank(record.analyzedBy, "N\u00e3o informado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Quando (coleta)", esc(nonBlank(record.collectedAt, "N\u00e3o informado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Quando (an\u00e1lise)", esc(nonBlank(record.analyzedAt, "N\u00e3o informado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Onde (local)", esc(nonBlank(record.custodyLocation, "N\u00e3o informado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Como foi armazenado", esc(record.formattedStorageDescription())); //$NON-NLS-1$
        h.append("</table></section>"); //$NON-NLS-1$

        // Secao 4: Ferramenta e Parametros
        h.append("<section class=\"card\"><h2>4. Ferramenta e Par\u00e2metros</h2><table class=\"kv\">"); //$NON-NLS-1$
        row(h, "Ferramenta", "IPED " + esc(nonBlank(record.ipedVersion, "4.3.1")) + " + Plugin Chat Captura IPED " + esc(nonBlank(record.pluginVersion, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        row(h, "Configura\u00e7\u00e3o de extra\u00e7\u00e3o", esc(nonBlank(record.extractionConfig, "-"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Filtros aplicados", esc(nonBlank(record.appliedFilters, "Nenhum filtro adicional declarado"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Origem Avilla vinculada", esc(nonBlank(record.originFolderName, "N\u00e3o vinculada"))); //$NON-NLS-1$ //$NON-NLS-2$
        row(h, "Caso IPED pai", esc(nonBlank(record.parentIpedFolderName, "N\u00e3o vinculado"))); //$NON-NLS-1$ //$NON-NLS-2$
        h.append("</table></section>"); //$NON-NLS-1$

        // Secao 5: Hashes dos Arquivos Originais
        h.append("<section class=\"card\"><h2>5. Hashes dos Arquivos Originais</h2><table class=\"kv\">"); //$NON-NLS-1$
        if (!record.originReportPdfSha256.isEmpty()) rowMono(h, "report.pdf (SHA-256)", record.originReportPdfSha256); //$NON-NLS-1$
        if (!record.originHashsTxtSha256.isEmpty()) rowMono(h, "Hashs.txt (SHA-256)", record.originHashsTxtSha256); //$NON-NLS-1$
        if (!record.originHashsAvillaSha256.isEmpty()) rowMono(h, "Hashs.avilla (SHA-256)", record.originHashsAvillaSha256); //$NON-NLS-1$
        if (!record.originMsgstoreSha256.isEmpty()) rowMono(h, "msgstore.db (" + nonBlank(record.originMsgstorePath, "origem") + ")", record.originMsgstoreSha256); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (!record.originWaDbSha256.isEmpty()) rowMono(h, "wa.db (SHA-256)", record.originWaDbSha256); //$NON-NLS-1$
        if (!record.originKeySha256.isEmpty()) rowMono(h, "key (SHA-256)", record.originKeySha256); //$NON-NLS-1$
        if (record.originReportPdfSha256.isEmpty() && record.originHashsTxtSha256.isEmpty() && record.originMsgstoreSha256.isEmpty()) {
            row(h, "Origem custodiada", "Nenhum artefato de origem Avilla vinculado."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        h.append("</table></section>"); //$NON-NLS-1$

        // Secao 6: Capturas e Hashes Exportados
        h.append("<section class=\"card\"><h2>6. Capturas e Hashes SHA-256 dos Arquivos Exportados</h2>"); //$NON-NLS-1$
        if (record.knownCaptures.isEmpty()) {
            h.append("<p>Nenhuma captura presente na pasta destino.</p>"); //$NON-NLS-1$
        } else {
            for (CustodyCaseRecord.CaptureSnapshot snap : record.knownCaptures.values()) {
                h.append("<h3>").append(esc(snap.folder)).append(" &nbsp; <a href=\"../") //$NON-NLS-1$ //$NON-NLS-2$
                        .append(esc(snap.folder)).append("/whatsapp.html\">Abrir chat</a></h3><table class=\"kv\">"); //$NON-NLS-1$
                row(h, "Conversa / Item fonte", esc(nonBlank(snap.sourceItemName, snap.title))); //$NON-NLS-1$
                if (!snap.sourceItemPath.isEmpty()) rowMono(h, "Caminho no IPED", snap.sourceItemPath); //$NON-NLS-1$
                if (!snap.sourceItemSha256.isEmpty()) rowMono(h, "Hash do item original (SHA-256)", snap.sourceItemSha256); //$NON-NLS-1$
                else if (!snap.sourceItemHash.isEmpty()) rowMono(h, "Hash do item original (IPED)", snap.sourceItemHash); //$NON-NLS-1$
                if (!snap.sourceDbSha256.isEmpty()) rowMono(h, "msgstore.db vinculado (SHA-256)", snap.sourceDbSha256); //$NON-NLS-1$
                row(h, "\u00c2ncoras / Intervalo", esc(nonBlank(snap.startAnchor, "-") + " -> " + nonBlank(snap.endAnchor, "-"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                row(h, "Arquivos por categoria", esc(snap.counts.toSummaryLine("OK") + " | total=" + snap.fileCount + " arquivos (" + snap.totalBytes + " bytes)")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                rowMono(h, "SHA-256 whatsapp.html", nonBlank(snap.htmlSha256, "-")); //$NON-NLS-1$ //$NON-NLS-2$
                if (!snap.messagesSha256.isEmpty()) rowMono(h, "SHA-256 messages.json", snap.messagesSha256); //$NON-NLS-1$
                if (!snap.manifestSha256.isEmpty()) rowMono(h, "SHA-256 manifest.json", snap.manifestSha256); //$NON-NLS-1$
                if (!snap.integritySha256.isEmpty()) rowMono(h, "SHA-256 integridade.json", snap.integritySha256); //$NON-NLS-1$
                if (!snap.originBindingSha256.isEmpty()) rowMono(h, "SHA-256 origin-binding.json", snap.originBindingSha256); //$NON-NLS-1$
                if (!snap.captureRootHash.isEmpty()) rowMono(h, "captureRootHash", snap.captureRootHash); //$NON-NLS-1$
                if (!snap.contentRootHash.isEmpty()) rowMono(h, "contentRootHash", snap.contentRootHash); //$NON-NLS-1$
                rowMono(h, "folderHash (pasta completa)", nonBlank(snap.folderHash, "-")); //$NON-NLS-1$ //$NON-NLS-2$
                h.append("</table>"); //$NON-NLS-1$
            }
        }
        h.append("</section>"); //$NON-NLS-1$

        // Secao 7: Passo a Passo Cronologico
        h.append("<section class=\"card\"><h2>7. Passo a Passo Cronol\u00f3gico da Extra\u00e7\u00e3o</h2>"); //$NON-NLS-1$
        if (record.extractionSteps.isEmpty()) {
            h.append("<p>Nenhuma etapa registrada.</p>"); //$NON-NLS-1$
        } else {
            h.append("<table><thead><tr><th>Data / Hor\u00e1rio</th><th>Opera\u00e7\u00e3o</th><th>Captura</th><th>Etapa</th><th>Detalhe</th></tr></thead><tbody>"); //$NON-NLS-1$
            for (CustodyCaseRecord.ExtractionStep step : record.extractionSteps) {
                h.append("<tr><td class=\"mono\">").append(esc(step.timestampLocal)) //$NON-NLS-1$
                        .append("</td><td>").append(esc(step.operation)) //$NON-NLS-1$
                        .append("</td><td class=\"mono\">").append(esc(nonBlank(step.captureFolder, "-"))) //$NON-NLS-1$ //$NON-NLS-2$
                        .append("</td><td><b>").append(esc(step.stage)) //$NON-NLS-1$
                        .append("</b></td><td>").append(esc(step.detail)).append("</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            h.append("</tbody></table>"); //$NON-NLS-1$
        }
        h.append("</section>"); //$NON-NLS-1$

        // Secao 8: Log Cronologico Unificado (laudo/Log.txt)
        h.append("<section class=\"card\"><h2>8. Log Cronol\u00f3gico (laudo/Log.txt)</h2>"); //$NON-NLS-1$
        boolean logOk = chainedLogSnap != null && chainedLogSnap.chainValid;
        String statusBadge = logOk ? "b-ok" : "b-bad"; //$NON-NLS-1$ //$NON-NLS-2$
        h.append("<table class=\"kv\">"); //$NON-NLS-1$
        row(h, "Status do Log.txt", "<span class=\"badge " + statusBadge + "\">" + esc(nonBlank(record.logIntegrityStatus, "NAO_INICIADO")) + "</span>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        if (!record.logCreatedAtLocal.isBlank()) {
            row(h, "Criado em", esc(record.logCreatedAtLocal + (record.logCreatedAtUtc.isBlank() ? "" : " (UTC: " + record.logCreatedAtUtc + ")"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
        row(h, "Sess\u00f5es registradas", esc("Total: " + record.logSessionCount //$NON-NLS-1$ //$NON-NLS-2$
                + " (Adicionadas: " + record.logAddedCount //$NON-NLS-1$
                + " | Re-Extra\u00eddas: " + record.logReextractedCount //$NON-NLS-1$
                + " | Removidas: " + record.logRemovedCount + ") | " + record.logFileBytes + " bytes")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (!record.logFileSha256.isBlank()) {
            rowMono(h, "SHA-256 do arquivo laudo/Log.txt", record.logFileSha256); //$NON-NLS-1$
        }
        h.append("</table>"); //$NON-NLS-1$
        if (!record.logIncidents.isEmpty()) {
            h.append("<h3>Ocorr\u00eancias registradas em laudo/Log.txt</h3><ul>"); //$NON-NLS-1$
            for (String inc : record.logIncidents) {
                h.append("<li class=\"mono\">").append(esc(inc)).append("</li>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            h.append("</ul>"); //$NON-NLS-1$
        }
        if (chainedLogSnap != null && !chainedLogSnap.sessions.isEmpty()) {
            h.append("<table><thead><tr><th>Sess\u00e3o</th><th>Data / Hor\u00e1rio</th><th>Evento</th><th>Pasta</th><th>Status</th><th>Detalhes</th><th>SHA-256 Pasta</th></tr></thead><tbody>"); //$NON-NLS-1$
            for (CaptureAuditLogManager.SessionRecord s : chainedLogSnap.sessions) {
                String badgeCls = s.eventType.contains("REMOVID") || s.status.contains("ERRO") //$NON-NLS-1$ //$NON-NLS-2$
                        ? "b-bad" : (s.status.contains("ATUALIZ") || s.status.contains("REPAR") ? "b-warn" : "b-ok"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                h.append("<tr><td>#").append(s.sessionNumber) //$NON-NLS-1$
                        .append("</td><td class=\"mono\">").append(esc(s.timestampLocal)) //$NON-NLS-1$
                        .append("</td><td><b>").append(esc(s.eventType)).append("</b>") //$NON-NLS-1$ //$NON-NLS-2$
                        .append("</td><td class=\"mono\">").append(esc(s.captureFolder)) //$NON-NLS-1$
                        .append("</td><td><span class=\"badge ").append(badgeCls).append("\">").append(esc(s.status)).append("</span>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .append("</td><td>").append(esc(s.detail)) //$NON-NLS-1$
                        .append("</td><td class=\"mono\">").append(esc(s.folderHash)) //$NON-NLS-1$
                        .append("</td></tr>"); //$NON-NLS-1$
            }
            h.append("</tbody></table>"); //$NON-NLS-1$
        }
        h.append("</section></main></body></html>"); //$NON-NLS-1$
        return h.toString();
    }

    private static void deviceRow(StringBuilder h, String label, String value, String origin) {
        h.append("<tr><th>").append(esc(label)).append("</th><td>") //$NON-NLS-1$ //$NON-NLS-2$
                .append(esc(nonBlank(value, "N\u00e3o informado"))).append("</td><td><span class=\"badge b-src\">") //$NON-NLS-1$ //$NON-NLS-2$
                .append(esc(origin)).append("</span></td></tr>"); //$NON-NLS-1$
    }

    private static void row(StringBuilder h, String label, String valueHtml) {
        h.append("<tr><th>").append(esc(label)).append("</th><td>").append(valueHtml).append("</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static void rowMono(StringBuilder h, String label, String value) {
        h.append("<tr><th>").append(esc(label)).append("</th><td class=\"mono\">").append(esc(value)).append("</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String esc(String s) {
        if (s == null) return ""; //$NON-NLS-1$
        return s.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String nonBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }
}
