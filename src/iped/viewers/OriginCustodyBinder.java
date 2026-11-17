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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                normParentIped, sourceMetadata == null ? "" : sourceMetadata.optString("itemHash", ""));
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

        JSONObject n3Audit = runReadOnlyN3SqliteAudit(dbFiles, capturedRecords);
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
        String overallStatus = computeOverallStatus(custodyFiles, dbFiles, gapInfo, ipedLineage);

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

    private static final class CustodyReportInfo {
        boolean present;
        String sha256 = "";
        String md5 = "";
        String lastModifiedUtc = "";
        String collectionCode = "";
        String startExtraction = "";
        String endExtraction = "";
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

        try {
            Class<?> pdDocCls = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperCls = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Method loadMethod = pdDocCls.getMethod("load", File.class);
            Method closeMethod = pdDocCls.getMethod("close");
            Method getTextMethod = stripperCls.getMethod("getText", pdDocCls);
            Object doc = loadMethod.invoke(null, pdfPath.toFile());
            String text;
            try {
                Object stripper = stripperCls.getDeclaredConstructor().newInstance();
                text = String.valueOf(getTextMethod.invoke(stripper, doc));
            } finally {
                closeMethod.invoke(doc);
            }
            extractCustodyFieldsFromPdfText(text, info);
            info.parseStatus = "extraido_pdfbox";
        } catch (ClassNotFoundException noPdfBox) {
            info.parseStatus = "pdfbox_indisponivel";
        } catch (Exception e) {
            LOGGER.warn("Falha controlada ao extrair metadados tecnicos de report.pdf: {}", e.getMessage());
            info.parseStatus = "erro_leitura_pdf";
        }
        return info;
    }

    static void extractCustodyFieldsFromPdfText(String text, CustodyReportInfo out) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("collection code:") && out.collectionCode.isEmpty()) {
                out.collectionCode = afterColonOrNext(lines, i);
            } else if (lower.startsWith("start of extraction:") && out.startExtraction.isEmpty()) {
                out.startExtraction = extractDateOrValue(afterColonOrNext(lines, i));
            } else if (lower.startsWith("end of extraction:") && out.endExtraction.isEmpty()) {
                out.endExtraction = extractDateOrValue(afterColonOrNext(lines, i));
            } else if (lower.startsWith("hash md5 hashs.txt:") && out.declaredHashsTxtMd5.isEmpty()) {
                out.declaredHashsTxtMd5 = extractHexToken(lines, i, 32);
            } else if (lower.startsWith("hash sha256 hashs.txt:") && out.declaredHashsTxtSha256.isEmpty()) {
                out.declaredHashsTxtSha256 = extractHexToken(lines, i, 64);
            } else if (lower.startsWith("hash md5 hashs.avilla:") && out.declaredHashsAvillaMd5.isEmpty()) {
                out.declaredHashsAvillaMd5 = extractHexToken(lines, i, 32);
            } else if (lower.startsWith("hash sha256 hashs.avilla:") && out.declaredHashsAvillaSha256.isEmpty()) {
                out.declaredHashsAvillaSha256 = extractHexToken(lines, i, 64);
            }
        }
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

    private static JSONArray inspectCustodyFiles(Path originRoot, CustodyReportInfo pdfInfo, HashsTxtIndex hashsIndex) throws IOException {
        JSONArray files = new JSONArray();
        if (hashsIndex.present) {
            boolean md5Ok = pdfInfo.declaredHashsTxtMd5.isEmpty() || pdfInfo.declaredHashsTxtMd5.equalsIgnoreCase(hashsIndex.fileMd5);
            boolean shaOk = pdfInfo.declaredHashsTxtSha256.isEmpty() || pdfInfo.declaredHashsTxtSha256.equalsIgnoreCase(hashsIndex.fileSha256);
            JSONObject obj = new JSONObject()
                    .put("name", "Hashs.txt")
                    .put("sha256", hashsIndex.fileSha256)
                    .put("md5", hashsIndex.fileMd5)
                    .put("encoding", hashsIndex.encoding)
                    .put("lastModifiedUtc", hashsIndex.lastModifiedUtc);
            if (pdfInfo.present && (!pdfInfo.declaredHashsTxtSha256.isEmpty() || !pdfInfo.declaredHashsTxtMd5.isEmpty())) {
                obj.put("matchesReportPdf", md5Ok && shaOk);
            }
            files.put(obj);
        }
        Path avillaPath = findCaseInsensitiveFile(originRoot, "Hashs.avilla");
        if (avillaPath != null) {
            FileHashes afh = hashFileDual(avillaPath);
            boolean md5Ok = pdfInfo.declaredHashsAvillaMd5.isEmpty() || pdfInfo.declaredHashsAvillaMd5.equalsIgnoreCase(afh.md5);
            boolean shaOk = pdfInfo.declaredHashsAvillaSha256.isEmpty() || pdfInfo.declaredHashsAvillaSha256.equalsIgnoreCase(afh.sha256);
            JSONObject obj = new JSONObject()
                    .put("name", "Hashs.avilla")
                    .put("sha256", afh.sha256)
                    .put("md5", afh.md5)
                    .put("note", "Arquivo cifrado + HMAC proprio do Avilla; validado por hash contra report.pdf")
                    .put("lastModifiedUtc", Instant.ofEpochMilli(Files.getLastModifiedTime(avillaPath, LinkOption.NOFOLLOW_LINKS).toMillis()).toString());
            if (pdfInfo.present && (!pdfInfo.declaredHashsAvillaSha256.isEmpty() || !pdfInfo.declaredHashsAvillaMd5.isEmpty())) {
                obj.put("matchesReportPdf", md5Ok && shaOk);
            }
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

    private static JSONObject inspectIpedLineage(
            Path originRoot,
            Path parentIpedRoot,
            JSONObject sourceMetadata,
            OriginDbFiles dbFiles,
            HashsTxtIndex hashsIndex) throws IOException {
        String itemHash = sourceMetadata == null ? "" : sourceMetadata.optString("itemHash", "").trim().toLowerCase(Locale.ROOT);
        String itemHashAlg = itemHash.length() == 64 ? "SHA-256" : (itemHash.length() == 32 ? "MD5" : "DESCONHECIDO");
        JSONObject out = new JSONObject()
                .put("currentItemHashAlgorithm", itemHashAlg)
                .put("currentItemHash", itemHash)
                .put("parentIpedProvided", parentIpedRoot != null)
                .put("htmlMatchedInParentIpedCsv", false)
                .put("msgstoreMatchedInParentIpedCsv", false)
                .put("cryptBackupsMatchedInParentIpedCsv", 0);

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

            boolean htmlMatched = false;
            boolean dbMatched = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                List<String> cols = parseCsvRow(line, delim);
                String rowMd5 = md5Col >= 0 && md5Col < cols.size() ? cols.get(md5Col).trim().toLowerCase(Locale.ROOT) : "";
                String rowSha256 = sha256Col >= 0 && sha256Col < cols.size() ? cols.get(sha256Col).trim().toLowerCase(Locale.ROOT) : "";
                String rowName = nameCol >= 0 && nameCol < cols.size() ? cols.get(nameCol).trim().toLowerCase(Locale.ROOT) : "";

                if (!itemHash.isEmpty() && ((itemHash.length() == 32 && itemHash.equals(rowMd5))
                        || (itemHash.length() == 64 && itemHash.equals(rowSha256)))) {
                    htmlMatched = true;
                }
                if (!dbFiles.msgstoreMd5.isEmpty() && dbFiles.msgstoreMd5.equals(rowMd5) && rowName.contains("msgstore")) {
                    dbMatched = true;
                }
                if (!dbFiles.msgstoreSha256.isEmpty() && dbFiles.msgstoreSha256.equals(rowSha256) && rowName.contains("msgstore")) {
                    dbMatched = true;
                }
                if (!rowSha256.isEmpty() && cryptShas.contains(rowSha256)) {
                    matchedCryptIds.add(rowSha256);
                }
                if (!rowMd5.isEmpty() && originCryptMd5s.contains(rowMd5)) {
                    matchedCryptMd5s.add(rowMd5);
                }
            }
            int matchedTotal = Math.max(matchedCryptIds.size(), matchedCryptMd5s.size());
            out.put("htmlMatchedInParentIpedCsv", htmlMatched);
            out.put("msgstoreMatchedInParentIpedCsv", dbMatched);
            out.put("cryptBackupsMatchedInParentIpedCsv", matchedTotal);
            out.put("status", htmlMatched && dbMatched ? "PAI_VINCULADO" : (htmlMatched || dbMatched ? "PAI_PARCIAL" : "PAI_SEM_CORRESPONDENCIA"));
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

    private static Set<String> collectExportedHtmlInlineBlobs(Path parentIpedRoot, String expectedItemHash) {
        Set<String> out = new HashSet<>();
        if (parentIpedRoot == null || expectedItemHash == null || expectedItemHash.isBlank()) {
            return out;
        }
        Path expDir = parentIpedRoot.resolve("Exportados");
        try {
            if (!Files.isDirectory(expDir, LinkOption.NOFOLLOW_LINKS) || CaptureIntegrity.isLinkOrReparse(expDir)) {
                return out;
            }
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
                    FileHashes fh = hashFileDual(file);
                    boolean match = (expectedItemHash.length() == 32 && fh.md5.equalsIgnoreCase(expectedItemHash))
                            || (expectedItemHash.length() == 64 && fh.sha256.equalsIgnoreCase(expectedItemHash));
                    if (match) {
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
                    diskShaToRel = scanMissingAttachmentsBySize(originRoot, capturedRecords, seenShas, inlineMediaShas, hashsIndex);
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
            HashsTxtIndex hashsIndex) throws IOException {
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
        Files.walkFileTree(normOrigin, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(normOrigin) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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

    private static JSONObject runReadOnlyN3SqliteAudit(OriginDbFiles dbFiles, JSONArray capturedRecords) {
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

            List<Long> positiveIds = new ArrayList<>();
            Map<Long, String> expectedTextById = new HashMap<>();
            int nonPositive = 0;
            for (int i = 0; i < capturedRecords.length(); i++) {
                JSONObject rec = capturedRecords.optJSONObject(i);
                if (rec == null) continue;
                String domId = rec.optString("domId", "").trim();
                try {
                    long id = Long.parseLong(domId);
                    if (id > 0) {
                        positiveIds.add(id);
                        expectedTextById.put(id, rec.optString("text", ""));
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
                    String sqlCols = hasMessageTable
                            ? "SELECT _id, chat_row_id, text_data FROM message WHERE _id IN ("
                            : "SELECT _id, key_remote_jid, data FROM messages WHERE _id IN (";
                    for (int offset = 0; offset < positiveIds.size(); offset += 400) {
                        List<Long> chunk = positiveIds.subList(offset, Math.min(positiveIds.size(), offset + 400));
                        StringBuilder q = new StringBuilder(sqlCols);
                        for (int k = 0; k < chunk.size(); k++) {
                            if (k > 0) q.append(',');
                            q.append('?');
                        }
                        q.append(')');
                        Map<Long, String> foundTexts = new HashMap<>();
                        try (PreparedStatement ps = con.prepareStatement(q.toString())) {
                            for (int k = 0; k < chunk.size(); k++) {
                                ps.setLong(k + 1, chunk.get(k));
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    long id = rs.getLong(1);
                                    if (hasMessageTable) seenChatIds.add(rs.getLong(2));
                                    String txt = rs.getString(3);
                                    foundTexts.put(id, txt == null ? "" : txt);
                                }
                            }
                        }
                        for (Long id : chunk) {
                            if (!foundTexts.containsKey(id)) {
                                recoveredOrAbsent++;
                                continue;
                            }
                            String dbTxt = normalizeWhitespace(foundTexts.get(id));
                            String capTxt = normalizeWhitespace(expectedTextById.get(id));
                            if (dbTxt.isEmpty() || capTxt.contains(dbTxt) || dbTxt.contains(capTxt)) {
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
            out.put("status", diverged == 0 && intact ? "CONFERE" : (diverged > 0 ? "COM_DIVERGENCIAS" : "ALTERACAO_ORIGEM_DETECTADA"))
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

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
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
        if (!hasDb) {
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

        String gapStatus = inHashs ? "CUSTODIA_COMPLETA"
                : (compOk ? "CUSTODIA_COMPLEMENTADA_POS_TAR" : "CUSTODIA_PARCIAL_COM_CONTINUIDADE_DOCUMENTADA");

        return new JSONObject()
                .put("hasPostManifestGap", gap)
                .put("status", gapStatus)
                .put("extractionWindow", extWindow)
                .put("avillaManifestFinalizedUtc", formalAt)
                .put("unpackedDatabaseCreatedUtc", dbFiles.msgstoreCtimeUtc)
                .put("pericialStatementPtBr", statement);
    }

    private static String computeOverallStatus(JSONArray custodyFiles, OriginDbFiles dbFiles, JSONObject gapInfo, JSONObject ipedLineage) {
        for (int i = 0; i < custodyFiles.length(); i++) {
            JSONObject cf = custodyFiles.optJSONObject(i);
            if (cf != null && cf.has("matchesReportPdf") && !cf.optBoolean("matchesReportPdf", true)) {
                return "DIVERGENCIA_CUSTODIA";
            }
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
