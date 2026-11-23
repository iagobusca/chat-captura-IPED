/*
 * Chat Captura IPED - Gerador de Pacote de Relatorio da Captura (relatorio.htm, CSV e Log)
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Generates the embedded IPED-compatible report package inside each {@code html-message-v1}
 * capture directory before {@code integridade.json} is sealed:
 * <ul>
 *   <li>{@code Lista de Arquivos.csv} (IPED columns + {@code SHA256} + {@code Mensagem})</li>
 *   <li>{@code chat-captura-IPED.log} (technical deterministic capture log without message text)</li>
 *   <li>{@code relatorio.htm} + {@code relatorio/**} (logo-free IPED frameset, case info,
 *       categories, image gallery, integrity summary, offline help, and clean CSS)</li>
 * </ul>
 */
final class CaptureReportPackageWriter {

    static final String CSV_FILE_NAME = "Lista de Arquivos.csv"; //$NON-NLS-1$
    static final String LOG_FILE_NAME = "chat-captura-IPED.log"; //$NON-NLS-1$
    static final String REPORT_ENTRY_FILE_NAME = "relatorio.htm"; //$NON-NLS-1$
    static final String REPORT_SUBDIR_NAME = "relatorio"; //$NON-NLS-1$

    private static final String STRICT_CSP =
            "default-src 'none'; style-src 'self'; img-src 'self'; media-src 'self'; " //$NON-NLS-1$
            + "frame-src 'self'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"; //$NON-NLS-1$

    private static final Pattern ALLOWED_RELATORIO_SUBPATH = Pattern.compile(
            "^relatorio/(?:[A-Za-z0-9._-]+/)?[A-Za-z0-9._-]+\\.(?:htm|html|css)$"); //$NON-NLS-1$

    private static final int MAX_THUMBS = 500;

    private CaptureReportPackageWriter() {
    }

    static boolean isAllowedReportPackagePath(String relPath) {
        if (relPath == null || relPath.isEmpty() || relPath.contains("..") || relPath.indexOf('\\') >= 0) { //$NON-NLS-1$
            return false;
        }
        return CSV_FILE_NAME.equals(relPath)
                || LOG_FILE_NAME.equals(relPath)
                || REPORT_ENTRY_FILE_NAME.equals(relPath)
                || ALLOWED_RELATORIO_SUBPATH.matcher(relPath).matches();
    }

    static final class AttachmentItem {
        final String relPath;
        final String fileName;
        final String ext;
        final String mime;
        final String categoryKey;
        final String categoryLabel;
        final long size;
        final String sha256;
        final String md5;
        final String sha1;
        final String messageRef;

        AttachmentItem(String relPath, String fileName, String ext, String mime,
                String categoryKey, String categoryLabel, long size,
                String sha256, String md5, String sha1, String messageRef) {
            this.relPath = relPath;
            this.fileName = fileName;
            this.ext = ext;
            this.mime = mime;
            this.categoryKey = categoryKey;
            this.categoryLabel = categoryLabel;
            this.size = size;
            this.sha256 = sha256;
            this.md5 = md5;
            this.sha1 = sha1;
            this.messageRef = messageRef;
        }
    }

    static final class MultiDigest {
        final String md5;
        final String sha1;
        final String sha256;

        MultiDigest(String md5, String sha1, String sha256) {
            this.md5 = md5;
            this.sha1 = sha1;
            this.sha256 = sha256;
        }
    }

    static final class ReportPackageSummary {
        final List<String> writtenRelativePaths;
        final long totalBytes;

        ReportPackageSummary(List<String> writtenRelativePaths, long totalBytes) {
            this.writtenRelativePaths = writtenRelativePaths;
            this.totalBytes = totalBytes;
        }
    }

