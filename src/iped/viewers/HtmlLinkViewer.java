package iped.viewers;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import iped.parsers.threema.ThreemaParser;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

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
    private static final String RECAPTURE_TRASH_FOLDER = ".iped-reextract-trash"; //$NON-NLS-1$

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
    private volatile Path sensitiveImagesFolder;
    private volatile int sensitiveSimilarityPercent = 90;
    private volatile int sensitiveBlurPercent = 90;
    private volatile SensitiveImageMatcher sensitiveImageMatcher;
    private volatile RecaptureJob activeRecaptureJob;
    private final Set<String> sensitiveBlurHashes = new HashSet<>();

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

    private void resetRecaptureMarksAndPreviewState() {
        captureStartMessageId = null;
        captureEndMessageId = null;
        try {
            clearSensitiveBlur();
            executeScriptAndWait("document.documentElement.classList.remove('iped-capture-running');" //$NON-NLS-1$
                    + "if(window.ipedChatCapture){window.ipedChatCapture.clear();window.ipedChatCapture.enableInteraction();}"); //$NON-NLS-1$
        } catch (Exception e) {
            LOGGER.debug("Unable to reset re-extract marks", e); //$NON-NLS-1$
        }
        chatCapturePanel.updateCounters(0, 0);
    }

    public void startChatCapture(File outputFolder) {
        startChatCapture(outputFolder, null, null);
    }

    public void startChatCapture(File outputFolder, String customFolderName) {
        startChatCapture(outputFolder, customFolderName, null);
    }

    public void startChatCapture(File outputFolder, String customFolderName, File sensitiveImagesFolder) {
        startChatCapture(outputFolder, customFolderName, sensitiveImagesFolder, 90, 90);
    }

    public void startChatCapture(File outputFolder, String customFolderName, File sensitiveImagesFolder,
            int sensitiveSimilarityPercent) {
        startChatCapture(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent, 90);
    }

    public void startChatCapture(File outputFolder, String customFolderName, File sensitiveImagesFolder,
            int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
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
        this.sensitiveImagesFolder = sensitiveImagesFolder == null || sensitiveImagesFolder.getPath().isBlank() ? null
                : sensitiveImagesFolder.toPath();
        this.sensitiveSimilarityPercent = normalizeSimilarityPercent(sensitiveSimilarityPercent);
        this.sensitiveBlurPercent = normalizeBlurPercent(sensitiveBlurPercent);
        captureRunning = true;
        stopCaptureRequested = false;
        chatCapturePanel.setCaptureRunning(true);
        chatCapturePanel.setStatus("ChatCapturePanel.Capturing"); //$NON-NLS-1$
        new Thread(() -> runChatCapture(outputFolder.toPath(), customFolderName), "IPED-chat-capture").start(); //$NON-NLS-1$
    }

    public void showRecaptureDialog(File outputFolder) {
        showRecaptureDialog(outputFolder, null);
    }

    public void showRecaptureDialog(File outputFolder, File sensitiveImagesFolder) {
        showRecaptureDialog(outputFolder, sensitiveImagesFolder, 90, 90);
    }

    public void showRecaptureDialog(File outputFolder, File sensitiveImagesFolder, int sensitiveSimilarityPercent) {
        showRecaptureDialog(outputFolder, sensitiveImagesFolder, sensitiveSimilarityPercent, 90);
    }

    public void showRecaptureDialog(File outputFolder, File sensitiveImagesFolder, int sensitiveSimilarityPercent,
            int sensitiveBlurPercent) {
        if (captureRunning) {
            return;
        }
        if (outputFolder == null || outputFolder.getPath().isBlank()) {
            chatCapturePanel.setStatus("ChatCapturePanel.MissingFolder"); //$NON-NLS-1$
            return;
        }
        Path outputRoot = outputFolder.toPath();
        List<RecaptureJob> jobs;
        try {
            jobs = loadRecaptureJobs(outputRoot);
        } catch (Exception e) {
            LOGGER.warn("Unable to load recapture jobs from {}", outputRoot, e); //$NON-NLS-1$
            JOptionPane.showMessageDialog(chatCapturePanel, e.getMessage() == null ? e.toString() : e.getMessage(),
                    "Re-Extrair", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }
        if (jobs.isEmpty()) {
            JOptionPane.showMessageDialog(chatCapturePanel, "Nenhuma captura valida encontrada para re-extrair.", //$NON-NLS-1$
                    "Re-Extrair", JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
            return;
        }

        JPanel list = new JPanel(new GridLayout(0, 1, 4, 4));
        List<JCheckBox> checks = new ArrayList<>();
        for (RecaptureJob job : jobs) {
            JCheckBox check = new JCheckBox(job.displayLabel(), job.valid);
            check.setEnabled(job.valid);
            checks.add(check);
            list.add(check);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new java.awt.Dimension(720, Math.min(420, 34 * Math.max(3, jobs.size()))));
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new javax.swing.JLabel("Selecione as capturas que serao re-extraidas:"), BorderLayout.NORTH); //$NON-NLS-1$
        panel.add(scroll, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(chatCapturePanel, panel, "Re-Extrair capturas", //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        List<RecaptureJob> selected = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            if (checks.get(i).isSelected() && jobs.get(i).valid) {
                selected.add(jobs.get(i));
            }
        }
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(chatCapturePanel, "Nenhuma captura selecionada.", "Re-Extrair", JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }
        this.sensitiveImagesFolder = sensitiveImagesFolder == null || sensitiveImagesFolder.getPath().isBlank() ? null
                : sensitiveImagesFolder.toPath();
        this.sensitiveSimilarityPercent = normalizeSimilarityPercent(sensitiveSimilarityPercent);
        this.sensitiveBlurPercent = normalizeBlurPercent(sensitiveBlurPercent);
        startRecaptureBatch(outputRoot, selected);
    }

    private List<RecaptureJob> loadRecaptureJobs(Path outputRoot) throws IOException {
        List<String> folders = readCaptureFolders(outputRoot);
        List<RecaptureJob> jobs = new ArrayList<>();
        for (String folder : folders) {
            Path folderPath = outputRoot.resolve(folder);
            Path manifest = folderPath.resolve("manifest.json"); //$NON-NLS-1$
            RecaptureJob job = new RecaptureJob();
            job.folder = folder;
            job.folderPath = folderPath;
            if (!Files.isRegularFile(manifest)) {
                job.invalidReason = "manifest.json nao encontrado"; //$NON-NLS-1$
                jobs.add(job);
                continue;
            }
            String content = Files.readString(manifest);
            job.status = jsonField(content, "status"); //$NON-NLS-1$
            job.startMessageId = jsonField(content, "startMessageId"); //$NON-NLS-1$
            job.endMessageId = jsonField(content, "endMessageId"); //$NON-NLS-1$
            job.startAnchor = readAnchor(content, "startAnchor"); //$NON-NLS-1$
            job.endAnchor = readAnchor(content, "endAnchor"); //$NON-NLS-1$
            String source = jsonObjectField(content, "sourceMetadata"); //$NON-NLS-1$
            job.sourceName = jsonField(source, "nome"); //$NON-NLS-1$
            job.sourceTitle = jsonField(source, "title"); //$NON-NLS-1$
            job.sourceHash = jsonField(source, "hash"); //$NON-NLS-1$
            job.sourcePath = jsonField(source, "caminho"); //$NON-NLS-1$
            Path coordinates = folderPath.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
            if (Files.isRegularFile(coordinates)) {
                String coordinateContent = Files.readString(coordinates);
                CaptureAnchor coordinateStartAnchor = firstBlockAnchor(coordinateContent, job.startMessageId);
                CaptureAnchor coordinateEndAnchor = lastBlockAnchor(coordinateContent, job.endMessageId);
                if (!job.startAnchor.isUsable()) {
                    job.startAnchor = coordinateStartAnchor;
                } else {
                    job.startAnchor = enrichAnchorFromCoordinates(job.startAnchor, coordinateStartAnchor);
                }
                if (!job.endAnchor.isUsable()) {
                    job.endAnchor = coordinateEndAnchor;
                } else {
                    job.endAnchor = enrichAnchorFromCoordinates(job.endAnchor, coordinateEndAnchor);
                }
            }
            job.title = firstNonBlank(job.sourceTitle, job.sourceName, folder);
            job.chatId = inferChatId(job.title);
            job.luceneQueries = buildRecaptureQueries(job);
            job.luceneQuery = job.luceneQueries.isEmpty() ? "" : job.luceneQueries.get(0); //$NON-NLS-1$
            job.valid = job.startAnchor.isUsable() && job.endAnchor.isUsable() && !job.luceneQueries.isEmpty()
                    && job.sourceName != null && !job.sourceName.isBlank();
            if (!job.valid && job.invalidReason == null) {
                job.invalidReason = "dados insuficientes para localizar chat/inicio/fim por assinatura"; //$NON-NLS-1$
            }
            jobs.add(job);
        }
        return jobs;
    }

    private List<String> readCaptureFolders(Path outputRoot) throws IOException {
        List<String> folders = new ArrayList<>();
        Path index = outputRoot.resolve("capturas-index.json"); //$NON-NLS-1$
        if (Files.isRegularFile(index)) {
            String content = Files.readString(index);
            Matcher matcher = Pattern.compile("\"folder\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(content); //$NON-NLS-1$
            while (matcher.find()) {
                String folder = unescapeJson(matcher.group(1));
                if (!folder.isBlank() && !folders.contains(folder)) {
                    folders.add(folder);
                }
            }
        }
        if (folders.isEmpty() && Files.isDirectory(outputRoot)) {
            try (Stream<Path> stream = Files.list(outputRoot)) {
                stream.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                        .forEach(path -> folders.add(outputRoot.relativize(path).toString().replace('\\', '/')));
            }
        }
        return folders;
    }

    private List<String> buildRecaptureQueries(RecaptureJob job) {
        List<String> queries = new ArrayList<>();
        if (job == null) {
            return queries;
        }
        if (job.sourceName != null && !job.sourceName.isBlank()) {
            queries.add("name:\"" + attachSearcher.escapeQuery(job.sourceName) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (job.sourcePath != null && !job.sourcePath.isBlank()) {
            String sourcePath = job.sourcePath.replace('\\', '/');
            if (!sourcePath.matches(".*_\\d+$")) { //$NON-NLS-1$
                queries.add("path:\"" + attachSearcher.escapeQuery(sourcePath + "_0") + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                queries.add("path:\"" + attachSearcher.escapeQuery(sourcePath + "_1") + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            queries.add("path:\"" + attachSearcher.escapeQuery(sourcePath) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (job.sourceHash != null && !job.sourceHash.isBlank()) {
            queries.add("hash:\"" + attachSearcher.escapeQuery(job.sourceHash) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (job.sourceTitle != null && !job.sourceTitle.isBlank() && !job.sourceTitle.equals(job.sourceName)) {
            queries.add("\"" + attachSearcher.escapeQuery(job.sourceTitle) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (job.chatId != null && !job.chatId.isBlank()) {
            queries.add("\"" + attachSearcher.escapeQuery(job.chatId) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return queries;
    }

    private void startRecaptureBatch(Path outputRoot, List<RecaptureJob> jobs) {
        captureRunning = true;
        stopCaptureRequested = false;
        chatCapturePanel.setCaptureRunning(true);
        chatCapturePanel.setRecaptureProgress("Preparando re-extracao...", 0, jobs.size(), 0, 7, true); //$NON-NLS-1$
        new Thread(() -> runRecaptureBatch(outputRoot, jobs), "IPED-chat-recapture").start(); //$NON-NLS-1$
    }

    private void runRecaptureBatch(Path outputRoot, List<RecaptureJob> jobs) {
        int success = 0;
        int failed = 0;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()); //$NON-NLS-1$
        try {
            for (int i = 0; i < jobs.size() && !stopCaptureRequested; i++) {
                RecaptureJob job = jobs.get(i);
                chatCapturePanel.setStatusText("Re-extraindo " + (i + 1) + "/" + jobs.size() + ": " + job.folder); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                try {
                    resetRecaptureMarksAndPreviewState();
                    recaptureOne(outputRoot, job, stamp, i + 1, jobs.size());
                    success++;
                } catch (Throwable e) {
                    failed++;
                    LOGGER.warn("Unable to re-extract capture {}", job.folder, e); //$NON-NLS-1$
                } finally {
                    resetRecaptureMarksAndPreviewState();
                }
            }
            chatCapturePanel.setRecaptureProgress("Limpando temporarios e recriando indice geral das capturas...", jobs.size(), jobs.size(), 7, 7, true); //$NON-NLS-1$
            cleanupRecaptureAuxiliaryFolders(outputRoot);
            safeRebuildCaptureRootIndex(outputRoot);
            chatCapturePanel.setStatusText("Re-extração concluída. Sucesso: " + success + " Falhas: " + failed); //$NON-NLS-1$ //$NON-NLS-2$
        } finally {
            setCaptureInteractionEnabled(true);
            restoreCaptureHeader();
            setCaptureMarksVisible(true);
            captureRunning = false;
            stopCaptureRequested = false;
            chatCapturePanel.setCaptureRunning(false);
            chatCapturePanel.clearRecaptureProgress();
        }
    }

    private void recaptureOne(Path outputRoot, RecaptureJob job, String batchStamp, int batchCurrent, int batchTotal)
            throws Exception {
        updateRecaptureProgress(job, batchCurrent, batchTotal, 1, "Abrindo chat pelo indice do caso...", true); //$NON-NLS-1$
        if (!openRecaptureChat(job)) {
            throw new IOException("Chat nao encontrado: " + job.displayLabel()); //$NON-NLS-1$
        }
        updateRecaptureProgress(job, batchCurrent, batchTotal, 2, "Localizando mensagem inicial e final no chat...", true); //$NON-NLS-1$
        prepareRecaptureChatRange(job);

        updateRecaptureProgress(job, batchCurrent, batchTotal, 4, "Capturando novamente imagens, anexos e coordenadas...", true); //$NON-NLS-1$
        String tmpName = job.folder + ".__reextract_tmp_" + batchStamp; //$NON-NLS-1$
        Path tmpFolder = outputRoot.resolve(tmpName);
        LOGGER.info("Recapture capture tmp folder: {}", tmpFolder); //$NON-NLS-1$
        CaptureRunResult result;
        activeRecaptureJob = job;
        try {
            result = runChatCaptureInternal(outputRoot, tmpName, tmpFolder, false);
        } finally {
            activeRecaptureJob = null;
        }
        updateRecaptureProgress(job, batchCurrent, batchTotal, 5, "Validando resultado temporario da re-extracao...", false); //$NON-NLS-1$
        Path manifest = result.captureDir.resolve("manifest.json"); //$NON-NLS-1$
        String status = Files.isRegularFile(manifest) ? jsonField(Files.readString(manifest), "status") : result.status; //$NON-NLS-1$
        if (!"completed".equals(status)) { //$NON-NLS-1$
            deleteDirectoryIfExists(result.captureDir);
            throw new IOException("Re-extração nao completou: " + status); //$NON-NLS-1$
        }

        updateRecaptureProgress(job, batchCurrent, batchTotal, 6, "Substituindo a extracao antiga pela nova...", false); //$NON-NLS-1$
        Path oldFolder = uniqueSibling(outputRoot.resolve(job.folder + ".__old_" + batchStamp)); //$NON-NLS-1$
        LOGGER.info("Recapture replacing old folder {} with {}", job.folderPath, result.captureDir); //$NON-NLS-1$
        Files.move(job.folderPath, oldFolder);
        try {
            Files.move(result.captureDir, job.folderPath);
        } catch (IOException e) {
            if (Files.exists(oldFolder) && !Files.exists(job.folderPath)) {
                Files.move(oldFolder, job.folderPath);
            }
            throw e;
        }
        try {
            deleteDirectoryIfExists(oldFolder);
        } catch (IOException e) {
            disposeOldRecaptureFolder(oldFolder, outputRoot, batchStamp, e);
        }
        LOGGER.info("Recapture completed for {}", job.folder); //$NON-NLS-1$
        updateRecaptureProgress(job, batchCurrent, batchTotal, 7, "Chat re-extraido com sucesso.", false); //$NON-NLS-1$
    }

    private boolean openRecaptureChat(RecaptureJob job) throws InterruptedException {
        if (job == null || job.luceneQueries == null || job.luceneQueries.isEmpty()) {
            return false;
        }
        for (String query : job.luceneQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            job.luceneQuery = query;
            LOGGER.info("Trying to re-extract chat {} with query {}", job.folder, query); //$NON-NLS-1$
            if (!attachSearcher.openItem(query, null, job.sourceName)) {
                continue;
            }
            if (waitUntilCaptureSourceMatches(job, 8000)) {
                return true;
            }
            LOGGER.info("Opened candidate for {}, but source did not match. Trying next query.", job.folder); //$NON-NLS-1$
        }
        return false;
    }

    private void prepareRecaptureChatRange(RecaptureJob job) throws IOException, InterruptedException {
        if (job == null) {
            throw new IOException("Re-extracao sem dados do chat."); //$NON-NLS-1$
        }
        RecaptureLocatedRange range = resolveRecaptureRangeFromDom(job);
        captureStartMessageId = range == null ? "" : range.startId; //$NON-NLS-1$
        if (captureStartMessageId == null || captureStartMessageId.isBlank()) {
            throw new IOException("Mensagem inicial nao localizada no DOM: " + job.startAnchor.display()); //$NON-NLS-1$
        }
        captureEndMessageId = range == null ? "" : range.endId; //$NON-NLS-1$
        if (captureEndMessageId == null || captureEndMessageId.isBlank()) {
            throw new IOException("Mensagem final nao localizada no DOM: " + job.endAnchor.display()); //$NON-NLS-1$
        }
        scrollToCaptureStartMessage();
        highlightCaptureMarks();
        sleep(800);
    }

    private RecaptureLocatedRange resolveRecaptureRangeFromDom(RecaptureJob job) throws InterruptedException {
        if (job == null) {
            return new RecaptureLocatedRange("", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        LOGGER.info("Recapture DOM resolve started for {}", job.folder); //$NON-NLS-1$
        executeScriptAndWait("window.scrollTo(0,0);"); //$NON-NLS-1$
        sleep(600);

        RecaptureLocatedRange lastRange = new RecaptureLocatedRange("", ""); //$NON-NLS-1$ //$NON-NLS-2$
        double lastY = -1;
        int stuck = 0;
        long deadline = System.currentTimeMillis() + 180000;
        while (System.currentTimeMillis() < deadline && !stopCaptureRequested) {
            Object result = executeScriptAndWait("window.ipedChatCapture ? window.ipedChatCapture.resolveRecaptureRangeFromDom(" //$NON-NLS-1$
                    + buildAnchorJs(job.startAnchor) + "," + buildAnchorJs(job.endAnchor) + ") : '|';"); //$NON-NLS-1$ //$NON-NLS-2$
            lastRange = RecaptureLocatedRange.parse(result == null ? "|" : result.toString()); //$NON-NLS-1$
            if (!lastRange.startId.isBlank() && !lastRange.endId.isBlank()) {
                LOGGER.info("Recapture DOM resolved for {}: {} -> {}", job.folder, lastRange.startId, lastRange.endId); //$NON-NLS-1$
                return lastRange;
            }

            double before = getScrollY();
            executeScriptAndWait("window.scrollBy(0,4000);"); //$NON-NLS-1$
            sleep(250);
            double after = getScrollY();
            if (after == before || after == lastY) {
                stuck++;
                if (stuck >= 8) {
                    break;
                }
            } else {
                stuck = 0;
            }
            lastY = after;
        }
        LOGGER.info("Recapture DOM resolve failed for {}: start={} end={}", job.folder, lastRange.startId, lastRange.endId); //$NON-NLS-1$
        return lastRange;
    }

    private String buildAnchorJs(CaptureAnchor anchor) {
        CaptureAnchor safeAnchor = anchor == null ? new CaptureAnchor() : anchor.normalized();
        return "{id:" + js(safeAnchor.id) //$NON-NLS-1$
                + ",type:" + js(safeAnchor.type) //$NON-NLS-1$
                + ",text:" + js(safeAnchor.text) //$NON-NLS-1$
                + ",date:" + js(safeAnchor.date) //$NON-NLS-1$
                + ",hash:" + js(safeAnchor.hash) + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private RecaptureLocatedRange scanRecaptureRangeFromChatStart(RecaptureJob job) throws InterruptedException {
        if (job == null) {
            return new RecaptureLocatedRange("", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        LOGGER.info("Recapture scan started for {}", job.folder); //$NON-NLS-1$
        executeScriptAndWait("window.scrollTo(0,0);"); //$NON-NLS-1$
        sleep(600);

        String startId = ""; //$NON-NLS-1$
        String endId = ""; //$NON-NLS-1$
        double lastY = -1;
        int stuck = 0;
        long deadline = System.currentTimeMillis() + 180000;

        while (System.currentTimeMillis() < deadline && !stopCaptureRequested) {
            RecaptureLocatedRange partial = RecaptureLocatedRange.parse(
                    scanVisibleBlocksForAnchors(job.startAnchor, job.endAnchor, startId));

            if (startId.isBlank() && !partial.startId.isBlank()) {
                startId = partial.startId;
                captureStartMessageId = startId;
                LOGGER.info("Recapture start found: {} -> {}", job.folder, startId); //$NON-NLS-1$
                highlightCaptureMarks();
                sleep(250);
            }

            if (!startId.isBlank() && !partial.endId.isBlank()) {
                endId = partial.endId;
                captureEndMessageId = endId;
                LOGGER.info("Recapture end found: {} -> {}", job.folder, endId); //$NON-NLS-1$
                highlightCaptureMarks();
                sleep(250);
                return new RecaptureLocatedRange(startId, endId);
            }

            double currentY = getScrollY();
            executeScriptAndWait("window.scrollBy(0,900);"); //$NON-NLS-1$
            sleep(180);

            double nextY = getScrollY();
            if (nextY == currentY || nextY == lastY) {
                stuck++;
                if (stuck >= 8) {
                    break;
                }
            } else {
                stuck = 0;
            }
            lastY = nextY;
        }

        return new RecaptureLocatedRange(startId, endId);
    }

    private String scanVisibleBlocksForAnchors(CaptureAnchor start, CaptureAnchor end, String currentStartId) {
        CaptureAnchor safeStart = start == null ? new CaptureAnchor() : start.normalized();
        CaptureAnchor safeEnd = end == null ? new CaptureAnchor() : end.normalized();
        Object result = executeScriptAndWait("window.ipedChatCapture ? window.ipedChatCapture.scanVisibleBlocksForAnchors(" //$NON-NLS-1$
                + js(safeStart.type) + "," + js(safeStart.text) + "," + js(safeStart.date) + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + js(safeStart.hash) + "," + js(safeEnd.type) + "," + js(safeEnd.text) + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + js(safeEnd.date) + "," + js(safeEnd.hash) + "," + js(currentStartId) //$NON-NLS-1$ //$NON-NLS-2$
                + ") : '|';"); //$NON-NLS-1$
        return result == null ? "|" : result.toString(); //$NON-NLS-1$
    }

    private String locateAnchorMessage(CaptureAnchor anchor, long timeoutMs) throws InterruptedException {
        if (anchor == null || !anchor.isUsable()) {
            return ""; //$NON-NLS-1$
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        if (anchor.documentTop >= 0) {
            executeScriptAndWait("window.scrollTo(0," + Math.max(0, Math.floor(anchor.documentTop - 600)) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
            sleep(450);
            String id = findAnchorInCurrentDom(anchor);
            if (!id.isBlank()) {
                executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.scrollToMessage(" + js(id) + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
                return id;
            }
            double baseY = getScrollY();
            int[] offsets = new int[] { -1800, 1800, -3600, 3600, -7200, 7200, -12000, 12000 };
            for (int offset : offsets) {
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                executeScriptAndWait("window.scrollTo(0," + Math.max(0, Math.floor(baseY + offset)) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
                sleep(250);
                id = findAnchorInCurrentDom(anchor);
                if (!id.isBlank()) {
                    executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.scrollToMessage(" + js(id) + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
                    return id;
                }
            }
        }

        executeScriptAndWait("window.scrollTo(0,0);"); //$NON-NLS-1$
        sleep(350);
        double lastY = -1;
        int stuckCount = 0;
        while (System.currentTimeMillis() < deadline) {
            String id = findAnchorInCurrentDom(anchor);
            if (!id.isBlank()) {
                executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.scrollToMessage(" + js(id) + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
                return id;
            }
            double currentY = getScrollY();
            if (currentY == lastY) {
                stuckCount++;
                if (stuckCount >= 6) {
                    break;
                }
            } else {
                stuckCount = 0;
            }
            lastY = currentY;
            executeScriptAndWait("window.scrollBy(0,12000);"); //$NON-NLS-1$
            sleep(180);
        }
        return findAnchorInCurrentDom(anchor);
    }

    private String findAnchorInCurrentDom(CaptureAnchor anchor) {
        Object result = executeScriptAndWait("window.ipedChatCapture ? window.ipedChatCapture.findMessageByAnchor(" //$NON-NLS-1$
                + js(anchor.type) + "," + js(anchor.text) + "," + js(anchor.date) + "," + js(anchor.hash) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "," + js(anchor.id) + "," + anchor.documentTop //$NON-NLS-1$ //$NON-NLS-2$
                + ") : '';"); //$NON-NLS-1$
        return result == null ? "" : result.toString(); //$NON-NLS-1$
    }

    private void updateRecaptureProgress(RecaptureJob job, int batchCurrent, int batchTotal, int step,
            String stageMessage, boolean indeterminate) {
        String folder = job == null || job.folder == null || job.folder.isBlank() ? "" : " [" + job.folder + "]"; //$NON-NLS-1$ //$NON-NLS-2$
        chatCapturePanel.setRecaptureProgress(stageMessage + folder, batchCurrent, batchTotal, step, 7, indeterminate);
    }

    private boolean waitUntilCaptureSourceMatches(RecaptureJob job, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            IItem item = captureSourceItem;
            if (item != null && matchesRecaptureJob(item, job)) {
                return true;
            }
            sleep(250);
        }
        return false;
    }

    private boolean waitUntilMessageElementExists(String messageId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object result = executeScriptAndWait("!!document.getElementById(" + js(messageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
            if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                return true;
            }
            sleep(250);
        }
        return false;
    }

    private boolean scrollUntilMessageExists(String messageId, long timeoutMs) throws InterruptedException {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        double lastY = -1;
        int direction = -1;
        while (System.currentTimeMillis() < deadline) {
            if (waitUntilMessageElementExists(messageId, 500)) {
                executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.scrollToMessage(" + js(messageId) + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
                return true;
            }
            double currentY = getScrollY();
            if (currentY <= 0 && direction < 0) {
                direction = 1;
            } else if (currentY == lastY && direction > 0) {
                direction = -1;
            }
            lastY = currentY;
            executeScriptAndWait("window.scrollBy(0," + (direction < 0 ? "-900" : "900") + ");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sleep(250);
        }
        return waitUntilMessageElementExists(messageId, 500);
    }

    private void ensureCaptureRangeElementsAvailable() throws IOException {
        if (captureStartMessageId == null || captureStartMessageId.isBlank() || captureEndMessageId == null
                || captureEndMessageId.isBlank()) {
            throw new IOException("Marcadores de inicio/fim ausentes para captura."); //$NON-NLS-1$
        }
        Object startExists = executeScriptAndWait("!!document.getElementById(" + js(captureStartMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        if (!(startExists instanceof Boolean) || !((Boolean) startExists).booleanValue()) {
            throw new IOException("Mensagem inicial nao encontrada no chat carregado: " + captureStartMessageId); //$NON-NLS-1$
        }
        Object endExists = executeScriptAndWait("!!document.getElementById(" + js(captureEndMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        if (!(endExists instanceof Boolean) || !((Boolean) endExists).booleanValue()) {
            throw new IOException("Mensagem final nao encontrada no chat carregado: " + captureEndMessageId); //$NON-NLS-1$
        }
    }

    private boolean scrollToCaptureStartMessage() {
        Object positioned = executeScriptAndWait("(function(id){" //$NON-NLS-1$
                + "try{if(window.ipedChatCapture&&window.ipedChatCapture.scrollToMessage(id)===true)return true;}catch(e){}" //$NON-NLS-1$
                + "var el=document.getElementById(id);if(!el)return false;" //$NON-NLS-1$
                + "var top=0,h=document.getElementById('topbar');" //$NON-NLS-1$
                + "if(h&&h.style.display!=='none'&&h.style.visibility!=='hidden'){" //$NON-NLS-1$
                + "var hr=h.getBoundingClientRect();if(hr.width>0&&hr.height>0)top=Math.max(0,Math.min(window.innerHeight,hr.bottom));}" //$NON-NLS-1$
                + "var r=el.getBoundingClientRect();window.scrollBy(0,Math.round(r.top-top));return true;" //$NON-NLS-1$
                + "})(" + js(captureStartMessageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        return positioned instanceof Boolean && ((Boolean) positioned).booleanValue();
    }

    private boolean matchesRecaptureJob(IItem item, RecaptureJob job) {
        if (item == null || job == null) {
            return false;
        }
        if (job.sourceName != null && !job.sourceName.isBlank()) {
            return job.sourceName.equals(item.getName());
        }
        if (job.sourcePath != null && !job.sourcePath.isBlank()) {
            String expected = job.sourcePath.replace('\\', '/');
            String actual = safe(item.getPath()).replace('\\', '/');
            return expected.equalsIgnoreCase(actual);
        }
        if (job.sourceHash != null && !job.sourceHash.isBlank() && job.sourceHash.equalsIgnoreCase(safe(item.getHash()))) {
            return true;
        }
        return false;
    }

    private Path uniqueSibling(Path path) throws IOException {
        if (!Files.exists(path)) {
            return path;
        }
        String name = path.getFileName().toString();
        Path parent = path.getParent();
        for (int i = 1; ; i++) {
            Path candidate = parent.resolve(name + "_" + i); //$NON-NLS-1$
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    private void runChatCapture(Path outputRoot, String customFolderName) {
        try {
            CaptureRunResult result = runChatCaptureInternal(outputRoot, customFolderName, null, true);
            chatCapturePanel.setStatus("completed".equals(result.status) ? "ChatCapturePanel.Completed" : "ChatCapturePanel.Stopped"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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

    private CaptureRunResult runChatCaptureInternal(Path outputRoot, String customFolderName, Path exactCaptureDir, boolean rebuildBefore) throws Exception {
        List<CaptureFrame> frames = new ArrayList<>();
        Map<String, ExportedFile> exported = new LinkedHashMap<>();
        Set<String> exportedAttachmentHashes = new HashSet<>();
        String status = "completed"; //$NON-NLS-1$
        Path captureDir = null;
        try {
            if (rebuildBefore) {
                safeRebuildCaptureRootIndex(outputRoot);
            }
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()); //$NON-NLS-1$
            captureDir = exactCaptureDir != null ? exactCaptureDir : outputRoot.resolve(buildCaptureFolderName(customFolderName, timestamp));
            if (Files.exists(captureDir)) {
                throw new IOException("Capture folder already exists: " + captureDir); //$NON-NLS-1$
            }
            safeRebuildCaptureRootIndex(outputRoot);
            Path screenshotsDir = captureDir.resolve("screenshots"); //$NON-NLS-1$
            Path attachmentsDir = captureDir.resolve("anexos"); //$NON-NLS-1$
            Files.createDirectories(screenshotsDir);
            Files.createDirectories(attachmentsDir);

            setCaptureInteractionEnabled(false);
            setCaptureMarksVisible(false);
            prepareSensitiveImageMatcher();
            sleep(150);
            ensureCaptureRangeElementsAvailable();
            if (!scrollToCaptureStartMessage()) {
                throw new IOException("Nao foi possivel posicionar na mensagem inicial: " + captureStartMessageId); //$NON-NLS-1$
            }
            prepareSensitiveBlurForRange();
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
                applySensitiveBlurToVisibleImages();
                sleep(80);
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
            return new CaptureRunResult(status, captureDir);
        } catch (Exception e) {
            if (captureDir != null && Files.exists(captureDir)) {
                LOGGER.warn("Capture failed at {}", captureDir, e); //$NON-NLS-1$
            }
            throw e;
        } finally {
            clearSensitiveBlur();
            sensitiveImageMatcher = null;
            sensitiveBlurHashes.clear();
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
                    .filter(path -> !isRecaptureAuxiliaryFolder(path))
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

    private boolean isRecaptureAuxiliaryFolder(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.equals(RECAPTURE_TRASH_FOLDER) || name.contains(".__old_") || name.contains(".__reextract_tmp_") //$NON-NLS-1$ //$NON-NLS-2$
                || name.contains(".__reextract_failed_"); //$NON-NLS-1$
    }

    private void cleanupRecaptureAuxiliaryFolders(Path outputRoot) {
        if (outputRoot == null || !Files.isDirectory(outputRoot)) {
            return;
        }
        try (Stream<Path> children = Files.list(outputRoot)) {
            children.filter(Files::isDirectory).filter(this::isRecaptureAuxiliaryFolder).forEach(path -> {
                try {
                    deleteDirectoryIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("Unable to delete temporary re-extract folder {}", path, e); //$NON-NLS-1$
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Unable to list temporary re-extract folders at {}", outputRoot, e); //$NON-NLS-1$
        }
    }

    private void disposeOldRecaptureFolder(Path oldFolder, Path outputRoot, String batchStamp, IOException originalError) {
        LOGGER.warn("Unable to delete old re-extract folder {}. Moving it to hidden trash.", oldFolder, originalError); //$NON-NLS-1$
        if (oldFolder == null || outputRoot == null || !Files.exists(oldFolder)) {
            return;
        }
        Path trashRoot = outputRoot.resolve(RECAPTURE_TRASH_FOLDER);
        try {
            Files.createDirectories(trashRoot);
            try {
                Files.setAttribute(trashRoot, "dos:hidden", Boolean.TRUE); //$NON-NLS-1$
            } catch (Exception e) {
                LOGGER.debug("Unable to hide re-extract trash folder {}", trashRoot, e); //$NON-NLS-1$
            }
            String suffix = batchStamp == null || batchStamp.isBlank()
                    ? DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()) : batchStamp; //$NON-NLS-1$
            Path trashTarget = uniqueSibling(trashRoot.resolve(oldFolder.getFileName().toString() + "." + suffix)); //$NON-NLS-1$
            try {
                Files.move(oldFolder, trashTarget, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(oldFolder, trashTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                deleteDirectoryIfExists(trashTarget);
            } catch (IOException e) {
                LOGGER.warn("Old re-extract folder is still locked at hidden trash {}", trashTarget, e); //$NON-NLS-1$
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to move old re-extract folder {} to hidden trash", oldFolder, e); //$NON-NLS-1$
        }
    }

    private void deleteDirectoryIfExists(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3 && Files.exists(path); attempt++) {
            try {
                deleteDirectoryTreeOnce(path);
                return;
            } catch (IOException e) {
                lastError = e;
                sleepQuietly(250L * attempt);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private void deleteDirectoryTreeOnce(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = new ArrayList<>();
            stream.forEach(paths::add);
            paths.sort(Comparator.reverseOrder());
            for (Path item : paths) {
                try {
                    item.toFile().setWritable(true);
                } catch (Exception e) {
                    LOGGER.debug("Unable to mark file writable before delete {}", item, e); //$NON-NLS-1$
                }
                Files.deleteIfExists(item);
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private CaptureAnchor readAnchor(String content, String field) {
        String object = jsonObjectField(content, field);
        if (object.isBlank()) {
            return new CaptureAnchor();
        }
        CaptureAnchor anchor = new CaptureAnchor();
        anchor.type = jsonField(object, "type"); //$NON-NLS-1$
        anchor.id = jsonField(object, "id"); //$NON-NLS-1$
        anchor.text = jsonField(object, "text"); //$NON-NLS-1$
        anchor.date = jsonField(object, "date"); //$NON-NLS-1$
        anchor.hash = jsonField(object, "hash"); //$NON-NLS-1$
        anchor.documentTop = jsonDoubleField(object, "documentTop", -1); //$NON-NLS-1$
        anchor.documentBottom = jsonDoubleField(object, "documentBottom", -1); //$NON-NLS-1$
        return anchor.normalized();
    }

    private CaptureAnchor enrichAnchorFromCoordinates(CaptureAnchor anchor, CaptureAnchor coordinateAnchor) {
        if (anchor == null) {
            return coordinateAnchor == null ? new CaptureAnchor() : coordinateAnchor.normalized();
        }
        if (coordinateAnchor == null || !coordinateAnchor.isUsable()) {
            return anchor.normalized();
        }
        if (anchor.id == null || anchor.id.isBlank()) {
            anchor.id = coordinateAnchor.id;
        }
        if ((anchor.type == null || anchor.type.isBlank()) && coordinateAnchor.type != null) {
            anchor.type = coordinateAnchor.type;
        }
        if ((anchor.text == null || anchor.text.isBlank()) && coordinateAnchor.text != null) {
            anchor.text = coordinateAnchor.text;
        }
        if ((anchor.date == null || anchor.date.isBlank()) && coordinateAnchor.date != null) {
            anchor.date = coordinateAnchor.date;
        }
        if ((anchor.hash == null || anchor.hash.isBlank()) && coordinateAnchor.hash != null) {
            anchor.hash = coordinateAnchor.hash;
        }
        if (anchor.documentTop < 0 && coordinateAnchor.documentTop >= 0) {
            anchor.documentTop = coordinateAnchor.documentTop;
        }
        if (anchor.documentBottom < 0 && coordinateAnchor.documentBottom >= 0) {
            anchor.documentBottom = coordinateAnchor.documentBottom;
        }
        return anchor.normalized();
    }

    private CaptureAnchor firstBlockAnchor(String coordinatesJson, String preferredId) {
        List<CaptureAnchor> anchors = readCoordinateAnchors(coordinatesJson);
        if (preferredId != null && !preferredId.isBlank()) {
            for (CaptureAnchor anchor : anchors) {
                if (preferredId.equals(anchor.id) && anchor.isUsable()) {
                    return anchor.normalized();
                }
            }
        }
        for (CaptureAnchor anchor : anchors) {
            if (!"date".equals(anchor.type) && anchor.isUsable()) { //$NON-NLS-1$
                return anchor.normalized();
            }
        }
        return new CaptureAnchor();
    }

    private CaptureAnchor lastBlockAnchor(String coordinatesJson, String preferredId) {
        List<CaptureAnchor> anchors = readCoordinateAnchors(coordinatesJson);
        if (preferredId != null && !preferredId.isBlank()) {
            for (CaptureAnchor anchor : anchors) {
                if (preferredId.equals(anchor.id) && anchor.isUsable()) {
                    return anchor.normalized();
                }
            }
        }
        for (int i = anchors.size() - 1; i >= 0; i--) {
            CaptureAnchor anchor = anchors.get(i);
            if (!"date".equals(anchor.type) && anchor.isUsable()) { //$NON-NLS-1$
                return anchor.normalized();
            }
        }
        return new CaptureAnchor();
    }

    private List<CaptureAnchor> readCoordinateAnchors(String coordinatesJson) {
        List<CaptureAnchor> anchors = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{\\\"id\\\"\\s*:\\s*(?:\\\"(?:\\\\.|[^\\\"])*\\\"|null).*?\\}", Pattern.DOTALL) //$NON-NLS-1$
                .matcher(safe(coordinatesJson));
        while (matcher.find()) {
            String object = matcher.group();
            CaptureAnchor anchor = new CaptureAnchor();
            anchor.id = jsonField(object, "id"); //$NON-NLS-1$
            anchor.type = jsonField(object, "type"); //$NON-NLS-1$
            anchor.text = jsonField(object, "text"); //$NON-NLS-1$
            anchor.hash = jsonField(object, "hash"); //$NON-NLS-1$
            anchor.date = extractMessageDate(anchor.text);
            anchor.documentTop = jsonDoubleField(object, "documentTop", -1); //$NON-NLS-1$
            anchor.documentBottom = jsonDoubleField(object, "documentBottom", -1); //$NON-NLS-1$
            anchors.add(anchor.normalized());
        }
        return anchors;
    }

    private CaptureAnchor anchorForMessage(List<CaptureFrame> frames, String messageId, boolean lastFallback) {
        CaptureAnchor fallback = new CaptureAnchor();
        for (CaptureFrame frame : frames) {
            for (CaptureBlock block : frame.blocks) {
                CaptureAnchor anchor = anchorFromBlock(block);
                if (!anchor.isUsable() || "date".equals(anchor.type)) { //$NON-NLS-1$
                    continue;
                }
                if (messageId != null && !messageId.isBlank() && messageId.equals(block.id)) {
                    return anchor;
                }
                if (!lastFallback && !fallback.isUsable()) {
                    fallback = anchor;
                } else if (lastFallback) {
                    fallback = anchor;
                }
            }
        }
        return fallback;
    }

    private CaptureAnchor anchorFromBlock(CaptureBlock block) {
        CaptureAnchor anchor = new CaptureAnchor();
        if (block == null) {
            return anchor;
        }
        anchor.id = block.id;
        anchor.type = coordinateBlockType(block, null);
        anchor.text = block.text;
        anchor.hash = block.hash;
        anchor.date = extractMessageDate(block.text);
        anchor.documentTop = block.documentTop;
        anchor.documentBottom = block.documentBottom;
        return anchor.normalized();
    }

    private String extractMessageDate(String text) {
        Matcher matcher = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?)").matcher(safe(text)); //$NON-NLS-1$
        return matcher.find() ? matcher.group(1).trim() : ""; //$NON-NLS-1$
    }

    private long jsonLongField(String content, String field, long fallback) {
        String value = jsonField(content, field);
        try {
            return value.isBlank() || "null".equals(value) ? fallback : Long.parseLong(value); //$NON-NLS-1$
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double jsonDoubleField(String content, String field, double fallback) {
        String value = jsonField(content, field);
        try {
            return value.isBlank() || "null".equals(value) ? fallback : Double.parseDouble(value); //$NON-NLS-1$
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
        String type = item == null ? "" : safe(item.getType()).toLowerCase(); //$NON-NLS-1$
        String name = item == null ? "" : safe(item.getName()).toLowerCase(); //$NON-NLS-1$
        if (type != null) {
            if (type.startsWith("image")) return "imagens"; //$NON-NLS-1$ //$NON-NLS-2$
            if (type.startsWith("audio")) return "audios"; //$NON-NLS-1$ //$NON-NLS-2$
            if (type.startsWith("video")) return "videos"; //$NON-NLS-1$ //$NON-NLS-2$
            if (type.contains("pdf")) return "pdfs"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (hasExtension(name, ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif", ".bmp", ".tif", ".tiff")) return "imagens"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
        if (hasExtension(name, ".opus", ".ogg", ".mp3", ".m4a", ".aac", ".wav", ".amr", ".flac")) return "audios"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
        if (hasExtension(name, ".mp4", ".3gp", ".mov", ".webm", ".avi", ".mkv")) return "videos"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
        if (hasExtension(name, ".pdf")) return "pdfs"; //$NON-NLS-1$ //$NON-NLS-2$
        return "outros"; //$NON-NLS-1$
    }

    private boolean hasExtension(String name, String... extensions) {
        String safeName = safe(name).toLowerCase();
        for (String extension : extensions) {
            if (safeName.endsWith(extension)) {
                return true;
            }
        }
        return false;
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
            out.println("<h2>Categorias</h2><a class=\"sub\" href=\"#\" data-section=\"captura\" onclick=\"return showSection('captura')\">Captura</a><a class=\"sub\" href=\"#\" data-section=\"audios\" onclick=\"return showSection('audios')\">&Aacute;udios</a><a class=\"sub\" href=\"#\" data-section=\"pdfs\" onclick=\"return showSection('pdfs')\">Documentos PDF</a><a class=\"sub\" href=\"#\" data-section=\"imagens\" onclick=\"return showSection('imagens')\">Outras Imagens</a><a class=\"sub\" href=\"#\" data-section=\"scans\" onclick=\"return showSection('scans')\">Poss&iacute;veis Digitaliza&ccedil;&otilde;es</a><a class=\"sub\" href=\"#\" data-section=\"videos\" onclick=\"return showSection('videos')\">V&iacute;deos</a><a class=\"sub\" href=\"#\" data-section=\"whatsapp\" onclick=\"return showSection('whatsapp')\">WhatsApp</a>"); //$NON-NLS-1$
            out.println("<h2>Ajuda</h2><a class=\"sub\" href=\"#\" data-section=\"ajuda\" onclick=\"return showSection('ajuda')\">Relat&oacute;rio e Anexo</a></div><div id=\"content\">"); //$NON-NLS-1$
            out.println("<div class=\"section\" id=\"info\"><div class=\"card\"><h2>Informa&ccedil;&otilde;es</h2><p><b>Status:</b> " + html(status) + "</p><p><b>Frames capturados:</b> " + frames.size() + "</p><p><b>Arquivos exportados:</b> " + exported.size() + "</p><p class=\"links\"><a href=\"whatsapp.html\">whatsapp.html</a><a href=\"whatsapp-coordinates.json\">whatsapp-coordinates.json</a><a href=\"texto.txt\">texto.txt</a><a href=\"hashes.txt\">hashes.txt</a><a href=\"Lista de Arquivos.csv\">Lista de Arquivos.csv</a><a href=\"manifest.json\">manifest.json</a></p></div></div>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            out.println("<div class=\"section\" id=\"busca\"><div class=\"card\"><h2>Busca por palavras-chave</h2><p>Use a busca do navegador neste relat&oacute;rio ou consulte os arquivos exportados na lista CSV.</p></div></div>"); //$NON-NLS-1$
            writeReportFileSection(out, "captura", "Captura", exported, "capture"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
        return "Categoria"; //$NON-NLS-1$
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
        out.println("<p class=\"subitem\"><b>Categorias (Opcional):</b> P&aacute;gina contendo lista dos arquivos agrupados segundo classifica&ccedil;&atilde;o autom&aacute;tica realizada pelo software pericial.</p>"); //$NON-NLS-1$
        out.println("<h3>Armazenamento e visualiza&ccedil;&atilde;o dos arquivos</h3>"); //$NON-NLS-1$
        out.println("<p>Os arquivos selecionados durante os exames foram renomeados e exportados para o diret&oacute;rio &quot;Exportados&quot; desta m&iacute;dia. Para obter os nomes e demais informa&ccedil;&otilde;es originais dos arquivos acesse as categorias do relat&oacute;rio.</p>"); //$NON-NLS-1$
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
        if ("capture".equals(filter)) return path.startsWith("screenshots/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("attachments".equals(filter)) return path.startsWith("anexos/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("audio".equals(filter)) return path.startsWith("anexos/audios/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("video".equals(filter)) return path.startsWith("anexos/videos/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("pdf".equals(filter)) return path.startsWith("anexos/pdfs/") || path.endsWith(".pdf"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if ("image".equals(filter)) return path.startsWith("anexos/imagens/"); //$NON-NLS-1$ //$NON-NLS-2$
        if ("scan".equals(filter)) return path.startsWith("anexos/pdfs/") || path.startsWith("anexos/imagens/"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if ("whatsapp".equals(filter)) return path.startsWith("anexos/"); //$NON-NLS-1$ //$NON-NLS-2$
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
        CaptureAnchor startAnchor = anchorForMessage(frames, captureStartMessageId, false);
        CaptureAnchor endAnchor = anchorForMessage(frames, captureEndMessageId, true);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
            out.println("{"); //$NON-NLS-1$
            out.println("  \"version\": 1,"); //$NON-NLS-1$
            out.println("  \"status\": " + json(status) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"startedAt\": " + json(Instant.now().toString()) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"startAnchor\": " + buildAnchorJson(startAnchor) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"endAnchor\": " + buildAnchorJson(endAnchor) + ","); //$NON-NLS-1$ //$NON-NLS-2$
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
        CaptureAnchor startAnchor = anchorForMessage(frames, captureStartMessageId, false);
        CaptureAnchor endAnchor = anchorForMessage(frames, captureEndMessageId, true);
        StringBuilder out = new StringBuilder();
        out.append("{\n"); //$NON-NLS-1$
        out.append("  \"version\": 1,\n"); //$NON-NLS-1$
        out.append("  \"status\": ").append(json(status)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"generatedAt\": ").append(json(Instant.now().toString())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"mode\": \"vertical-image-chat\",\n"); //$NON-NLS-1$
        out.append("  \"startAnchor\": ").append(buildAnchorJson(startAnchor)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"endAnchor\": ").append(buildAnchorJson(endAnchor)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
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

    private String buildAnchorJson(CaptureAnchor anchor) {
        CaptureAnchor safeAnchor = anchor == null ? new CaptureAnchor() : anchor.normalized();
        return "{\"id\": " + json(safeAnchor.id) //$NON-NLS-1$
                + ", \"type\": " + json(safeAnchor.type) //$NON-NLS-1$
                + ", \"text\": " + json(safeAnchor.text) //$NON-NLS-1$
                + ", \"date\": " + json(safeAnchor.date) //$NON-NLS-1$
                + ", \"hash\": " + json(safeAnchor.hash) //$NON-NLS-1$
                + ", \"documentTop\": " + safeAnchor.documentTop //$NON-NLS-1$
                + ", \"documentBottom\": " + safeAnchor.documentBottom + "}"; //$NON-NLS-1$ //$NON-NLS-2$
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
                applySensitiveCensorToCapturedImage(buffered, plan);
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

    private List<VisibleMediaBlock> getVisibleMediaBlocks(double safeBottom) {
        Object result = executeScriptAndWait("window.ipedChatCapture.getVisibleMediaBlocks(" + safeBottom + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        List<VisibleMediaBlock> blocks = new ArrayList<>();
        if (result == null || result.toString().isBlank()) {
            return blocks;
        }
        for (String row : result.toString().split(";")) { //$NON-NLS-1$
            String[] parts = row.split(",", -1); //$NON-NLS-1$
            if (parts.length >= 2) {
                String id = urlDecode(parts[0]);
                String hash = urlDecode(parts[1]);
                if (!id.isBlank() && !hash.isBlank()) {
                    blocks.add(new VisibleMediaBlock(id, hash));
                }
            }
        }
        return blocks;
    }

    private Set<String> getRangeMediaHashes(String startId, String endId) {
        Object result = executeScriptAndWait("window.ipedChatCapture.getRangeMediaHashes(" + js(startId) + "," //$NON-NLS-1$ //$NON-NLS-2$
                + js(endId) + ");"); //$NON-NLS-1$
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

    private void prepareSensitiveImageMatcher() {
        sensitiveBlurHashes.clear();
        sensitiveImageMatcher = null;
        if (sensitiveImagesFolder == null || !Files.isDirectory(sensitiveImagesFolder)) {
            return;
        }
        try {
            SensitiveImageMatcher matcher = new SensitiveImageMatcher(sensitiveImagesFolder, sensitiveSimilarityPercent);
            if (matcher.hasReferences()) {
                sensitiveImageMatcher = matcher;
                chatCapturePanel.setStatusText("Blur sensivel ativo (" + sensitiveSimilarityPercent + "%): " //$NON-NLS-1$ //$NON-NLS-2$
                        + sensitiveImagesFolder);
            }
        } catch (Throwable e) {
            LOGGER.warn("Unable to initialize sensitive image blur from {}", sensitiveImagesFolder, e); //$NON-NLS-1$
            chatCapturePanel.setStatusText("Blur sensivel desativado: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    private int normalizeSimilarityPercent(int percent) {
        if (percent <= 50) {
            return 50;
        }
        if (percent <= 75) {
            return 75;
        }
        return 90;
    }

    private int normalizeBlurPercent(int percent) {
        if (percent <= 20) {
            return 20;
        }
        if (percent <= 30) {
            return 30;
        }
        if (percent <= 60) {
            return 60;
        }
        return 90;
    }

    private int sensitiveBlurCssPixels() {
        switch (normalizeBlurPercent(sensitiveBlurPercent)) {
            case 20:
                return 5;
            case 30:
                return 8;
            case 60:
                return 15;
            default:
                return 22;
        }
    }

    private void applySensitiveBlurToVisibleImages() {
        SensitiveImageMatcher matcher = sensitiveImageMatcher;
        if (matcher == null) {
            return;
        }
        try {
            List<VisibleMediaBlock> visibleBlocks = getVisibleMediaBlocks(getViewportBottom());
            Set<String> hashesToBlur = new HashSet<>();
            Set<String> idsToBlur = new HashSet<>();
            for (VisibleMediaBlock block : visibleBlocks) {
                String hash = block.hash;
                if (sensitiveBlurHashes.contains(hash)) {
                    hashesToBlur.add(hash);
                    idsToBlur.add(block.id);
                    continue;
                }
                if (isSensitiveImageHash(hash, matcher)) {
                    sensitiveBlurHashes.add(hash);
                    hashesToBlur.add(hash);
                    idsToBlur.add(block.id);
                }
            }
            if (!idsToBlur.isEmpty()) {
                applySensitiveBlurMessageIds(idsToBlur);
            }
            if (!hashesToBlur.isEmpty()) {
                applySensitiveBlurHashes(hashesToBlur);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to apply sensitive image blur", e); //$NON-NLS-1$
        }
    }

    private void prepareSensitiveBlurForRange() {
        SensitiveImageMatcher matcher = sensitiveImageMatcher;
        if (matcher == null) {
            return;
        }
        try {
            Set<String> rangeHashes = getRangeMediaHashes(captureStartMessageId, captureEndMessageId);
            Set<String> hashesToBlur = new HashSet<>();
            for (String hash : rangeHashes) {
                if (sensitiveBlurHashes.contains(hash) || isSensitiveImageHash(hash, matcher)) {
                    sensitiveBlurHashes.add(hash);
                    hashesToBlur.add(hash);
                }
            }
            LOGGER.info("Sensitive blur range scan: {} media hash(es), {} matched at {}%", rangeHashes.size(), //$NON-NLS-1$
                    hashesToBlur.size(), sensitiveSimilarityPercent);
            if (!hashesToBlur.isEmpty()) {
                applySensitiveBlurHashes(hashesToBlur);
                chatCapturePanel.setStatusText("Blur sensivel aplicado em " + hashesToBlur.size() + " imagem(ns)."); //$NON-NLS-1$ //$NON-NLS-2$
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to prepare sensitive blur for capture range", e); //$NON-NLS-1$
        }
    }

    private boolean isSensitiveImageHash(String hash, SensitiveImageMatcher matcher) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        List<IItem> items = attachSearcher.getItems("hash:" + attachSearcher.escapeQuery(hash)); //$NON-NLS-1$
        for (IItem item : items) {
            if (!isImageItem(item)) {
                continue;
            }
            File source;
            try {
                source = item.getTempFile();
            } catch (IOException e) {
                LOGGER.debug("Unable to get temp image file for sensitive blur hash {}", hash, e); //$NON-NLS-1$
                continue;
            }
            if (source != null && source.exists() && matcher.matches(source)) {
                return true;
            }
        }
        if (matchesSensitiveImageFromPreviousCapture(hash, matcher)) {
            return true;
        }
        return false;
    }

    private boolean matchesSensitiveImageFromPreviousCapture(String hash, SensitiveImageMatcher matcher) {
        RecaptureJob job = activeRecaptureJob;
        if (job == null || job.folderPath == null || hash == null || hash.isBlank() || matcher == null) {
            return false;
        }
        Path coordinates = job.folderPath.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
        if (!Files.isRegularFile(coordinates)) {
            return false;
        }
        String wanted = hash.trim().toUpperCase();
        try {
            String content = Files.readString(coordinates);
            Matcher blockMatcher = Pattern.compile("\\{\\\"id\\\"\\s*:\\s*(?:\\\"(?:\\\\.|[^\\\"])*\\\"|null).*?\\}", Pattern.DOTALL) //$NON-NLS-1$
                    .matcher(content);
            while (blockMatcher.find()) {
                String object = blockMatcher.group();
                String blockHash = jsonField(object, "hash").trim().toUpperCase(); //$NON-NLS-1$
                if (!wanted.equals(blockHash)) {
                    continue;
                }
                String file = jsonField(object, "file"); //$NON-NLS-1$
                if (file == null || file.isBlank() || "null".equals(file)) { //$NON-NLS-1$
                    continue;
                }
                Path candidate = job.folderPath.resolve(file.replace('/', File.separatorChar)).normalize();
                if (Files.isRegularFile(candidate) && matcher.matches(candidate.toFile())) {
                    LOGGER.info("Sensitive blur matched previous capture file {} for hash {}", candidate, wanted); //$NON-NLS-1$
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to match sensitive image from previous capture for hash {}", hash, e); //$NON-NLS-1$
        }
        return false;
    }

    private boolean isImageItem(IItem item) {
        if (item == null) {
            return false;
        }
        String type = item.getType();
        if (type != null && type.toLowerCase().startsWith("image")) { //$NON-NLS-1$
            return true;
        }
        String name = safe(item.getName()).toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                || name.endsWith(".bmp"); //$NON-NLS-1$
    }

    private double getViewportBottom() {
        Object result = executeScriptAndWait("window.innerHeight || document.documentElement.clientHeight || 0;"); //$NON-NLS-1$
        return result instanceof Number ? ((Number) result).doubleValue() : 0;
    }

    private void applySensitiveBlurHashes(Set<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        StringBuilder script = new StringBuilder("if(window.ipedChatCapture){window.ipedChatCapture.setSensitiveBlurPixels(" //$NON-NLS-1$
                + sensitiveBlurCssPixels() + ");window.ipedChatCapture.blurMediaHashes(["); //$NON-NLS-1$
        int i = 0;
        for (String hash : hashes) {
            if (i++ > 0) {
                script.append(',');
            }
            script.append(js(hash));
        }
        script.append("]);}"); //$NON-NLS-1$
        executeScriptAndWait(script.toString());
    }

    private void applySensitiveBlurMessageIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StringBuilder script = new StringBuilder("if(window.ipedChatCapture){window.ipedChatCapture.setSensitiveBlurPixels(" //$NON-NLS-1$
                + sensitiveBlurCssPixels() + ");window.ipedChatCapture.blurMessageIds(["); //$NON-NLS-1$
        int i = 0;
        for (String id : ids) {
            if (i++ > 0) {
                script.append(',');
            }
            script.append(js(id));
        }
        script.append("]);}"); //$NON-NLS-1$
        executeScriptAndWait(script.toString());
    }

    private void applySensitiveCensorToCapturedImage(BufferedImage image, CapturePlan plan) {
        if (image == null || plan == null || plan.blocks == null || sensitiveBlurHashes.isEmpty()) {
            return;
        }
        int applied = 0;
        for (CaptureBlock block : plan.blocks) {
            if (block == null || block.hash == null || block.hash.isBlank() || !sensitiveBlurHashes.contains(block.hash)) {
                continue;
            }
            if (!"image".equalsIgnoreCase(safe(block.type))) { //$NON-NLS-1$
                continue;
            }
            int x = Math.max(0, Math.min(block.x, image.getWidth() - 1));
            int y = Math.max(0, Math.min(block.y, image.getHeight() - 1));
            int width = Math.max(1, Math.min(block.width, image.getWidth() - x));
            int height = Math.max(1, Math.min(block.height, image.getHeight() - y));
            pixelateRegion(image, x, y, width, height);
            applied++;
        }
        if (applied > 0) {
            LOGGER.info("Sensitive blur burned into captured frame: {} region(s)", applied); //$NON-NLS-1$
        }
    }

    private void pixelateRegion(BufferedImage image, int x, int y, int width, int height) {
        int blockSize = Math.max(12, Math.min(32, Math.max(width, height) / 18));
        Graphics2D g = image.createGraphics();
        try {
            for (int py = y; py < y + height; py += blockSize) {
                for (int px = x; px < x + width; px += blockSize) {
                    int bw = Math.min(blockSize, x + width - px);
                    int bh = Math.min(blockSize, y + height - py);
                    g.setColor(new java.awt.Color(averageRgb(image, px, py, bw, bh), true));
                    g.fillRect(px, py, bw, bh);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private int averageRgb(BufferedImage image, int x, int y, int width, int height) {
        long a = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        int stepX = Math.max(1, width / 6);
        int stepY = Math.max(1, height / 6);
        for (int py = y; py < y + height; py += stepY) {
            for (int px = x; px < x + width; px += stepX) {
                int argb = image.getRGB(px, py);
                a += (argb >>> 24) & 0xff;
                r += (argb >>> 16) & 0xff;
                g += (argb >>> 8) & 0xff;
                b += argb & 0xff;
                count++;
            }
        }
        if (count == 0) {
            return 0xff000000;
        }
        return ((int) (a / count) << 24) | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
    }

    private void clearSensitiveBlur() {
        try {
            executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.clearSensitiveBlur();}"); //$NON-NLS-1$
        } catch (Exception e) {
            LOGGER.debug("Unable to clear sensitive blur", e); //$NON-NLS-1$
        }
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
                        + "norm:function(s){var v=String(s||'').replace(/\\s+/g,' ').trim().toLowerCase();try{v=v.normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}catch(e){}return v;}," //$NON-NLS-1$
                        + "dateKey:function(s){var v=String(s||'');var m=v.match(/(\\d{4})[-\\/](\\d{2})[-\\/](\\d{2})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?/);if(m)return m[1]+m[2]+m[3]+m[4]+m[5]+(m[6]||'');m=v.match(/(\\d{2})[-\\/](\\d{2})[-\\/](\\d{4})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?/);if(m)return m[3]+m[2]+m[1]+m[4]+m[5]+(m[6]||'');return v.replace(/\\D/g,'');}," //$NON-NLS-1$
                        + "dateMatchesText:function(tx,date){var k=this.dateKey(date);if(!k)return false;var d=String(tx||'').replace(/\\D/g,'');if(!d)return false;if(d.indexOf(k)>=0)return true;if(k.length>=12&&d.indexOf(k.substring(0,12))>=0)return true;return false;}," //$NON-NLS-1$
                        + "anchorTokens:function(text,date){var n=this.norm(text),d=this.norm(date);if(d)n=n.replace(d,' ');n=n.replace(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?/g,' ');n=n.replace(/\\b\\d{10,}\\b/g,' ');var stop={whatsapp:1,chat:1,mensagem:1,arquivo:1,audio:1,imagem:1,video:1,file:1};var raw=n.split(/[^a-z0-9\\u00c0-\\u017f]+/),out=[],seen={};for(var i=0;i<raw.length;i++){var w=raw[i];if(w.length<3||stop[w]||seen[w])continue;seen[w]=1;out.push(w);}return out;}," //$NON-NLS-1$
                        + "dateNearElement:function(el,date){if(!this.dateKey(date))return false;var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){if(items[i]!==el)continue;for(var d=-2;d<=2;d++){var n=items[i+d];if(!n)continue;if(this.dateMatchesText(this.blockText(n),date))return true;}break;}return false;}," //$NON-NLS-1$
                        + "findMessageByAnchor:function(type,text,date,hash,anchorId,documentTop){var nt=this.norm(text),nh=this.norm(hash),wantType=this.norm(type),tokens=this.anchorTokens(text,date),best='',bestScore=0,bestHasDate=false,bestTokenHits=0;var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){var el=items[i],bt=this.norm(this.blockType(el)),bh=this.norm(this.blockHash(el)),tx=this.norm(this.blockText(el)),score=0,hasDate=false,tokenHits=0;if(this.dateMatchesText(tx,date))hasDate=true;else if(this.dateNearElement(el,date))hasDate=true;if(anchorId&&el.id===anchorId)score+=35;if(wantType&&wantType!=='message'&&bt===wantType)score+=20;if(wantType&&wantType!=='message'&&bt!==wantType&&nh)score-=10;if(nh&&bh===nh)score+=100;if(hasDate)score+=30;if(tokens.length){for(var t=0;t<tokens.length;t++){if(tx.indexOf(tokens[t])>=0)tokenHits++;}if(tokenHits>0){score+=Math.round(70*tokenHits/tokens.length);if(tokens.length<=2)score+=15;}}if(nt){if(tx===nt)score+=70;else if(tx.indexOf(nt)>=0||nt.indexOf(tx)>=0)score+=35;}if(!nh&&wantType&&wantType!=='message'&&bt!==wantType)continue;if(score>bestScore){bestScore=score;best=el.id;bestHasDate=hasDate;bestTokenHits=tokenHits;}}if(nh&&bestScore>=100)return best;if(!nh&&tokens.length&&bestTokenHits>0&&bestScore>=55)return best;if(!nh&&nt&&bestScore>=75)return best;if(anchorId&&best===anchorId&&bestScore>=35)return best;if(!nh&&wantType&&wantType!=='message'&&bestScore>=45)return best;return '';}," //$NON-NLS-1$
                        + "getVisibleCaptureBlocks:function(){var viewport=this.getCaptureViewport();var blocks=[];var sy=window.scrollY||window.pageYOffset||0;document.querySelectorAll(this.messageSelector).forEach((function(el){if(!el.id)return;var r=this.visualRect(el);if(r.bottom<viewport.top||r.top>viewport.bottom||r.width<=0||r.height<=0)return;var top=r.top;var bottom=r.bottom;var visibleTop=Math.max(top,viewport.top);var visibleBottom=Math.min(bottom,viewport.bottom);var visibleHeight=Math.max(0,visibleBottom-visibleTop);blocks.push({id:el.id,type:this.blockType(el),left:r.left,top:top,right:r.right,bottom:bottom,width:r.width,height:bottom-top,documentTop:sy+top,documentBottom:sy+bottom,complete:top>=viewport.top&&bottom<=viewport.bottom,hash:this.blockHash(el),text:this.blockText(el),visibleHeight:visibleHeight});}).bind(this));return blocks;}," //$NON-NLS-1$
                        + "getAllCaptureBlocks:function(){var blocks=[],self=this;document.querySelectorAll(this.messageSelector).forEach(function(el){if(!el.id)return;blocks.push({id:el.id,type:self.blockType(el),text:self.blockText(el),hash:self.blockHash(el),el:el});});return blocks;}," //$NON-NLS-1$
                        + "extractTimestamp:function(text){var s=String(text||''),m=s.match(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?/);if(m)return m[0];m=s.match(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}/);if(m)return m[0];return '';}," //$NON-NLS-1$
                        + "blockTimestamp:function(blocks,index){var b=blocks[index];if(!b)return '';var direct=this.extractTimestamp(b.text||'');if(direct)return direct;for(var d=-2;d<=2;d++){if(d===0)continue;var n=blocks[index+d];if(!n)continue;var near=this.extractTimestamp(n.text||'');if(near)return near;}return '';}," //$NON-NLS-1$
                        + "cleanMessageText:function(text){var s=this.norm(text||'');s=s.replace(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?/g,' ');s=s.replace(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}/g,' ');s=s.replace(/\\b\\d{10,}\\b/g,' ');s=s.replace(/\\s+/g,' ').trim();return s;}," //$NON-NLS-1$
                        + "sameMessageText:function(blockText,anchorText){var a=this.cleanMessageText(anchorText),b=this.cleanMessageText(blockText);if(!a||!b)return false;if(a===b)return true;if(b.indexOf(a)>=0)return true;if(a.indexOf(b)>=0)return true;return false;}," //$NON-NLS-1$
                        + "sameDateText:function(blockText,anchorText){var a=this.norm(anchorText||''),b=this.norm(blockText||'');if(!a||!b)return false;if(a===b)return true;if(b.indexOf(a)>=0)return true;if(a.indexOf(b)>=0)return true;var ak=this.dateKey(a),bk=this.dateKey(b);return !!(ak&&bk&&ak===bk);}," //$NON-NLS-1$
                        + "sameRecaptureAnchor:function(blocks,index,anchor){var b=blocks[index];if(!b||!anchor)return false;var wantedType=this.norm(anchor.type),blockType=this.norm(b.type);if(blockType==='date'&&wantedType!=='date')return false;if(wantedType==='date')return this.sameDateText(b.text,anchor.text||anchor.date);var blockTime=this.dateKey(this.blockTimestamp(blocks,index)),anchorTime=this.dateKey(anchor.date||anchor.text);if(!blockTime||!anchorTime||blockTime!==anchorTime)return false;var anchorHash=this.norm(anchor.hash);if(anchorHash)return this.norm(b.hash)===anchorHash;return this.sameMessageText(b.text,anchor.text);}," //$NON-NLS-1$
                        + "resolveRecaptureRangeFromDom:function(startAnchor,endAnchor){var blocks=this.getAllCaptureBlocks(),startId='',endId='',startIndex=-1;for(var i=0;i<blocks.length;i++){if(this.sameRecaptureAnchor(blocks,i,startAnchor)){startId=blocks[i].id;startIndex=i;break;}}if(startIndex>=0){for(var j=startIndex;j<blocks.length;j++){if(this.sameRecaptureAnchor(blocks,j,endAnchor)){endId=blocks[j].id;break;}}}return startId+'|'+endId;}," //$NON-NLS-1$
                        + "getMediaBlocksBetween:function(startId,endId){var blocks=this.getAllCaptureBlocks(),out=[],active=false,enc=encodeURIComponent;for(var i=0;i<blocks.length;i++){if(blocks[i].id===startId)active=true;if(active&&blocks[i].hash)out.push(enc(blocks[i].id)+','+enc(blocks[i].hash));if(active&&blocks[i].id===endId)break;}return out.join(';')}," //$NON-NLS-1$
                        + "visibleBlockHasDate:function(blocks,index,date){if(!this.dateKey(date))return false;var b=blocks[index];if(b&&this.dateMatchesText(b.text||'',date))return true;if(b&&b.id){var el=document.getElementById(b.id);if(el&&this.dateNearElement(el,date))return true;}for(var d=-2;d<=2;d++){if(d===0)continue;var n=blocks[index+d];if(!n)continue;if(this.dateMatchesText(n.text||'',date))return true;if(n.id){var nel=document.getElementById(n.id);if(nel&&this.dateNearElement(nel,date))return true;}}return false;}," //$NON-NLS-1$
                        + "anchorMatchesBlock:function(blocks,index,type,text,date,hash){var block=blocks[index];if(!block)return false;var wantType=this.norm(type),tx=this.norm(block.text||''),bt=this.norm(block.type||''),bh=this.norm(block.hash||''),h=this.norm(hash),hasDate=this.visibleBlockHasDate(blocks,index,date);if(bt==='date'&&wantType!=='date')return false;if(wantType==='date'){var wantedDate=this.norm(text||date),blockText=this.norm(block.text||'');if(!wantedDate||!blockText)return false;if(blockText===wantedDate)return true;if(blockText.indexOf(wantedDate)>=0)return true;if(wantedDate.indexOf(blockText)>=0)return true;var wantedKey=this.dateKey(wantedDate),blockKey=this.dateKey(blockText);return !!(wantedKey&&blockKey&&wantedKey===blockKey);}if(h)return bh===h&&hasDate;var tokens=this.anchorTokens(text,date);if(!hasDate||!tokens.length)return false;var hits=0;for(var i=0;i<tokens.length;i++){if(tx.indexOf(tokens[i])>=0)hits++;}return hits>0&&hits>=Math.max(1,Math.ceil(tokens.length*.4));}," //$NON-NLS-1$
                        + "scanVisibleBlocksForAnchors:function(st,stx,sd,sh,et,etx,ed,eh,currentStartId){var blocks=this.getVisibleCaptureBlocks(),startId=currentStartId||'',endId='';for(var i=0;i<blocks.length;i++){if(!startId&&this.anchorMatchesBlock(blocks,i,st,stx,sd,sh))startId=blocks[i].id;if(startId&&this.anchorMatchesBlock(blocks,i,et,etx,ed,eh)){endId=blocks[i].id;break;}}return startId+'|'+endId;}," //$NON-NLS-1$
                    + "getVisibleMediaHashes:function(safeBottom){var a=[],seen={},viewport=this.getCaptureViewport(),self=this;document.querySelectorAll(this.messageSelector).forEach(function(el){var r=self.visualRect(el);if(r.bottom<viewport.top||r.top>safeBottom||r.width<=0||r.height<=0)return;var input=el.querySelector('input.check[name]');if(input&&input.name&&!seen[input.name]){seen[input.name]=true;a.push(input.name);}});return a.join('|');}," //$NON-NLS-1$
                    + "getVisibleMediaBlocks:function(safeBottom){var a=[],seen={},viewport=this.getCaptureViewport(),self=this,enc=encodeURIComponent;document.querySelectorAll(this.messageSelector).forEach(function(el){if(!el.id)return;var r=self.visualRect(el);if(r.bottom<viewport.top||r.top>safeBottom||r.width<=0||r.height<=0)return;if(self.blockType(el)!=='image'&&!el.querySelector('.imageImg,img,[style*=\"background-image\"]'))return;var input=el.querySelector('input.check[name]');if(input&&input.name&&!seen[el.id+'|'+input.name]){seen[el.id+'|'+input.name]=true;a.push(enc(el.id)+','+enc(input.name));}});return a.join(';');}," //$NON-NLS-1$
                    + "getRangeMediaHashes:function(startId,endId){var rows=this.getMediaBlocksBetween(startId,endId),a=[],seen={};if(!rows)return '';rows.split(';').forEach(function(row){var p=row.split(',');if(p.length>=2){var h=decodeURIComponent(p[1]);if(h&&!seen[h]){seen[h]=true;a.push(h);}}});return a.join('|');}," //$NON-NLS-1$
                    + "sensitiveBlurPixels:22," //$NON-NLS-1$
                    + "setSensitiveBlurPixels:function(px){px=parseInt(px,10);this.sensitiveBlurPixels=(isNaN(px)||px<1)?22:Math.min(40,px);}," //$NON-NLS-1$
                    + "blurMessageIds:function(ids){var self=this;(ids||[]).forEach(function(id){var el=document.getElementById(id);if(el)self.censorMessageElement(el);});}," //$NON-NLS-1$
                    + "blurMediaHashes:function(hashes){var set={},self=this;(hashes||[]).forEach(function(h){set[h]=true;});document.querySelectorAll('input.check[name]').forEach(function(input){if(!set[input.name])return;var el=input;while(el&&el!==document.body){if(el.id&&(el.classList.contains('linha')||el.tagName==='TR'||el.hasAttribute('data-iped-capture-date')))break;el=el.parentElement;}if(el)self.censorMessageElement(el);});}," //$NON-NLS-1$
                    + "censorMessageElement:function(el){if(!el)return;el.setAttribute('data-iped-sensitive-blur','1');var targets=el.querySelectorAll('.imageImg,img,canvas,[style*=\"background-image\"]');if(!targets.length)targets=[el];for(var i=0;i<targets.length;i++)this.censorVisualTarget(targets[i],el);}," //$NON-NLS-1$
                    + "censorVisualTarget:function(target,owner){if(!target)return;if(!target.hasAttribute('data-iped-original-filter'))target.setAttribute('data-iped-original-filter',target.style.filter||'');var px=this.sensitiveBlurPixels||22;target.style.filter='blur('+px+'px) saturate(.25) brightness(.55)';}," //$NON-NLS-1$
                    + "clearSensitiveBlur:function(){document.querySelectorAll('[data-iped-sensitive-blur]').forEach(function(el){Array.prototype.forEach.call(el.querySelectorAll('[data-iped-original-filter]'),function(img){img.style.filter=img.getAttribute('data-iped-original-filter')||'';img.removeAttribute('data-iped-original-filter');});el.removeAttribute('data-iped-sensitive-blur');});}," //$NON-NLS-1$
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

    private String urlDecode(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
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
        if (path.startsWith("screenshots/")) return "Captura"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.startsWith("anexos/audios/")) return "Audio"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.startsWith("anexos/videos/")) return "Video"; //$NON-NLS-1$ //$NON-NLS-2$
        if (path.startsWith("anexos/pdfs/") || path.endsWith(".pdf")) return "Documento PDF"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (path.startsWith("anexos/imagens/")) return "Imagem"; //$NON-NLS-1$ //$NON-NLS-2$
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

    private static class CaptureRunResult {
        final String status;
        final Path captureDir;

        CaptureRunResult(String status, Path captureDir) {
            this.status = status;
            this.captureDir = captureDir;
        }
    }

    private static class RecaptureJob {
        String folder = ""; //$NON-NLS-1$
        Path folderPath;
        String title = ""; //$NON-NLS-1$
        String chatId = ""; //$NON-NLS-1$
        String status = ""; //$NON-NLS-1$
        String startMessageId = ""; //$NON-NLS-1$
        String endMessageId = ""; //$NON-NLS-1$
        CaptureAnchor startAnchor = new CaptureAnchor();
        CaptureAnchor endAnchor = new CaptureAnchor();
        String sourceHash = ""; //$NON-NLS-1$
        String sourcePath = ""; //$NON-NLS-1$
        String sourceName = ""; //$NON-NLS-1$
        String sourceTitle = ""; //$NON-NLS-1$
        String luceneQuery = ""; //$NON-NLS-1$
        List<String> luceneQueries = new ArrayList<>();
        String invalidReason;
        boolean valid;

        String displayLabel() {
            String base = folder + " | " + (title == null || title.isBlank() ? chatId : title) //$NON-NLS-1$
                    + " | " + startAnchor.display() + " -> " + endAnchor.display(); //$NON-NLS-1$ //$NON-NLS-2$
            return valid ? base : base + " [invalido: " + invalidReason + "]"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static class RecaptureLocatedRange {
        final String startId;
        final String endId;

        RecaptureLocatedRange(String startId, String endId) {
            this.startId = startId == null ? "" : startId; //$NON-NLS-1$
            this.endId = endId == null ? "" : endId; //$NON-NLS-1$
        }

        static RecaptureLocatedRange parse(String value) {
            String[] parts = value == null ? new String[0] : value.split("\\|", -1); //$NON-NLS-1$
            return new RecaptureLocatedRange(parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static class CaptureAnchor {
        String id = ""; //$NON-NLS-1$
        String type = ""; //$NON-NLS-1$
        String text = ""; //$NON-NLS-1$
        String date = ""; //$NON-NLS-1$
        String hash = ""; //$NON-NLS-1$
        double documentTop = -1;
        double documentBottom = -1;

        CaptureAnchor normalized() {
            type = type == null ? "" : type.trim(); //$NON-NLS-1$
            text = text == null ? "" : text.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
            date = date == null ? "" : date.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
            hash = hash == null ? "" : hash.trim().toUpperCase(); //$NON-NLS-1$
            return this;
        }

        boolean isUsable() {
            normalized();
            return !date.isBlank() && (!hash.isBlank() || !text.isBlank());
        }

        String display() {
            normalized();
            String key = hash.isBlank() ? text : hash;
            if (key.length() > 64) {
                key = key.substring(0, 64) + "..."; //$NON-NLS-1$
            }
            return type + " " + date + " " + key; //$NON-NLS-1$ //$NON-NLS-2$
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

    private static class VisibleMediaBlock {
        final String id;
        final String hash;

        VisibleMediaBlock(String id, String hash) {
            this.id = id;
            this.hash = hash;
        }
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

    private static class SensitiveImageMatcher {
        private static final int MIN_FEATURES = 20;
        private static final int MIN_GOOD_MATCHES = 12;
        private static final double LOWE_RATIO = 0.72d;
        private static final float MAX_HAMMING_DISTANCE = 60f;
        private static volatile boolean opencvLoaded;

        private final ORB orb;
        private final DescriptorMatcher matcher;
        private final double minSimilarityRatio;
        private final Set<String> referenceFileHashes = new HashSet<>();
        private final List<Mat> referenceDescriptors = new ArrayList<>();

        SensitiveImageMatcher(Path folder, int similarityPercent) throws IOException {
            loadOpenCv();
            minSimilarityRatio = Math.max(0.50d, Math.min(0.90d, similarityPercent / 100.0d));
            orb = ORB.create(3000);
            matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile).filter(SensitiveImageMatcher::isSupportedImage).forEach(path -> {
                    addReferenceFileHash(path.toFile());
                    Mat descriptors = descriptors(path.toFile());
                    if (descriptors != null && !descriptors.empty()) {
                        referenceDescriptors.add(descriptors);
                    }
                });
            }
        }

        boolean hasReferences() {
            return !referenceDescriptors.isEmpty() || !referenceFileHashes.isEmpty();
        }

        boolean matches(File candidate) {
            if (matchesReferenceFileHash(candidate)) {
                return true;
            }
            Mat candidateDescriptors = descriptors(candidate);
            if (candidateDescriptors == null || candidateDescriptors.empty()) {
                return false;
            }
            for (Mat reference : referenceDescriptors) {
                if (isSimilar(reference, candidateDescriptors)) {
                    return true;
                }
            }
            return false;
        }

        private void addReferenceFileHash(File file) {
            try {
                String md5 = digestFile(file, "MD5"); //$NON-NLS-1$
                String sha256 = digestFile(file, "SHA-256"); //$NON-NLS-1$
                if (!md5.isBlank()) {
                    referenceFileHashes.add(md5);
                }
                if (!sha256.isBlank()) {
                    referenceFileHashes.add(sha256);
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to hash sensitive reference image {}", file, e); //$NON-NLS-1$
            }
        }

        private boolean matchesReferenceFileHash(File candidate) {
            if (candidate == null || !candidate.isFile() || referenceFileHashes.isEmpty()) {
                return false;
            }
            try {
                String md5 = digestFile(candidate, "MD5"); //$NON-NLS-1$
                if (!md5.isBlank() && referenceFileHashes.contains(md5)) {
                    return true;
                }
                String sha256 = digestFile(candidate, "SHA-256"); //$NON-NLS-1$
                return !sha256.isBlank() && referenceFileHashes.contains(sha256);
            } catch (IOException e) {
                LOGGER.debug("Unable to hash candidate image {}", candidate, e); //$NON-NLS-1$
                return false;
            }
        }

        private static String digestFile(File file, String algorithm) throws IOException {
            if (file == null || !file.isFile()) {
                return ""; //$NON-NLS-1$
            }
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm);
                try (InputStream in = Files.newInputStream(file.toPath());
                        DigestInputStream dis = new DigestInputStream(in, digest)) {
                    byte[] buffer = new byte[8192];
                    while (dis.read(buffer) != -1) {
                        // consume stream
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : digest.digest()) {
                    sb.append(String.format("%02X", b)); //$NON-NLS-1$
                }
                return sb.toString();
            } catch (Exception e) {
                throw new IOException("Unable to calculate " + algorithm + " for " + file, e); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        private boolean isSimilar(Mat reference, Mat candidate) {
            if (reference.rows() < MIN_FEATURES || candidate.rows() < MIN_FEATURES) {
                return false;
            }
            int minFeatures = Math.max(1, Math.min(reference.rows(), candidate.rows()));
            int forward = countGoodKnnMatches(reference, candidate);
            if (forward < MIN_GOOD_MATCHES) {
                return false;
            }
            int backward = countGoodKnnMatches(candidate, reference);
            int good = Math.min(forward, backward);
            double ratio = (double) good / minFeatures;
            return good >= MIN_GOOD_MATCHES && ratio >= minSimilarityRatio;
        }

        private int countGoodKnnMatches(Mat query, Mat train) {
            List<MatOfDMatch> knn = new ArrayList<>();
            matcher.knnMatch(query, train, knn, 2);
            int good = 0;
            for (MatOfDMatch mat : knn) {
                DMatch[] matches = mat.toArray();
                if (matches.length < 2) {
                    continue;
                }
                DMatch best = matches[0];
                DMatch second = matches[1];
                if (best.distance <= MAX_HAMMING_DISTANCE && best.distance < second.distance * LOWE_RATIO) {
                    good++;
                }
            }
            return good;
        }

        private Mat descriptors(File image) {
            if (image == null || !image.isFile()) {
                return null;
            }
            Mat img = Imgcodecs.imread(image.getAbsolutePath());
            if (img.empty()) {
                return null;
            }
            Mat gray = new Mat();
            if (img.channels() > 1) {
                Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = img;
            }
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            Mat descriptors = new Mat();
            orb.detectAndCompute(gray, new Mat(), keypoints, descriptors);
            return descriptors;
        }

        private static boolean isSupportedImage(Path path) {
            String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(); //$NON-NLS-1$
            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    || name.endsWith(".bmp"); //$NON-NLS-1$
        }

        private static synchronized void loadOpenCv() throws IOException {
            if (opencvLoaded) {
                return;
            }
            List<Path> candidates = Arrays.asList(
                    Path.of(System.getProperty("user.dir", "."), "../plugins/opencv_java4120.dll"), //$NON-NLS-1$ //$NON-NLS-2$
                    Path.of(System.getProperty("user.dir", "."), "plugins/opencv_java4120.dll"), //$NON-NLS-1$ //$NON-NLS-2$
                    Path.of("..", "plugins", "opencv_java4120.dll")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            for (Path candidate : candidates) {
                Path absolute = candidate.toAbsolutePath().normalize();
                if (Files.isRegularFile(absolute)) {
                    System.load(absolute.toString());
                    opencvLoaded = true;
                    LOGGER.info("OpenCV loaded for sensitive image blur: {}", Core.VERSION); //$NON-NLS-1$
                    return;
                }
            }
            throw new IOException("opencv_java4120.dll nao encontrada na pasta plugins"); //$NON-NLS-1$
        }
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
