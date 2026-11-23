/*
 * Chat Captura IPED - Modelo de Custodia e Laudo Tecnico
 * Copyright (C) 2026 iagobusca
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persistent structured model stored at {@code <outputRoot>/laudo/custodia-caso.json}.
 * Holds device identification, chain of custody, IPED/plugin parameters, known capture
 * snapshots (used for ADDED / DELETED reconciliation), and chronological step records.
 */
final class CustodyCaseRecord {

    static final int SCHEMA_VERSION = 1;
    static final String LAUDO_DIR_NAME = "laudo"; //$NON-NLS-1$
    static final String CUSTODY_JSON_FILE = "custodia-caso.json"; //$NON-NLS-1$
    static final String REPORT_ORIGIN_PDF_FILE = "report.pdf"; //$NON-NLS-1$
    static final String LEGACY_REPORT_ORIGIN_PDF_FILE = "report-origem.pdf"; //$NON-NLS-1$
    static final String REPORT_PLUGIN_PDF_FILE = "report-plugin.pdf"; //$NON-NLS-1$
    static final String REPORT_UNIFIED_PDF_FILE = "report-completo-unificado.pdf"; //$NON-NLS-1$
    static final String REPORT_HTML_FILE = "laudo-tecnico.html"; //$NON-NLS-1$
    static final String LOG_TXT_FILE = "Log.txt"; //$NON-NLS-1$
    static final String AUDIT_LOG_JSONL_FILE = "registro-capturas.jsonl"; //$NON-NLS-1$
    static final String AUDIT_LOG_TXT_FILE = "registro-capturas.log"; //$NON-NLS-1$
    static final String LAUDO_HASHES_TXT_FILE = "laudo-hashes.txt"; //$NON-NLS-1$

    private static final int MAX_SHORT_FIELD = 512;
    private static final int MAX_LONG_FIELD = 4096;
    private static final int MAX_METHOD_TEXT = 16384;
    private static final int MAX_STEPS = 5000;

    enum StorageType {
        PASTA("PASTA", "Pasta"), //$NON-NLS-1$ //$NON-NLS-2$
        MIDIA_LACRADA("MIDIA_LACRADA", "M\u00eddia lacrada"), //$NON-NLS-1$ //$NON-NLS-2$
        CUSTOM("CUSTOM", "Custom"); //$NON-NLS-1$ //$NON-NLS-2$

        final String code;
        final String displayLabel;

        StorageType(String code, String displayLabel) {
            this.code = code;
            this.displayLabel = displayLabel;
        }

