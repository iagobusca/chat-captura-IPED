package iped.viewers;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import iped.parsers.threema.ThreemaParser;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import iped.data.IItem;
import iped.data.IItemId;
import iped.data.SelectionListener;
import iped.io.IStreamSource;
import iped.parsers.discord.DiscordParser;
import iped.parsers.mail.win10.Win10MailParser;
import iped.parsers.shareaza.ShareazaDownloadParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.util.Util;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.utils.IOUtil;
import iped.viewers.api.AttachmentSearcher;
import iped.viewers.localization.Messages;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import netscape.javascript.JSObject;

/**
 * Visualizador Html específico que abre links apontando para arquivos do caso,
 * como anexos transferidos em chats ou itens transferidos via P2P.
 *
 * @author Nassif
 *
 */
public class HtmlLinkViewer extends HtmlViewer implements SelectionListener {

    private static Logger LOGGER = LoggerFactory.getLogger(HtmlLinkViewer.class);

    public static final String PREVIEW_WITH_LINKS_MIME = "application/x-preview-with-links"; //$NON-NLS-1$

    public static final String PREVIEW_WITH_LINKS_HEADER = "<!--Preview With Links-->"; //$NON-NLS-1$

    public static final String UFED_HTML_REPORT_MIME = "application/x-ufed-html-report"; //$NON-NLS-1$

    private static final double CHAT_CAPTURE_SCALE = 2.0d;
    private static final double CHAT_CAPTURE_END_MARGIN_CSS = 32.0d;

    protected AttachmentSearcher attachSearcher;

    private HashSet<String> mediaHashesInView = new HashSet<>();

    private boolean cheking = false;

    private final ChatCapturePanel chatCapturePanel = new ChatCapturePanel(this);
    private volatile IItem captureSourceItem;
    private volatile String captureContextMessageId;
    private volatile String captureStartMessageId;
    private volatile String captureEndMessageId;
    private volatile boolean captureRunning;
    private volatile boolean stopCaptureRequested;

