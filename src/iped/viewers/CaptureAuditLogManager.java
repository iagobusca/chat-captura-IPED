/*
 * Chat Captura IPED - Log Encadeado de Auditoria de Capturas (Start / Re-Extrair / Adicionado / Deletado)
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Maintains the tamper-evident chained audit log under {@code <outputRoot>/laudo/registro-capturas.jsonl}
 * (plus human-readable {@code registro-capturas.log}). Tracks:
 * <ul>
 *   <li>{@code ADICIONADO}: new capture folder appearing under outputRoot</li>
 *   <li>{@code DELETADO}: previously known capture folder removed from outputRoot</li>
 *   <li>{@code START}: new capture completion with categorized file integrity counts</li>
 *   <li>{@code RE-EXTRAIR}: unchanged ({@code INALTERADO_OK}), repaired/updated, or error per capture</li>
 * </ul>
 */
final class CaptureAuditLogManager {

    private static final int MAX_LOG_ENTRIES_READ = 10_000;
    private static final int MAX_CHAINED_LOG_TXT_LINES = 100_000;
    static final String ZERO_HASH_64 = "0000000000000000000000000000000000000000000000000000000000000000"; //$NON-NLS-1$

    interface LogRecoveryPrompt {
        /**
         * Alerts the user about a missing or tampered {@code laudo/Log.txt} and asks whether
         * to recreate it (recording the incident inside {@code report-plugin.pdf}).
         */
        boolean confirmRecreateLog(String issueType, String details);
    }

    static volatile LogRecoveryPrompt defaultRecoveryPrompt = null;

    static final class ChainedLogSnapshot {
        boolean exists;
        boolean chainValid;
        String status = "NAO_INICIADO"; //$NON-NLS-1$
        String errorDetail = ""; //$NON-NLS-1$
        String createdAtLocal = ""; //$NON-NLS-1$
        String createdAtUtc = ""; //$NON-NLS-1$
        int lastLineIndex = -1;
        int lineCount = 0;
        long byteLength = 0L;
        String prefixSha256 = ""; //$NON-NLS-1$
        String headHash = ""; //$NON-NLS-1$
        final List<String> lines = new ArrayList<>();
    }

    private CaptureAuditLogManager() {
    }

    static final class AuditLogEntry {
        int sequence;
        String timestampLocal = ""; //$NON-NLS-1$
        String timestampUtc = ""; //$NON-NLS-1$
        String eventType = ""; //$NON-NLS-1$
        String captureFolder = ""; //$NON-NLS-1$
        String status = ""; //$NON-NLS-1$
        String summary = ""; //$NON-NLS-1$
        String folderHash = ""; //$NON-NLS-1$
        String previousFolderHash = ""; //$NON-NLS-1$
        CustodyCaseRecord.MediaCounts counts = new CustodyCaseRecord.MediaCounts();
        String previousRecordHash = ""; //$NON-NLS-1$
        String recordHash = ""; //$NON-NLS-1$

