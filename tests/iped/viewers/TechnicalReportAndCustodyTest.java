package iped.viewers;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * End-to-end tests for beta technical report (HTML + native PDFBox), origin report.pdf
 * ingestion/copy, CustodyReportDialog [Pasta, Midia lacrada, Custom], and chained audit log
 * (START / RE-EXTRAIR / ADICIONADO / DELETADO).
 */
public final class TechnicalReportAndCustodyTest {

    private TechnicalReportAndCustodyTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("chat-captura-laudo-beta-");
        try {
            testWithoutOriginReportPdfGeneratesPluginOnly(tempRoot);
            testWithOriginReportPdfCopiesMergesAndValidatesPdfBoxOutput(tempRoot);
            testRealAvillaReportPdfFormatAndTrilingualPluginPdf(tempRoot);
            testChainedAuditLogStartReextractAddedDeletedAndTamper(tempRoot);
            testChainedLogTxtPdfAnchorAndVerifierWithoutPdfBox(tempRoot);
            testPhase3CaptureReportPackageGenerationAndSecurity(tempRoot);
            testCustodyDialogSelectorAndSwingHtmlDisable();
            testLaudoDirectoryIgnoredByVerifierDiscover(tempRoot);
            System.out.println("TechnicalReportAndCustodyTest: PASS");
        } finally {
            deleteTree(tempRoot);
            try {
                javafx.application.Platform.exit();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void testWithoutOriginReportPdfGeneratesPluginOnly(Path root) throws Exception {
        Path outDir = Files.createDirectories(root.resolve("case-no-origin"));
        createSampleHtmlCapture(outDir.resolve("Chat_SemOrigem-20261123-090000"), "Conversa Sem Origem");

        CustodyCaseRecord rec = new CustodyCaseRecord();
        rec.deviceModel = "Pixel 9 Pro";
        rec.deviceOsVersion = "Android 15";
        rec.collectedBy = "Perito <script>alert(1)</script> Silva";
        rec.analyzedBy = "Analista Souza";
        rec.collectedAt = "2026-11-23 08:00:00 -03:00";
        rec.custodyLocation = "Laboratorio Central";
        rec.storageType = CustodyCaseRecord.StorageType.CUSTOM;
        rec.storageCustomText = "HD externo criptografado VeraCrypt";
        rec.appliedFilters = "Filtro: categoria WhatsApp + conversa especifica";
        rec.addStep("START", "Chat_SemOrigem-20261123-090000", "Selecao de intervalo", "Mensagens 101 a 101 selecionadas no IPED");

        CaptureAuditLogManager.recordStartCapture(outDir, rec, outDir.resolve("Chat_SemOrigem-20261123-090000"));
        TechnicalReportGenerator.GenerationResult res = TechnicalReportGenerator.generateAll(outDir, rec, null, "a".repeat(64));

        check(res.pdfGenerated && Files.isRegularFile(res.pluginPdf), "report-plugin.pdf deve ser gerado mesmo sem report.pdf de origem");
        check(!res.originPdfCopied && !Files.exists(outDir.resolve("laudo/report.pdf")) && !Files.exists(outDir.resolve("laudo/report-origem.pdf")),
                "nao deve criar laudo/report.pdf sem pasta de origem");
        check(Files.isRegularFile(res.htmlReport), "laudo-tecnico.html deve ser gerado");
        check(Files.isRegularFile(res.hashesTxt), "laudo-hashes.txt deve ser gerado");

        String html = Files.readString(res.htmlReport, StandardCharsets.UTF_8);
        check(!html.contains("<script>alert(1)</script>"), "XSS deve ser escapado em laudo-tecnico.html");
        check(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "conteudo escapado deve aparecer no HTML");
        check(html.contains("Custom: HD externo criptografado VeraCrypt"), "armazenamento custom deve constar no HTML");
        check(html.contains("default-src 'none'"), "CSP restrita deve estar presente no HTML");

        String pdfText = readPdfText(res.pluginPdf);
        check(pdfText.contains("Pixel 9 Pro"), "modelo deve constar no PDF gerado via PDFBox");
        check(pdfText.contains("Custom: HD externo criptografado VeraCrypt"), "armazenamento custom deve constar no PDF");
        check(pdfText.contains("START"), "evento START deve constar no PDF");
        check(pdfText.contains("ADICIONADO captura"), "evento ADICIONADO captura deve constar no PDF");
    }

    private static void testWithOriginReportPdfCopiesMergesAndValidatesPdfBoxOutput(Path root) throws Exception {
        Path originDir = Files.createDirectories(root.resolve("origin-avilla"));
        Path originPdf = originDir.resolve("report.pdf");
        createSyntheticOriginReportPdf(originPdf);
        String expectedPdfSha = CaptureIntegrity.sha256(originPdf);

        Path outDir = Files.createDirectories(root.resolve("case-with-origin"));
        createSampleHtmlCapture(outDir.resolve("Chat_ComOrigem-20261123-091000"), "Conversa Com Origem");

        OriginCustodyBinder.OriginReportPreview preview = OriginCustodyBinder.readDeviceAndCustodyPreview(originDir);
        check(preview.reportPdfPresent, "preview deve detectar report.pdf");
        check("Samsung".equals(preview.manufacturer), "fabricante extraido do report.pdf: " + preview.manufacturer);
        check("SM-S928B".equals(preview.model), "modelo extraido do report.pdf: " + preview.model);
        check(preview.imei.contains("351234567890123"), "IMEI extraido do report.pdf: " + preview.imei);
        check("R5CW999XYZ".equals(preview.serialNumber), "serial extraido do report.pdf: " + preview.serialNumber);
        check("Android 14".equals(preview.osVersion), "SO extraido do report.pdf: " + preview.osVersion);
        check("AV-2026-0099".equals(preview.collectionCode), "Collection Code extraido do report.pdf: " + preview.collectionCode);
        check(preview.whatsappVersion.isEmpty(), "WhatsApp version ausente no report.pdf sintetico para testar complementacao");

        CustodyCaseRecord rec = CustodyCaseRecord.loadOrCreate(outDir);
        rec.mergeFromOriginPreview(preview, false);
        // Complement what was missing in original report.pdf
        rec.whatsappVersion = "2.24.20.79";
        rec.collectedBy = "Perito Coleta";
        rec.analyzedBy = "Perito Analise";
        rec.custodyLocation = "Sala Cofre 02";
        rec.storageType = CustodyCaseRecord.StorageType.MIDIA_LACRADA;
        rec.storageSealReference = "LACRE-778899";
        rec.copyOriginReportPdf = true;
        rec.generateUnifiedPdf = true;

        CaptureAuditLogManager.recordStartCapture(outDir, rec, outDir.resolve("Chat_ComOrigem-20261123-091000"));
        TechnicalReportGenerator.GenerationResult res = TechnicalReportGenerator.generateAll(outDir, rec, originDir, "b".repeat(64));

        check(res.originPdfCopied && Files.isRegularFile(res.originPdfCopy), "laudo/report.pdf deve ser copiado");
        check("report.pdf".equals(res.originPdfCopy.getFileName().toString()), "copia intacta deve chamar-se laudo/report.pdf");
        check(expectedPdfSha.equalsIgnoreCase(CaptureIntegrity.sha256(res.originPdfCopy)), "SHA-256 da copia de report.pdf deve ser identico ao original");
        check(res.pdfGenerated && Files.isRegularFile(res.pluginPdf), "report-plugin.pdf deve ser gerado");
        check(res.unifiedPdf != null && Files.isRegularFile(res.unifiedPdf), "report-completo-unificado.pdf deve ser gerado quando habilitado");

        String pluginPdfText = readPdfText(res.pluginPdf);
        check(pluginPdfText.contains("SM-S928B"), "modelo do report.pdf original deve constar no report-plugin.pdf");
        check(pluginPdfText.contains("2.24.20.79"), "versao do WhatsApp complementada deve constar no report-plugin.pdf");
        check(pluginPdfText.contains("LACRE-778899"), "lacre da midia lacrada deve constar no report-plugin.pdf");
        check(pluginPdfText.contains(expectedPdfSha), "SHA-256 do report.pdf original deve constar no report-plugin.pdf");

        try (PDDocument mergedDoc = PDDocument.load(res.unifiedPdf.toFile());
             PDDocument pluginDoc = PDDocument.load(res.pluginPdf.toFile())) {
            check(mergedDoc.getNumberOfPages() == pluginDoc.getNumberOfPages() + 1,
                    "PDF unificado deve ter paginas do report-plugin.pdf + pagina do report.pdf original");
        }
    }

    private static void testRealAvillaReportPdfFormatAndTrilingualPluginPdf(Path root) throws Exception {
        Path originDir = Files.createDirectories(root.resolve("origin-avilla-real"));
        Path originPdf = originDir.resolve("report.pdf");
        createExactAvillaReportPdf(originPdf);
        String expectedPdfSha = CaptureIntegrity.sha256(originPdf);

        OriginCustodyBinder.OriginReportPreview preview = OriginCustodyBinder.readDeviceAndCustodyPreview(originDir);
        check(preview.reportPdfPresent, "preview deve detectar report.pdf padrao Avilla");
        check("CP002-hqle2Jam".equals(preview.collectionCode), "Collection code Avilla: " + preview.collectionCode);
        check("Redmi Note 14 Pro".equals(preview.model), "Collected Device Avilla: " + preview.model);
        check("11".equals(preview.osVersion), "Operating System Version Avilla: " + preview.osVersion);
        check("3a75b587".equals(preview.serialNumber), "Device Serial Number Avilla: " + preview.serialNumber);
        check("04-11-2026-09-13-22".equals(preview.startExtraction), "Start of extraction: " + preview.startExtraction);
        check("04-11-2026-09-58-17".equals(preview.endExtraction), "End of extraction: " + preview.endExtraction);

        // Critical regression: empty Avilla fields MUST remain empty (never absorb next field label)
        check(preview.operatorName.isEmpty(), "Operator name vazio nao pode absorver proximo rotulo: " + preview.operatorName);
        check(preview.operatorIdNumber.isEmpty(), "Identification number vazio nao pode absorver proximo rotulo: " + preview.operatorIdNumber);
        check(preview.seizureOrigin.isEmpty(), "Origin of the evidence vazio nao pode absorver proximo rotulo: " + preview.seizureOrigin);
        check(preview.caseNumber.isEmpty(), "Case number vazio nao pode absorver proximo rotulo: " + preview.caseNumber);
        check(preview.declaredCalcDeviceTxtMd5.isEmpty(), "hash vazio nao pode absorver rotulo seguinte");
        check("78e1afcf29f4adc15759ac8efa08684b".equals(preview.declaredHashsTxtMd5), "HASH MD5 Hashs.txt");
        check("af939419e1e1729fe92603d91147fdf0ba148ce172cbc2e0669d39b7b6c09d07".equals(preview.declaredHashsTxtSha256), "HASH Sha256 Hashs.txt");
        check("d0320d6b9151f55f8ca3076d7aa6cc96".equals(preview.declaredHashsAvillaMd5), "HASH MD5 Hashs.avilla");
        check("c38cf6a8c6ecb65a2624bc391b590fb27b70eef3f2b8b8ef9b67e6b8556925c4".equals(preview.declaredHashsAvillaSha256), "HASH Sha256 Hashs.avilla");
        check(preview.methodUsedText.contains("adb.exe pull") && preview.methodUsedText.contains("proceso de recopilaci"),
                "Method used original PT/EN/ES deve ser preservado integralmente");

        Path outDir = Files.createDirectories(root.resolve("case-avilla-real"));
        Path cap = outDir.resolve("Chat_CasoY-20261123-113000");
        createSampleHtmlCapture(cap, "Conversa Caso Y");

        CustodyCaseRecord rec = CustodyCaseRecord.loadOrCreate(outDir);
        rec.mergeFromOriginPreview(preview, false);
        rec.caseNumber = "IPL-2026-0042";
        rec.appliedFilters = "Contatos filtrados: +55 11 99999-0000";
        rec.analyzedBy = "Perito Oficial";
        rec.organizationHeader = "Instituto de Criminalistica";
        rec.reportNumber = "123/2026";
        rec.reportDate = "23/11/2026";
        rec.reportTitle = "Exame em Mensagens WhatsApp";
        rec.copyOriginReportPdf = true;

        CaptureAuditLogManager.recordStartCapture(outDir, rec, cap);
        TechnicalReportGenerator.GenerationResult res = TechnicalReportGenerator.generateAll(outDir, rec, originDir, "e".repeat(64));

        check(Files.isRegularFile(outDir.resolve("laudo/report.pdf")), "laudo/report.pdf deve existir");
        check(Files.isRegularFile(outDir.resolve("laudo/report-plugin.pdf")), "laudo/report-plugin.pdf deve existir");
        check(expectedPdfSha.equalsIgnoreCase(CaptureIntegrity.sha256(outDir.resolve("laudo/report.pdf"))),
                "laudo/report.pdf deve ser copia byte a byte sem edicao");

        String pdfText = readPdfText(res.pluginPdf);
        String pdfNorm = pdfText.replaceAll("\\s+", " ");
        check(pdfNorm.contains("laudo/report.pdf") && pdfNorm.contains("[PT]") && pdfNorm.contains("[EN]") && pdfNorm.contains("[ES]"),
                "report-plugin.pdf deve avisar no inicio em PT/EN/ES que laudo/report.pdf e o original");
        check(pdfNorm.contains("Collection code: CP002-hqle2Jam"), "Collection code no formato Avilla");
        check(pdfNorm.contains("Collected Device: Redmi Note 14 Pro"), "Collected Device no formato Avilla");
        check(pdfNorm.contains("Operating System Version: 11"), "Operating System Version no formato Avilla");
        check(pdfNorm.contains("Device Serial Number: 3a75b587"), "Device Serial Number no formato Avilla");
        check(pdfNorm.contains("Case number") && pdfNorm.contains("IPL-2026-0042"), "Case number preenchido");
        check(pdfNorm.contains("af939419e1e1729fe92603d91147fdf0ba148ce172cbc2e0669d39b7b6c09d07"), "HASH Sha256 Hashs.txt presente");

        int idxLog = pdfNorm.indexOf("Log Cronol");
        int idxMethod = pdfNorm.indexOf("Method used");
        int idxAdbEnd = pdfNorm.indexOf("proceso de recopilaci");
        int idxPluginPt = pdfNorm.indexOf("Processamento IPED e Plugin Chat Captura IPED");
        int idxPluginEn = pdfNorm.indexOf("IPED Processing and Chat Captura IPED Plugin");
        int idxPluginEs = pdfNorm.indexOf("Procesamiento IPED y Plugin Chat Captura IPED");

        check(idxLog > 0 && idxMethod > idxLog, "Log Cronologico Encadeado deve ficar ACIMA de Method used");
        check(idxAdbEnd > idxMethod, "Method used original (adb pull) deve aparecer dentro de Method used");
        check(idxPluginPt > idxAdbEnd && idxPluginEn > idxPluginPt && idxPluginEs > idxPluginEn,
                "Continuacao trilingue PT/EN/ES do plugin deve aparecer logo abaixo do metodo original");
        check(pdfNorm.contains("copia o arquivo report.pdf de origem original para a pasta laudo/"),
                "Metodologia PT deve documentar a copia intacta do report.pdf para laudo/");
        check(pdfNorm.contains("copies the original source report.pdf into the laudo/ folder"),
                "Metodologia EN deve documentar a copia intacta do report.pdf para laudo/");
        check(pdfNorm.contains("copia el archivo report.pdf original de origen a la carpeta laudo/"),
                "Metodologia ES deve documentar a copia intacta do report.pdf para laudo/");
    }

    private static void createExactAvillaReportPdf(Path pdfPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            String[] lines = new String[] {
                "Collection code: CP002-hqle2Jam",
                "______________________________________________________________________________",
                "Evidence Information",
                "______________________________________________________________________________",
                "Collected Device: Redmi Note 14 Pro",
                "Operating System Version: 11",
                "Device Serial Number: 3a75b587",
                "______________________________________________________________________________",
                "System operator identification",
                "______________________________________________________________________________",
                "Operator name (Nome do operador):",
                "Identification number (Numero de identificacao):",
                "Company (Orgao):",
                "Position Company (Cargo):",
                "E-mail:",
                "Phone:",
                "Comments:",
                "Official Avilla Forensics Operator Certification:",
                "______________________________________________________________________________",
                "Who seized the device (Quem fez a apreensao do dispositivo) - Chain Of Custody",
                "______________________________________________________________________________",
                "Origin of the evidence (Origem da Evidencia):",
                "Collection date (Data da Apreensao):",
                "Collection time (Hora da Apreensao):",
                "Evidence Bag Sealed by (Saco de evidencias selado por):",
                "Seal number (n do Lacre):",
                "Where the evidence was stored (Onde a evidencia estava armazenada):",
                "Responsible for storage (Responsaveis pelo armazenamento):",
                "Was the device sealed on? (O dispositivo foi lacrado ligado?):",
                "Did you receive the device turned on? (Voce recebeu o dispositivo ligado?):",
                "Comments:",
                "______________________________________________________________________________",
                "Chain Of Custody",
                "______________________________________________________________________________",
                "Case number (N do caso):",
                "Legal Authorization (Autorizacao Legal):",
                "Victims's Full Name (Nome completo das vitimas):",
                "Suspects's full name (Nome completo dos suspeitos):",
                "Destination of the evidence (Destino da envidencia):",
                "Seal number (n do Lacre):",
                "Responsible for storage (Responsaveis pelo armazenamento):",
                "______________________________________________________________________________",
                "Collection information",
                "______________________________________________________________________________",
                "Start of extraction: 04-11-2026-09-13-22",
                "End of extraction: 04-11-2026-09-58-17",
                "______________________________________________________________________________",
                "Calculation of hashes",
                "______________________________________________________________________________",
                "HASH MD5 Calculation-directly-on-the-device.txt:",
                "HASH Sha256 Calculation-directly-on-the-device.txt:",
                "HASH MD5 Calculation-directly-on-the-device.avilla:",
                "HASH MD5 Hashs.txt:",
                "78e1afcf29f4adc15759ac8efa08684b",
                "HASH Sha256 Hashs.txt:",
                "af939419e1e1729fe92603d91147fdf0ba148ce172cbc2e0669d39b7b6c09d07",
                "HASH MD5 Hashs.avilla:",
                "d0320d6b9151f55f8ca3076d7aa6cc96",
                "HASH Sha256 Hashs.avilla:",
                "c38cf6a8c6ecb65a2624bc391b590fb27b70eef3f2b8b8ef9b67e6b8556925c4",
                "______________________________________________________________________________",
                "Method used",
                "______________________________________________________________________________",
                "Comando utilizado: \"adb.exe pull /sdcard/path_do_arquivo \\pasta_destino_no_computador\". Preserva integridade.",
                "Command used: \"adb.exe pull /sdcard/file_path \\destination_folder_on_computer\". Preserves integrity.",
                "Comando utilizado: \"adb.exe pull /sdcard/file_path \\destination_folder_on_computer\". No se alteren durante el proceso de recopilacion."
            };
            float y = 795f;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9f);
                cs.setLeading(11f);
                cs.newLineAtOffset(40f, y);
                for (String ln : lines) {
                    if (y < 60f) {
                        cs.endText();
                        cs.close();
                        PDPage p2 = new PDPage(PDRectangle.A4);
                        doc.addPage(p2);
                        cs = new PDPageContentStream(doc, p2);
                        y = 795f;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 9f);
                        cs.setLeading(11f);
                        cs.newLineAtOffset(40f, y);
                    }
                    cs.showText(ln);
                    cs.newLine();
                    y -= 11f;
                }
                cs.endText();
            } finally {
                cs.close();
            }
            doc.save(pdfPath.toFile());
        }
    }

    private static void testChainedAuditLogStartReextractAddedDeletedAndTamper(Path root) throws Exception {
        Path outDir = Files.createDirectories(root.resolve("case-audit-log"));
        Path cap1 = outDir.resolve("Chat_Alfa-20261123-092000");
        Path cap2 = outDir.resolve("Chat_Beta-20261123-092500");
        createSampleHtmlCapture(cap1, "Chat Alfa");

        CustodyCaseRecord rec = CustodyCaseRecord.loadOrCreate(outDir);
        CaptureAuditLogManager.recordStartCapture(outDir, rec, cap1);
        rec.save(outDir);

        // Simulate RE-EXTRAIR with all files unchanged OK
        String prevHash = rec.knownCaptures.get(cap1.getFileName().toString()).folderHash;
        CaptureAuditLogManager.recordRecaptureOutcome(outDir, rec, cap1, cap1.getFileName().toString(),
                "INALTERADO_OK", "Intervalo inalterado", prevHash);

        // Add second capture externally and reconcile -> ADICIONADO captura
        createSampleHtmlCapture(cap2, "Chat Beta");
        CaptureAuditLogManager.ReconcileSummary addSum = CaptureAuditLogManager.reconcileKnownCaptures(outDir, rec, "RE-EXTRAIR");
        check(addSum.addedFolders.contains("Chat_Beta-20261123-092500"), "deve detectar nova captura ADICIONADO");
        rec.save(outDir);

        // Delete first capture and reconcile -> DELETADO captura
        deleteTree(cap1);
        CaptureAuditLogManager.ReconcileSummary delSum = CaptureAuditLogManager.reconcileKnownCaptures(outDir, rec, "LAUDO");
        check(delSum.deletedFolders.contains("Chat_Alfa-20261123-092000"), "deve detectar captura removida DELETADO");
        rec.save(outDir);

        List<CaptureAuditLogManager.AuditLogEntry> entries = CaptureAuditLogManager.readLogEntries(outDir);
        check(CaptureAuditLogManager.verifyAuditLogChain(entries), "cadeia SHA-256 do log de capturas deve conferir");
        String logTxt = Files.readString(outDir.resolve("laudo/registro-capturas.log"), StandardCharsets.UTF_8);
        check(logTxt.contains("START | Chat_Alfa-20261123-092000"), "log deve conter START");
        check(logTxt.contains("ADICIONADO captura | Chat_Alfa-20261123-092000"), "log deve conter ADICIONADO captura");
        check(logTxt.contains("RE-EXTRAIR | Chat_Alfa-20261123-092000") && logTxt.contains("INALTERADO_OK"),
                "log deve conter RE-EXTRAIR INALTERADO_OK");
        check(logTxt.contains("msg=1 inalterado OK, img=1 inalterado OK, audio=1 inalterado OK, video=1 inalterado OK, doc=1 inalterado OK"),
                "log RE-EXTRAIR deve discriminar msg/img/audio/video/doc inalterados OK: " + logTxt);
        check(logTxt.contains("ADICIONADO captura | Chat_Beta-20261123-092500"), "log deve conter ADICIONADO da segunda captura");
        check(logTxt.contains("DELETADO captura | Chat_Alfa-20261123-092000"), "log deve conter DELETADO da primeira captura");

        // Tamper a single entry and verify chain fails closed
        entries.get(1).summary = "ADULTERADO";
        check(!CaptureAuditLogManager.verifyAuditLogChain(entries), "adulteracao na cadeia do log deve reprovar verifyAuditLogChain");
    }

    private static void testChainedLogTxtPdfAnchorAndVerifierWithoutPdfBox(Path root) throws Exception {
        Path outDir = Files.createDirectories(root.resolve("case-log-txt-phase2"));
        Path cap1 = outDir.resolve("Chat_Fase2-20261123-120000");
        createSampleHtmlCapture(cap1, "Chat Fase 2");

        CustodyCaseRecord rec = CustodyCaseRecord.loadOrCreate(outDir);
        rec.analyzedBy = "Perito Fase 2";
        CaptureAuditLogManager.recordStartCapture(outDir, rec, cap1);
        String prevHash = rec.knownCaptures.get(cap1.getFileName().toString()).folderHash;
        CaptureAuditLogManager.recordRecaptureOutcome(outDir, rec, cap1, cap1.getFileName().toString(),
                "INALTERADO_OK", "Todos inalterados", prevHash);
        TechnicalReportGenerator.GenerationResult res = TechnicalReportGenerator.generateAll(outDir, rec, null, "f".repeat(64));

        Path logTxt = outDir.resolve("laudo/Log.txt");
        check(Files.isRegularFile(logTxt), "laudo/Log.txt deve ser criado");
        List<String> lines = Files.readAllLines(logTxt, StandardCharsets.UTF_8);
        check(lines.size() >= 4, "Log.txt deve conter linha 0 CRIADO + START + ADICIONADO + RE-EXTRAIR, atual=" + lines.size());
        check(lines.get(0).startsWith("0|") && lines.get(0).contains("|CRIADO|") && lines.get(0).contains("prev=" + CaptureAuditLogManager.ZERO_HASH_64),
                "Linha 0 deve ser CRIADO com prev=64 zeros: " + lines.get(0));

        // 1. Verify PDFBox wrote the uncompressed ASCII anchor into /Info and IntegrityVerifier parses it WITHOUT PDFBox
        String rawPdfAscii = new String(Files.readAllBytes(res.pluginPdf), StandardCharsets.ISO_8859_1);
        check(rawPdfAscii.contains("CHATCAPTURA_LOGANCHOR_V1;"),
                "PDF gerado via PDFBox deve conter marcador ASCII CHATCAPTURA_LOGANCHOR_V1; no dicionario /Info sem compressao");

        Object audit1 = invokeVerifierAuditLog(outDir);
        check(getBoolField(audit1, "applicable") && getBoolField(audit1, "passed") && !getBoolField(audit1, "hasWarning"),
                "Auditoria do Verificador deve passar sem avisos: " + getStringField(audit1, "status") + " - " + getStringField(audit1, "detail"));
        check("CONFERE".equals(getStringField(audit1, "status")), "Status esperado CONFERE, recebido: " + getStringField(audit1, "status"));

        // 2. Append extra line AFTER PDF generation -> Verifier must report CONFERE_COM_LINHAS_POSTERIORES (warning, still valid prefix)
        CaptureAuditLogManager.appendChainedLogLine(outDir, rec, "RE-EXTRAIR", cap1.getFileName().toString(),
                "INALTERADO_OK", "Evento posterior ao PDF", null);
        Object auditExtra = invokeVerifierAuditLog(outDir);
        check(getBoolField(auditExtra, "passed") && getBoolField(auditExtra, "hasWarning")
                        && "CONFERE_COM_LINHAS_POSTERIORES".equals(getStringField(auditExtra, "status")),
                "Linha posterior valida deve gerar CONFERE_COM_LINHAS_POSTERIORES: " + getStringField(auditExtra, "status"));

        // 3. Truncate Log.txt below anchored X -> Verifier MUST fail with FALHA_LOG_TRUNCADO
        List<String> truncated = lines.subList(0, 2);
        Files.write(logTxt, (String.join("\n", truncated) + "\n").getBytes(StandardCharsets.UTF_8));
        Object auditTrunc = invokeVerifierAuditLog(outDir);
        check(!getBoolField(auditTrunc, "passed") && "FALHA_LOG_TRUNCADO".equals(getStringField(auditTrunc, "status")),
                "Log truncado deve reprovar com FALHA_LOG_TRUNCADO: " + getStringField(auditTrunc, "status"));

        // 4. Delete Log.txt -> Verifier MUST fail with FALHA_LOG_AUSENTE
        Files.deleteIfExists(logTxt);
        Object auditMissing = invokeVerifierAuditLog(outDir);
        check(!getBoolField(auditMissing, "passed") && "FALHA_LOG_AUSENTE".equals(getStringField(auditMissing, "status")),
                "Log ausente com ancora no PDF deve reprovar com FALHA_LOG_AUSENTE: " + getStringField(auditMissing, "status"));

        // 5. Missing Log.txt + user confirms RECREATE -> recreates Log.txt, marks PDF, Verifier reports CONFERE_RECRIADO_NO_PDF
        String[] promptedIssue = new String[1];
        CaptureAuditLogManager.appendChainedLogLine(outDir, rec, "START", cap1.getFileName().toString(),
                "CONCLUIDO_OK", "Apos recriacao", (issueType, details) -> {
                    promptedIssue[0] = issueType;
                    return true;
                });
        check("AUSENTE".equals(promptedIssue[0]), "Prompt deve ser acionado com AUSENTE");
        check("RECRIADO_POR_SOLICITACAO_DO_USUARIO".equals(rec.logIntegrityStatus), "Status deve registrar recriacao");
        check(!rec.logIncidents.isEmpty(), "Incidente de recriacao deve ser persistido");
        List<String> recreatedLines = Files.readAllLines(logTxt, StandardCharsets.UTF_8);
        check(recreatedLines.get(0).contains("|CRIADO_RECRIADO|"), "Linha 0 recriada deve marcar CRIADO_RECRIADO");
        TechnicalReportGenerator.GenerationResult resRecreated = TechnicalReportGenerator.generateAll(outDir, rec, null, "f".repeat(64));
        String recreatedPdfText = readPdfText(resRecreated.pluginPdf).replaceAll("\\s+", " ");
        check(recreatedPdfText.contains("RECRIADO_POR_SOLICITACAO_DO_USUARIO"),
                "PDF deve marcar explicitamente que Log.txt foi recriado por solicitacao do usuario");
        Object auditRecreated = invokeVerifierAuditLog(outDir);
        check(getBoolField(auditRecreated, "passed") && getBoolField(auditRecreated, "hasWarning")
                        && "CONFERE_RECRIADO_NO_PDF".equals(getStringField(auditRecreated, "status")),
                "Verificador deve reportar CONFERE_RECRIADO_NO_PDF: " + getStringField(auditRecreated, "status"));

        // 6. Tamper a line inside Log.txt -> Verifier MUST fail with FALHA_LOG_ADULTERADO
        String tamperedContent = Files.readString(logTxt, StandardCharsets.UTF_8).replace("Apos recriacao", "ADULTERADO_X");
        Files.writeString(logTxt, tamperedContent, StandardCharsets.UTF_8);
        Object auditTampered = invokeVerifierAuditLog(outDir);
        check(!getBoolField(auditTampered, "passed") && "FALHA_LOG_ADULTERADO".equals(getStringField(auditTampered, "status")),
                "Log adulterado deve reprovar com FALHA_LOG_ADULTERADO: " + getStringField(auditTampered, "status"));

        // 7. User chooses NOT to recreate on tamper -> does not overwrite broken Log.txt and marks incident
        promptedIssue[0] = null;
        CaptureAuditLogManager.appendChainedLogLine(outDir, rec, "RE-EXTRAIR", cap1.getFileName().toString(),
                "INALTERADO_OK", "Nao deve gravar", (issueType, details) -> {
                    promptedIssue[0] = issueType;
                    return false;
                });
        check("ADULTERADO".equals(promptedIssue[0]), "Prompt deve ser acionado com ADULTERADO");
        check("ADULTERADO_NAO_RECRIADO".equals(rec.logIntegrityStatus), "Status deve ser ADULTERADO_NAO_RECRIADO");
        check(Files.readString(logTxt, StandardCharsets.UTF_8).contains("ADULTERADO_X"),
                "Quando usuario recusa recriacao, Log.txt adulterado nao deve ser sobrescrito");
    }

    private static Object invokeVerifierAuditLog(Path capturesRoot) throws Exception {
        Class<?> iv = Class.forName("IntegrityVerifier");
        java.lang.reflect.Method m = iv.getDeclaredMethod("auditLaudoChainedLog", Path.class);
        m.setAccessible(true);
        return m.invoke(null, capturesRoot);
    }

    private static boolean getBoolField(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(target);
    }

    private static String getStringField(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object v = f.get(target);
        return v == null ? "" : v.toString();
    }

    private static void testCustodyDialogSelectorAndSwingHtmlDisable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CustodyCaseRecord rec = new CustodyCaseRecord();
            rec.collectedBy = "<html><b>x</b>";
            CustodyReportDialog dialog = new CustodyReportDialog(null, rec, null, "Start", false);
            try {
                verifyAllSwingHtmlDisabled(dialog.getContentPane());
                dialog.getStorageComboForTest().setSelectedItem(CustodyCaseRecord.StorageType.PASTA);
                check(!dialog.getStorageCustomFieldForTest().isEnabled(), "campo custom deve ficar desabilitado em Pasta");
                dialog.getStorageComboForTest().setSelectedItem(CustodyCaseRecord.StorageType.MIDIA_LACRADA);
                check(!dialog.getStorageCustomFieldForTest().isEnabled(), "campo custom deve ficar desabilitado em Midia lacrada");
                dialog.getStorageComboForTest().setSelectedItem(CustodyCaseRecord.StorageType.CUSTOM);
                check(dialog.getStorageCustomFieldForTest().isEnabled(), "campo custom deve habilitar ao selecionar Custom");
                check(!containsLabelText(dialog.getContentPane(), "Nome do operador"),
                        "janela do laudo nao deve exibir campos de operador");
                check(!containsLabelText(dialog.getContentPane(), "Quem fez a apreens"),
                        "janela do laudo nao deve exibir campos de apreensao");
                check(containsLabelText(dialog.getContentPane(), "Protocolo Criminal"),
                        "janela do laudo deve exibir campos do relatorio HTML");
                check(containsLabelText(dialog.getContentPane(), "Case number"),
                        "janela do laudo deve exibir campos de Cadeia de Custodia");
            } finally {
                dialog.dispose();
            }
        });
    }

    private static boolean containsLabelText(Container container, String needle) {
        for (Component c : container.getComponents()) {
            if (c instanceof JLabel) {
                String txt = ((JLabel) c).getText();
                if (txt != null && txt.contains(needle)) {
                    return true;
                }
            }
            if (c instanceof Container && containsLabelText((Container) c, needle)) {
                return true;
            }
        }
        return false;
    }

    private static void testLaudoDirectoryIgnoredByVerifierDiscover(Path root) throws Exception {
        Path caseDir = Files.createDirectories(root.resolve("case-verifier-ignore-laudo"));
        Path laudoDir = Files.createDirectories(caseDir.resolve("laudo"));
        // Even if someone drops manifest.json or texto.txt inside laudo/, discover must ignore laudo/
        Files.writeString(laudoDir.resolve("texto.txt"), "nao e captura", StandardCharsets.UTF_8);
        Files.writeString(laudoDir.resolve("manifest.json"), "{}", StandardCharsets.UTF_8);

        check(HtmlLinkViewer.isReportAuxiliaryFolder(laudoDir), "HtmlLinkViewer.isReportAuxiliaryFolder deve reconhecer laudo/");
        check(!HtmlLinkViewer.isRecaptureAuxiliaryFolder(laudoDir), "isRecaptureAuxiliaryFolder NUNCA pode incluir laudo/ (pois limpa pasta)");

        Class<?> iv = Class.forName("IntegrityVerifier");
        java.lang.reflect.Method isIgnored = iv.getDeclaredMethod("isIgnoredCaptureDirectoryName", String.class);
        isIgnored.setAccessible(true);
        check(Boolean.TRUE.equals(isIgnored.invoke(null, "laudo")), "IntegrityVerifier deve ignorar pasta laudo");
    }

    private static void testPhase3CaptureReportPackageGenerationAndSecurity(Path root) throws Exception {
        Path outDir = Files.createDirectories(root.resolve("case-phase3-report-pkg"));
        Path capDir = Files.createDirectories(outDir.resolve("Chat_Fase3-20261123-120000"));
        createSampleHtmlCapture(capDir, "Conversa Fase 3");

        // Inject secret text into messages.json to assert chat-captura-IPED.log never leaks message text
        JSONObject messagesObj = new JSONObject(Files.readString(capDir.resolve("messages.json"), StandardCharsets.UTF_8));
        JSONArray messagesArr = messagesObj.getJSONArray("messages");
        messagesArr.getJSONObject(0).put("text", "TEXTO_CONFIDENCIAL_NAO_DEVE_VAZAR_NO_LOG");
        Files.writeString(capDir.resolve("messages.json"), messagesObj.toString(2), StandardCharsets.UTF_8);

        JSONObject manifest = new JSONObject(Files.readString(capDir.resolve("manifest.json"), StandardCharsets.UTF_8));
        manifest.put("createdAt", "2026-11-23T12:00:00Z");
        Files.writeString(capDir.resolve("manifest.json"), manifest.toString(2), StandardCharsets.UTF_8);

        CustodyCaseRecord rec = new CustodyCaseRecord();
        rec.organizationHeader = "Instituto de Pericia Independente";
        rec.reportNumber = "LAUDO-2026/042";
        rec.reportDate = "23/11/2026";
        rec.reportTitle = "Exame de Conversa WhatsApp";
        rec.signatories = "Perito Oficial A / Perito B";
        rec.caseNumber = "IPL 123/2026";
        rec.legalAuthorization = "Decisao Judicial 999";
        rec.requestReference = "Oficio 77/2026";
        rec.forensicProtocolNumber = "PROT-8888";
        rec.forensicProtocolDate = "20/11/2026";
        rec.examinedMaterial = "Smartphone Redmi Note 14 Pro";
        rec.deviceModel = "Redmi Note 14 Pro";
        rec.deviceSerialNumber = "3a75b587";
        rec.collectedBy = "Equipe Alfa";
        rec.analyzedBy = "Perito Oficial A";
        rec.custodyLocation = "Sala Cofre 02";

        CaptureReportPackageWriter.ReportPackageSummary pkg = CaptureReportPackageWriter.writeCaptureReportPackage(
                outDir, capDir, "Chat_Fase3-20261123-120000", manifest, messagesArr, null, rec);
        check(pkg.writtenRelativePaths.contains("Lista de Arquivos.csv"), "deve gerar Lista de Arquivos.csv");
        check(pkg.writtenRelativePaths.contains("chat-captura-IPED.log"), "deve gerar chat-captura-IPED.log");
        check(pkg.writtenRelativePaths.contains("relatorio.htm"), "deve gerar relatorio.htm");
        check(pkg.writtenRelativePaths.contains("relatorio/contents.htm"), "deve gerar relatorio/contents.htm");
        check(pkg.writtenRelativePaths.contains("relatorio/caseinformation.htm"), "deve gerar relatorio/caseinformation.htm");
        check(pkg.writtenRelativePaths.contains("relatorio/thumbs_001.htm"), "deve gerar galeria thumbs_001.htm");
        check(pkg.writtenRelativePaths.contains("relatorio/cat_whatsapp.html"), "deve gerar cat_whatsapp.html");
        check(pkg.writtenRelativePaths.contains("relatorio/cat_imagens.html"), "deve gerar cat_imagens.html");
        check(pkg.writtenRelativePaths.contains("relatorio/cat_audios.html"), "deve gerar cat_audios.html");
        check(pkg.writtenRelativePaths.contains("relatorio/cat_videos.html"), "deve gerar cat_videos.html");
        check(pkg.writtenRelativePaths.contains("relatorio/cat_documentos_pdf.html"), "deve gerar cat_documentos_pdf.html");
        check(pkg.writtenRelativePaths.contains("relatorio/integridade_resumo.htm"), "deve gerar integridade_resumo.htm");
        check(pkg.writtenRelativePaths.contains("relatorio/ajuda.htm"), "deve gerar ajuda.htm");

        String csv = Files.readString(capDir.resolve("Lista de Arquivos.csv"), StandardCharsets.UTF_8);
        check(csv.startsWith("\"Nome\";\"Atalho\";\"Tamanho\";\"Ext\";\"Marcador\";\"Categoria\";\"MD5\";\"SHA1\";\"Deletado\";\"Recuperado\";\"Acesso\";\"Modifica\u00e7\u00e3o\";\"Cria\u00e7\u00e3o\";\"Caminho\";\"TrackId\";\"SHA256\";\"Mensagem\""),
                "Lista de Arquivos.csv deve seguir cabecalho IPED + SHA256 + Mensagem");
        check(csv.contains("\"whatsapp.html\""), "CSV deve listar whatsapp.html");
        check(csv.contains("\"Imagens\"") && csv.contains("\"\u00c1udios\"") && csv.contains("\"V\u00eddeos\"") && csv.contains("\"Documentos\""),
                "CSV deve categorizar anexos");
        check(CaptureReportPackageWriter.sanitizeCsvCell("=CMD|' /C calc'!A0").startsWith("'="),
                "CSV deve neutralizar formula injection");

        String logTxt = Files.readString(capDir.resolve("chat-captura-IPED.log"), StandardCharsets.UTF_8);
        check(logTxt.startsWith("CHAT_CAPTURA_IPED_LOG_V1\n"), "log da captura deve ter cabecalho V1");
        check(!logTxt.contains("TEXTO_CONFIDENCIAL_NAO_DEVE_VAZAR_NO_LOG"),
                "chat-captura-IPED.log NUNCA pode vazar texto privado das mensagens");
        check(logTxt.contains("counts=msg:1,img:1,audio:1,video:1,doc:1,outros:0"),
                "log da captura deve registrar contagens corretas");

        String caseInfoHtml = Files.readString(capDir.resolve("relatorio/caseinformation.htm"), StandardCharsets.UTF_8);
        check(caseInfoHtml.contains("Instituto de Pericia Independente"), "caseinformation.htm deve conter cabecalho de orgao");
        check(caseInfoHtml.contains("LAUDO-2026/042") && caseInfoHtml.contains("23/11/2026"), "caseinformation.htm deve conter Laudo e data");
        check(caseInfoHtml.contains("Exame de Conversa WhatsApp"), "caseinformation.htm deve conter Titulo");
        check(caseInfoHtml.contains("Perito Oficial A / Perito B"), "caseinformation.htm deve conter Signatario(s)");
        check(caseInfoHtml.contains("Oficio 77/2026") && caseInfoHtml.contains("PROT-8888"), "caseinformation.htm deve conter Solicitacao e Protocolo");
        check(caseInfoHtml.contains("Smartphone Redmi Note 14 Pro"), "caseinformation.htm deve conter Material examinado");
        check(caseInfoHtml.contains("../Lista%20de%20Arquivos.csv") && caseInfoHtml.contains("../chat-captura-IPED.log"),
                "caseinformation.htm deve linkar CSV e log relativos");

        // Ensure ZERO logos/PF branding/scripts/external links across all generated HTML & CSS files
        Class<?> iv = Class.forName("IntegrityVerifier");
        java.lang.reflect.Method valHtml = iv.getDeclaredMethod("validateAuxiliaryReportHtml", Path.class, String.class, String.class);
        valHtml.setAccessible(true);
        java.lang.reflect.Method valCss = iv.getDeclaredMethod("validateAuxiliaryReportCss", String.class, String.class);
        valCss.setAccessible(true);
        for (String rel : pkg.writtenRelativePaths) {
            if (rel.endsWith(".htm") || rel.endsWith(".html") || rel.endsWith(".css")) {
                String content = Files.readString(capDir.resolve(rel), StandardCharsets.UTF_8);
                String lower = content.toLowerCase(java.util.Locale.ROOT);
                check(!lower.contains("emblema.png") && !lower.contains("armas.gif") && !lower.contains("dpf.ico")
                        && !lower.contains("background.jpg") && !lower.contains("header.gif")
                        && !lower.contains("pol\u00edcia federal") && !lower.contains("policia federal")
                        && !lower.contains("<script") && !lower.contains("http://") && !lower.contains("https://"),
                        "Artefato sem logos/scripts/URLs externas: " + rel);
                if (rel.endsWith(".css")) {
                    valCss.invoke(null, rel, content);
                } else {
                    valHtml.invoke(null, capDir, rel, content);
                }
            }
        }

        // Seal integridade.json and verify all report package files are inventoried
        JSONObject integrity = CaptureIntegrity.integrityRecord(capDir, null, "capture_created", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION);
        JSONArray invArts = integrity.getJSONArray("artifacts");
        boolean foundCsv = false, foundLog = false, foundRel = false;
        for (int i = 0; i < invArts.length(); i++) {
            String p = invArts.getJSONObject(i).optString("path", "");
            if ("Lista de Arquivos.csv".equals(p)) foundCsv = true;
            if ("chat-captura-IPED.log".equals(p)) foundLog = true;
            if ("relatorio.htm".equals(p)) foundRel = true;
        }
        check(foundCsv && foundLog && foundRel, "integridade.json deve selar Lista de Arquivos.csv, chat-captura-IPED.log e relatorio.htm");
    }

    private static void verifyAllSwingHtmlDisabled(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JLabel || c instanceof JCheckBox) {
                Object prop = ((javax.swing.JComponent) c).getClientProperty("html.disable");
                check(Boolean.TRUE.equals(prop), "Componente Swing sem html.disable=true: " + c.getClass().getSimpleName());
            }
            if (c instanceof Container) {
                verifyAllSwingHtmlDisabled((Container) c);
            }
        }
    }

    private static void createSyntheticOriginReportPdf(Path pdfPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.setLeading(15f);
                cs.newLineAtOffset(50, 760);
                String[] lines = new String[] {
                    "Avilla Forensics Extraction Report",
                    "Collection Code: AV-2026-0099",
                    "Start of extraction: 20-11-2026-10-15-00",
                    "End of extraction: 20-11-2026-11-00-00",
                    "Manufacturer: Samsung",
                    "Model: SM-S928B",
                    "IMEI 1: 351234567890123",
                    "Serial Number: R5CW999XYZ",
                    "Android Version: Android 14",
                    "Hash SHA256 Hashs.txt: " + "1".repeat(64)
                };
                for (String ln : lines) {
                    cs.showText(ln);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }
    }

    private static void createSampleHtmlCapture(Path capDir, String title) throws IOException {
        Path attDir = Files.createDirectories(capDir.resolve("attachments"));
        byte[] img = new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2 };
        byte[] aud = "OggS-audio".getBytes(StandardCharsets.US_ASCII);
        byte[] vid = new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2' };
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

        String imgSha = sha256Bytes(img);
        String audSha = sha256Bytes(aud);
        String vidSha = sha256Bytes(vid);
        String pdfSha = sha256Bytes(pdf);

        Files.write(attDir.resolve(imgSha + ".png"), img);
        Files.write(attDir.resolve(audSha + ".ogg"), aud);
        Files.write(attDir.resolve(vidSha + ".mp4"), vid);
        Files.write(attDir.resolve(pdfSha + ".pdf"), pdf);

        JSONArray atts = new JSONArray()
                .put(new JSONObject().put("path", "attachments/" + imgSha + ".png").put("mime", "image/png").put("size", img.length).put("sha256", imgSha))
                .put(new JSONObject().put("path", "attachments/" + audSha + ".ogg").put("mime", "audio/ogg").put("size", aud.length).put("sha256", audSha))
                .put(new JSONObject().put("path", "attachments/" + vidSha + ".mp4").put("mime", "video/mp4").put("size", vid.length).put("sha256", vidSha))
                .put(new JSONObject().put("path", "attachments/" + pdfSha + ".pdf").put("mime", "application/pdf").put("size", pdf.length).put("sha256", pdfSha));

        JSONObject msg = new JSONObject()
                .put("domId", "101")
                .put("order", 0)
                .put("timestamp", "2026-11-23 09:00:00")
                .put("sender", "Contato")
                .put("direction", "incoming")
                .put("type", "attachment")
                .put("duplicateOrdinal", 0)
                .put("text", "Mensagem com 4 tipos de midia")
                .put("classes", "incoming")
                .put("key", "a".repeat(64))
                .put("messageHash", "b".repeat(64))
                .put("attachments", atts);

        Files.writeString(capDir.resolve("messages.json"), new JSONObject().put("messages", new JSONArray().put(msg)).toString(2), StandardCharsets.UTF_8);
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><div>ok</div></body></html>";
        Files.writeString(capDir.resolve("whatsapp.html"), html, StandardCharsets.UTF_8);
        JSONObject manifest = new JSONObject()
                .put("schemaVersion", 1)
                .put("captureFormat", "html-message-v1")
                .put("status", "completed")
                .put("state", "complete")
                .put("ipedVersion", "4.3.1")
                .put("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION)
                .put("messageCount", 1)
                .put("htmlSha256", CaptureIntegrity.sha256Text(html))
                .put("captureRootHash", "c".repeat(64))
                .put("anchors", new JSONObject().put("start", "101").put("end", "101"))
                .put("sourceMetadata", new JSONObject().put("name", title).put("path", "/case/whatsapp/" + title + ".html").put("itemSha256", "d".repeat(64)));
        Files.writeString(capDir.resolve("manifest.json"), manifest.toString(2), StandardCharsets.UTF_8);
    }

    private static String readPdfText(Path pdfPath) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String sha256Bytes(byte[] data) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAILED: " + msg);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (java.util.stream.Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}