    public HtmlLinkViewer(AttachmentSearcher attachSearcher) {
        this.attachSearcher = attachSearcher;
        this.fileHandler = new AttachmentHandler();
        this.enableJavascript = true;

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                    @Override
                    public void changed(ObservableValue<? extends Worker.State> ov, Worker.State oldState,
                            Worker.State newState) {
                        if (newState == Worker.State.SUCCEEDED) {
                            installChatCaptureSupport();
                            updateSelection();
                            // imprecise, not needed for current chat reports after #633
                            // scrollToPosition();
                        }
                        if (newState == Worker.State.RUNNING) {
                            mediaHashesInView.clear();
                        }
                    }
                });
                // some htmls need this to javascript work early
                webEngine.documentProperty().addListener(new ChangeListener<Document>() {

                    @Override
                    public void changed(ObservableValue<? extends Document> observable, Document oldValue,
                            Document newValue) {
                        addJavascriptListener(webEngine);
                    }

                });
                // but other htmls need this to javascript work early
                webEngine.getLoadWorker().progressProperty().addListener(new ChangeListener<Number>() {

                    @Override
                    public void changed(ObservableValue<? extends Number> observable, Number oldValue,
                            Number newValue) {
                        addJavascriptListener(webEngine);
                        installChatCaptureSupport();
                        updateSelection();
                        if (newValue.floatValue() > 0) {
                            // imprecise, not needed for current chat reports after #633
                            // scrollToPosition();
                        }
                    }
                });
                htmlViewer.setContextMenuEnabled(false);
                htmlViewer.setOnContextMenuRequested(event -> {
                    captureContextMessageId = getMessageIdAt(event.getX(), event.getY());
                    ContextMenu menu = new ContextMenu();
                    MenuItem start = new MenuItem(Messages.getString("MenuClass.ChatCaptureSetStart")); //$NON-NLS-1$
                    MenuItem end = new MenuItem(Messages.getString("MenuClass.ChatCaptureSetEnd")); //$NON-NLS-1$
                    MenuItem dateStart = new MenuItem(Messages.getString("MenuClass.ChatCaptureSetStartDate")); //$NON-NLS-1$
                    MenuItem clear = new MenuItem(Messages.getString("MenuClass.ChatCaptureClear")); //$NON-NLS-1$
                    boolean hasMessage = captureContextMessageId != null && !captureContextMessageId.isBlank();
                    boolean isDate = hasMessage && isDateCaptureElement(captureContextMessageId);
                    start.setDisable(!hasMessage);
                    end.setDisable(!hasMessage);
                    dateStart.setDisable(!isDate);
                    start.setOnAction(e -> setChatCaptureStartFromContext());
                    dateStart.setOnAction(e -> setChatCaptureStartDateFromContext());
                    end.setOnAction(e -> setChatCaptureEndFromContext());
                    clear.setOnAction(e -> clearChatCaptureMarks());
                    menu.getItems().add(isDate ? dateStart : start);
                    menu.getItems().addAll(end, clear);
                    menu.show(htmlViewer, event.getScreenX(), event.getScreenY());
                    event.consume();
                });
            }
        });
    }

    public ChatCapturePanel getChatCapturePanel() {
        return chatCapturePanel;
    }

    public boolean isChatCaptureRunning() {
        return captureRunning;
    }

    public void stopChatCapture() {
        stopCaptureRequested = true;
        chatCapturePanel.setStatus("ChatCapturePanel.Stopping"); //$NON-NLS-1$
    }

    private void setChatCaptureStartFromContext() {
        if (captureContextMessageId == null || captureContextMessageId.isBlank()) {
            return;
        }
        captureStartMessageId = captureContextMessageId;
        highlightCaptureMarks();
        chatCapturePanel.setStatus("ChatCapturePanel.StartMarked"); //$NON-NLS-1$
    }

    private void setChatCaptureEndFromContext() {
        if (captureContextMessageId == null || captureContextMessageId.isBlank()) {
            return;
        }
        captureEndMessageId = captureContextMessageId;
        highlightCaptureMarks();
        chatCapturePanel.setStatus("ChatCapturePanel.EndMarked"); //$NON-NLS-1$
    }

    private void setChatCaptureStartDateFromContext() {
        if (captureContextMessageId == null || captureContextMessageId.isBlank()) {
            return;
        }
        captureStartMessageId = captureContextMessageId;
        highlightCaptureMarks();
        chatCapturePanel.setStatus("ChatCapturePanel.StartDateMarked"); //$NON-NLS-1$
    }

    private void clearChatCaptureMarks() {
        captureStartMessageId = null;
        captureEndMessageId = null;
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.clear();}"); //$NON-NLS-1$
        chatCapturePanel.updateCounters(0, 0);
        chatCapturePanel.setStatus("ChatCapturePanel.MarkMessages"); //$NON-NLS-1$
    }

    public void startChatCapture(File outputFolder) {
        startChatCapture(outputFolder, null);
    }

    public void startChatCapture(File outputFolder, String customFolderName) {
        if (captureRunning) {
            return;
        }
        if (captureStartMessageId == null || captureEndMessageId == null) {
            chatCapturePanel.setStatus("ChatCapturePanel.MissingMarks"); //$NON-NLS-1$
            return;
        }
        if (outputFolder == null || outputFolder.getPath().isBlank()) {
            chatCapturePanel.setStatus("ChatCapturePanel.MissingFolder"); //$NON-NLS-1$
            return;
        }
        captureRunning = true;
        stopCaptureRequested = false;
        chatCapturePanel.setCaptureRunning(true);
        chatCapturePanel.setStatus("ChatCapturePanel.Capturing"); //$NON-NLS-1$
        new Thread(() -> runChatCapture(outputFolder.toPath(), customFolderName), "IPED-chat-capture").start(); //$NON-NLS-1$
    }

    private void runChatCapture(Path outputRoot, String customFolderName) {
        List<CaptureFrame> frames = new ArrayList<>();
        Map<String, ExportedFile> exported = new LinkedHashMap<>();
        Set<String> exportedAttachmentHashes = new HashSet<>();
        String status = "completed"; //$NON-NLS-1$
        try {
            safeRebuildCaptureRootIndex(outputRoot);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()); //$NON-NLS-1$
            Path captureDir = outputRoot.resolve(buildCaptureFolderName(customFolderName, timestamp));
            Path screenshotsDir = captureDir.resolve("screenshots"); //$NON-NLS-1$
            Path attachmentsDir = captureDir.resolve("anexos"); //$NON-NLS-1$
            Files.createDirectories(screenshotsDir);
            Files.createDirectories(attachmentsDir);

            setCaptureInteractionEnabled(false);
            setCaptureMarksVisible(false);
            sleep(150);
            executeScriptAndWait("window.ipedChatCapture.scrollToMessage(" + js(captureStartMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
            sleep(500);

            double lastY = -1;
            int stuck = 0;
            String lastCapturedMessageId = null;
            Set<String> capturedBlockIds = new HashSet<>();
            boolean reachedEnd = false;
            boolean hitFrameLimit = true;
            for (int i = 1; i <= 500 && !stopCaptureRequested; i++) {
                Path framePath = screenshotsDir.resolve(String.format("frame_%04d.png", i)); //$NON-NLS-1$
                setCaptureHeaderVisible(i == 1);
                sleep(120);
                CapturePlan plan = captureImage(framePath, i == 1, lastCapturedMessageId, capturedBlockIds);
                if (plan == null) {
                    executeScriptAndWait("window.ipedChatCapture.scrollNextCaptureStep();"); //$NON-NLS-1$
                    sleep(450);
                    continue;
                }
                Set<String> hashes = getCaptureBlockHashes(plan);
                frames.add(new CaptureFrame(i, captureDir.relativize(framePath).toString(), plan.firstEligibleMessageId,
                        plan.lastCompleteMessageId != null ? plan.lastCompleteMessageId : plan.lastVisibleMessageId, plan.firstVisibleMessageIdRaw,
                        plan.residualTopBlockId, plan.oversizedContinuation, plan.exportDocumentTop, plan.exportDocumentBottom, plan.cropTopCss,
                        hashes, plan.imageWidth, plan.imageHeight, plan.blocks));
                hashFile(framePath, exported, "screenshots/" + framePath.getFileName().toString(), null, captureSourceItem); //$NON-NLS-1$
                exportVisibleAttachments(hashes, attachmentsDir, captureDir, exported, exportedAttachmentHashes);
                addCapturedBlockIds(capturedBlockIds, plan);
                chatCapturePanel.updateCounters(frames.size(), exportedAttachmentHashes.size());

                if (plan.endMessageCaptured) {
                    reachedEnd = true;
                    hitFrameLimit = false;
                    break;
                }

                double currentY = getScrollY();
                if (currentY == lastY) {
                    stuck++;
                    if (stuck >= 3) {
                        status = "stopped_no_scroll_progress"; //$NON-NLS-1$
                        hitFrameLimit = false;
                        break;
                    }
                } else {
                    stuck = 0;
                }
                lastY = currentY;
                if (!advanceCaptureByMessageBoundary(plan, lastCapturedMessageId)) {
                    scrollAfterCapturePlan(plan);
                }
                lastCapturedMessageId = plan.lastCompleteMessageId;
                sleep(450);
            }
            if (stopCaptureRequested) {
                status = "interrupted"; //$NON-NLS-1$
            } else if (!reachedEnd && hitFrameLimit) {
                status = "stopped_frame_limit_end_not_found"; //$NON-NLS-1$
            }
            createReportThumbnails(captureDir, exported);
            CaptureSourceMetadata sourceMetadata = readCaptureSourceMetadata();
            writeHashes(captureDir.resolve("hashes.txt"), exported); //$NON-NLS-1$
            writeFileListCsv(captureDir.resolve("Lista de Arquivos.csv"), exported); //$NON-NLS-1$
            writeText(captureDir.resolve("texto.txt"), buildCaptureText(sourceMetadata, frames)); //$NON-NLS-1$
            writeInteractiveCaptureReport(captureDir.resolve("relatorio.htm"), status, frames, exported); //$NON-NLS-1$
            writeManifest(captureDir.resolve("manifest.json"), status, frames, exported, sourceMetadata); //$NON-NLS-1$
            String whatsappJson = buildWhatsAppCoordinatesJson(status, frames, exported, sourceMetadata);
            writeText(captureDir.resolve("whatsapp-coordinates.json"), whatsappJson); //$NON-NLS-1$
            writeWhatsAppHtml(captureDir.resolve("whatsapp.html"), whatsappJson); //$NON-NLS-1$
            chatCapturePanel.setStatus("completed".equals(status) ? "ChatCapturePanel.Completed" : "ChatCapturePanel.Stopped"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } catch (Exception e) {
            LOGGER.error("Error capturing chat interval", e); //$NON-NLS-1$
            chatCapturePanel.setStatusText(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            setCaptureInteractionEnabled(true);
            restoreCaptureHeader();
            setCaptureMarksVisible(true);
            captureRunning = false;
            stopCaptureRequested = false;
            chatCapturePanel.setCaptureRunning(false);
            safeRebuildCaptureRootIndex(outputRoot);
        }
    }

    private String buildCaptureFolderName(String customFolderName, String timestamp) {
        String fallback = "captura-chat-" + timestamp; //$NON-NLS-1$
        String name = customFolderName == null ? "" : customFolderName.trim(); //$NON-NLS-1$
        if (name.isEmpty()) {
            return fallback;
        }
        name = safeName(name.replace("{timestamp}", timestamp)); //$NON-NLS-1$
        if (name.isEmpty()) {
            return fallback;
        }
        if (!name.matches(".*\\d{8}-\\d{6}$")) { //$NON-NLS-1$
            name = name + "-" + timestamp; //$NON-NLS-1$
        }
        return name;
    }

    private void safeRebuildCaptureRootIndex(Path outputRoot) {
        try {
            rebuildCaptureRootIndex(outputRoot);
        } catch (Exception e) {
            LOGGER.warn("Unable to rebuild capture root index at {}", outputRoot, e); //$NON-NLS-1$
        }
    }

    private void rebuildCaptureRootIndex(Path outputRoot) throws IOException {
        if (outputRoot == null || !Files.isDirectory(outputRoot)) {
            return;
        }
        List<CaptureRootEntry> captures = new ArrayList<>();
        try (Stream<Path> children = Files.list(outputRoot)) {
            children.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("_backup_chat_capture_")) //$NON-NLS-1$
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> {
                        try {
                            CaptureRootEntry entry = readCaptureRootEntry(outputRoot, path);
                            if (entry != null) {
                                captures.add(entry);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Unable to inspect capture folder {}", path, e); //$NON-NLS-1$
                        }
                    });
        }
        String rootHash = calculateRootHash(captures);
        writeCaptureRootIndexJson(outputRoot.resolve("capturas-index.json"), outputRoot, rootHash, captures); //$NON-NLS-1$
        writeCaptureRootIndexHtml(outputRoot.resolve("index.html"), outputRoot, rootHash, captures); //$NON-NLS-1$
        writeCaptureRootReport(outputRoot.resolve("relatorio.htm"), outputRoot, rootHash, captures); //$NON-NLS-1$
    }

    private CaptureRootEntry readCaptureRootEntry(Path outputRoot, Path captureDir) throws IOException {
        if (!isCaptureFolder(captureDir)) {
            return null;
        }
        CaptureRootEntry entry = new CaptureRootEntry();
        entry.folder = outputRoot.relativize(captureDir).toString().replace('\\', '/');
        entry.createdAt = readDirectoryCreationDate(captureDir);
        entry.status = "unknown"; //$NON-NLS-1$

        Path manifest = captureDir.resolve("manifest.json"); //$NON-NLS-1$
        if (Files.isRegularFile(manifest)) {
            String content = Files.readString(manifest);
            entry.status = nonBlank(jsonField(content, "status"), entry.status); //$NON-NLS-1$
            String source = jsonObjectField(content, "sourceMetadata"); //$NON-NLS-1$
            entry.title = firstNonBlank(jsonField(source, "nome"), jsonField(source, "title")); //$NON-NLS-1$ //$NON-NLS-2$
            entry.chatId = inferChatId(entry.title);
            entry.sizeBytes = jsonLongField(source, "tamanho", 0); //$NON-NLS-1$
        }

        Path coordinates = captureDir.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
        if ((entry.title == null || entry.title.isBlank()) && Files.isRegularFile(coordinates)) {
            String content = Files.readString(coordinates);
            String source = jsonObjectField(content, "sourceMetadata"); //$NON-NLS-1$
            entry.title = firstNonBlank(jsonField(source, "nome"), jsonField(source, "title")); //$NON-NLS-1$ //$NON-NLS-2$
            entry.chatId = inferChatId(entry.title);
            entry.status = nonBlank(jsonField(content, "status"), entry.status); //$NON-NLS-1$
        }

        Path text = captureDir.resolve("texto.txt"); //$NON-NLS-1$
        if (Files.isRegularFile(text)) {
            CaptureTextHeader header = readCaptureTextHeader(text);
            entry.chatId = nonBlank(entry.chatId, header.chatId);
            entry.title = nonBlank(entry.title, header.title);
        }

        entry.title = nonBlank(entry.title, entry.folder);
        entry.chatId = nonBlank(entry.chatId, inferChatId(entry.title));
        entry.frameCount = countRegularFiles(captureDir.resolve("screenshots")); //$NON-NLS-1$
        entry.attachmentCount = countRegularFiles(captureDir.resolve("anexos")); //$NON-NLS-1$
        FolderHash folderHash = calculateFolderHash(captureDir);
        entry.folderHash = folderHash.hash;
        entry.fileCount = folderHash.fileCount;
        entry.sizeBytes = folderHash.sizeBytes;
        entry.linksWhatsapp = entry.folder + "/whatsapp.html"; //$NON-NLS-1$
        entry.linksReport = entry.folder + "/relatorio.htm"; //$NON-NLS-1$
        entry.linksText = entry.folder + "/texto.txt"; //$NON-NLS-1$
        entry.linksManifest = entry.folder + "/manifest.json"; //$NON-NLS-1$
        entry.linksHashes = entry.folder + "/hashes.txt"; //$NON-NLS-1$
        return entry;
    }

    private boolean isCaptureFolder(Path captureDir) {
        return Files.isRegularFile(captureDir.resolve("manifest.json")) //$NON-NLS-1$
                || Files.isRegularFile(captureDir.resolve("whatsapp.html")) //$NON-NLS-1$
                || Files.isRegularFile(captureDir.resolve("relatorio.htm")) //$NON-NLS-1$
                || Files.isRegularFile(captureDir.resolve("texto.txt")); //$NON-NLS-1$
    }

    private FolderHash calculateFolderHash(Path folder) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> folder.relativize(path).toString().replace('\\', '/').toLowerCase()));
        StringBuilder canonical = new StringBuilder();
        FolderHash result = new FolderHash();
        for (Path file : files) {
            String relative = folder.relativize(file).toString().replace('\\', '/');
            long size = Files.size(file);
            String sha256 = digest(file, "SHA-256"); //$NON-NLS-1$
            canonical.append(relative).append('|').append(size).append('|').append(sha256).append('\n');
            result.fileCount++;
            result.sizeBytes += size;
        }
        result.hash = sha256Text(canonical.toString());
        return result;
    }

    private String calculateRootHash(List<CaptureRootEntry> captures) throws IOException {
        StringBuilder canonical = new StringBuilder();
        captures.stream()
                .sorted(Comparator.comparing(entry -> safe(entry.folder).toLowerCase()))
                .forEach(entry -> canonical.append(safe(entry.folder)).append('|')
                        .append(safe(entry.folderHash)).append('|')
                        .append(entry.sizeBytes).append('|')
                        .append(entry.fileCount).append('\n'));
        return sha256Text(canonical.toString());
    }

    private String sha256Text(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            return toHex(digest.digest(safe(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private int countRegularFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(folder)) {
            return (int) stream.filter(Files::isRegularFile).count();
        }
    }

    private String readDirectoryCreationDate(Path folder) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(folder, BasicFileAttributes.class);
            return formatUtcDate(Date.from(attrs.creationTime().toInstant()));
        } catch (IOException e) {
            return extractTimestampDate(folder.getFileName().toString());
        }
    }

    private String extractTimestampDate(String folderName) {
        Matcher matcher = Pattern.compile("(\\d{8})-(\\d{6})$").matcher(safe(folderName)); //$NON-NLS-1$
        if (!matcher.find()) {
            return ""; //$NON-NLS-1$
        }
        String date = matcher.group(1);
        String time = matcher.group(2);
        return date.substring(6, 8) + "/" + date.substring(4, 6) + "/" + date.substring(0, 4) //$NON-NLS-1$ //$NON-NLS-2$
                + " " + time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private CaptureTextHeader readCaptureTextHeader(Path textPath) {
        CaptureTextHeader header = new CaptureTextHeader();
        try (BufferedReader reader = Files.newBufferedReader(textPath)) {
            String line;
            int useful = 0;
            while ((line = reader.readLine()) != null && useful < 4) {
                String value = line.trim();
                if (value.isEmpty()) {
                    continue;
                }
                useful++;
                if (header.chatId.isBlank()) {
                    header.chatId = value;
                } else if (header.title.isBlank()) {
                    header.title = value;
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to read capture text header from {}", textPath, e); //$NON-NLS-1$
        }
        return header;
    }

    private String inferChatId(String value) {
        String text = safe(value);
        int dash = text.lastIndexOf(" - "); //$NON-NLS-1$
        if (dash >= 0 && dash + 3 < text.length()) {
            text = text.substring(dash + 3);
        }
        text = text.replaceAll("_\\d+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
        Matcher matcher = Pattern.compile("\\d{8,}").matcher(text); //$NON-NLS-1$
        String last = ""; //$NON-NLS-1$
        while (matcher.find()) {
            last = matcher.group();
        }
        return last;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return ""; //$NON-NLS-1$
    }

    private String jsonObjectField(String content, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\{([^{}]*)\\}", Pattern.DOTALL).matcher(safe(content)); //$NON-NLS-1$ //$NON-NLS-2$
        return matcher.find() ? matcher.group(1) : ""; //$NON-NLS-1$
    }

    private String jsonField(String content, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\"((?:\\\\.|[^\"])*)\"|null|true|false|-?\\d+)", Pattern.DOTALL).matcher(safe(content)); //$NON-NLS-1$ //$NON-NLS-2$
        if (!matcher.find()) {
            return ""; //$NON-NLS-1$
        }
        String quoted = matcher.group(2);
        return quoted != null ? unescapeJson(quoted) : matcher.group(1);
    }

    private long jsonLongField(String content, String field, long fallback) {
        String value = jsonField(content, field);
        try {
            return value.isBlank() || "null".equals(value) ? fallback : Long.parseLong(value); //$NON-NLS-1$
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String unescapeJson(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < safe(value).length(); i++) {
            char c = value.charAt(i);
            if (!escaping && c == '\\') {
                escaping = true;
                continue;
            }
            if (escaping) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                escaping = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void writeCaptureRootIndexJson(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("{"); //$NON-NLS-1$
            out.println("  \"version\": 1,"); //$NON-NLS-1$
            out.println("  \"generatedAt\": " + json(Instant.now().toString()) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"rootPath\": " + json(outputRoot.toAbsolutePath().toString()) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"rootHash\": " + json(rootHash) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"captures\": ["); //$NON-NLS-1$
            for (int i = 0; i < captures.size(); i++) {
                CaptureRootEntry entry = captures.get(i);
                out.println("    {"); //$NON-NLS-1$
                out.println("      \"folder\": " + json(entry.folder) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"title\": " + json(entry.title) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"chatId\": " + json(entry.chatId) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"createdAt\": " + json(entry.createdAt) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"folderHash\": " + json(entry.folderHash) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"fileCount\": " + entry.fileCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"attachmentCount\": " + entry.attachmentCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"frameCount\": " + entry.frameCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"sizeBytes\": " + entry.sizeBytes + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"status\": " + json(entry.status) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"links\": {"); //$NON-NLS-1$
                out.println("        \"whatsapp\": " + json(entry.linksWhatsapp) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("        \"report\": " + json(entry.linksReport) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("        \"text\": " + json(entry.linksText) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("        \"manifest\": " + json(entry.linksManifest) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("        \"hashes\": " + json(entry.linksHashes)); //$NON-NLS-1$
                out.println("      }"); //$NON-NLS-1$
                out.println("    }" + (i + 1 == captures.size() ? "" : ",")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            out.println("  ]"); //$NON-NLS-1$
            out.println("}"); //$NON-NLS-1$
        }
    }

    private void writeCaptureRootIndexHtml(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>&Iacute;ndice de Capturas</title>"); //$NON-NLS-1$
            out.println("<style>body{margin:0;background:#f2efe9;color:#1f2a30;font-family:Arial,sans-serif}.hero{background:#102b35;color:#fff;padding:26px 34px}.hero h1{margin:0 0 8px;font-size:28px}.hero p{margin:4px 0;color:#d7e6ea}.hero .hash{display:inline-block;background:#06171d;color:#f7fbfd;border:1px solid #6fa9b8;box-shadow:inset 0 0 0 1px rgba(255,255,255,.08)}.wrap{padding:24px 34px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px}.card{background:white;border:1px solid #d8d1c8;border-radius:10px;padding:16px;box-shadow:0 2px 10px rgba(0,0,0,.06)}.card h2{font-size:18px;margin:0 0 8px}.meta{font-size:13px;color:#52646c;line-height:1.45}.hash{font-family:Consolas,monospace;font-size:11px;word-break:break-all;background:#f5f5f5;padding:8px;border-radius:6px}.btn{display:inline-block;margin:8px 6px 0 0;padding:8px 10px;background:#0b6b5d;color:white;text-decoration:none;border-radius:5px;font-size:13px}.btn.alt{background:#46545a}.empty{background:white;border:1px solid #ddd;padding:18px;border-radius:8px}</style>"); //$NON-NLS-1$
            out.println("</head><body><section class=\"hero\"><h1>&Iacute;ndice de Capturas de Chat</h1>"); //$NON-NLS-1$
            out.println("<p><b>Pasta raiz:</b> " + html(outputRoot.toAbsolutePath().toString()) + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<p><b>Total de capturas:</b> " + captures.size() + " &nbsp; <b>Hash geral:</b> <span class=\"hash\">" + html(rootHash) + "</span></p></section><main class=\"wrap\">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (captures.isEmpty()) {
                out.println("<div class=\"empty\">Nenhuma captura encontrada nesta pasta.</div>"); //$NON-NLS-1$
            } else {
                out.println("<div class=\"grid\">"); //$NON-NLS-1$
                for (CaptureRootEntry entry : captures) {
                    out.println("<article class=\"card\"><h2>" + html(entry.folder) + "</h2>"); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<div class=\"meta\"><b>Chat:</b> " + html(entry.title) + "<br><b>ID:</b> " + html(entry.chatId) //$NON-NLS-1$ //$NON-NLS-2$
                            + "<br><b>Data:</b> " + html(entry.createdAt) + "<br><b>Status:</b> " + html(entry.status) //$NON-NLS-1$ //$NON-NLS-2$
                            + "<br><b>Frames:</b> " + entry.frameCount + " &nbsp; <b>Anexos:</b> " + entry.attachmentCount //$NON-NLS-1$ //$NON-NLS-2$
                            + "<br><b>Arquivos:</b> " + entry.fileCount + " &nbsp; <b>Tamanho:</b> " + formatBytes(entry.sizeBytes) + "</div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<p class=\"hash\">" + html(entry.folderHash) + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<a class=\"btn\" href=\"" + html(entry.linksWhatsapp) + "\">Abrir WhatsApp</a><a class=\"btn\" href=\"" + html(entry.linksReport) + "\">Abrir Relat&oacute;rio</a><a class=\"btn alt\" href=\"" + html(entry.linksText) + "\">Texto</a><a class=\"btn alt\" href=\"" + html(entry.linksManifest) + "\">Manifest</a></article>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                }
                out.println("</div>"); //$NON-NLS-1$
            }
            out.println("</main></body></html>"); //$NON-NLS-1$
        }
    }

    private void writeCaptureRootReport(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>Relat&oacute;rio Geral de Capturas</title>"); //$NON-NLS-1$
            out.println("<style>body{font-family:Arial,sans-serif;color:#222;margin:24px}.columnHead{background:#c0c0c0;border:1px solid #777;text-align:left;padding:4px}.clrBkgrnd{background:#e8e8e8}.mono{font-family:Consolas,monospace;font-size:12px;word-break:break-all}table{border-collapse:collapse;width:100%;margin-top:12px}td,th{border:1px solid #ccc;padding:6px;vertical-align:top;font-size:13px}th{background:#ddd}.summary{border:1px solid #ccc;padding:12px;background:#fafafa}</style></head><body>"); //$NON-NLS-1$
            out.println("<table><tbody><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\">Relat&oacute;rio Geral de Capturas</th></tr><tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">Coment&aacute;rios: </span>&Iacute;ndice pai das capturas existentes na pasta raiz.</td></tr></tbody></table>"); //$NON-NLS-1$
            out.println("<div class=\"summary\"><p><b>Pasta raiz:</b> " + html(outputRoot.toAbsolutePath().toString()) + "</p><p><b>Quantidade de capturas:</b> " + captures.size() + "</p><p><b>Hash geral da raiz:</b> <span class=\"mono\">" + html(rootHash) + "</span></p><p><a href=\"index.html\">Abrir index.html</a> &nbsp; <a href=\"capturas-index.json\">Abrir capturas-index.json</a></p></div>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<table><thead><tr><th>Pasta</th><th>Chat</th><th>Data</th><th>Status</th><th>Arquivos</th><th>Anexos</th><th>Hash da pasta</th><th>Links</th></tr></thead><tbody>"); //$NON-NLS-1$
            if (captures.isEmpty()) {
                out.println("<tr><td colspan=\"8\">Nenhuma captura encontrada.</td></tr>"); //$NON-NLS-1$
            } else {
                for (CaptureRootEntry entry : captures) {
                    out.println("<tr><td>" + html(entry.folder) + "</td><td>" + html(entry.title) + "<br><span class=\"mono\">" + html(entry.chatId) + "</span></td><td>" + html(entry.createdAt) + "</td><td>" + html(entry.status) + "</td><td>" + entry.fileCount + "</td><td>" + entry.attachmentCount + "</td><td class=\"mono\">" + html(entry.folderHash) + "</td><td><a href=\"" + html(entry.linksReport) + "\">relatorio.htm</a><br><a href=\"" + html(entry.linksWhatsapp) + "\">whatsapp.html</a><br><a href=\"" + html(entry.linksText) + "\">texto.txt</a></td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
                }
            }
            out.println("</tbody></table></body></html>"); //$NON-NLS-1$
        }
    }

    private void exportVisibleAttachments(Set<String> hashes, Path attachmentsDir, Path captureDir, Map<String, ExportedFile> exported, Set<String> exportedAttachmentHashes) throws IOException {
        for (String hash : hashes) {
            if (hash == null || hash.isBlank() || exportedAttachmentHashes.contains(hash)) {
                continue;
            }
            List<IItem> items = attachSearcher.getItems("hash:" + attachSearcher.escapeQuery(hash)); //$NON-NLS-1$
            for (IItem item : items) {
                File source = item.getTempFile();
                if (source == null || !source.exists()) {
                    continue;
                }
                Path groupDir = attachmentsDir.resolve(groupFolder(item));
                Files.createDirectories(groupDir);
                String name = safeName(item.getName());
                if (name.isBlank()) {
                    name = hash;
                }
                Path target = uniquePath(groupDir.resolve(name));
                Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                hashFile(target, exported, captureDir.relativize(target).toString(), hash, item);
            }
            exportedAttachmentHashes.add(hash);
        }
    }

    private String groupFolder(IItem item) {
        String type = item.getType();
        if (type != null) {
            type = type.toLowerCase();
            if (type.startsWith("image")) return "imagens"; //$NON-NLS-1$ //$NON-NLS-2$
            if (type.startsWith("audio")) return "audios"; //$NON-NLS-1$ //$NON-NLS-2$
            if (type.startsWith("video")) return "videos"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return "outros"; //$NON-NLS-1$
    }

    private Path uniquePath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        String fileName = path.getFileName().toString();
        String base = fileName;
        String ext = ""; //$NON-NLS-1$
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        for (int i = 1; ; i++) {
            Path candidate = path.resolveSibling(base + "_" + i + ext); //$NON-NLS-1$
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    private ExportedFile hashFile(Path path, Map<String, ExportedFile> exported, String relativePath, String chatHash, IItem sourceItem) throws IOException {
        ExportedFile file = new ExportedFile();
        file.relativePath = relativePath.replace('\\', '/');
        file.absolutePath = path.toAbsolutePath().toString();
        file.chatHash = chatHash;
        file.size = Files.size(path);
        file.md5 = digest(path, "MD5"); //$NON-NLS-1$
        file.sha256 = digest(path, "SHA-256"); //$NON-NLS-1$
        applyExportedMetadata(file, path, sourceItem);
        exported.put(file.relativePath, file);
        return file;
    }

    private void applyExportedMetadata(ExportedFile file, Path path, IItem sourceItem) {
        if (sourceItem != null) {
            file.originalName = sourceItem.getName();
            file.originalPath = sourceItem.getPath();
            file.originalType = sourceItem.getType();
            file.deleted = sourceItem.isDeleted();
            file.category = sourceItem.getCategories();
            file.creationDate = formatUtcDate(sourceItem.getCreationDate());
            file.modificationDate = formatUtcDate(sourceItem.getModDate());
            file.accessDate = formatUtcDate(sourceItem.getAccessDate());
            if (file.chatHash == null || file.chatHash.isBlank()) {
                file.chatHash = sourceItem.getHash();
            }
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (file.creationDate == null || file.creationDate.isBlank()) {
                file.creationDate = formatUtcDate(Date.from(attrs.creationTime().toInstant()));
            }
            if (file.modificationDate == null || file.modificationDate.isBlank()) {
                file.modificationDate = formatUtcDate(Date.from(attrs.lastModifiedTime().toInstant()));
            }
            if (file.accessDate == null || file.accessDate.isBlank()) {
                file.accessDate = formatUtcDate(Date.from(attrs.lastAccessTime().toInstant()));
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to read exported file timestamps for {}", path, e); //$NON-NLS-1$
        }
    }

    private String digest(Path path, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(path); DigestInputStream din = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (din.read(buffer) != -1) {
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b)); //$NON-NLS-1$
        }
        return sb.toString();
    }

    private void writeHashes(Path path, Map<String, ExportedFile> exported) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            for (ExportedFile file : exported.values()) {
                out.println("Algorithm : SHA256"); //$NON-NLS-1$
                out.println("Hash      : " + safe(file.sha256)); //$NON-NLS-1$
                out.println("Path      : " + safe(file.absolutePath)); //$NON-NLS-1$
                out.println();
            }
        }
    }

    private void writeFileListCsv(Path path, Map<String, ExportedFile> exported) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println(csv("Nome") + ";" + csv("Caminho Relativo") + ";" + csv("Caminho Absoluto") + ";" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    + csv("Tipo") + ";" + csv("Tamanho") + ";" + csv("MD5") + ";" + csv("SHA256") + ";" + csv("Hash do Chat")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            for (ExportedFile file : exported.values()) {
                out.println(csv(displayName(file)) + ";" + csv(file.relativePath) + ";" + csv(file.absolutePath) + ";" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + csv(fileType(file)) + ";" + file.size + ";" + csv(file.md5) + ";" + csv(file.sha256) + ";" + csv(file.chatHash)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
        }
    }

    private void createReportThumbnails(Path captureDir, Map<String, ExportedFile> exported) {
        for (ExportedFile file : exported.values()) {
            if (!isImageForReport(file)) {
                continue;
            }
            try {
                Path source = captureDir.resolve(file.relativePath);
                BufferedImage image = ImageIO.read(source.toFile());
                if (image == null) {
                    continue;
                }
                int max = 112;
                double scale = Math.min((double) max / Math.max(1, image.getWidth()), (double) max / Math.max(1, image.getHeight()));
                scale = Math.min(1.0d, scale);
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                BufferedImage thumb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = thumb.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawImage(image, 0, 0, width, height, null);
                } finally {
                    g.dispose();
                }
                String sha = safe(file.sha256);
                String first = sha.length() > 0 ? sha.substring(0, 1).toUpperCase() : "0"; //$NON-NLS-1$
                String second = sha.length() > 1 ? sha.substring(1, 2).toUpperCase() : "0"; //$NON-NLS-1$
                Path thumbPath = captureDir.resolve("thumbs").resolve(first).resolve(second).resolve((sha.isBlank() ? safeName(fileName(file.relativePath)) : sha) + ".jpg"); //$NON-NLS-1$ //$NON-NLS-2$
                Files.createDirectories(thumbPath.getParent());
                ImageIO.write(thumb, "jpg", thumbPath.toFile()); //$NON-NLS-1$
                file.thumbRelativePath = captureDir.relativize(thumbPath).toString().replace('\\', '/');
            } catch (Exception e) {
                LOGGER.debug("Unable to create report thumbnail for {}", file.relativePath, e); //$NON-NLS-1$
            }
        }
    }

    private void writeInteractiveCaptureReport(Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("<!DOCTYPE html>"); //$NON-NLS-1$
            out.println("<html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>Relat&oacute;rio de Captura de Chat</title>"); //$NON-NLS-1$
            out.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"res/common.css\"><link rel=\"stylesheet\" type=\"text/css\" href=\"res/bookmarks.css\">"); //$NON-NLS-1$
            out.println("<style>body{margin:0;font-family:Arial,sans-serif;background:#fff;color:#222}#menu{position:fixed;left:0;top:0;bottom:0;width:280px;background:#263238;color:white;padding:16px;overflow:auto;box-sizing:border-box}#menu h1{font-size:18px;margin:0 0 14px}#menu h2{font-size:15px;margin:18px 0 8px;color:#d9f3ff}#menu a{color:white;display:block;padding:4px 0;text-decoration:none;cursor:pointer}.sub{padding-left:18px!important;color:#dce7ea!important}.active-link{font-weight:bold;color:#fff59d!important}#content{margin-left:310px;padding:24px}.section{display:none}.section.active{display:block}.report-page{background:white}.card{background:white;border:1px solid #ddd;padding:16px;margin-bottom:16px}.columnHead{background:#c0c0c0;border:1px solid #777;text-align:left;color:#000;padding:4px}.clrBkgrnd{background:#e8e8e8}.bkmkLblFiles{background:#c0c0c0;border:1px solid #777;font-weight:bold;margin-top:12px;padding:3px}.bkmkSeparator{height:8px;margin-top:8px}.bkmkValue{font-size:13px}.row{display:flex;width:100%;clear:both}.bkmkColLeft{display:inline-block;width:220px;box-sizing:border-box;padding:3px 5px;border-bottom:1px solid #fff;font-weight:normal}.bkmkColRight{display:inline-block;flex:1;box-sizing:border-box;padding:3px 5px;border-bottom:1px solid #eee;word-break:break-word}.labelBorderless{border-left:0;border-right:0}.thumb{width:auto;height:auto;max-width:112px;max-height:112px}.mono{font-family:Consolas,monospace;font-size:12px;word-break:break-all}.links a{margin-right:14px}.empty{color:#777;font-style:italic}.help-page{max-width:980px;line-height:1.45}.help-page h2{font-size:22px;margin:0 0 16px}.help-page h3{font-size:18px;margin:28px 0 10px}.help-page p{margin:10px 0 10px 28px}.help-page .item{margin-left:28px}.help-page .subitem{margin-left:56px}.help-page .cmd{font-family:Consolas,monospace;background:#f4f4f4;border:1px solid #ddd;padding:6px 8px;display:inline-block}</style>"); //$NON-NLS-1$
            out.println("<script>function showSection(id){document.querySelectorAll('.section').forEach(function(s){s.classList.remove('active')});var el=document.getElementById(id);if(el)el.classList.add('active');document.querySelectorAll('#menu a[data-section]').forEach(function(a){a.classList.toggle('active-link',a.getAttribute('data-section')===id)});return false;}window.onload=function(){showSection('info');};</script>"); //$NON-NLS-1$
            out.println("</head><body><div id=\"menu\"><h1>Relat&oacute;rio de Captura</h1>"); //$NON-NLS-1$
            out.println("<h2>Informa&ccedil;&otilde;es</h2><a href=\"#\" data-section=\"info\" onclick=\"return showSection('info')\">Informa&ccedil;&otilde;es</a><a href=\"#\" data-section=\"busca\" onclick=\"return showSection('busca')\">Busca por palavras-chave</a>"); //$NON-NLS-1$
            out.println("<h2>Marcadores</h2><a class=\"sub\" href=\"#\" data-section=\"sem-marcador\" onclick=\"return showSection('sem-marcador')\">[Sem Marcador]</a><a class=\"sub\" href=\"#\" data-section=\"whatsapp-share\" onclick=\"return showSection('whatsapp-share')\">Provavelmente Compartilhados via WhatsApp</a>"); //$NON-NLS-1$
            out.println("<h2>Categorias</h2><a class=\"sub\" href=\"#\" data-section=\"audios\" onclick=\"return showSection('audios')\">&Aacute;udios</a><a class=\"sub\" href=\"#\" data-section=\"pdfs\" onclick=\"return showSection('pdfs')\">Documentos PDF</a><a class=\"sub\" href=\"#\" data-section=\"imagens\" onclick=\"return showSection('imagens')\">Outras Imagens</a><a class=\"sub\" href=\"#\" data-section=\"scans\" onclick=\"return showSection('scans')\">Poss&iacute;veis Digitaliza&ccedil;&otilde;es</a><a class=\"sub\" href=\"#\" data-section=\"videos\" onclick=\"return showSection('videos')\">V&iacute;deos</a><a class=\"sub\" href=\"#\" data-section=\"whatsapp\" onclick=\"return showSection('whatsapp')\">WhatsApp</a>"); //$NON-NLS-1$
            out.println("<h2>Ajuda</h2><a class=\"sub\" href=\"#\" data-section=\"ajuda\" onclick=\"return showSection('ajuda')\">Relat&oacute;rio e Anexo</a></div><div id=\"content\">"); //$NON-NLS-1$
            out.println("<div class=\"section\" id=\"info\"><div class=\"card\"><h2>Informa&ccedil;&otilde;es</h2><p><b>Status:</b> " + html(status) + "</p><p><b>Frames capturados:</b> " + frames.size() + "</p><p><b>Arquivos exportados:</b> " + exported.size() + "</p><p class=\"links\"><a href=\"whatsapp.html\">whatsapp.html</a><a href=\"whatsapp-coordinates.json\">whatsapp-coordinates.json</a><a href=\"texto.txt\">texto.txt</a><a href=\"hashes.txt\">hashes.txt</a><a href=\"Lista de Arquivos.csv\">Lista de Arquivos.csv</a><a href=\"manifest.json\">manifest.json</a></p></div></div>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            out.println("<div class=\"section\" id=\"busca\"><div class=\"card\"><h2>Busca por palavras-chave</h2><p>Use a busca do navegador neste relat&oacute;rio ou consulte os arquivos exportados na lista CSV.</p></div></div>"); //$NON-NLS-1$
            writeReportFileSection(out, "sem-marcador", "[Sem Marcador]", exported, "all"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "whatsapp-share", "Provavelmente Compartilhados via WhatsApp", exported, "attachments"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "audios", "&Aacute;udios", exported, "audio"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "pdfs", "Documentos PDF", exported, "pdf"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "imagens", "Outras Imagens", exported, "image"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "scans", "Poss&iacute;veis Digitaliza&ccedil;&otilde;es", exported, "scan"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "videos", "V&iacute;deos", exported, "video"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportFileSection(out, "whatsapp", "WhatsApp", exported, "whatsapp"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            writeReportHelpSection(out);
            out.println("</div></body></html>"); //$NON-NLS-1$
        }
    }

    private void writeReportFileSection(PrintWriter out, String id, String title, Map<String, ExportedFile> exported, String filter) {
        List<ExportedFile> files = new ArrayList<>();
        for (ExportedFile file : exported.values()) {
            if (matchesReportFilter(file, filter)) {
                files.add(file);
            }
        }

        out.println("<div class=\"section\" id=\"" + html(id) + "\"><div class=\"report-page\">"); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<table width=\"100%\"><tbody><tr><td>P&aacute;gina 1 de 1</td></tr></tbody></table>"); //$NON-NLS-1$
        out.println("<table width=\"100%\"><tbody><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\">" + reportSectionKind(filter) + ": " + title + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
        out.println("<tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">Coment&aacute;rios: </span>-</td></tr></tbody></table>"); //$NON-NLS-1$
        out.println("<span style=\"font-weight:bold\">Contagem de arquivos: </span>" + files.size()); //$NON-NLS-1$
        out.println("<div class=\"bkmkLblFiles\" width=\"100%\" border=\"1\">Arquivos</div>"); //$NON-NLS-1$

        if (files.isEmpty()) {
            out.println("<p class=\"empty\">Nenhum arquivo nesta se&ccedil;&atilde;o.</p>"); //$NON-NLS-1$
        } else {
            for (ExportedFile file : files) {
                writeReportFileEntry(out, file, title);
            }
        }
        out.println("</div></div>"); //$NON-NLS-1$
    }

    private String reportSectionKind(String filter) {
        return "all".equals(filter) || "attachments".equals(filter) ? "Marcador" : "Categoria"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private void writeReportFileEntry(PrintWriter out, ExportedFile file, String typeLabel) {
        out.println("<div class=\"clrBkgrnd bkmkSeparator bkmkValue\"></div>"); //$NON-NLS-1$
        writeReportRow(out, "Nome", "<b>" + html(displayName(file)) + "</b>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        writeReportRow(out, "Caminho", html(file.relativePath)); //$NON-NLS-1$
        writeReportRow(out, "Tipo de arquivo ", typeLabel); //$NON-NLS-1$
        writeReportRow(out, "Tamanho l&oacute;gico", formatBytes(file.size)); //$NON-NLS-1$
        writeReportRow(out, "Data de cria&ccedil;&atilde;o", html(nonBlank(file.creationDate, "-"))); //$NON-NLS-1$ //$NON-NLS-2$
        writeReportRow(out, "Data de modifica&ccedil;&atilde;o", html(nonBlank(file.modificationDate, "-"))); //$NON-NLS-1$ //$NON-NLS-2$
        writeReportRow(out, "Data de acesso", html(nonBlank(file.accessDate, "-"))); //$NON-NLS-1$ //$NON-NLS-2$
        writeReportRow(out, "Exclu&iacute;do", "N&atilde;o"); //$NON-NLS-1$ //$NON-NLS-2$
        writeReportRow(out, "Reconstitu&iacute;do", "N&atilde;o"); //$NON-NLS-1$ //$NON-NLS-2$
        writeReportRow(out, "Hash", html(nonBlank(file.chatHash, file.sha256).toUpperCase())); //$NON-NLS-1$
        out.println("<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">Exportado como</span><span class=\"bkmkColRight bkmkValue\"><b><a href=\"" //$NON-NLS-1$
                + html(file.relativePath) + "\">" + html(file.relativePath) + "</a></b></span></div>"); //$NON-NLS-1$ //$NON-NLS-2$
        if (isImageForReport(file)) {
            String thumb = file.thumbRelativePath != null ? file.thumbRelativePath : file.relativePath;
            out.println("<table width=\"100%\"><tbody><tr><td><a href=\"" + html(file.relativePath) + "\"><img src=\"" //$NON-NLS-1$ //$NON-NLS-2$
                    + html(thumb) + "\" class=\"thumb\"></a></td></tr></tbody></table>"); //$NON-NLS-1$
        }
    }

    private void writeReportRow(PrintWriter out, String label, String valueHtml) {
        out.println("<div class=\"row\"><span class=\"bkmkColLeft bkmkValue labelBorderless clrBkgrnd\" width=\"100%\" border=\"1\">" //$NON-NLS-1$
                + label + "</span><span class=\"bkmkColRight bkmkValue\">" + valueHtml + "</span></div>"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String formatBytes(long size) {
        return String.format("%,d", size).replace(',', '.') + " Bytes"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void writeReportHelpSection(PrintWriter out) {
        out.println("<div class=\"section\" id=\"ajuda\"><div class=\"card help-page\">"); //$NON-NLS-1$
        out.println("<h2>Ajuda</h2><p><b>Itens do Menu:</b></p>"); //$NON-NLS-1$
        out.println("<p class=\"item\"><b>Informa&ccedil;&otilde;es</b><br>P&aacute;gina inicial contendo informa&ccedil;&otilde;es referentes ao exame pericial, como o n&uacute;mero do laudo, material examinado, peritos respons&aacute;veis, entre outros.</p>"); //$NON-NLS-1$
        out.println("<p class=\"item\"><b>Arquivos selecionados</b></p>"); //$NON-NLS-1$
        out.println("<p class=\"subitem\"><b>Marcadores:</b> P&aacute;gina contendo lista dos arquivos agrupados segundo classifica&ccedil;&atilde;o realizada pelo perito durante o exame pericial.</p>"); //$NON-NLS-1$
        out.println("<p class=\"subitem\"><b>Categorias (Opcional):</b> P&aacute;gina contendo lista dos arquivos agrupados segundo classifica&ccedil;&atilde;o autom&aacute;tica realizada pelo software pericial.</p>"); //$NON-NLS-1$
        out.println("<h3>Armazenamento e visualiza&ccedil;&atilde;o dos arquivos</h3>"); //$NON-NLS-1$
        out.println("<p>Os arquivos selecionados durante os exames foram renomeados e exportados para o diret&oacute;rio &quot;Exportados&quot; desta m&iacute;dia. Para obter os nomes e demais informa&ccedil;&otilde;es originais dos arquivos acesse &quot;Marcadores&quot;.</p>"); //$NON-NLS-1$
        out.println("<p>Recomenda-se configurar o programa navegador para modo de trabalho offline, de forma que o conte&uacute;do de arquivos hipertexto, tais como tempor&aacute;rios de navega&ccedil;&atilde;o na Internet, seja visualizado sem atualiza&ccedil;&atilde;o de dados em servidores externos.</p>"); //$NON-NLS-1$
        out.println("<p>Para visualizar o conte&uacute;do desta m&iacute;dia &oacute;tica num computador com sistema Linux/Unix, certifique-se de que as configura&ccedil;&otilde;es de locales do sistema est&atilde;o configuradas para o conjunto de caracteres ISO-8859-1.</p>"); //$NON-NLS-1$
        out.println("<p>Nem todos os arquivos exportados nesta m&iacute;dia podem ser abertos diretamente pelo programa navegador utilizado. Neste caso, pode ser necess&aacute;ria a instala&ccedil;&atilde;o do aplicativo apropriado (entre em contato com o suporte t&eacute;cnico de inform&aacute;tica do seu setor para informa&ccedil;&otilde;es sobre visualizadores dispon&iacute;veis).</p>"); //$NON-NLS-1$
        out.println("<h3>O Algoritmo SHA-256</h3>"); //$NON-NLS-1$
        out.println("<p>O SHA-256 (Secure Hash Algorithm de 256 bits) &eacute; um algoritmo que, a partir de uma mensagem de entrada de qualquer tamanho, gera uma sa&iacute;da de tamanho fixo de 256 bits (conhecido como c&oacute;digo de integridade, resumo ou hash), calculada a partir do conte&uacute;do dessa mensagem. A seguran&ccedil;a do procedimento consiste no fato de n&atilde;o ser conhecido m&eacute;todo computacionalmente vi&aacute;vel para produzir o mesmo c&oacute;digo de integridade a partir de duas mensagens distintas ou, a partir do c&oacute;digo de integridade, obter a mensagem de entrada.</p>"); //$NON-NLS-1$
        out.println("<p>Cada arquivo contido nesta m&iacute;dia &oacute;tica &eacute; tratado como se fosse uma mensagem que passa individualmente pelo processamento do algoritmo. Ao final, obt&eacute;m-se a rela&ccedil;&atilde;o dos nomes dos arquivos precedidos por seus respectivos c&oacute;digos de integridade em formato hexadecimal.</p>"); //$NON-NLS-1$
        out.println("<p>Esta m&iacute;dia &oacute;tica apresenta um arquivo denominado &quot;hashes.txt&quot; que cont&eacute;m a rela&ccedil;&atilde;o supracitada (listagem dos nomes dos arquivos precedidos do respectivo c&oacute;digo de integridade). Por sua vez, o c&oacute;digo de integridade do arquivo &quot;hashes.txt&quot; encontra-se no laudo impresso.</p>"); //$NON-NLS-1$
        out.println("<p>O acr&eacute;scimo, altera&ccedil;&atilde;o ou remo&ccedil;&atilde;o de um &uacute;nico caractere em um arquivo &eacute; condi&ccedil;&atilde;o suficiente para que o c&oacute;digo de integridade gerado seja diferente, tornando detect&aacute;vel a altera&ccedil;&atilde;o do conte&uacute;do desta m&iacute;dia &oacute;tica.</p>"); //$NON-NLS-1$
        out.println("<h3>Verifica&ccedil;&atilde;o da integridade da m&iacute;dia &oacute;tica</h3>"); //$NON-NLS-1$
        out.println("<p>Para verificar a integridade das m&iacute;dias &oacute;ticas, qualquer programa que suporte o algoritmo SHA-256 pode ser utilizado. O processo de verifica&ccedil;&atilde;o envolve duas etapas que devem ser executadas para cada m&iacute;dia:</p>"); //$NON-NLS-1$
        out.println("<p class=\"subitem\">c&aacute;lculo da integridade do arquivo &ldquo;hashes.txt&rdquo; e compara&ccedil;&atilde;o com o resultado presente no laudo impresso;<br>c&aacute;lculo da integridade dos arquivos contidos nesta m&iacute;dia e compara&ccedil;&atilde;o com os valores registrados no arquivo &ldquo;hashes.txt&rdquo;.</p>"); //$NON-NLS-1$
        out.println("<p>Um dos programas que pode ser utilizado para realizar essa verifica&ccedil;&atilde;o &eacute; o FSUM, dispon&iacute;vel gratuitamente na Internet no endere&ccedil;o http://www.slavasoft.com.</p>"); //$NON-NLS-1$
        out.println("<p>Assumindo que o sistema operacional utilizado para a verifica&ccedil;&atilde;o seja da fam&iacute;lia Windows, que o programa FSUM esteja armazenado na pasta &quot;c:\\fsum\\&quot; e que esta m&iacute;dia &oacute;tica esteja no drive &quot;d:\\&quot;, as seguintes etapas devem ser executadas:</p>"); //$NON-NLS-1$
        out.println("<p class=\"subitem\">Na janela do prompt de comando verificar o c&oacute;digo de integridade do arquivo &quot;hashes.txt&quot;, digitando:<br><span class=\"cmd\">c:\\fsum\\fsum -d&quot;d:&quot; -sha256 d:\\hashes.txt</span></p>"); //$NON-NLS-1$
        out.println("<p class=\"subitem\">Nesta mesma janela, verificar os c&oacute;digos de integridade dos arquivos contidos nesta m&iacute;dia &oacute;tica, digitando:<br><span class=\"cmd\">c:\\fsum\\fsum -jf -d&quot;d:&quot; -c d:\\hashes.txt</span></p>"); //$NON-NLS-1$
        out.println("<p>O resultado da etapa (1) ser&aacute; um c&oacute;digo de integridade apresentado na tela, que deve ser comparado com aquele presente no laudo impresso. Ambos devem ser id&ecirc;nticos, indicando que n&atilde;o houve altera&ccedil;&atilde;o nesta m&iacute;dia &oacute;tica.</p>"); //$NON-NLS-1$
        out.println("<p>O resultado esperado da etapa (2) &eacute; a correta verifica&ccedil;&atilde;o de todos os arquivos, ou seja, o programa n&atilde;o deve acusar nenhuma falha, indicando que todos os arquivos presentes nesta m&iacute;dia &oacute;tica est&atilde;o &iacute;ntegros conforme os c&oacute;digos de integridade calculados durante a produ&ccedil;&atilde;o do laudo.</p>"); //$NON-NLS-1$
        out.println("</div></div>"); //$NON-NLS-1$
    }

    private boolean matchesReportFilter(ExportedFile file, String filter) {
        String path = file == null ? "" : safe(file.relativePath).toLowerCase(); //$NON-NLS-1$
        if ("all".equals(filter)) return true; //$NON-NLS-1$
        if ("attachments".equals(filter)) return path.startsWith("anexos/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("audio".equals(filter)) return path.contains("/audios/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("video".equals(filter)) return path.contains("/videos/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("pdf".equals(filter)) return path.endsWith(".pdf"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("image".equals(filter)) return path.startsWith("screenshots/") || path.contains("/imagens/") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        if ("scan".equals(filter)) return path.endsWith(".pdf") || path.contains("/imagens/"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if ("whatsapp".equals(filter)) return path.startsWith("screenshots/") || path.startsWith("anexos/"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return false;
    }

    private boolean isImageForReport(ExportedFile file) {
        String path = file == null ? "" : safe(file.relativePath).toLowerCase(); //$NON-NLS-1$
        return path.startsWith("screenshots/") || path.contains("/imagens/") || path.endsWith(".png") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || path.endsWith(".gif") || path.endsWith(".bmp"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void writeCaptureReport(Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("<!DOCTYPE html>"); //$NON-NLS-1$
            out.println("<html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">"); //$NON-NLS-1$
            out.println("<title>Relatório de Captura de Chat</title>"); //$NON-NLS-1$
            out.println("<style>body{margin:0;font-family:Arial,sans-serif;background:#f4f4f4;color:#222}#menu{position:fixed;left:0;top:0;bottom:0;width:280px;background:#263238;color:white;padding:16px;overflow:auto;box-sizing:border-box}#menu h1{font-size:18px;margin:0 0 14px}#menu h2{font-size:15px;margin:18px 0 8px;color:#d9f3ff}#menu a,#menu div{color:white;display:block;padding:4px 0;text-decoration:none}.sub{padding-left:18px!important;color:#dce7ea!important}#content{margin-left:310px;padding:24px}.card{background:white;border:1px solid #ddd;border-radius:6px;padding:16px;margin-bottom:16px;box-shadow:0 1px 2px rgba(0,0,0,.05)}.frame img{max-width:100%;border:1px solid #ccc;background:white}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:6px;font-size:13px;vertical-align:top}th{background:#eee}.mono{font-family:Consolas,monospace;font-size:12px;word-break:break-all}.links a{margin-right:14px}</style>"); //$NON-NLS-1$
            out.println("</head><body>"); //$NON-NLS-1$
            out.println("<div id=\"menu\"><h1>Relatório de Captura</h1>"); //$NON-NLS-1$
            out.println("<h2>Informações</h2><a href=\"#info\">Informações</a><a href=\"#busca\">Busca por palavras-chave</a>"); //$NON-NLS-1$
            out.println("<h2>Marcadores</h2><div class=\"sub\">[Sem Marcador]</div><div class=\"sub\">Provavelmente Compartilhados via WhatsApp</div>"); //$NON-NLS-1$
            out.println("<h2>Categorias</h2><div class=\"sub\">Áudios</div><div class=\"sub\">Documentos PDF</div><div class=\"sub\">Outras Imagens</div><div class=\"sub\">Possíveis Digitalizações</div><div class=\"sub\">Vídeos</div><div class=\"sub\">WhatsApp</div>"); //$NON-NLS-1$
            out.println("<h2>Ajuda</h2><a href=\"#ajuda\">Relatório e Anexo</a></div>"); //$NON-NLS-1$
            out.println("<div id=\"content\">"); //$NON-NLS-1$
            out.println("<div class=\"card\" id=\"info\"><h2>Informações</h2>"); //$NON-NLS-1$
            out.println("<p><b>Status:</b> " + html(status) + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<p><b>Frames capturados:</b> " + frames.size() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<p><b>Arquivos exportados:</b> " + exported.size() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<p class=\"links\"><a href=\"hashes.txt\">hashes.txt</a><a href=\"Lista de Arquivos.csv\">Lista de Arquivos.csv</a><a href=\"manifest.json\">manifest.json</a></p></div>"); //$NON-NLS-1$
            out.println("<div class=\"card\" id=\"busca\"><h2>Busca por palavras-chave</h2><p>Use a busca do navegador neste relatório ou consulte os arquivos exportados na lista CSV.</p></div>"); //$NON-NLS-1$
            out.println("<div class=\"card\"><h2>Arquivos Exportados</h2><table><thead><tr><th>Nome</th><th>Tipo</th><th>Tamanho</th><th>SHA256</th><th>Caminho</th></tr></thead><tbody>"); //$NON-NLS-1$
            for (ExportedFile file : exported.values()) {
                out.println("<tr><td><a href=\"" + html(file.relativePath) + "\">" + html(fileName(file.relativePath)) + "</a></td><td>" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + html(fileType(file)) + "</td><td>" + file.size + "</td><td class=\"mono\">" + html(file.sha256) //$NON-NLS-1$ //$NON-NLS-2$
                        + "</td><td class=\"mono\">" + html(file.relativePath) + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            out.println("</tbody></table></div>"); //$NON-NLS-1$
            out.println("<div class=\"card\" id=\"ajuda\"><h2>Relatório e Anexo</h2><p>Este pacote contém imagens da captura do chat, anexos exportados, lista CSV e hashes SHA256 para verificação de integridade.</p></div>"); //$NON-NLS-1$
            out.println("</div></body></html>"); //$NON-NLS-1$
        }
    }

    private void writeManifest(Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported, CaptureSourceMetadata sourceMetadata) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("{"); //$NON-NLS-1$
            out.println("  \"version\": 1,"); //$NON-NLS-1$
            out.println("  \"status\": " + json(status) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"startedAt\": " + json(Instant.now().toString()) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"startMessageId\": " + json(captureStartMessageId) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"endMessageId\": " + json(captureEndMessageId) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"sourceMetadata\": " + buildInlineMetadataJson(sourceMetadata) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"frames\": ["); //$NON-NLS-1$
            for (int i = 0; i < frames.size(); i++) {
                CaptureFrame frame = frames.get(i);
                out.println("    {\"sequence\": " + frame.sequence + ", \"image\": " + json(frame.image) + ", \"firstVisibleMessageId\": " + json(frame.firstMessageId) + ", \"firstVisibleMessageIdRaw\": " + json(frame.firstVisibleMessageIdRaw) + ", \"residualTopBlockId\": " + json(frame.residualTopBlockId) + ", \"oversizedContinuation\": " + frame.oversizedContinuation + ", \"exportDocumentTop\": " + frame.exportDocumentTop + ", \"exportDocumentBottom\": " + frame.exportDocumentBottom + ", \"cropTopCss\": " + frame.cropTopCss + ", \"lastVisibleMessageId\": " + json(frame.lastMessageId) + ", \"visibleMediaHashes\": " + jsonArray(frame.hashes) + "}" + (i + 1 == frames.size() ? "" : ",")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
            }
            out.println("  ],"); //$NON-NLS-1$
            out.println("  \"exportedItems\": ["); //$NON-NLS-1$
            List<ExportedFile> files = new ArrayList<>(exported.values());
            for (int i = 0; i < files.size(); i++) {
                ExportedFile file = files.get(i);
                out.println("    {\"relativePath\": " + json(file.relativePath) + ", \"size\": " + file.size + ", \"md5\": " + json(file.md5) + ", \"sha256\": " + json(file.sha256) + ", \"chatHash\": " + json(file.chatHash) + "}" + (i + 1 == files.size() ? "" : ",")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            }
            out.println("  ]"); //$NON-NLS-1$
            out.println("}"); //$NON-NLS-1$
        }
    }

    private void writeText(Path path, String content) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.print(content);
        }
    }

    private CaptureSourceMetadata readCaptureSourceMetadata() {
        CaptureSourceMetadata metadata = new CaptureSourceMetadata();
        IItem item = captureSourceItem;
        if (item == null) {
            return metadata;
        }
        metadata.name = item.getName();
        metadata.size = item.getLength() == null ? 0 : item.getLength();
        metadata.type = item.getType();
        metadata.deleted = item.isDeleted();
        metadata.category = item.getCategories();
        metadata.creationDate = formatUtcDate(item.getCreationDate());
        metadata.modificationDate = formatUtcDate(item.getModDate());
        metadata.accessDate = formatUtcDate(item.getAccessDate());
        metadata.hash = item.getHash();
        metadata.path = item.getPath();
        metadata.text = readItemText(item);
        Metadata tikaMetadata = item.getMetadata();
        metadata.title = firstMetadataValue(tikaMetadata, "common:dc:title", "dc:title", "title"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        metadata.metadataText = buildTikaMetadataText(tikaMetadata, metadata);
        return metadata;
    }

    private String firstMetadataValue(Metadata metadata, String... names) {
        if (metadata == null) {
            return ""; //$NON-NLS-1$
        }
        for (String name : names) {
            String value = metadata.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return ""; //$NON-NLS-1$
    }

    private String buildTikaMetadataText(Metadata metadata, CaptureSourceMetadata sourceMetadata) {
        StringBuilder out = new StringBuilder();
        if (metadata != null) {
            String[] names = metadata.names();
            Arrays.sort(names);
            for (String name : names) {
                String[] values = metadata.getValues(name);
                if (values == null || values.length == 0) {
                    continue;
                }
                out.append(name).append(": ");
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        out.append(' ');
                    }
                    out.append(values[i]);
                }
                out.append("\r\n"); //$NON-NLS-1$
            }
        }
        appendMetadataIfMissing(out, "Content-Length", sourceMetadata.size <= 0 ? "" : Long.toString(sourceMetadata.size)); //$NON-NLS-1$ //$NON-NLS-2$
        appendMetadataIfMissing(out, "hash", sourceMetadata.hash); //$NON-NLS-1$
        appendMetadataIfMissing(out, "path", sourceMetadata.path); //$NON-NLS-1$
        return out.toString();
    }

    private void appendMetadataIfMissing(StringBuilder out, String name, String value) {
        if (value == null || value.isBlank() || out.indexOf(name + ":") >= 0) { //$NON-NLS-1$
            return;
        }
        out.append(name).append(": ").append(value).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String readItemText(IItem item) {
        try (Reader reader = item.getTextReader()) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
            return text.toString();
        } catch (Exception e) {
            LOGGER.debug("Unable to read parsed text from capture source item", e); //$NON-NLS-1$
            try {
                return safe(item.getParsedTextCache());
            } catch (Exception ignored) {
                return ""; //$NON-NLS-1$
            }
        }
    }

    private String buildMetadataText(CaptureSourceMetadata metadata) {
        StringBuilder out = new StringBuilder();
        out.append("Propriedades Básicas\r\n"); //$NON-NLS-1$
        out.append("nome\t").append(safe(metadata.name)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("tamanho\t").append(formatNumber(metadata.size)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("tipo\t").append(safe(metadata.type)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("deletado\t").append(metadata.deleted).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("categoria\t").append(safe(metadata.category)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("criacao\t").append(safe(metadata.creationDate)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("modificacao\t").append(safe(metadata.modificationDate)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("acesso\t").append(safe(metadata.accessDate)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("hash\t").append(safe(metadata.hash)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("caminho\t").append(safe(metadata.path)).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        return out.toString();
    }

    private String buildMetadataJson(CaptureSourceMetadata metadata) {
        return "{\n" //$NON-NLS-1$
                + "  \"nome\": " + json(metadata.name) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"tamanho\": " + metadata.size + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"tipo\": " + json(metadata.type) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"deletado\": " + metadata.deleted + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"categoria\": " + json(metadata.category) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"criacao\": " + json(metadata.creationDate) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"modificacao\": " + json(metadata.modificationDate) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"acesso\": " + json(metadata.accessDate) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"hash\": " + json(metadata.hash) + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "  \"caminho\": " + json(metadata.path) + "\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "}\n"; //$NON-NLS-1$
    }

    private String buildCaptureText(CaptureSourceMetadata metadata, List<CaptureFrame> frames) {
        StringBuilder out = new StringBuilder();
        String title = nonBlank(metadata.title, metadata.name);
        out.append(nonBlank(metadata.title, inferChatIdentifier(metadata))).append("\r\n\r\n\r\n"); //$NON-NLS-1$
        out.append("                    ").append(safe(title)).append("\r\n\r\n\r\n"); //$NON-NLS-1$ //$NON-NLS-2$

        Set<String> seen = new HashSet<>();
        StringBuilder messages = new StringBuilder();
        for (CaptureFrame frame : frames) {
            for (CaptureBlock block : frame.blocks) {
                String text = safe(block.text).trim();
                if (text.isBlank() || !seen.add(safe(block.id))) {
                    continue;
                }
                messages.append(text).append("\r\n\r\n"); //$NON-NLS-1$
            }
        }
        if (messages.length() == 0 && metadata.text != null && !metadata.text.isBlank()) {
            messages.append(metadata.text.trim()).append("\r\n\r\n"); //$NON-NLS-1$
        }
        out.append(messages);
        out.append("METADADOS: \r\n"); //$NON-NLS-1$
        out.append(safe(metadata.metadataText));
        out.append("----------------------------------- \r\n"); //$NON-NLS-1$
        return out.toString();
    }

    private String inferChatIdentifier(CaptureSourceMetadata metadata) {
        if (metadata == null) {
            return ""; //$NON-NLS-1$
        }
        String title = safe(metadata.title);
        if (!title.isBlank()) {
            return title;
        }
        String name = safe(metadata.name);
        int dash = name.lastIndexOf(" - "); //$NON-NLS-1$
        if (dash >= 0 && dash + 3 < name.length()) {
            return name.substring(dash + 3).trim();
        }
        return name;
    }

    private String buildWhatsAppCoordinatesJson(String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported, CaptureSourceMetadata metadata) {
        StringBuilder out = new StringBuilder();
        out.append("{\n"); //$NON-NLS-1$
        out.append("  \"version\": 1,\n"); //$NON-NLS-1$
        out.append("  \"status\": ").append(json(status)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"generatedAt\": ").append(json(Instant.now().toString())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"mode\": \"vertical-image-chat\",\n"); //$NON-NLS-1$
        out.append("  \"sourceMetadata\": ").append(buildInlineMetadataJson(metadata)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"frames\": [\n"); //$NON-NLS-1$
        for (int i = 0; i < frames.size(); i++) {
            CaptureFrame frame = frames.get(i);
            out.append("    {\"sequence\": ").append(frame.sequence) //$NON-NLS-1$
                    .append(", \"image\": ").append(json(frame.image)) //$NON-NLS-1$
                    .append(", \"width\": ").append(frame.imageWidth) //$NON-NLS-1$
                    .append(", \"height\": ").append(frame.imageHeight) //$NON-NLS-1$
                    .append(", \"firstVisibleMessageId\": ").append(json(frame.firstMessageId)) //$NON-NLS-1$
                    .append(", \"firstVisibleMessageIdRaw\": ").append(json(frame.firstVisibleMessageIdRaw)) //$NON-NLS-1$
                    .append(", \"residualTopBlockId\": ").append(json(frame.residualTopBlockId)) //$NON-NLS-1$
                    .append(", \"oversizedContinuation\": ").append(frame.oversizedContinuation) //$NON-NLS-1$
                    .append(", \"exportDocumentTop\": ").append(frame.exportDocumentTop) //$NON-NLS-1$
                    .append(", \"exportDocumentBottom\": ").append(frame.exportDocumentBottom) //$NON-NLS-1$
                    .append(", \"cropTopCss\": ").append(frame.cropTopCss) //$NON-NLS-1$
                    .append(", \"lastVisibleMessageId\": ").append(json(frame.lastMessageId)) //$NON-NLS-1$
                    .append(", \"blocks\": ["); //$NON-NLS-1$
            for (int j = 0; j < frame.blocks.size(); j++) {
                CaptureBlock block = frame.blocks.get(j);
                ExportedFile linkedFile = findExportedByHash(exported, block.hash);
                out.append("{\"id\": ").append(json(block.id)) //$NON-NLS-1$
                        .append(", \"type\": ").append(json(coordinateBlockType(block, linkedFile))) //$NON-NLS-1$
                        .append(", \"x\": ").append(block.x) //$NON-NLS-1$
                        .append(", \"y\": ").append(block.y) //$NON-NLS-1$
                        .append(", \"width\": ").append(block.width) //$NON-NLS-1$
                        .append(", \"height\": ").append(block.height) //$NON-NLS-1$
                        .append(", \"documentTop\": ").append(block.documentTop) //$NON-NLS-1$
                        .append(", \"documentBottom\": ").append(block.documentBottom) //$NON-NLS-1$
                        .append(", \"text\": ").append(json(block.text)) //$NON-NLS-1$
                        .append(", \"hash\": ").append(json(block.hash)) //$NON-NLS-1$
                        .append(", \"file\": ").append(json(linkedFile == null ? null : linkedFile.relativePath)) //$NON-NLS-1$
                        .append("}"); //$NON-NLS-1$
                if (j + 1 < frame.blocks.size()) {
                    out.append(", "); //$NON-NLS-1$
                }
            }
            out.append("]}"); //$NON-NLS-1$
            if (i + 1 < frames.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  ],\n"); //$NON-NLS-1$
        out.append("  \"exportedItems\": [\n"); //$NON-NLS-1$
        List<ExportedFile> files = new ArrayList<>(exported.values());
        for (int i = 0; i < files.size(); i++) {
            ExportedFile file = files.get(i);
            out.append("    {\"relativePath\": ").append(json(file.relativePath)) //$NON-NLS-1$
                    .append(", \"name\": ").append(json(displayName(file))) //$NON-NLS-1$
                    .append(", \"type\": ").append(json(fileType(file))) //$NON-NLS-1$
                    .append(", \"size\": ").append(file.size) //$NON-NLS-1$
                    .append(", \"creationDate\": ").append(json(file.creationDate)) //$NON-NLS-1$
                    .append(", \"modificationDate\": ").append(json(file.modificationDate)) //$NON-NLS-1$
                    .append(", \"accessDate\": ").append(json(file.accessDate)) //$NON-NLS-1$
                    .append(", \"md5\": ").append(json(file.md5)) //$NON-NLS-1$
                    .append(", \"sha256\": ").append(json(file.sha256)) //$NON-NLS-1$
                    .append(", \"chatHash\": ").append(json(file.chatHash)) //$NON-NLS-1$
                    .append("}"); //$NON-NLS-1$
            if (i + 1 < files.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  ]\n"); //$NON-NLS-1$
        out.append("}\n"); //$NON-NLS-1$
        return out.toString();
    }

    private String coordinateBlockType(CaptureBlock block, ExportedFile linkedFile) {
        if (linkedFile != null && isImageForReport(linkedFile)) {
            return "image"; //$NON-NLS-1$
        }
        return block == null ? "message" : safe(block.type); //$NON-NLS-1$
    }

    private ExportedFile findExportedByHash(Map<String, ExportedFile> exported, String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        for (ExportedFile file : exported.values()) {
            if (hash.equalsIgnoreCase(safe(file.chatHash)) || hash.equalsIgnoreCase(safe(file.md5)) || hash.equalsIgnoreCase(safe(file.sha256))) {
                return file;
            }
        }
        return null;
    }

    private String buildInlineMetadataJson(CaptureSourceMetadata metadata) {
        return "{\"nome\": " + json(metadata == null ? null : metadata.name) //$NON-NLS-1$
                + ", \"tamanho\": " + (metadata == null ? 0 : metadata.size) //$NON-NLS-1$
                + ", \"tipo\": " + json(metadata == null ? null : metadata.type) //$NON-NLS-1$
                + ", \"deletado\": " + (metadata != null && metadata.deleted) //$NON-NLS-1$
                + ", \"categoria\": " + json(metadata == null ? null : metadata.category) //$NON-NLS-1$
                + ", \"criacao\": " + json(metadata == null ? null : metadata.creationDate) //$NON-NLS-1$
                + ", \"modificacao\": " + json(metadata == null ? null : metadata.modificationDate) //$NON-NLS-1$
                + ", \"acesso\": " + json(metadata == null ? null : metadata.accessDate) //$NON-NLS-1$
                + ", \"hash\": " + json(metadata == null ? null : metadata.hash) //$NON-NLS-1$
                + ", \"caminho\": " + json(metadata == null ? null : metadata.path) + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void writeWhatsAppHtml(Path path, String coordinatesJson) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>WhatsApp</title>"); //$NON-NLS-1$
            out.println("<style>body{margin:0;background:#efe7dc;font-family:Arial,sans-serif;color:#222}.top{position:sticky;top:0;z-index:5;background:#075e54;color:white;padding:12px 18px;box-shadow:0 1px 4px rgba(0,0,0,.25)}.top h1{font-size:18px;margin:0}.wrap{max-width:960px;margin:0 auto;padding:18px 12px 80px}.frame{position:relative;margin:0 auto 6px;line-height:0;background:#ddd;box-shadow:0 1px 3px rgba(0,0,0,.15)}.frame img{width:100%;height:auto;display:block}.hotspot{position:absolute;border:2px solid transparent;background:rgba(37,211,102,.001);cursor:pointer;box-sizing:border-box}.hotspot:hover{border-color:#25d366;background:rgba(37,211,102,.16)}.hotspot[data-type='audio'],.hotspot[data-type='video'],.hotspot[data-type='image'],.hotspot[data-type='file']{background:rgba(3,169,244,.001)}.hotspot[data-type='audio']:hover,.hotspot[data-type='video']:hover,.hotspot[data-type='image']:hover,.hotspot[data-type='file']:hover{border-color:#03a9f4;background:rgba(3,169,244,.16)}#side{position:fixed;right:-360px;top:0;bottom:0;width:320px;background:#fff;z-index:20;box-shadow:-2px 0 10px rgba(0,0,0,.25);transition:right .2s ease;padding:18px;overflow:auto}#side.open{right:0}#side h2{font-size:18px;margin:0 0 12px}#side textarea{width:100%;height:180px;box-sizing:border-box}.btn{display:inline-block;margin:8px 6px 0 0;padding:8px 12px;border:0;background:#075e54;color:white;text-decoration:none;cursor:pointer}.btn.secondary{background:#555}#modal{position:fixed;inset:0;display:none;align-items:center;justify-content:center;background:rgba(0,0,0,.74);z-index:30;padding:24px;box-sizing:border-box}#modal.open{display:flex}.modal-card{max-width:min(920px,96vw);max-height:92vh;background:#111;color:white;padding:14px;box-sizing:border-box}.modal-card img,.modal-card video{max-width:100%;max-height:75vh}.modal-card audio{width:min(720px,88vw)}.muted{color:#666;font-size:13px}</style>"); //$NON-NLS-1$
            out.println("</head><body><div class=\"top\"><h1>WhatsApp - visualiza&ccedil;&atilde;o por imagens</h1></div><div class=\"wrap\" id=\"chat\"></div><aside id=\"side\"></aside><div id=\"modal\" onclick=\"closeModal(event)\"><div class=\"modal-card\" id=\"modalCard\"></div></div>"); //$NON-NLS-1$
            out.println("<script id=\"coords\" type=\"application/json\">" + coordinatesJson.replace("</", "<\\/") + "</script>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<script>var DATA=JSON.parse(document.getElementById('coords').textContent);var chat=document.getElementById('chat'),side=document.getElementById('side'),modal=document.getElementById('modal'),modalCard=document.getElementById('modalCard');function esc(s){return String(s||'').replace(/[&<>\\\"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\\\"':'&quot;'}[c];});}function pct(v,total){return total?((v*100/total)+'%'):'0%';}function isImageFile(p){return /\\.(png|jpe?g|webp|gif|bmp)$/i.test(String(p||'').split('?')[0]);}function itemTitle(b){if(b.type==='date')return 'Data';if(b.type==='audio')return 'Audio';if(b.type==='video')return 'Video';if(b.type==='image'||isImageFile(b.file))return 'Imagem';if(b.type==='file')return 'Arquivo';return 'Mensagem';}function openSide(html){side.innerHTML=html;side.classList.add('open');}function closeSide(){side.classList.remove('open');}function copyText(){var t=document.getElementById('copyText');if(!t)return;t.select();document.execCommand('copy');}function showText(b){openSide('<h2>'+itemTitle(b)+'</h2><textarea id=\"copyText\">'+esc(b.text||'')+'</textarea><br><button class=\"btn\" onclick=\"copyText()\">Copiar</button><button class=\"btn secondary\" onclick=\"closeSide()\">Fechar</button>');}function showFile(b){if(!b.file){showText(b);return;}openSide('<h2>'+itemTitle(b)+'</h2><p class=\"muted\">'+esc(b.text||b.file)+'</p><a class=\"btn\" href=\"'+esc(b.file)+'\" download>Baixar</a><a class=\"btn secondary\" href=\"'+esc(b.file)+'\" target=\"_blank\">Abrir</a><button class=\"btn secondary\" onclick=\"closeSide()\">Fechar</button>');}function openModal(html){modalCard.innerHTML=html+'<br><button class=\"btn secondary\" onclick=\"modal.classList.remove(\\'open\\')\">Fechar</button>';modal.classList.add('open');}function closeModal(e){if(e.target===modal)modal.classList.remove('open');}function activate(b){if(b.type==='audio'&&b.file){openModal('<h2>Audio</h2><audio controls autoplay src=\"'+esc(b.file)+'\"></audio>');return;}if(b.type==='video'&&b.file){openModal('<h2>Video</h2><video controls autoplay src=\"'+esc(b.file)+'\"></video>');return;}if(b.file&&(b.type==='image'||isImageFile(b.file))){openModal('<h2>Imagem</h2><img src=\"'+esc(b.file)+'\"><br><a class=\"btn\" href=\"'+esc(b.file)+'\" download>Baixar imagem</a><a class=\"btn secondary\" href=\"'+esc(b.file)+'\" target=\"_blank\">Abrir em nova aba</a>');return;}if(b.type==='file'){showFile(b);return;}showText(b);}DATA.frames.forEach(function(f){var box=document.createElement('div');box.className='frame';box.style.maxWidth=f.width+'px';var img=document.createElement('img');img.src=f.image;img.alt='Frame '+f.sequence;box.appendChild(img);(f.blocks||[]).forEach(function(b){var h=document.createElement('button');h.className='hotspot';h.type='button';h.dataset.type=b.type;h.title=itemTitle(b);h.style.left=pct(b.x,f.width);h.style.top=pct(b.y,f.height);h.style.width=pct(b.width,f.width);h.style.height=pct(b.height,f.height);h.onclick=function(ev){ev.preventDefault();activate(b);};box.appendChild(h);});chat.appendChild(box);});</script>"); //$NON-NLS-1$
            out.println("</body></html>"); //$NON-NLS-1$
        }
    }

    private CapturePlan captureImage(Path path, boolean includeHeader, String previousLastCompleteMessageId, Set<String> capturedBlockIds) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = new Exception[1];
        final CapturePlan[] planHolder = new CapturePlan[1];
        Platform.runLater(() -> {
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setTransform(Transform.scale(CHAT_CAPTURE_SCALE, CHAT_CAPTURE_SCALE));
                WritableImage image = htmlViewer.snapshot(params, null);
                BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
                CapturePlan plan = getCapturePlan(includeHeader, previousLastCompleteMessageId);
                planHolder[0] = plan;
                CaptureRect rect = plan.rect;
                int outputWidth = buffered.getWidth();
                int outputHeight = buffered.getHeight();
                if (rect != null) {
                    int x = Math.max(0, Math.min((int) Math.floor(rect.x * CHAT_CAPTURE_SCALE), buffered.getWidth() - 1));
                    int y = Math.max(0, Math.min((int) Math.floor(rect.y * CHAT_CAPTURE_SCALE), buffered.getHeight() - 1));
                    int width = Math.max(1, Math.min((int) Math.ceil(rect.width * CHAT_CAPTURE_SCALE), buffered.getWidth() - x));
                    int height = Math.max(1, Math.min((int) Math.ceil(rect.height * CHAT_CAPTURE_SCALE), buffered.getHeight() - y));
                    buffered = buffered.getSubimage(x, y, width, height);
                    outputWidth = width;
                    outputHeight = height;
                }
                buffered = normalizeCapturedImage(buffered, plan, includeHeader, previousLastCompleteMessageId, capturedBlockIds);
                if (buffered == null) {
                    planHolder[0] = null;
                    return;
                }
                outputWidth = buffered.getWidth();
                outputHeight = buffered.getHeight();
                plan.imageWidth = outputWidth;
                plan.imageHeight = outputHeight;
                mapCaptureBlocksToImage(plan);
                ImageIO.write(buffered, "png", path.toFile()); //$NON-NLS-1$
            } catch (Exception e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IOException("Timeout capturing chat screenshot"); //$NON-NLS-1$
        }
        if (error[0] != null) {
            throw error[0];
        }
        return planHolder[0];
    }

    private BufferedImage normalizeCapturedImage(BufferedImage image, CapturePlan plan, boolean firstFrame, String previousLastCompleteMessageId, Set<String> capturedBlockIds) {
        if (image == null || plan == null || plan.rect == null) {
            return image;
        }
        plan.exportDocumentTop = plan.documentTop;
        plan.exportDocumentBottom = plan.documentBottom;
        plan.cropTopCss = 0;
        if (firstFrame) {
            image = cropCaptureBeforeStartMessage(image, plan);
        } else {
            cropCaptureBeforeRepeatedTopBlock(plan, previousLastCompleteMessageId, capturedBlockIds);
        }
        cropCaptureAfterEndMessage(plan);
        int cropTopPx = (int) Math.round(plan.cropTopCss * CHAT_CAPTURE_SCALE);
        if (!firstFrame && cropTopPx > 0 && cropTopPx < image.getHeight()) {
            image = image.getSubimage(0, cropTopPx, image.getWidth(), Math.max(1, image.getHeight() - cropTopPx));
        }
        int targetHeightPx = (int) Math.round(plan.rect.height * CHAT_CAPTURE_SCALE);
        if (targetHeightPx < image.getHeight()) {
            image = image.getSubimage(0, 0, image.getWidth(), Math.max(1, targetHeightPx));
        }
        plan.blocks.removeIf(block -> block.documentBottom <= plan.exportDocumentTop || block.documentTop >= plan.exportDocumentBottom);
        refreshCapturePlanMessageBounds(plan);
        return plan.blocks.isEmpty() ? null : image;
    }

    private BufferedImage cropCaptureBeforeStartMessage(BufferedImage image, CapturePlan plan) {
        if (image == null || plan == null || plan.blocks == null || captureStartMessageId == null) {
            return image;
        }
        for (CaptureBlock block : plan.blocks) {
            if (captureStartMessageId.equals(block.id) && block.documentTop > plan.exportDocumentTop) {
                double headerBottomCss = Math.max(0, Math.min(plan.headerBottomCss, block.top));
                double removedCss = block.top - headerBottomCss;
                if (headerBottomCss <= 0 || removedCss <= 0) {
                    return image;
                }
                int headerBottomPx = Math.max(0, Math.min((int) Math.round(headerBottomCss * CHAT_CAPTURE_SCALE), image.getHeight()));
                int contentStartPx = Math.max(headerBottomPx, Math.min((int) Math.round(block.top * CHAT_CAPTURE_SCALE), image.getHeight()));
                int newHeight = Math.max(1, headerBottomPx + image.getHeight() - contentStartPx);
                int imageType = image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : image.getType();
                BufferedImage joined = new BufferedImage(image.getWidth(), newHeight, imageType);
                Graphics2D g = joined.createGraphics();
                try {
                    if (headerBottomPx > 0) {
                        g.drawImage(image, 0, 0, image.getWidth(), headerBottomPx, 0, 0, image.getWidth(), headerBottomPx, null);
                    }
                    g.drawImage(image, 0, headerBottomPx, image.getWidth(), newHeight, 0, contentStartPx, image.getWidth(), image.getHeight(), null);
                } finally {
                    g.dispose();
                }
                plan.cropTopCss = removedCss;
                plan.exportDocumentTop = block.documentTop;
                plan.preservedHeaderCss = headerBottomCss;
                plan.rect = new CaptureRect(plan.rect.x, 0, plan.rect.width,
                        Math.max(1, plan.preservedHeaderCss + plan.exportDocumentBottom - plan.exportDocumentTop));
                for (CaptureBlock current : plan.blocks) {
                    if (current.documentTop >= plan.exportDocumentTop) {
                        current.top = headerBottomCss + (current.top - block.top);
                    }
                }
                return joined;
            }
        }
        return image;
    }

    private void cropCaptureBeforeRepeatedTopBlock(CapturePlan plan, String previousLastCompleteMessageId, Set<String> capturedBlockIds) {
        if (plan == null || plan.blocks == null || plan.blocks.isEmpty()) {
            return;
        }
        double cropDocumentTop = plan.exportDocumentTop;
        for (CaptureBlock block : plan.blocks) {
            if (block.documentBottom <= cropDocumentTop) {
                continue;
            }
            boolean alreadyCaptured = (capturedBlockIds != null && capturedBlockIds.contains(block.id))
                    || (previousLastCompleteMessageId != null && previousLastCompleteMessageId.equals(block.id));
            if (!alreadyCaptured) {
                break;
            }
            if (block.documentBottom < plan.exportDocumentBottom) {
                cropDocumentTop = Math.max(cropDocumentTop, block.documentBottom);
                continue;
            }
            break;
        }
        if (cropDocumentTop > plan.exportDocumentTop) {
            double cropTopCss = cropDocumentTop - plan.exportDocumentTop;
            plan.cropTopCss = Math.max(0, cropTopCss);
            plan.exportDocumentTop = cropDocumentTop;
            plan.rect = new CaptureRect(plan.rect.x, plan.rect.y + plan.cropTopCss, plan.rect.width,
                    Math.max(1, plan.exportDocumentBottom - plan.exportDocumentTop));
        }
    }

    private void addCapturedBlockIds(Set<String> capturedBlockIds, CapturePlan plan) {
        if (capturedBlockIds == null || plan == null || plan.blocks == null) {
            return;
        }
        for (CaptureBlock block : plan.blocks) {
            if (block.id != null && block.documentTop >= plan.exportDocumentTop && block.documentBottom <= plan.exportDocumentBottom) {
                capturedBlockIds.add(block.id);
            }
        }
    }

    private void cropCaptureAfterEndMessage(CapturePlan plan) {
        if (plan == null || plan.blocks == null || captureEndMessageId == null) {
            return;
        }
        for (CaptureBlock block : plan.blocks) {
            if (captureEndMessageId.equals(block.id)) {
                if (block.complete || block.documentBottom <= plan.exportDocumentBottom) {
                    double wantedBottom = block.documentBottom + CHAT_CAPTURE_END_MARGIN_CSS;
                    double clippedBottom = Math.min(wantedBottom, plan.exportDocumentBottom);
                    plan.exportDocumentBottom = Math.max(plan.exportDocumentTop + 1, clippedBottom);
                    plan.rect = new CaptureRect(plan.rect.x, plan.rect.y, plan.rect.width,
                            Math.max(1, plan.preservedHeaderCss + plan.exportDocumentBottom - plan.exportDocumentTop));
                    plan.endMessageCaptured = true;
                }
                return;
            }
        }
    }

    private void refreshCapturePlanMessageBounds(CapturePlan plan) {
        if (plan == null || plan.blocks == null || plan.blocks.isEmpty()) {
            return;
        }
        plan.firstEligibleMessageId = plan.blocks.get(0).id;
        plan.lastVisibleMessageId = plan.blocks.get(plan.blocks.size() - 1).id;
    }

    private void setCaptureHeaderVisible(boolean visible) {
        executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.setHeaderVisible(" + visible + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void restoreCaptureHeader() {
        executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.restoreHeader();}"); //$NON-NLS-1$
    }

    private void setCaptureInteractionEnabled(boolean enabled) {
        if (enabled) {
            executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.enableInteraction();}"); //$NON-NLS-1$
            setHtmlViewerMouseTransparent(false);
        } else {
            setHtmlViewerMouseTransparent(true);
            executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.disableInteraction();}"); //$NON-NLS-1$
        }
    }

    private void setHtmlViewerMouseTransparent(boolean transparent) {
        if (Platform.isFxApplicationThread()) {
            htmlViewer.setMouseTransparent(transparent);
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            htmlViewer.setMouseTransparent(transparent);
            latch.countDown();
        });
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getMessageIdAt(double x, double y) {
        Object result = executeScriptAndWait("window.ipedChatCapture ? window.ipedChatCapture.messageAt(" + x + "," + y + ") : null;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return result == null ? null : result.toString();
    }

    private boolean isDateCaptureElement(String id) {
        return id != null && id.startsWith("iped-capture-date-"); //$NON-NLS-1$
    }

    private double getScrollY() {
        Object result = executeScriptAndWait("window.scrollY;"); //$NON-NLS-1$
        return result instanceof Number ? ((Number) result).doubleValue() : -1;
    }

    private boolean isEndVisible() {
        Object result = executeScriptAndWait("window.ipedChatCapture.isVisible(" + js(captureEndMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private boolean advanceCaptureByMessageBoundary(CapturePlan plan, String previousLastCapturedMessageId) {
        if (plan == null || plan.lastCompleteMessageId == null || plan.nextMessageId == null) {
            return false;
        }
        if (plan.lastCompleteMessageId.equals(plan.nextMessageId)) {
            return false;
        }
        if (previousLastCapturedMessageId != null && previousLastCapturedMessageId.equals(plan.nextMessageId)) {
            return false;
        }
        Object result = executeScriptAndWait("window.ipedChatCapture.scrollToNextMessageAfter(" + js(plan.lastCompleteMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private void scrollAfterCapturePlan(CapturePlan plan) {
        if (plan != null && plan.scrollAdvance > 0) {
            executeScriptAndWait("window.scrollBy(0," + plan.scrollAdvance + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        } else if (plan != null && plan.lastCompleteMessageId != null) {
            executeScriptAndWait("window.ipedChatCapture.scrollAfterBlock(" + js(plan.lastCompleteMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            executeScriptAndWait("window.ipedChatCapture.scrollNextCaptureStep();"); //$NON-NLS-1$
        }
    }

    private Set<String> getVisibleMediaHashes(double safeBottom) {
        Object result = executeScriptAndWait("window.ipedChatCapture.getVisibleMediaHashes(" + safeBottom + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        Set<String> hashes = new HashSet<>();
        if (result != null && !result.toString().isBlank()) {
            for (String hash : result.toString().split("\\|")) { //$NON-NLS-1$
                if (!hash.isBlank()) {
                    hashes.add(hash);
                }
            }
        }
        return hashes;
    }

    private Set<String> getCaptureBlockHashes(CapturePlan plan) {
        Set<String> hashes = new HashSet<>();
        if (plan == null || plan.blocks == null) {
            return hashes;
        }
        for (CaptureBlock block : plan.blocks) {
            if (block.hash != null && !block.hash.isBlank()) {
                hashes.add(block.hash);
            }
        }
        return hashes;
    }

    private void setCaptureMarksVisible(boolean visible) {
        if (visible) {
            executeScriptAndWait("document.documentElement.classList.remove('iped-capture-running');"); //$NON-NLS-1$
            highlightCaptureMarks();
        } else {
            executeScriptAndWait("document.documentElement.classList.add('iped-capture-running');if(window.ipedChatCapture){window.ipedChatCapture.clear();}"); //$NON-NLS-1$
        }
    }

    private CapturePlan getCapturePlan(boolean includeHeader, String previousLastCompleteMessageId) {
        Object result = webEngine.executeScript("window.ipedChatCapture ? window.ipedChatCapture.getCapturePlan(" + includeHeader + "," + js(previousLastCompleteMessageId) + ") : null;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (result == null) {
            return CapturePlan.fallback();
        }
        String[] parts = result.toString().split("\\|", -1); //$NON-NLS-1$
        if (parts.length < 16) {
            return CapturePlan.fallback();
        }
        try {
            CaptureRect rect = new CaptureRect(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            CapturePlan plan = new CapturePlan();
            plan.rect = rect;
            plan.documentTop = Double.parseDouble(parts[4]);
            plan.documentBottom = Double.parseDouble(parts[5]);
            plan.exportDocumentTop = plan.documentTop;
            plan.exportDocumentBottom = plan.documentBottom;
            plan.firstVisibleMessageIdRaw = emptyToNull(parts[6]);
            plan.lastVisibleMessageId = emptyToNull(parts[7]);
            plan.safeBottom = Double.parseDouble(parts[8]);
            plan.scrollAdvance = Double.parseDouble(parts[9]);
            plan.lastCompleteMessageId = emptyToNull(parts[10]);
            plan.nextMessageId = emptyToNull(parts[11]);
            plan.firstEligibleMessageId = emptyToNull(parts[12]);
            plan.residualTopBlockId = emptyToNull(parts[13]);
            plan.residualVisibleHeight = Double.parseDouble(parts[14]);
            plan.oversizedContinuation = "1".equals(parts[15]); //$NON-NLS-1$
            plan.hasCompleteBlock = plan.lastCompleteMessageId != null;
            if (parts.length > 16) {
                plan.blocks = parseCaptureBlocks(parts[16]);
            }
            if (parts.length > 17) {
                plan.headerBottomCss = Double.parseDouble(parts[17]);
            }
            if (plan.firstEligibleMessageId == null) {
                plan.firstEligibleMessageId = plan.firstVisibleMessageIdRaw;
            }
            return plan;
        } catch (NumberFormatException e) {
            return CapturePlan.fallback();
        }
    }

    private List<CaptureBlock> parseCaptureBlocks(String encoded) {
        List<CaptureBlock> blocks = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return blocks;
        }
        for (String record : encoded.split(";", -1)) { //$NON-NLS-1$
            String[] fields = record.split(",", -1); //$NON-NLS-1$
            if (fields.length < 12) {
                continue;
            }
            try {
                CaptureBlock block = new CaptureBlock();
                block.id = decodeCaptureField(fields[0]);
                block.type = decodeCaptureField(fields[1]);
                block.left = Double.parseDouble(fields[2]);
                block.top = Double.parseDouble(fields[3]);
                block.cssWidth = Double.parseDouble(fields[4]);
                block.cssHeight = Double.parseDouble(fields[5]);
                block.visibleHeight = Double.parseDouble(fields[6]);
                block.documentTop = Double.parseDouble(fields[7]);
                block.documentBottom = Double.parseDouble(fields[8]);
                block.complete = "1".equals(fields[9]); //$NON-NLS-1$
                block.hash = decodeCaptureField(fields[10]);
                block.text = decodeCaptureField(fields[11]);
                blocks.add(block);
            } catch (Exception e) {
                LOGGER.debug("Unable to parse capture block {}", record, e); //$NON-NLS-1$
            }
        }
        return blocks;
    }

    private String decodeCaptureField(String value) {
        try {
            return URLDecoder.decode(safe(value), StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return safe(value);
        }
    }

    private void mapCaptureBlocksToImage(CapturePlan plan) {
        if (plan == null || plan.blocks == null || plan.blocks.isEmpty()) {
            return;
        }
        double originX = plan.rect == null ? 0 : plan.rect.x;
        double originY = plan.rect == null ? 0 : plan.rect.y;
        for (CaptureBlock block : plan.blocks) {
            int x = (int) Math.round((block.left - originX) * CHAT_CAPTURE_SCALE);
            int y = (int) Math.round((block.top - originY) * CHAT_CAPTURE_SCALE);
            int width = Math.max(1, (int) Math.round(block.cssWidth * CHAT_CAPTURE_SCALE));
            int height = Math.max(1, (int) Math.round(block.cssHeight * CHAT_CAPTURE_SCALE));
            int x2 = Math.min(plan.imageWidth, Math.max(0, x + width));
            int y2 = Math.min(plan.imageHeight, Math.max(0, y + height));
            block.x = Math.max(0, Math.min(plan.imageWidth, x));
            block.y = Math.max(0, Math.min(plan.imageHeight, y));
            block.width = Math.max(1, x2 - block.x);
            block.height = Math.max(1, y2 - block.y);
        }
    }

    private Object executeScriptAndWait(String script) {
        if (Platform.isFxApplicationThread()) {
            return webEngine.executeScript(script);
        }
        CountDownLatch latch = new CountDownLatch(1);
        final Object[] result = new Object[1];
        Platform.runLater(() -> {
            try {
                result[0] = webEngine.executeScript(script);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private void installChatCaptureSupport() {
        if (webEngine == null || webEngine.getDocument() == null) {
            return;
        }
        try {
            webEngine.executeScript(
                "if(!window.ipedChatCapture){" //$NON-NLS-1$
                        + "var s=document.createElement('style');s.textContent='.iped-capture-start:not(.iped-capture-hide-marks){outline:3px solid #2e7d32!important}.iped-capture-end:not(.iped-capture-hide-marks){outline:3px solid #c62828!important}.iped-capture-range:not(.iped-capture-hide-marks){background-color:rgba(255,235,59,.18)!important}.iped-capture-running .iped-capture-start,.iped-capture-running .iped-capture-end,.iped-capture-running .iped-capture-range{outline:none!important;box-shadow:none!important;background-color:inherit!important}.iped-capture-no-interaction,.iped-capture-no-interaction *{cursor:none!important;pointer-events:none!important;user-select:none!important;-webkit-user-select:none!important}.iped-capture-no-interaction *{animation:none!important;transition:none!important;caret-color:transparent!important}';(document.head||document.documentElement).appendChild(s);" //$NON-NLS-1$
                        + "var dateRe=/\\b(\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}|\\d{2}[-\\/]\\d{2}[-\\/]\\d{4})\\b/;" //$NON-NLS-1$
                        + "Array.prototype.forEach.call(document.querySelectorAll('body *'),function(el){if(el.children.length>0||el.id)return;var t=(el.innerText||el.textContent||'').trim();if(dateRe.test(t)){el.id='iped-capture-date-'+Math.random().toString(36).slice(2);el.setAttribute('data-iped-capture-date','true');}});" //$NON-NLS-1$
                        + "window.ipedChatCapture={" //$NON-NLS-1$
                        + "messageSelector:'.linha,tr[id],[data-iped-capture-date]'," //$NON-NLS-1$
                        + "messageAt:function(x,y){var el=document.elementFromPoint(x,y);while(el&&el!==document.body){if(el.id&&(el.classList.contains('linha')||el.tagName==='TR'||el.hasAttribute('data-iped-capture-date')))return el.id;el=el.parentElement;}return null;}," //$NON-NLS-1$
                        + "mark:function(kind,id){var el=document.getElementById(id);if(el)el.classList.add(kind==='start'?'iped-capture-start':'iped-capture-end');}," //$NON-NLS-1$
                        + "clear:function(){document.querySelectorAll('.iped-capture-start,.iped-capture-end,.iped-capture-range').forEach(function(e){e.classList.remove('iped-capture-start','iped-capture-end','iped-capture-range');});}," //$NON-NLS-1$
                        + "setMarksVisible:function(visible){document.querySelectorAll('.iped-capture-start,.iped-capture-end,.iped-capture-range').forEach(function(e){if(visible)e.classList.remove('iped-capture-hide-marks');else e.classList.add('iped-capture-hide-marks');});}," //$NON-NLS-1$
                        + "disableInteraction:function(){document.documentElement.classList.add('iped-capture-no-interaction');document.body&&document.body.classList.add('iped-capture-no-interaction');if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();}," //$NON-NLS-1$
                        + "enableInteraction:function(){document.documentElement.classList.remove('iped-capture-no-interaction');document.body&&document.body.classList.remove('iped-capture-no-interaction');}," //$NON-NLS-1$
                        + "setHeaderVisible:function(visible){var h=document.getElementById('topbar');if(!h)return;if(!h.hasAttribute('data-iped-capture-original-display')){h.setAttribute('data-iped-capture-original-display',h.style.display||'');h.setAttribute('data-iped-capture-original-visibility',h.style.visibility||'');}if(visible){h.style.display=h.getAttribute('data-iped-capture-original-display')||'';h.style.visibility=h.getAttribute('data-iped-capture-original-visibility')||'visible';}else{h.style.display='none';h.style.visibility='hidden';}document.body.offsetHeight;}," //$NON-NLS-1$
                        + "restoreHeader:function(){var h=document.getElementById('topbar');if(!h)return;var d=h.getAttribute('data-iped-capture-original-display');var v=h.getAttribute('data-iped-capture-original-visibility');h.style.display=d===null?'':d;h.style.visibility=v===null?'':v;h.removeAttribute('data-iped-capture-original-display');h.removeAttribute('data-iped-capture-original-visibility');}," //$NON-NLS-1$
                        + "getCaptureViewport:function(){var top=0;var h=document.getElementById('topbar');if(h&&h.style.display!=='none'&&h.style.visibility!=='hidden'){var hr=h.getBoundingClientRect();if(hr.width>0&&hr.height>0)top=Math.max(top,Math.min(window.innerHeight,hr.bottom));}return {top:top,bottom:window.innerHeight,height:Math.max(1,window.innerHeight-top)};}," //$NON-NLS-1$
                        + "visualRect:function(el){var r=el.getBoundingClientRect();var cs=window.getComputedStyle?window.getComputedStyle(el):null;var mt=cs?parseFloat(cs.marginTop)||0:0;var mb=cs?parseFloat(cs.marginBottom)||0:0;return {left:r.left,right:r.right,top:r.top-mt,bottom:r.bottom+mb,width:r.width,height:r.height+mt+mb};}," //$NON-NLS-1$
                        + "scrollToMessage:function(id){return this.scrollToMessageTop(id,this.getCaptureViewport().top);}," //$NON-NLS-1$
                        + "scrollToMessageTop:function(id,topOffset){var el=document.getElementById(id);if(!el)return false;var viewport=this.getCaptureViewport();var target=typeof topOffset==='number'?topOffset:viewport.top;var r=this.visualRect(el);window.scrollBy(0,Math.round(r.top-target));return true;}," //$NON-NLS-1$
                        + "getOrderedMessages:function(){var items=[];document.querySelectorAll(this.messageSelector).forEach(function(el){if(el&&el.id)items.push(el);});return items;}," //$NON-NLS-1$
                        + "getNextMessageAfter:function(id){var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){if(items[i].id===id){for(var j=i+1;j<items.length;j++){if(items[j]&&items[j].id)return items[j].id;}break;}}return null;}," //$NON-NLS-1$
                        + "scrollToNextMessageAfter:function(id){var nextId=this.getNextMessageAfter(id);if(!nextId)return false;return this.scrollToMessageTop(nextId,this.getCaptureViewport().top);}," //$NON-NLS-1$
                        + "visibleContentBottom:function(){var viewport=this.getCaptureViewport(),self=this,bottom=viewport.top;document.querySelectorAll(this.messageSelector).forEach(function(el){var r=self.visualRect(el);if(r.bottom>=viewport.top&&r.top<=viewport.bottom&&r.width>0&&r.height>0)bottom=Math.max(bottom,Math.min(viewport.bottom,r.bottom));});return bottom;}," //$NON-NLS-1$
                        + "scrollNextCaptureStep:function(){var bottom=this.visibleContentBottom();if(!bottom||bottom<1)bottom=Math.max(120,Math.floor(window.innerHeight*.80));window.scrollBy(0,Math.max(1,Math.floor(bottom)));}," //$NON-NLS-1$
                        + "scrollAfterBlock:function(id){var el=document.getElementById(id);if(!el){this.scrollNextCaptureStep();return;}var r=this.visualRect(el);window.scrollBy(0,Math.max(1,Math.floor(r.bottom)));}," //$NON-NLS-1$
                        + "isVisible:function(id){var el=document.getElementById(id);if(!el)return false;var viewport=this.getCaptureViewport();var r=this.visualRect(el);return r.bottom>=viewport.top&&r.top<=viewport.bottom;}," //$NON-NLS-1$
                        + "getVisibleRange:function(){var viewport=this.getCaptureViewport(),self=this;var first='',last='';document.querySelectorAll(this.messageSelector).forEach(function(el){if(!el.id)return;var r=self.visualRect(el);if(r.bottom>=viewport.top&&r.top<=viewport.bottom){if(!first)first=el.id;last=el.id;}});return first+'|'+last;}," //$NON-NLS-1$
                        + "blockType:function(el){if(el.hasAttribute('data-iped-capture-date')||el.classList.contains('date')||el.querySelector('.date'))return 'date';if(el.querySelector('audio,.audioImg,.iped-audio'))return 'audio';if(el.querySelector('video,.videoImg,.iped-video'))return 'video';if(el.querySelector('.imageImg'))return 'image';if(el.querySelector('.attachImg'))return 'file';return 'message';}," //$NON-NLS-1$
                        + "blockHash:function(el){var h=el.querySelector('input.check[name]');return h?h.name:'';}," //$NON-NLS-1$
                        + "blockText:function(el){var c=el.cloneNode(true);Array.prototype.forEach.call(c.querySelectorAll('script,style,input,button,audio,video,img'),function(n){n.parentNode&&n.parentNode.removeChild(n);});return (c.innerText||c.textContent||'').replace(/\\s+/g,' ').trim();}," //$NON-NLS-1$
                        + "getVisibleCaptureBlocks:function(){var viewport=this.getCaptureViewport();var blocks=[];var sy=window.scrollY||window.pageYOffset||0;document.querySelectorAll(this.messageSelector).forEach((function(el){if(!el.id)return;var r=this.visualRect(el);if(r.bottom<viewport.top||r.top>viewport.bottom||r.width<=0||r.height<=0)return;var top=r.top;var bottom=r.bottom;var visibleTop=Math.max(top,viewport.top);var visibleBottom=Math.min(bottom,viewport.bottom);var visibleHeight=Math.max(0,visibleBottom-visibleTop);blocks.push({id:el.id,type:this.blockType(el),left:r.left,top:top,right:r.right,bottom:bottom,width:r.width,height:bottom-top,documentTop:sy+top,documentBottom:sy+bottom,complete:top>=viewport.top&&bottom<=viewport.bottom,hash:this.blockHash(el),text:this.blockText(el),visibleHeight:visibleHeight});}).bind(this));return blocks;}," //$NON-NLS-1$
                        + "getVisibleMediaHashes:function(safeBottom){var a=[];document.querySelectorAll('input.check[name]').forEach(function(el){var r=el.getBoundingClientRect();if(r.bottom>=0&&r.top<=safeBottom&&r.bottom<=safeBottom)a.push(el.name);});return a.join('|');}," //$NON-NLS-1$
                    + "getCapturePlan:function(includeHeader,previousLastCompleteId){var viewport=this.getCaptureViewport();var blocks=this.getVisibleCaptureBlocks();var left=999999,right=0,safeBottom=viewport.top,firstRaw='',lastVisible='',lastComplete='',found=false,lastBottom=0,nextTop=0,scrollAdvance=0,nextMessageId='',firstEligible='',residualTopBlockId='',residualVisibleHeight=0,oversizedContinuation=false,headerBottom=0;blocks.forEach(function(b){left=Math.min(left,b.left);right=Math.max(right,b.right);if(!firstRaw)firstRaw=b.id;lastVisible=b.id;if(b.complete){found=true;lastComplete=b.id;lastBottom=Math.max(lastBottom,b.bottom);safeBottom=Math.max(safeBottom,b.bottom);}});if(found){blocks.forEach(function(b){if(b.id!==lastComplete&&b.top>=lastBottom&&(nextTop===0||b.top<nextTop))nextTop=b.top;});if(nextTop>lastBottom){safeBottom=lastBottom+Math.floor((nextTop-lastBottom)/2);}nextMessageId=this.getNextMessageAfter(lastComplete)||'';scrollAdvance=safeBottom-viewport.top;}if(!found){safeBottom=Math.max(viewport.top+1,viewport.top+Math.floor(viewport.height*.80));blocks.forEach(function(b){if(!firstRaw)firstRaw=b.id;lastVisible=b.id;left=Math.min(left,b.left);right=Math.max(right,b.right);});scrollAdvance=safeBottom-viewport.top;}if(blocks.length>0){var firstBlock=blocks[0];var sameAsPrevious=!!(previousLastCompleteId&&firstBlock&&firstBlock.id===previousLastCompleteId);var isOversized=!!(firstBlock&&firstBlock.height>viewport.height);var tinyResidual=!!(firstBlock&&firstBlock.visibleHeight>0&&(firstBlock.visibleHeight<=24||firstBlock.visibleHeight<=Math.max(12,firstBlock.height*.1)));if(sameAsPrevious&&tinyResidual&&!isOversized){residualTopBlockId=firstBlock.id;residualVisibleHeight=firstBlock.visibleHeight;}else if(sameAsPrevious&&isOversized){oversizedContinuation=true;}}var exportBlocks=blocks;firstEligible=exportBlocks.length?exportBlocks[0].id:firstRaw;if(includeHeader){var h=document.getElementById('topbar');if(h){var hr=h.getBoundingClientRect();if(hr.width>0&&hr.height>0){headerBottom=Math.max(0,Math.min(viewport.bottom,hr.bottom));safeBottom=Math.max(safeBottom,headerBottom);}}}var conv=document.getElementById('conversation'),topbar=document.getElementById('topbar'),base=conv||topbar;if(base){var br=base.getBoundingClientRect();if(br.width>0){left=br.left;right=br.right;}}if(left===999999||right<=left){left=0;right=window.innerWidth;}var pad=8;left=Math.max(0,Math.floor(left-pad));right=Math.min(window.innerWidth,Math.ceil(right+pad));safeBottom=Math.max(viewport.top+1,Math.min(viewport.bottom,Math.floor(safeBottom)));scrollAdvance=Math.max(1,Math.min(viewport.height,Math.floor(scrollAdvance||safeBottom-viewport.top)));var rectTop=includeHeader?0:viewport.top;var rectHeight=Math.max(1,safeBottom-rectTop);var sy=window.scrollY||window.pageYOffset||0;var documentTop=sy+rectTop;var documentBottom=sy+rectTop+rectHeight;var enc=encodeURIComponent;var blockData=exportBlocks.filter(function(b){return b.bottom>rectTop&&b.top<safeBottom;}).map(function(b){return [enc(b.id),enc(b.type),Math.round(b.left),Math.round(b.top),Math.round(b.width),Math.round(b.height),Math.round(b.visibleHeight),Math.round(b.documentTop),Math.round(b.documentBottom),b.complete?'1':'0',enc(b.hash||''),enc(b.text||'')].join(',');}).join(';');return [left,rectTop,Math.max(1,right-left),rectHeight,documentTop,documentBottom,firstRaw,lastVisible,safeBottom,scrollAdvance,lastComplete,nextMessageId,firstEligible,residualTopBlockId,residualVisibleHeight,oversizedContinuation?'1':'0',blockData,headerBottom].join('|');}" //$NON-NLS-1$
                        + "};}"); //$NON-NLS-1$
        } catch (Exception e) {
            LOGGER.debug("Unable to install chat capture script yet", e); //$NON-NLS-1$
        }
    }

    private void highlightCaptureMarks() {
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.clear();" //$NON-NLS-1$
                + (captureStartMessageId == null ? "" : "window.ipedChatCapture.mark('start'," + js(captureStartMessageId) + ");") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + (captureEndMessageId == null ? "" : "window.ipedChatCapture.mark('end'," + js(captureEndMessageId) + ");") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "}"); //$NON-NLS-1$
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private String js(String value) {
        return json(value);
    }

    private String json(String value) {
        if (value == null) {
            return "null"; //$NON-NLS-1$
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    }

    private String jsonArray(Set<String> values) {
        StringBuilder sb = new StringBuilder("["); //$NON-NLS-1$
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append(json(value));
            first = false;
        }
        return sb.append(']').toString();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safe(String value) {
        return value == null ? "" : value; //$NON-NLS-1$
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatUtcDate(Date date) {
        if (date == null) {
            return ""; //$NON-NLS-1$
        }
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'UTC'"); //$NON-NLS-1$
        format.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
        return format.format(date);
    }

    private String formatNumber(long value) {
        return String.format("%,d", value).replace(',', '.'); //$NON-NLS-1$
    }

    private String csv(String value) {
        return "\"" + safe(value).replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private String html(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
    }

    private String fileName(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ""; //$NON-NLS-1$
        }
        int slash = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    private String displayName(ExportedFile file) {
        if (file == null) {
            return ""; //$NON-NLS-1$
        }
        String path = safe(file.relativePath).replace('\\', '/');
        String name = fileName(path);
        if (path.startsWith("screenshots/frame_") && name.startsWith("frame_")) { //$NON-NLS-1$ //$NON-NLS-2$
            int dot = name.lastIndexOf('.');
            String sequence = dot > 6 ? name.substring(6, dot) : name.substring(6);
            return "Captura " + sequence; //$NON-NLS-1$
        }
        return name;
    }

    private String fileType(ExportedFile file) {
        String path = file == null ? "" : safe(file.relativePath).toLowerCase(); //$NON-NLS-1$
        if (path.startsWith("screenshots/") || path.endsWith(".png")) return "Imagem PNG"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (path.contains("/audios/")) return "Áudio"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.contains("/videos/")) return "Vídeo"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.endsWith(".pdf")) return "Documento PDF"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.contains("/imagens/")) return "Imagem"; //$NON-NLS-1$ //$NON-NLS-2$
        return "Arquivo"; //$NON-NLS-1$
    }

    private String safeName(String value) {
        return value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]+", "_").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static class CaptureRootEntry {
        String folder;
        String title;
        String chatId;
        String createdAt;
        String folderHash;
        int fileCount;
        int attachmentCount;
        int frameCount;
        long sizeBytes;
        String status;
        String linksWhatsapp;
        String linksReport;
        String linksText;
        String linksManifest;
        String linksHashes;
    }

    private static class FolderHash {
        String hash;
        int fileCount;
        long sizeBytes;
    }

    private static class CaptureTextHeader {
        String chatId = ""; //$NON-NLS-1$
        String title = ""; //$NON-NLS-1$
    }

    private static class CaptureRect {
        final double x;
        final double y;
        final double width;
        final double height;
        CaptureRect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class CapturePlan {
        CaptureRect rect;
        String firstVisibleMessageIdRaw;
        String firstEligibleMessageId;
        String lastVisibleMessageId;
        String lastCompleteMessageId;
        String nextMessageId;
        String residualTopBlockId;
        double residualVisibleHeight;
        boolean oversizedContinuation;
        double documentTop;
        double documentBottom;
        double exportDocumentTop;
        double exportDocumentBottom;
        double cropTopCss;
        double headerBottomCss;
        double preservedHeaderCss;
        double safeBottom;
        double scrollAdvance;
        boolean hasCompleteBlock;
        boolean endMessageCaptured;
        int imageWidth;
        int imageHeight;
        List<CaptureBlock> blocks = new ArrayList<>();

        static CapturePlan fallback() {
            CapturePlan plan = new CapturePlan();
            plan.rect = null;
            plan.safeBottom = 0;
            plan.scrollAdvance = 0;
            return plan;
        }
    }

    private static class CaptureFrame {
        final int sequence;
        final String image;
        final String firstMessageId;
        final String lastMessageId;
        final String firstVisibleMessageIdRaw;
        final String residualTopBlockId;
        final boolean oversizedContinuation;
        final double exportDocumentTop;
        final double exportDocumentBottom;
        final double cropTopCss;
        final Set<String> hashes;
        final int imageWidth;
        final int imageHeight;
        final List<CaptureBlock> blocks;
        CaptureFrame(int sequence, String image, String firstMessageId, String lastMessageId, String firstVisibleMessageIdRaw, String residualTopBlockId, boolean oversizedContinuation, double exportDocumentTop, double exportDocumentBottom, double cropTopCss, Set<String> hashes, int imageWidth, int imageHeight, List<CaptureBlock> blocks) {
            this.sequence = sequence;
            this.image = image.replace('\\', '/');
            this.firstMessageId = firstMessageId;
            this.lastMessageId = lastMessageId;
            this.firstVisibleMessageIdRaw = firstVisibleMessageIdRaw;
            this.residualTopBlockId = residualTopBlockId;
            this.oversizedContinuation = oversizedContinuation;
            this.exportDocumentTop = exportDocumentTop;
            this.exportDocumentBottom = exportDocumentBottom;
            this.cropTopCss = cropTopCss;
            this.hashes = hashes;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.blocks = blocks == null ? new ArrayList<>() : new ArrayList<>(blocks);
        }
    }

    private static class CaptureBlock {
        String id;
        String type;
        String hash;
        String text;
        double left;
        double top;
        double cssWidth;
        double cssHeight;
        double visibleHeight;
        double documentTop;
        double documentBottom;
        boolean complete;
        int x;
        int y;
        int width;
        int height;
    }

    private static class CaptureSourceMetadata {
        String name;
        long size;
        String type;
        boolean deleted;
        String category;
        String creationDate;
        String modificationDate;
        String accessDate;
        String hash;
        String path;
        String text;
        String title;
        String metadataText;
    }

    private static class ExportedFile {
        String relativePath;
        String thumbRelativePath;
        String absolutePath;
        String originalName;
        String originalPath;
        String originalType;
        boolean deleted;
        String category;
        String creationDate;
        String modificationDate;
        String accessDate;
        String chatHash;
        long size;
        String md5;
        String sha256;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return WhatsAppParser.WHATSAPP_CHAT.toString().equals(contentType)
                || ThreemaParser.THREEMA_CHAT.toString().equals(contentType)
                || SkypeParser.CONVERSATION_MIME_TYPE.equals(contentType)
                || SkypeParser.FILETRANSFER_MIME_TYPE.equals(contentType)
                || UFED_HTML_REPORT_MIME.equals(contentType) || PREVIEW_WITH_LINKS_MIME.equals(contentType)
                || TelegramParser.TELEGRAM_CHAT.toString().equals(contentType)
                || Win10MailParser.WIN10_MAIL_MSG.toString().equals(contentType)
                || DiscordParser.CHAT_MIME_TYPE_HTML.equals(contentType)
                || ShareazaDownloadParser.SHAREAZA_DOWNLOAD_META.equals(contentType);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected int getMaxHtmlSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        captureSourceItem = content instanceof IItem ? (IItem) content : null;
        if (content != null) {
            attachSearcher.updateSelectionCache();
        }
        super.loadFile(content, terms);
    }

    public class AttachmentHandler extends FileHandler {

        public void open(final String luceneQuery) {

            IItem item = attachSearcher.getItem(luceneQuery);
            if (!IOUtil.isToOpenExternally(item.getName(), item.getType())) {
                return;
            }
            File file = null;
            try {
                file = Util.getFileWithRightExt(item);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            if (file == null) {
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            LOGGER.info("Attachment not found by query " + luceneQuery); //$NON-NLS-1$
                            JOptionPane.showMessageDialog(null, Messages.getString("HtmlLinkViewer.AttachNotFound")); //$NON-NLS-1$
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                this.openFile(file);
            }
        }

        public void check(String luceneQuery, boolean checked) {
            cheking = true;
            attachSearcher.checkItem(luceneQuery, checked);
            cheking = false;
        }

        public boolean isChecked(String hash) {
            return attachSearcher.isChecked(hash);
        }

    }

    private void runScript(String script) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webEngine.executeScript(script);
            }
        });
    }

    private void updateSelection() {
        JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
        window.setMember("updatedHashes", mediaHashesInView); //$NON-NLS-1$
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) {var hash = x[i].name; if(updatedHashes.add(hash)) x[i].checked = app.isChecked(hash);}";
        webEngine.executeScript(script);
    }

    @Override
    public void setSelected(IItemId item, boolean value) {
        if (cheking || mediaHashesInView.isEmpty())
            return;
        String hash = attachSearcher.getHash(item);
        if (!mediaHashesInView.contains(hash))
            return;
        String script = "var x = document.getElementsByName(\"" + hash + "\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = " + value + ";";
        runScript(script);
    }

    @Override
    public void clearAll() {
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = false;";
        runScript(script);
    }

    @Override
    public void selectAll() {
        String script = "var x = document.getElementsByClassName(\"check\");"
                + "for(var i = 0; i < x.length; i++) x[i].checked = true;";
        runScript(script);
    }

}
