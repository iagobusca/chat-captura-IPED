/*
 * Chat Captura IPED - Auditoria de Origem Forense
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates {@code origin-binding.json} linking an HTML message capture to its
 * Avilla forensic origin, optional parent IPED case CSV, inline HTML media blobs,
 * and a read-only temporary-copy N3 verification against {@code msgstore.db}.
 */
final class OriginCustodyBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginCustodyBinder.class);

    static final int ORIGIN_BINDING_VERSION = 1;
    static final String ORIGIN_BINDING_FILE = "origin-binding.json";
    private static final long MAX_PDF_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_PDF_PAGES_SAMPLED = 30;
    private static final int MAX_PDF_TEXT_CHARS = 2 * 1024 * 1024;
    private static final long PDF_PARSE_TIMEOUT_SECONDS = 10L;
    private static final int MAX_ORIGIN_WALK_DEPTH = 12;
    private static final int MAX_ORIGIN_WALK_FILES = 50_000;
    private static final long MAX_HASHS_TXT_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_CSV_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_EXPORTED_HTML_BYTES = 64L * 1024L * 1024L;
    private static final Pattern REPORT_DATE_PATTERN = Pattern.compile("(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern INLINE_DATA_URI_PATTERN = Pattern.compile("data:(?:image|audio|video)/[^;\"'\\s]+;base64,([A-Za-z0-9+/=]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYPT_BACKUP_PATTERN = Pattern.compile("(?:msgstore|wa)(?:-[^\\\\/]+)?\\.db\\.crypt\\d+$", Pattern.CASE_INSENSITIVE);

    private OriginCustodyBinder() {
    }

    static final class OriginBindingResult {
        final JSONObject fullBinding;
        final JSONObject manifestSummary;

        OriginBindingResult(JSONObject fullBinding, JSONObject manifestSummary) {
            this.fullBinding = fullBinding;
            this.manifestSummary = manifestSummary;
        }
    }

    static OriginBindingResult buildOriginBinding(
            Path originRoot,
            Path parentIpedRoot,
            JSONObject sourceMetadata,
            JSONArray capturedRecords,
            Set<String> inlineMediaSha256,
            Map<String, String> capturedAttachmentPathBySha,
            String captureOutputFolderName) throws IOException {
        return buildOriginBinding(
                originRoot,
                parentIpedRoot,
                sourceMetadata,
                capturedRecords,
                inlineMediaSha256,
                capturedAttachmentPathBySha,
                captureOutputFolderName,
                null);
    }

    static OriginBindingResult buildOriginBinding(
            Path originRoot,
            Path parentIpedRoot,
            JSONObject sourceMetadata,
            JSONArray capturedRecords,
            Set<String> inlineMediaSha256,
            Map<String, String> capturedAttachmentPathBySha,
            String captureOutputFolderName,
            String capturedHtml) throws IOException {
        if (originRoot == null) {
            throw new IOException("Pasta de origem custodiada nao informada.");
        }
        Path normOrigin = validateSafeDirectory(originRoot, "pasta de origem custodiada");
        Path normParentIped = parentIpedRoot == null ? null : validateSafeDirectory(parentIpedRoot, "caso IPED pai");

        CustodyReportInfo pdfInfo = readCustodyPdf(normOrigin.resolve("report.pdf"));
        HashsTxtIndex hashsIndex = parseHashsTxt(findCaseInsensitiveFile(normOrigin, "Hashs.txt"));
        JSONArray custodyFiles = inspectCustodyFiles(normOrigin, pdfInfo, hashsIndex);

        OriginDbFiles dbFiles = findOriginDatabases(normOrigin, hashsIndex);
        JSONObject complementInfo = readComplementSha256File(normOrigin, dbFiles);

        JSONObject ipedLineage = inspectIpedLineage(normOrigin, normParentIped, sourceMetadata, dbFiles, hashsIndex);
        Set<String> parentExportedInlineBlobShas = collectExportedHtmlInlineBlobs(
                normParentIped, resolveSourceItemSha256(sourceMetadata));
        Set<String> allInlineBlobs = new HashSet<>(parentExportedInlineBlobShas);
        if (inlineMediaSha256 != null) {
            for (String s : inlineMediaSha256) {
                if (s != null && !s.isBlank()) {
                    allInlineBlobs.add(s.toLowerCase(Locale.ROOT));
                }
            }
        }

        AttachmentTraceSummary attSummary = classifyAttachments(
                normOrigin, hashsIndex, capturedRecords, allInlineBlobs, capturedAttachmentPathBySha);

        JSONObject n3Audit = runReadOnlyN3SqliteAudit(dbFiles, capturedRecords, capturedHtml);
        JSONObject gapInfo = buildTimelineGap(pdfInfo, hashsIndex, dbFiles, complementInfo, ipedLineage, attSummary, custodyFiles);

        JSONObject captureAct = new JSONObject()
                .put("capturedAtUtc", OffsetDateTime.now(ZoneOffset.UTC).toString())
                .put("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION)
                .put("ipedVersion", "4.3.1")
                .put("messagesCaptured", capturedRecords == null ? 0 : capturedRecords.length())
                .put("uniqueAttachmentsCaptured", attSummary.uniqueAttachments)
                .put("attachmentStatusCounts", attSummary.counts)
                .put("outputFolderName", captureOutputFolderName == null ? "" : captureOutputFolderName);

        String collectionCode = pdfInfo.collectionCode.isEmpty() ? inferCollectionCodeFromRoot(normOrigin) : pdfInfo.collectionCode;
        String overallStatus = computeOverallStatus(custodyFiles, hashsIndex, dbFiles, gapInfo, ipedLineage, n3Audit);

        JSONObject full = new JSONObject()
                .put("originBindingVersion", ORIGIN_BINDING_VERSION)
                .put("status", overallStatus)
                .put("collectionCode", collectionCode)
                .put("originFolderRelativeName", normOrigin.getFileName() == null ? "" : normOrigin.getFileName().toString())
                .put("custodyFiles", custodyFiles)
                .put("hashsTxtIndexedEntries", hashsIndex.uniqueShaCount)
                .put("sourceDatabase", dbFiles.toJson())
                .put("postExtractionComplement", complementInfo)
                .put("ipedLineage", ipedLineage)
                .put("attachmentsOrigin", attSummary.toJson())
                .put("n3SqliteAudit", n3Audit)
                .put("custodyTimelineGap", gapInfo)
                .put("captureAct", captureAct);

        JSONObject summary = new JSONObject()
                .put("originBindingVersion", ORIGIN_BINDING_VERSION)
                .put("status", overallStatus)
                .put("collectionCode", collectionCode)
                .put("hasPostManifestGap", gapInfo.optBoolean("hasPostManifestGap", false))
                .put("custodyHashsTxtSha256", hashsIndex.fileSha256)
                .put("reportPdfSha256", pdfInfo.sha256)
                .put("sourceDatabaseSha256", dbFiles.msgstoreSha256)
                .put("sourceDatabaseMd5", dbFiles.msgstoreMd5)
                .put("parentIpedMatchedHtml", ipedLineage.optBoolean("htmlMatchedInParentIpedCsv", false))
                .put("parentIpedMatchedDb", ipedLineage.optBoolean("msgstoreMatchedInParentIpedCsv", false))
                .put("n3Status", n3Audit.optString("status", "NAO_EXECUTADO"));

        return new OriginBindingResult(full, summary);
    }

    static Path validateSafeDirectory(Path dir, String label) throws IOException {
        if (dir == null) {
            throw new IOException("Diretorio nulo: " + label);
        }
        Path norm = dir.toAbsolutePath().normalize();
        Path root = norm.getRoot();
        if (root != null && !norm.equals(root)) {
            Path cur = root;
            for (Path part : root.relativize(norm)) {
                cur = cur.resolve(part);
                if (CaptureIntegrity.isLinkOrReparse(cur)) {
                    throw new IOException("Links simbolicos ou juncoes nao sao permitidos no caminho de " + label + ": " + cur);
                }
            }
        }
        if (CaptureIntegrity.isLinkOrReparse(norm) || !Files.isDirectory(norm, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Diretorio invalido para " + label + ": " + norm);
        }
        return norm;
    }

    private static Path findCaseInsensitiveFile(Path dir, String exactName) throws IOException {
        Path direct = dir.resolve(exactName);
        if (Files.exists(direct, LinkOption.NOFOLLOW_LINKS)) {
            if (CaptureIntegrity.isLinkOrReparse(direct) || !Files.isRegularFile(direct, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Arquivo invalido na origem: " + direct);
            }
            return direct;
        }
        try (var stream = Files.list(dir)) {
            for (Path candidate : (Iterable<Path>) stream::iterator) {
                if (candidate.getFileName() != null
                        && candidate.getFileName().toString().equalsIgnoreCase(exactName)
                        && !CaptureIntegrity.isLinkOrReparse(candidate)
                        && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static final class OriginReportPreview {
        boolean reportPdfPresent;
        Path reportPdfPath;
        String originFolderName = "";
        String reportPdfSha256 = "";
        String reportPdfMd5 = "";
        String reportPdfParseStatus = "ausente";
        String collectionCode = "";
        String extractionTool = "";
        String startExtraction = "";
        String endExtraction = "";
        String manufacturer = "";
        String model = "";
        String imei = "";
        String serialNumber = "";
        String osVersion = "";
        String whatsappVersion = "";
        String examinerOrCollector = "";
        String declaredHashsTxtSha256 = "";
        String declaredHashsAvillaSha256 = "";
        String hashsTxtSha256 = "";
        String hashsAvillaSha256 = "";
        String msgstoreRelPath = "";
        String msgstoreSha256 = "";
        String waDbSha256 = "";
        String keySha256 = "";
    }

    static OriginReportPreview readDeviceAndCustodyPreview(Path originRoot) throws IOException {
        OriginReportPreview preview = new OriginReportPreview();
        if (originRoot == null) {
            return preview;
        }
        Path normOrigin = validateSafeDirectory(originRoot, "pasta de origem custodiada");
        preview.originFolderName = normOrigin.getFileName() == null ? "" : normOrigin.getFileName().toString();
        Path pdfPath = findCaseInsensitiveFile(normOrigin, "report.pdf");
        if (pdfPath != null) {
            preview.reportPdfPath = pdfPath;
            CustodyReportInfo pdfInfo = readCustodyPdf(pdfPath);
            preview.reportPdfPresent = pdfInfo.present;
            preview.reportPdfSha256 = pdfInfo.sha256;
            preview.reportPdfMd5 = pdfInfo.md5;
            preview.reportPdfParseStatus = pdfInfo.parseStatus;
            preview.collectionCode = pdfInfo.collectionCode.isEmpty() ? inferCollectionCodeFromRoot(normOrigin) : pdfInfo.collectionCode;
            preview.extractionTool = pdfInfo.extractionTool;
            preview.startExtraction = pdfInfo.startExtraction;
            preview.endExtraction = pdfInfo.endExtraction;
            preview.manufacturer = pdfInfo.manufacturer;
            preview.model = pdfInfo.model;
            preview.imei = pdfInfo.imei;
            preview.serialNumber = pdfInfo.serialNumber;
            preview.osVersion = pdfInfo.osVersion;
            preview.whatsappVersion = pdfInfo.whatsappVersion;
            preview.examinerOrCollector = pdfInfo.examinerOrCollector;
            preview.declaredHashsTxtSha256 = pdfInfo.declaredHashsTxtSha256;
            preview.declaredHashsAvillaSha256 = pdfInfo.declaredHashsAvillaSha256;
        } else {
            preview.collectionCode = inferCollectionCodeFromRoot(normOrigin);
        }
        Path hashsTxt = findCaseInsensitiveFile(normOrigin, "Hashs.txt");
        if (hashsTxt != null && CaptureIntegrity.safeRegularFileSize(hashsTxt) <= MAX_HASHS_TXT_BYTES) {
            preview.hashsTxtSha256 = CaptureIntegrity.sha256(hashsTxt);
        }
        Path hashsAvilla = findCaseInsensitiveFile(normOrigin, "Hashs.avilla");
        if (hashsAvilla != null && CaptureIntegrity.safeRegularFileSize(hashsAvilla) <= MAX_PDF_BYTES) {
            preview.hashsAvillaSha256 = CaptureIntegrity.sha256(hashsAvilla);
        }
        String[] preferred = new String[] {
            "sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases/msgstore.db",
            "sdcard/com.whatsapp/databases/msgstore.db",
            "databases/msgstore.db",
            "msgstore.db"
        };
        for (String rel : preferred) {
            Path p = normOrigin.resolve(rel.replace('/', File.separatorChar));
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(p)
                    && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                preview.msgstoreRelPath = rel;
                preview.msgstoreSha256 = CaptureIntegrity.sha256(p);
                Path dbDir = p.getParent();
                if (dbDir != null) {
                    Path wa = dbDir.resolve("wa.db");
                    if (Files.isRegularFile(wa, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(wa)) {
                        preview.waDbSha256 = CaptureIntegrity.sha256(wa);
                    }
                    Path appRoot = dbDir.getParent();
                    if (appRoot != null) {
                        Path key = appRoot.resolve("files").resolve("key");
                        if (Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(key)) {
                            preview.keySha256 = CaptureIntegrity.sha256(key);
                        }
                    }
                }
                break;
            }
        }
        return preview;
    }

    static final class CustodyReportInfo {
        boolean present;
        String sha256 = "";
        String md5 = "";
        String lastModifiedUtc = "";
        String collectionCode = "";
        String startExtraction = "";
        String endExtraction = "";
        String manufacturer = "";
        String model = "";
        String imei = "";
        String serialNumber = "";
        String osVersion = "";
        String whatsappVersion = "";
        String extractionTool = "";
        String examinerOrCollector = "";
        String declaredHashsTxtMd5 = "";
        String declaredHashsTxtSha256 = "";
        String declaredHashsAvillaMd5 = "";
        String declaredHashsAvillaSha256 = "";
        String parseStatus = "ausente";
    }

    private static CustodyReportInfo readCustodyPdf(Path pdfPath) throws IOException {
        CustodyReportInfo info = new CustodyReportInfo();
        if (pdfPath == null || !Files.exists(pdfPath, LinkOption.NOFOLLOW_LINKS)) {
            return info;
        }
        if (CaptureIntegrity.isLinkOrReparse(pdfPath) || !Files.isRegularFile(pdfPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("report.pdf invalido (link/juncao): " + pdfPath);
        }
        long size = CaptureIntegrity.safeRegularFileSize(pdfPath);
        if (size > MAX_PDF_BYTES) {
            info.present = true;
            info.parseStatus = "excede_limite";
            return info;
        }
        info.present = true;
        FileHashes fh = hashFileDual(pdfPath);
        info.sha256 = fh.sha256;
        info.md5 = fh.md5;
        info.lastModifiedUtc = Instant.ofEpochMilli(Files.getLastModifiedTime(pdfPath, LinkOption.NOFOLLOW_LINKS).toMillis()).toString();

        ExecutorService pdfExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chat-captura-pdf-custody");
            t.setDaemon(true);
            return t;
        });
        try {
            Class<?> pdDocCls = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperCls = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Method getTextMethod = stripperCls.getMethod("getText", pdDocCls);
            Method closeMethod = pdDocCls.getMethod("close");

            CompletableFuture<String> parseFuture = CompletableFuture.supplyAsync(() -> {
                Object doc = null;
                try {
                    try {
                        Class<?> memCls = Class.forName("org.apache.pdfbox.io.MemoryUsageSetting");
                        Method setupMixed = memCls.getMethod("setupMixed", long.class);
                        Object memSetting = setupMixed.invoke(null, 8L * 1024L * 1024L);
                        Method loadMixed = pdDocCls.getMethod("load", File.class, memCls);
                        doc = loadMixed.invoke(null, pdfPath.toFile(), memSetting);
                    } catch (NoSuchMethodException | ClassNotFoundException fallback) {
                        Method loadMethod = pdDocCls.getMethod("load", File.class);
                        doc = loadMethod.invoke(null, pdfPath.toFile());
                    }
                    Object stripper = stripperCls.getDeclaredConstructor().newInstance();
                    try {
                        Method getNumberOfPages = pdDocCls.getMethod("getNumberOfPages");
                        int pageCount = (Integer) getNumberOfPages.invoke(doc);
                        if (pageCount > 0) {
                            Method setStartPage = stripperCls.getMethod("setStartPage", int.class);
                            Method setEndPage = stripperCls.getMethod("setEndPage", int.class);
                            setStartPage.invoke(stripper, 1);
                            setEndPage.invoke(stripper, Math.min(pageCount, MAX_PDF_PAGES_SAMPLED));
                        }
                    } catch (Throwable ignored) {
                    }
                    String extracted = String.valueOf(getTextMethod.invoke(stripper, doc));
                    return extracted.length() > MAX_PDF_TEXT_CHARS
                            ? extracted.substring(0, MAX_PDF_TEXT_CHARS)
                            : extracted;
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (doc != null) {
                        try {
                            closeMethod.invoke(doc);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }, pdfExecutor);

            String text = parseFuture.get(PDF_PARSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            extractCustodyFieldsFromPdfText(text, info);
            info.parseStatus = "extraido_pdfbox";
        } catch (ClassNotFoundException noPdfBox) {
            info.parseStatus = "pdfbox_indisponivel";
        } catch (TimeoutException timeout) {
            LOGGER.warn("Timeout controlado ({}s) ao extrair metadados tecnicos de report.pdf", PDF_PARSE_TIMEOUT_SECONDS);
            info.parseStatus = "timeout_leitura_pdf";
        } catch (Exception e) {
            LOGGER.warn("Falha controlada ao extrair metadados tecnicos de report.pdf: {}", e.getMessage());
            info.parseStatus = "erro_leitura_pdf";
        } finally {
            pdfExecutor.shutdownNow();
        }
        return info;
    }

    static void extractCustodyFieldsFromPdfText(String text, CustodyReportInfo out) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        Set<String> imeis = new LinkedHashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            String ascii = stripDiacriticsLower(line);
            if (out.extractionTool.isEmpty() && ascii.contains("avilla forensics")) {
                out.extractionTool = line.length() <= 120 ? line : "Avilla Forensics";
            }
            if (lower.startsWith("collection code:") && out.collectionCode.isEmpty()) {
                out.collectionCode = afterColonOrNext(lines, i);
            } else if ((lower.startsWith("start of extraction:") || ascii.startsWith("inicio da extracao:")) && out.startExtraction.isEmpty()) {
                out.startExtraction = extractDateOrValue(afterColonOrNext(lines, i));
            } else if ((lower.startsWith("end of extraction:") || ascii.startsWith("fim da extracao:")) && out.endExtraction.isEmpty()) {
                out.endExtraction = extractDateOrValue(afterColonOrNext(lines, i));
            } else if (lower.startsWith("hash md5 hashs.txt:") && out.declaredHashsTxtMd5.isEmpty()) {
                out.declaredHashsTxtMd5 = extractHexToken(lines, i, 32);
            } else if (lower.startsWith("hash sha256 hashs.txt:") && out.declaredHashsTxtSha256.isEmpty()) {
                out.declaredHashsTxtSha256 = extractHexToken(lines, i, 64);
            } else if (lower.startsWith("hash md5 hashs.avilla:") && out.declaredHashsAvillaMd5.isEmpty()) {
                out.declaredHashsAvillaMd5 = extractHexToken(lines, i, 32);
            } else if (lower.startsWith("hash sha256 hashs.avilla:") && out.declaredHashsAvillaSha256.isEmpty()) {
                out.declaredHashsAvillaSha256 = extractHexToken(lines, i, 64);
            } else if (startsWithAny(ascii, "manufacturer:", "brand:", "fabricante:", "marca:") && out.manufacturer.isEmpty()) {
                out.manufacturer = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "device model:", "product model:", "model:", "modelo:", "modelo do dispositivo:") && out.model.isEmpty()) {
                out.model = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "imei:", "imei 1:", "imei1:", "imei 2:", "imei2:")) {
                String val = cleanExtractedFieldValue(afterColonOrNext(lines, i));
                if (!val.isEmpty()) {
                    imeis.add(val);
                }
            } else if (startsWithAny(ascii, "serial number:", "device serial:", "serial:", "numero de serie:", "n de serie:", "no de serie:", "ro.serialno:") && out.serialNumber.isEmpty()) {
                out.serialNumber = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "android version:", "os version:", "system version:", "versao do so:", "versao do android:", "so:", "sistema operacional:") && out.osVersion.isEmpty()) {
                out.osVersion = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "whatsapp version:", "versao do whatsapp:", "com.whatsapp version:", "whatsapp:") && out.whatsappVersion.isEmpty()) {
                out.whatsappVersion = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "extraction tool:", "tool:", "ferramenta:", "software:") && out.extractionTool.isEmpty()) {
                out.extractionTool = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            } else if (startsWithAny(ascii, "examiner:", "collector:", "perito:", "coletado por:", "responsavel:") && out.examinerOrCollector.isEmpty()) {
                out.examinerOrCollector = cleanExtractedFieldValue(afterColonOrNext(lines, i));
            }
        }
        if (out.imei.isEmpty() && !imeis.isEmpty()) {
            out.imei = String.join(" / ", imeis);
        }
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String p : prefixes) {
            if (value.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static String stripDiacriticsLower(String input) {
        String norm = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
        return norm.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String cleanExtractedFieldValue(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed) || "n/a".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }

    private static String afterColonOrNext(String[] lines, int index) {
        String cur = lines[index].trim();
        int colon = cur.indexOf(':');
        if (colon >= 0) {
            String val = cur.substring(colon + 1).trim();
            if (!val.isEmpty()) {
                return val;
            }
        }
        if (index + 1 < lines.length) {
            return lines[index + 1].trim();
        }
        return "";
    }

    private static String extractDateOrValue(String value) {
        Matcher m = REPORT_DATE_PATTERN.matcher(value);
        return m.find() ? m.group(1) : value;
    }

    private static String extractHexToken(String[] lines, int index, int len) {
        Pattern p = Pattern.compile("(?i)\\b([0-9a-f]{" + len + "})\\b");
        String inline = afterColonOrNext(lines, index);
        Matcher m1 = p.matcher(inline);
        if (m1.find()) {
            return m1.group(1).toLowerCase(Locale.ROOT);
        }
        if (index + 1 < lines.length) {
            Matcher m2 = p.matcher(lines[index + 1].trim());
            if (m2.find()) {
                return m2.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    static final class HashsTxtIndex {
        boolean present;
        String encoding = "";
        String fileSha256 = "";
        String fileMd5 = "";
        String lastModifiedUtc = "";
        int uniqueShaCount;
        final Map<String, String> sha256ToRelPath = new HashMap<>();
        final Map<String, String> relPathToSha256 = new HashMap<>();
        final List<JSONObject> cryptBackups = new ArrayList<>();
    }

    static HashsTxtIndex parseHashsTxt(Path path) throws IOException {
        HashsTxtIndex idx = new HashsTxtIndex();
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return idx;
        }
        if (CaptureIntegrity.isLinkOrReparse(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Hashs.txt invalido (link/juncao): " + path);
        }
        long size = CaptureIntegrity.safeRegularFileSize(path);
        if (size > MAX_HASHS_TXT_BYTES) {
            throw new IOException("Hashs.txt excede o limite de " + MAX_HASHS_TXT_BYTES + " bytes.");
        }
        idx.present = true;
        FileHashes fh = hashFileDual(path);
        idx.fileSha256 = fh.sha256;
        idx.fileMd5 = fh.md5;
        idx.lastModifiedUtc = Instant.ofEpochMilli(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()).toString();

        Charset cs = detectTextCharset(path);
        idx.encoding = cs.name();

        String curAlg = "";
        String curHash = "";
        StringBuilder curPath = new StringBuilder();
        boolean inPath = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)), cs), 32768)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = stripBom(rawLine);
                if (line.trim().isEmpty()) {
                    flushHashsEntry(idx, curAlg, curHash, curPath.toString());
                    curAlg = "";
                    curHash = "";
                    curPath.setLength(0);
                    inPath = false;
                    continue;
                }
                if (inPath && (line.startsWith("            ") || line.startsWith("\t"))) {
                    curPath.append(line.trim());
                    continue;
                }
                String trimmed = line.trim();
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String val = trimmed.substring(colon + 1).trim();
                    if ("algorithm".equals(key)) {
                        flushHashsEntry(idx, curAlg, curHash, curPath.toString());
                        curAlg = val.toUpperCase(Locale.ROOT);
                        curHash = "";
                        curPath.setLength(0);
                        inPath = false;
                    } else if ("hash".equals(key)) {
                        curHash = val.toLowerCase(Locale.ROOT);
                        inPath = false;
                    } else if ("path".equals(key)) {
                        curPath.setLength(0);
                        curPath.append(val);
                        inPath = true;
                    }
                }
            }
            flushHashsEntry(idx, curAlg, curHash, curPath.toString());
        }
        return idx;
    }

    private static void flushHashsEntry(HashsTxtIndex idx, String alg, String hash, String rawPath) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            return;
        }
        if (!alg.isEmpty() && !alg.contains("SHA256") && !alg.contains("SHA-256")) {
            return;
        }
        String rel = normalizeRelativeOriginPath(rawPath);
        if (idx.sha256ToRelPath.putIfAbsent(hash, rel) == null) {
            idx.uniqueShaCount++;
        }
        if (!rel.isEmpty()) {
            idx.relPathToSha256.putIfAbsent(rel.toLowerCase(Locale.ROOT), hash);
            if (CRYPT_BACKUP_PATTERN.matcher(rel).find()) {
                idx.cryptBackups.add(new JSONObject().put("relativePath", rel).put("sha256", hash));
            }
        }
    }

    static String normalizeRelativeOriginPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String norm = rawPath.trim().replace('\\', '/');
        String lower = norm.toLowerCase(Locale.ROOT);
        int sdcardIdx = lower.indexOf("/sdcard/");
        if (sdcardIdx >= 0) {
            return norm.substring(sdcardIdx + 1);
        }
        Matcher m = Pattern.compile("(?i)(?:^|/)CopyAll/[^/]+/(.+)$").matcher(norm);
        if (m.find()) {
            return m.group(1);
        }
        if (norm.startsWith("sdcard/")) {
            return norm;
        }
        int lastSlash = norm.lastIndexOf('/');
        return lastSlash >= 0 ? norm.substring(lastSlash + 1) : norm;
    }

    static Charset detectTextCharset(Path path) throws IOException {
        byte[] bom = new byte[4];
        int read = 0;
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            while (read < 4) {
                int r = in.read(bom, read, 4 - read);
                if (r < 0) break;
                read += r;
            }
        }
        if (read >= 2) {
            int b0 = bom[0] & 0xFF;
            int b1 = bom[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) return StandardCharsets.UTF_16LE;
            if (b0 == 0xFE && b1 == 0xFF) return StandardCharsets.UTF_16BE;
        }
        return StandardCharsets.UTF_8;
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static void applyReportPdfCrossCheck(
            JSONObject entry,
            CustodyReportInfo pdfInfo,
            String declaredSha256,
            String actualSha256,
            String declaredMd5,
            String actualMd5) {
        if (!pdfInfo.present) {
            entry.put("matchesReportPdfStatus", "NAO_VERIFICAVEL_PDF_AUSENTE");
            return;
        }
        boolean hasDeclaredSha = declaredSha256 != null && !declaredSha256.isEmpty();
        boolean hasDeclaredMd5 = declaredMd5 != null && !declaredMd5.isEmpty();
        if (hasDeclaredSha) {
            boolean shaMatch = declaredSha256.equalsIgnoreCase(actualSha256);
            boolean md5Match = !hasDeclaredMd5 || declaredMd5.equalsIgnoreCase(actualMd5);
            entry.put("matchesReportPdf", shaMatch && md5Match);
            entry.put("matchesReportPdfAlgorithm", "SHA-256");
            entry.put("matchesReportPdfStatus", (shaMatch && md5Match) ? "CONFERE_SHA256" : "DIVERGE");
        } else if (hasDeclaredMd5) {
            // MD5 jamais valida integridade isoladamente quando SHA-256 nao foi declarado.
            boolean md5Match = declaredMd5.equalsIgnoreCase(actualMd5);
            entry.put("matchesReportPdfSha256Verified", false);
            entry.put("matchesReportPdfMd5Informative", md5Match);
            entry.put("matchesReportPdfStatus", md5Match ? "NAO_VERIFICAVEL_SEM_SHA256_NO_PDF" : "DIVERGE_MD5_NO_PDF");
            entry.put("reportPdfHashNote", md5Match ? "MD5_INFORMATIVO_SEM_SHA256_NO_PDF" : "DIVERGENCIA_MD5_NO_PDF");
            if (!md5Match) {
                entry.put("matchesReportPdf", false);
            }
        } else {
            entry.put("matchesReportPdfStatus", "NAO_VERIFICAVEL_SEM_HASH_DECLARADO_NO_PDF");
            entry.put("reportPdfHashNote", "SEM_HASH_DECLARADO_NO_PDF");
        }
    }

    private static JSONArray inspectCustodyFiles(Path originRoot, CustodyReportInfo pdfInfo, HashsTxtIndex hashsIndex) throws IOException {
        JSONArray files = new JSONArray();
        if (hashsIndex.present) {
            JSONObject obj = new JSONObject()
                    .put("name", "Hashs.txt")
                    .put("sha256", hashsIndex.fileSha256)
                    .put("md5", hashsIndex.fileMd5)
                    .put("encoding", hashsIndex.encoding)
                    .put("lastModifiedUtc", hashsIndex.lastModifiedUtc);
            applyReportPdfCrossCheck(obj, pdfInfo, pdfInfo.declaredHashsTxtSha256, hashsIndex.fileSha256,
                    pdfInfo.declaredHashsTxtMd5, hashsIndex.fileMd5);
            files.put(obj);
        }
        Path avillaPath = findCaseInsensitiveFile(originRoot, "Hashs.avilla");
        if (avillaPath != null) {
            FileHashes afh = hashFileDual(avillaPath);
            JSONObject obj = new JSONObject()
                    .put("name", "Hashs.avilla")
                    .put("sha256", afh.sha256)
                    .put("md5", afh.md5)
                    .put("note", "Arquivo cifrado + HMAC proprio do Avilla; validado por hash contra report.pdf")
                    .put("lastModifiedUtc", Instant.ofEpochMilli(Files.getLastModifiedTime(avillaPath, LinkOption.NOFOLLOW_LINKS).toMillis()).toString());
            applyReportPdfCrossCheck(obj, pdfInfo, pdfInfo.declaredHashsAvillaSha256, afh.sha256,
                    pdfInfo.declaredHashsAvillaMd5, afh.md5);
            files.put(obj);
        }
        if (pdfInfo.present) {
            files.put(new JSONObject()
                    .put("name", "report.pdf")
                    .put("sha256", pdfInfo.sha256)
                    .put("md5", pdfInfo.md5)
                    .put("parseStatus", pdfInfo.parseStatus)
                    .put("extractionWindow", (pdfInfo.startExtraction.isEmpty() && pdfInfo.endExtraction.isEmpty())
                            ? "" : (pdfInfo.startExtraction + " .. " + pdfInfo.endExtraction))
                    .put("lastModifiedUtc", pdfInfo.lastModifiedUtc));
        }
        return files;
    }

    private static final class OriginDbFiles {
        Path msgstorePath;
        String msgstoreRelPath = "";
        String msgstoreSha256 = "";
        String msgstoreMd5 = "";
        long msgstoreSize = -1;
        String msgstoreCtimeUtc = "";
        String msgstoreMtimeUtc = "";
        boolean listedInHashsTxt;

        Path walPath;
        String walSha256 = "";
        long walSize = -1;

        Path shmPath;

        Path waDbPath;
        String waDbSha256 = "";

        Path keyPath;
        String keySha256 = "";

        List<JSONObject> cryptBackups = Collections.emptyList();

        JSONObject toJson() {
            JSONObject o = new JSONObject()
                    .put("present", msgstorePath != null)
                    .put("relativePath", msgstoreRelPath)
                    .put("sha256", msgstoreSha256)
                    .put("md5", msgstoreMd5)
                    .put("size", msgstoreSize)
                    .put("createdTimeUtc", msgstoreCtimeUtc)
                    .put("lastModifiedUtc", msgstoreMtimeUtc)
                    .put("listedInHashsTxt", listedInHashsTxt)
                    .put("walPresent", walPath != null)
                    .put("walSize", walSize)
                    .put("walSha256", walSha256)
                    .put("waDbSha256", waDbSha256)
                    .put("keySha256", keySha256);
            JSONArray cryptArr = new JSONArray();
            for (JSONObject cb : cryptBackups) {
                cryptArr.put(cb);
            }
            o.put("cryptBackupsInHashsTxt", cryptArr);
            return o;
        }
    }

    private static OriginDbFiles findOriginDatabases(Path originRoot, HashsTxtIndex hashsIndex) throws IOException {
        OriginDbFiles db = new OriginDbFiles();
        db.cryptBackups = hashsIndex.cryptBackups;

        String[] preferred = new String[] {
            "sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases/msgstore.db",
            "sdcard/com.whatsapp/databases/msgstore.db",
            "databases/msgstore.db",
            "msgstore.db"
        };
        for (String rel : preferred) {
            Path p = originRoot.resolve(rel.replace('/', File.separatorChar));
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                if (CaptureIntegrity.isLinkOrReparse(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("msgstore.db invalido (link/juncao): " + p);
                }
                db.msgstorePath = p;
                break;
            }
        }
        if (db.msgstorePath == null) {
            db.msgstorePath = findDatabaseBounded(originRoot, "msgstore.db", 10);
        }
        if (db.msgstorePath != null) {
            Path normOrigin = originRoot.toAbsolutePath().normalize();
            db.msgstoreRelPath = normOrigin.relativize(db.msgstorePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
            BasicFileAttributes attrs = Files.readAttributes(db.msgstorePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            db.msgstoreSize = attrs.size();
            db.msgstoreCtimeUtc = attrs.creationTime().toInstant().toString();
            db.msgstoreMtimeUtc = attrs.lastModifiedTime().toInstant().toString();
            FileHashes fh = hashFileDual(db.msgstorePath);
            db.msgstoreSha256 = fh.sha256;
            db.msgstoreMd5 = fh.md5;
            db.listedInHashsTxt = hashsIndex.sha256ToRelPath.containsKey(db.msgstoreSha256);

            Path dbDir = db.msgstorePath.getParent();
            Path wal = dbDir.resolve("msgstore.db-wal");
            if (Files.isRegularFile(wal, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(wal)) {
                db.walPath = wal;
                db.walSize = CaptureIntegrity.safeRegularFileSize(wal);
                db.walSha256 = CaptureIntegrity.sha256(wal);
            }
            Path shm = dbDir.resolve("msgstore.db-shm");
            if (Files.isRegularFile(shm, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(shm)) {
                db.shmPath = shm;
            }
            Path waDb = dbDir.resolve("wa.db");
            if (Files.isRegularFile(waDb, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(waDb)) {
                db.waDbPath = waDb;
                db.waDbSha256 = CaptureIntegrity.sha256(waDb);
            }
            Path appRoot = dbDir.getParent();
            if (appRoot != null) {
                Path key = appRoot.resolve("files").resolve("key");
                if (Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(key)) {
                    db.keyPath = key;
                    db.keySha256 = CaptureIntegrity.sha256(key);
                }
            }
        }
        return db;
    }

    private static Path findDatabaseBounded(Path root, String targetName, int maxDepth) throws IOException {
        Path[] found = new Path[1];
        final int[] scannedCount = new int[1];
        Files.walkFileTree(root, Collections.emptySet(), maxDepth, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(Locale.ROOT);
                if ("media".equals(name) || "thumbs".equals(name) || "cache".equals(name) || "code_cache".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (++scannedCount[0] > MAX_ORIGIN_WALK_FILES) {
                    return FileVisitResult.TERMINATE;
                }
                if (!attrs.isSymbolicLink() && !attrs.isOther() && attrs.isRegularFile()
                        && file.getFileName() != null && targetName.equalsIgnoreCase(file.getFileName().toString())) {
                    found[0] = file;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
    }

    private static JSONObject readComplementSha256File(Path originRoot, OriginDbFiles db) throws IOException {
        Path comp = originRoot.resolve("custodia-complementar-pos-tar.sha256");
        JSONObject info = new JSONObject().put("present", false);
        if (!Files.exists(comp, LinkOption.NOFOLLOW_LINKS)) {
            return info;
        }
        if (CaptureIntegrity.isLinkOrReparse(comp) || !Files.isRegularFile(comp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("custodia-complementar-pos-tar.sha256 invalido");
        }
        String text = CaptureIntegrity.readUtf8Limited(comp, CaptureIntegrity.MAX_JSON_BYTES);
        String fileSha = CaptureIntegrity.sha256(comp);
        boolean matchesMsgstore = !db.msgstoreSha256.isEmpty()
                && text.toLowerCase(Locale.ROOT).contains(db.msgstoreSha256.toLowerCase(Locale.ROOT));
        info.put("present", true)
                .put("sha256", fileSha)
                .put("matchesMsgstoreDb", matchesMsgstore)
                .put("timestampTokenPresent", Files.isRegularFile(originRoot.resolve("custodia-complementar-pos-tar.tsr"), LinkOption.NOFOLLOW_LINKS));
        return info;
    }

    private static String resolveSourceItemSha256(JSONObject sourceMetadata) {
        if (sourceMetadata == null) return "";
        String itemSha = sourceMetadata.optString("itemSha256", "").trim().toLowerCase(Locale.ROOT);
        if (itemSha.matches("[0-9a-f]{64}")) {
            return itemSha;
        }
        String itemHash = sourceMetadata.optString("itemHash", "").trim().toLowerCase(Locale.ROOT);
        return itemHash.matches("[0-9a-f]{64}") ? itemHash : "";
    }

    private static String resolveLegacySourceItemMd5(JSONObject sourceMetadata) {
        if (sourceMetadata == null) return "";
        String itemHash = sourceMetadata.optString("itemHash", "").trim().toLowerCase(Locale.ROOT);
        return itemHash.matches("[0-9a-f]{32}") ? itemHash : "";
    }

    private static JSONObject inspectIpedLineage(
            Path originRoot,
            Path parentIpedRoot,
            JSONObject sourceMetadata,
            OriginDbFiles dbFiles,
            HashsTxtIndex hashsIndex) throws IOException {
        String itemSha256 = resolveSourceItemSha256(sourceMetadata);
        String legacyItemMd5 = resolveLegacySourceItemMd5(sourceMetadata);
        String itemHashAlg = !itemSha256.isEmpty() ? "SHA-256" : (!legacyItemMd5.isEmpty() ? "MD5_INFORMATIVO_SEM_SHA256" : "DESCONHECIDO");
        JSONObject out = new JSONObject()
                .put("currentItemHashAlgorithm", itemHashAlg)
                .put("currentItemHash", !itemSha256.isEmpty() ? itemSha256 : legacyItemMd5)
                .put("currentItemSha256", itemSha256)
                .put("parentIpedProvided", parentIpedRoot != null)
                .put("htmlMatchedInParentIpedCsv", false)
                .put("msgstoreMatchedInParentIpedCsv", false)
                .put("cryptBackupsMatchedInParentIpedCsv", 0)
                .put("htmlMatchedMd5InformativeOnly", false)
                .put("msgstoreMatchedMd5InformativeOnly", false)
                .put("cryptBackupsMatchedMd5InformativeOnly", 0);

        if (parentIpedRoot == null) {
            return out;
        }
        Path csv = parentIpedRoot.resolve("Lista de Arquivos.csv");
        if (!Files.isRegularFile(csv, LinkOption.NOFOLLOW_LINKS) || CaptureIntegrity.isLinkOrReparse(csv)) {
            out.put("status", "csv_pai_ausente");
            return out;
        }
        long size = CaptureIntegrity.safeRegularFileSize(csv);
        if (size > MAX_CSV_BYTES) {
            out.put("status", "csv_pai_excede_limite");
            return out;
        }

        Set<String> cryptShas = new HashSet<>();
        Set<String> originCryptMd5s = new HashSet<>();
        for (JSONObject cb : hashsIndex.cryptBackups) {
            String s = cb.optString("sha256", "").toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) cryptShas.add(s);
            String rel = cb.optString("relativePath", "");
            if (!rel.isEmpty() && originRoot != null) {
                Path p = resolveSafeChild(originRoot, rel);
                if (p != null && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    originCryptMd5s.add(hashFileDual(p).md5);
                }
            }
        }
        Set<String> matchedCryptIds = new HashSet<>();
        Set<String> matchedCryptMd5s = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(Files.newInputStream(csv, LinkOption.NOFOLLOW_LINKS)), StandardCharsets.UTF_8), 65536)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                out.put("status", "csv_pai_vazio");
                return out;
            }
            char delim = headerLine.indexOf(';') >= 0 ? ';' : ',';
            List<String> headers = parseCsvRow(stripBom(headerLine), delim);
            int md5Col = findColumnIndex(headers, "md5");
            int sha256Col = findColumnIndex(headers, "sha256", "sha-256");
            int nameCol = findColumnIndex(headers, "nome", "name");

            boolean htmlMatchedSha = false;
            boolean dbMatchedSha = false;
            boolean htmlMatchedMd5Only = false;
            boolean dbMatchedMd5Only = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                List<String> cols = parseCsvRow(line, delim);
                String rowMd5 = md5Col >= 0 && md5Col < cols.size() ? cols.get(md5Col).trim().toLowerCase(Locale.ROOT) : "";
                String rowSha256 = sha256Col >= 0 && sha256Col < cols.size() ? cols.get(sha256Col).trim().toLowerCase(Locale.ROOT) : "";
                String rowName = nameCol >= 0 && nameCol < cols.size() ? cols.get(nameCol).trim().toLowerCase(Locale.ROOT) : "";

                if (!itemSha256.isEmpty() && !rowSha256.isEmpty() && itemSha256.equals(rowSha256)) {
                    htmlMatchedSha = true;
                } else if (!legacyItemMd5.isEmpty() && rowSha256.isEmpty() && legacyItemMd5.equals(rowMd5)) {
                    htmlMatchedMd5Only = true;
                }
                if (rowName.contains("msgstore")) {
                    if (!dbFiles.msgstoreSha256.isEmpty() && !rowSha256.isEmpty()) {
                        if (dbFiles.msgstoreSha256.equals(rowSha256)) {
                            dbMatchedSha = true;
                        }
                    } else if (rowSha256.isEmpty() && !dbFiles.msgstoreMd5.isEmpty() && dbFiles.msgstoreMd5.equals(rowMd5)) {
                        dbMatchedMd5Only = true;
                    }
                }
                if (!rowSha256.isEmpty() && cryptShas.contains(rowSha256)) {
                    matchedCryptIds.add(rowSha256);
                } else if (rowSha256.isEmpty() && !rowMd5.isEmpty() && originCryptMd5s.contains(rowMd5)) {
                    matchedCryptMd5s.add(rowMd5);
                }
            }
            out.put("htmlMatchedInParentIpedCsv", htmlMatchedSha);
            out.put("msgstoreMatchedInParentIpedCsv", dbMatchedSha);
            out.put("cryptBackupsMatchedInParentIpedCsv", matchedCryptIds.size());
            out.put("htmlMatchedMd5InformativeOnly", htmlMatchedMd5Only);
            out.put("msgstoreMatchedMd5InformativeOnly", dbMatchedMd5Only);
            out.put("cryptBackupsMatchedMd5InformativeOnly", matchedCryptMd5s.size());
            String status;
            if (htmlMatchedSha && dbMatchedSha) {
                status = "PAI_VINCULADO";
            } else if (htmlMatchedSha || dbMatchedSha) {
                status = "PAI_PARCIAL";
            } else if (htmlMatchedMd5Only || dbMatchedMd5Only) {
                status = "PAI_SOMENTE_MD5_INFORMATIVO_SEM_SHA256";
            } else {
                status = "PAI_SEM_CORRESPONDENCIA";
            }
            out.put("status", status);
        }
        return out;
    }

    private static Path resolveSafeChild(Path root, String relativePath) {
        if (root == null || relativePath == null || relativePath.isBlank()) return null;
        try {
            return CaptureIntegrity.resolveContained(root, relativePath.replace('\\', '/'));
        } catch (IOException unsafe) {
            return null;
        }
    }

    static List<String> parseCsvRow(String line, char delimiter) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    private static int findColumnIndex(List<String> headers, String... names) {
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).trim().toLowerCase(Locale.ROOT);
            for (String n : names) {
                if (h.equals(n)) return i;
            }
        }
        return -1;
    }

    private static Set<String> collectExportedHtmlInlineBlobs(Path parentIpedRoot, String expectedItemSha256) {
        Set<String> out = new HashSet<>();
        if (parentIpedRoot == null || expectedItemSha256 == null || !expectedItemSha256.trim().matches("(?i)[0-9a-f]{64}")) {
            return out;
        }
        String targetSha = expectedItemSha256.trim().toLowerCase(Locale.ROOT);
        Path expDir = parentIpedRoot.resolve("Exportados");
        try {
            if (!Files.isDirectory(expDir, LinkOption.NOFOLLOW_LINKS) || CaptureIntegrity.isLinkOrReparse(expDir)) {
                return out;
            }
            final int[] scannedCount = new int[1];
            Files.walkFileTree(expDir, Collections.emptySet(), 6, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(expDir) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (++scannedCount[0] > MAX_ORIGIN_WALK_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".html") && !name.endsWith(".htm")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (attrs.size() > MAX_EXPORTED_HTML_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }
                    // Decisao de correspondencia do HTML exportado exige SHA-256 (nunca MD5 isolado).
                    String actualSha = CaptureIntegrity.sha256(file);
                    if (actualSha.equalsIgnoreCase(targetSha)) {
                        String html = CaptureIntegrity.readUtf8Limited(file, MAX_EXPORTED_HTML_BYTES);
                        Matcher m = INLINE_DATA_URI_PATTERN.matcher(html);
                        while (m.find()) {
                            try {
                                byte[] decoded = Base64.getDecoder().decode(m.group(1));
                                out.add(sha256Bytes(decoded));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
        return out;
    }

    private static final class AttachmentTraceSummary {
        int uniqueAttachments;
        boolean originDiskScanTruncated;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final JSONArray items = new JSONArray();

        AttachmentTraceSummary() {
            counts.put("CUSTODIADO_AVILLA", 0);
            counts.put("EMBUTIDO_NO_HTML_IPED", 0);
            counts.put("EM_ORIGEM_SEM_HASH_TXT", 0);
            counts.put("NAO_LOCALIZADO", 0);
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("uniqueAttachments", uniqueAttachments)
                    .put("originDiskScanTruncated", originDiskScanTruncated)
                    .put("counts", new JSONObject(counts))
                    .put("items", items);
        }
    }

    private static AttachmentTraceSummary classifyAttachments(
            Path originRoot,
            HashsTxtIndex hashsIndex,
            JSONArray capturedRecords,
            Set<String> inlineMediaShas,
            Map<String, String> pathBySha) throws IOException {
        AttachmentTraceSummary summary = new AttachmentTraceSummary();
        Set<String> seenShas = new LinkedHashSet<>();
        Map<String, String> relBySha = new HashMap<>();

        if (capturedRecords != null) {
            for (int i = 0; i < capturedRecords.length(); i++) {
                JSONObject row = capturedRecords.optJSONObject(i);
                if (row == null) continue;
                JSONArray atts = row.optJSONArray("attachments");
                if (atts == null) continue;
                for (int j = 0; j < atts.length(); j++) {
                    JSONObject att = atts.optJSONObject(j);
                    if (att == null) continue;
                    String sha = att.optString("sha256", "").toLowerCase(Locale.ROOT);
                    if (sha.matches("[0-9a-f]{64}") && seenShas.add(sha)) {
                        relBySha.put(sha, att.optString("path", ""));
                    }
                }
            }
        }
        if (pathBySha != null) {
            for (Map.Entry<String, String> e : pathBySha.entrySet()) {
                String s = e.getKey().toLowerCase(Locale.ROOT);
                if (seenShas.add(s)) {
                    relBySha.put(s, e.getValue());
                }
            }
        }

        summary.uniqueAttachments = seenShas.size();
        Map<String, String> diskShaToRel = null;
        for (String sha : seenShas) {
            String originRel = hashsIndex.sha256ToRelPath.get(sha);
            String status;
            boolean verifiedOnDisk = false;
            if (originRel != null) {
                status = "CUSTODIADO_AVILLA";
                Path originDisk = resolveSafeChild(originRoot, originRel);
                if (originDisk != null && Files.isRegularFile(originDisk, LinkOption.NOFOLLOW_LINKS)
                        && !CaptureIntegrity.isLinkOrReparse(originDisk)) {
                    verifiedOnDisk = sha.equalsIgnoreCase(CaptureIntegrity.sha256(originDisk));
                }
            } else if (inlineMediaShas.contains(sha)) {
                status = "EMBUTIDO_NO_HTML_IPED";
                originRel = "(inline data-URI em HTML exportado pelo IPED)";
            } else {
                if (diskShaToRel == null) {
                    boolean[] truncatedOut = new boolean[1];
                    diskShaToRel = scanMissingAttachmentsBySize(originRoot, capturedRecords, seenShas, inlineMediaShas, hashsIndex, truncatedOut);
                    summary.originDiskScanTruncated = truncatedOut[0];
                }
                String diskRel = diskShaToRel.get(sha);
                if (diskRel != null) {
                    originRel = diskRel;
                    status = "EM_ORIGEM_SEM_HASH_TXT";
                    verifiedOnDisk = true;
                } else {
                    originRel = "";
                    status = "NAO_LOCALIZADO";
                }
            }
            summary.counts.put(status, summary.counts.get(status) + 1);
            JSONObject entry = new JSONObject()
                    .put("sha256", sha)
                    .put("capturePath", relBySha.getOrDefault(sha, ""))
                    .put("status", status)
                    .put("originFileVerified", verifiedOnDisk);
            if (!originRel.isEmpty()) {
                entry.put("originRelativePath", originRel);
            }
            summary.items.put(entry);
        }
        return summary;
    }

    private static Map<String, String> scanMissingAttachmentsBySize(
            Path originRoot,
            JSONArray capturedRecords,
            Set<String> seenShas,
            Set<String> inlineMediaShas,
            HashsTxtIndex hashsIndex,
            boolean[] truncatedOut) throws IOException {
        Map<String, String> out = new HashMap<>();
        Set<Long> targetSizes = new HashSet<>();
        Set<String> targetShas = new HashSet<>();
        if (capturedRecords != null) {
            for (int i = 0; i < capturedRecords.length(); i++) {
                JSONObject row = capturedRecords.optJSONObject(i);
                JSONArray atts = row == null ? null : row.optJSONArray("attachments");
                if (atts == null) continue;
                for (int j = 0; j < atts.length(); j++) {
                    JSONObject att = atts.optJSONObject(j);
                    if (att == null) continue;
                    String s = att.optString("sha256", "").toLowerCase(Locale.ROOT);
                    if (s.matches("[0-9a-f]{64}") && !hashsIndex.sha256ToRelPath.containsKey(s) && !inlineMediaShas.contains(s)) {
                        long sz = att.optLong("size", -1);
                        if (sz > 0) targetSizes.add(sz);
                        targetShas.add(s);
                    }
                }
            }
        }
        if (targetSizes.isEmpty() || targetShas.isEmpty()) {
            return out;
        }
        Path normOrigin = originRoot.toAbsolutePath().normalize();
        final int[] scannedCount = new int[1];
        Files.walkFileTree(normOrigin, Collections.emptySet(), MAX_ORIGIN_WALK_DEPTH, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(normOrigin) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(Locale.ROOT);
                if ("cache".equals(name) || "code_cache".equals(name) || "app_webview".equals(name) || name.startsWith(".thumb")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (++scannedCount[0] > MAX_ORIGIN_WALK_FILES) {
                    if (truncatedOut != null && truncatedOut.length > 0) {
                        truncatedOut[0] = true;
                    }
                    return FileVisitResult.TERMINATE;
                }
                if (!attrs.isSymbolicLink() && !attrs.isOther() && attrs.isRegularFile()
                        && targetSizes.contains(attrs.size())) {
                    String s = CaptureIntegrity.sha256(file).toLowerCase(Locale.ROOT);
                    if (targetShas.contains(s)) {
                        out.putIfAbsent(s, normOrigin.relativize(file).toString().replace('\\', '/'));
                        if (out.size() >= targetShas.size()) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static final Pattern CAPTURE_KEY_DIV_PATTERN = Pattern.compile(
            "<div\\b[^>]*\\bdata-(?:capture-)?message-key=\"([0-9a-fA-F]{64})\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOJI_IMG_PATTERN = Pattern.compile(
            "<img\\b[^>]*\\bclass=\"e([0-9a-fA-F_]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FWD_IMG_CLASS_PATTERN = Pattern.compile(
            "\\bclass=\"[^\"]*\\bfwd\\b[^\"]*\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_DIV_TAG_PATTERN = Pattern.compile(
            "<(/?)div\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_SPAN_PATTERN = Pattern.compile(
            "<span\\b[^>]*\\bclass=\"(?:name|name_call|time|recovered)\"[^>]*>.*?</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile(
            "<script\\b[^>]*>.*?</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BLOCK_BREAK_TAG_PATTERN = Pattern.compile(
            "<(?:br|/div|/p|/li)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern FORWARDED_PREFIX_PATTERN = Pattern.compile(
            "^(?:Encaminhada|Forwarded)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ENTITY_DEC_PATTERN = Pattern.compile("&#(\\d{1,7});");
    private static final Pattern NUMERIC_ENTITY_HEX_PATTERN = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");

    private static JSONObject runReadOnlyN3SqliteAudit(
            OriginDbFiles dbFiles,
            JSONArray capturedRecords,
            String capturedHtml) {
        JSONObject out = new JSONObject()
                .put("status", "NAO_EXECUTADO")
                .put("prePostCopyOriginSha256Match", false)
                .put("totalMessages", capturedRecords == null ? 0 : capturedRecords.length())
                .put("matchedActive", 0)
                .put("recoveredOrNonTable", 0)
                .put("diverged", 0)
                .put("schema", "");

        if (dbFiles.msgstorePath == null || capturedRecords == null || capturedRecords.isEmpty()) {
            return out;
        }
        Path tempDir = null;
        try {
            Class.forName("org.sqlite.JDBC");
            String preSha = dbFiles.msgstoreSha256;
            tempDir = Files.createTempDirectory("chat-captura-n3-");
            Path tempDb = tempDir.resolve("msgstore.db");
            Files.copy(dbFiles.msgstorePath, tempDb, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            if (dbFiles.walPath != null) {
                Files.copy(dbFiles.walPath, tempDir.resolve("msgstore.db-wal"), StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            }
            if (dbFiles.shmPath != null) {
                Files.copy(dbFiles.shmPath, tempDir.resolve("msgstore.db-shm"), StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            }

            Map<String, String> htmlBlockByKey = extractCapturedHtmlBlocksByKey(capturedHtml);

            List<Long> positiveIds = new ArrayList<>();
            Map<Long, JSONObject> expectedRecordById = new HashMap<>();
            int nonPositive = 0;
            for (int i = 0; i < capturedRecords.length(); i++) {
                JSONObject rec = capturedRecords.optJSONObject(i);
                if (rec == null) continue;
                String domId = rec.optString("domId", "").trim();
                try {
                    long id = Long.parseLong(domId);
                    if (id > 0) {
                        positiveIds.add(id);
                        expectedRecordById.put(id, rec);
                    } else {
                        nonPositive++;
                    }
                } catch (NumberFormatException nfe) {
                    nonPositive++;
                }
            }

            int matched = 0;
            int recoveredOrAbsent = nonPositive;
            int diverged = 0;
            Set<Long> seenChatIds = new HashSet<>();
            String schemaUsed = "";

            String url = "jdbc:sqlite:" + tempDb.toAbsolutePath();
            try (Connection con = DriverManager.getConnection(url)) {
                boolean hasMessageTable = sqliteTableExists(con, "message");
                boolean hasLegacyMessages = !hasMessageTable && sqliteTableExists(con, "messages");
                schemaUsed = hasMessageTable ? "message" : (hasLegacyMessages ? "messages" : "desconhecido");
                if (hasMessageTable || hasLegacyMessages) {
                    boolean hasMsgTypeCol = hasMessageTable
                            ? sqliteColumnExists(con, "message", "message_type")
                            : sqliteColumnExists(con, "messages", "media_wa_type");
                    String sqlCols;
                    if (hasMessageTable) {
                        sqlCols = hasMsgTypeCol
                                ? "SELECT _id, chat_row_id, text_data, message_type FROM message WHERE _id IN ("
                                : "SELECT _id, chat_row_id, text_data, 0 FROM message WHERE _id IN (";
                    } else {
                        sqlCols = hasMsgTypeCol
                                ? "SELECT _id, key_remote_jid, data, media_wa_type FROM messages WHERE _id IN ("
                                : "SELECT _id, key_remote_jid, data, 0 FROM messages WHERE _id IN (";
                    }
                    for (int offset = 0; offset < positiveIds.size(); offset += 400) {
                        List<Long> chunk = positiveIds.subList(offset, Math.min(positiveIds.size(), offset + 400));
                        StringBuilder q = new StringBuilder(sqlCols);
                        for (int k = 0; k < chunk.size(); k++) {
                            if (k > 0) q.append(',');
                            q.append('?');
                        }
                        q.append(')');
                        Map<Long, String> foundTexts = new HashMap<>();
                        Map<Long, Integer> foundTypes = new HashMap<>();
                        try (PreparedStatement ps = con.prepareStatement(q.toString())) {
                            for (int k = 0; k < chunk.size(); k++) {
                                ps.setLong(k + 1, chunk.get(k));
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    long id = rs.getLong(1);
                                    if (hasMessageTable) seenChatIds.add(rs.getLong(2));
                                    String txt = rs.getString(3);
                                    int mtype = 0;
                                    try {
                                        mtype = rs.getInt(4);
                                    } catch (Exception ignored) {
                                    }
                                    foundTexts.put(id, txt == null ? "" : txt);
                                    foundTypes.put(id, mtype);
                                }
                            }
                        }
                        for (Long id : chunk) {
                            if (!foundTexts.containsKey(id)) {
                                recoveredOrAbsent++;
                                continue;
                            }
                            String dbTxt = foundTexts.get(id);
                            int sqliteMsgType = foundTypes.getOrDefault(id, 0);
                            JSONObject rec = expectedRecordById.get(id);
                            String capTxt = rec != null ? rec.optString("text", "") : "";
                            String sender = rec != null ? rec.optString("sender", "") : "";
                            String time = rec != null ? rec.optString("timestamp", "") : "";
                            String key = rec != null ? rec.optString("key", "").toLowerCase(Locale.ROOT) : "";
                            String recType = rec != null ? rec.optString("type", "") : "";
                            JSONArray atts = rec != null ? rec.optJSONArray("attachments") : null;
                            boolean hasAtt = atts != null && atts.length() > 0;
                            String htmlChunk = (!key.isEmpty() && htmlBlockByKey != null) ? htmlBlockByKey.getOrDefault(key, "") : "";
                            if (matchesForensicMessageRecord(sqliteMsgType, dbTxt, capTxt, sender, time, htmlChunk, hasAtt, recType)) {
                                matched++;
                            } else {
                                diverged++;
                            }
                        }
                    }
                }
            }

            String postSha = CaptureIntegrity.sha256(dbFiles.msgstorePath);
            boolean intact = preSha.equalsIgnoreCase(postSha);
            String n3Status;
            if (!intact) {
                n3Status = "ALTERACAO_ORIGEM_DETECTADA";
            } else if (diverged > 0) {
                n3Status = "COM_DIVERGENCIAS";
            } else if (matched > 0) {
                n3Status = "CONFERE";
            } else if (positiveIds.isEmpty()) {
                n3Status = "SEM_MENSAGENS_TABELA_ATIVA";
            } else {
                n3Status = "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO";
            }
            out.put("status", n3Status)
                    .put("schema", schemaUsed)
                    .put("prePostCopyOriginSha256Match", intact)
                    .put("matchedActive", matched)
                    .put("recoveredOrNonTable", recoveredOrAbsent)
                    .put("diverged", diverged)
                    .put("distinctChatRowIdsMatched", seenChatIds.size());
        } catch (ClassNotFoundException noSqlite) {
            out.put("status", "sqlite_driver_indisponivel");
        } catch (Exception e) {
            LOGGER.warn("Falha controlada na auditoria N3 em copia temporaria SQLite: {}", e.getMessage());
            out.put("status", "erro_sqlite_leitura");
        } finally {
            if (tempDir != null) {
                try (var s = Files.walk(tempDir)) {
                    List<Path> paths = new ArrayList<>();
                    s.forEach(paths::add);
                    paths.sort(Comparator.reverseOrder());
                    for (Path p : paths) {
                        Files.deleteIfExists(p);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return out;
    }

    private static boolean sqliteTableExists(Connection con, String table) {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sqliteColumnExists(Connection con, String table, String column) {
        if (!"message".equals(table) && !"messages".equals(table)) {
            return false;
        }
        try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String colName = rs.getString(2);
                if (column.equalsIgnoreCase(colName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static Map<String, String> extractCapturedHtmlBlocksByKey(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        List<String> keys = new ArrayList<>();
        List<Integer> bodyStarts = new ArrayList<>();
        List<Integer> tagStarts = new ArrayList<>();
        Matcher m = CAPTURE_KEY_DIV_PATTERN.matcher(rawHtml);
        while (m.find()) {
            keys.add(m.group(1).toLowerCase(Locale.ROOT));
            tagStarts.add(m.start());
            bodyStarts.add(m.end());
        }
        for (int i = 0; i < keys.size(); i++) {
            int start = bodyStarts.get(i);
            int end = (i + 1 < tagStarts.size()) ? tagStarts.get(i + 1) : rawHtml.length();
            if (end <= start) continue;
            String chunk = rawHtml.substring(start, end);
            int modalIdx = chunk.indexOf("<section class=\"capture-modal\"");
            if (modalIdx >= 0) {
                chunk = chunk.substring(0, modalIdx);
            }
            int annotIdx = chunk.indexOf("<div class=\"capture-annotation\"");
            if (annotIdx >= 0) {
                chunk = chunk.substring(0, annotIdx);
            }
            int scriptIdx = chunk.indexOf("<script");
            if (scriptIdx >= 0) {
                chunk = chunk.substring(0, scriptIdx);
            }
            map.put(keys.get(i), chunk);
        }
        return map;
    }

    static String cleanHtmlBubbleToText(String htmlChunk) {
        if (htmlChunk == null || htmlChunk.isEmpty()) {
            return "";
        }
        String h = SCRIPT_BLOCK_PATTERN.matcher(htmlChunk).replaceAll(" ");
        h = decodeIpedEmojiImgs(h);
        boolean hasFwd = FWD_IMG_CLASS_PATTERN.matcher(h).find();
        h = removeBalancedDivByClass(h, "(?:quoteFrom|quoteTo|quote|date)");
        h = META_SPAN_PATTERN.matcher(h).replaceAll(" ");
        h = BLOCK_BREAK_TAG_PATTERN.matcher(h).replaceAll(" ");
        h = ANY_HTML_TAG_PATTERN.matcher(h).replaceAll(" ");
        String res = normalizeWhitespace(unescapeBasicHtmlEntities(h));
        if (hasFwd) {
            res = FORWARDED_PREFIX_PATTERN.matcher(res).replaceFirst("").trim();
        }
        return res;
    }

    private static String extractFullHtmlBubbleInnerText(String htmlChunk) {
        if (htmlChunk == null || htmlChunk.isEmpty()) {
            return "";
        }
        String h = removeBalancedDivByClass(htmlChunk, "date");
        h = SCRIPT_BLOCK_PATTERN.matcher(h).replaceAll(" ");
        h = BLOCK_BREAK_TAG_PATTERN.matcher(h).replaceAll(" ");
        h = ANY_HTML_TAG_PATTERN.matcher(h).replaceAll(" ");
        return normalizeWhitespace(unescapeBasicHtmlEntities(h));
    }

    private static String decodeIpedEmojiImgs(String html) {
        Matcher m = EMOJI_IMG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rawCodes = m.group(1);
            String[] parts = rawCodes.split("_");
            StringBuilder emoji = new StringBuilder();
            for (String p : parts) {
                if (p == null || p.isEmpty()) continue;
                try {
                    int cp = Integer.parseInt(p, 16);
                    if (Character.isValidCodePoint(cp)) {
                        emoji.append(new String(Character.toChars(cp)));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(emoji.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String removeBalancedDivByClass(String html, String classRegex) {
        Pattern openPat = Pattern.compile(
                "<div\\b[^>]*\\bclass=\"[^\"]*\\b" + classRegex + "\\b[^\"]*\"[^>]*>",
                Pattern.CASE_INSENSITIVE);
        String cur = html;
        int guard = 0;
        while (guard++ < 32) {
            Matcher openM = openPat.matcher(cur);
            if (!openM.find()) {
                break;
            }
            int start = openM.start();
            int pos = openM.end();
            int depth = 1;
            int removeEnd = -1;
            Matcher divM = ANY_DIV_TAG_PATTERN.matcher(cur);
            divM.region(pos, cur.length());
            while (divM.find()) {
                if ("/".equals(divM.group(1))) {
                    depth--;
                } else {
                    depth++;
                }
                if (depth == 0) {
                    removeEnd = divM.end();
                    break;
                }
            }
            if (removeEnd < 0) {
                break;
            }
            cur = cur.substring(0, start) + " " + cur.substring(removeEnd);
        }
        return cur;
    }

    private static String unescapeBasicHtmlEntities(String text) {
        if (text == null || text.isEmpty() || text.indexOf('&') < 0) {
            return text == null ? "" : text;
        }
        String s = text.replace("&nbsp;", "\u00A0")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
        if (s.contains("&#")) {
            Matcher decM = NUMERIC_ENTITY_DEC_PATTERN.matcher(s);
            StringBuffer sbDec = new StringBuffer();
            while (decM.find()) {
                try {
                    int cp = Integer.parseInt(decM.group(1), 10);
                    if (Character.isValidCodePoint(cp)) {
                        decM.appendReplacement(sbDec, Matcher.quoteReplacement(new String(Character.toChars(cp))));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            decM.appendTail(sbDec);
            s = sbDec.toString();

            Matcher hexM = NUMERIC_ENTITY_HEX_PATTERN.matcher(s);
            StringBuffer sbHex = new StringBuffer();
            while (hexM.find()) {
                try {
                    int cp = Integer.parseInt(hexM.group(1), 16);
                    if (Character.isValidCodePoint(cp)) {
                        hexM.appendReplacement(sbHex, Matcher.quoteReplacement(new String(Character.toChars(cp))));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            hexM.appendTail(sbHex);
            s = sbHex.toString();
        }
        return s.replace("&amp;", "&");
    }

    static boolean matchesForensicMessageRecord(
            int sqliteMessageType,
            String dbRaw,
            String capRaw,
            String senderRaw,
            String timeRaw,
            String htmlChunk,
            boolean hasAttachments,
            String recordType) {
        if (matchesForensicMessageText(dbRaw, capRaw, senderRaw, timeRaw)) {
            return true;
        }
        String db = normalizeWhitespace(dbRaw);
        String cap = normalizeWhitespace(capRaw);
        boolean isSystem = sqliteMessageType == 7
                || "system".equalsIgnoreCase(recordType)
                || (htmlChunk != null && htmlChunk.contains("class=\"systemmessage\""));

        if (db.isEmpty()) {
            if (cap.isEmpty()) {
                return true;
            }
            return sqliteMessageType != 0 || hasAttachments || isSystem;
        }

        if (htmlChunk != null && !htmlChunk.isEmpty()) {
            String fullBubbleText = extractFullHtmlBubbleInnerText(htmlChunk);
            boolean capConsistentWithHtml = cap.equals(fullBubbleText)
                    || stripNonAlphanumeric(cap).equals(stripNonAlphanumeric(fullBubbleText));
            if (capConsistentWithHtml) {
                String cleanedHtml = cleanHtmlBubbleToText(htmlChunk);
                if (matchesForensicMessageText(db, cleanedHtml, "", timeRaw)) {
                    return true;
                }
                String dbNoFmt = stripWhatsAppFormatting(db);
                String clNoFmt = stripWhatsAppFormatting(cleanedHtml);
                if (!dbNoFmt.isEmpty() && canonicalTextSha256Equals(dbNoFmt, clNoFmt)) {
                    return true;
                }
                String dbAlnum = stripNonAlphanumeric(dbNoFmt);
                String clAlnum = stripNonAlphanumeric(clNoFmt);
                if (!dbAlnum.isEmpty() && canonicalTextSha256Equals(dbAlnum, clAlnum)) {
                    return true;
                }
                if (isSystem) {
                    return true;
                }
                if (sqliteMessageType != 0) {
                    if (!dbNoFmt.isEmpty() && (clNoFmt.contains(dbNoFmt) || (!dbAlnum.isEmpty() && clAlnum.contains(dbAlnum)))) {
                        return true;
                    }
                    if (sqliteMessageType == 9 && (hasAttachments || htmlChunk.contains("<a"))) {
                        return true;
                    }
                }
            }
        } else {
            String dbNoFmt = stripWhatsAppFormatting(db);
            String capNoFmt = stripWhatsAppFormatting(cap);
            if (!dbNoFmt.isEmpty() && matchesForensicMessageText(dbNoFmt, capNoFmt, stripWhatsAppFormatting(senderRaw), timeRaw)) {
                return true;
            }
            if (isSystem) {
                return true;
            }
            if (sqliteMessageType != 0 && !dbNoFmt.isEmpty() && capNoFmt.contains(dbNoFmt)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesForensicMessageText(String dbRaw, String capRaw, String senderRaw, String timeRaw) {
        String db = normalizeWhitespace(dbRaw);
        String cap = normalizeWhitespace(capRaw);
        if (db.isEmpty() && cap.isEmpty()) {
            return true;
        }
        if (db.isEmpty() || cap.isEmpty()) {
            return false;
        }
        if (canonicalTextSha256Equals(db, cap)) {
            return true;
        }
        String stripped = cap;
        String sender = normalizeWhitespace(senderRaw);
        if (!sender.isEmpty() && !sender.equals(stripped) && stripped.startsWith(sender)) {
            stripped = stripped.substring(sender.length()).trim();
        }
        String time = normalizeWhitespace(timeRaw);
        if (!time.isEmpty() && !time.equals(stripped) && stripped.endsWith(time)) {
            stripped = stripped.substring(0, stripped.length() - time.length()).trim();
        }
        if (canonicalTextSha256Equals(db, stripped)) {
            return true;
        }
        String noTimeSuffix = stripped.replaceAll("\\s+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?$", "").trim();
        if (canonicalTextSha256Equals(db, noTimeSuffix)) {
            return true;
        }
        String noIsoSuffix = stripped.replaceAll(
                "\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?"
                        + "(?:\\s+Editada\\s+em\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?)?$",
                "").trim();
        if (canonicalTextSha256Equals(db, noIsoSuffix)) {
            return true;
        }
        String noTimePrefix = stripped.replaceFirst("^\\d{1,2}:\\d{2}(?::\\d{2})?\\s+", "").trim();
        return canonicalTextSha256Equals(db, noTimePrefix);
    }

    private static String stripWhatsAppFormatting(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return normalizeWhitespace(value.replaceAll("[*_~`\\uFE0F]+", ""));
    }

    private static String stripNonAlphanumeric(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static boolean canonicalTextSha256Equals(String canonicalA, String canonicalB) {
        if (canonicalA == null || canonicalB == null || canonicalA.isEmpty() || canonicalB.isEmpty()) {
            return false;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digestA = md.digest(canonicalA.getBytes(StandardCharsets.UTF_8));
            md.reset();
            byte[] digestB = md.digest(canonicalB.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(digestA, digestB);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        return nfc.replaceAll("[\\s\\u00A0\\u2007\\u202F]+", " ").trim();
    }

    private static JSONObject buildTimelineGap(
            CustodyReportInfo pdfInfo,
            HashsTxtIndex hashsIndex,
            OriginDbFiles dbFiles,
            JSONObject complementInfo,
            JSONObject ipedLineage,
            AttachmentTraceSummary attSummary,
            JSONArray custodyFiles) {
        boolean hasDb = dbFiles.msgstorePath != null;
        boolean inHashs = dbFiles.listedInHashsTxt;
        boolean compOk = complementInfo != null && complementInfo.optBoolean("matchesMsgstoreDb", false);
        boolean gap = hasDb && !inHashs;

        String extWindow = (pdfInfo.startExtraction.isEmpty() && pdfInfo.endExtraction.isEmpty())
                ? "conforme relatorio Avilla" : (pdfInfo.startExtraction + " .. " + pdfInfo.endExtraction);
        String formalAt = !hashsIndex.lastModifiedUtc.isEmpty() ? hashsIndex.lastModifiedUtc : pdfInfo.lastModifiedUtc;

        String statement;
        if (!hashsIndex.present) {
            statement = "Hashs.txt ausente na pasta de origem informada; referencia criptografica inicial nao verificavel.";
        } else if (!hasDb) {
            statement = "Extracao e custodia Avilla referenciadas pelo relatorio e Hashs.txt; msgstore.db nao localizado na raiz informada.";
        } else if (inHashs) {
            statement = "Extracao realizada em " + extWindow + ", com custodia integral formalizada no Avilla em " + formalAt
                    + ", incluindo o proprio msgstore.db no Hashs.txt (" + dbFiles.msgstoreSha256 + ").";
        } else if (compOk) {
            statement = "Extracao inicial formalizada no Avilla em " + formalAt + "; container com.whatsapp.tar com msgstore.db ("
                    + dbFiles.msgstoreSha256 + ") gerado posteriormente (" + dbFiles.msgstoreCtimeUtc
                    + ") e coberto por adendo pos-extracao verificado.";
        } else {
            int custCount = attSummary.counts.getOrDefault("CUSTODIADO_AVILLA", 0);
            int embCount = attSummary.counts.getOrDefault("EMBUTIDO_NO_HTML_IPED", 0);
            statement = "Extracao realizada em " + extWindow + ", com custodia inicial formalizada no Avilla em " + formalAt
                    + " (Hashs.txt SHA-256 " + shortSha(hashsIndex.fileSha256)
                    + ", cobrindo " + custCount + "/" + attSummary.uniqueAttachments + " midias capturadas e "
                    + dbFiles.cryptBackups.size() + " backups crypt). O container com.whatsapp.tar com msgstore.db em texto claro ("
                    + dbFiles.msgstoreSha256 + ") foi criado posteriormente em " + dbFiles.msgstoreCtimeUtc
                    + " (fora do Hashs.txt inicial), tendo sua integridade formalizada a partir deste vinculo e correlacionada "
                    + (ipedLineage.optBoolean("msgstoreMatchedInParentIpedCsv", false) ? "ao caso IPED pai e " : "")
                    + "a " + embCount + " blob(s) embutido(s) no HTML exportado.";
        }

        String gapStatus = !hashsIndex.present ? "REFERENCIA_HASHS_TXT_AUSENTE"
                : (inHashs ? "CUSTODIA_COMPLETA"
                : (compOk ? "CUSTODIA_COMPLEMENTADA_POS_TAR" : "CUSTODIA_PARCIAL_COM_CONTINUIDADE_DOCUMENTADA"));

        return new JSONObject()
                .put("hasPostManifestGap", gap)
                .put("status", gapStatus)
                .put("extractionWindow", extWindow)
                .put("avillaManifestFinalizedUtc", formalAt)
                .put("unpackedDatabaseCreatedUtc", dbFiles.msgstoreCtimeUtc)
                .put("pericialStatementPtBr", statement);
    }

    private static String computeOverallStatus(
            JSONArray custodyFiles,
            HashsTxtIndex hashsIndex,
            OriginDbFiles dbFiles,
            JSONObject gapInfo,
            JSONObject ipedLineage,
            JSONObject n3Audit) {
        for (int i = 0; i < custodyFiles.length(); i++) {
            JSONObject cf = custodyFiles.optJSONObject(i);
            if (cf != null && cf.has("matchesReportPdf") && !cf.optBoolean("matchesReportPdf", true)) {
                return "DIVERGENCIA_CUSTODIA";
            }
        }
        if (n3Audit != null) {
            String n3Status = n3Audit.optString("status", "");
            int diverged = n3Audit.optInt("diverged", 0);
            boolean prePostMatch = n3Audit.optBoolean("prePostCopyOriginSha256Match", true);
            if (!prePostMatch || "ALTERACAO_ORIGEM_DETECTADA".equalsIgnoreCase(n3Status)) {
                return "ALTERACAO_ORIGEM_DETECTADA";
            }
            if (diverged > 0 || "COM_DIVERGENCIAS".equalsIgnoreCase(n3Status)) {
                return "DIVERGENCIA_CUSTODIA_N3";
            }
            if ("erro_sqlite_leitura".equalsIgnoreCase(n3Status)
                    || "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO".equalsIgnoreCase(n3Status)) {
                return "FALHA_AUDITORIA_N3";
            }
        }
        if (hashsIndex == null || !hashsIndex.present) {
            return "REFERENCIA_CUSTODIA_AUSENTE";
        }
        return gapInfo.optString("status", "VINCULADO");
    }

    private static String inferCollectionCodeFromRoot(Path normOrigin) {
        Path p = normOrigin.getParent();
        if (p != null && "CopyAll".equalsIgnoreCase(p.getFileName() == null ? "" : p.getFileName().toString()) && p.getParent() != null) {
            return p.getParent().getFileName().toString();
        }
        return normOrigin.getFileName() == null ? "" : normOrigin.getFileName().toString();
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? (sha == null ? "" : sha) : sha.substring(0, 8) + "...";
    }

    private static String sha256Bytes(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            return hexLower(dig);
        } catch (Exception e) {
            throw new IOException("SHA-256 indisponivel", e);
        }
    }

    static final class FileHashes {
        final String sha256;
        final String md5;

        FileHashes(String sha256, String md5) {
            this.sha256 = sha256;
            this.md5 = md5;
        }
    }

    static FileHashes hashFileDual(Path file) throws IOException {
        if (file == null || CaptureIntegrity.isLinkOrReparse(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo invalido para calculo de hashes: " + file);
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), 32768)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) {
                        sha.update(buf, 0, n);
                        md5.update(buf, 0, n);
                    }
                }
            }
            return new FileHashes(hexLower(sha.digest()), hexLower(md5.digest()));
        } catch (Exception e) {
            throw new IOException("Falha ao calcular SHA-256/MD5 de " + file, e);
        }
    }

    private static String hexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