        static StorageType fromCode(String raw) {
            if (raw == null || raw.isBlank()) {
                return PASTA;
            }
            String norm = raw.trim().toUpperCase(Locale.ROOT);
            for (StorageType t : values()) {
                if (t.code.equals(norm) || t.displayLabel.equalsIgnoreCase(raw.trim())) {
                    return t;
                }
            }
            if (norm.contains("LACRAD") || norm.contains("MIDIA") || norm.contains("M\u00cdDIA")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                return MIDIA_LACRADA;
            }
            if (norm.contains("CUSTOM") || norm.contains("PERSONALIZ")) { //$NON-NLS-1$ //$NON-NLS-2$
                return CUSTOM;
            }
            return PASTA;
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    static final class MediaCounts {
        int msg;
        int img;
        int audio;
        int video;
        int doc;
        int other;
        int annotations;

        MediaCounts() {
        }

        MediaCounts(int msg, int img, int audio, int video, int doc, int other, int annotations) {
            this.msg = Math.max(0, msg);
            this.img = Math.max(0, img);
            this.audio = Math.max(0, audio);
            this.video = Math.max(0, video);
            this.doc = Math.max(0, doc);
            this.other = Math.max(0, other);
            this.annotations = Math.max(0, annotations);
        }

        int totalAttachments() {
            return img + audio + video + doc + other;
        }

        String toSummaryLine(String suffix) {
            String s = (suffix == null || suffix.isBlank()) ? "" : " " + suffix.trim(); //$NON-NLS-1$ //$NON-NLS-2$
            return "msg=" + msg + s + ", img=" + img + s + ", audio=" + audio + s //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + ", video=" + video + s + ", doc=" + doc + s + ", outros=" + other + s //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + (annotations > 0 ? ", anotacoes=" + annotations + s : ""); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("msg", msg) //$NON-NLS-1$
                    .put("img", img) //$NON-NLS-1$
                    .put("audio", audio) //$NON-NLS-1$
                    .put("video", video) //$NON-NLS-1$
                    .put("doc", doc) //$NON-NLS-1$
                    .put("other", other) //$NON-NLS-1$
                    .put("annotations", annotations); //$NON-NLS-1$
        }

        static MediaCounts fromJson(JSONObject o) {
            if (o == null) {
                return new MediaCounts();
            }
            return new MediaCounts(
                    o.optInt("msg", 0), //$NON-NLS-1$
                    o.optInt("img", 0), //$NON-NLS-1$
                    o.optInt("audio", 0), //$NON-NLS-1$
                    o.optInt("video", 0), //$NON-NLS-1$
                    o.optInt("doc", 0), //$NON-NLS-1$
                    o.optInt("other", 0), //$NON-NLS-1$
                    o.optInt("annotations", 0)); //$NON-NLS-1$
        }
    }

    static final class CaptureSnapshot {
        String folder = ""; //$NON-NLS-1$
        String title = ""; //$NON-NLS-1$
        String chatId = ""; //$NON-NLS-1$
        String format = ""; //$NON-NLS-1$
        String status = ""; //$NON-NLS-1$
        String createdAt = ""; //$NON-NLS-1$
        String lastSeenAt = ""; //$NON-NLS-1$
        String folderHash = ""; //$NON-NLS-1$
        String htmlSha256 = ""; //$NON-NLS-1$
        String messagesSha256 = ""; //$NON-NLS-1$
        String manifestSha256 = ""; //$NON-NLS-1$
        String integritySha256 = ""; //$NON-NLS-1$
        String originBindingSha256 = ""; //$NON-NLS-1$
        String captureRootHash = ""; //$NON-NLS-1$
        String contentRootHash = ""; //$NON-NLS-1$
        String sourceItemName = ""; //$NON-NLS-1$
        String sourceItemPath = ""; //$NON-NLS-1$
        String sourceItemSha256 = ""; //$NON-NLS-1$
        String sourceItemHash = ""; //$NON-NLS-1$
        String sourceDbPath = ""; //$NON-NLS-1$
        String sourceDbSha256 = ""; //$NON-NLS-1$
        String n3Status = ""; //$NON-NLS-1$
        String startAnchor = ""; //$NON-NLS-1$
        String endAnchor = ""; //$NON-NLS-1$
        boolean censorshipEnabled;
        int censorshipSimilarity;
        int censorshipBlur;
        int censorshipMatchedCount;
        boolean annotationsEnabled;
        int fileCount;
        long totalBytes;
        MediaCounts counts = new MediaCounts();

        JSONObject toJson() {
            return new JSONObject()
                    .put("folder", folder) //$NON-NLS-1$
                    .put("title", title) //$NON-NLS-1$
                    .put("chatId", chatId) //$NON-NLS-1$
                    .put("format", format) //$NON-NLS-1$
                    .put("status", status) //$NON-NLS-1$
                    .put("createdAt", createdAt) //$NON-NLS-1$
                    .put("lastSeenAt", lastSeenAt) //$NON-NLS-1$
                    .put("folderHash", folderHash) //$NON-NLS-1$
                    .put("htmlSha256", htmlSha256) //$NON-NLS-1$
                    .put("messagesSha256", messagesSha256) //$NON-NLS-1$
                    .put("manifestSha256", manifestSha256) //$NON-NLS-1$
                    .put("integritySha256", integritySha256) //$NON-NLS-1$
                    .put("originBindingSha256", originBindingSha256) //$NON-NLS-1$
                    .put("captureRootHash", captureRootHash) //$NON-NLS-1$
                    .put("contentRootHash", contentRootHash) //$NON-NLS-1$
                    .put("sourceItemName", sourceItemName) //$NON-NLS-1$
                    .put("sourceItemPath", sourceItemPath) //$NON-NLS-1$
                    .put("sourceItemSha256", sourceItemSha256) //$NON-NLS-1$
                    .put("sourceItemHash", sourceItemHash) //$NON-NLS-1$
                    .put("sourceDbPath", sourceDbPath) //$NON-NLS-1$
                    .put("sourceDbSha256", sourceDbSha256) //$NON-NLS-1$
                    .put("n3Status", n3Status) //$NON-NLS-1$
                    .put("startAnchor", startAnchor) //$NON-NLS-1$
                    .put("endAnchor", endAnchor) //$NON-NLS-1$
                    .put("censorshipEnabled", censorshipEnabled) //$NON-NLS-1$
                    .put("censorshipSimilarity", censorshipSimilarity) //$NON-NLS-1$
                    .put("censorshipBlur", censorshipBlur) //$NON-NLS-1$
                    .put("censorshipMatchedCount", censorshipMatchedCount) //$NON-NLS-1$
                    .put("annotationsEnabled", annotationsEnabled) //$NON-NLS-1$
                    .put("fileCount", fileCount) //$NON-NLS-1$
                    .put("totalBytes", totalBytes) //$NON-NLS-1$
                    .put("counts", counts.toJson()); //$NON-NLS-1$
        }

        static CaptureSnapshot fromJson(JSONObject o) {
            CaptureSnapshot s = new CaptureSnapshot();
            if (o == null) {
                return s;
            }
            s.folder = clamp(o.optString("folder", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.title = clamp(o.optString("title", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.chatId = clamp(o.optString("chatId", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.format = clamp(o.optString("format", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.status = clamp(o.optString("status", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.createdAt = clamp(o.optString("createdAt", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.lastSeenAt = clamp(o.optString("lastSeenAt", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.folderHash = clamp(o.optString("folderHash", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.htmlSha256 = clamp(o.optString("htmlSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.messagesSha256 = clamp(o.optString("messagesSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.manifestSha256 = clamp(o.optString("manifestSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.integritySha256 = clamp(o.optString("integritySha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.originBindingSha256 = clamp(o.optString("originBindingSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.captureRootHash = clamp(o.optString("captureRootHash", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.contentRootHash = clamp(o.optString("contentRootHash", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceItemName = clamp(o.optString("sourceItemName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceItemPath = clamp(o.optString("sourceItemPath", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceItemSha256 = clamp(o.optString("sourceItemSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceItemHash = clamp(o.optString("sourceItemHash", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceDbPath = clamp(o.optString("sourceDbPath", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.sourceDbSha256 = clamp(o.optString("sourceDbSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.n3Status = clamp(o.optString("n3Status", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.startAnchor = clamp(o.optString("startAnchor", ""), 256); //$NON-NLS-1$ //$NON-NLS-2$
            s.endAnchor = clamp(o.optString("endAnchor", ""), 256); //$NON-NLS-1$ //$NON-NLS-2$
            s.censorshipEnabled = o.optBoolean("censorshipEnabled", false); //$NON-NLS-1$
            s.censorshipSimilarity = o.optInt("censorshipSimilarity", 0); //$NON-NLS-1$
            s.censorshipBlur = o.optInt("censorshipBlur", 0); //$NON-NLS-1$
            s.censorshipMatchedCount = o.optInt("censorshipMatchedCount", 0); //$NON-NLS-1$
            s.annotationsEnabled = o.optBoolean("annotationsEnabled", false); //$NON-NLS-1$
            s.fileCount = o.optInt("fileCount", 0); //$NON-NLS-1$
            s.totalBytes = o.optLong("totalBytes", 0L); //$NON-NLS-1$
            s.counts = MediaCounts.fromJson(o.optJSONObject("counts")); //$NON-NLS-1$
            return s;
        }
    }

    static final class ExtractionStep {
        String timestampLocal = ""; //$NON-NLS-1$
        String timestampUtc = ""; //$NON-NLS-1$
        String operation = ""; //$NON-NLS-1$
        String captureFolder = ""; //$NON-NLS-1$
        String stage = ""; //$NON-NLS-1$
        String detail = ""; //$NON-NLS-1$

        ExtractionStep() {
        }

        static ExtractionStep now(String operation, String captureFolder, String stage, String detail) {
            ExtractionStep s = new ExtractionStep();
            OffsetDateTime localNow = OffsetDateTime.now(ZoneId.systemDefault());
            s.timestampLocal = formatLocalTimestamp(localNow);
            s.timestampUtc = localNow.withOffsetSameInstant(ZoneOffset.UTC).toString();
            s.operation = clamp(operation, 64);
            s.captureFolder = clamp(captureFolder, MAX_SHORT_FIELD);
            s.stage = clamp(stage, 128);
            s.detail = clamp(detail, MAX_LONG_FIELD);
            return s;
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("timestampLocal", timestampLocal) //$NON-NLS-1$
                    .put("timestampUtc", timestampUtc) //$NON-NLS-1$
                    .put("operation", operation) //$NON-NLS-1$
                    .put("captureFolder", captureFolder) //$NON-NLS-1$
                    .put("stage", stage) //$NON-NLS-1$
                    .put("detail", detail); //$NON-NLS-1$
        }

        static ExtractionStep fromJson(JSONObject o) {
            ExtractionStep s = new ExtractionStep();
            if (o == null) {
                return s;
            }
            s.timestampLocal = clamp(o.optString("timestampLocal", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
            s.timestampUtc = clamp(o.optString("timestampUtc", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
            s.operation = clamp(o.optString("operation", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
            s.captureFolder = clamp(o.optString("captureFolder", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            s.stage = clamp(o.optString("stage", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            s.detail = clamp(o.optString("detail", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            return s;
        }
    }

    // --- Device Identification ---
    String deviceManufacturer = ""; //$NON-NLS-1$
    String deviceModel = ""; //$NON-NLS-1$
    String deviceImei = ""; //$NON-NLS-1$
    String deviceSerialNumber = ""; //$NON-NLS-1$
    String deviceOsVersion = ""; //$NON-NLS-1$
    String whatsappVersion = ""; //$NON-NLS-1$
    String collectionCode = ""; //$NON-NLS-1$
    String originExtractionTool = ""; //$NON-NLS-1$
    String originStartExtraction = ""; //$NON-NLS-1$
    String originEndExtraction = ""; //$NON-NLS-1$
    final Set<String> fieldsExtractedFromOriginPdf = new LinkedHashSet<>();

    // --- Chain of Custody ---
    String collectedBy = ""; //$NON-NLS-1$
    String analyzedBy = ""; //$NON-NLS-1$
    String collectedAt = ""; //$NON-NLS-1$
    String analyzedAt = ""; //$NON-NLS-1$
    String custodyLocation = ""; //$NON-NLS-1$
    StorageType storageType = StorageType.PASTA;
    String storageCustomText = ""; //$NON-NLS-1$
    String storageSealReference = ""; //$NON-NLS-1$
    String caseNumber = ""; //$NON-NLS-1$
    String legalAuthorization = ""; //$NON-NLS-1$
    String victimsFullName = ""; //$NON-NLS-1$
    String suspectsFullName = ""; //$NON-NLS-1$
    String evidenceDestination = ""; //$NON-NLS-1$
    String storageResponsible = ""; //$NON-NLS-1$

    // --- HTML Report Metadata (relatorio.htm / caseinformation.htm) ---
    String organizationHeader = ""; //$NON-NLS-1$
    String reportNumber = ""; //$NON-NLS-1$
    String reportDate = ""; //$NON-NLS-1$
    String reportTitle = ""; //$NON-NLS-1$
    String signatories = ""; //$NON-NLS-1$
    String requestReference = ""; //$NON-NLS-1$
    String forensicProtocolNumber = ""; //$NON-NLS-1$
    String forensicProtocolDate = ""; //$NON-NLS-1$
    String examinedMaterial = ""; //$NON-NLS-1$

    // --- Origin report.pdf reproduced sections (read from origin report.pdf, not required in dialog) ---
    String originOperatorName = ""; //$NON-NLS-1$
    String originOperatorIdNumber = ""; //$NON-NLS-1$
    String originOperatorCompany = ""; //$NON-NLS-1$
    String originOperatorPosition = ""; //$NON-NLS-1$
    String originOperatorEmail = ""; //$NON-NLS-1$
    String originOperatorPhone = ""; //$NON-NLS-1$
    String originOperatorComments = ""; //$NON-NLS-1$
    String originOperatorCertification = ""; //$NON-NLS-1$
    String originSeizureOrigin = ""; //$NON-NLS-1$
    String originSeizureDate = ""; //$NON-NLS-1$
    String originSeizureTime = ""; //$NON-NLS-1$
    String originSeizureSealedBy = ""; //$NON-NLS-1$
    String originSeizureSealNumber = ""; //$NON-NLS-1$
    String originSeizureStoredWhere = ""; //$NON-NLS-1$
    String originSeizureStorageResponsible = ""; //$NON-NLS-1$
    String originSeizureSealedOn = ""; //$NON-NLS-1$
    String originSeizureReceivedOn = ""; //$NON-NLS-1$
    String originSeizureComments = ""; //$NON-NLS-1$
    String originDeclaredCalcDeviceTxtMd5 = ""; //$NON-NLS-1$
    String originDeclaredCalcDeviceTxtSha256 = ""; //$NON-NLS-1$
    String originDeclaredCalcDeviceAvillaMd5 = ""; //$NON-NLS-1$
    String originDeclaredCalcDeviceAvillaSha256 = ""; //$NON-NLS-1$
    String originDeclaredHashsTxtMd5 = ""; //$NON-NLS-1$
    String originDeclaredHashsTxtSha256 = ""; //$NON-NLS-1$
    String originDeclaredHashsAvillaMd5 = ""; //$NON-NLS-1$
    String originDeclaredHashsAvillaSha256 = ""; //$NON-NLS-1$
    String originMethodUsedText = ""; //$NON-NLS-1$

    // --- Tool & Parameters ---
    String ipedVersion = "4.3.1"; //$NON-NLS-1$
    String pluginVersion = HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION;
    String extractionConfig = "Formato html-message-v1 (apresentacao v2), verificacao SHA-256 e cadeia de integridade"; //$NON-NLS-1$
    String appliedFilters = ""; //$NON-NLS-1$
    String originFolderName = ""; //$NON-NLS-1$
    String parentIpedFolderName = ""; //$NON-NLS-1$
    boolean copyOriginReportPdf = true;
    boolean generateUnifiedPdf = false;

    // --- Origin report.pdf and custody files metadata ---
    boolean originReportPdfPresent;
    boolean originReportPdfCopied;
    String originReportPdfSha256 = ""; //$NON-NLS-1$
    String originReportPdfMd5 = ""; //$NON-NLS-1$
    String originReportPdfCopiedSha256 = ""; //$NON-NLS-1$
    String originReportPdfParseStatus = "nao_verificado"; //$NON-NLS-1$
    String originHashsTxtSha256 = ""; //$NON-NLS-1$
    String originHashsAvillaSha256 = ""; //$NON-NLS-1$
    String originMsgstorePath = ""; //$NON-NLS-1$
    String originMsgstoreSha256 = ""; //$NON-NLS-1$
    String originWaDbSha256 = ""; //$NON-NLS-1$
    String originKeySha256 = ""; //$NON-NLS-1$

    // --- Chained Log.txt Anchor & Incident History ---
    String logCreatedAtLocal = ""; //$NON-NLS-1$
    String logCreatedAtUtc = ""; //$NON-NLS-1$
    int logAnchorLastLine = -1;
    String logAnchorPrefixSha256 = ""; //$NON-NLS-1$
    String logAnchorHeadHash = ""; //$NON-NLS-1$
    long logFileBytes = 0L;
    String logIntegrityStatus = "NAO_INICIADO"; //$NON-NLS-1$
    final List<String> logIncidents = new ArrayList<>();

    // --- Metadata & History ---
    String createdAtLocal = ""; //$NON-NLS-1$
    String updatedAtLocal = ""; //$NON-NLS-1$
    String updatedAtUtc = ""; //$NON-NLS-1$
    final Map<String, CaptureSnapshot> knownCaptures = new LinkedHashMap<>();
    final List<ExtractionStep> extractionSteps = new ArrayList<>();

    CustodyCaseRecord() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        this.createdAtLocal = formatLocalTimestamp(now);
        this.analyzedAt = formatLocalTimestamp(now);
    }

    static String formatLocalTimestamp(OffsetDateTime dt) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dt.withNano(0));
    }

    static String nowLocalTimestamp() {
        return formatLocalTimestamp(OffsetDateTime.now(ZoneId.systemDefault()));
    }

    static String nowUtcTimestamp() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString();
    }

    String formattedStorageDescription() {
        if (storageType == StorageType.CUSTOM) {
            String custom = storageCustomText == null ? "" : storageCustomText.trim(); //$NON-NLS-1$
            return custom.isEmpty() ? "Custom (nao especificado)" : "Custom: " + custom; //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (storageType == StorageType.MIDIA_LACRADA) {
            String seal = storageSealReference == null ? "" : storageSealReference.trim(); //$NON-NLS-1$
            return seal.isEmpty() ? StorageType.MIDIA_LACRADA.displayLabel
                    : StorageType.MIDIA_LACRADA.displayLabel + " (Lacre/Ref.: " + seal + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        String seal = storageSealReference == null ? "" : storageSealReference.trim(); //$NON-NLS-1$
        return seal.isEmpty() ? StorageType.PASTA.displayLabel
                : StorageType.PASTA.displayLabel + " (Ref.: " + seal + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    void addStep(String operation, String captureFolder, String stage, String detail) {
        extractionSteps.add(ExtractionStep.now(operation, captureFolder, stage, detail));
        while (extractionSteps.size() > MAX_STEPS) {
            extractionSteps.remove(0);
        }
    }

    void mergeFromOriginPreview(OriginCustodyBinder.OriginReportPreview preview, boolean overwriteExisting) {
        if (preview == null) {
            return;
        }
        this.originReportPdfPresent = preview.reportPdfPresent;
        this.originReportPdfSha256 = clamp(preview.reportPdfSha256, 128);
        this.originReportPdfMd5 = clamp(preview.reportPdfMd5, 128);
        this.originReportPdfParseStatus = clamp(preview.reportPdfParseStatus, 128);
        if (!preview.originFolderName.isEmpty()) {
            this.originFolderName = clamp(preview.originFolderName, MAX_SHORT_FIELD);
        }
        if (!preview.hashsTxtSha256.isEmpty()) {
            this.originHashsTxtSha256 = clamp(preview.hashsTxtSha256, 128);
        }
        if (!preview.hashsAvillaSha256.isEmpty()) {
            this.originHashsAvillaSha256 = clamp(preview.hashsAvillaSha256, 128);
        }
        if (!preview.msgstoreRelPath.isEmpty()) {
            this.originMsgstorePath = clamp(preview.msgstoreRelPath, MAX_LONG_FIELD);
        }
        if (!preview.msgstoreSha256.isEmpty()) {
            this.originMsgstoreSha256 = clamp(preview.msgstoreSha256, 128);
        }
        if (!preview.waDbSha256.isEmpty()) {
            this.originWaDbSha256 = clamp(preview.waDbSha256, 128);
        }
        if (!preview.keySha256.isEmpty()) {
            this.originKeySha256 = clamp(preview.keySha256, 128);
        }

        if (preview.reportPdfPresent) {
            if (!preview.manufacturer.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("deviceManufacturer"); //$NON-NLS-1$
                if (overwriteExisting || deviceManufacturer.isBlank()) {
                    deviceManufacturer = clamp(preview.manufacturer, MAX_SHORT_FIELD);
                }
            }
            if (!preview.model.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("deviceModel"); //$NON-NLS-1$
                if (overwriteExisting || deviceModel.isBlank()) {
                    deviceModel = clamp(preview.model, MAX_SHORT_FIELD);
                }
            }
            if (!preview.imei.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("deviceImei"); //$NON-NLS-1$
                if (overwriteExisting || deviceImei.isBlank()) {
                    deviceImei = clamp(preview.imei, MAX_SHORT_FIELD);
                }
            }
            if (!preview.serialNumber.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("deviceSerialNumber"); //$NON-NLS-1$
                if (overwriteExisting || deviceSerialNumber.isBlank()) {
                    deviceSerialNumber = clamp(preview.serialNumber, MAX_SHORT_FIELD);
                }
            }
            if (!preview.osVersion.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("deviceOsVersion"); //$NON-NLS-1$
                if (overwriteExisting || deviceOsVersion.isBlank()) {
                    deviceOsVersion = clamp(preview.osVersion, MAX_SHORT_FIELD);
                }
            }
            if (!preview.whatsappVersion.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("whatsappVersion"); //$NON-NLS-1$
                if (overwriteExisting || whatsappVersion.isBlank()) {
                    whatsappVersion = clamp(preview.whatsappVersion, MAX_SHORT_FIELD);
                }
            }
            if (!preview.collectionCode.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("collectionCode"); //$NON-NLS-1$
                if (overwriteExisting || collectionCode.isBlank()) {
                    collectionCode = clamp(preview.collectionCode, MAX_SHORT_FIELD);
                }
            }
            if (!preview.extractionTool.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("originExtractionTool"); //$NON-NLS-1$
                if (overwriteExisting || originExtractionTool.isBlank()) {
                    originExtractionTool = clamp(preview.extractionTool, MAX_SHORT_FIELD);
                }
            }
            if (!preview.startExtraction.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("originStartExtraction"); //$NON-NLS-1$
                if (overwriteExisting || originStartExtraction.isBlank()) {
                    originStartExtraction = clamp(preview.startExtraction, 128);
                }
                if (overwriteExisting || collectedAt.isBlank()) {
                    collectedAt = clamp(preview.startExtraction
                            + (preview.endExtraction.isEmpty() ? "" : " a " + preview.endExtraction), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            if (!preview.endExtraction.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("originEndExtraction"); //$NON-NLS-1$
                if (overwriteExisting || originEndExtraction.isBlank()) {
                    originEndExtraction = clamp(preview.endExtraction, 128);
                }
            }
            if (!preview.examinerOrCollector.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("collectedBy"); //$NON-NLS-1$
                if (overwriteExisting || collectedBy.isBlank()) {
                    collectedBy = clamp(preview.examinerOrCollector, MAX_SHORT_FIELD);
                }
            }
            if (!preview.operatorName.isEmpty()) originOperatorName = clamp(preview.operatorName, MAX_SHORT_FIELD);
            if (!preview.operatorIdNumber.isEmpty()) originOperatorIdNumber = clamp(preview.operatorIdNumber, MAX_SHORT_FIELD);
            if (!preview.operatorCompany.isEmpty()) {
                originOperatorCompany = clamp(preview.operatorCompany, MAX_SHORT_FIELD);
                if (overwriteExisting || organizationHeader.isBlank()) {
                    organizationHeader = originOperatorCompany;
                }
            }
            if (!preview.operatorPosition.isEmpty()) originOperatorPosition = clamp(preview.operatorPosition, MAX_SHORT_FIELD);
            if (!preview.operatorEmail.isEmpty()) originOperatorEmail = clamp(preview.operatorEmail, MAX_SHORT_FIELD);
            if (!preview.operatorPhone.isEmpty()) originOperatorPhone = clamp(preview.operatorPhone, MAX_SHORT_FIELD);
            if (!preview.operatorComments.isEmpty()) originOperatorComments = clamp(preview.operatorComments, MAX_LONG_FIELD);
            if (!preview.operatorCertification.isEmpty()) originOperatorCertification = clamp(preview.operatorCertification, MAX_SHORT_FIELD);

            if (!preview.seizureOrigin.isEmpty()) originSeizureOrigin = clamp(preview.seizureOrigin, MAX_SHORT_FIELD);
            if (!preview.seizureDate.isEmpty()) originSeizureDate = clamp(preview.seizureDate, MAX_SHORT_FIELD);
            if (!preview.seizureTime.isEmpty()) originSeizureTime = clamp(preview.seizureTime, MAX_SHORT_FIELD);
            if (!preview.seizureSealedBy.isEmpty()) originSeizureSealedBy = clamp(preview.seizureSealedBy, MAX_SHORT_FIELD);
            if (!preview.seizureSealNumber.isEmpty()) originSeizureSealNumber = clamp(preview.seizureSealNumber, MAX_SHORT_FIELD);
            if (!preview.seizureStoredWhere.isEmpty()) originSeizureStoredWhere = clamp(preview.seizureStoredWhere, MAX_SHORT_FIELD);
            if (!preview.seizureStorageResponsible.isEmpty()) originSeizureStorageResponsible = clamp(preview.seizureStorageResponsible, MAX_SHORT_FIELD);
            if (!preview.seizureSealedOn.isEmpty()) originSeizureSealedOn = clamp(preview.seizureSealedOn, MAX_SHORT_FIELD);
            if (!preview.seizureReceivedOn.isEmpty()) originSeizureReceivedOn = clamp(preview.seizureReceivedOn, MAX_SHORT_FIELD);
            if (!preview.seizureComments.isEmpty()) originSeizureComments = clamp(preview.seizureComments, MAX_LONG_FIELD);

            if (!preview.caseNumber.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("caseNumber"); //$NON-NLS-1$
                if (overwriteExisting || caseNumber.isBlank()) {
                    caseNumber = clamp(preview.caseNumber, MAX_SHORT_FIELD);
                }
            }
            if (!preview.legalAuthorization.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("legalAuthorization"); //$NON-NLS-1$
                if (overwriteExisting || legalAuthorization.isBlank()) {
                    legalAuthorization = clamp(preview.legalAuthorization, MAX_SHORT_FIELD);
                }
            }
            if (!preview.victimsFullName.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("victimsFullName"); //$NON-NLS-1$
                if (overwriteExisting || victimsFullName.isBlank()) {
                    victimsFullName = clamp(preview.victimsFullName, MAX_SHORT_FIELD);
                }
            }
            if (!preview.suspectsFullName.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("suspectsFullName"); //$NON-NLS-1$
                if (overwriteExisting || suspectsFullName.isBlank()) {
                    suspectsFullName = clamp(preview.suspectsFullName, MAX_SHORT_FIELD);
                }
            }
            if (!preview.evidenceDestination.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("evidenceDestination"); //$NON-NLS-1$
                if (overwriteExisting || evidenceDestination.isBlank()) {
                    evidenceDestination = clamp(preview.evidenceDestination, MAX_SHORT_FIELD);
                }
            }
            String bestSeal = !preview.custodySealNumber.isEmpty() ? preview.custodySealNumber : preview.seizureSealNumber;
            if (!bestSeal.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("storageSealReference"); //$NON-NLS-1$
                if (overwriteExisting || storageSealReference.isBlank()) {
                    storageSealReference = clamp(bestSeal, MAX_SHORT_FIELD);
                }
            }
            String bestResponsible = !preview.custodyStorageResponsible.isEmpty()
                    ? preview.custodyStorageResponsible
                    : preview.seizureStorageResponsible;
            if (!bestResponsible.isEmpty()) {
                fieldsExtractedFromOriginPdf.add("storageResponsible"); //$NON-NLS-1$
                if (overwriteExisting || storageResponsible.isBlank()) {
                    storageResponsible = clamp(bestResponsible, MAX_SHORT_FIELD);
                }
            }

            if (!preview.declaredCalcDeviceTxtMd5.isEmpty()) originDeclaredCalcDeviceTxtMd5 = clamp(preview.declaredCalcDeviceTxtMd5, 128);
            if (!preview.declaredCalcDeviceTxtSha256.isEmpty()) originDeclaredCalcDeviceTxtSha256 = clamp(preview.declaredCalcDeviceTxtSha256, 128);
            if (!preview.declaredCalcDeviceAvillaMd5.isEmpty()) originDeclaredCalcDeviceAvillaMd5 = clamp(preview.declaredCalcDeviceAvillaMd5, 128);
            if (!preview.declaredCalcDeviceAvillaSha256.isEmpty()) originDeclaredCalcDeviceAvillaSha256 = clamp(preview.declaredCalcDeviceAvillaSha256, 128);
            if (!preview.declaredHashsTxtMd5.isEmpty()) originDeclaredHashsTxtMd5 = clamp(preview.declaredHashsTxtMd5, 128);
            if (!preview.declaredHashsTxtSha256.isEmpty()) originDeclaredHashsTxtSha256 = clamp(preview.declaredHashsTxtSha256, 128);
            if (!preview.declaredHashsAvillaMd5.isEmpty()) originDeclaredHashsAvillaMd5 = clamp(preview.declaredHashsAvillaMd5, 128);
            if (!preview.declaredHashsAvillaSha256.isEmpty()) originDeclaredHashsAvillaSha256 = clamp(preview.declaredHashsAvillaSha256, 128);
            if (!preview.methodUsedText.isEmpty()) originMethodUsedText = clamp(preview.methodUsedText, MAX_METHOD_TEXT);
        }
    }

    String provenanceTag(String fieldKey, String currentValue) {
        if (currentValue == null || currentValue.isBlank()) {
            return originReportPdfPresent
                    ? "Ausente no report.pdf original e nao informado" //$NON-NLS-1$
                    : "Nao informado"; //$NON-NLS-1$
        }
        if (originReportPdfPresent && fieldsExtractedFromOriginPdf.contains(fieldKey)) {
            return "Lido do report.pdf original (conferido no plugin)"; //$NON-NLS-1$
        }
        if (originReportPdfPresent) {
            return "Faltava no report.pdf — complementado pelo plugin/analista"; //$NON-NLS-1$
        }
        return "Registrado pelo plugin/analista"; //$NON-NLS-1$
    }

    static CustodyCaseRecord loadOrCreate(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            return new CustodyCaseRecord();
        }
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, LAUDO_DIR_NAME);
        Path jsonPath = CaptureIntegrity.resolveContained(laudoDir, CUSTODY_JSON_FILE);
        if (!Files.exists(jsonPath, LinkOption.NOFOLLOW_LINKS)) {
            return new CustodyCaseRecord();
        }
        if (CaptureIntegrity.isLinkOrReparse(laudoDir) || CaptureIntegrity.isLinkOrReparse(jsonPath)
                || !Files.isRegularFile(jsonPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo de custodia invalido ou link/juncao nao permitido: " + jsonPath); //$NON-NLS-1$
        }
        String raw = CaptureIntegrity.readJsonUtf8Limited(jsonPath);
        return fromJson(new JSONObject(raw));
    }

    void save(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            throw new IOException("Pasta de saida nula para salvar custodia do laudo."); //$NON-NLS-1$
        }
        Path laudoDir = CaptureIntegrity.resolveContained(outputRoot, LAUDO_DIR_NAME);
        CaptureIntegrity.createDirectoriesSafe(outputRoot, laudoDir);
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        if (createdAtLocal == null || createdAtLocal.isBlank()) {
            createdAtLocal = formatLocalTimestamp(now);
        }
        updatedAtLocal = formatLocalTimestamp(now);
        updatedAtUtc = now.withOffsetSameInstant(ZoneOffset.UTC).toString();
        pluginVersion = HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION;
        Path jsonPath = CaptureIntegrity.resolveContained(laudoDir, CUSTODY_JSON_FILE);
        CaptureIntegrity.writeAtomically(outputRoot, jsonPath, toJson().toString(2), CaptureIntegrity.MAX_JSON_BYTES);
    }

    JSONObject toJson() {
        JSONObject device = new JSONObject()
                .put("manufacturer", clamp(deviceManufacturer, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("model", clamp(deviceModel, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("imei", clamp(deviceImei, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("serialNumber", clamp(deviceSerialNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("osVersion", clamp(deviceOsVersion, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("whatsappVersion", clamp(whatsappVersion, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("collectionCode", clamp(collectionCode, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("originExtractionTool", clamp(originExtractionTool, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("originStartExtraction", clamp(originStartExtraction, 128)) //$NON-NLS-1$
                .put("originEndExtraction", clamp(originEndExtraction, 128)) //$NON-NLS-1$
                .put("fieldsExtractedFromOriginPdf", new JSONArray(fieldsExtractedFromOriginPdf)); //$NON-NLS-1$

        JSONObject custody = new JSONObject()
                .put("collectedBy", clamp(collectedBy, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("analyzedBy", clamp(analyzedBy, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("collectedAt", clamp(collectedAt, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("analyzedAt", clamp(analyzedAt, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("location", clamp(custodyLocation, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("storageType", storageType == null ? StorageType.PASTA.code : storageType.code) //$NON-NLS-1$
                .put("storageCustomText", clamp(storageCustomText, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("storageSealReference", clamp(storageSealReference, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("storageDisplay", formattedStorageDescription()) //$NON-NLS-1$
                .put("caseNumber", clamp(caseNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("legalAuthorization", clamp(legalAuthorization, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("victimsFullName", clamp(victimsFullName, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("suspectsFullName", clamp(suspectsFullName, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("evidenceDestination", clamp(evidenceDestination, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("storageResponsible", clamp(storageResponsible, MAX_SHORT_FIELD)); //$NON-NLS-1$

        JSONObject htmlReport = new JSONObject()
                .put("organizationHeader", clamp(organizationHeader, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("reportNumber", clamp(reportNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("reportDate", clamp(reportDate, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("reportTitle", clamp(reportTitle, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("signatories", clamp(signatories, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("requestReference", clamp(requestReference, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("forensicProtocolNumber", clamp(forensicProtocolNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("forensicProtocolDate", clamp(forensicProtocolDate, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("examinedMaterial", clamp(examinedMaterial, MAX_LONG_FIELD)); //$NON-NLS-1$

        JSONObject tool = new JSONObject()
                .put("ipedVersion", clamp(ipedVersion, 64)) //$NON-NLS-1$
                .put("pluginVersion", clamp(pluginVersion, 64)) //$NON-NLS-1$
                .put("extractionConfig", clamp(extractionConfig, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("appliedFilters", clamp(appliedFilters, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("originFolderName", clamp(originFolderName, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("parentIpedFolderName", clamp(parentIpedFolderName, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("copyOriginReportPdf", copyOriginReportPdf) //$NON-NLS-1$
                .put("generateUnifiedPdf", generateUnifiedPdf); //$NON-NLS-1$

        JSONObject originPdf = new JSONObject()
                .put("present", originReportPdfPresent) //$NON-NLS-1$
                .put("copied", originReportPdfCopied) //$NON-NLS-1$
                .put("sourceSha256", clamp(originReportPdfSha256, 128)) //$NON-NLS-1$
                .put("sourceMd5", clamp(originReportPdfMd5, 128)) //$NON-NLS-1$
                .put("copiedSha256", clamp(originReportPdfCopiedSha256, 128)) //$NON-NLS-1$
                .put("parseStatus", clamp(originReportPdfParseStatus, 128)) //$NON-NLS-1$
                .put("hashsTxtSha256", clamp(originHashsTxtSha256, 128)) //$NON-NLS-1$
                .put("hashsAvillaSha256", clamp(originHashsAvillaSha256, 128)) //$NON-NLS-1$
                .put("msgstorePath", clamp(originMsgstorePath, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("msgstoreSha256", clamp(originMsgstoreSha256, 128)) //$NON-NLS-1$
                .put("waDbSha256", clamp(originWaDbSha256, 128)) //$NON-NLS-1$
                .put("keySha256", clamp(originKeySha256, 128)) //$NON-NLS-1$
                .put("operatorName", clamp(originOperatorName, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorIdNumber", clamp(originOperatorIdNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorCompany", clamp(originOperatorCompany, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorPosition", clamp(originOperatorPosition, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorEmail", clamp(originOperatorEmail, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorPhone", clamp(originOperatorPhone, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("operatorComments", clamp(originOperatorComments, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("operatorCertification", clamp(originOperatorCertification, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureOrigin", clamp(originSeizureOrigin, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureDate", clamp(originSeizureDate, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureTime", clamp(originSeizureTime, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureSealedBy", clamp(originSeizureSealedBy, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureSealNumber", clamp(originSeizureSealNumber, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureStoredWhere", clamp(originSeizureStoredWhere, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureStorageResponsible", clamp(originSeizureStorageResponsible, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureSealedOn", clamp(originSeizureSealedOn, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureReceivedOn", clamp(originSeizureReceivedOn, MAX_SHORT_FIELD)) //$NON-NLS-1$
                .put("seizureComments", clamp(originSeizureComments, MAX_LONG_FIELD)) //$NON-NLS-1$
                .put("declaredCalcDeviceTxtMd5", clamp(originDeclaredCalcDeviceTxtMd5, 128)) //$NON-NLS-1$
                .put("declaredCalcDeviceTxtSha256", clamp(originDeclaredCalcDeviceTxtSha256, 128)) //$NON-NLS-1$
                .put("declaredCalcDeviceAvillaMd5", clamp(originDeclaredCalcDeviceAvillaMd5, 128)) //$NON-NLS-1$
                .put("declaredCalcDeviceAvillaSha256", clamp(originDeclaredCalcDeviceAvillaSha256, 128)) //$NON-NLS-1$
                .put("declaredHashsTxtMd5", clamp(originDeclaredHashsTxtMd5, 128)) //$NON-NLS-1$
                .put("declaredHashsTxtSha256", clamp(originDeclaredHashsTxtSha256, 128)) //$NON-NLS-1$
                .put("declaredHashsAvillaMd5", clamp(originDeclaredHashsAvillaMd5, 128)) //$NON-NLS-1$
                .put("declaredHashsAvillaSha256", clamp(originDeclaredHashsAvillaSha256, 128)) //$NON-NLS-1$
                .put("methodUsedText", clamp(originMethodUsedText, MAX_METHOD_TEXT)); //$NON-NLS-1$

        JSONObject knownObj = new JSONObject();
        for (Map.Entry<String, CaptureSnapshot> e : knownCaptures.entrySet()) {
            knownObj.put(e.getKey(), e.getValue().toJson());
        }

        JSONArray stepsArr = new JSONArray();
        for (ExtractionStep step : extractionSteps) {
            stepsArr.put(step.toJson());
        }

        JSONArray incidentsArr = new JSONArray();
        for (String inc : logIncidents) {
            incidentsArr.put(clamp(inc, MAX_LONG_FIELD));
        }
        JSONObject logAnchor = new JSONObject()
                .put("file", LOG_TXT_FILE) //$NON-NLS-1$
                .put("createdAtLocal", clamp(logCreatedAtLocal, 64)) //$NON-NLS-1$
                .put("createdAtUtc", clamp(logCreatedAtUtc, 64)) //$NON-NLS-1$
                .put("lastLineIndex", logAnchorLastLine) //$NON-NLS-1$
                .put("prefixSha256", clamp(logAnchorPrefixSha256, 128)) //$NON-NLS-1$
                .put("headHash", clamp(logAnchorHeadHash, 128)) //$NON-NLS-1$
                .put("byteLength", Math.max(0L, logFileBytes)) //$NON-NLS-1$
                .put("integrityStatus", clamp(logIntegrityStatus, 128)) //$NON-NLS-1$
                .put("incidents", incidentsArr); //$NON-NLS-1$

        return new JSONObject()
                .put("schemaVersion", SCHEMA_VERSION) //$NON-NLS-1$
                .put("createdAtLocal", clamp(createdAtLocal, 64)) //$NON-NLS-1$
                .put("updatedAtLocal", clamp(updatedAtLocal, 64)) //$NON-NLS-1$
                .put("updatedAtUtc", clamp(updatedAtUtc, 64)) //$NON-NLS-1$
                .put("device", device) //$NON-NLS-1$
                .put("chainOfCustody", custody) //$NON-NLS-1$
                .put("htmlReportMetadata", htmlReport) //$NON-NLS-1$
                .put("toolAndParameters", tool) //$NON-NLS-1$
                .put("originReport", originPdf) //$NON-NLS-1$
                .put("chronologicalLog", logAnchor) //$NON-NLS-1$
                .put("knownCaptures", knownObj) //$NON-NLS-1$
                .put("extractionSteps", stepsArr); //$NON-NLS-1$
    }

    static CustodyCaseRecord fromJson(JSONObject root) {
        CustodyCaseRecord r = new CustodyCaseRecord();
        if (root == null) {
            return r;
        }
        r.createdAtLocal = clamp(root.optString("createdAtLocal", r.createdAtLocal), 64); //$NON-NLS-1$
        r.updatedAtLocal = clamp(root.optString("updatedAtLocal", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
        r.updatedAtUtc = clamp(root.optString("updatedAtUtc", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$

        JSONObject dev = root.optJSONObject("device"); //$NON-NLS-1$
        if (dev != null) {
            r.deviceManufacturer = clamp(dev.optString("manufacturer", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.deviceModel = clamp(dev.optString("model", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.deviceImei = clamp(dev.optString("imei", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.deviceSerialNumber = clamp(dev.optString("serialNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.deviceOsVersion = clamp(dev.optString("osVersion", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.whatsappVersion = clamp(dev.optString("whatsappVersion", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.collectionCode = clamp(dev.optString("collectionCode", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originExtractionTool = clamp(dev.optString("originExtractionTool", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originStartExtraction = clamp(dev.optString("originStartExtraction", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originEndExtraction = clamp(dev.optString("originEndExtraction", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            JSONArray fromPdf = dev.optJSONArray("fieldsExtractedFromOriginPdf"); //$NON-NLS-1$
            if (fromPdf != null) {
                for (int i = 0; i < fromPdf.length(); i++) {
                    String k = fromPdf.optString(i, "").trim(); //$NON-NLS-1$
                    if (!k.isEmpty()) {
                        r.fieldsExtractedFromOriginPdf.add(k);
                    }
                }
            }
        }

        JSONObject cus = root.optJSONObject("chainOfCustody"); //$NON-NLS-1$
        if (cus != null) {
            r.collectedBy = clamp(cus.optString("collectedBy", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.analyzedBy = clamp(cus.optString("analyzedBy", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.collectedAt = clamp(cus.optString("collectedAt", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.analyzedAt = clamp(cus.optString("analyzedAt", r.analyzedAt), MAX_SHORT_FIELD); //$NON-NLS-1$
            r.custodyLocation = clamp(cus.optString("location", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.storageType = StorageType.fromCode(cus.optString("storageType", StorageType.PASTA.code)); //$NON-NLS-1$
            r.storageCustomText = clamp(cus.optString("storageCustomText", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.storageSealReference = clamp(cus.optString("storageSealReference", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.caseNumber = clamp(cus.optString("caseNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.legalAuthorization = clamp(cus.optString("legalAuthorization", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.victimsFullName = clamp(cus.optString("victimsFullName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.suspectsFullName = clamp(cus.optString("suspectsFullName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.evidenceDestination = clamp(cus.optString("evidenceDestination", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.storageResponsible = clamp(cus.optString("storageResponsible", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JSONObject htmlMeta = root.optJSONObject("htmlReportMetadata"); //$NON-NLS-1$
        if (htmlMeta != null) {
            r.organizationHeader = clamp(htmlMeta.optString("organizationHeader", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.reportNumber = clamp(htmlMeta.optString("reportNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.reportDate = clamp(htmlMeta.optString("reportDate", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.reportTitle = clamp(htmlMeta.optString("reportTitle", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.signatories = clamp(htmlMeta.optString("signatories", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.requestReference = clamp(htmlMeta.optString("requestReference", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.forensicProtocolNumber = clamp(htmlMeta.optString("forensicProtocolNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.forensicProtocolDate = clamp(htmlMeta.optString("forensicProtocolDate", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.examinedMaterial = clamp(htmlMeta.optString("examinedMaterial", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JSONObject tool = root.optJSONObject("toolAndParameters"); //$NON-NLS-1$
        if (tool != null) {
            r.ipedVersion = clamp(tool.optString("ipedVersion", "4.3.1"), 64); //$NON-NLS-1$ //$NON-NLS-2$
            r.pluginVersion = clamp(tool.optString("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION), 64); //$NON-NLS-1$
            r.extractionConfig = clamp(tool.optString("extractionConfig", r.extractionConfig), MAX_LONG_FIELD); //$NON-NLS-1$
            r.appliedFilters = clamp(tool.optString("appliedFilters", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originFolderName = clamp(tool.optString("originFolderName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.parentIpedFolderName = clamp(tool.optString("parentIpedFolderName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.copyOriginReportPdf = tool.optBoolean("copyOriginReportPdf", true); //$NON-NLS-1$
            r.generateUnifiedPdf = tool.optBoolean("generateUnifiedPdf", false); //$NON-NLS-1$
        }

        JSONObject orig = root.optJSONObject("originReport"); //$NON-NLS-1$
        if (orig != null) {
            r.originReportPdfPresent = orig.optBoolean("present", false); //$NON-NLS-1$
            r.originReportPdfCopied = orig.optBoolean("copied", false); //$NON-NLS-1$
            r.originReportPdfSha256 = clamp(orig.optString("sourceSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originReportPdfMd5 = clamp(orig.optString("sourceMd5", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originReportPdfCopiedSha256 = clamp(orig.optString("copiedSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originReportPdfParseStatus = clamp(orig.optString("parseStatus", "nao_verificado"), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originHashsTxtSha256 = clamp(orig.optString("hashsTxtSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originHashsAvillaSha256 = clamp(orig.optString("hashsAvillaSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originMsgstorePath = clamp(orig.optString("msgstorePath", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originMsgstoreSha256 = clamp(orig.optString("msgstoreSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originWaDbSha256 = clamp(orig.optString("waDbSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originKeySha256 = clamp(orig.optString("keySha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorName = clamp(orig.optString("operatorName", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorIdNumber = clamp(orig.optString("operatorIdNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorCompany = clamp(orig.optString("operatorCompany", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorPosition = clamp(orig.optString("operatorPosition", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorEmail = clamp(orig.optString("operatorEmail", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorPhone = clamp(orig.optString("operatorPhone", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorComments = clamp(orig.optString("operatorComments", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originOperatorCertification = clamp(orig.optString("operatorCertification", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureOrigin = clamp(orig.optString("seizureOrigin", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureDate = clamp(orig.optString("seizureDate", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureTime = clamp(orig.optString("seizureTime", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureSealedBy = clamp(orig.optString("seizureSealedBy", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureSealNumber = clamp(orig.optString("seizureSealNumber", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureStoredWhere = clamp(orig.optString("seizureStoredWhere", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureStorageResponsible = clamp(orig.optString("seizureStorageResponsible", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureSealedOn = clamp(orig.optString("seizureSealedOn", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureReceivedOn = clamp(orig.optString("seizureReceivedOn", ""), MAX_SHORT_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originSeizureComments = clamp(orig.optString("seizureComments", ""), MAX_LONG_FIELD); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredCalcDeviceTxtMd5 = clamp(orig.optString("declaredCalcDeviceTxtMd5", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredCalcDeviceTxtSha256 = clamp(orig.optString("declaredCalcDeviceTxtSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredCalcDeviceAvillaMd5 = clamp(orig.optString("declaredCalcDeviceAvillaMd5", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredCalcDeviceAvillaSha256 = clamp(orig.optString("declaredCalcDeviceAvillaSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredHashsTxtMd5 = clamp(orig.optString("declaredHashsTxtMd5", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredHashsTxtSha256 = clamp(orig.optString("declaredHashsTxtSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredHashsAvillaMd5 = clamp(orig.optString("declaredHashsAvillaMd5", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originDeclaredHashsAvillaSha256 = clamp(orig.optString("declaredHashsAvillaSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.originMethodUsedText = clamp(orig.optString("methodUsedText", ""), MAX_METHOD_TEXT); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JSONObject logObj = root.optJSONObject("chronologicalLog"); //$NON-NLS-1$
        if (logObj != null) {
            r.logCreatedAtLocal = clamp(logObj.optString("createdAtLocal", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
            r.logCreatedAtUtc = clamp(logObj.optString("createdAtUtc", ""), 64); //$NON-NLS-1$ //$NON-NLS-2$
            r.logAnchorLastLine = logObj.optInt("lastLineIndex", -1); //$NON-NLS-1$
            r.logAnchorPrefixSha256 = clamp(logObj.optString("prefixSha256", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.logAnchorHeadHash = clamp(logObj.optString("headHash", ""), 128); //$NON-NLS-1$ //$NON-NLS-2$
            r.logFileBytes = Math.max(0L, logObj.optLong("byteLength", 0L)); //$NON-NLS-1$
            r.logIntegrityStatus = clamp(logObj.optString("integrityStatus", "NAO_INICIADO"), 128); //$NON-NLS-1$ //$NON-NLS-2$
            JSONArray incArr = logObj.optJSONArray("incidents"); //$NON-NLS-1$
            if (incArr != null) {
                for (int i = 0; i < incArr.length() && i < 500; i++) {
                    String s = clamp(incArr.optString(i, ""), MAX_LONG_FIELD); //$NON-NLS-1$
                    if (!s.isEmpty()) {
                        r.logIncidents.add(s);
                    }
                }
            }
        }

        JSONObject knownObj = root.optJSONObject("knownCaptures"); //$NON-NLS-1$
        if (knownObj != null) {
            List<String> keys = new ArrayList<>(knownObj.keySet());
            keys.sort(String.CASE_INSENSITIVE_ORDER);
            for (String k : keys) {
                CaptureSnapshot snap = CaptureSnapshot.fromJson(knownObj.optJSONObject(k));
                if (!snap.folder.isEmpty()) {
                    r.knownCaptures.put(snap.folder, snap);
                }
            }
        }

        JSONArray stepsArr = root.optJSONArray("extractionSteps"); //$NON-NLS-1$
        if (stepsArr != null) {
            int start = Math.max(0, stepsArr.length() - MAX_STEPS);
            for (int i = start; i < stepsArr.length(); i++) {
                r.extractionSteps.add(ExtractionStep.fromJson(stepsArr.optJSONObject(i)));
            }
        }
        return r;
    }

    static String clamp(String value, int maxChars) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        String clean = value.replace('\0', ' ').trim();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars);
    }
}