        JSONObject toJsonWithoutRecordHash() {
            return new JSONObject()
                    .put("sequence", sequence) //$NON-NLS-1$
                    .put("timestampLocal", timestampLocal) //$NON-NLS-1$
                    .put("timestampUtc", timestampUtc) //$NON-NLS-1$
                    .put("eventType", eventType) //$NON-NLS-1$
                    .put("captureFolder", captureFolder) //$NON-NLS-1$
                    .put("status", status) //$NON-NLS-1$
                    .put("summary", summary) //$NON-NLS-1$
                    .put("folderHash", folderHash) //$NON-NLS-1$
                    .put("previousFolderHash", previousFolderHash) //$NON-NLS-1$
                    .put("counts", counts.toJson()) //$NON-NLS-1$
                    .put("previousRecordHash", previousRecordHash) //$NON-NLS-1$
                    .put("recordHashFormat", "canonical-v1"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JSONObject toJson() {
            return toJsonWithoutRecordHash().put("recordHash", recordHash); //$NON-NLS-1$
        }

        String toReadableLine() {
            StringBuilder sb = new StringBuilder();
            sb.append('#').append(sequence).append(" | ") //$NON-NLS-1$
                    .append(eventType).append(" | ") //$NON-NLS-1$
                    .append(captureFolder).append(" | ") //$NON-NLS-1$
                    .append(timestampLocal).append(" | ") //$NON-NLS-1$
                    .append(status);
            if (!summary.isEmpty()) {
                sb.append(" | ").append(summary); //$NON-NLS-1$
            }
            if (!folderHash.isEmpty()) {
                sb.append(" | hash=").append(folderHash); //$NON-NLS-1$
            } else if (!previousFolderHash.isEmpty()) {
                sb.append(" | ultimo_hash=").append(previousFolderHash); //$NON-NLS-1$
            }
            return sb.toString();
        }

        static AuditLogEntry fromJson(JSONObject o) {
            AuditLogEntry e = new AuditLogEntry();
            if (o == null) {
                return e;
            }
            e.sequence = o.optInt("sequence", 0); //$NON-NLS-1$
            e.timestampLocal = o.optString("timestampLocal", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.timestampUtc = o.optString("timestampUtc", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.eventType = o.optString("eventType", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.captureFolder = o.optString("captureFolder", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.status = o.optString("status", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.summary = o.optString("summary", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.folderHash = o.optString("folderHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.previousFolderHash = o.optString("previousFolderHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.counts = CustodyCaseRecord.MediaCounts.fromJson(o.optJSONObject("counts")); //$NON-NLS-1$
            e.previousRecordHash = o.optString("previousRecordHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            e.recordHash = o.optString("recordHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            return e;
        }
    }

    static final class ReconcileSummary {
        final Map<String, CustodyCaseRecord.CaptureSnapshot> currentCaptures = new LinkedHashMap<>();
        final List<String> addedFolders = new ArrayList<>();
        final List<String> deletedFolders = new ArrayList<>();
    }

    static boolean isIgnoredRootAuxFolder(Path path) {
        if (path == null || path.getFileName() == null) {
            return true;
        }
        String name = path.getFileName().toString();
        if (name.isEmpty()) {
            return true;
        }
        return CustodyCaseRecord.LAUDO_DIR_NAME.equalsIgnoreCase(name)
                || "integridade".equalsIgnoreCase(name) //$NON-NLS-1$
                || "verificador-integridade".equalsIgnoreCase(name) //$NON-NLS-1$
                || "vinculado".equalsIgnoreCase(name) //$NON-NLS-1$
                || name.startsWith("_backup_chat_capture_") //$NON-NLS-1$
                || HtmlLinkViewer.isRecaptureAuxiliaryFolder(path);
    }

    static boolean isCaptureDirectory(Path dir) {
        if (dir == null || !CaptureIntegrity.isSafeDirectory(dir) || isIgnoredRootAuxFolder(dir)) {
            return false;
        }
        return CaptureIntegrity.isSafeRegularFile(dir.resolve("manifest.json")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(dir.resolve("whatsapp.html")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(dir.resolve("relatorio.htm")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(dir.resolve("texto.txt")); //$NON-NLS-1$
    }

    static Map<String, CustodyCaseRecord.CaptureSnapshot> scanCurrentCaptures(Path outputRoot) throws IOException {
        Map<String, CustodyCaseRecord.CaptureSnapshot> result = new LinkedHashMap<>();
        if (outputRoot == null || !CaptureIntegrity.isSafeDirectory(outputRoot)) {
            return result;
        }
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.list(outputRoot).limit(50_000L)) {
            s.filter(CaptureAuditLogManager::isCaptureDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(dirs::add);
        }
        for (Path dir : dirs) {
            CustodyCaseRecord.CaptureSnapshot snap = inspectCaptureFolder(outputRoot, dir);
            if (snap != null && !snap.folder.isEmpty()) {
                result.put(snap.folder, snap);
            }
        }
        return result;
    }

    static CustodyCaseRecord.CaptureSnapshot inspectCaptureFolder(Path outputRoot, Path captureDir) throws IOException {
        if (captureDir == null || !CaptureIntegrity.isSafeDirectory(captureDir)) {
            return null;
        }
        CustodyCaseRecord.CaptureSnapshot snap = new CustodyCaseRecord.CaptureSnapshot();
        snap.folder = captureDir.getFileName() == null ? "" : captureDir.getFileName().toString(); //$NON-NLS-1$
        snap.lastSeenAt = CustodyCaseRecord.nowLocalTimestamp();

        Path manifestPath = captureDir.resolve("manifest.json"); //$NON-NLS-1$
        JSONObject manifest = null;
        if (CaptureIntegrity.isSafeRegularFile(manifestPath)) {
            snap.manifestSha256 = CaptureIntegrity.sha256(manifestPath);
            try {
                manifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(manifestPath));
            } catch (Exception ignored) {
            }
        }

        Path integrityPath = captureDir.resolve("integridade.json"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(integrityPath)) {
            snap.integritySha256 = CaptureIntegrity.sha256(integrityPath);
            try {
                JSONObject integrity = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(integrityPath));
                snap.contentRootHash = integrity.optString("contentRootHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (Exception ignored) {
            }
        }

        Path htmlPath = captureDir.resolve("whatsapp.html"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(htmlPath)) {
            snap.htmlSha256 = CaptureIntegrity.sha256(htmlPath);
        }

        Path messagesPath = captureDir.resolve("messages.json"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(messagesPath)) {
            snap.messagesSha256 = CaptureIntegrity.sha256(messagesPath);
        }

        Path originBindingPath = captureDir.resolve(OriginCustodyBinder.ORIGIN_BINDING_FILE);
        if (CaptureIntegrity.isSafeRegularFile(originBindingPath)) {
            snap.originBindingSha256 = CaptureIntegrity.sha256(originBindingPath);
            try {
                JSONObject ob = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(originBindingPath));
                JSONObject db = ob.optJSONObject("sourceDatabase"); //$NON-NLS-1$
                if (db != null) {
                    snap.sourceDbPath = db.optString("relativePath", ""); //$NON-NLS-1$ //$NON-NLS-2$
                    snap.sourceDbSha256 = db.optString("sha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
                }
                JSONObject n3 = ob.optJSONObject("n3SqliteAudit"); //$NON-NLS-1$
                if (n3 != null) {
                    snap.n3Status = n3.optString("status", ""); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } catch (Exception ignored) {
            }
        }

        if (manifest != null) {
            snap.format = manifest.optString("captureFormat", "legacy-screenshot"); //$NON-NLS-1$ //$NON-NLS-2$
            snap.status = manifest.optString("status", "unknown"); //$NON-NLS-1$ //$NON-NLS-2$
            snap.createdAt = manifest.optString("createdAt", manifest.optString("startedAt", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            snap.captureRootHash = manifest.optString("captureRootHash", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (snap.htmlSha256.isEmpty()) {
                snap.htmlSha256 = manifest.optString("htmlSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            JSONObject src = manifest.optJSONObject("sourceMetadata"); //$NON-NLS-1$
            if (src != null) {
                snap.sourceItemName = src.optString("name", src.optString("nome", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                snap.sourceItemPath = src.optString("path", src.optString("caminho", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                snap.sourceItemSha256 = src.optString("itemSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
                snap.sourceItemHash = src.optString("itemHash", src.optString("hash", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                snap.title = !snap.sourceItemName.isEmpty() ? snap.sourceItemName : snap.folder;
            }
            JSONObject anchors = manifest.optJSONObject("anchors"); //$NON-NLS-1$
            if (anchors != null) {
                snap.startAnchor = anchors.optString("start", ""); //$NON-NLS-1$ //$NON-NLS-2$
                snap.endAnchor = anchors.optString("end", ""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            JSONObject censor = manifest.optJSONObject("censorship"); //$NON-NLS-1$
            if (censor != null) {
                snap.censorshipEnabled = true;
                snap.censorshipSimilarity = censor.optInt("similarityPercent", 0); //$NON-NLS-1$
                snap.censorshipBlur = censor.optInt("levelPercent", 0); //$NON-NLS-1$
                snap.censorshipMatchedCount = censor.optInt("censoredMessages", 0); //$NON-NLS-1$
            }
            snap.annotationsEnabled = !manifest.optString("annotationsSha256", "").isEmpty() //$NON-NLS-1$ //$NON-NLS-2$
                    || manifest.optJSONObject("annotations") != null; //$NON-NLS-1$
        }
        if (snap.title.isEmpty()) {
            snap.title = snap.folder;
        }
        if (snap.format.isEmpty()) {
            snap.format = Files.isRegularFile(messagesPath, LinkOption.NOFOLLOW_LINKS)
                    ? "html-message-v1" : "legacy-screenshot"; //$NON-NLS-1$ //$NON-NLS-2$
        }

        snap.counts = classifyCaptureMediaCounts(captureDir, manifest);
        FolderStats stats = computeFolderHashAndStats(captureDir);
        snap.folderHash = stats.hash;
        snap.fileCount = stats.fileCount;
        snap.totalBytes = stats.totalBytes;
        return snap;
    }

    static CustodyCaseRecord.MediaCounts classifyCaptureMediaCounts(Path captureDir, JSONObject manifest) {
        CustodyCaseRecord.MediaCounts counts = new CustodyCaseRecord.MediaCounts();
        Path messagesPath = captureDir.resolve("messages.json"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(messagesPath)) {
            try {
                String raw = CaptureIntegrity.readUtf8Limited(messagesPath, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES);
                JSONObject wrapper = new JSONObject(raw);
                JSONArray msgs = wrapper.optJSONArray("messages"); //$NON-NLS-1$
                Set<String> seenAttPaths = new LinkedHashSet<>();
                if (msgs != null) {
                    counts.msg = msgs.length();
                    for (int i = 0; i < msgs.length(); i++) {
                        JSONObject row = msgs.optJSONObject(i);
                        if (row == null) continue;
                        JSONArray atts = row.optJSONArray("attachments"); //$NON-NLS-1$
                        if (atts == null) continue;
                        for (int j = 0; j < atts.length(); j++) {
                            JSONObject att = atts.optJSONObject(j);
                            if (att == null) continue;
                            String p = att.optString("path", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
                            if (p.isEmpty() || !seenAttPaths.add(p.toLowerCase(Locale.ROOT))) {
                                continue;
                            }
                            incrementCategory(counts, att.optString("mime", ""), p); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                }
            } catch (Exception ignored) {
                if (manifest != null) {
                    counts.msg = Math.max(0, manifest.optInt("messageCount", 0)); //$NON-NLS-1$
                }
            }
            if (CaptureIntegrity.isSafeRegularFile(captureDir.resolve("annotations.json"))) { //$NON-NLS-1$
                counts.annotations = 1;
            }
            return counts;
        }

        // Legacy screenshot capture fallback
        Path screenshots = captureDir.resolve("screenshots"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeDirectory(screenshots)) {
            counts.img += countFilesInSubtree(screenshots);
        }
        Path anexos = captureDir.resolve("anexos"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeDirectory(anexos)) {
            try (Stream<Path> s = Files.walk(anexos, 4)) {
                s.filter(CaptureIntegrity::isSafeRegularFile).forEach(p -> {
                    String rel = anexos.relativize(p).toString().replace('\\', '/');
                    incrementCategory(counts, "", rel); //$NON-NLS-1$
                });
            } catch (IOException ignored) {
            }
        }
        if (CaptureIntegrity.isSafeRegularFile(captureDir.resolve("annotations.json")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(captureDir.resolve("legacy-annotations.json"))) { //$NON-NLS-1$
            counts.annotations = 1;
        }
        return counts;
    }

    private static void incrementCategory(CustodyCaseRecord.MediaCounts counts, String mimeRaw, String pathRaw) {
        String mime = mimeRaw == null ? "" : mimeRaw.trim().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        String path = pathRaw == null ? "" : pathRaw.trim().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        if (mime.startsWith("image/") || path.startsWith("imagens/") //$NON-NLS-1$ //$NON-NLS-2$
                || hasExt(path, ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".heic", ".tif", ".tiff")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
            counts.img++;
        } else if (mime.startsWith("audio/") || path.startsWith("audios/") //$NON-NLS-1$ //$NON-NLS-2$
                || hasExt(path, ".ogg", ".opus", ".wav", ".mp3", ".m4a", ".aac", ".amr", ".flac")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
            counts.audio++;
        } else if (mime.startsWith("video/") || path.startsWith("videos/") //$NON-NLS-1$ //$NON-NLS-2$
                || hasExt(path, ".mp4", ".webm", ".3gp", ".mov", ".avi", ".mkv")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            counts.video++;
        } else if (mime.contains("pdf") || mime.startsWith("text/") //$NON-NLS-1$ //$NON-NLS-2$
                || mime.contains("word") || mime.contains("excel") || mime.contains("powerpoint") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || mime.contains("officedocument") || mime.contains("zip") || mime.contains("rar") || mime.contains("7z") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || path.startsWith("pdfs/") //$NON-NLS-1$
                || hasExt(path, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".zip", ".rar", ".7z")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            counts.doc++;
        } else {
            counts.other++;
        }
    }

    private static boolean hasExt(String path, String... exts) {
        for (String ext : exts) {
            if (path.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static int countFilesInSubtree(Path dir) {
        int[] c = new int[1];
        try (Stream<Path> s = Files.walk(dir, 4)) {
            s.filter(CaptureIntegrity::isSafeRegularFile).forEach(p -> c[0]++);
        } catch (IOException ignored) {
        }
        return c[0];
    }

    private static final class FolderStats {
        String hash = ""; //$NON-NLS-1$
        int fileCount;
        long totalBytes;
    }

    private static FolderStats computeFolderHashAndStats(Path captureDir) throws IOException {
        FolderStats stats = new FolderStats();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(captureDir, Collections.emptySet(), 12, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(captureDir) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isSymbolicLink() && !attrs.isOther() && attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(p -> captureDir.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT)));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            for (Path f : files) {
                String rel = captureDir.relativize(f).toString().replace('\\', '/');
                long sz = CaptureIntegrity.safeRegularFileSize(f);
                String sha = CaptureIntegrity.sha256(f);
                md.update((rel + "|" + sz + "|" + sha + "\n").getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                stats.fileCount++;
                stats.totalBytes += sz;
            }
            byte[] dig = md.digest();
            StringBuilder hex = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                hex.append(String.format("%02x", b & 0xff)); //$NON-NLS-1$
            }
            stats.hash = hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 indisponivel", e); //$NON-NLS-1$
        }
        return stats;
    }

    /**
     * Reconciles current capture folders on disk against {@code record.knownCaptures}.
     * Emits {@code ADICIONADO captura} for new folders and {@code DELETADO captura} for
     * missing folders, updating {@code record.knownCaptures} in place.
     */
    static ReconcileSummary reconcileKnownCaptures(Path outputRoot, CustodyCaseRecord record,
            String triggerContext) throws IOException {
        ReconcileSummary summary = new ReconcileSummary();
        Map<String, CustodyCaseRecord.CaptureSnapshot> current = scanCurrentCaptures(outputRoot);
        summary.currentCaptures.putAll(current);

        // 1. Detect DELETED captures (present in previous snapshot, absent now)
        List<String> previousNames = new ArrayList<>(record.knownCaptures.keySet());
        previousNames.sort(String.CASE_INSENSITIVE_ORDER);
        for (String prevFolder : previousNames) {
            if (!current.containsKey(prevFolder)) {
                CustodyCaseRecord.CaptureSnapshot prev = record.knownCaptures.get(prevFolder);
                summary.deletedFolders.add(prevFolder);
                String delDetail = "Detectado ausente na pasta raiz durante " + triggerContext //$NON-NLS-1$
                        + " | anterior: " + (prev == null ? "-" : prev.counts.toSummaryLine("")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                appendEvent(outputRoot,
                        "DELETADO captura", //$NON-NLS-1$
                        prevFolder,
                        "REMOVIDO_DA_PASTA", //$NON-NLS-1$
                        delDetail,
                        "", //$NON-NLS-1$
                        prev == null ? "" : prev.folderHash, //$NON-NLS-1$
                        prev == null ? new CustodyCaseRecord.MediaCounts() : prev.counts);
                appendChainedLogLine(outputRoot, record, "DELETADO", prevFolder, "REMOVIDO_DA_PASTA", //$NON-NLS-1$ //$NON-NLS-2$
                        delDetail + (prev == null || prev.folderHash.isEmpty() ? "" : "; hashAnterior=" + prev.folderHash), //$NON-NLS-1$ //$NON-NLS-2$
                        defaultRecoveryPrompt);
                record.addStep(triggerContext, prevFolder, "DELETADO captura", //$NON-NLS-1$
                        "Captura removida da pasta raiz (ultimo hash conhecido: " //$NON-NLS-1$
                                + (prev == null ? "-" : prev.folderHash) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        // 2. Detect ADDED captures (present now, absent in previous snapshot)
        for (Map.Entry<String, CustodyCaseRecord.CaptureSnapshot> e : current.entrySet()) {
            String folder = e.getKey();
            CustodyCaseRecord.CaptureSnapshot snap = e.getValue();
            if (!record.knownCaptures.containsKey(folder)) {
                summary.addedFolders.add(folder);
                String addDetail = "Captura adicionada (" + triggerContext + ") | " + snap.counts.toSummaryLine("OK"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                appendEvent(outputRoot,
                        "ADICIONADO captura", //$NON-NLS-1$
                        folder,
                        "ADICIONADO_OK", //$NON-NLS-1$
                        addDetail,
                        snap.folderHash,
                        "", //$NON-NLS-1$
                        snap.counts);
                appendChainedLogLine(outputRoot, record, "ADICIONADO", folder, "ADICIONADO_OK", //$NON-NLS-1$ //$NON-NLS-2$
                        addDetail + (snap.folderHash.isEmpty() ? "" : "; folderHash=" + snap.folderHash), //$NON-NLS-1$ //$NON-NLS-2$
                        defaultRecoveryPrompt);
                record.addStep(triggerContext, folder, "ADICIONADO captura", //$NON-NLS-1$
                        "Nova captura registrada: " + snap.counts.toSummaryLine("OK") + " | hash=" + snap.folderHash); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }

        record.knownCaptures.clear();
        record.knownCaptures.putAll(current);
        return summary;
    }

    static AuditLogEntry recordStartCapture(Path outputRoot, CustodyCaseRecord record, Path captureDir) throws IOException {
        CustodyCaseRecord.CaptureSnapshot snap = inspectCaptureFolder(outputRoot, captureDir);
        if (snap == null) {
            return null;
        }
        String detail = "Captura concluida e todos os arquivos integros OK | "
                + snap.counts.toSummaryLine("OK") //$NON-NLS-1$
                + (snap.htmlSha256.isEmpty() ? "" : " | htmlSha256=" + snap.htmlSha256) //$NON-NLS-1$ //$NON-NLS-2$
                + (snap.captureRootHash.isEmpty() ? "" : " | captureRootHash=" + snap.captureRootHash); //$NON-NLS-1$ //$NON-NLS-2$
        AuditLogEntry entry = appendEvent(outputRoot,
                "START", //$NON-NLS-1$
                snap.folder,
                "CONCLUIDO_OK", //$NON-NLS-1$
                detail,
                snap.folderHash,
                "", //$NON-NLS-1$
                snap.counts);
        appendChainedLogLine(outputRoot, record, "START", snap.folder, "CONCLUIDO_OK", detail, defaultRecoveryPrompt); //$NON-NLS-1$ //$NON-NLS-2$
        record.addStep("START", snap.folder, "Captura concluida", detail); //$NON-NLS-1$ //$NON-NLS-2$
        // Also reconcile so ADICIONADO + any external deletions are logged cleanly
        reconcileKnownCaptures(outputRoot, record, "START"); //$NON-NLS-1$
        return entry;
    }

    static AuditLogEntry recordRecaptureOutcome(Path outputRoot, CustodyCaseRecord record,
            Path captureDir, String folderName, String statusCode, String extraDetail,
            String previousFolderHash) throws IOException {
        CustodyCaseRecord.CaptureSnapshot snap = (captureDir != null && CaptureIntegrity.isSafeDirectory(captureDir))
                ? inspectCaptureFolder(outputRoot, captureDir)
                : null;
        CustodyCaseRecord.MediaCounts counts = snap != null ? snap.counts : new CustodyCaseRecord.MediaCounts();
        String newHash = snap != null ? snap.folderHash : ""; //$NON-NLS-1$
        String suffix = "INALTERADO_OK".equals(statusCode) ? "inalterado OK" : "OK"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        StringBuilder detail = new StringBuilder();
        if ("INALTERADO_OK".equals(statusCode)) { //$NON-NLS-1$
            detail.append("Todos os arquivos inalterados OK | ").append(counts.toSummaryLine(suffix)); //$NON-NLS-1$
        } else {
            detail.append(counts.toSummaryLine(suffix));
        }
        if (extraDetail != null && !extraDetail.isBlank()) {
            detail.append(" | ").append(extraDetail.trim()); //$NON-NLS-1$
        }
        AuditLogEntry entry = appendEvent(outputRoot,
                "RE-EXTRAIR", //$NON-NLS-1$
                folderName,
                statusCode,
                detail.toString(),
                newHash,
                previousFolderHash == null ? "" : previousFolderHash, //$NON-NLS-1$
                counts);
        appendChainedLogLine(outputRoot, record, "RE-EXTRAIR", folderName, statusCode, detail.toString(), defaultRecoveryPrompt); //$NON-NLS-1$
        record.addStep("RE-EXTRAIR", folderName, statusCode, detail.toString()); //$NON-NLS-1$
        if (snap != null) {
            record.knownCaptures.put(snap.folder, snap);
        }
        return entry;
    }

    static synchronized AuditLogEntry appendEvent(Path outputRoot, String eventType, String captureFolder,
            String status, String summary, String folderHash, String previousFolderHash,
            CustodyCaseRecord.MediaCounts counts) throws IOException {
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, CustodyCaseRecord.LAUDO_DIR_NAME);
        CaptureIntegrity.createDirectoriesSafe(outputRoot, laudoDir);
        Path jsonlPath = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.AUDIT_LOG_JSONL_FILE);
        Path txtPath = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.AUDIT_LOG_TXT_FILE);

        List<AuditLogEntry> existing = readLogEntries(outputRoot);
        int nextSeq = existing.size() + 1;
        String prevHash = existing.isEmpty() ? "" : existing.get(existing.size() - 1).recordHash; //$NON-NLS-1$

        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        AuditLogEntry entry = new AuditLogEntry();
        entry.sequence = nextSeq;
        entry.timestampLocal = CustodyCaseRecord.formatLocalTimestamp(now);
        entry.timestampUtc = now.withOffsetSameInstant(ZoneOffset.UTC).withNano(0).toString();
        entry.eventType = CustodyCaseRecord.clamp(eventType, 64);
        entry.captureFolder = CustodyCaseRecord.clamp(captureFolder, 512);
        entry.status = CustodyCaseRecord.clamp(status, 64);
        entry.summary = CustodyCaseRecord.clamp(summary, 2048);
        entry.folderHash = CustodyCaseRecord.clamp(folderHash, 128);
        entry.previousFolderHash = CustodyCaseRecord.clamp(previousFolderHash, 128);
        entry.counts = counts == null ? new CustodyCaseRecord.MediaCounts() : counts;
        entry.previousRecordHash = prevHash;
        entry.recordHash = CaptureIntegrity.sha256Text(CaptureIntegrity.canonicalJson(entry.toJsonWithoutRecordHash()));

        if (CaptureIntegrity.isLinkOrReparse(jsonlPath) || CaptureIntegrity.isLinkOrReparse(txtPath)) {
            throw new IOException("Link simbolico ou juncao nao permitido no log de auditoria."); //$NON-NLS-1$
        }
        try (BufferedWriter bw = Files.newBufferedWriter(jsonlPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            bw.write(entry.toJson().toString());
            bw.write('\n');
        }
        try (BufferedWriter bw = Files.newBufferedWriter(txtPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            bw.write(entry.toReadableLine());
            bw.write('\n');
        }
        return entry;
    }

    static List<AuditLogEntry> readLogEntries(Path outputRoot) throws IOException {
        List<AuditLogEntry> list = new ArrayList<>();
        if (outputRoot == null) {
            return list;
        }
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, CustodyCaseRecord.LAUDO_DIR_NAME);
        Path jsonlPath = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.AUDIT_LOG_JSONL_FILE);
        if (!Files.exists(jsonlPath, LinkOption.NOFOLLOW_LINKS)) {
            return list;
        }
        if (CaptureIntegrity.isLinkOrReparse(laudoDir) || CaptureIntegrity.isLinkOrReparse(jsonlPath)
                || !Files.isRegularFile(jsonlPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo de log de auditoria invalido: " + jsonlPath); //$NON-NLS-1$
        }
        if (CaptureIntegrity.safeRegularFileSize(jsonlPath) > CaptureIntegrity.MAX_AUDIT_BYTES) {
            throw new IOException("Log de auditoria excede o limite de 16 MiB."); //$NON-NLS-1$
        }
        try (BufferedReader br = Files.newBufferedReader(jsonlPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                list.add(AuditLogEntry.fromJson(new JSONObject(trimmed)));
                if (list.size() >= MAX_LOG_ENTRIES_READ) {
                    break;
                }
            }
        }
        return list;
    }

    static boolean verifyAuditLogChain(List<AuditLogEntry> entries) {
        if (entries == null) {
            return false;
        }
        String expectedPrev = ""; //$NON-NLS-1$
        for (int i = 0; i < entries.size(); i++) {
            AuditLogEntry e = entries.get(i);
            if (e.sequence != i + 1 || !expectedPrev.equals(e.previousRecordHash)) {
                return false;
            }
            String computed;
            try {
                computed = CaptureIntegrity.sha256Text(CaptureIntegrity.canonicalJson(e.toJsonWithoutRecordHash()));
            } catch (IOException ex) {
                return false;
            }
            if (!computed.equalsIgnoreCase(e.recordHash)) {
                return false;
            }
            expectedPrev = e.recordHash;
        }
        return true;
    }

    // ==================== Append-Only Chained laudo/Log.txt (Phase 2) ====================

    private static String sanitizeLogField(String raw, int maxLen) {
        if (raw == null) {
            return "-"; //$NON-NLS-1$
        }
        String clean = raw.replace('\0', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('|', '/')
                .replaceAll("\\s+", " ") //$NON-NLS-1$ //$NON-NLS-2$
                .trim();
        if (clean.isEmpty()) {
            return "-"; //$NON-NLS-1$
        }
        return clean.length() <= maxLen ? clean : clean.substring(0, maxLen);
    }

    private static String buildChainedLogLine(int seq, String tsLocal, String tsUtc,
            String eventType, String folder, String status, String detail, String prevHash) throws IOException {
        String safePrev = (prevHash == null || !prevHash.matches("(?i)[0-9a-f]{64}"))
                ? ZERO_HASH_64 : prevHash.toLowerCase(Locale.ROOT);
        String payload = seq
                + "|" + sanitizeLogField(tsLocal, 64) //$NON-NLS-1$
                + "|" + sanitizeLogField(tsUtc, 64) //$NON-NLS-1$
                + "|" + sanitizeLogField(eventType, 64) //$NON-NLS-1$
                + "|" + sanitizeLogField(folder, 256) //$NON-NLS-1$
                + "|" + sanitizeLogField(status, 64) //$NON-NLS-1$
                + "|" + sanitizeLogField(detail, 1500) //$NON-NLS-1$
                + "|prev=" + safePrev; //$NON-NLS-1$
        String lineHash = CaptureIntegrity.sha256Text(payload);
        return payload + "|hash=" + lineHash; //$NON-NLS-1$
    }

    static ChainedLogSnapshot readAndVerifyChainedLogTxt(Path outputRoot,
            int expectedAnchorLine, String expectedAnchorPrefixSha256) throws IOException {
        ChainedLogSnapshot snap = new ChainedLogSnapshot();
        if (outputRoot == null) {
            return snap;
        }
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, CustodyCaseRecord.LAUDO_DIR_NAME);
        Path logPath = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.LOG_TXT_FILE);
        if (!Files.exists(logPath, LinkOption.NOFOLLOW_LINKS)) {
            snap.exists = false;
            if (expectedAnchorLine >= 0) {
                snap.status = "AUSENTE"; //$NON-NLS-1$
                snap.errorDetail = "laudo/Log.txt ausente, mas havia ancora registrada ate a linha " + expectedAnchorLine; //$NON-NLS-1$
            } else {
                snap.status = "NAO_INICIADO"; //$NON-NLS-1$
            }
            return snap;
        }
        if (CaptureIntegrity.isLinkOrReparse(laudoDir) || CaptureIntegrity.isLinkOrReparse(logPath)
                || !Files.isRegularFile(logPath, LinkOption.NOFOLLOW_LINKS)) {
            snap.exists = true;
            snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
            snap.errorDetail = "laudo/Log.txt e link simbolico/juncao ou arquivo invalido."; //$NON-NLS-1$
            return snap;
        }
        long size = CaptureIntegrity.safeRegularFileSize(logPath);
        snap.exists = true;
        snap.byteLength = size;
        if (size > CaptureIntegrity.MAX_AUDIT_BYTES) {
            snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
            snap.errorDetail = "laudo/Log.txt excede limite maximo de 16 MiB."; //$NON-NLS-1$
            return snap;
        }
        List<String> rawLines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                if (rawLines.isEmpty() && !ln.isEmpty() && ln.charAt(0) == '\uFEFF') {
                    ln = ln.substring(1);
                }
                if (ln.isEmpty()) {
                    continue;
                }
                rawLines.add(ln);
                if (rawLines.size() > MAX_CHAINED_LOG_TXT_LINES) {
                    snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                    snap.errorDetail = "laudo/Log.txt excede limite maximo de linhas."; //$NON-NLS-1$
                    return snap;
                }
            }
        }
        if (rawLines.isEmpty()) {
            snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
            snap.errorDetail = "laudo/Log.txt esta vazio."; //$NON-NLS-1$
            return snap;
        }

        String expectedPrev = ZERO_HASH_64;
        MessageDigest fullPrefixMd;
        MessageDigest anchorPrefixMd = null;
        try {
            fullPrefixMd = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            if (expectedAnchorLine >= 0) {
                anchorPrefixMd = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 indisponivel", e); //$NON-NLS-1$
        }

        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            int hashIdx = line.lastIndexOf("|hash="); //$NON-NLS-1$
            if (hashIdx <= 0) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Linha " + i + " sem sufixo |hash= valido."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            String payload = line.substring(0, hashIdx);
            String declaredHash = line.substring(hashIdx + "|hash=".length()).trim().toLowerCase(Locale.ROOT); //$NON-NLS-1$
            if (!declaredHash.matches("[0-9a-f]{64}")) { //$NON-NLS-1$
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Linha " + i + " com hash invalido."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            String[] parts = payload.split("\\|", -1); //$NON-NLS-1$
            if (parts.length != 8) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Linha " + i + " com numero invalido de colunas (" + parts.length + ")."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                return snap;
            }
            int parsedSeq;
            try {
                parsedSeq = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException nfe) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Linha " + i + " com indice invalido."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            if (parsedSeq != i) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Sequencia quebrada na linha " + i + " (encontrado " + parsedSeq + ")."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                return snap;
            }
            if (!parts[7].startsWith("prev=")) { //$NON-NLS-1$
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Linha " + i + " sem campo prev=."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            String declaredPrev = parts[7].substring("prev=".length()).trim().toLowerCase(Locale.ROOT); //$NON-NLS-1$
            if (!expectedPrev.equals(declaredPrev)) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Encadeamento prev divergente na linha " + i + "."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            String computedHash = CaptureIntegrity.sha256Text(payload);
            if (!computedHash.equalsIgnoreCase(declaredHash)) {
                snap.status = "CADEIA_ADULTERADA"; //$NON-NLS-1$
                snap.errorDetail = "Hash SHA-256 divergente na linha " + i + "."; //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
            if (i == 0) {
                snap.createdAtLocal = parts[1].trim();
                snap.createdAtUtc = parts[2].trim();
            }
            byte[] normalizedBytes = (line + "\n").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
            fullPrefixMd.update(normalizedBytes);
            if (anchorPrefixMd != null && i <= expectedAnchorLine) {
                anchorPrefixMd.update(normalizedBytes);
            }
            expectedPrev = declaredHash;
        }

        snap.lines.addAll(rawLines);
        snap.lineCount = rawLines.size();
        snap.lastLineIndex = rawLines.size() - 1;
        snap.headHash = expectedPrev;
        snap.prefixSha256 = toHex(fullPrefixMd.digest());

        if (expectedAnchorLine >= 0) {
            if (snap.lastLineIndex < expectedAnchorLine) {
                snap.chainValid = false;
                snap.status = "TRUNCADO"; //$NON-NLS-1$
                snap.errorDetail = "Log.txt truncado: esperadas pelo menos " + (expectedAnchorLine + 1) //$NON-NLS-1$
                        + " linhas (0.." + expectedAnchorLine + "), encontradas " + snap.lineCount + "."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                return snap;
            }
            if (expectedAnchorPrefixSha256 != null && !expectedAnchorPrefixSha256.isBlank() && anchorPrefixMd != null) {
                String computedAnchorPrefix = toHex(anchorPrefixMd.digest());
                if (!computedAnchorPrefix.equalsIgnoreCase(expectedAnchorPrefixSha256.trim())) {
                    snap.chainValid = false;
                    snap.status = "PREFIXO_DIVERGENTE"; //$NON-NLS-1$
                    snap.errorDetail = "Prefixo 0.." + expectedAnchorLine + " diverge da ancora anterior (" //$NON-NLS-1$ //$NON-NLS-2$
                            + expectedAnchorPrefixSha256.substring(0, Math.min(12, expectedAnchorPrefixSha256.length())) + "...)."; //$NON-NLS-1$
                    return snap;
                }
            }
        }
        snap.chainValid = true;
        snap.status = "CONFERE"; //$NON-NLS-1$
        return snap;
    }

    static synchronized ChainedLogSnapshot ensureChainedLogInitialized(Path outputRoot,
            CustodyCaseRecord record, LogRecoveryPrompt prompt) throws IOException {
        return appendChainedLogLine(outputRoot, record, null, null, null, null, prompt);
    }

    static synchronized ChainedLogSnapshot appendChainedLogLine(Path outputRoot, CustodyCaseRecord record,
            String eventType, String captureFolder, String status, String detail,
            LogRecoveryPrompt prompt) throws IOException {
        if (outputRoot == null || record == null) {
            return new ChainedLogSnapshot();
        }
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, CustodyCaseRecord.LAUDO_DIR_NAME);
        CaptureIntegrity.createDirectoriesSafe(outputRoot, laudoDir);
        Path logPath = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.LOG_TXT_FILE);

        ChainedLogSnapshot snap = readAndVerifyChainedLogTxt(
                outputRoot, record.logAnchorLastLine, record.logAnchorPrefixSha256);

        if ("NAO_INICIADO".equals(snap.status)) { //$NON-NLS-1$
            // First-time creation of laudo/Log.txt (never truncates existing files)
            OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
            String tsLocal = CustodyCaseRecord.formatLocalTimestamp(now);
            String tsUtc = now.withOffsetSameInstant(ZoneOffset.UTC).withNano(0).toString();
            String line0 = buildChainedLogLine(0, tsLocal, tsUtc, "CRIADO", "-", "OK", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "Log.txt criado em " + tsLocal + "; plugin=" + HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION, //$NON-NLS-1$ //$NON-NLS-2$
                    ZERO_HASH_64);
            appendRawLineLocked(logPath, line0);

            // Preserve reference to legacy registro-capturas.jsonl if present without modifying it
            Path legacyJsonl = CaptureIntegrity.resolveContained(laudoDir, CustodyCaseRecord.AUDIT_LOG_JSONL_FILE);
            if (CaptureIntegrity.isSafeRegularFile(legacyJsonl)) {
                String legacySha = CaptureIntegrity.sha256(legacyJsonl);
                String prev0 = extractLineHash(line0);
                String line1 = buildChainedLogLine(1, tsLocal, tsUtc, "IMPORTADO_LEGADO", "-", "OK", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "Referencia preservada de registro-capturas.jsonl sha256=" + legacySha, prev0); //$NON-NLS-1$
                appendRawLineLocked(logPath, line1);
            }
            snap = readAndVerifyChainedLogTxt(outputRoot, -1, ""); //$NON-NLS-1$
            syncRecordFromLogSnapshot(record, snap, false);
        } else if (!snap.chainValid) {
            // Missing or tampered Log.txt! Alert user and ask whether to recreate (marking in PDF).
            String issueType = "AUSENTE".equals(snap.status) ? "AUSENTE" : "ADULTERADO"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String prevAnchorDesc = "linhas 0.." + record.logAnchorLastLine //$NON-NLS-1$
                    + " prefix=" + (record.logAnchorPrefixSha256.isEmpty() ? "-" : record.logAnchorPrefixSha256) //$NON-NLS-1$ //$NON-NLS-2$
                    + " head=" + (record.logAnchorHeadHash.isEmpty() ? "-" : record.logAnchorHeadHash); //$NON-NLS-1$ //$NON-NLS-2$
            String alertDetails = "Problema detectado em laudo/Log.txt (" + snap.status + "): " //$NON-NLS-1$ //$NON-NLS-2$
                    + snap.errorDetail + "\nAncora anterior: " + prevAnchorDesc; //$NON-NLS-1$

            LogRecoveryPrompt activePrompt = prompt != null ? prompt : defaultRecoveryPrompt;
            boolean recreate = activePrompt != null && activePrompt.confirmRecreateLog(issueType, alertDetails);

            OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
            String tsLocal = CustodyCaseRecord.formatLocalTimestamp(now);
            String tsUtc = now.withOffsetSameInstant(ZoneOffset.UTC).withNano(0).toString();

            if (recreate) {
                String backupName = ""; //$NON-NLS-1$
                if (Files.exists(logPath, LinkOption.NOFOLLOW_LINKS) && !CaptureIntegrity.isLinkOrReparse(logPath)) {
                    String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(now); //$NON-NLS-1$
                    backupName = "Log.txt.corrompido-" + stamp; //$NON-NLS-1$
                    Path backupPath = CaptureIntegrity.resolveContained(laudoDir, backupName);
                    try {
                        Files.move(logPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException moveErr) {
                        Files.deleteIfExists(logPath);
                        backupName = ""; //$NON-NLS-1$
                    }
                }
                String incident = "[" + tsLocal + "] MOTIVO=" + issueType + " (" + snap.status + ") | ACAO=RECRIADO_POR_SOLICITACAO_DO_USUARIO | ANCORA_ANTERIOR=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + prevAnchorDesc + (backupName.isEmpty() ? "" : " | COPIA_CORROMPIDA=" + backupName) //$NON-NLS-1$ //$NON-NLS-2$
                        + " | DETALHE=" + snap.errorDetail; //$NON-NLS-1$
                record.logIncidents.add(incident);
                record.logIntegrityStatus = "RECRIADO_POR_SOLICITACAO_DO_USUARIO"; //$NON-NLS-1$
                record.addStep("LOG_AUDITORIA", "laudo/Log.txt", "RECRIADO_POR_SOLICITACAO_DO_USUARIO", incident); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                String line0 = buildChainedLogLine(0, tsLocal, tsUtc, "CRIADO_RECRIADO", "-", "RECRIADO", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "Log.txt recriado por solicitacao do usuario em " + tsLocal + "; motivo=" + issueType //$NON-NLS-1$ //$NON-NLS-2$
                                + "; ancoraAnterior=" + prevAnchorDesc + "; plugin=" + HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION, //$NON-NLS-1$ //$NON-NLS-2$
                        ZERO_HASH_64);
                appendRawLineLocked(logPath, line0);
                snap = readAndVerifyChainedLogTxt(outputRoot, -1, ""); //$NON-NLS-1$
                syncRecordFromLogSnapshot(record, snap, true);
            } else {
                String incident = "[" + tsLocal + "] MOTIVO=" + issueType + " (" + snap.status + ") | ACAO=NAO_RECRIADO_PELO_USUARIO | ANCORA_ANTERIOR=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + prevAnchorDesc + " | DETALHE=" + snap.errorDetail; //$NON-NLS-1$
                record.logIncidents.add(incident);
                record.logIntegrityStatus = "AUSENTE".equals(issueType) //$NON-NLS-1$
                        ? "AUSENTE_NAO_RECRIADO" : "ADULTERADO_NAO_RECRIADO"; //$NON-NLS-1$ //$NON-NLS-2$
                record.addStep("LOG_AUDITORIA", "laudo/Log.txt", record.logIntegrityStatus, incident); //$NON-NLS-1$ //$NON-NLS-2$
                return snap;
            }
        }

        if (eventType != null && !eventType.isBlank()) {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
            String tsLocal = CustodyCaseRecord.formatLocalTimestamp(now);
            String tsUtc = now.withOffsetSameInstant(ZoneOffset.UTC).withNano(0).toString();
            int nextSeq = snap.lastLineIndex + 1;
            String nextLine = buildChainedLogLine(nextSeq, tsLocal, tsUtc,
                    eventType, captureFolder, status, detail, snap.headHash);
            appendRawLineLocked(logPath, nextLine);
            snap = readAndVerifyChainedLogTxt(outputRoot, snap.lastLineIndex, snap.prefixSha256);
            boolean keepRecreatedFlag = "RECRIADO_POR_SOLICITACAO_DO_USUARIO".equals(record.logIntegrityStatus); //$NON-NLS-1$
            syncRecordFromLogSnapshot(record, snap, keepRecreatedFlag);
        } else {
            boolean keepRecreatedFlag = "RECRIADO_POR_SOLICITACAO_DO_USUARIO".equals(record.logIntegrityStatus); //$NON-NLS-1$
            syncRecordFromLogSnapshot(record, snap, keepRecreatedFlag);
        }
        return snap;
    }

    private static void syncRecordFromLogSnapshot(CustodyCaseRecord record, ChainedLogSnapshot snap, boolean keepRecreatedStatus) {
        if (record == null || snap == null || !snap.chainValid) {
            return;
        }
        record.logCreatedAtLocal = snap.createdAtLocal;
        record.logCreatedAtUtc = snap.createdAtUtc;
        record.logAnchorLastLine = snap.lastLineIndex;
        record.logAnchorPrefixSha256 = snap.prefixSha256;
        record.logAnchorHeadHash = snap.headHash;
        record.logFileBytes = snap.byteLength;
        record.logIntegrityStatus = keepRecreatedStatus ? "RECRIADO_POR_SOLICITACAO_DO_USUARIO" : "CONFERE"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String extractLineHash(String line) {
        int idx = line.lastIndexOf("|hash="); //$NON-NLS-1$
        return idx >= 0 ? line.substring(idx + "|hash=".length()).trim() : ZERO_HASH_64; //$NON-NLS-1$
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF)); //$NON-NLS-1$
        }
        return sb.toString();
    }

    private static void appendRawLineLocked(Path logPath, String line) throws IOException {
        if (CaptureIntegrity.isLinkOrReparse(logPath)) {
            throw new IOException("Link simbolico ou juncao nao permitido em laudo/Log.txt."); //$NON-NLS-1$
        }
        byte[] data = (line + "\n").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
        try (FileChannel ch = FileChannel.open(logPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            FileLock lock = null;
            try {
                lock = ch.lock();
            } catch (Throwable ignored) {
            }
            try {
                ByteBuffer buf = ByteBuffer.wrap(data);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                ch.force(false);
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