    /**
     * Writes the complete capture report package inside {@code captureStagingDir}.
     * Must be invoked after {@code attachments/*}, {@code messages.json}, {@code whatsapp.html},
     * {@code manifest.json} (and optional {@code origin-binding.json}) are present in staging
     * and before {@code integridade.json} is computed.
     */
    static ReportPackageSummary writeCaptureReportPackage(
            Path outputRoot,
            Path captureStagingDir,
            String finalCaptureFolderName,
            JSONObject manifest,
            JSONArray messages,
            JSONObject originBindingJson,
            CustodyCaseRecord custodyRecord) throws IOException {
        if (outputRoot == null || captureStagingDir == null || manifest == null || messages == null) {
            throw new IOException("Parametros invalidos para geracao do pacote de relatorio da captura."); //$NON-NLS-1$
        }
        String folderName = nonBlank(finalCaptureFolderName,
                captureStagingDir.getFileName() == null ? "captura" : captureStagingDir.getFileName().toString()); //$NON-NLS-1$
        String createdAt = manifest.optString("createdAt", ""); //$NON-NLS-1$ //$NON-NLS-2$
        String pluginVersion = manifest.optString("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION); //$NON-NLS-1$
        String ipedVersion = manifest.optString("ipedVersion", "4.3.1"); //$NON-NLS-1$ //$NON-NLS-2$
        JSONObject sourceMeta = manifest.optJSONObject("sourceMetadata"); //$NON-NLS-1$
        JSONObject anchors = manifest.optJSONObject("anchors"); //$NON-NLS-1$

        Path htmlPath = captureStagingDir.resolve("whatsapp.html"); //$NON-NLS-1$
        Path messagesPath = captureStagingDir.resolve("messages.json"); //$NON-NLS-1$
        MultiDigest htmlDigest = computeMultiDigest(htmlPath);
        long htmlSize = CaptureIntegrity.safeRegularFileSize(htmlPath);
        String messagesSha256 = CaptureIntegrity.sha256(messagesPath);

        List<AttachmentItem> attachments = collectSortedAttachments(captureStagingDir, messages);
        int imgCount = 0;
        int audioCount = 0;
        int videoCount = 0;
        int docCount = 0;
        int otherCount = 0;
        Map<String, List<AttachmentItem>> byCategory = new LinkedHashMap<>();
        byCategory.put("imagens", new ArrayList<>()); //$NON-NLS-1$
        byCategory.put("audios", new ArrayList<>()); //$NON-NLS-1$
        byCategory.put("videos", new ArrayList<>()); //$NON-NLS-1$
        byCategory.put("documentos_pdf", new ArrayList<>()); //$NON-NLS-1$
        byCategory.put("outros", new ArrayList<>()); //$NON-NLS-1$

        for (AttachmentItem att : attachments) {
            List<AttachmentItem> bucket = byCategory.get(att.categoryKey);
            if (bucket != null) {
                bucket.add(att);
            }
            if ("imagens".equals(att.categoryKey)) { //$NON-NLS-1$
                imgCount++;
            } else if ("audios".equals(att.categoryKey)) { //$NON-NLS-1$
                audioCount++;
            } else if ("videos".equals(att.categoryKey)) { //$NON-NLS-1$
                videoCount++;
            } else if ("documentos_pdf".equals(att.categoryKey)) { //$NON-NLS-1$
                docCount++;
            } else {
                otherCount++;
            }
        }

        Map<String, String> filesToWrite = new LinkedHashMap<>();

        // 1. Lista de Arquivos.csv
        filesToWrite.put(CSV_FILE_NAME, buildFilesCsv(
                createdAt, anchors, htmlSize, htmlDigest, attachments));

        // 2. chat-captura-IPED.log
        filesToWrite.put(LOG_FILE_NAME, buildCaptureLog(
                folderName, createdAt, pluginVersion, ipedVersion, manifest, sourceMeta, anchors,
                messages.length(), messagesSha256, htmlDigest.sha256,
                imgCount, audioCount, videoCount, docCount, otherCount,
                originBindingJson, attachments));

        // 3. CSS (clean, logo-free, zero url() / @import)
        filesToWrite.put("relatorio/res/estilo.css", buildEstiloCss()); //$NON-NLS-1$
        filesToWrite.put("relatorio/res/info.css", buildInfoCss()); //$NON-NLS-1$
        filesToWrite.put("relatorio/res/contents.css", buildContentsCss()); //$NON-NLS-1$
        filesToWrite.put("relatorio/res/main.css", buildMainCss()); //$NON-NLS-1$

        // 4. relatorio.htm (frameset)
        filesToWrite.put(REPORT_ENTRY_FILE_NAME, buildFramesetHtml(folderName));

        // 5. relatorio/contents.htm
        filesToWrite.put("relatorio/contents.htm", buildContentsHtml( //$NON-NLS-1$
                messages.length(), byCategory, imgCount > 0));

        // 6. relatorio/caseinformation.htm
        filesToWrite.put("relatorio/caseinformation.htm", buildCaseInformationHtml( //$NON-NLS-1$
                folderName, createdAt, pluginVersion, ipedVersion, manifest, sourceMeta, anchors,
                messages.length(), imgCount, audioCount, videoCount, docCount, otherCount,
                originBindingJson, custodyRecord));

        // 7. Category pages
        filesToWrite.put("relatorio/cat_whatsapp.html", buildWhatsappCategoryHtml( //$NON-NLS-1$
                folderName, createdAt, anchors, messages.length(), htmlSize, htmlDigest));
        for (Map.Entry<String, List<AttachmentItem>> entry : byCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                filesToWrite.put("relatorio/cat_" + entry.getKey() + ".html", //$NON-NLS-1$ //$NON-NLS-2$
                        buildAttachmentCategoryHtml(categoryDisplayTitle(entry.getKey()), entry.getValue()));
            }
        }

        // 8. Image gallery (only when images exist)
        if (imgCount > 0) {
            filesToWrite.put("relatorio/thumbs_001.htm", buildThumbsHtml(byCategory.get("imagens"))); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // 9. Integrity summary & offline help
        filesToWrite.put("relatorio/integridade_resumo.htm", buildIntegritySummaryHtml( //$NON-NLS-1$
                folderName, createdAt, manifest, messagesSha256, htmlDigest, originBindingJson, attachments.size()));
        filesToWrite.put("relatorio/ajuda.htm", buildHelpHtml(pluginVersion, ipedVersion)); //$NON-NLS-1$

        List<String> written = new ArrayList<>();
        long totalBytes = 0L;
        for (Map.Entry<String, String> entry : filesToWrite.entrySet()) {
            String rel = entry.getKey();
            if (!isAllowedReportPackagePath(rel)) {
                throw new IOException("Caminho de relatorio fora da allowlist: " + rel); //$NON-NLS-1$
            }
            Path target = CaptureIntegrity.resolveContained(captureStagingDir, rel);
            String content = entry.getValue();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            CaptureIntegrity.writeBytesAtomically(outputRoot, target, bytes, CaptureIntegrity.MAX_JSON_BYTES);
            written.add(rel);
            totalBytes += bytes.length;
        }
        Collections.sort(written);
        return new ReportPackageSummary(written, totalBytes);
    }

    private static List<AttachmentItem> collectSortedAttachments(Path captureStagingDir, JSONArray messages)
            throws IOException {
        Map<String, JSONObject> firstSpecByPath = new LinkedHashMap<>();
        Map<String, String> firstMsgRefByPath = new LinkedHashMap<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null) {
                continue;
            }
            String domId = msg.optString("domId", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String ts = msg.optString("timestamp", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String ref = ts.isEmpty() ? domId : (domId + " (" + ts + ")"); //$NON-NLS-1$ //$NON-NLS-2$
            JSONArray atts = msg.optJSONArray("attachments"); //$NON-NLS-1$
            if (atts == null) {
                continue;
            }
            for (int j = 0; j < atts.length(); j++) {
                JSONObject att = atts.optJSONObject(j);
                if (att == null) {
                    continue;
                }
                String rel = att.optString("path", ""); //$NON-NLS-1$ //$NON-NLS-2$
                if (rel.isEmpty() || firstSpecByPath.containsKey(rel)) {
                    continue;
                }
                firstSpecByPath.put(rel, att);
                firstMsgRefByPath.put(rel, ref);
            }
        }
        List<String> sortedPaths = new ArrayList<>(firstSpecByPath.keySet());
        Collections.sort(sortedPaths);
        List<AttachmentItem> items = new ArrayList<>(sortedPaths.size());
        for (String rel : sortedPaths) {
            JSONObject att = firstSpecByPath.get(rel);
            Path file = CaptureIntegrity.resolveContained(captureStagingDir, rel);
            if (!CaptureIntegrity.isSafeRegularFile(file)) {
                continue;
            }
            long size = CaptureIntegrity.safeRegularFileSize(file);
            MultiDigest digest = computeMultiDigest(file);
            String fileName = file.getFileName() == null ? rel : file.getFileName().toString();
            String ext = extractExtension(fileName);
            String mime = att.optString("mime", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String catKey = classifyCategoryKey(mime, ext);
            items.add(new AttachmentItem(
                    rel,
                    fileName,
                    ext,
                    mime,
                    catKey,
                    categoryDisplayTitle(catKey),
                    size,
                    digest.sha256,
                    digest.md5,
                    digest.sha1,
                    firstMsgRefByPath.getOrDefault(rel, ""))); //$NON-NLS-1$
        }
        items.sort(Comparator.comparing(a -> a.relPath));
        return items;
    }

    private static String classifyCategoryKey(String mime, String ext) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT); //$NON-NLS-1$
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT); //$NON-NLS-1$
        if (m.startsWith("image/") || e.equals("png") || e.equals("jpg") || e.equals("jpeg") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || e.equals("gif") || e.equals("webp")) { //$NON-NLS-1$ //$NON-NLS-2$
            return "imagens"; //$NON-NLS-1$
        }
        if (m.startsWith("audio/") || e.equals("ogg") || e.equals("wav") || e.equals("mp3")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            return "audios"; //$NON-NLS-1$
        }
        if (m.startsWith("video/") || e.equals("mp4") || e.equals("webm")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            return "videos"; //$NON-NLS-1$
        }
        if ("application/pdf".equals(m) || e.equals("pdf") || e.equals("doc") || e.equals("docx") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || e.equals("xls") || e.equals("xlsx") || e.equals("ppt") || e.equals("pptx") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || e.equals("txt") || e.equals("csv")) { //$NON-NLS-1$ //$NON-NLS-2$
            return "documentos_pdf"; //$NON-NLS-1$
        }
        return "outros"; //$NON-NLS-1$
    }

    private static String categoryDisplayTitle(String key) {
        if ("imagens".equals(key)) { //$NON-NLS-1$
            return "Imagens"; //$NON-NLS-1$
        }
        if ("audios".equals(key)) { //$NON-NLS-1$
            return "\u00c1udios"; //$NON-NLS-1$
        }
        if ("videos".equals(key)) { //$NON-NLS-1$
            return "V\u00eddeos"; //$NON-NLS-1$
        }
        if ("documentos_pdf".equals(key)) { //$NON-NLS-1$
            return "Documentos"; //$NON-NLS-1$
        }
        return "Outros Arquivos"; //$NON-NLS-1$
    }

    private static String extractExtension(String fileName) {
        if (fileName == null) {
            return ""; //$NON-NLS-1$
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot >= fileName.length() - 1) {
            return ""; //$NON-NLS-1$
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static MultiDigest computeMultiDigest(Path file) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1"); //$NON-NLS-1$
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            byte[] buf = new byte[65536];
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    md5.update(buf, 0, read);
                    sha1.update(buf, 0, read);
                    sha256.update(buf, 0, read);
                }
            }
            return new MultiDigest(toHex(md5.digest()), toHex(sha1.digest()), toHex(sha256.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Algoritmo de hash indisponivel", e); //$NON-NLS-1$
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0x0f, 16));
            sb.append(Character.forDigit(b & 0x0f, 16));
        }
        return sb.toString();
    }

    private static String buildFilesCsv(
            String createdAt,
            JSONObject anchors,
            long htmlSize,
            MultiDigest htmlDigest,
            List<AttachmentItem> attachments) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("\"Nome\";\"Atalho\";\"Tamanho\";\"Ext\";\"Marcador\";\"Categoria\";\"MD5\";\"SHA1\";\"Deletado\";\"Recuperado\";\"Acesso\";\"Modifica\u00e7\u00e3o\";\"Cria\u00e7\u00e3o\";\"Caminho\";\"TrackId\";\"SHA256\";\"Mensagem\"\n"); //$NON-NLS-1$
        String anchorRange = anchors == null ? "" //$NON-NLS-1$
                : (anchors.optString("start", "") + ".." + anchors.optString("end", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        appendCsvRow(sb,
                "whatsapp.html", //$NON-NLS-1$
                "", //$NON-NLS-1$
                Long.toString(htmlSize),
                "html", //$NON-NLS-1$
                "Captura Selecionada", //$NON-NLS-1$
                "Conversas WhatsApp", //$NON-NLS-1$
                htmlDigest.md5.toUpperCase(Locale.ROOT),
                htmlDigest.sha1.toUpperCase(Locale.ROOT),
                "false", //$NON-NLS-1$
                "false", //$NON-NLS-1$
                "", //$NON-NLS-1$
                createdAt,
                createdAt,
                "whatsapp.html", //$NON-NLS-1$
                "0", //$NON-NLS-1$
                htmlDigest.sha256.toUpperCase(Locale.ROOT),
                anchorRange);
        int trackId = 1;
        for (AttachmentItem att : attachments) {
            appendCsvRow(sb,
                    att.fileName,
                    "", //$NON-NLS-1$
                    Long.toString(att.size),
                    att.ext,
                    "Captura Selecionada", //$NON-NLS-1$
                    att.categoryLabel,
                    att.md5.toUpperCase(Locale.ROOT),
                    att.sha1.toUpperCase(Locale.ROOT),
                    "false", //$NON-NLS-1$
                    "false", //$NON-NLS-1$
                    "", //$NON-NLS-1$
                    createdAt,
                    createdAt,
                    att.relPath,
                    Integer.toString(trackId++),
                    att.sha256.toUpperCase(Locale.ROOT),
                    att.messageRef);
        }
        return sb.toString();
    }

    private static void appendCsvRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append('"').append(sanitizeCsvCell(cells[i])).append('"');
        }
        sb.append('\n');
    }

    static String sanitizeCsvCell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        String cleaned = raw.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\0', ' '); //$NON-NLS-1$ //$NON-NLS-2$
        char first = cleaned.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
            cleaned = "'" + cleaned; //$NON-NLS-1$
        }
        return cleaned.replace("\"", "\"\""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String buildCaptureLog(
            String folderName,
            String createdAt,
            String pluginVersion,
            String ipedVersion,
            JSONObject manifest,
            JSONObject sourceMeta,
            JSONObject anchors,
            int messageCount,
            String messagesSha256,
            String htmlSha256,
            int imgCount,
            int audioCount,
            int videoCount,
            int docCount,
            int otherCount,
            JSONObject originBindingJson,
            List<AttachmentItem> attachments) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("CHAT_CAPTURA_IPED_LOG_V1\n"); //$NON-NLS-1$
        sb.append("captureFolder=").append(cleanLogValue(folderName)).append('\n'); //$NON-NLS-1$
        sb.append("captureFormat=").append(cleanLogValue(manifest.optString("captureFormat", "html-message-v1"))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("pluginVersion=").append(cleanLogValue(pluginVersion)).append('\n'); //$NON-NLS-1$
        sb.append("ipedVersion=").append(cleanLogValue(ipedVersion)).append('\n'); //$NON-NLS-1$
        sb.append("createdAt=").append(cleanLogValue(createdAt)).append('\n'); //$NON-NLS-1$
        sb.append("sourceName=").append(cleanLogValue(sourceMeta == null ? "" : sourceMeta.optString("name", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("sourceSize=").append(sourceMeta == null ? 0L : sourceMeta.optLong("size", 0L)).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("sourceType=").append(cleanLogValue(sourceMeta == null ? "" : sourceMeta.optString("type", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("sourceItemHash=").append(cleanLogValue(sourceMeta == null ? "" : sourceMeta.optString("itemHash", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("sourceItemSha256=").append(cleanLogValue(sourceMeta == null ? "" : sourceMeta.optString("itemSha256", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("sourceFingerprint=").append(cleanLogValue(manifest.optString("sourceFingerprint", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("conversationKey=").append(cleanLogValue(manifest.optString("conversationKey", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("anchorsStart=").append(cleanLogValue(anchors == null ? "" : anchors.optString("start", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("anchorsEnd=").append(cleanLogValue(anchors == null ? "" : anchors.optString("end", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sb.append("messageCount=").append(messageCount).append('\n'); //$NON-NLS-1$
        sb.append("counts=msg:").append(messageCount) //$NON-NLS-1$
                .append(",img:").append(imgCount) //$NON-NLS-1$
                .append(",audio:").append(audioCount) //$NON-NLS-1$
                .append(",video:").append(videoCount) //$NON-NLS-1$
                .append(",doc:").append(docCount) //$NON-NLS-1$
                .append(",outros:").append(otherCount).append('\n'); //$NON-NLS-1$
        sb.append("htmlSha256=").append(cleanLogValue(htmlSha256)).append('\n'); //$NON-NLS-1$
        sb.append("messagesSha256=").append(cleanLogValue(messagesSha256)).append('\n'); //$NON-NLS-1$
        sb.append("annotationsSha256=").append(cleanLogValue(manifest.optString("annotationsSha256", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("captureRootHash=").append(cleanLogValue(manifest.optString("captureRootHash", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        if (originBindingJson != null) {
            sb.append("originBindingPresent=true\n"); //$NON-NLS-1$
            sb.append("originBindingStatus=").append(cleanLogValue(originBindingJson.optString("status", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append("originBindingSha256=").append(cleanLogValue(manifest.optString("originBindingSha256", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append("collectionCode=").append(cleanLogValue(originBindingJson.optString("collectionCode", ""))).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            sb.append("originBindingPresent=false\n"); //$NON-NLS-1$
        }
        JSONObject censorship = manifest.optJSONObject("censorship"); //$NON-NLS-1$
        if (censorship != null) {
            sb.append("censorshipEnabled=true\n"); //$NON-NLS-1$
            sb.append("censorshipMatchedMessages=").append(censorship.optInt("censoredMessageCount", 0)).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            sb.append("censorshipEnabled=false\n"); //$NON-NLS-1$
        }
        sb.append("attachmentCount=").append(attachments.size()).append('\n'); //$NON-NLS-1$
        for (AttachmentItem att : attachments) {
            sb.append("ATTACHMENT|").append(cleanLogValue(att.relPath)) //$NON-NLS-1$
                    .append('|').append(att.size)
                    .append('|').append(cleanLogValue(att.mime))
                    .append('|').append(att.sha256)
                    .append('|').append(att.md5)
                    .append('|').append(att.sha1)
                    .append('\n');
        }
        return sb.toString();
    }

    private static String cleanLogValue(String s) {
        if (s == null) {
            return ""; //$NON-NLS-1$
        }
        return s.replace('\r', ' ').replace('\n', ' ').replace('\0', ' ').trim();
    }

    private static String buildFramesetHtml(String folderName) {
        return "<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html lang=\"pt-BR\">\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "<meta charset=\"utf-8\">\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + STRICT_CSP + "\">\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<title>Relat\u00f3rio de Captura - " + esc(folderName) + "</title>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "</head>\n" //$NON-NLS-1$
                + "<frameset cols=\"230,*\">\n" //$NON-NLS-1$
                + "<frame src=\"relatorio/contents.htm\" name=\"ContentsPage\">\n" //$NON-NLS-1$
                + "<frame src=\"relatorio/caseinformation.htm\" name=\"ReportPage\">\n" //$NON-NLS-1$
                + "<noframes>\n" //$NON-NLS-1$
                + "<body>\n" //$NON-NLS-1$
                + "<p>Este relat\u00f3rio utiliza frames. Acesse <a href=\"relatorio/caseinformation.htm\">Informa\u00e7\u00f5es da Captura</a> ou <a href=\"whatsapp.html\">Conversa Capturada</a>.</p>\n" //$NON-NLS-1$
                + "</body>\n" //$NON-NLS-1$
                + "</noframes>\n" //$NON-NLS-1$
                + "</frameset>\n" //$NON-NLS-1$
                + "</html>\n"; //$NON-NLS-1$
    }

    private static String buildContentsHtml(int messageCount, Map<String, List<AttachmentItem>> byCategory, boolean hasImages) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n") //$NON-NLS-1$
                .append("<meta charset=\"utf-8\">\n") //$NON-NLS-1$
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(STRICT_CSP).append("\">\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<title>Navega\u00e7\u00e3o</title>\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/contents.css\">\n") //$NON-NLS-1$
                .append("</head>\n<body>\n") //$NON-NLS-1$
                .append("<div class=\"brand-title\">Chat Captura IPED</div>\n") //$NON-NLS-1$
                .append("<div class=\"brand-sub\">Relat\u00f3rio da Captura</div>\n") //$NON-NLS-1$
                .append("<nav class=\"nav-menu\">\n") //$NON-NLS-1$
                .append("<div class=\"nav-group\">Principal</div>\n") //$NON-NLS-1$
                .append("<ul>\n") //$NON-NLS-1$
                .append("<li><a href=\"caseinformation.htm\" target=\"ReportPage\">Informa\u00e7\u00f5es</a></li>\n") //$NON-NLS-1$
                .append("<li><a href=\"../whatsapp.html\" target=\"ReportPage\">Conversa WhatsApp (").append(messageCount).append(" msgs)</a></li>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("</ul>\n") //$NON-NLS-1$
                .append("<div class=\"nav-group\">Categorias</div>\n") //$NON-NLS-1$
                .append("<ul>\n") //$NON-NLS-1$
                .append("<li><a href=\"cat_whatsapp.html\" target=\"ReportPage\">Conversas WhatsApp (1)</a></li>\n"); //$NON-NLS-1$
        for (Map.Entry<String, List<AttachmentItem>> e : byCategory.entrySet()) {
            if (!e.getValue().isEmpty()) {
                sb.append("<li><a href=\"cat_").append(e.getKey()).append(".html\" target=\"ReportPage\">") //$NON-NLS-1$ //$NON-NLS-2$
                        .append(esc(categoryDisplayTitle(e.getKey())))
                        .append(" (").append(e.getValue().size()).append(")</a></li>\n"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        sb.append("</ul>\n"); //$NON-NLS-1$
        if (hasImages) {
            sb.append("<div class=\"nav-group\">Visualiza\u00e7\u00e3o</div>\n<ul>\n") //$NON-NLS-1$
                    .append("<li><a href=\"thumbs_001.htm\" target=\"ReportPage\">Galeria de Imagens</a></li>\n") //$NON-NLS-1$
                    .append("</ul>\n"); //$NON-NLS-1$
        }
        sb.append("<div class=\"nav-group\">Auditoria</div>\n<ul>\n") //$NON-NLS-1$
                .append("<li><a href=\"integridade_resumo.htm\" target=\"ReportPage\">Resumo de Integridade</a></li>\n") //$NON-NLS-1$
                .append("<li><a href=\"ajuda.htm\" target=\"ReportPage\">Ajuda</a></li>\n") //$NON-NLS-1$
                .append("</ul>\n</nav>\n</body>\n</html>\n"); //$NON-NLS-1$
        return sb.toString();
    }

    private static String buildCaseInformationHtml(
            String folderName,
            String createdAt,
            String pluginVersion,
            String ipedVersion,
            JSONObject manifest,
            JSONObject sourceMeta,
            JSONObject anchors,
            int messageCount,
            int imgCount,
            int audioCount,
            int videoCount,
            int docCount,
            int otherCount,
            JSONObject originBindingJson,
            CustodyCaseRecord rec) {
        String orgHeader = rec == null ? "" : nonNull(rec.organizationHeader).trim(); //$NON-NLS-1$
        String reportNum = rec == null ? "" : nonNull(rec.reportNumber).trim(); //$NON-NLS-1$
        String reportDate = rec == null ? "" : nonNull(rec.reportDate).trim(); //$NON-NLS-1$
        String reportTitle = rec == null ? "" : nonNull(rec.reportTitle).trim(); //$NON-NLS-1$
        if (reportTitle.isEmpty()) {
            reportTitle = "Captura de Conversa - " + folderName; //$NON-NLS-1$
        }
        String signatories = rec == null ? "" : nonNull(rec.signatories).trim(); //$NON-NLS-1$
        if (signatories.isEmpty() && rec != null) {
            signatories = nonNull(rec.analyzedBy).trim();
        }
        String caseNumber = rec == null ? "" : nonNull(rec.caseNumber).trim(); //$NON-NLS-1$
        String legalAuth = rec == null ? "" : nonNull(rec.legalAuthorization).trim(); //$NON-NLS-1$
        String requestRef = rec == null ? "" : nonNull(rec.requestReference).trim(); //$NON-NLS-1$
        String protoNum = rec == null ? "" : nonNull(rec.forensicProtocolNumber).trim(); //$NON-NLS-1$
        String protoDate = rec == null ? "" : nonNull(rec.forensicProtocolDate).trim(); //$NON-NLS-1$
        String examinedMaterial = rec == null ? "" : nonNull(rec.examinedMaterial).trim(); //$NON-NLS-1$
        if (examinedMaterial.isEmpty()) {
            String srcName = sourceMeta == null ? "" : sourceMeta.optString("name", ""); //$NON-NLS-1$ //$NON-NLS-2$
            examinedMaterial = srcName.isEmpty() ? folderName : srcName;
        }

        StringBuilder sb = new StringBuilder(6144);
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n") //$NON-NLS-1$
                .append("<meta charset=\"utf-8\">\n") //$NON-NLS-1$
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(STRICT_CSP).append("\">\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<title>Informa\u00e7\u00f5es da Captura</title>\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/info.css\">\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/estilo.css\">\n") //$NON-NLS-1$
                .append("</head>\n<body>\n<div class=\"page-wrap\">\n"); //$NON-NLS-1$

        sb.append("<header class=\"report-header\">\n"); //$NON-NLS-1$
        if (!orgHeader.isEmpty()) {
            sb.append("<div class=\"org-name\">").append(esc(orgHeader)).append("</div>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("<h1>Relat\u00f3rio T\u00e9cnico da Captura</h1>\n") //$NON-NLS-1$
                .append("<div class=\"subtitle\">Chat Captura IPED ").append(esc(pluginVersion)) //$NON-NLS-1$
                .append(" | IPED ").append(esc(ipedVersion)).append("</div>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("</header>\n"); //$NON-NLS-1$

        sb.append("<table class=\"info\">\n") //$NON-NLS-1$
                .append("<tr><th class=\"key\">Laudo</th><td><b>").append(esc(dashIfEmpty(reportNum))) //$NON-NLS-1$
                .append("</b> de ").append(esc(dashIfEmpty(reportDate))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">T\u00edtulo</th><td>").append(esc(dashIfEmpty(reportTitle))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Signat\u00e1rio(s)</th><td>").append(esc(dashIfEmpty(signatories))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Refer\u00eancias</th><td><b>").append(esc(dashIfEmpty(caseNumber))).append("</b><br>") //$NON-NLS-1$ //$NON-NLS-2$
                .append(esc(dashIfEmpty(legalAuth))).append("<br>") //$NON-NLS-1$
                .append("Solicita\u00e7\u00e3o ").append(esc(dashIfEmpty(requestRef))).append("<br>") //$NON-NLS-1$ //$NON-NLS-2$
                .append("Protocolo Criminal\u00edstica n\u00ba ").append(esc(dashIfEmpty(protoNum))) //$NON-NLS-1$
                .append(" de ").append(esc(dashIfEmpty(protoDate))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Material examinado</th><td>").append(esc(dashIfEmpty(examinedMaterial))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("</table>\n"); //$NON-NLS-1$

        sb.append("<h2>Dados da Captura</h2>\n<table class=\"info\">\n") //$NON-NLS-1$
                .append("<tr><th class=\"key\">Pasta da Captura</th><td><code>").append(esc(folderName)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Formato</th><td><code>").append(esc(manifest.optString("captureFormat", "html-message-v1"))).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append("<tr><th class=\"key\">Data/Hora (UTC)</th><td>").append(esc(dashIfEmpty(createdAt))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Item de Origem</th><td>").append(esc(dashIfEmpty(sourceMeta == null ? "" : sourceMeta.optString("name", "")))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .append("<tr><th class=\"key\">Hash do Item de Origem</th><td><code>") //$NON-NLS-1$
                .append(esc(dashIfEmpty(sourceMeta == null ? "" : nonBlank(sourceMeta.optString("itemSha256", ""), sourceMeta.optString("itemHash", ""))))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append("</code></td></tr>\n") //$NON-NLS-1$
                .append("<tr><th class=\"key\">\u00c2ncoras</th><td><code>") //$NON-NLS-1$
                .append(esc(anchors == null ? "-" : (anchors.optString("start", "-") + " .. " + anchors.optString("end", "-")))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                .append("</code></td></tr>\n") //$NON-NLS-1$
                .append("<tr><th class=\"key\">Totais Capturados</th><td>Mensagens: <b>").append(messageCount) //$NON-NLS-1$
                .append("</b> | Imagens: <b>").append(imgCount) //$NON-NLS-1$
                .append("</b> | \u00c1udios: <b>").append(audioCount) //$NON-NLS-1$
                .append("</b> | V\u00eddeos: <b>").append(videoCount) //$NON-NLS-1$
                .append("</b> | Documentos: <b>").append(docCount) //$NON-NLS-1$
                .append("</b> | Outros: <b>").append(otherCount).append("</b></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">SHA-256 whatsapp.html</th><td><code>").append(esc(manifest.optString("htmlSha256", "-"))).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append("<tr><th class=\"key\">captureRootHash</th><td><code>").append(esc(manifest.optString("captureRootHash", "-"))).append("</code></td></tr>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (originBindingJson != null) {
            sb.append("<tr><th class=\"key\">V\u00ednculo de Origem</th><td>") //$NON-NLS-1$
                    .append(esc(dashIfEmpty(originBindingJson.optString("status", "")))) //$NON-NLS-1$ //$NON-NLS-2$
                    .append(" | Collection Code: <code>").append(esc(dashIfEmpty(originBindingJson.optString("collectionCode", "")))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("</code></td></tr>\n"); //$NON-NLS-1$
        }
        sb.append("</table>\n"); //$NON-NLS-1$

        if (rec != null) {
            sb.append("<h2>Cadeia de Cust\u00f3dia e Dispositivo</h2>\n<table class=\"info\">\n") //$NON-NLS-1$
                    .append("<tr><th class=\"key\">Dispositivo</th><td>").append(esc(dashIfEmpty(combineNonEmpty(" ", rec.deviceManufacturer, rec.deviceModel)))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("<tr><th class=\"key\">IMEI / N\u00ba de S\u00e9rie</th><td>").append(esc(dashIfEmpty(combineNonEmpty(" / ", rec.deviceImei, rec.deviceSerialNumber)))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("<tr><th class=\"key\">SO / WhatsApp</th><td>").append(esc(dashIfEmpty(combineNonEmpty(" / ", rec.deviceOsVersion, rec.whatsappVersion)))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("<tr><th class=\"key\">Quem coletou</th><td>").append(esc(dashIfEmpty(rec.collectedBy))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<tr><th class=\"key\">Quem analisou</th><td>").append(esc(dashIfEmpty(rec.analyzedBy))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<tr><th class=\"key\">Quando (Coleta / An\u00e1lise)</th><td>").append(esc(dashIfEmpty(combineNonEmpty(" | ", rec.collectedAt, rec.analyzedAt)))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("<tr><th class=\"key\">Onde</th><td>").append(esc(dashIfEmpty(rec.custodyLocation))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<tr><th class=\"key\">Armazenamento</th><td>").append(esc(dashIfEmpty(rec.formattedStorageDescription()))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("</table>\n"); //$NON-NLS-1$
        }

        sb.append("<h2>Artefatos da Captura</h2>\n<ul class=\"artifact-links\">\n") //$NON-NLS-1$
                .append("<li><a href=\"../whatsapp.html\">whatsapp.html</a> — Conversa capturada renderizada offline</li>\n") //$NON-NLS-1$
                .append("<li><a href=\"../Lista%20de%20Arquivos.csv\">Lista de Arquivos.csv</a> — Invent\u00e1rio tabular (MD5, SHA-1, SHA-256)</li>\n") //$NON-NLS-1$
                .append("<li><a href=\"../chat-captura-IPED.log\">chat-captura-IPED.log</a> — Log t\u00e9cnico determin\u00edstico da captura</li>\n") //$NON-NLS-1$
                .append("<li><a href=\"../manifest.json\">manifest.json</a> — Manifesto estruturado e hashes das mensagens</li>\n") //$NON-NLS-1$
                .append("<li><a href=\"../integridade.json\">integridade.json</a> — Cadeia local de integridade SHA-256</li>\n"); //$NON-NLS-1$
        if (originBindingJson != null) {
            sb.append("<li><a href=\"../origin-binding.json\">origin-binding.json</a> — V\u00ednculo criptogr\u00e1fico com a origem</li>\n"); //$NON-NLS-1$
        }
        sb.append("</ul>\n</div>\n</body>\n</html>\n"); //$NON-NLS-1$
        return sb.toString();
    }

    private static String buildWhatsappCategoryHtml(
            String folderName,
            String createdAt,
            JSONObject anchors,
            int messageCount,
            long htmlSize,
            MultiDigest htmlDigest) {
        String range = anchors == null ? "-" : (anchors.optString("start", "-") + " .. " + anchors.optString("end", "-")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        return "<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n" //$NON-NLS-1$
                + "<meta charset=\"utf-8\">\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + STRICT_CSP + "\">\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<title>Categoria: Conversas WhatsApp</title>\n" //$NON-NLS-1$
                + "<link rel=\"stylesheet\" href=\"res/estilo.css\">\n" //$NON-NLS-1$
                + "<link rel=\"stylesheet\" href=\"res/main.css\">\n" //$NON-NLS-1$
                + "</head>\n<body>\n<div class=\"page-wrap\">\n" //$NON-NLS-1$
                + "<h1>Categoria: Conversas WhatsApp</h1>\n" //$NON-NLS-1$
                + "<table class=\"data-table\">\n" //$NON-NLS-1$
                + "<thead><tr><th>Arquivo</th><th>Pasta</th><th>Mensagens</th><th>\u00c2ncoras</th><th>Tamanho</th><th>Data (UTC)</th><th>SHA-256</th></tr></thead>\n" //$NON-NLS-1$
                + "<tbody><tr><td><a href=\"../whatsapp.html\">whatsapp.html</a></td><td>" + esc(folderName) //$NON-NLS-1$
                + "</td><td>" + messageCount + "</td><td><code>" + esc(range) + "</code></td><td>" + htmlSize //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + " bytes</td><td>" + esc(dashIfEmpty(createdAt)) + "</td><td><code>" + esc(htmlDigest.sha256) //$NON-NLS-1$ //$NON-NLS-2$
                + "</code></td></tr></tbody>\n" //$NON-NLS-1$
                + "</table>\n</div>\n</body>\n</html>\n"; //$NON-NLS-1$
    }

    private static String buildAttachmentCategoryHtml(String title, List<AttachmentItem> items) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n") //$NON-NLS-1$
                .append("<meta charset=\"utf-8\">\n") //$NON-NLS-1$
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(STRICT_CSP).append("\">\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<title>Categoria: ").append(esc(title)).append("</title>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<link rel=\"stylesheet\" href=\"res/estilo.css\">\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/main.css\">\n") //$NON-NLS-1$
                .append("</head>\n<body>\n<div class=\"page-wrap\">\n") //$NON-NLS-1$
                .append("<h1>Categoria: ").append(esc(title)).append(" (").append(items.size()).append(")</h1>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append("<table class=\"data-table\">\n") //$NON-NLS-1$
                .append("<thead><tr><th>Arquivo</th><th>Tamanho</th><th>MIME</th><th>Mensagem</th><th>SHA-256</th><th>MD5</th></tr></thead>\n<tbody>\n"); //$NON-NLS-1$
        for (AttachmentItem att : items) {
            sb.append("<tr><td><a href=\"../").append(esc(att.relPath)).append("\">").append(esc(att.fileName)).append("</a></td>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("<td>").append(att.size).append(" bytes</td>") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<td><code>").append(esc(att.mime)).append("</code></td>") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<td>").append(esc(dashIfEmpty(att.messageRef))).append("</td>") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<td><code>").append(esc(att.sha256)).append("</code></td>") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<td><code>").append(esc(att.md5)).append("</code></td></tr>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("</tbody>\n</table>\n</div>\n</body>\n</html>\n"); //$NON-NLS-1$
        return sb.toString();
    }

    private static String buildThumbsHtml(List<AttachmentItem> images) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n") //$NON-NLS-1$
                .append("<meta charset=\"utf-8\">\n") //$NON-NLS-1$
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(STRICT_CSP).append("\">\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<title>Galeria de Imagens</title>\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/estilo.css\">\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/main.css\">\n") //$NON-NLS-1$
                .append("</head>\n<body>\n<div class=\"page-wrap\">\n") //$NON-NLS-1$
                .append("<h1>Galeria de Imagens (").append(images.size()).append(")</h1>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        if (images.size() > MAX_THUMBS) {
            sb.append("<p class=\"note\">Exibindo as primeiras ").append(MAX_THUMBS) //$NON-NLS-1$
                    .append(" imagens. Consulte a categoria Imagens ou Lista de Arquivos.csv para a listagem completa.</p>\n"); //$NON-NLS-1$
        }
        sb.append("<div class=\"thumb-grid\">\n"); //$NON-NLS-1$
        int limit = Math.min(images.size(), MAX_THUMBS);
        for (int i = 0; i < limit; i++) {
            AttachmentItem img = images.get(i);
            String shortSha = img.sha256.length() > 12 ? img.sha256.substring(0, 12) : img.sha256;
            sb.append("<div class=\"thumb-card\">") //$NON-NLS-1$
                    .append("<a href=\"../").append(esc(img.relPath)).append("\">") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("<img src=\"../").append(esc(img.relPath)).append("\" alt=\"").append(esc(img.fileName)).append("\">") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .append("</a>") //$NON-NLS-1$
                    .append("<div class=\"thumb-meta\"><code>").append(esc(shortSha)).append("</code> | ") //$NON-NLS-1$ //$NON-NLS-2$
                    .append(img.size).append(" B<br>").append(esc(dashIfEmpty(img.messageRef))).append("</div>") //$NON-NLS-1$ //$NON-NLS-2$
                    .append("</div>\n"); //$NON-NLS-1$
        }
        sb.append("</div>\n</div>\n</body>\n</html>\n"); //$NON-NLS-1$
        return sb.toString();
    }

    private static String buildIntegritySummaryHtml(
            String folderName,
            String createdAt,
            JSONObject manifest,
            String messagesSha256,
            MultiDigest htmlDigest,
            JSONObject originBindingJson,
            int attachmentCount) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n") //$NON-NLS-1$
                .append("<meta charset=\"utf-8\">\n") //$NON-NLS-1$
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(STRICT_CSP).append("\">\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<title>Resumo de Integridade</title>\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/info.css\">\n") //$NON-NLS-1$
                .append("<link rel=\"stylesheet\" href=\"res/estilo.css\">\n") //$NON-NLS-1$
                .append("</head>\n<body>\n<div class=\"page-wrap\">\n") //$NON-NLS-1$
                .append("<h1>Resumo de Integridade da Captura</h1>\n") //$NON-NLS-1$
                .append("<table class=\"info\">\n") //$NON-NLS-1$
                .append("<tr><th class=\"key\">Pasta</th><td><code>").append(esc(folderName)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Data/Hora (UTC)</th><td>").append(esc(dashIfEmpty(createdAt))).append("</td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">captureRootHash</th><td><code>").append(esc(manifest.optString("captureRootHash", "-"))).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append("<tr><th class=\"key\">whatsapp.html (SHA-256)</th><td><code>").append(esc(htmlDigest.sha256)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">whatsapp.html (MD5)</th><td><code>").append(esc(htmlDigest.md5)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">whatsapp.html (SHA-1)</th><td><code>").append(esc(htmlDigest.sha1)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">messages.json (SHA-256)</th><td><code>").append(esc(messagesSha256)).append("</code></td></tr>\n") //$NON-NLS-1$ //$NON-NLS-2$
                .append("<tr><th class=\"key\">Anexos Verificados</th><td>").append(attachmentCount).append("</td></tr>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        if (originBindingJson != null) {
            sb.append("<tr><th class=\"key\">origin-binding.json (SHA-256)</th><td><code>") //$NON-NLS-1$
                    .append(esc(manifest.optString("originBindingSha256", "-"))).append("</code></td></tr>\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("</table>\n") //$NON-NLS-1$
                .append("<p class=\"note\">Todos os artefatos deste relat\u00f3rio, incluindo Lista de Arquivos.csv e chat-captura-IPED.log, est\u00e3o selados por SHA-256 em integridade.json e validados pelo Verificador de Integridade.</p>\n") //$NON-NLS-1$
                .append("</div>\n</body>\n</html>\n"); //$NON-NLS-1$
        return sb.toString();
    }

    private static String buildHelpHtml(String pluginVersion, String ipedVersion) {
        return "<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n" //$NON-NLS-1$
                + "<meta charset=\"utf-8\">\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + STRICT_CSP + "\">\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<title>Ajuda - Relat\u00f3rio de Captura</title>\n" //$NON-NLS-1$
                + "<link rel=\"stylesheet\" href=\"res/estilo.css\">\n" //$NON-NLS-1$
                + "<link rel=\"stylesheet\" href=\"res/main.css\">\n" //$NON-NLS-1$
                + "</head>\n<body>\n<div class=\"page-wrap\">\n" //$NON-NLS-1$
                + "<h1>Ajuda do Relat\u00f3rio Offline</h1>\n" //$NON-NLS-1$
                + "<p>Este pacote foi gerado pelo plugin <b>Chat Captura IPED " + esc(pluginVersion) //$NON-NLS-1$
                + "</b> (compat\u00edvel com IPED " + esc(ipedVersion) + ") para consulta offline e verifica\u00e7\u00e3o criptogr\u00e1fica.</p>\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "<h2>Estrutura da Pasta de Captura</h2>\n" //$NON-NLS-1$
                + "<ul>\n" //$NON-NLS-1$
                + "<li><code>relatorio.htm</code>: entrada principal com navega\u00e7\u00e3o por categorias e informa\u00e7\u00f5es do caso.</li>\n" //$NON-NLS-1$
                + "<li><code>whatsapp.html</code>: visualiza\u00e7\u00e3o offline das mensagens e m\u00eddias capturadas.</li>\n" //$NON-NLS-1$
                + "<li><code>Lista de Arquivos.csv</code>: invent\u00e1rio tabular com MD5, SHA-1 e SHA-256 de cada item.</li>\n" //$NON-NLS-1$
                + "<li><code>chat-captura-IPED.log</code>: log t\u00e9cnico determin\u00edstico da captura (sem texto de mensagens).</li>\n" //$NON-NLS-1$
                + "<li><code>manifest.json</code> e <code>integridade.json</code>: metadados, hashes individuais e cadeia de integridade.</li>\n" //$NON-NLS-1$
                + "<li><code>attachments/</code>: c\u00f3pias das m\u00eddias indexadas por SHA-256.</li>\n" //$NON-NLS-1$
                + "</ul>\n" //$NON-NLS-1$
                + "<h2>Verifica\u00e7\u00e3o de Integridade</h2>\n" //$NON-NLS-1$
                + "<p>Execute o <b>Verificador de Integridade</b> (<code>integridade-verifier.exe</code> ou <code>integridade-verifier.jar</code>) na pasta raiz das capturas para auditar integridade local, integridade p\u00fablica, log cronol\u00f3gico em <code>laudo/Log.txt</code> e v\u00ednculo de origem.</p>\n" //$NON-NLS-1$
                + "</div>\n</body>\n</html>\n"; //$NON-NLS-1$
    }

    private static String buildEstiloCss() {
        return "body{font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#1f2d38;background:#f7f9fb;margin:0;padding:0;}\n" //$NON-NLS-1$
                + ".page-wrap{max-width:1180px;margin:0 auto;padding:20px 24px;background:#ffffff;min-height:100vh;box-sizing:border-box;}\n" //$NON-NLS-1$
                + "h1{font-size:20px;color:#14304a;margin:0 0 12px 0;border-bottom:2px solid #14304a;padding-bottom:6px;}\n" //$NON-NLS-1$
                + "h2{font-size:15px;color:#1d4568;margin:20px 0 8px 0;}\n" //$NON-NLS-1$
                + "a{color:#0f4c81;text-decoration:none;}\n" //$NON-NLS-1$
                + "a:hover{text-decoration:underline;}\n" //$NON-NLS-1$
                + "code{font-family:Consolas,monospace;font-size:12px;background:#f0f4f8;padding:1px 4px;border-radius:3px;word-break:break-all;}\n" //$NON-NLS-1$
                + ".note{background:#eef5fb;border-left:4px solid #1d4568;padding:10px 12px;margin-top:14px;color:#1f2d38;}\n"; //$NON-NLS-1$
    }

    private static String buildInfoCss() {
        return ".report-header{margin-bottom:16px;padding-bottom:10px;border-bottom:2px solid #14304a;}\n" //$NON-NLS-1$
                + ".org-name{font-size:13px;font-weight:bold;text-transform:uppercase;letter-spacing:0.4px;color:#334e68;margin-bottom:4px;}\n" //$NON-NLS-1$
                + ".subtitle{font-size:12px;color:#486581;}\n" //$NON-NLS-1$
                + "table.info{width:100%;border-collapse:collapse;margin-bottom:14px;}\n" //$NON-NLS-1$
                + "table.info th.key{width:210px;text-align:left;vertical-align:top;background:#eef3f8;color:#102a43;padding:7px 10px;border:1px solid #cbd5e1;font-weight:bold;}\n" //$NON-NLS-1$
                + "table.info td{padding:7px 10px;border:1px solid #cbd5e1;vertical-align:top;word-break:break-word;}\n" //$NON-NLS-1$
                + ".artifact-links{margin:6px 0 0 18px;padding:0;line-height:1.7;}\n"; //$NON-NLS-1$
    }

    private static String buildContentsCss() {
        return "body{font-family:Arial,Helvetica,sans-serif;font-size:13px;margin:0;padding:12px;background:#162b40;color:#e6eef6;}\n" //$NON-NLS-1$
                + ".brand-title{font-size:15px;font-weight:bold;color:#ffffff;margin-bottom:2px;}\n" //$NON-NLS-1$
                + ".brand-sub{font-size:11px;color:#b8cbe0;margin-bottom:14px;padding-bottom:10px;border-bottom:1px solid #2d4a67;}\n" //$NON-NLS-1$
                + ".nav-group{font-size:11px;font-weight:bold;text-transform:uppercase;color:#9fb6ce;margin:12px 0 5px 0;letter-spacing:0.4px;}\n" //$NON-NLS-1$
                + ".nav-menu ul{list-style:none;margin:0;padding:0;}\n" //$NON-NLS-1$
                + ".nav-menu li{margin:2px 0;}\n" //$NON-NLS-1$
                + ".nav-menu a{display:block;padding:5px 7px;border-radius:4px;color:#e6eef6;text-decoration:none;}\n" //$NON-NLS-1$
                + ".nav-menu a:hover{background:#244361;color:#ffffff;}\n"; //$NON-NLS-1$
    }

    private static String buildMainCss() {
        return "table.data-table{width:100%;border-collapse:collapse;margin-top:10px;}\n" //$NON-NLS-1$
                + "table.data-table th{background:#1d4568;color:#ffffff;text-align:left;padding:7px 8px;border:1px solid #163652;font-size:12px;}\n" //$NON-NLS-1$
                + "table.data-table td{padding:6px 8px;border:1px solid #cbd5e1;vertical-align:top;font-size:12px;word-break:break-word;}\n" //$NON-NLS-1$
                + "table.data-table tbody tr:nth-child(even){background:#f8fafc;}\n" //$NON-NLS-1$
                + ".thumb-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px;margin-top:12px;}\n" //$NON-NLS-1$
                + ".thumb-card{border:1px solid #cbd5e1;border-radius:4px;padding:8px;background:#f8fafc;text-align:center;}\n" //$NON-NLS-1$
                + ".thumb-card img{max-width:100%;max-height:140px;object-fit:contain;display:block;margin:0 auto 6px auto;}\n" //$NON-NLS-1$
                + ".thumb-meta{font-size:11px;color:#334e68;word-break:break-word;}\n"; //$NON-NLS-1$
    }

    private static String esc(String raw) {
        if (raw == null || raw.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break; //$NON-NLS-1$
                case '<': sb.append("&lt;"); break; //$NON-NLS-1$
                case '>': sb.append("&gt;"); break; //$NON-NLS-1$
                case '"': sb.append("&quot;"); break; //$NON-NLS-1$
                case '\'': sb.append("&#39;"); break; //$NON-NLS-1$
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s; //$NON-NLS-1$
    }

    private static String nonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim(); //$NON-NLS-1$
    }

    private static String dashIfEmpty(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim(); //$NON-NLS-1$
    }

    private static String combineNonEmpty(String sep, String a, String b) {
        String sa = a == null ? "" : a.trim(); //$NON-NLS-1$
        String sb = b == null ? "" : b.trim(); //$NON-NLS-1$
        if (sa.isEmpty()) {
            return sb;
        }
        if (sb.isEmpty()) {
            return sa;
        }
        return sa + sep + sb;
    }
}
