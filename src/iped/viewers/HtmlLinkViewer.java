package iped.viewers;

import java.io.File;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import iped.parsers.threema.ThreemaParser;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
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
import javafx.scene.control.SeparatorMenuItem;
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

    public static final String CHAT_CAPTURE_PLUGIN_VERSION = "2.8.11-beta"; //$NON-NLS-1$
    private static final int MAX_RECAPTURE_CANDIDATES = 100000;
    private static final int MANIFEST_VERSION = 3;
    private static final int COORDINATES_SCHEMA_VERSION = 2;
    private static final int HTML_GENERATOR_VERSION = 4;
    private static final String CAPTURE_MEDIA_STOP_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();"; //$NON-NLS-1$
    private static final String CAPTURE_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-Pq7vIvwsQpte7OdNyOYYE9cKIEPmIFR7E/G7kB1JaJI='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"; //$NON-NLS-1$
    private static final long MAX_HTML_MESSAGE_ATTACHMENT_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_HTML_MESSAGE_CAPTURE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_HTML_MESSAGE_COUNT = 25000;
    private static final int MAX_HTML_MESSAGE_TEXT_CHARS = 1024 * 1024;

    protected AttachmentSearcher attachSearcher;

    private HashSet<String> mediaHashesInView = new HashSet<>();

    private boolean cheking = false;

    private final ChatCapturePanel chatCapturePanel = new ChatCapturePanel(this);
    private volatile IItem captureSourceItem;
    private volatile String captureContextMessageId;
    private volatile String captureStartMessageId;
    private volatile String captureEndMessageId;
    private volatile boolean captureRunning;
    private final AtomicBoolean captureStarting = new AtomicBoolean(false);
    private final AtomicBoolean recapturePreparing = new AtomicBoolean(false);
    private volatile boolean stopCaptureRequested;
    private final ExecutorService uiScriptExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "IPED-chat-ui-script"); //$NON-NLS-1$
        thread.setDaemon(true);
        return thread;
    });
    private volatile Path sensitiveImagesFolder;
    private volatile int sensitiveSimilarityPercent = 90;
    private volatile int sensitiveBlurPercent = 90;
    private volatile Path originAuditFolder;
    private volatile Path originParentIpedFolder;
    private volatile CustodyCaseRecord activeCustodyRecord;
    private volatile String lastKnownRootHash = ""; //$NON-NLS-1$
    private volatile SensitiveImageMatcher sensitiveImageMatcher;
private volatile RecaptureJob activeRecaptureJob;
    private final Set<String> sensitiveBlurHashes = new HashSet<>();
    private final ChatAnnotationStore annotationStore = new ChatAnnotationStore();
    private volatile ChatAnnotationSet pendingAnnotations;
    private volatile AnnotationPendingLink pendingLink;
    private volatile Path currentCaptureOutputRoot;
    private final List<CaptureFrame> currentCaptureFrames = new ArrayList<>();
    private volatile String avatarBase64Cache;
    private JCheckBox recaptureAnnotationsToggle;
    private JCheckBox recaptureLegacyMigrationToggle;
    private JCheckBox recaptureLegacyResetToggle;
    private volatile boolean linkedCaptureViewer;

    /** Teto de resultados devolvidos ao painel, para chats muito grandes. */
    private static final int SEARCH_RESULT_LIMIT = 400;

    private final List<ChatSearchHit> searchHits = new ArrayList<>();
    private final Object searchLock = new Object();
    private final java.util.concurrent.atomic.AtomicInteger searchGeneration = new java.util.concurrent.atomic.AtomicInteger();
    private volatile int searchTotal;
    private volatile int searchIndex = -1;

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
                    MenuItem notes = new MenuItem(Messages.getString("MenuClass.ChatCaptureNotes")); //$NON-NLS-1$
                    MenuItem link = new MenuItem(Messages.getString("MenuClass.ChatCaptureLink")); //$NON-NLS-1$
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
                    if (!isDate) menu.getItems().add(end);
                    menu.getItems().add(clear);
                    // Na janela do chat vinculado nao ha anotacao nova: o vinculo
                    // pertence a mensagem de origem.
                    if (!linkedCaptureViewer) {
                        menu.getItems().add(new SeparatorMenuItem());
                        menu.getItems().addAll(notes, link);
                    }
                    if (!linkedCaptureViewer) {
                        applyAnnotationMenuState(notes, link);
                    }
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
        applyCaptureMarker(captureContextMessageId, CaptureBoundaryResolver.Role.START, "ChatCapturePanel.StartMarked"); //$NON-NLS-1$
    }

    private void setChatCaptureEndFromContext() {
        if (captureContextMessageId == null || captureContextMessageId.isBlank()) {
            return;
        }
        applyCaptureMarker(captureContextMessageId, CaptureBoundaryResolver.Role.END, "ChatCapturePanel.EndMarked"); //$NON-NLS-1$
    }

    private void setChatCaptureStartDateFromContext() {
        if (captureContextMessageId == null || captureContextMessageId.isBlank()) {
            return;
        }
        applyCaptureMarker(captureContextMessageId, CaptureBoundaryResolver.Role.START, "ChatCapturePanel.StartDateMarked"); //$NON-NLS-1$
    }

    private void applyCaptureMarker(String requestedId, CaptureBoundaryResolver.Role role, String successKey) {
        try {
            List<CaptureBoundaryResolver.Row> rows = readCaptureBoundaryRows();
            CaptureBoundaryResolver.Resolution result = CaptureBoundaryResolver.resolve(rows, requestedId, role);
            if (!result.isValid()) {
                chatCapturePanel.setStatus(captureBoundaryMessageKey(result.errorCode));
                return;
            }
            if (role == CaptureBoundaryResolver.Role.START) captureStartMessageId = result.messageId;
            else captureEndMessageId = result.messageId;
            highlightCaptureMarks();
            chatCapturePanel.setStatus(successKey);
        } catch (Exception failure) {
            LOGGER.debug("Unable to resolve chat capture boundary (role={})", role, failure); //$NON-NLS-1$
            chatCapturePanel.setStatus("ChatCapturePanel.MarkerMissing"); //$NON-NLS-1$
        }
    }

    /** Validate both markers before verifier installation, output-index writes or worker startup. */
    boolean validateCaptureStart(File outputFolder) {
        if (captureRunning) return false;
        if (outputFolder == null || outputFolder.getPath().isBlank()) {
            chatCapturePanel.setStatus("ChatCapturePanel.MissingFolder"); //$NON-NLS-1$
            return false;
        }
        CaptureRangeValidation range = resolveCaptureRange();
        if (!range.valid()) {
            chatCapturePanel.setStatus(captureBoundaryMessageKey(range.errorCode));
            return false;
        }
        captureStartMessageId = range.start.messageId;
        captureEndMessageId = range.end.messageId;
        LOGGER.debug("Capture range validated: start={} end={}", captureStartMessageId, captureEndMessageId); //$NON-NLS-1$
        chatCapturePanel.setStatusText(Messages.getString("ChatCapturePanel.RangeReady") + " " //$NON-NLS-1$
                + captureStartMessageId + " -> " + captureEndMessageId); //$NON-NLS-1$
        return true;
    }

    private CaptureRangeValidation resolveCaptureRange() {
        if (captureStartMessageId == null || captureStartMessageId.isBlank()) return CaptureRangeValidation.error("start_marker_missing"); //$NON-NLS-1$
        if (captureEndMessageId == null || captureEndMessageId.isBlank()) return CaptureRangeValidation.error("end_marker_missing"); //$NON-NLS-1$
        try {
            List<CaptureBoundaryResolver.Row> rows = readCaptureBoundaryRows();
            CaptureBoundaryResolver.Resolution start = CaptureBoundaryResolver.resolve(rows, captureStartMessageId,
                    CaptureBoundaryResolver.Role.START);
            CaptureBoundaryResolver.Resolution end = CaptureBoundaryResolver.resolve(rows, captureEndMessageId,
                    CaptureBoundaryResolver.Role.END);
            String error = CaptureBoundaryResolver.validateRange(rows, start, end);
            return error.isEmpty() ? CaptureRangeValidation.success(start, end) : CaptureRangeValidation.error(error);
        } catch (Exception failure) {
            LOGGER.debug("Unable to validate chat capture range", failure); //$NON-NLS-1$
            return CaptureRangeValidation.error("marker_missing"); //$NON-NLS-1$
        }
    }

    private List<CaptureBoundaryResolver.Row> readCaptureBoundaryRows() throws IOException {
        String script = "(function(){var out=[];document.querySelectorAll('div.linha').forEach(function(row){var raw=row.id||'',messageId=/^-?\\d+(?:_[0-9a-zA-Z:._-]+)?$/.test(raw)?raw:'',dates=[];if(row.hasAttribute('data-iped-capture-date')&&row.id)dates.push(row.id);row.querySelectorAll('[data-iped-capture-date][id]').forEach(function(date){dates.push(date.id);});if(messageId||dates.length)out.push({messageId:messageId,dateIds:dates});});return JSON.stringify(out);})()"; //$NON-NLS-1$
        Object raw = executeScriptAndWait(script);
        if (!(raw instanceof String)) throw new IOException("Não foi possível ler os marcadores de mensagem do DOM."); //$NON-NLS-1$
        JSONArray jsonRows = new JSONArray((String) raw);
        List<CaptureBoundaryResolver.Row> rows = new ArrayList<>(jsonRows.length());
        for (int i = 0; i < jsonRows.length(); i++) {
            JSONObject row = jsonRows.optJSONObject(i);
            if (row == null) throw new IOException("Lista de marcadores DOM inválida."); //$NON-NLS-1$
            JSONArray dateIds = row.optJSONArray("dateIds"); //$NON-NLS-1$
            List<String> dates = new ArrayList<>();
            for (int d = 0; dateIds != null && d < dateIds.length(); d++) dates.add(dateIds.optString(d, "")); //$NON-NLS-1$
            rows.add(new CaptureBoundaryResolver.Row(row.optString("messageId", ""), dates)); //$NON-NLS-1$
        }
        return rows;
    }

    private String captureBoundaryMessageKey(String errorCode) {
        switch (errorCode == null ? "" : errorCode) { //$NON-NLS-1$
        case "end_date_not_supported": return "ChatCapturePanel.EndDateNotAllowed"; //$NON-NLS-1$ //$NON-NLS-2$
        case "start_date_no_message": return "ChatCapturePanel.StartDateNoMessage"; //$NON-NLS-1$ //$NON-NLS-2$
        case "marker_ambiguous": return "ChatCapturePanel.MarkerAmbiguous"; //$NON-NLS-1$ //$NON-NLS-2$
        case "invalid_order": return "ChatCapturePanel.InvalidRangeOrder"; //$NON-NLS-1$ //$NON-NLS-2$
        case "marker_not_message": return "ChatCapturePanel.MarkerNotMessage"; //$NON-NLS-1$ //$NON-NLS-2$
        case "start_marker_missing":
        case "end_marker_missing":
        case "marker_missing": return "ChatCapturePanel.MarkerMissing"; //$NON-NLS-1$ //$NON-NLS-2$
        default: return "ChatCapturePanel.MarkerMissing"; //$NON-NLS-1$
        }
    }

private void clearChatCaptureMarks() {
        captureStartMessageId = null;
        captureEndMessageId = null;
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.clear();}"); //$NON-NLS-1$
        chatCapturePanel.updateCounters(0, 0);
        chatCapturePanel.updateAnnotationCounters(0, 0, 0);
        chatCapturePanel.setStatus("ChatCapturePanel.MarkMessages"); //$NON-NLS-1$
    }

    // ------------------------------------------------------------------ notas e links

    private String currentChatKey() {
        IItem item = captureSourceItem;
        if (item == null) {
            return ""; //$NON-NLS-1$
        }
        return ChatAnnotationStore.chatKeyOf(item.getHash(), item.getName(), item.getPath());
    }

    private String currentChatTitle() {
        IItem item = captureSourceItem;
        if (item == null) {
            return ""; //$NON-NLS-1$
        }
        String name = item.getName();
        int dash = name == null ? -1 : name.lastIndexOf(" - "); //$NON-NLS-1$
        return dash >= 0 && dash + 3 < name.length() ? name.substring(dash + 3).trim() : safe(name);
    }

    private void bindAnnotationStore(Path outputRoot) {
        annotationStore.bindOutputRoot(outputRoot);
        refreshAnnotationBadges();
    }

    private int getMessageIndex(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return -1;
        }
        Object result = executeScriptAndWait("window.ipedChatCapture ? window.ipedChatCapture.getMessageIndex(" //$NON-NLS-1$
                + js(messageId) + ") : -1;"); //$NON-NLS-1$
        return result instanceof Number ? ((Number) result).intValue() : -1;
    }

    private AnnotationEligibility evaluateAnnotationEligibility(String messageId) {
        boolean isDate = messageId != null && isDateCaptureElement(messageId);
        return AnnotationEligibility.evaluate(messageId, captureStartMessageId, captureEndMessageId,
                getMessageIndex(messageId), getMessageIndex(captureStartMessageId), getMessageIndex(captureEndMessageId),
                isDate);
    }

    private void applyAnnotationMenuState(MenuItem notes, MenuItem link) {
        AnnotationEligibility eligibility;
        if (annotationOnlyMode) {
            if (captureContextMessageId == null || captureContextMessageId.isBlank()
                    || isDateCaptureElement(captureContextMessageId)) {
                eligibility = AnnotationEligibility.blocked("MenuClass.ChatCaptureNotesDisabledDate");
            } else {
                eligibility = AnnotationEligibility.ok();
            }
        } else {
            eligibility = evaluateAnnotationEligibility(captureContextMessageId);
        }
        String target = captureContextMessageId;
        // MenuItem nao aceita tooltip, entao o motivo vai no proprio rotulo do item.
        String suffix = eligibility.eligible ? "" : "  -  " + Messages.getString(eligibility.reasonKey); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        notes.setText(Messages.getString("MenuClass.ChatCaptureNotes") + suffix); //$NON-NLS-1$
        link.setText(Messages.getString("MenuClass.ChatCaptureLink") + suffix); //$NON-NLS-1$
        notes.setDisable(!eligibility.eligible);
        link.setDisable(!eligibility.eligible);
        notes.setOnAction(e -> openNoteDialogAsync(target));
        link.setOnAction(e -> openLinkDialogAsync(target));
    }

    /**
 * Lista os chats do caso. Roda so quando o analista pede, porque consulta o indice.
 * O nome do chat vem de {@code dc:title}, gravado pelo parser no item do chat.
 *
 * Usa varias formas de consulta e une os resultados, porque o campo de tipo de
 * conteudo e tokenizado e o nome exato do campo varia conforme a indexacao.
 */
    private List<LinkedChatRef> loadCaseChats() {
        List<LinkedChatRef> chats = new ArrayList<>();
        if (attachSearcher == null) {
            return chats;
        }
        String[] queries = { "contentType:\"application/x-whatsapp-chat\"", //$NON-NLS-1$
                "contentType:whatsapp AND contentType:chat", //$NON-NLS-1$
                "mimetype:\"application/x-whatsapp-chat\"" }; //$NON-NLS-1$
        for (String query : queries) {
            try {
                List<IItem> items = attachSearcher.getItems(query);
                if (items == null) {
                    continue;
                }
                for (IItem item : items) {
                    addCaseChat(chats, item);
                }
            } catch (Exception e) {
                LOGGER.debug("Chat listing query failed: {}", query, e); //$NON-NLS-1$
            }
        }
        chats.sort(Comparator.comparing(chat -> chat.title, String.CASE_INSENSITIVE_ORDER));
        LOGGER.info("Chats do caso encontrados: {}", chats.size()); //$NON-NLS-1$
        return chats;
    }

    private void addCaseChat(List<LinkedChatRef> chats, IItem item) {
        if (item == null) {
            return;
        }
        String title = firstMetadataValue(item.getMetadata(), "dc:title", "title", "common:dc:title"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if (title.isEmpty()) {
            title = inferChatId(item.getName());
        }
        title = title.trim();
        if (title.isEmpty()) {
            return;
        }
        for (LinkedChatRef known : chats) {
            if (known.title.equalsIgnoreCase(title)) {
                if (known.item == null) {
                    known.item = item;
                }
                return;
            }
        }
        LinkedChatRef chat = new LinkedChatRef(ChatAnnotationStore.chatRefIdOf(title, item.getName()), title);
        chat.origin = ChatAnnotationStore.ORIGIN_CASE;
        chat.sourceName = safe(item.getName());
        chat.chatKey = ChatAnnotationStore.chatKeyOf(item.getHash(), item.getName(), item.getPath());
        chat.participants = readParticipants(item);
        chat.item = item;
        chats.add(chat);
    }

    /** Nomes de quem participa do chat, lidos de Communication:Participants. */
    private String readParticipants(IItem item) {
        try {
            Metadata metadata = item.getMetadata();
            if (metadata == null) {
                return ""; //$NON-NLS-1$
            }
            String[] values = metadata.getValues("Communication:Participants"); //$NON-NLS-1$
            if (values == null || values.length == 0) {
                return ""; //$NON-NLS-1$
            }
            StringBuilder out = new StringBuilder();
            for (String value : values) {
                String name = value == null ? "" : value.trim(); //$NON-NLS-1$
                if (name.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", "); //$NON-NLS-1$
                }
                out.append(name);
                if (out.length() > 120) {
                    break;
                }
            }
            return out.toString();
        } catch (Exception e) {
            return ""; //$NON-NLS-1$
        }
    }

    /**
     * Item do chat vinculado. Se o chat veio da lista do caso, o item ja esta em maos
     * e nao ha consulta. Senao tenta pelo titulo.
     */
    private IItem findLinkedChatItem(LinkedChatRef chat) {
        if (chat == null || attachSearcher == null) {
            return null;
        }
        if (chat.hasItem()) {
            return chat.item;
        }
        if (chat.title.isEmpty()) {
            return null;
        }
        String[] queries = { "contentType:\"application/x-whatsapp-chat\" AND title:\"" //$NON-NLS-1$ //$NON-NLS-2$
                + attachSearcher.escapeQuery(chat.title) + "\"", //$NON-NLS-1$
                "contentType:whatsapp AND \"" + attachSearcher.escapeQuery(chat.title) + "\"", //$NON-NLS-1$
                "\"" + attachSearcher.escapeQuery(chat.title) + "\"" }; //$NON-NLS-1$
        for (String query : queries) {
            try {
                List<IItem> items = attachSearcher.getItems(query);
                if (items != null && !items.isEmpty()) {
                    return items.get(0);
                }
            } catch (Exception e) {
                LOGGER.debug("Unable to resolve linked chat with query {}", query, e); //$NON-NLS-1$
            }
        }
        return null;
    }

    private void openNoteDialogAsync(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        runOffEdtIfNeeded(() -> {
            String chatKey = currentChatKey();
            ChatAnnotation existing = annotationStore.get(chatKey, messageId);
            String initialNote = existing == null ? "" : existing.noteText; //$NON-NLS-1$
            String initialAuthor = existing == null || existing.noteAuthor.isEmpty() ? defaultAnnotationAuthor()
                    : existing.noteAuthor;
            String preview = getMessagePreviewText(messageId);
            SwingUtilities.invokeLater(() -> {
                ChatNoteDialog dialog = new ChatNoteDialog(SwingUtilities.getWindowAncestor(chatCapturePanel), initialNote,
                        initialAuthor, preview);
                dialog.setVisible(true);
                if (!dialog.isConfirmed()) {
                    return;
                }
                String text = dialog.getNoteText();
                if (text.isEmpty()) {
                    annotationStore.removeNote(chatKey, messageId);
                    chatCapturePanel.setStatus("ChatCapturePanel.NoteRemoved"); //$NON-NLS-1$
                } else {
                    annotationStore.setNote(chatKey, messageId, text, dialog.getNoteAuthor());
                    chatCapturePanel.setStatus("ChatCapturePanel.NotesMarked"); //$NON-NLS-1$
                }
                refreshAnnotationsUi();
            });
        });
    }

    private void openLinkDialogAsync(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String chatKey = currentChatKey();
        ChatAnnotation existing = annotationStore.get(chatKey, messageId);
        List<LinkedChatRef> alreadyLinked = existing == null ? new ArrayList<>() : existing.links;
        String chatTitle = currentChatTitle();
        SwingUtilities.invokeLater(() -> {
            // Os chats do caso sao carregados sob demanda, dentro do dialogo, para nao
            // consultar o indice Lucene no caminho comum.
            LinkedChatPickerDialog dialog = new LinkedChatPickerDialog(
                    SwingUtilities.getWindowAncestor(chatCapturePanel), alreadyLinked, chatTitle,
                    this::loadCaseChats);
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) {
                return;
            }
            List<LinkedChatRef> selected = dialog.getSelectedChats();
            List<String> removeKeys = new ArrayList<>();
            for (LinkedChatRef link : alreadyLinked) {
                if (!containsKey(selected, link.key())) {
                    removeKeys.add(link.key());
                }
            }
            if (!removeKeys.isEmpty()) {
                annotationStore.removeLinks(chatKey, messageId, removeKeys);
            }
            annotationStore.addLinks(chatKey, messageId, selected);
            chatCapturePanel.setStatus("ChatCapturePanel.LinksMarked"); //$NON-NLS-1$
            LinkedChatRef openTarget = dialog.getOpenChatTarget();
            refreshAnnotationsUi();
            if (openTarget != null) {
                openLinkedChatCaptureWindow(chatKey, messageId, openTarget);
            }
        });
    }

    private static boolean containsKey(List<LinkedChatRef> chats, String key) {
        for (LinkedChatRef chat : chats) {
            if (chat.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private String defaultAnnotationAuthor() {
        String user = System.getProperty("user.name", ""); //$NON-NLS-1$ //$NON-NLS-2$
        return user == null ? "" : user.trim(); //$NON-NLS-1$
    }

    private String getMessagePreviewText(String messageId) {
        Object result = executeScriptAndWait("(function(id){var el=document.getElementById(id);if(!el)return '';"
                //$NON-NLS-1$
                + "var c=el.cloneNode(true);Array.prototype.forEach.call(c.querySelectorAll('script,style,input,button,audio,video,img'),function(n){n.parentNode&&n.parentNode.removeChild(n);});"
                //$NON-NLS-1$
                + "return (c.innerText||c.textContent||'').replace(/\\s+/g,' ').trim();})(" + js(messageId) + ");"); //$NON-NLS-1$ //$NON-NLS-2$
        return result == null ? "" : safe(result.toString()); //$NON-NLS-1$
    }

    private void refreshAnnotationsUi() {
        runOffEdtIfNeeded(this::refreshAnnotationsUiNow);
    }

    private void refreshAnnotationsUiNow() {
        List<ChatAnnotation> items = annotationStore.itemsFor(currentChatKey());
        int notes = 0;
        int links = 0;
        int outOfRange = 0;
        Map<String, Integer> indexById = (captureStartMessageId != null && captureEndMessageId != null && !items.isEmpty())
                ? getOrderedMessageIndexMap()
                : Collections.emptyMap();
        int startIndex = captureStartMessageId == null ? -1 : indexById.getOrDefault(captureStartMessageId, -1);
        int endIndex = captureEndMessageId == null ? -1 : indexById.getOrDefault(captureEndMessageId, -1);
        StringBuilder badges = new StringBuilder();
        for (ChatAnnotation item : items) {
            if (item.hasNote()) {
                notes++;
            }
            if (item.hasLinks()) {
                links++;
            }
            int msgIndex = item.messageId == null ? -1 : indexById.getOrDefault(item.messageId, -1);
            item.range = classifyAnnotationRange(msgIndex, startIndex, endIndex);
            if (item.range == ChatAnnotation.RANGE_OUT) {
                outOfRange++;
            }
            if (!item.isEmpty()) {
                badges.append(encodeRow(item.messageId, item.hasNote(), item.hasLinks())).append(';');
            }
        }
        chatCapturePanel.updateAnnotationCounters(notes, links, outOfRange);
        refreshAnnotationBadges(badges.toString());
    }

    private Map<String, Integer> getOrderedMessageIndexMap() {
        Map<String, Integer> indexById = new HashMap<>();
        try {
            Object raw = executeScriptAndWait(
                    "(function(){if(!window.ipedChatCapture)return '[]';var m=window.ipedChatCapture.getOrderedMessages(),out=[];for(var i=0;i<m.length;i++){out.push(m[i]&&m[i].id?m[i].id:'');}return JSON.stringify(out);})()"); //$NON-NLS-1$
            if (raw instanceof String) {
                JSONArray ids = new JSONArray((String) raw);
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, ""); //$NON-NLS-1$
                    if (!id.isEmpty() && !indexById.containsKey(id)) {
                        indexById.put(id, i);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to build ordered message index map", e); //$NON-NLS-1$
        }
        return indexById;
    }

    private int classifyAnnotationRange(String messageId, int startIndex, int endIndex) {
        if (captureStartMessageId == null || captureEndMessageId == null) {
            return ChatAnnotation.RANGE_IN;
        }
        return classifyAnnotationRange(getMessageIndex(messageId), startIndex, endIndex);
    }

    private int classifyAnnotationRange(int index, int startIndex, int endIndex) {
        if (captureStartMessageId == null || captureEndMessageId == null) {
            return ChatAnnotation.RANGE_IN;
        }
        if (index < 0) {
            return ChatAnnotation.RANGE_ORPHAN;
        }
        if (startIndex >= 0 && endIndex >= 0 && index >= startIndex && index <= endIndex) {
            return ChatAnnotation.RANGE_IN;
        }
        return ChatAnnotation.RANGE_OUT;
    }

    private static String encodeRow(String messageId, boolean note, boolean link) {
        return urlEncode(messageId) + "|" + (note ? "1" : "0") + "|" + (link ? "1" : "0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            return value;
        }
    }

    private void refreshAnnotationBadges() {
        StringBuilder badges = new StringBuilder();
        for (ChatAnnotation item : annotationStore.itemsFor(currentChatKey())) {
            if (!item.isEmpty()) {
                badges.append(encodeRow(item.messageId, item.hasNote(), item.hasLinks())).append(';');
            }
        }
        refreshAnnotationBadges(badges.toString());
    }

    private void clearAnnotationBadges() {
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.clearAnnotationBadges();}"); //$NON-NLS-1$
    }

    private void refreshAnnotationBadges(String rows) {
        // Durante a captura o icone nao pode aparecer em nenhum frame.
        if (!annotationStore.isBound() || linkedCaptureViewer || captureRunning) {
            clearAnnotationBadges();
            return;
        }
        String payload = rows.endsWith(";") ? rows.substring(0, rows.length() - 1) : rows; //$NON-NLS-1$
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.setAnnotationBadges(" + js(payload) + ");}"); //$NON-NLS-1$
    }

    // ------------------------------------------------------- captura encadeada

    /**
 * Abre uma janela nova com o chat vinculado e o mesmo painel de captura usado
 * normalmente: pasta de destino, nome custom, pasta de imagens para blur,
 * similaridade, nivel de blur, Start/Stop e o menu de marcar inicio, final e limpar.
 * A captura e feita nessa janela, sem trocar o chat que esta aberto no viewer.
 */
    private void openLinkedChatCaptureWindow(String originChatKey, String originMessageId, LinkedChatRef chat) {
        if (chat == null || chat.title.isEmpty()) {
            return;
        }
        final LinkedChatRef target = chat.copy();
        new Thread(() -> {
            IItem item = findLinkedChatItem(target);
            final String title = target.title;
            SwingUtilities.invokeLater(() -> {
                if (item == null) {
                    JOptionPane.showMessageDialog(chatCapturePanel,
                            ChatCapturePanel.plainMessageComponent(Messages.getString("ChatCaptureLink.OpenFailed")),
                            Messages.getString("ChatCaptureLink.Title"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                try {
                    File destination = resolveLinkedDestination();
                    if (destination == null) {
                        JOptionPane.showMessageDialog(chatCapturePanel,
                                ChatCapturePanel.plainMessageComponent(
                                        Messages.getString("ChatCaptureLink.SetDestinationFirst")), //$NON-NLS-1$
                                Messages.getString("ChatCaptureLink.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                        return;
                    }
                    String linkedName = target.chatRefId == null || target.chatRefId.trim().isEmpty()
                            ? linkedFolderName(originMessageId, title)
                            : safeName(target.chatRefId);
                    if (linkedName.isEmpty()) {
                        linkedName = linkedFolderName(originMessageId, title);
                    }
                    File folder = new File(destination, linkedName);
                    ChatCaptureLinkedWindow window = new ChatCaptureLinkedWindow(
                            SwingUtilities.getWindowAncestor(chatCapturePanel), attachSearcher, item, title, destination,
                            folder.getName());
                    // O vinculo pendente fica no viewer da janela nova, porque e a
                    // captura de la que precisa receber o linkedFrom.
                    window.getLinkedViewer().setPendingLink(
                            new AnnotationPendingLink(originChatKey, originMessageId, target.chatRefId, title));
                } catch (Throwable e) {
                    LOGGER.warn("Unable to open linked chat capture window", e); //$NON-NLS-1$
                    JOptionPane.showMessageDialog(chatCapturePanel,
                            ChatCapturePanel.plainMessageComponent(Messages.getString("ChatCaptureLink.OpenFailed")),
                            Messages.getString("ChatCaptureLink.Title"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                }
            });
        }, "IPED-chat-linked-open").start(); //$NON-NLS-1$
    }

    /** Pasta fixa das capturas vinculadas: sempre "vinculado" dentro da raiz de origem. */
    private static final String LINKED_FOLDER = "vinculado"; //$NON-NLS-1$

    /** Pasta raiz do conteudo capturado de origem, a partir do campo do painel. */
    private File resolveLinkedDestination() {
        String base = chatCapturePanel.getOutputFolderText();
        if (base.isEmpty()) {
            return null;
        }
        return new File(base, LINKED_FOLDER);
    }

    /** Nome da subpasta: identifica o chat e a mensagem de origem. */
    private String linkedFolderName(String originMessageId, String chatTitle) {
        String title = safeName(chatTitle);
        if (title.length() > 40) {
            title = safeName(title.substring(0, 40));
        }
        String suffix = originMessageId == null || originMessageId.isBlank() ? "" : "_msg" + originMessageId; //$NON-NLS-1$
        String result = safeName(title + suffix);
        return result.isEmpty() ? "vinculado" : result; //$NON-NLS-1$
    }

    private String pendingLinkLabel() {
        AnnotationPendingLink link = pendingLink;
        if (link == null) {
            return ""; //$NON-NLS-1$
        }
        return link.chatTitle + " (msg " + link.originMessageId + ")"; //$NON-NLS-1$
    }

    /** Usado pela janela de captura do chat vinculado. */
    void setPendingLink(AnnotationPendingLink link) {
        this.pendingLink = link;
    }

    /**
     * Marca este viewer como o da janela de captura de chat vinculado: some com o
     * menu de nota/vinculo e com o painel de anotacoes, porque a anotacao pertence
     * a mensagem de origem e ja foi registrada em {@code linkedFrom}.
     */
    void setLinkedCaptureViewer() {
        this.linkedCaptureViewer = true;
        chatCapturePanel.hideAnnotationsUi();
        clearAnnotationBadges();
    }

    // ------------------------------------------------------------- pre-flight

    private AnnotationPreflight buildAnnotationPreflight() {
        AnnotationPreflight preflight = new AnnotationPreflight();
        List<ChatAnnotation> items = annotationStore.itemsFor(currentChatKey());
        if (items.isEmpty()) {
            return preflight;
        }
        Map<String, Integer> indexById = getOrderedMessageIndexMap();
        int startIndex = captureStartMessageId == null ? -1 : indexById.getOrDefault(captureStartMessageId, -1);
        int endIndex = captureEndMessageId == null ? -1 : indexById.getOrDefault(captureEndMessageId, -1);
        for (ChatAnnotation item : items) {
            if (item.isEmpty()) {
                continue;
            }
            int msgIndex = item.messageId == null ? -1 : indexById.getOrDefault(item.messageId, -1);
            item.range = classifyAnnotationRange(msgIndex, startIndex, endIndex);
            preflight.add(item, msgIndex);
        }
        return preflight;
    }

    /** true quando o chat atual tem ao menos uma anotacao do analista. */
    private boolean hasAnnotations() {
        for (ChatAnnotation item : annotationStore.itemsFor(currentChatKey())) {
            if (!item.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
 * Quantos chats vinculados desta captura ainda nao tem pasta de captura gravada.
 *
 * A resolucao acontece na exportacao, entao a captura do chat vinculado precisa
 * existir antes da captura de origem. Servir para avisar o analista.
 */
    private int countUnresolvedLinkedChats() {
        List<ChatAnnotation> items = annotationStore.itemsFor(currentChatKey());
        if (items.isEmpty()) {
            return 0;
        }
        List<CaptureRootEntry> known = listCaptureEntries();
        int pending = 0;
        for (ChatAnnotation item : items) {
            for (LinkedChatRef link : item.links) {
                if (matchLinkedEntry(known, link) == null) {
                    pending++;
                }
            }
        }
        return pending;
    }

    /** Aviso no pre-flight quando o chat vinculado ainda nao tem captura gravada. */
    private String unresolvedLinksLabel() {
        int pending = countUnresolvedLinkedChats();
        if (pending <= 0) {
            return ""; //$NON-NLS-1$
        }
        return " | " + Messages.getString("ChatCapturePreflight.LinkedWithoutCapture") + " " + pending; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
 * Chamado pelo JavaScript quando o analista clica no icone [nota]/[link] do chat.
 * O JavaScript roda na thread do JavaFX, entao o dialogo Swing e aberto na EDT
 * para nao congelar o visualizador.
 */
    public void annotationAction(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String id = messageId;
        runOffEdtIfNeeded(() -> {
            String preview = getMessagePreviewText(id);
            SwingUtilities.invokeLater(() -> showAnnotationActions(id, preview));
        });
    }

    private void showAnnotationActions(String messageId, String preview) {
        if (captureRunning || linkedCaptureViewer) {
            return;
        }
        String chatKey = currentChatKey();
        ChatAnnotation annotation = annotationStore.get(chatKey, messageId);
        if (annotation == null || annotation.isEmpty()) {
            return;
        }
        ChatAnnotationActionDialog dialog = new ChatAnnotationActionDialog(
                SwingUtilities.getWindowAncestor(chatCapturePanel), annotation, preview);
        dialog.setVisible(true);
        boolean changed = false;
        if (dialog.isNoteRemoved()) {
            annotationStore.removeNote(chatKey, messageId);
            changed = true;
        }
        if (dialog.isLinksRemoved()) {
            annotationStore.removeAllLinks(chatKey, messageId);
            changed = true;
        }
        if (changed) {
            refreshAnnotationsUi();
        }
    }

    private String captureRangeLabel() {
        if (captureStartMessageId == null || captureEndMessageId == null) {
            return ""; //$NON-NLS-1$
        }
        return Messages.getString("ChatCapturePreflight.InRange") //$NON-NLS-1$
                + " (" + captureStartMessageId + " -> " + captureEndMessageId + ")"; //$NON-NLS-1$ //$NON-NLS-2$
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

    public void annotateSavedChat() {
        java.io.File outputRoot = null;
        String current = chatCapturePanel.getOutputFolderText();
        if (current != null && !current.isBlank()) {
            java.io.File candidate = new java.io.File(current);
            if (candidate.isDirectory()) {
                outputRoot = candidate;
            }
        }
        if (outputRoot == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Selecione a pasta de saida com chats salvos");
            if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(chatCapturePanel)) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            outputRoot = chooser.getSelectedFile();
        }
        if (outputRoot == null || !outputRoot.isDirectory()) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(chatCapturePanel),
                    ChatCapturePanel.plainMessageComponent("Pasta de saida invalida."), "Anotar chat salvo",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        SavedChatPickerDialog dialog = new SavedChatPickerDialog(SwingUtilities.getWindowAncestor(chatCapturePanel), outputRoot);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        String folderName = dialog.getSelectedFolder();
        if (folderName == null || folderName.isBlank()) {
            return;
        }
        String title = "";
        String sourceName = "";
        String sourceHash = "";
        String sourcePath = "";
        try {
            Path rootPath = outputRoot.toPath().toAbsolutePath().normalize();
            Path savedFolderPath = CaptureIntegrity.resolveContained(rootPath, folderName);
            Path manifestPath = CaptureIntegrity.resolveContained(savedFolderPath, "manifest.json"); //$NON-NLS-1$
            if (CaptureIntegrity.isSafeDirectory(savedFolderPath) && CaptureIntegrity.isSafeRegularFile(manifestPath)) {
                String content = CaptureIntegrity.readJsonUtf8Limited(manifestPath);
                JSONObject root = new JSONObject(content);
                title = jsonString(root, "title"); //$NON-NLS-1$
                if (title.isEmpty()) {
                    title = jsonString(root, "chatTitle"); //$NON-NLS-1$
                }
                sourceName = jsonString(root, "sourceName"); //$NON-NLS-1$
                sourceHash = jsonString(root, "sourceHash"); //$NON-NLS-1$
                sourcePath = jsonString(root, "sourcePath"); //$NON-NLS-1$
                JSONObject sourceMetadata = root.optJSONObject("sourceMetadata"); //$NON-NLS-1$
                if (sourceMetadata != null) {
                    if (sourceName.isEmpty()) {
                        sourceName = jsonString(sourceMetadata, "nome"); //$NON-NLS-1$
                    }
                    if (sourceHash.isEmpty()) {
                        sourceHash = jsonString(sourceMetadata, "hash"); //$NON-NLS-1$
                    }
                    if (sourcePath.isEmpty()) {
                        sourcePath = jsonString(sourceMetadata, "caminho"); //$NON-NLS-1$
                    }
                    if (title.isEmpty()) {
                        title = jsonString(sourceMetadata, "nome"); //$NON-NLS-1$
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to read manifest of saved chat {}", folderName, e);
        }

        java.util.List<String> queries = new java.util.ArrayList<>();
        if (sourceName != null && !sourceName.isBlank()) {
            queries.add("name:\"" + attachSearcher.escapeQuery(sourceName) + "\"");
        }
        if (sourcePath != null && !sourcePath.isBlank()) {
            queries.add("path:\"" + attachSearcher.escapeQuery(sourcePath) + "\"");
        }
        if (sourceHash != null && !sourceHash.isBlank()) {
            queries.add("hash:\"" + attachSearcher.escapeQuery(sourceHash) + "\"");
        }
        if (title != null && !title.isBlank()) {
            queries.add("\"" + attachSearcher.escapeQuery(title) + "\"");
        }
        if (queries.isEmpty() && folderName != null && !folderName.isBlank()) {
            queries.add("\"" + attachSearcher.escapeQuery(folderName) + "\"");
        }

        iped.data.IItem item = null;
        for (String query : queries) {
            try {
                item = attachSearcher.getItem(query);
                if (item != null) {
                    break;
                }
            } catch (Exception e) {
                LOGGER.debug("Unable to resolve saved chat with query {}", query, e);
            }
        }
        if (item == null) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(chatCapturePanel),
                    ChatCapturePanel.plainMessageComponent("Nao foi possivel localizar o chat de origem no caso."),
                    "Anotar chat salvo", JOptionPane.ERROR_MESSAGE);
            return;
        }

        annotationOnlyMode = true;
        linkedCaptureViewer = false;
        captureRunning = false;
        captureSourceItem = item;
        currentCaptureOutputRoot = outputRoot.toPath();
        chatCapturePanel.setOutputFolder(outputRoot);
        annotationStore.bindOutputRoot(outputRoot.toPath());
        chatCapturePanel.setStatus("Chat salvo carregado para anotacao");

        final iped.data.IItem savedItem = item;
        new Thread(() -> {
            try {
                Thread.sleep(300);
                loadFile(savedItem, null);
            } catch (Exception e) {
                LOGGER.warn("Unable to load saved chat in annotation mode", e);
            }
        }, "IPED-chat-saved-annotate").start();
    }

    public void saveAnnotations() {
        if (!annotationStore.isBound()) {
            chatCapturePanel.setStatus("Nenhuma pasta de anotacoes vinculada");
            return;
        }
        if (annotationStore.persist()) {
            chatCapturePanel.setStatus("Trechos e notas salvos. Use Re-Extrair para atualizar o whatsapp.html.");
        } else {
            chatCapturePanel.setStatusText("Falha ao gravar anotacoes na pasta selecionada."); //$NON-NLS-1$
        }
    }

    private volatile boolean annotationOnlyMode;

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

    void reportMissingOutputFolder() {
        chatCapturePanel.setStatus("ChatCapturePanel.MissingFolder"); //$NON-NLS-1$
    }

    static String cleanDirectoryInputPath(File folder) {
        return folder == null ? "" : ChatCapturePanel.stripSurroundingQuotes(folder.getPath()); //$NON-NLS-1$
    }

    static Path toValidatedDirectoryPath(File folder, String fieldLabel, boolean mustExist) throws IOException {
        String cleaned = cleanDirectoryInputPath(folder);
        if (cleaned.isEmpty()) {
            return null;
        }
        Path parsed;
        try {
            parsed = Paths.get(cleaned);
        } catch (InvalidPathException e) {
            throw new IOException("Caminho inválido para " + fieldLabel + ": " + cleaned + " (" + e.getReason() + ")", e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
        Path normalized = parsed.toAbsolutePath().normalize();
        if (mustExist) {
            return OriginCustodyBinder.validateSafeDirectory(normalized, fieldLabel);
        }
        Path root = normalized.getRoot();
        if (root != null && !normalized.equals(root)) {
            Path current = root;
            for (Path part : root.relativize(normalized)) {
                current = current.resolve(part);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && CaptureIntegrity.isLinkOrReparse(current)) {
                    throw new IOException("Links simbólicos ou junctions não são permitidos em " + fieldLabel + ": " + current); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }
        return normalized;
    }

    private void showCaptureUiError(java.awt.Component parent, String title, String message) {
        String text = (message == null || message.trim().isEmpty()) ? title : message.trim();
        chatCapturePanel.setStatusText(text);
        Runnable showDialog = () -> JOptionPane.showMessageDialog(
                parent != null ? parent : chatCapturePanel,
                ChatCapturePanel.plainMessageComponent(text),
                title, JOptionPane.ERROR_MESSAGE);
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog.run();
        } else {
            SwingUtilities.invokeLater(showDialog);
        }
    }

    public void setOriginAuditSourcesValidated(File originDir, File parentIpedDir) throws IOException {
        Path originPath = toValidatedDirectoryPath(originDir, "Origem Avilla", true); //$NON-NLS-1$
        Path parentPath = toValidatedDirectoryPath(parentIpedDir, "Caso IPED pai", true); //$NON-NLS-1$
        this.originAuditFolder = originPath;
        this.originParentIpedFolder = parentPath;
    }

    public void setOriginAuditSources(File originDir, File parentIpedDir) {
        try {
            setOriginAuditSourcesValidated(originDir, parentIpedDir);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public void setOriginAuditSources(Path originDir, Path parentIpedDir) {
        this.originAuditFolder = originDir;
        this.originParentIpedFolder = parentIpedDir;
    }

    void startChatCaptureFromUi(java.awt.Component alertParent, File outputFolder, String customFolderName,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent,
            File originDir, File parentIpedDir) {
        try {
            toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
            toValidatedDirectoryPath(sensitiveImagesFolder, "Pasta imagens blur", true); //$NON-NLS-1$
            setOriginAuditSourcesValidated(originDir, parentIpedDir);
        } catch (IOException e) {
            showCaptureUiError(alertParent, "Captura de Chat", e.getMessage()); //$NON-NLS-1$
            return;
        }
        startChatCaptureFromUi(alertParent, outputFolder, customFolderName, sensitiveImagesFolder,
                sensitiveSimilarityPercent, sensitiveBlurPercent);
    }

    void startChatCaptureFromUi(java.awt.Component alertParent, File outputFolder, String customFolderName,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
        if (captureRunning) {
            return;
        }
        if (outputFolder == null || outputFolder.getPath().trim().isEmpty()) {
            reportMissingOutputFolder();
            return;
        }
        if (!captureStarting.compareAndSet(false, true)) {
            return;
        }
        Thread starter = new Thread(() -> {
            try {
                startChatCaptureInternal(alertParent, outputFolder, customFolderName, sensitiveImagesFolder,
                        sensitiveSimilarityPercent, sensitiveBlurPercent);
            } finally {
                captureStarting.set(false);
            }
        }, "IPED-chat-capture-start"); //$NON-NLS-1$
        starter.setDaemon(true);
        starter.start();
    }

    public void startChatCapture(File outputFolder, String customFolderName, File sensitiveImagesFolder,
            int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
        if (SwingUtilities.isEventDispatchThread()) {
            startChatCaptureFromUi(null, outputFolder, customFolderName, sensitiveImagesFolder,
                    sensitiveSimilarityPercent, sensitiveBlurPercent);
            return;
        }
        if (captureRunning) {
            return;
        }
        if (!captureStarting.compareAndSet(false, true)) {
            return;
        }
        try {
            startChatCaptureInternal(null, outputFolder, customFolderName, sensitiveImagesFolder,
                    sensitiveSimilarityPercent, sensitiveBlurPercent);
        } finally {
            captureStarting.set(false);
        }
    }

    private void startChatCaptureInternal(java.awt.Component alertParent, File outputFolder, String customFolderName,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
        if (captureRunning) {
            return;
        }
        try {
            toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
            toValidatedDirectoryPath(sensitiveImagesFolder, "Pasta imagens blur", true); //$NON-NLS-1$
        } catch (IOException e) {
            showCaptureUiError(alertParent, "Captura de Chat", e.getMessage()); //$NON-NLS-1$
            return;
        }
        if (!validateCaptureStart(outputFolder)) return;
        final Path outputPath;
        try {
            outputPath = toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
        } catch (IOException e) {
            showCaptureUiError(alertParent, "Captura de Chat", e.getMessage()); //$NON-NLS-1$
            return;
        }
        VerifierIntegrity.installVerifierFiles(outputPath);
        if (alertParent != null) {
            try {
                SwingUtilities.invokeAndWait(() -> VerifierIntegrity.showAlertIfNeeded(alertParent, outputPath));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.warn("Unable to show verifier integrity alert", e); //$NON-NLS-1$
            }
            if (!linkedCaptureViewer) {
                CustodyCaseRecord confirmedRecord = confirmCustodyBeforeAction(alertParent, outputPath, "Start"); //$NON-NLS-1$
                if (confirmedRecord == null) {
                    chatCapturePanel.setStatus("ChatCapturePanel.StartCancelled"); //$NON-NLS-1$
                    return;
                }
            }
        }
        annotationStore.bindOutputRoot(outputFolder.toPath());
        // Recurso opcional: so entra no output se o toggle estiver ligado e houver
        // anotacao. A captura de chat vinculado nunca exporta anotacoes.
        if (linkedCaptureViewer || !chatCapturePanel.isAnnotationsEnabled()) {
            pendingAnnotations = null;
            startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent,
                    sensitiveBlurPercent);
            return;
        }
        AnnotationPreflight preflight = buildAnnotationPreflight();
        if (!preflight.hasContent()) {
            pendingAnnotations = null;
            startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent,
                    sensitiveBlurPercent);
            return;
        }
        if (!confirmAnnotationPreflight(preflight)) {
            chatCapturePanel.setStatus("ChatCapturePanel.StartCancelled"); //$NON-NLS-1$
            return;
        }
        if (!chatCapturePanel.isAnnotationsEnabled()) {
            pendingAnnotations = null;
        } else {
            pendingAnnotations = preflight.toAnnotationSet();
        }
        startCaptureBody(outputFolder, customFolderName, sensitiveImagesFolder, sensitiveSimilarityPercent,
                sensitiveBlurPercent);
    }

    private boolean confirmAnnotationPreflight(AnnotationPreflight preflight) {
        final boolean[] confirmed = new boolean[1];
        Runnable showPreflight = () -> {
            ChatPreflightDialog dialog = new ChatPreflightDialog(SwingUtilities.getWindowAncestor(chatCapturePanel),
                    preflight, captureRangeLabel() + unresolvedLinksLabel(), pendingLinkLabel());
            dialog.setVisible(true);
            confirmed[0] = dialog.isConfirmed();
            if (dialog.isConfirmed() && !dialog.isIncludeEnabled()) {
                chatCapturePanel.setAnnotationsEnabled(false);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            showPreflight.run();
            return confirmed[0];
        }
        try {
            SwingUtilities.invokeAndWait(showPreflight);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.warn("Unable to show annotations pre-flight", e); //$NON-NLS-1$
            return false;
        }
        return confirmed[0];
    }

    private CustodyCaseRecord loadOrPrepareCustodyRecord(Path outputRoot) {
        CustodyCaseRecord record;
        try {
            record = CustodyCaseRecord.loadOrCreate(outputRoot);
        } catch (Exception e) {
            LOGGER.warn("Unable to load existing custody record at {}", outputRoot, e); //$NON-NLS-1$
            record = new CustodyCaseRecord();
        }
        record.pluginVersion = CHAT_CAPTURE_PLUGIN_VERSION;
        record.ipedVersion = "4.3.1"; //$NON-NLS-1$
        if (this.originParentIpedFolder != null && this.originParentIpedFolder.getFileName() != null) {
            record.parentIpedFolderName = this.originParentIpedFolder.getFileName().toString();
        }
        if (this.originAuditFolder != null) {
            try {
                OriginCustodyBinder.OriginReportPreview preview = OriginCustodyBinder.readDeviceAndCustodyPreview(this.originAuditFolder);
                record.mergeFromOriginPreview(preview, false);
            } catch (Exception e) {
                LOGGER.warn("Unable to read origin report.pdf preview from {}", this.originAuditFolder, e); //$NON-NLS-1$
            }
        }
        return record;
    }

    private CustodyCaseRecord confirmCustodyBeforeAction(java.awt.Component alertParent, Path outputRoot,
            String operationLabel) {
        final CustodyCaseRecord record = loadOrPrepareCustodyRecord(outputRoot);
        final boolean[] confirmed = new boolean[1];
        Runnable show = () -> {
            java.awt.Window owner = alertParent instanceof java.awt.Window
                    ? (java.awt.Window) alertParent
                    : SwingUtilities.getWindowAncestor(alertParent != null ? alertParent : chatCapturePanel);
            CustodyReportDialog dialog = new CustodyReportDialog(owner, record, this.originAuditFolder,
                    operationLabel, false);
            dialog.setVisible(true);
            confirmed[0] = dialog.isConfirmed();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                LOGGER.warn("Unable to show custody report dialog", e); //$NON-NLS-1$
                return null;
            }
        }
        if (!confirmed[0]) {
            return null;
        }
        record.addStep(operationLabel, "", "Cadastro/conferencia de custodia confirmada", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Coletado por=" + nonBlank(record.collectedBy, "-") //$NON-NLS-1$ //$NON-NLS-2$
                        + " | Analisado por=" + nonBlank(record.analyzedBy, "-") //$NON-NLS-1$ //$NON-NLS-2$
                        + " | Armazenamento=" + record.formattedStorageDescription()); //$NON-NLS-1$
        try {
            record.save(outputRoot);
        } catch (IOException e) {
            LOGGER.warn("Unable to save custody record before {}", operationLabel, e); //$NON-NLS-1$
        }
        this.activeCustodyRecord = record;
        return record;
    }

    void openCustodyReportDialogFromUi(java.awt.Component alertParent, File outputFolder,
            File originDir, File parentIpedDir) {
        if (captureRunning) {
            return;
        }
        final Path outputPath;
        try {
            outputPath = toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
            if (outputPath == null) {
                reportMissingOutputFolder();
                return;
            }
            setOriginAuditSourcesValidated(originDir, parentIpedDir);
        } catch (IOException e) {
            showCaptureUiError(alertParent, "Dados do Laudo", e.getMessage()); //$NON-NLS-1$
            return;
        }
        CustodyCaseRecord record = loadOrPrepareCustodyRecord(outputPath);
        java.awt.Window owner = alertParent instanceof java.awt.Window
                ? (java.awt.Window) alertParent
                : SwingUtilities.getWindowAncestor(alertParent != null ? alertParent : chatCapturePanel);
        CustodyReportDialog dialog = new CustodyReportDialog(owner, record, this.originAuditFolder, "Laudo", true); //$NON-NLS-1$
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        record.addStep("LAUDO", "", "Dados do laudo atualizados manualmente", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "Analisado por=" + nonBlank(record.analyzedBy, "-") //$NON-NLS-1$ //$NON-NLS-2$
                        + " | Armazenamento=" + record.formattedStorageDescription()); //$NON-NLS-1$
        this.activeCustodyRecord = record;
        Thread genThread = new Thread(() -> {
            try {
                CaptureAuditLogManager.reconcileKnownCaptures(outputPath, record, "LAUDO"); //$NON-NLS-1$
                safeRebuildCaptureRootIndex(outputPath);
                TechnicalReportGenerator.GenerationResult res = TechnicalReportGenerator.generateAll(
                        outputPath, record, this.originAuditFolder, this.lastKnownRootHash);
                safeRebuildCaptureRootIndex(outputPath);
                String msg = "Laudo tecnico atualizado com sucesso em:\n" //$NON-NLS-1$
                        + "- HTML: " + res.htmlReport + "\n" //$NON-NLS-1$ //$NON-NLS-2$
                        + (res.pdfGenerated ? "- PDF: " + res.pluginPdf + "\n" //$NON-NLS-1$ //$NON-NLS-2$
                                : "- PDF indisponivel: " + res.pdfError + "\n") //$NON-NLS-1$ //$NON-NLS-2$
                        + (res.originPdfCopied ? "- report.pdf original copiado (SHA-256 verificado): " + res.originPdfCopy + "\n" : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + (res.unifiedPdf != null ? "- PDF unificado: " + res.unifiedPdf + "\n" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        alertParent != null ? alertParent : chatCapturePanel,
                        ChatCapturePanel.plainMessageComponent(msg),
                        "Dados do Laudo", JOptionPane.INFORMATION_MESSAGE)); //$NON-NLS-1$
            } catch (Exception ex) {
                LOGGER.warn("Unable to generate technical report", ex); //$NON-NLS-1$
                showCaptureUiError(alertParent, "Dados do Laudo", ex.getMessage() == null ? ex.toString() : ex.getMessage()); //$NON-NLS-1$
            }
        }, "IPED-chat-laudo-gen"); //$NON-NLS-1$
        genThread.setDaemon(true);
        genThread.start();
    }

    private void finalizeLaudoAfterStart(Path outputRoot, Path captureDir) {
        if (linkedCaptureViewer || outputRoot == null || captureDir == null) {
            return;
        }
        try {
            CustodyCaseRecord record = this.activeCustodyRecord;
            if (record == null) {
                record = loadOrPrepareCustodyRecord(outputRoot);
            }
            CaptureAuditLogManager.recordStartCapture(outputRoot, record, captureDir);
            safeRebuildCaptureRootIndex(outputRoot);
            TechnicalReportGenerator.generateAll(outputRoot, record, this.originAuditFolder, this.lastKnownRootHash);
            this.activeCustodyRecord = record;
            safeRebuildCaptureRootIndex(outputRoot);
        } catch (Exception e) {
            LOGGER.warn("Unable to update technical report after capture at {}", outputRoot, e); //$NON-NLS-1$
        }
    }

    private void startCaptureBody(File outputFolder, String customFolderName, File sensitiveImagesFolder,
            int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
        final Path outputPath;
        final Path validatedSensitive;
        try {
            outputPath = toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
            validatedSensitive = toValidatedDirectoryPath(sensitiveImagesFolder, "Pasta imagens blur", true); //$NON-NLS-1$
        } catch (IOException e) {
            showCaptureUiError(chatCapturePanel, "Captura de Chat", e.getMessage()); //$NON-NLS-1$
            return;
        }
        this.sensitiveImagesFolder = validatedSensitive;
        this.sensitiveSimilarityPercent = normalizeSimilarityPercent(sensitiveSimilarityPercent);
        this.sensitiveBlurPercent = normalizeBlurPercent(sensitiveBlurPercent);
        captureRunning = true;
        stopCaptureRequested = false;
        chatCapturePanel.setCaptureRunning(true);
        chatCapturePanel.setStatus("ChatCapturePanel.Capturing"); //$NON-NLS-1$
        new Thread(() -> runChatCapture(outputPath, customFolderName), "IPED-chat-capture").start(); //$NON-NLS-1$
    }

    void startRecaptureFlowFromUi(java.awt.Component alertParent, File outputFolder,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent,
            File originDir, File parentIpedDir) {
        startRecaptureFlowInternal(alertParent, outputFolder, sensitiveImagesFolder,
                sensitiveSimilarityPercent, sensitiveBlurPercent, true, originDir, parentIpedDir);
    }

    void startRecaptureFlowFromUi(java.awt.Component alertParent, File outputFolder,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent) {
        startRecaptureFlowInternal(alertParent, outputFolder, sensitiveImagesFolder,
                sensitiveSimilarityPercent, sensitiveBlurPercent, false, null, null);
    }

    private void startRecaptureFlowInternal(java.awt.Component alertParent, File outputFolder,
            File sensitiveImagesFolder, int sensitiveSimilarityPercent, int sensitiveBlurPercent,
            boolean applyOriginSources, File originDir, File parentIpedDir) {
        if (captureRunning) {
            return;
        }
        if (outputFolder == null || outputFolder.getPath().trim().isEmpty()) {
            reportMissingOutputFolder();
            return;
        }
        final Path outputPath;
        final Path validatedSensitive;
        final Path validatedOrigin;
        final Path validatedParentIped;
        try {
            outputPath = toValidatedDirectoryPath(outputFolder, "Pasta destino", false); //$NON-NLS-1$
            if (outputPath == null) {
                reportMissingOutputFolder();
                return;
            }
            validatedSensitive = toValidatedDirectoryPath(sensitiveImagesFolder, "Pasta imagens blur", true); //$NON-NLS-1$
            if (applyOriginSources) {
                validatedOrigin = toValidatedDirectoryPath(originDir, "Origem Avilla", true); //$NON-NLS-1$
                validatedParentIped = toValidatedDirectoryPath(parentIpedDir, "Caso IPED pai", true); //$NON-NLS-1$
            } else {
                validatedOrigin = null;
                validatedParentIped = null;
            }
        } catch (IOException e) {
            showCaptureUiError(alertParent, "Re-Extrair", e.getMessage()); //$NON-NLS-1$
            return;
        }
        if (applyOriginSources) {
            setOriginAuditSources(validatedOrigin, validatedParentIped);
        }
        if (!recapturePreparing.compareAndSet(false, true)) {
            return;
        }
        Thread initializer = new Thread(() -> {
            boolean handedOff = false;
            try {
                if (captureRunning) {
                    return;
                }
                VerifierIntegrity.installVerifierFiles(outputPath);
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (alertParent != null) {
                            VerifierIntegrity.showAlertIfNeeded(alertParent, outputPath);
                            CustodyCaseRecord confirmedRecord = confirmCustodyBeforeAction(alertParent, outputPath, "Re-Extrair"); //$NON-NLS-1$
                            if (confirmedRecord == null) {
                                chatCapturePanel.setStatus("ChatCapturePanel.StartCancelled"); //$NON-NLS-1$
                                return;
                            }
                        }
                        showRecaptureDialog(outputPath.toFile(),
                                validatedSensitive == null ? null : validatedSensitive.toFile(),
                                sensitiveSimilarityPercent, sensitiveBlurPercent);
                    } finally {
                        recapturePreparing.set(false);
                    }
                });
                handedOff = true;
            } catch (Exception e) {
                LOGGER.warn("Unable to prepare recapture flow", e); //$NON-NLS-1$
                showCaptureUiError(alertParent, "Re-Extrair", e.getMessage()); //$NON-NLS-1$
            } finally {
                if (!handedOff) {
                    recapturePreparing.set(false);
                }
            }
        }, "IPED-chat-recapture-init"); //$NON-NLS-1$
        initializer.setDaemon(true);
        initializer.start();
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
        List<String> folders;
        try {
            folders = runRecaptureLoad("Re-Extrair — procurando capturas", progress -> readCaptureFolders(outputRoot, progress));
        } catch (Exception e) {
            LOGGER.warn("Unable to load recapture jobs from {}", outputRoot, e); //$NON-NLS-1$
            JOptionPane.showMessageDialog(chatCapturePanel,
                    ChatCapturePanel.plainMessageComponent(e.getMessage() == null ? e.toString() : e.getMessage()),
                    "Re-Extrair", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }
        if (folders.isEmpty()) {
            JOptionPane.showMessageDialog(chatCapturePanel,
                    ChatCapturePanel.plainMessageComponent("Nenhuma captura valida encontrada para re-extrair."), //$NON-NLS-1$
                    "Re-Extrair", JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
            return;
        }

        final int pageSize = 100;
        int page = 0;
        final boolean[] allFuture = {false};
        boolean accepted = false;
        Set<String> selectedIds = new HashSet<>();
        Set<String> knownValidIds = new HashSet<>();
        Set<String> knownInvalidIds = new HashSet<>();
        boolean annotationsEnabled = true;
        boolean migrateLegacyToHtml = false;
        boolean resetLegacyRecords = false;
        List<String> selectedFolders = new ArrayList<>();
        while (!accepted) {
            int start = page * pageSize;
            List<String> pageFolders = folders.subList(start, Math.min(folders.size(), start + pageSize));
            List<RecaptureJob> jobs;
            try { jobs = runRecaptureLoad("Re-Extrair — validando página", progress -> loadRecaptureJobs(outputRoot, pageFolders, true, progress)); }
            catch (Exception e) { JOptionPane.showMessageDialog(chatCapturePanel, ChatCapturePanel.plainMessageComponent(e.getMessage()), "Re-Extrair", JOptionPane.ERROR_MESSAGE); return; }
            JPanel list = new JPanel();
            list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
            List<JCheckBox> checks = new ArrayList<>();
            for (RecaptureJob job : jobs) {
                if (job.valid) {
                    knownValidIds.add(job.folder);
                    knownInvalidIds.remove(job.folder);
                } else {
                    knownInvalidIds.add(job.folder);
                    knownValidIds.remove(job.folder);
                    selectedIds.remove(job.folder);
                }
                String normalizedFolder = job.folder == null ? "" : job.folder.replace('\\', '/'); //$NON-NLS-1$
                int separator = normalizedFolder.lastIndexOf('/');
                String label = separator >= 0 ? normalizedFolder.substring(separator + 1) : normalizedFolder;
                JCheckBox check = ChatCapturePanel.plainCheckBox(label,
                        shouldSelectRecaptureCheckBox(job.valid, allFuture[0], selectedIds.contains(job.folder)));
                check.setEnabled(job.valid);
                check.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                checks.add(check); list.add(check);
                list.add(javax.swing.Box.createVerticalStrut(2));
                // The selection page needs only light descriptors. Drop full inventory JSON now.
                job.expectedArtifacts = null; job.captureFingerprint = null; job.artifactGenerators = null;
            }
            JLabel count = ChatCapturePanel.plainLabel("Exibindo " + (start + 1) + "–" + (start + jobs.size()) + " de " + folders.size() + " capturas");
            JButton bulk = new JButton(allFuture[0] ? "Desmarcar todos" : "Marcar todos");
            bulk.addActionListener(e -> { allFuture[0] = !allFuture[0]; selectedIds.clear(); for(int i=0;i<checks.size();i++) if(checks.get(i).isEnabled())checks.get(i).setSelected(allFuture[0]); bulk.setText(allFuture[0] ? "Desmarcar todos" : "Marcar todos"); });
            for (int i=0;i<checks.size();i++) { final String id=jobs.get(i).folder; checks.get(i).addActionListener(e -> { boolean checked=((JCheckBox)e.getSource()).isSelected();if(allFuture[0]){for(String existing:folders)if(!existing.equals(id)&&!knownInvalidIds.contains(existing))selectedIds.add(existing);allFuture[0]=false;bulk.setText("Marcar todos");}if(checked)selectedIds.add(id);else selectedIds.remove(id); }); }
            JButton prev = new JButton("Anterior"); prev.setEnabled(page > 0);
            JButton next = new JButton("Próximo"); next.setEnabled(start + pageSize < folders.size());
            JPanel header = new JPanel(new BorderLayout()); header.add(count,BorderLayout.CENTER); header.add(bulk,BorderLayout.EAST);
            JScrollPane captureScroll = new JScrollPane(list);
            captureScroll.setPreferredSize(recaptureScrollPreferredSize());
            JPanel panel = new JPanel(new BorderLayout(6,6)); panel.add(header,BorderLayout.NORTH); panel.add(captureScroll,BorderLayout.CENTER); panel.add(buildRecaptureTogglePanel(jobs, annotationsEnabled, migrateLegacyToHtml, resetLegacyRecords),BorderLayout.SOUTH);
            Object[] options = {prev,next,"Confirmar","Cancelar"};
            int option=JOptionPane.showOptionDialog(chatCapturePanel,panel,"Re-Extrair capturas — página "+(page+1),JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[2]);
            annotationsEnabled = recaptureAnnotationsToggle != null && recaptureAnnotationsToggle.isSelected();
            migrateLegacyToHtml = recaptureLegacyMigrationToggle != null && recaptureLegacyMigrationToggle.isSelected();
            resetLegacyRecords = recaptureLegacyResetToggle != null && recaptureLegacyResetToggle.isSelected();
            for(int i=0;i<checks.size();i++){String id=jobs.get(i).folder;if(checks.get(i).isEnabled()&&checks.get(i).isSelected())selectedIds.add(id);else selectedIds.remove(id);}
            if(option==0){page=Math.max(0,page-1);continue;}
            if(option==1){page=Math.min((folders.size()-1)/pageSize,page+1);continue;}
            if(option!=2)return;
            accepted=true;
        }
        List<String> candidateFolders = collectSelectedRecaptureFolders(folders, selectedIds, knownInvalidIds, allFuture[0]);
        List<String> unvalidated = new ArrayList<>();
        for (String folder : candidateFolders) {
            if (!knownValidIds.contains(folder)) unvalidated.add(folder);
        }
        if (unvalidated.isEmpty()) {
            selectedFolders.addAll(candidateFolders);
        } else {
            try {
                Set<String> validSet = runRecaptureLoad("Re-Extrair — filtrando válidas",
                        progress -> filterValidRecaptureFolders(outputRoot, unvalidated, pageSize, knownValidIds, progress));
                for (String folder : candidateFolders) {
                    if (knownValidIds.contains(folder) || validSet.contains(folder)) selectedFolders.add(folder);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(chatCapturePanel,
                        ChatCapturePanel.plainMessageComponent(e.getMessage() == null ? e.toString() : e.getMessage()),
                        "Re-Extrair", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                return;
            }
        }
        if (selectedFolders.isEmpty()) {
            JOptionPane.showMessageDialog(chatCapturePanel,
                    ChatCapturePanel.plainMessageComponent("Nenhuma captura selecionada."),
                    "Re-Extrair", JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }
        try {
            this.sensitiveImagesFolder = toValidatedDirectoryPath(sensitiveImagesFolder, "Pasta imagens blur", true); //$NON-NLS-1$
        } catch (IOException e) {
            showCaptureUiError(chatCapturePanel, "Re-Extrair", e.getMessage()); //$NON-NLS-1$
            return;
        }
        this.sensitiveSimilarityPercent = normalizeSimilarityPercent(sensitiveSimilarityPercent);
        this.sensitiveBlurPercent = normalizeBlurPercent(sensitiveBlurPercent);
        if (migrateLegacyToHtml && this.sensitiveImagesFolder == null) {
            int answer = JOptionPane.showConfirmDialog(chatCapturePanel,
                    ChatCapturePanel.plainMessageComponent(
                            Messages.getString("ChatCapturePanel.MigrateLegacyNoReferenceWarning")), //$NON-NLS-1$
                    "Re-Extrair", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$
            if (answer != JOptionPane.YES_OPTION) return;
        }
        startRecaptureBatch(outputRoot, selectedFolders, annotationsEnabled, migrateLegacyToHtml, resetLegacyRecords);
    }

    static boolean shouldSelectRecaptureCheckBox(boolean valid, boolean allFutureSelected, boolean explicitlySelected) {
        return valid && (allFutureSelected || explicitlySelected);
    }

    static List<String> collectSelectedRecaptureFolders(List<String> folders, Set<String> selectedIds,
            Set<String> knownInvalidIds, boolean allFutureSelected) {
        List<String> selected = new ArrayList<>();
        if (folders == null) {
            return selected;
        }
        for (String folder : folders) {
            if (knownInvalidIds != null && knownInvalidIds.contains(folder)) {
                continue;
            }
            if (allFutureSelected || (selectedIds != null && selectedIds.contains(folder))) {
                selected.add(folder);
            }
        }
        return selected;
    }

    private Set<String> filterValidRecaptureFolders(Path outputRoot, List<String> candidateFolders, int chunkSize,
            Set<String> knownInvalidIds, java.util.function.IntConsumer progress) throws IOException {
        Set<String> valid = new HashSet<>();
        int processed = 0;
        int step = Math.max(1, chunkSize);
        for (int start = 0; start < candidateFolders.size(); start += step) {
            List<String> slice = candidateFolders.subList(start, Math.min(candidateFolders.size(), start + step));
            List<RecaptureJob> jobs = loadRecaptureJobs(outputRoot, slice, true, null);
            for (RecaptureJob job : jobs) {
                if (job.valid) {
                    valid.add(job.folder);
                } else if (knownInvalidIds != null) {
                    knownInvalidIds.add(job.folder);
                }
                processed++;
                if (progress != null) progress.accept(processed);
            }
        }
        return valid;
    }

    /**
     * Sizes the capture list so the whole dialog fits the monitor's usable area.
     * The dialog chrome (header, toggles and option buttons) takes roughly
     * 215 px, so the scroll viewport grows proportionally but always leaves
     * room for those controls.
     */
    private java.awt.Dimension recaptureScrollPreferredSize() {
        java.awt.Dimension screen;
        java.awt.Insets insets;
        java.awt.GraphicsConfiguration gc = chatCapturePanel == null ? null : chatCapturePanel.getGraphicsConfiguration();
        if (gc != null) {
            java.awt.Rectangle bounds = gc.getBounds();
            screen = new java.awt.Dimension(bounds.width, bounds.height);
            insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        } else {
            screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            insets = new java.awt.Insets(0, 0, 0, 0);
        }
        int usableWidth = Math.max(320, screen.width - insets.left - insets.right);
        int usableHeight = Math.max(240, screen.height - insets.top - insets.bottom);
        int width = Math.min(1800, Math.max(520, Math.round(usableWidth * 0.62f)));
        int height = Math.min(1100, Math.max(280, Math.round(usableHeight * 0.60f)));
        width = Math.min(width, Math.max(320, usableWidth - 60));
        height = Math.min(height, Math.max(240, usableHeight - 220));
        return new java.awt.Dimension(width, height);
    }

    private JPanel buildRecaptureTogglePanel(List<RecaptureJob> jobs, boolean annotationsEnabled,
            boolean migrateLegacyToHtml, boolean resetLegacyRecords) {
        boolean allLegacy = !jobs.isEmpty();
        boolean hasSelectableLegacy = false;
        boolean hasSelectableNew = false;
        for (RecaptureJob job : jobs) {
            allLegacy = allLegacy && job.legacyAnnotations;
            hasSelectableLegacy |= job.valid && job.legacyCapture;
            hasSelectableNew |= job.valid && job.htmlMessageCapture;
        }
        recaptureAnnotationsToggle = ChatCapturePanel.plainCheckBox(Messages.getString("ChatCapturePanel.IncludeAnnotations"), annotationsEnabled); //$NON-NLS-1$
        recaptureAnnotationsToggle.setEnabled(!allLegacy);
        if (allLegacy) {
            recaptureAnnotationsToggle.setToolTipText(Messages.getString("ChatCapturePanel.LegacyAnnotationsLocked")); //$NON-NLS-1$
        }
        recaptureLegacyMigrationToggle = ChatCapturePanel.plainCheckBox(
                Messages.getString("ChatCapturePanel.MigrateLegacyToHtml"), migrateLegacyToHtml); //$NON-NLS-1$
        recaptureLegacyMigrationToggle.setToolTipText(Messages.getString("ChatCapturePanel.MigrateLegacyToHtmlTip")); //$NON-NLS-1$
        recaptureLegacyMigrationToggle.setEnabled(hasSelectableLegacy);
        final boolean legacyAvailable = hasSelectableLegacy;
        final boolean newAvailable = hasSelectableNew;
        boolean resetAvailable = newAvailable || (legacyAvailable && migrateLegacyToHtml);
        recaptureLegacyResetToggle = ChatCapturePanel.plainCheckBox(
                Messages.getString("ChatCapturePanel.ResetLegacyRecords"), resetAvailable && resetLegacyRecords); //$NON-NLS-1$
        recaptureLegacyResetToggle.setToolTipText(resetAvailable || legacyAvailable
                ? Messages.getString("ChatCapturePanel.ResetLegacyRecordsTip") //$NON-NLS-1$
                : Messages.getString("ChatCapturePanel.ResetLegacyRecordsUnavailable")); //$NON-NLS-1$
        recaptureLegacyResetToggle.setEnabled(resetAvailable);
        recaptureLegacyMigrationToggle.addActionListener(e -> {
            boolean enabled = newAvailable
                    || (legacyAvailable && recaptureLegacyMigrationToggle.isSelected());
            recaptureLegacyResetToggle.setEnabled(enabled);
            if (!enabled) recaptureLegacyResetToggle.setSelected(false);
        });
        JPanel togglePanel = new JPanel();
        togglePanel.setLayout(new BoxLayout(togglePanel, BoxLayout.Y_AXIS));
        togglePanel.add(recaptureAnnotationsToggle);
        togglePanel.add(recaptureLegacyMigrationToggle);
        togglePanel.add(recaptureLegacyResetToggle);
        if (!hasSelectableLegacy) {
            recaptureLegacyMigrationToggle.setToolTipText(Messages.getString("ChatCapturePanel.MigrateLegacyUnavailable")); //$NON-NLS-1$
        }
        return togglePanel;
    }

    private List<RecaptureJob> loadRecaptureJobs(Path outputRoot) throws IOException {
        return loadRecaptureJobs(outputRoot, readCaptureFolders(outputRoot));
    }

    private List<RecaptureJob> loadRecaptureJobs(Path outputRoot, List<String> folders) throws IOException {
        return loadRecaptureJobs(outputRoot, folders, false);
    }

    private List<RecaptureJob> loadRecaptureJobs(Path outputRoot, List<String> folders, boolean descriptorOnly) throws IOException {
        return loadRecaptureJobs(outputRoot, folders, descriptorOnly, null);
    }

    List<RecaptureJob> loadRecaptureJobs(Path outputRoot, List<String> folders, boolean descriptorOnly,
            java.util.function.IntConsumer progress) throws IOException {
        List<RecaptureJob> jobs = new ArrayList<>();
        long pageMetadataBytes = 0;
        for (String folder : folders) {
            RecaptureJob job = new RecaptureJob();
            job.folder = folder;
            Path folderPath;
            try {
                folderPath = CaptureIntegrity.resolveContained(outputRoot, folder);
            } catch (IOException unsafe) {
                job.invalidReason = "caminho da captura inválido"; //$NON-NLS-1$
                jobs.add(job);
                continue;
            }
            Path manifest = folderPath.resolve("manifest.json"); //$NON-NLS-1$
            job.folderPath = folderPath;
            if (!CaptureIntegrity.isSafeDirectory(folderPath)
                    || !CaptureIntegrity.isSafeRegularFile(manifest)) {
                job.invalidReason = "manifest.json nao encontrado"; //$NON-NLS-1$
                jobs.add(job);
                continue;
            }
            pageMetadataBytes += CaptureIntegrity.safeRegularFileSize(manifest);
            Path coordinates = folderPath.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
            if (CaptureIntegrity.isSafeRegularFile(coordinates)) pageMetadataBytes += CaptureIntegrity.safeRegularFileSize(coordinates);
            if (pageMetadataBytes > 16L * 1024L * 1024L) {
                throw new IOException("Os metadados desta página excedem o orçamento seguro de 16 MiB; selecione uma página menor.");
            }
            String content = CaptureIntegrity.readJsonUtf8Limited(manifest);
            String coordinateContent = Files.isRegularFile(coordinates, LinkOption.NOFOLLOW_LINKS)
                    ? CaptureIntegrity.readJsonUtf8Limited(coordinates)
                    : null;
            if (!populateRecaptureJobMetadata(job, content, coordinateContent)) {
                LOGGER.warn("Invalid manifest JSON in {}", manifest); //$NON-NLS-1$
                job.invalidReason = "manifest.json inválido"; //$NON-NLS-1$
                jobs.add(job);
                continue;
            }

            Path text = folderPath.resolve("texto.txt"); //$NON-NLS-1$
            if (Files.isRegularFile(text, LinkOption.NOFOLLOW_LINKS)) {
                CaptureTextHeader textHeader = readCaptureTextHeader(text);
                job.sourceName = firstNonBlank(job.sourceName, textHeader.title, textHeader.chatId);
                job.sourceTitle = firstNonBlank(job.sourceTitle, textHeader.title);
                job.chatId = firstNonBlank(job.chatId, textHeader.chatId);
            }

            // Legado: se ainda sem nome de origem, usa o titulo/pasta para permitir
            // localizar o chat e re-extrair, salvando na versao recente.
            job.title = firstNonBlank(job.sourceTitle, job.sourceName, folder);
            if (job.sourceName == null || job.sourceName.isBlank()) {
                job.sourceName = job.title;
            }
            if (job.sourceTitle == null || job.sourceTitle.isBlank()) {
                job.sourceTitle = job.title;
            }
            if (job.chatId == null || job.chatId.isBlank()) {
                job.chatId = inferChatId(job.title);
            }

            job.luceneQueries = buildRecaptureQueries(job);
            job.luceneQuery = job.luceneQueries.isEmpty() ? "" : job.luceneQueries.get(0); //$NON-NLS-1$
            boolean hasAnchors = job.htmlMessageCapture
                    ? job.startMessageId.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?") && job.endMessageId.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")
                    : job.startAnchor.isUsable() && job.endAnchor.isUsable();
            boolean hasQueries = !job.luceneQueries.isEmpty();
            // Capturas legadas podem nao ter todos os dados de assinatura; aceita
            // quando ha ancora utilizavel e ao menos uma consulta derivada.
            job.valid = hasAnchors && (hasQueries || job.legacyAnnotations);
            if (!job.valid && job.invalidReason == null) {
                job.invalidReason = "dados insuficientes para localizar chat/inicio/fim por assinatura"; //$NON-NLS-1$
            }
            jobs.add(job);
            if (descriptorOnly) {
                job.startAnchorLabel = job.startAnchor == null ? "(âncora inicial indisponível)" : job.startAnchor.display(); //$NON-NLS-1$
                job.endAnchorLabel = job.endAnchor == null ? "(âncora final indisponível)" : job.endAnchor.display(); //$NON-NLS-1$
                job.captureFingerprint = null;
                job.artifactGenerators = null;
                job.expectedArtifacts = null;
                job.startAnchor = null;
                job.endAnchor = null;
                job.luceneQueries = java.util.Collections.emptyList();
                job.luceneQuery = "";
                job.sourceName = boundedDescriptor(job.sourceName);
                job.sourceTitle = boundedDescriptor(job.sourceTitle);
                job.title = boundedDescriptor(job.title);
                job.sourcePath = boundedDescriptor(job.sourcePath);
            }
            if (progress != null) progress.accept(jobs.size());
        }
        return jobs;
    }

    static boolean populateRecaptureJobMetadata(RecaptureJob job, String manifestContent, String coordinateContent) {
        JSONObject savedManifest = parseJsonObjectOrNull(manifestContent);
        if (savedManifest == null) {
            return false;
        }
        job.captureFingerprint = savedManifest.optJSONObject("captureFingerprint");
        job.artifactGenerators = savedManifest.optJSONObject("artifactGenerators");
        job.expectedArtifacts = savedManifest.optJSONArray("artifacts");
        JSONObject priorCensorship = savedManifest.optJSONObject("censorship"); //$NON-NLS-1$
        job.priorCensorshipDigest = jsonString(priorCensorship, "presentationDigest"); //$NON-NLS-1$
        job.htmlMessageCapture = "html-message-v1".equals(jsonString(savedManifest, "captureFormat")); //$NON-NLS-1$ //$NON-NLS-2$
        job.legacyCapture = !job.htmlMessageCapture;
        job.legacyAnnotations = !job.htmlMessageCapture && jsonLong(savedManifest, "version", 1L) < 2L; //$NON-NLS-1$
        job.status = jsonString(savedManifest, "status"); //$NON-NLS-1$
        job.startMessageId = jsonString(savedManifest, "startMessageId"); //$NON-NLS-1$
        job.endMessageId = jsonString(savedManifest, "endMessageId"); //$NON-NLS-1$
        job.startAnchor = readAnchor(savedManifest, "startAnchor"); //$NON-NLS-1$
        job.endAnchor = readAnchor(savedManifest, "endAnchor"); //$NON-NLS-1$
        // Legacy captures may store the numeric message ids separately; enrich anchors after loading both.
        if (job.legacyCapture) {
            if (job.startAnchor.id.isBlank()) job.startAnchor.id = safe(job.startMessageId);
            if (job.endAnchor.id.isBlank()) job.endAnchor.id = safe(job.endMessageId);
        }
        if (job.htmlMessageCapture) {
            JSONObject anchors = savedManifest.optJSONObject("anchors"); //$NON-NLS-1$
            job.startMessageId = jsonString(anchors, "start"); //$NON-NLS-1$
            job.endMessageId = jsonString(anchors, "end"); //$NON-NLS-1$
            job.startAnchor.id = job.startMessageId;
            job.endAnchor.id = job.endMessageId;
            job.startAnchorLabel = "ID " + job.startMessageId; //$NON-NLS-1$
            job.endAnchorLabel = "ID " + job.endMessageId; //$NON-NLS-1$
        }
        // Capturas v2 nao gravam startMessageId/endMessageId; usa o id das ancoras.
        if (job.startMessageId == null || job.startMessageId.isBlank()) {
            job.startMessageId = job.startAnchor == null ? "" : safe(job.startAnchor.id); //$NON-NLS-1$
        }
        if (job.endMessageId == null || job.endMessageId.isBlank()) {
            job.endMessageId = job.endAnchor == null ? "" : safe(job.endAnchor.id); //$NON-NLS-1$
        }
        JSONObject source = savedManifest.optJSONObject("sourceMetadata"); //$NON-NLS-1$
        job.sourceName = firstNonBlank(jsonString(source, "nome"), jsonString(source, "name")); //$NON-NLS-1$ //$NON-NLS-2$
        job.sourceTitle = firstNonBlank(jsonString(source, "title"), jsonString(source, "name"), jsonString(source, "nome")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        job.sourceHash = firstNonBlank(jsonString(source, "itemHash"), jsonString(source, "hash"), jsonString(source, "sha256")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        job.sourcePath = jsonString(source, "caminho"); //$NON-NLS-1$

        // Compatibilidade com capturas legadas: tenta recuperar dados de origem
        // a partir de whatsapp-coordinates.json quando o manifest nao trouxe sourceMetadata completo.
        if (coordinateContent != null && !coordinateContent.isBlank()) {
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
            if (job.sourceName == null || job.sourceName.isBlank()) {
                JSONObject coordRoot = parseJsonObjectOrNull(coordinateContent);
                JSONObject coordSource = coordRoot == null ? null : coordRoot.optJSONObject("sourceMetadata"); //$NON-NLS-1$
                job.sourceName = firstNonBlank(job.sourceName, jsonString(coordSource, "nome"), jsonString(coordSource, "title")); //$NON-NLS-1$ //$NON-NLS-2$
                job.sourceTitle = firstNonBlank(job.sourceTitle, jsonString(coordSource, "title"), jsonString(coordSource, "nome")); //$NON-NLS-1$ //$NON-NLS-2$
                job.sourceHash = firstNonBlank(job.sourceHash, jsonString(coordSource, "hash")); //$NON-NLS-1$
                job.sourcePath = firstNonBlank(job.sourcePath, jsonString(coordSource, "caminho")); //$NON-NLS-1$
            }
        }
        return true;
    }

    private interface RecaptureLoad<T> { T load(java.util.function.IntConsumer progress) throws Exception; }

    private <T> T runRecaptureLoad(String title, RecaptureLoad<T> operation) throws Exception {
        final javax.swing.SwingWorker<T, Integer>[] holder = new javax.swing.SwingWorker[1];
        Runnable show = () -> {
            javax.swing.JDialog dialog = new javax.swing.JDialog((java.awt.Frame) null, title, true);
            JLabel status = ChatCapturePanel.plainLabel("Procurando… 0 processados");
            JButton cancel = new JButton("Cancelar");
            JPanel content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14));
            content.add(status, BorderLayout.CENTER);
            content.add(cancel, BorderLayout.SOUTH);
            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(chatCapturePanel);
            javax.swing.SwingWorker<T, Integer> worker = new javax.swing.SwingWorker<T, Integer>() {
                protected T doInBackground() throws Exception { return operation.load(value -> publish(value)); }
                protected void process(List<Integer> values) { if (!values.isEmpty()) status.setText("Procurando… " + values.get(values.size() - 1) + " processados"); }
                protected void done() { dialog.dispose(); }
            };
            cancel.addActionListener(event -> { worker.cancel(true); dialog.dispose(); });
            holder[0] = worker;
            worker.execute();
            dialog.setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) show.run();
        else SwingUtilities.invokeAndWait(show);
        try { return holder[0].get(); }
        catch (java.util.concurrent.CancellationException e) { throw new IOException("Operação de busca/validação cancelada.", e); }
        catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IOException("Falha durante busca/validação de capturas.", cause);
        }
    }

    private static String boundedDescriptor(String value) {
        if (value == null || value.length() <= 512) return value;
        return value.substring(0, 512);
    }


    private List<String> readCaptureFolders(Path outputRoot) throws IOException {
        return readCaptureFolders(outputRoot, null);
    }

    private List<String> readCaptureFolders(Path outputRoot, java.util.function.IntConsumer progress) throws IOException {
        List<String> folders = new ArrayList<>();
        Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Path normalizedRoot = outputRoot.toAbsolutePath().normalize();
        if (!CaptureIntegrity.isSafeDirectory(normalizedRoot)) {
            throw new IOException("A pasta de capturas não existe ou não é um diretório válido: " + normalizedRoot);
        }
        Path index = CaptureIntegrity.resolveContained(normalizedRoot, "capturas-index.json"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(index)) {
            String content = CaptureIntegrity.readJsonUtf8Limited(index);
            JSONArray indexedCaptures;
            try { indexedCaptures = new JSONObject(content).optJSONArray("captures"); }
            catch (RuntimeException invalidIndex) { throw new IOException("O índice de capturas contém JSON inválido.", invalidIndex); }
            if (indexedCaptures != null) for (int i = 0; i < indexedCaptures.length(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Busca cancelada");
                JSONObject indexedCapture = indexedCaptures.optJSONObject(i);
                if (indexedCapture == null) continue;
                String folder = indexedCapture.optString("folder", "").replace('\\', '/');
                if (folder.isBlank() || !seen.add(folder)) {
                    continue;
                }
                Path resolved;
                try {
                    resolved = CaptureIntegrity.resolveContained(normalizedRoot, folder);
                } catch (IOException unsafe) {
                    continue;
                }
                if (!CaptureIntegrity.isSafeDirectory(resolved)
                        || !CaptureIntegrity.isSafeRegularFile(resolved.resolve("manifest.json"))) { //$NON-NLS-1$
                    continue;
                }
                if (folders.size() >= MAX_RECAPTURE_CANDIDATES) {
                    throw new IOException("O índice excede o limite de " + MAX_RECAPTURE_CANDIDATES + " capturas candidatas.");
                }
                folders.add(normalizedRoot.relativize(resolved).toString().replace('\\', '/'));
                if (progress != null && folders.size() % 100 == 0) progress.accept(folders.size());
            }
        }
        if (folders.isEmpty() && CaptureIntegrity.isSafeDirectory(normalizedRoot)) {
            try (Stream<Path> stream = Files.list(normalizedRoot).limit(MAX_RECAPTURE_CANDIDATES * 2L)) {
                List<Path> candidates = new ArrayList<>();
                java.util.Iterator<Path> iterator = stream.iterator();
                while (iterator.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Busca cancelada");
                    Path path = iterator.next();
                    if (!CaptureIntegrity.isSafeDirectory(path) || isRecaptureAuxiliaryFolder(path)
                            || isReportAuxiliaryFolder(path)
                            || isLinkedCaptureFolder(path)
                            || !CaptureIntegrity.isSafeRegularFile(path.resolve("manifest.json"))) continue; //$NON-NLS-1$
                    if (candidates.size() >= MAX_RECAPTURE_CANDIDATES) {
                        throw new IOException("O diretório excede o limite de " + MAX_RECAPTURE_CANDIDATES + " capturas candidatas.");
                    }
                    candidates.add(path);
                }
                candidates.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
                for (Path candidate : candidates) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Busca cancelada");
                    String folder = normalizedRoot.relativize(candidate.toAbsolutePath().normalize()).toString().replace('\\', '/');
                    if (seen.add(folder)) folders.add(folder);
                    if (progress != null && folders.size() % 100 == 0) progress.accept(folders.size());
                }
            }
        }
        if (progress != null) progress.accept(folders.size());
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

    private void startRecaptureBatch(Path outputRoot, List<String> captureIds, boolean annotationsEnabled,
            boolean migrateLegacyToHtml, boolean resetLegacyRecords) {
        captureRunning = true;
        stopCaptureRequested = false;
        chatCapturePanel.setCaptureRunning(true);
        if (!annotationsEnabled) {
            chatCapturePanel.setAnnotationsEnabled(false);
        }
        annotationStore.bindOutputRoot(outputRoot);
        chatCapturePanel.setRecaptureProgress("Preparando re-extracao...", 0, captureIds.size(), 0, 7, true); //$NON-NLS-1$
        new Thread(() -> runRecaptureBatch(outputRoot, captureIds, annotationsEnabled, migrateLegacyToHtml, resetLegacyRecords), "IPED-chat-recapture").start(); //$NON-NLS-1$
    }

    private void runRecaptureBatch(Path outputRoot, List<String> captureIds, boolean annotationsEnabled,
            boolean migrateLegacyToHtml, boolean resetLegacyRecords) {
        int success = 0;
        int failed = 0;
        int replaced = 0;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()); //$NON-NLS-1$
        try {
            VerifierIntegrity.installVerifierFiles(outputRoot);
            CustodyCaseRecord batchRecord = this.activeCustodyRecord;
            if (batchRecord == null) {
                batchRecord = loadOrPrepareCustodyRecord(outputRoot);
            }
            try {
                CaptureAuditLogManager.reconcileKnownCaptures(outputRoot, batchRecord, "RE-EXTRAIR"); //$NON-NLS-1$
            } catch (Exception logInitErr) {
                LOGGER.warn("Unable to reconcile known captures before re-extract batch", logInitErr); //$NON-NLS-1$
            }
            for (int i = 0; i < captureIds.size() && !stopCaptureRequested; i++) {
                RecaptureJob job;
                try {
                    List<RecaptureJob> one = loadRecaptureJobs(outputRoot, java.util.Collections.singletonList(captureIds.get(i)));
                    job = one.isEmpty() ? null : one.get(0);
                    if (job == null || !job.valid) throw new IOException(job == null ? "Captura indisponível" : job.invalidReason);
                    job.legacyCapture = !job.htmlMessageCapture;
                    job.migrateLegacyToHtml = migrateLegacyToHtml && job.legacyCapture;
                    job.resetLegacyRecords = resetLegacyRecords && job.targetHtmlMessageCapture();
                } catch (Exception invalid) {
                    failed++;
                    LOGGER.warn("Selected capture became invalid before processing: {}", captureIds.get(i), invalid);
                    try {
                        CaptureAuditLogManager.recordRecaptureOutcome(outputRoot, batchRecord, null,
                                captureIds.get(i), "ERRO", invalid.getMessage(), ""); //$NON-NLS-1$ //$NON-NLS-2$
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                chatCapturePanel.setStatusText("Re-extraindo " + (i + 1) + "/" + captureIds.size() + ": " + job.folder); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                CustodyCaseRecord.CaptureSnapshot prevSnap = batchRecord.knownCaptures.get(job.folder);
                String prevHash = prevSnap == null ? "" : prevSnap.folderHash; //$NON-NLS-1$
                try {
                    resetRecaptureMarksAndPreviewState();
                    boolean captureReplaced = recaptureOne(outputRoot, job, stamp, i + 1, captureIds.size(), annotationsEnabled);
                    success++;
                    if (captureReplaced) replaced++;
                    try {
                        CaptureAuditLogManager.recordRecaptureOutcome(outputRoot, batchRecord, job.folderPath,
                                job.folder,
                                captureReplaced ? "ATUALIZADO_REPARADO" : "INALTERADO_OK", //$NON-NLS-1$ //$NON-NLS-2$
                                captureReplaced ? "Captura atualizada/reparada na re-extracao" : "Intervalo e arquivos integros sem alteracao", //$NON-NLS-1$ //$NON-NLS-2$
                                prevHash);
                    } catch (Exception logErr) {
                        LOGGER.warn("Unable to write re-extract audit log for {}", job.folder, logErr); //$NON-NLS-1$
                    }
                } catch (Throwable e) {
                    failed++;
                    LOGGER.warn("Unable to re-extract capture {}", job.folder, e); //$NON-NLS-1$
                    try {
                        CaptureAuditLogManager.recordRecaptureOutcome(outputRoot, batchRecord, job.folderPath,
                                job.folder, "ERRO", e.getMessage() == null ? e.toString() : e.getMessage(), prevHash); //$NON-NLS-1$
                    } catch (Exception ignored) {
                    }
                } finally {
                    resetRecaptureMarksAndPreviewState();
                }
            }
            chatCapturePanel.setRecaptureProgress("Limpando temporarios e recriando indice geral das capturas...", captureIds.size(), captureIds.size(), 7, 7, true); //$NON-NLS-1$
            cleanupRecaptureAuxiliaryFolders(outputRoot);
            if (resetLegacyRecords && replaced > 0) deleteRootIntegrityReportsIfExists(outputRoot);
            safeRebuildCaptureRootIndex(outputRoot);
            try {
                CaptureAuditLogManager.reconcileKnownCaptures(outputRoot, batchRecord, "RE-EXTRAIR"); //$NON-NLS-1$
                TechnicalReportGenerator.generateAll(outputRoot, batchRecord, this.originAuditFolder, this.lastKnownRootHash);
                this.activeCustodyRecord = batchRecord;
                safeRebuildCaptureRootIndex(outputRoot);
            } catch (Exception reportErr) {
                LOGGER.warn("Unable to regenerate technical report after re-extract batch", reportErr); //$NON-NLS-1$
            }
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

    private boolean recaptureOne(Path outputRoot, RecaptureJob job, String batchStamp, int batchCurrent, int batchTotal,
            boolean annotationsEnabled) throws Exception {
        updateRecaptureProgress(job, batchCurrent, batchTotal, 1, "Abrindo chat pelo indice do caso...", true); //$NON-NLS-1$
        if (!openRecaptureChat(job)) {
            throw new IOException("Chat nao encontrado: " + job.displayLabel()); //$NON-NLS-1$
        }
        updateRecaptureProgress(job, batchCurrent, batchTotal, 2, "Localizando mensagem inicial e final no chat...", true); //$NON-NLS-1$
        prepareRecaptureChatRange(job);

        activeRecaptureJob = job;
        try {
            if (job.htmlMessageCapture && !job.resetLegacyRecords && !job.priorCensorshipDigest.isEmpty() && this.sensitiveImagesFolder == null) {
                throw new IOException(Messages.getString("ChatCapturePanel.CensorshipWouldBeLost")); //$NON-NLS-1$
            }
            if (job.resetLegacyRecords && job.legacyCapture) {
                job.reusableAttachmentsByHashAndSize = buildReusableAttachmentIndex(job);
            }
            pendingAnnotations = null;
            if (annotationsEnabled && !job.legacyAnnotations) {
                pendingAnnotations = buildAnnotationPreflight().toAnnotationSet();
                if (pendingAnnotations.isEmpty() && !hasAnnotations()) {
                    pendingAnnotations = null;
                }
            }
            if (!job.resetLegacyRecords && !job.migrateLegacyToHtml && tryMigrateOrSkipCapture(job, batchCurrent, batchTotal)) {
                return false;
            }

            updateRecaptureProgress(job, batchCurrent, batchTotal, 4,
                    job.migrateLegacyToHtml ? Messages.getString("ChatCapturePanel.MigrateExtracting") : "Capturando novamente imagens, anexos e coordenadas...", true); //$NON-NLS-1$ //$NON-NLS-2$
            String tmpName = job.folder + ".__reextract_tmp_" + batchStamp; //$NON-NLS-1$
            Path tmpFolder = outputRoot.resolve(tmpName);
            LOGGER.info("Recapture capture tmp folder: {}", tmpFolder); //$NON-NLS-1$
            CaptureRunResult result = runChatCaptureInternal(outputRoot, tmpName, tmpFolder);
            try {
            updateRecaptureProgress(job, batchCurrent, batchTotal, 5, "Validando resultado temporario da re-extracao...", false); //$NON-NLS-1$
            Path manifest = result.captureDir.resolve("manifest.json"); //$NON-NLS-1$
            String status = Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                    ? jsonString(parseJsonObjectOrNull(CaptureIntegrity.readJsonUtf8Limited(manifest)), "status") //$NON-NLS-1$
                    : result.status;
            if (!"completed".equals(status)) { //$NON-NLS-1$
                deleteDirectoryIfExists(result.captureDir);
                throw new IOException("Re-extração nao completou: " + status); //$NON-NLS-1$
            }

            if (job.targetHtmlMessageCapture()) validateHtmlCaptureCandidate(job, result.captureDir);
            if (job.htmlMessageCapture && !job.resetLegacyRecords) {
                boolean presentationUnchanged = isHtmlPresentationUnchanged(job, result.captureDir);
                SelectiveRepairOutcome selective = presentationUnchanged
                        ? trySelectiveHtmlCaptureRepair(job.folderPath, result.captureDir, this.originAuditFolder != null)
                        : SelectiveRepairOutcome.notHandled();
                if (selective.handledInPlace) {
                    deleteDirectoryIfExists(result.captureDir);
                    if (!selective.modified) {
                        CaptureIntegrity.appendAudit(job.folderPath, new JSONObject()
                                .put("action", "recapture_skipped_content_unchanged") //$NON-NLS-1$ //$NON-NLS-2$
                                .put("result", "selected_interval_message_hashes_unchanged")); //$NON-NLS-1$ //$NON-NLS-2$
                        updateRecaptureProgress(job, batchCurrent, batchTotal, 7,
                                Messages.getString("ChatCapturePanel.ContentUnchanged"), false); //$NON-NLS-1$
                        return false;
                    }
                    updateRecaptureProgress(job, batchCurrent, batchTotal, 7,
                            "Re-extração seletiva: " + selective.summary, false); //$NON-NLS-1$
                    return true;
                }
                if (isReadableHtmlMessagesJson(job.folderPath)) {
                    writeHtmlMessageReconciliation(job.folderPath, result.captureDir);
                    if (isHtmlReconciliationUnchanged(result.captureDir) && !presentationUnchanged) {
                        CaptureIntegrity.appendAudit(job.folderPath, new JSONObject()
                                .put("action", "recapture_censorship_presentation_changed") //$NON-NLS-1$ //$NON-NLS-2$
                                .put("previousPresentationDigest", job.priorCensorshipDigest)); //$NON-NLS-1$
                    }
                    if (job.targetHtmlMessageCapture()) validateHtmlCaptureCandidate(job, result.captureDir);
                }
            }
            if (!job.resetLegacyRecords) {
                if (!job.migrateLegacyToHtml) recordRestoredArtifactAudit(job, result.captureDir);
                appendIntegrityHistory(result.captureDir, job.folderPath,
                        job.migrateLegacyToHtml ? "legacy_screenshot_capture_migrated_to_html_message_v1" : "recapture_completed"); //$NON-NLS-1$ //$NON-NLS-2$
                if (job.migrateLegacyToHtml) {
                    CaptureIntegrity.appendAudit(result.captureDir, new JSONObject()
                            .put("action", "legacy_screenshot_capture_migrated_to_html_message_v1")
                            .put("sourceFormat", "legacy-screenshot").put("result", "promoted")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }
            updateRecaptureProgress(job, batchCurrent, batchTotal, 6,
                    job.migrateLegacyToHtml ? "Substituindo a captura legada validada..." : "Substituindo a extracao antiga pela nova...", false); //$NON-NLS-1$ //$NON-NLS-2$
            Path oldFolder = uniqueSibling(outputRoot.resolve(job.folder + ".__old_" + batchStamp)); //$NON-NLS-1$
            LOGGER.info("Recapture replacing old folder {} with {}", job.folderPath, result.captureDir);
            if (CaptureIntegrity.isLinkOrReparse(outputRoot) || CaptureIntegrity.isLinkOrReparse(job.folderPath)
                    || CaptureIntegrity.isLinkOrReparse(result.captureDir) || CaptureIntegrity.isLinkOrReparse(oldFolder)) {
                throw new IOException("Symbolic links or junctions are not allowed during recapture swap"); //$NON-NLS-1$
            }
            Files.move(job.folderPath, oldFolder);
            try {
                Files.move(result.captureDir, job.folderPath);
            } catch (IOException e) {
                if (Files.exists(oldFolder, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(job.folderPath, LinkOption.NOFOLLOW_LINKS)
                        && !CaptureIntegrity.isLinkOrReparse(oldFolder)) {
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
            String completion = job.migrateLegacyToHtml
                    ? Messages.getString("ChatCapturePanel.MigrationCompleted") //$NON-NLS-1$
                    : "Chat re-extraido com sucesso."; //$NON-NLS-1$
            if (job.migrateLegacyToHtml && !job.resetLegacyRecords
                    && Files.isRegularFile(job.folderPath.resolve("legacy-annotations.json"), LinkOption.NOFOLLOW_LINKS)) { //$NON-NLS-1$
                completion += " " + Messages.getString("ChatCapturePanel.MigrationAnnotationsPreserved"); //$NON-NLS-1$
            }
            updateRecaptureProgress(job, batchCurrent, batchTotal, 7,
                    completion, false);
            return true;
            } catch (Exception failure) {
                if (Files.exists(result.captureDir, LinkOption.NOFOLLOW_LINKS)) {
                    try { deleteDirectoryIfExists(result.captureDir); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                }
                throw failure;
            }
        } finally {
            pendingAnnotations = null;
            activeRecaptureJob = null;
        }
    }

    private void recordRestoredArtifactAudit(RecaptureJob job, Path newCapture) throws IOException {
        if (job.expectedArtifacts == null || job.expectedArtifacts.length() == 0) {
            return;
        }
        JSONArray oldState = CaptureIntegrity.compareInventory(job.folderPath, job.expectedArtifacts);
        for (int i = 0; i < oldState.length(); i++) {
            JSONObject previous = oldState.optJSONObject(i);
            if (previous == null) {
                continue;
            }
            String oldStatus = previous.optString("status", "");
            if (!"missing".equals(oldStatus) && !"mismatch".equals(oldStatus)) {
                continue;
            }
            String relative = previous.optString("path", "");
            Path restored;
            try {
                restored = CaptureIntegrity.resolveContained(newCapture, relative);
            } catch (IOException e) {
                continue;
            }
            if (!CaptureIntegrity.isSafeRegularFile(restored)) {
                continue;
            }
            String restoredHash = CaptureIntegrity.sha256(restored);
            String expectedHash = previous.optString("expectedSha256", "");
            boolean same = !expectedHash.isBlank() && expectedHash.equalsIgnoreCase(restoredHash);
            CaptureIntegrity.appendAudit(newCapture, new JSONObject()
                    .put("action", "artifact_restored_by_full_recapture")
                    .put("path", relative)
                    .put("previousState", oldStatus)
                    .put("expectedSha256", expectedHash)
                    .put("restoredSha256", restoredHash)
                    .put("result", same ? "restored_same_hash" : "restored_changed_hash"));
        }
    }

    private void writeHtmlMessageReconciliation(Path previousCapture, Path currentCapture) throws IOException {
        JSONObject oldManifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(previousCapture.resolve("manifest.json"))); //$NON-NLS-1$
        if (!"html-message-v1".equals(oldManifest.optString("captureFormat"))) return; //$NON-NLS-1$ //$NON-NLS-2$
        JSONObject newManifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(currentCapture.resolve("manifest.json"))); //$NON-NLS-1$
        if (!oldManifest.optString("conversationKey").equals(newManifest.optString("conversationKey"))) {
            throw new IOException("A origem/conversa atual não corresponde à captura selecionada para Re-Extrair."); //$NON-NLS-1$
        }
        JSONObject oldWrapper = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(previousCapture.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES)); //$NON-NLS-1$
        JSONObject newWrapper = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(currentCapture.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES)); //$NON-NLS-1$
        JSONArray oldMessages = oldWrapper.optJSONArray("messages"), newMessages = newWrapper.optJSONArray("messages"); //$NON-NLS-1$ //$NON-NLS-2$
        if (oldMessages == null || newMessages == null) throw new IOException("Manifesto de mensagens ausente na reconciliação."); //$NON-NLS-1$
        Map<String, JSONObject> oldByKey = new java.util.TreeMap<>(), newByKey = new java.util.TreeMap<>();
        for (int i = 0; i < oldMessages.length(); i++) {
            JSONObject item = oldMessages.getJSONObject(i);
            if (oldByKey.put(item.optString("key"), item) != null) throw new IOException("Chave repetida na captura anterior."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        for (int i = 0; i < newMessages.length(); i++) {
            JSONObject item = newMessages.getJSONObject(i);
            if (newByKey.put(item.optString("key"), item) != null) throw new IOException("Chave repetida na nova captura."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Map<String, JSONObject> oldByDomId = new HashMap<>();
        for (JSONObject item : oldByKey.values()) {
            if (oldByDomId.put(item.optString("domId"), item) != null) throw new IOException("ID DOM repetido na captura anterior."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Set<String> usedOld = new HashSet<>();
        JSONArray entries = new JSONArray();
        int unchanged = 0, changed = 0, missing = 0, added = 0;
        for (JSONObject after : newByKey.values()) {
            String key = after.optString("key"); //$NON-NLS-1$
            JSONObject before = oldByKey.get(key);
            if (before == null) {
                JSONObject sameDomId = oldByDomId.get(after.optString("domId")); //$NON-NLS-1$
                if (sameDomId != null && !usedOld.contains(sameDomId.optString("key"))) before = sameDomId; //$NON-NLS-1$
            }
            String previousHash = before == null ? "" : before.optString("messageHash"); //$NON-NLS-1$ //$NON-NLS-2$
            String currentHash = after.optString("messageHash"); //$NON-NLS-1$
            String state;
            if (before == null) { state = "new_in_interval"; added++; } //$NON-NLS-1$
            else if (previousHash.equalsIgnoreCase(currentHash)) { state = "unchanged"; unchanged++; } //$NON-NLS-1$
            else { state = "changed"; changed++; }
            if (before != null) usedOld.add(before.optString("key")); //$NON-NLS-1$
            entries.put(new JSONObject().put("key", key).put("state", state) //$NON-NLS-1$ //$NON-NLS-2$
                    .put("domId", after.optString("domId")).put("previousKey", before == null ? "" : before.optString("key")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .put("previousMessageHash", previousHash).put("currentMessageHash", currentHash)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        for (JSONObject before : oldByKey.values()) {
            String key = before.optString("key"); //$NON-NLS-1$
            if (usedOld.contains(key)) continue;
            missing++;
            entries.put(new JSONObject().put("key", key).put("state", "missing_in_current_source") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("domId", before.optString("domId")).put("previousKey", key) //$NON-NLS-1$ //$NON-NLS-2$
                    .put("previousMessageHash", before.optString("messageHash")).put("currentMessageHash", "")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        String json = new JSONObject().put("schemaVersion", 1).put("captureFormat", "html-message-v1") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("previousCaptureRootHash", oldManifest.optString("captureRootHash")) //$NON-NLS-1$ //$NON-NLS-2$
                .put("previousReconciliationSha256", oldManifest.optString("reconciliationSha256", "")) //$NON-NLS-1$ //$NON-NLS-2$
                .put("currentSourceFingerprint", newManifest.optString("sourceFingerprint")) //$NON-NLS-1$ //$NON-NLS-2$
                .put("entries", entries).put("counts", new JSONObject().put("unchanged", unchanged).put("changed", changed) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        .put("missingInCurrentSource", missing).put("newInInterval", added)).toString(2) + System.lineSeparator(); //$NON-NLS-1$ //$NON-NLS-2$
        Path reconciliation = currentCapture.resolve("message-reconciliation.json"); //$NON-NLS-1$
        CaptureIntegrity.writeAtomically(currentCapture, reconciliation, json);
        JSONObject manifest = newManifest;
        String reconciliationSha = CaptureIntegrity.sha256(reconciliation);
        manifest.put("reconciliationFile", "message-reconciliation.json").put("reconciliationSha256", reconciliationSha); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        JSONArray messages = newMessages;
        JSONObject anchors = manifest.optJSONObject("anchors"); //$NON-NLS-1$
        manifest.put("captureRootHash", htmlCaptureRootHash(messages, manifest.optString("htmlSha256"), //$NON-NLS-1$ //$NON-NLS-2$
                manifest.optString("annotationsSha256"), reconciliationSha, manifest.optString("conversationKey"), //$NON-NLS-1$ //$NON-NLS-2$
                manifest.optString("sourceFingerprint"), manifest.optJSONObject("sourceMetadata"), //$NON-NLS-1$
                manifest.optString("ipedVersion"), manifest.optString("pluginVersion"), //$NON-NLS-1$ //$NON-NLS-2$
                anchors == null ? "" : anchors.optString("start"), anchors == null ? "" : anchors.optString("end"))); //$NON-NLS-1$ //$NON-NLS-2$
        CaptureIntegrity.writeAtomically(currentCapture, currentCapture.resolve("manifest.json"), manifest.toString(2) + System.lineSeparator()); //$NON-NLS-1$
        writeArtifactInventory(currentCapture);
    }
    private boolean isHtmlReconciliationUnchanged(Path capture) throws IOException {
        Path reconciliationPath = capture.resolve("message-reconciliation.json"); //$NON-NLS-1$
        JSONObject reconciliation = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(reconciliationPath));
        JSONObject counts = reconciliation.optJSONObject("counts"); //$NON-NLS-1$
        return counts != null && counts.optInt("changed", -1) == 0
                && counts.optInt("missingInCurrentSource", -1) == 0
                && counts.optInt("newInInterval", -1) == 0;
    }

    private boolean isHtmlPresentationUnchanged(RecaptureJob job, Path capture) throws IOException {
        JSONObject manifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(capture.resolve("manifest.json"))); //$NON-NLS-1$
        JSONObject censorship = manifest.optJSONObject("censorship"); //$NON-NLS-1$
        String current = censorship == null ? "" : censorship.optString("presentationDigest", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return current.equalsIgnoreCase(job.priorCensorshipDigest == null ? "" : job.priorCensorshipDigest); //$NON-NLS-1$
    }

    private void validateHtmlCaptureCandidate(RecaptureJob job, Path candidate) throws IOException {
        Path manifestPath = candidate.resolve("manifest.json"); //$NON-NLS-1$
        Path messagesPath = candidate.resolve("messages.json"); //$NON-NLS-1$
        Path htmlPath = candidate.resolve("whatsapp.html"); //$NON-NLS-1$
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(messagesPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(htmlPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A candidata HTML não contém todos os artefatos obrigatórios."); //$NON-NLS-1$
        }
        JSONObject manifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(manifestPath));
        if (!"html-message-v1".equals(manifest.optString("captureFormat")) //$NON-NLS-1$
                || !"completed".equals(manifest.optString("status")) //$NON-NLS-1$
                || !"complete".equals(manifest.optString("state"))) { //$NON-NLS-1$
            throw new IOException("Formato ou estado da candidata HTML é inválido."); //$NON-NLS-1$
        }
        if (Files.exists(candidate.resolve("screenshots"), LinkOption.NOFOLLOW_LINKS) //$NON-NLS-1$
                || Files.exists(candidate.resolve("whatsapp-coordinates.json"), LinkOption.NOFOLLOW_LINKS)) { //$NON-NLS-1$
            throw new IOException("A candidata HTML ainda contém artefatos do formato de frames."); //$NON-NLS-1$
        }
        JSONObject migration = manifest.optJSONObject("migration"); //$NON-NLS-1$
        if (job.resetLegacyRecords) {
            if (migration != null || Files.exists(candidate.resolve("migration.json"), LinkOption.NOFOLLOW_LINKS) //$NON-NLS-1$
                    || Files.exists(candidate.resolve("legacy-annotations.json"), LinkOption.NOFOLLOW_LINKS) //$NON-NLS-1$
                    || Files.exists(candidate.resolve("message-reconciliation.json"), LinkOption.NOFOLLOW_LINKS) //$NON-NLS-1$
                    || Files.exists(candidate.resolve(CaptureIntegrity.AUDIT_FILE), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A candidata de cadeia limpa ainda contém registros legados."); //$NON-NLS-1$
            }
        } else if (job.migrateLegacyToHtml) {
            Path migrationPath = candidate.resolve("migration.json"); //$NON-NLS-1$
            if (migration == null || !"legacy_screenshot_capture_migrated_to_html_message_v1" //$NON-NLS-1$
                    .equals(migration.optString("action"))
                    || !"migration.json".equals(manifest.optString("migrationFile")) //$NON-NLS-1$
                    || !Files.isRegularFile(migrationPath, LinkOption.NOFOLLOW_LINKS)
                    || !CaptureIntegrity.sha256(migrationPath).equalsIgnoreCase(manifest.optString("migrationSha256"))) { //$NON-NLS-1$
                throw new IOException("Registro de proveniência da migração ausente ou divergente."); //$NON-NLS-1$
            }
            JSONObject migrationFile = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(migrationPath));
            if (!CaptureIntegrity.canonicalJson(migration).equals(CaptureIntegrity.canonicalJson(migrationFile))) {
                throw new IOException("O manifesto e o registro da migração não correspondem."); //$NON-NLS-1$
            }
            boolean annotationsPreserved = migration.optBoolean("legacyAnnotationsPreserved", false); //$NON-NLS-1$
            if (annotationsPreserved != Files.isRegularFile(candidate.resolve("legacy-annotations.json"), LinkOption.NOFOLLOW_LINKS)) { //$NON-NLS-1$
                throw new IOException("O estado das anotações legadas diverge do registro de migração."); //$NON-NLS-1$
            }
        } else if (migration != null || Files.exists(candidate.resolve("migration.json"), LinkOption.NOFOLLOW_LINKS)) { //$NON-NLS-1$
            throw new IOException("Uma captura que não está sendo migrada contém registro de migração inesperado."); //$NON-NLS-1$
        }
        JSONObject anchors = manifest.optJSONObject("anchors"); //$NON-NLS-1$
        String start = anchors == null ? "" : anchors.optString("start"); //$NON-NLS-1$
        String end = anchors == null ? "" : anchors.optString("end"); //$NON-NLS-1$
        if (!start.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?") || !end.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")
                || !start.equals(captureStartMessageId) || !end.equals(captureEndMessageId)) {
            throw new IOException("As âncoras HTML não correspondem ao intervalo localizado para Re-Extrair."); //$NON-NLS-1$
        }
        CaptureSourceMetadata sourceMetadata = readCaptureSourceMetadata();
        String currentConversation = CaptureIntegrity.sha256Text(safe(sourceMetadata.name) + "\n" //$NON-NLS-1$
                + safe(sourceMetadata.path) + "\n" + safe(currentChatTitle())); //$NON-NLS-1$
        if (currentConversation.isBlank() || !currentConversation.equalsIgnoreCase(manifest.optString("conversationKey"))) {
            throw new IOException("A conversa da candidata HTML diverge da conversa localizada."); //$NON-NLS-1$
        }
        JSONObject wrapper = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(messagesPath, MAX_HTML_MESSAGE_CAPTURE_BYTES));
        JSONArray messages = wrapper.optJSONArray("messages"); //$NON-NLS-1$
        if (messages == null || messages.length() == 0 || messages.length() > MAX_HTML_MESSAGE_COUNT
                || messages.length() != manifest.optInt("messageCount", -1)) {
            throw new IOException("A lista de mensagens da candidata está ausente ou fora dos limites."); //$NON-NLS-1$
        }
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null || message.optInt("order", -1) != i
                    || !message.optString("domId").matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")
                    || !message.optString("key").matches("(?i)[0-9a-f]{64}")
                    || !message.optString("messageHash").matches("(?i)[0-9a-f]{64}")
                    || !ids.add(message.optString("domId")) || !keys.add(message.optString("key"))
                    || !htmlMessageHash(message).equalsIgnoreCase(message.optString("messageHash"))) {
                throw new IOException("A candidata contém ID, chave, ordem ou hash de mensagem inválidos."); //$NON-NLS-1$
            }
        }
        String htmlSha = CaptureIntegrity.sha256(htmlPath);
        if (!htmlSha.equalsIgnoreCase(manifest.optString("htmlSha256"))) {
            throw new IOException("O SHA-256 do whatsapp.html não corresponde ao manifesto."); //$NON-NLS-1$
        }
        if (CaptureIntegrity.safeRegularFileSize(htmlPath) > MAX_HTML_MESSAGE_CAPTURE_BYTES
                || !containsUtf8(htmlPath, "Content-Security-Policy") //$NON-NLS-1$
                || !containsUtf8(htmlPath, CAPTURE_CSP)
                || !containsUtf8(htmlPath, CAPTURE_MEDIA_STOP_SCRIPT)) {
            throw new IOException("O HTML da candidata não passou na verificação de estrutura e política local."); //$NON-NLS-1$
        }
        String computedRoot = htmlCaptureRootHash(messages, htmlSha,
                manifest.optString("annotationsSha256"), manifest.optString("reconciliationSha256"),
                manifest.optString("conversationKey"), manifest.optString("sourceFingerprint"),
                manifest.optJSONObject("sourceMetadata"), manifest.optString("ipedVersion"),
                manifest.optString("pluginVersion"), start, end);
        if (!computedRoot.equalsIgnoreCase(manifest.optString("captureRootHash"))) {
            throw new IOException("O hash raiz da candidata não confere com as mensagens e o HTML."); //$NON-NLS-1$
        }
        for (Path path : CaptureIntegrity.listRegularFilesSafe(candidate, 25000)) {
            CaptureIntegrity.resolveContained(candidate, candidate.relativize(path).toString());
        }
        JSONArray inventory = CaptureIntegrity.artifactInventory(candidate);
        if (inventory.length() == 0 || inventory.length() > 25000) {
            throw new IOException("Inventário da candidata vazio ou acima do limite."); //$NON-NLS-1$
        }
        long total = 0;
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject item = inventory.optJSONObject(i);
            if (item == null) throw new IOException("Entrada inválida no inventário da candidata."); //$NON-NLS-1$
            total += item.optLong("size", 0);
            if (total > MAX_HTML_MESSAGE_CAPTURE_BYTES) throw new IOException("A candidata ultrapassa o limite total de bytes."); //$NON-NLS-1$
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONArray attachments = messages.getJSONObject(i).optJSONArray("attachments"); //$NON-NLS-1$
            if (attachments == null) throw new IOException("Mensagem sem lista de anexos na candidata."); //$NON-NLS-1$
            for (int j = 0; j < attachments.length(); j++) {
                JSONObject attachment = attachments.optJSONObject(j);
                if (attachment == null) throw new IOException("Metadado de anexo inválido na candidata."); //$NON-NLS-1$
                String relative = attachment.optString("path"); //$NON-NLS-1$
                Path file = CaptureIntegrity.resolveContained(candidate, relative);
                if (!relative.matches("attachments/[0-9a-fA-F]{64}\\.[a-z0-9]{1,10}") //$NON-NLS-1$
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || CaptureIntegrity.safeRegularFileSize(file) != attachment.optLong("size", -1)
                        || !CaptureIntegrity.sha256(file).equalsIgnoreCase(attachment.optString("sha256"))) { //$NON-NLS-1$
                    throw new IOException("Anexo da candidata está ausente ou diverge do manifesto: " + relative); //$NON-NLS-1$
                }
            }
        }
    }

    private boolean containsUtf8(Path path, String expected) throws IOException {
        byte[] pattern = expected.getBytes(StandardCharsets.UTF_8);
        if (pattern.length == 0) return true;
        byte[] buffer = new byte[16 * 1024];
        String carry = ""; //$NON-NLS-1$
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                String section = carry + new String(buffer, 0, count, StandardCharsets.UTF_8);
                if (section.contains(expected)) return true;
                int keepChars = Math.min(expected.length() - 1, section.length());
                carry = section.substring(section.length() - keepChars);
            }
        }
        return false;
    }

    private boolean tryMigrateOrSkipCapture(RecaptureJob job, int batchCurrent, int batchTotal) throws IOException {
        if (job.captureFingerprint == null || !job.captureFingerprint.optBoolean("complete", false)
                || job.expectedArtifacts == null || job.expectedArtifacts.length() == 0
                || job.artifactGenerators == null) {
            return false;
        }
        CaptureSourceMetadata metadata = readCaptureSourceMetadata();
        CaptureAnchor start = copyAnchor(job.startAnchor);
        CaptureAnchor end = copyAnchor(job.endAnchor);
        start.id = captureStartMessageId;
        end.id = captureEndMessageId;
        JSONObject currentFingerprint = buildCaptureFingerprint(metadata, start, end);
        if (!currentFingerprint.optBoolean("complete", false)
                || !job.captureFingerprint.optString("value", "").equals(currentFingerprint.optString("value", ""))) {
            return false;
        }
        String oldSourceHash = job.sourceHash == null ? "" : job.sourceHash;
        String currentSourceHash = metadata == null || metadata.hash == null ? "" : metadata.hash;
        if (oldSourceHash.isBlank() || !oldSourceHash.equalsIgnoreCase(currentSourceHash)) {
            return false;
        }

        JSONArray comparison = CaptureIntegrity.compareInventory(job.folderPath, job.expectedArtifacts);
        boolean htmlNeedsUpdate = false;
        for (int i = 0; i < comparison.length(); i++) {
            JSONObject result = comparison.optJSONObject(i);
            if (result == null) {
                return false;
            }
            String rel = result.optString("path", "");
            String status = result.optString("status", "");
            if ("whatsapp.html".equals(rel) && ("missing".equals(status) || "mismatch".equals(status))) {
                htmlNeedsUpdate = true;
                continue;
            }
            if (!"match".equals(status)) {
                return false;
            }
        }

        JSONObject currentGenerators = new JSONObject(artifactGeneratorsJson());
        for (String name : currentGenerators.keySet()) {
            if (!"whatsapp.html".equals(name) && job.artifactGenerators.optInt(name, -1) != currentGenerators.optInt(name, -2)) {
                return false;
            }
        }
        int htmlGenerator = job.artifactGenerators.optInt("whatsapp.html", 0);
        boolean migrationRequired = htmlGenerator < HTML_GENERATOR_VERSION;
        if (migrationRequired || htmlNeedsUpdate) {
            if (!regenerateWhatsAppHtmlFromSavedCoordinates(job)) {
                return false;
            }
            JSONObject event = new JSONObject()
                    .put("action", "artifact_migrated")
                    .put("path", "whatsapp.html")
                    .put("oldGeneratorVersion", htmlGenerator)
                    .put("newGeneratorVersion", HTML_GENERATOR_VERSION)
                    .put("result", "migrated_from_saved_coordinates");
            CaptureIntegrity.appendAudit(job.folderPath, event);
            updateRecaptureProgress(job, batchCurrent, batchTotal, 7, "Arquivo HTML atualizado usando os dados salvos.", false); //$NON-NLS-1$
            return true;
        }

        JSONObject event = new JSONObject()
                .put("action", job.htmlMessageCapture ? "recapture_skipped_content_unchanged" : "capture_skipped_unchanged") //$NON-NLS-1$ //$NON-NLS-2$
                .put("sourceSha256", currentSourceHash)
                .put("result", "verified_inventory_and_fingerprint");
        CaptureIntegrity.appendAudit(job.folderPath, event);
        updateRecaptureProgress(job, batchCurrent, batchTotal, 7, "Captura inalterada; hashes e configuracoes conferem.", false); //$NON-NLS-1$
        return true;
    }

    private CaptureAnchor copyAnchor(CaptureAnchor source) {
        CaptureAnchor copy = new CaptureAnchor();
        if (source != null) {
            copy.id = source.id;
            copy.type = source.type;
            copy.text = source.text;
            copy.date = source.date;
            copy.hash = source.hash;
        }
        return copy;
    }

    boolean regenerateWhatsAppHtmlFromSavedCoordinates(RecaptureJob job) throws IOException {
        Path coordinates = job.folderPath.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
        if (!CaptureIntegrity.isSafeRegularFile(coordinates)) {
            return false;
        }
        String content = CaptureIntegrity.readJsonUtf8Limited(coordinates);
        JSONObject data;
        try {
            data = new JSONObject(content);
        } catch (RuntimeException e) {
            LOGGER.warn("Cannot migrate HTML from invalid coordinates JSON at {}", coordinates, e);
            return false;
        }
        JSONArray frames = data.optJSONArray("frames"); //$NON-NLS-1$
        if (frames == null || frames.length() == 0) {
            return false;
        }
        validateWhatsAppCoordinatesData(data);
        Path html = job.folderPath.resolve("whatsapp.html"); //$NON-NLS-1$
        Path temporary = uniqueSibling(html.resolveSibling("whatsapp.html.__migration_tmp")); //$NON-NLS-1$
        Path backup = uniqueSibling(job.folderPath.resolveSibling(job.folderPath.getFileName().toString() + ".__migration_html_backup")); //$NON-NLS-1$
        Path manifestPath = job.folderPath.resolve("manifest.json"); //$NON-NLS-1$
        String oldManifest = CaptureIntegrity.readJsonUtf8Limited(manifestPath);
        boolean backedUpHtml = false;
        try {
            CaptureIntegrity.ensureNoSymbolicLinkComponents(job.folderPath, job.folderPath);
            if (CaptureIntegrity.isLinkOrReparse(html) || CaptureIntegrity.isLinkOrReparse(temporary)
                    || CaptureIntegrity.isLinkOrReparse(backup)) {
                throw new IOException("Symbolic links or junctions are not allowed during HTML migration"); //$NON-NLS-1$
            }
            writeWhatsAppHtml(job.folderPath, temporary, content);
            String generated = CaptureIntegrity.readUtf8Limited(temporary);
            if (!generated.contains("id=\"coords\"") || !generated.contains("</html>")) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
            if (Files.exists(html, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(html, backup);
                backedUpHtml = true;
            }
            moveReplacing(temporary, html);
            JSONObject manifest = new JSONObject(oldManifest);
            JSONObject generators = manifest.optJSONObject("artifactGenerators"); //$NON-NLS-1$
            if (generators == null) {
                generators = new JSONObject();
            }
            generators.put("whatsapp.html", HTML_GENERATOR_VERSION); //$NON-NLS-1$
            manifest.put("artifactGenerators", generators); //$NON-NLS-1$
            manifest.put("pluginVersion", CHAT_CAPTURE_PLUGIN_VERSION); //$NON-NLS-1$
            manifest.put("artifacts", CaptureIntegrity.artifactInventory(job.folderPath)); //$NON-NLS-1$
            CaptureIntegrity.writeAtomically(job.folderPath, manifestPath, manifest.toString(2) + System.lineSeparator());
            recordHtmlMigrationAudit(job, html);
            if (backedUpHtml) {
                Files.deleteIfExists(backup);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            if (backedUpHtml && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                    && !CaptureIntegrity.isLinkOrReparse(backup) && !CaptureIntegrity.isLinkOrReparse(html)) {
                Files.deleteIfExists(html);
                Files.move(backup, html);
            }
            CaptureIntegrity.writeAtomically(job.folderPath, manifestPath, oldManifest);
            throw e;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void recordHtmlMigrationAudit(RecaptureJob job, Path html) throws IOException {
        String expectedHash = "";
        if (job.expectedArtifacts != null) {
            for (int i = 0; i < job.expectedArtifacts.length(); i++) {
                JSONObject artifact = job.expectedArtifacts.optJSONObject(i);
                if (artifact != null && "whatsapp.html".equals(artifact.optString("path", ""))) {
                    expectedHash = artifact.optString("sha256", "");
                    break;
                }
            }
        }
        String restoredHash = CaptureIntegrity.sha256(html);
        String result = expectedHash.isBlank() ? "restored_unverified"
                : expectedHash.equalsIgnoreCase(restoredHash) ? "restored_same_hash" : "restored_changed_hash";
        CaptureIntegrity.appendAudit(job.folderPath, new JSONObject()
                .put("action", "artifact_restored_by_regeneration")
                .put("path", "whatsapp.html")
                .put("expectedSha256", expectedHash)
                .put("restoredSha256", restoredHash)
                .put("result", result));
    }
    private void moveReplacing(Path source, Path destination) throws IOException {
        if (CaptureIntegrity.isLinkOrReparse(source) || CaptureIntegrity.isLinkOrReparse(destination)) {
            throw new IOException("Symbolic links or junctions are not allowed"); //$NON-NLS-1$
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
private boolean openRecaptureChat(RecaptureJob job) throws InterruptedException {
        if (job == null || job.luceneQueries == null || job.luceneQueries.isEmpty()) {
            return false;
        }
        if (!job.htmlMessageCapture && job.startAnchor.id.isBlank() && job.endAnchor.id.isBlank()
                && !job.startAnchor.isUsable() && !job.endAnchor.isUsable()) {
            LOGGER.warn("Skipping recapture job without usable anchors or saved ids: {}", job.folder); //$NON-NLS-1$
            return false;
        }
        for (String query : job.luceneQueries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            job.luceneQuery = query;
            LOGGER.info("Trying to re-extract chat {} with query {}", job.folder, query); //$NON-NLS-1$
            if (!openSearcherItem(attachSearcher, query, null, job.sourceName)) {
                continue;
            }
            if (waitUntilCaptureSourceMatches(job, 3000)) {
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
        normalizeRecaptureBoundariesFromDom();
        scrollToCaptureStartMessage();
        highlightCaptureMarks();
        sleep(800);
    }

    private void normalizeRecaptureBoundariesFromDom() {
        try {
            List<CaptureBoundaryResolver.Row> rows = readCaptureBoundaryRows();
            CaptureBoundaryResolver.Resolution start = CaptureBoundaryResolver.resolve(rows, captureStartMessageId,
                    CaptureBoundaryResolver.Role.START);
            int startIdx = 0;
            if (start.isValid()) {
                captureStartMessageId = start.messageId;
                startIdx = start.rowIndex;
            }
            CaptureBoundaryResolver.Resolution end = CaptureBoundaryResolver.resolve(rows, captureEndMessageId,
                    CaptureBoundaryResolver.Role.END);
            if (end.isValid()) {
                captureEndMessageId = end.messageId;
            } else if ("end_date_not_supported".equals(end.errorCode)) { //$NON-NLS-1$
                int dateIdx = -1;
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).dateIds.contains(captureEndMessageId)) {
                        dateIdx = i;
                        break;
                    }
                }
                if (dateIdx >= 0) {
                    String fallbackEnd = ""; //$NON-NLS-1$
                    for (int i = dateIdx - 1; i >= startIdx; i--) {
                        if (!rows.get(i).messageId.isEmpty()) {
                            fallbackEnd = rows.get(i).messageId;
                            break;
                        }
                    }
                    if (fallbackEnd.isEmpty()) {
                        for (int i = Math.max(dateIdx + 1, startIdx); i < rows.size(); i++) {
                            if (!rows.get(i).messageId.isEmpty()) {
                                fallbackEnd = rows.get(i).messageId;
                                break;
                            }
                        }
                    }
                    if (!fallbackEnd.isEmpty()) {
                        captureEndMessageId = fallbackEnd;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to normalize recapture boundaries", e); //$NON-NLS-1$
        }
    }

    private RecaptureLocatedRange resolveRecaptureRangeFromDom(RecaptureJob job) throws InterruptedException {
        if (job == null) {
            return new RecaptureLocatedRange("", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        LOGGER.info("Recapture DOM resolve started for {}", job.folder); //$NON-NLS-1$
        if (job.htmlMessageCapture) {
            String direct = "(function(a,b){return document.getElementById(a)&&document.getElementById(b)?a+'|'+b:'|';})(" //$NON-NLS-1$
                    + js(job.startMessageId) + "," + js(job.endMessageId) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            executeScriptAndWait("window.scrollTo(0,0);"); //$NON-NLS-1$
            long directDeadline = System.currentTimeMillis() + 180000;
            while (System.currentTimeMillis() < directDeadline && !stopCaptureRequested) {
                RecaptureLocatedRange found = RecaptureLocatedRange.parse(String.valueOf(executeScriptAndWait(direct)));
                if (!found.startId.isBlank() && !found.endId.isBlank()) return found;
                executeScriptAndWait("window.scrollBy(0,4000);"); //$NON-NLS-1$
                sleep(250);
            }
            return new RecaptureLocatedRange("", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        executeScriptAndWait("window.scrollTo(0,0);"); //$NON-NLS-1$
        sleep(600);

        // Fallback: if the legacy capture saved numeric message ids, try to locate them directly
        // before relying on the slower anchor-resolution script.
        if (job.startMessageId != null && !job.startMessageId.isBlank()
                && job.endMessageId != null && !job.endMessageId.isBlank()) {
            String direct = "(function(a,b){return document.getElementById(a)&&document.getElementById(b)?a+'|'+b:'|';})(" //$NON-NLS-1$
                    + js(job.startMessageId) + "," + js(job.endMessageId) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            Object directResult = executeScriptAndWait(direct);
            RecaptureLocatedRange directRange = RecaptureLocatedRange.parse(directResult == null ? "|" : directResult.toString()); //$NON-NLS-1$
            if (!directRange.startId.isBlank() && !directRange.endId.isBlank()) {
                LOGGER.info("Recapture DOM resolved directly for {}: {} -> {}", job.folder, directRange.startId, directRange.endId); //$NON-NLS-1$
                return directRange;
            }
        }

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
        CaptureRangeValidation range = resolveCaptureRange();
        if (!range.valid()) throw new IOException(Messages.getString(captureBoundaryMessageKey(range.errorCode)));
        captureStartMessageId = range.start.messageId;
        captureEndMessageId = range.end.messageId;
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
        boolean nameMatch = job.sourceName != null && !job.sourceName.isBlank()
                && job.sourceName.equals(item.getName());
        String expected = job.sourcePath == null ? "" : job.sourcePath.replace('\\', '/');
        String actual = safe(item.getPath()).replace('\\', '/');
        boolean pathMatch = !expected.isBlank() && expected.equalsIgnoreCase(actual);
        boolean hashMatch = job.sourceHash != null && !job.sourceHash.isBlank()
                && job.sourceHash.equalsIgnoreCase(safe(item.getHash()));
        if (nameMatch || pathMatch || hashMatch) {
            return true;
        }
        // Fallback para capturas legadas: tenta localizar pelo chatId/titulo no nome do item.
        String needle = firstNonBlank(job.chatId, job.title, job.sourceTitle);
        if (needle != null && !needle.isBlank()) {
            String itemText = (safe(item.getName()) + " " + safe(item.getPath())).toLowerCase();
            if (itemText.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Path uniqueSibling(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return path;
        }
        String name = path.getFileName().toString();
        Path parent = path.getParent();
        for (int i = 1; ; i++) {
            Path candidate = parent.resolve(name + "_" + i); //$NON-NLS-1$
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
    }

    private void runChatCapture(Path outputRoot, String customFolderName) {
        boolean captureCommitted = false;
        Path committedDir = null;
        try {
            CaptureRunResult result = runChatCaptureInternal(outputRoot, customFolderName, null);
            captureCommitted = result != null && CaptureIntegrity.isSafeDirectory(result.captureDir);
            if (captureCommitted) {
                committedDir = result.captureDir;
            }
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
            if (captureCommitted) {
                safeRebuildCaptureRootIndex(outputRoot);
                finalizeLaudoAfterStart(outputRoot, committedDir);
            }
        }
    }

    private CaptureRunResult runChatCaptureInternal(Path outputRoot, String customFolderName, Path exactCaptureDir) throws Exception {
        List<CaptureFrame> frames = new ArrayList<>();
        Map<String, ExportedFile> exported = new LinkedHashMap<>();
        Set<String> exportedAttachmentHashes = new HashSet<>();
        String status = "completed"; //$NON-NLS-1$
        Path captureDir = null;
        Path finalCaptureDir = null;
        boolean workDirCreated = false;
        boolean promoted = false;
        currentCaptureOutputRoot = outputRoot;
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()); //$NON-NLS-1$
            if (CaptureIntegrity.isLinkOrReparse(outputRoot)) {
                throw new IOException("Symbolic links or junctions are not allowed as capture output root"); //$NON-NLS-1$
            }
            finalCaptureDir = exactCaptureDir != null ? exactCaptureDir : outputRoot.resolve(buildCaptureFolderName(customFolderName, timestamp));
            captureDir = exactCaptureDir != null ? exactCaptureDir
                    : outputRoot.resolve(".__capture_staging_" + java.util.UUID.randomUUID().toString().replace("-", "")); //$NON-NLS-1$ //$NON-NLS-2$
            CaptureIntegrity.ensureNoSymbolicLinkComponents(outputRoot, finalCaptureDir);
            CaptureIntegrity.ensureNoSymbolicLinkComponents(outputRoot, captureDir);
            if (Files.exists(finalCaptureDir, LinkOption.NOFOLLOW_LINKS) || Files.exists(captureDir, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Capture folder already exists: " + finalCaptureDir); //$NON-NLS-1$
            }
            // Revalidar no worker antes de alterar a interface ou criar qualquer arquivo.
            ensureCaptureRangeElementsAvailable();
            setCaptureInteractionEnabled(false);
            setCaptureMarksVisible(false);
            prepareSensitiveImageMatcher();
            sleep(150);
            boolean htmlMessageCaptureEnabled = !"false".equalsIgnoreCase(System.getProperty("iped.chat.capture.htmlMessageV1", "true")); //$NON-NLS-1$ //$NON-NLS-2$
            if ((exactCaptureDir == null && htmlMessageCaptureEnabled)
                    || (activeRecaptureJob != null && activeRecaptureJob.targetHtmlMessageCapture())) {
                if (sensitiveImagesFolder != null && sensitiveImageMatcher == null) {
                    throw new IOException(Messages.getString("ChatCapturePanel.CensorshipReferenceUnavailable")); //$NON-NLS-1$
                }
                CaptureRunResult htmlResult = runHtmlMessageCapture(outputRoot, captureDir);
                workDirCreated = true;
                if (exactCaptureDir != null) return htmlResult;
                promoteCaptureDirectory(captureDir, finalCaptureDir);
                promoted = true;
                return new CaptureRunResult(htmlResult.status, finalCaptureDir);
            }
            CaptureIntegrity.createDirectoriesSafe(outputRoot, captureDir);
            workDirCreated = true;
            Path screenshotsDir = captureDir.resolve("screenshots"); //$NON-NLS-1$
            Path attachmentsDir = captureDir.resolve("anexos"); //$NON-NLS-1$
            CaptureIntegrity.createDirectoriesSafe(captureDir, screenshotsDir);
            CaptureIntegrity.createDirectoriesSafe(captureDir, attachmentsDir);
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
                CaptureFrame frame = new CaptureFrame(i, captureDir.relativize(framePath).toString(), plan.firstEligibleMessageId,
                        plan.lastCompleteMessageId != null ? plan.lastCompleteMessageId : plan.lastVisibleMessageId, plan.firstVisibleMessageIdRaw,
                        plan.residualTopBlockId, plan.oversizedContinuation, plan.exportDocumentTop, plan.exportDocumentBottom, plan.cropTopCss,
                        hashes, plan.imageWidth, plan.imageHeight, plan.blocks);
                frames.add(frame);
                currentCaptureFrames.add(frame);
                ExportedFile frameExport = hashFile(framePath, exported, "screenshots/" + framePath.getFileName().toString(), null, captureSourceItem); //$NON-NLS-1$
                frame.pngSha256 = frameExport.sha256;
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
            // Unico momento em que as anotacoes da sessao vao para o disco.
            // Sem anotacoes selecionadas nada e gravado, preservando o
            // manifesto v1 e a ausencia de anotacoes no resultado.
            if (pendingAnnotations != null && !pendingAnnotations.isEmpty()) {
                annotationStore.persistOrThrow();
            }
            CaptureSourceMetadata sourceMetadata = readCaptureSourceMetadata();
            Path finalReportDir = (exactCaptureDir == null ? finalCaptureDir : captureDir).toAbsolutePath().normalize();
            for (ExportedFile exportedFile : exported.values()) {
                if (exportedFile != null && exportedFile.relativePath != null && !exportedFile.relativePath.isBlank()) {
                    exportedFile.absolutePath = finalReportDir.resolve(exportedFile.relativePath).normalize().toString();
                }
            }
            writeHashes(captureDir, captureDir.resolve("hashes.txt"), exported); //$NON-NLS-1$
            writeFileListCsv(captureDir, captureDir.resolve("Lista de Arquivos.csv"), exported); //$NON-NLS-1$
            writeText(captureDir, captureDir.resolve("texto.txt"), buildCaptureText(sourceMetadata, frames)); //$NON-NLS-1$
            writeInteractiveCaptureReport(captureDir, captureDir.resolve("relatorio.htm"), status, frames, exported); //$NON-NLS-1$
            writeManifest(captureDir, captureDir.resolve("manifest.json"), status, frames, exported, sourceMetadata); //$NON-NLS-1$
            String whatsappJson = buildWhatsAppCoordinatesJson(status, frames, exported, sourceMetadata);
            writeText(captureDir, captureDir.resolve("whatsapp-coordinates.json"), whatsappJson); //$NON-NLS-1$
            writeWhatsAppHtml(captureDir, captureDir.resolve("whatsapp.html"), whatsappJson); //$NON-NLS-1$
            writeArtifactInventory(captureDir);
            if (exactCaptureDir == null) {
                promoteCaptureDirectory(captureDir, finalCaptureDir);
                promoted = true;
                return new CaptureRunResult(status, finalCaptureDir);
            }
            return new CaptureRunResult(status, captureDir);
        } catch (Exception e) {
            if (captureDir != null && Files.exists(captureDir, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.warn("Capture staging failed (work area created={})", workDirCreated, e); //$NON-NLS-1$
            }
            try {
                if (captureDir != null && (exactCaptureDir == null || workDirCreated)) deleteDirectoryIfExists(captureDir);
                if (promoted && finalCaptureDir != null) deleteDirectoryIfExists(finalCaptureDir);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        } finally {
            clearSensitiveBlur();
            closeSensitiveImageMatcher();
            sensitiveBlurHashes.clear();
            pendingAnnotations = null;
            pendingLink = null;
            currentCaptureOutputRoot = null;
            currentCaptureFrames.clear();
        }
    }

    @FunctionalInterface
    private interface PrintWriterBody {
        void write(PrintWriter out) throws IOException;
    }

    private static void writeAtomicallyWithPrinter(Path root, Path destination, long maxBytes, PrintWriterBody body) throws IOException {
        StringWriter buffer = new StringWriter(4096);
        try (PrintWriter out = new PrintWriter(buffer)) {
            body.write(out);
        }
        CaptureIntegrity.writeAtomically(root, destination, buffer.toString(), maxBytes);
    }

    private void promoteCaptureDirectory(Path staging, Path destination) throws IOException {
        if (CaptureIntegrity.isLinkOrReparse(staging) || CaptureIntegrity.isLinkOrReparse(destination)) {
            throw new IOException("Symbolic links or junctions are not allowed during capture promotion"); //$NON-NLS-1$
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("A pasta de destino da captura já existe: " + destination); //$NON-NLS-1$
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, destination);
        }
    }

    private CaptureRunResult runHtmlMessageCapture(Path outputRoot, Path captureDir) throws Exception {
        String script = "(function(startId,endId){var all=[].slice.call(document.querySelectorAll('div.linha[id]')).filter(function(n){return /^-?\\d+(?:_[0-9a-zA-Z:._-]+)?$/.test(n.id)}),theme=document.querySelector('style'),top=document.getElementById('topbar'),header=top&&top.cloneNode(true);if(!theme||!header||theme.textContent.length>2097152)return JSON.stringify({error:'theme_unavailable'});header.id='capture-topbar';[header].concat([].slice.call(header.querySelectorAll('*'))).forEach(function(n){[].slice.call(n.attributes||[]).forEach(function(a){var k=a.name.toLowerCase(),v=a.value||'';if(k.indexOf('on')===0||k==='style'||k==='href'||k==='srcset'||k==='srcdoc'||k==='action'||k==='formaction'||(k==='id'&&n!==header)||(k==='src'&&!/^data:image\\/(png|jpe?g|gif|webp);base64,/i.test(v)))n.removeAttribute(a.name);});});" //$NON-NLS-1$
                + "var ids=all.map(function(n){return n.id}),startMatches=ids.filter(function(x){return x===startId}).length;if(startMatches===0)return JSON.stringify({error:'start_marker_missing'});if(startMatches!==1)return JSON.stringify({error:'start_marker_ambiguous'});var endMatches=ids.filter(function(x){return x===endId}).length;if(endMatches===0)return JSON.stringify({error:'end_marker_missing'});if(endMatches!==1)return JSON.stringify({error:'end_marker_ambiguous'});" //$NON-NLS-1$
                + "var resolvedStart=startId,a=ids.indexOf(startId),b=ids.indexOf(endId);if(a>b)return JSON.stringify({error:'marker_order_invalid'});if(b-a+1>" + MAX_HTML_MESSAGE_COUNT + ")return JSON.stringify({error:'message_count_limit'});var rows=[],payloadBytes=0,seenMedia={};" //$NON-NLS-1$ //$NON-NLS-2$
                + "for(var i=a;i<=b;i++){var src=all[i],clone=src.cloneNode(true),resources=[],resourceByHash={},media=[],resourceError='';" //$NON-NLS-1$
                + "[].slice.call(clone.querySelectorAll('a[onclick]')).forEach(function(link){var click=link.getAttribute('onclick')||'';if(click.indexOf('app.open')<0)return;var match=click.match(/(?:sha-256|hash):([0-9a-f]{64})/i);if(!match){resourceError='';return;}var hash=match[1].toLowerCase(),token=resourceByHash[hash];if(!token){token='r'+resources.length;resourceByHash[hash]=token;resources.push({token:token,sha256:hash,label:(link.innerText||link.textContent||'').replace(/\\s+/g,' ').trim().substring(0,512)});}link.setAttribute('data-capture-link-token',token);});" //$NON-NLS-1$
                + "if(resourceError)return JSON.stringify({error:resourceError,id:src.id});" //$NON-NLS-1$
                + "var nodes=[clone].concat([].slice.call(clone.querySelectorAll('[src]')));nodes.forEach(function(node){var value=node.getAttribute&&node.getAttribute('src');if(!value)return;if(/^data:(image|audio|video)\\//i.test(value)){media.push(value);return;}var link=node.closest?node.closest('a[data-capture-link-token]'):null;if(!link){node.removeAttribute('src');node.removeAttribute('srcset');node.removeAttribute('poster');resourceError='';return;}node.removeAttribute('src');node.setAttribute('data-capture-src-token',link.getAttribute('data-capture-link-token'));});" //$NON-NLS-1$
                + "if(resourceError)return JSON.stringify({error:resourceError,id:src.id});var unsupportedLink=[].slice.call(clone.querySelectorAll('a')).some(function(x){var h=x.getAttribute('href')||'',click=x.getAttribute('onclick')||'';return /\\.(pdf|docx?|xlsx?|pptx?|zip|rar|7z|mp3|mp4|opus|ogg|wav)(?:$|[?#])/i.test(h)&&!x.hasAttribute('data-capture-link-token')||/javascript:open(?:Audio|Video|File)/i.test(h)});if(unsupportedLink){/* unsupported attachment skipped, continue capture */}" //$NON-NLS-1$
                + "clone.querySelectorAll('script,template,form,iframe,object,embed,meta,link,base,style,svg,math,input,button,select,textarea').forEach(function(x){x.remove()});" //$NON-NLS-1$
                + "[clone].concat([].slice.call(clone.querySelectorAll('*'))).forEach(function(n){[].slice.call(n.attributes||[]).forEach(function(at){var k=at.name.toLowerCase();if(k.indexOf('on')===0||k==='id'||k==='style'||k==='srcdoc'||k==='href'||k==='srcset'||k==='poster'||k==='action'||k==='formaction'||k==='xlink:href')n.removeAttribute(at.name);});});" //$NON-NLS-1$
                + "var cls=(src.className||'').toString(),txt=(src.innerText||src.textContent||'').replace(/\\s+/g,' ').trim(),timeEl=src.querySelector('.time,.timestamp,[data-time]'),time=(timeEl?(timeEl.innerText||timeEl.textContent||''):'')||src.getAttribute('data-time')||'',senderEl=src.querySelector('span.name,span.name_call,.sender,.author,span.from'),sender=senderEl?(senderEl.innerText||senderEl.textContent||''):'';if(txt.length>" + MAX_HTML_MESSAGE_TEXT_CHARS + ")return JSON.stringify({error:'message_text_limit',id:src.id});payloadBytes+=clone.outerHTML.length+txt.length+resources.length*128+media.reduce(function(n,x){if(seenMedia[x])return n;seenMedia[x]=1;return n+x.length},0);if(payloadBytes>" + MAX_HTML_MESSAGE_CAPTURE_BYTES + ")return JSON.stringify({error:'capture_size_limit',id:src.id});var prev=src.previousElementSibling,separator='';if(prev&&prev.classList.contains('linha')&&(!prev.id||!/^-?\\d+(?:_[0-9a-zA-Z:._-]+)?$/.test(prev.id))){var sep=prev.cloneNode(true);[sep].concat([].slice.call(sep.querySelectorAll('*'))).forEach(function(n){[].slice.call(n.attributes||[]).forEach(function(at){var k=at.name.toLowerCase();if(k.indexOf('on')===0||k==='id'||k==='style'||k==='href'||k==='src'||k==='srcset'||k==='srcdoc')n.removeAttribute(at.name);});});separator=sep.outerHTML;}var direction=/outgoing/i.test(cls)?'outgoing':/incoming/i.test(cls)?'incoming':'unknown',type=/systemmessage/i.test(cls)?'system':(media.length||resources.length)?'attachment':'text';rows.push({domId:src.id,order:i-a,text:txt,classes:cls,timestamp:time.replace(/\\s+/g,' ').trim(),sender:sender.replace(/\\s+/g,' ').trim(),direction:direction,type:type,html:clone.outerHTML,separatorBefore:separator,media:media,resources:resources});}" //$NON-NLS-1$ //$NON-NLS-2$
                + "return JSON.stringify({rows:rows,resolvedStart:resolvedStart,themeCss:theme.textContent,header:header.outerHTML});})(" + js(captureStartMessageId) + "," + js(captureEndMessageId) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        Object raw = executeScriptAndWait(script);
        if (!(raw instanceof String)) throw new IOException("Falha ao extrair mensagens do DOM do IPED."); //$NON-NLS-1$
        JSONObject extracted = new JSONObject((String) raw);
        if (extracted.has("error")) throw new IOException("Captura HTML interrompida: " + extracted.optString("error") + " " + extracted.optString("id")); //$NON-NLS-1$ //$NON-NLS-2$
        JSONArray rows = extracted.optJSONArray("rows"); //$NON-NLS-1$
        if (rows == null || rows.isEmpty()) throw new IOException("O intervalo não contém mensagens elegíveis."); //$NON-NLS-1$
        if (rows.length() > MAX_HTML_MESSAGE_COUNT) throw new IOException("O intervalo ultrapassa 25.000 mensagens."); //$NON-NLS-1$
        String resolvedStartMessageId = extracted.optString("resolvedStart", ""); //$NON-NLS-1$
        if (resolvedStartMessageId.isBlank() || !resolvedStartMessageId.equals(rows.getJSONObject(0).optString("domId"))) { //$NON-NLS-1$
            throw new IOException("Não foi possível registrar a mensagem correspondente ao marcador inicial."); //$NON-NLS-1$
        }
        String themeCss = sanitizeCaptureThemeCss(extracted.optString("themeCss", "")); //$NON-NLS-1$
        String headerHtml = validateCaptureHeaderHtml(extracted.optString("header", "")); //$NON-NLS-1$
        if (CaptureIntegrity.isLinkOrReparse(outputRoot)) {
            throw new IOException("Symbolic links or junctions are not allowed as capture output root"); //$NON-NLS-1$
        }
        Path staging = outputRoot.resolve(".__html_capture_" + java.util.UUID.randomUUID().toString().replace("-", "")); //$NON-NLS-1$ //$NON-NLS-2$
        CaptureIntegrity.createDirectoriesSafe(outputRoot, staging);
        boolean promoted = false;
        RecaptureJob migrationJob = activeRecaptureJob;
        if (migrationJob != null && migrationJob.legacyCapture && migrationJob.resetLegacyRecords) {
            migrationJob.reusableAttachmentStaging = staging;
            migrationJob.stagedReusableAttachmentPaths.clear();
        }
        try {
        JSONArray records = new JSONArray();
        Map<String, byte[]> attachments = new LinkedHashMap<>();
        Map<String, String> attachmentModals = new LinkedHashMap<>();
        Map<String, Integer> duplicateOrdinals = new LinkedHashMap<>();
        Set<String> seenMessageIds = new HashSet<>();
        final SensitiveImageMatcher censor = sensitiveImageMatcher;
        final int censorBlurPx = sensitiveBlurCssPixels();
        final int censorLevel = normalizeBlurPercent(sensitiveBlurPercent);
        final int censorSimilarity = sensitiveSimilarityPercent;
        final Map<String, Boolean> censorVerdicts = new HashMap<>();
        final Map<String, Set<String>> censoredKeysBySha = new TreeMap<>();
        int censoredMessageCount = 0;
        long censorStartNanos = System.nanoTime();
        CaptureSourceMetadata sourceMetadata = readCaptureSourceMetadata();
        String conversationIdentity = safe(sourceMetadata.name) + "\n" + safe(sourceMetadata.path) + "\n" + safe(currentChatTitle()); //$NON-NLS-1$
        String conversationKey = CaptureIntegrity.sha256Text(conversationIdentity);
        long aggregateAttachmentBytes = 0;
        final long maxAggregateAttachmentBytes = MAX_HTML_MESSAGE_ATTACHMENT_BYTES * 6L;
        Set<String> uniqueAttachmentPaths = new HashSet<>();
        Set<String> inlineMediaSha256 = new HashSet<>();
        Map<String, String> attachmentPathBySha = new HashMap<>();
        Map<String, DecodedHtmlAttachment> resolvedCaseAttachments = new HashMap<>();
        Set<String> skippedCaseAttachments = new HashSet<>();
        String annotations = migrationJob != null && migrationJob.migrateLegacyToHtml ? "" : buildAnnotationsJson(); //$NON-NLS-1$
        Map<String, JSONObject> annotationsByMessage = new HashMap<>();
        if (!annotations.isBlank()) {
            JSONArray annotationItems = new JSONObject(annotations).optJSONArray("items"); //$NON-NLS-1$
            for (int i = 0; annotationItems != null && i < annotationItems.length(); i++) {
                JSONObject item = annotationItems.optJSONObject(i);
                if (item != null) annotationsByMessage.put(item.optString("messageId"), item); //$NON-NLS-1$
            }
        }
        StringBuilder page = new StringBuilder(128 * 1024);
        page.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"") //$NON-NLS-1$
                .append(CAPTURE_CSP).append("\"><title>WhatsApp</title><style>").append(themeCss) //$NON-NLS-1$
                .append("</style><style>").append(captureViewerCss()); //$NON-NLS-1$
        final int viewerCssEnd = page.length();
        page.append("</style></head><body>") //$NON-NLS-1$
                .append(headerHtml).append("<div id=\"capture-conversation\">"); //$NON-NLS-1$
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i); JSONObject record = new JSONObject();
            String domId = row.getString("domId"); //$NON-NLS-1$
            if (!seenMessageIds.add(domId)) throw new IOException("ID DOM duplicado no intervalo selecionado: " + domId); //$NON-NLS-1$
            JSONArray attachmentList = new JSONArray();
            String fragment = row.getString("html"); //$NON-NLS-1$
            JSONArray media = row.optJSONArray("media"); //$NON-NLS-1$
            Set<String> messageAttachmentPaths = new HashSet<>();
            Set<String> messageCensoredSha = new HashSet<>();
            Set<String> contentTypes = new HashSet<>();
            Map<String, String> dataUriToRelative = new LinkedHashMap<>();
            Map<String, String[]> tokenResources = new LinkedHashMap<>();
            if (media != null) for (int mediaIndex = 0; mediaIndex < media.length(); mediaIndex++) {
                String dataUri = media.optString(mediaIndex); //$NON-NLS-1$
                DecodedHtmlAttachment attachment;
                try {
                    attachment = decodeHtmlAttachment(dataUri);
                } catch (IOException skipped) {
                    dataUriToRelative.put(dataUri, ""); //$NON-NLS-1$
                    LOGGER.warn("Anexo inline ignorado na mensagem {}: {}", domId, skipped.getMessage()); //$NON-NLS-1$
                    continue;
                }
                String relative = "attachments/" + attachment.sha256 + "." + attachment.extension; //$NON-NLS-1$ //$NON-NLS-2$
                boolean alreadyCounted = uniqueAttachmentPaths.contains(relative);
                if (!alreadyCounted && (aggregateAttachmentBytes + attachment.size > maxAggregateAttachmentBytes
                        || rows.length() + uniqueAttachmentPaths.size() + 1L > MAX_HTML_MESSAGE_COUNT)) {
                    dataUriToRelative.put(dataUri, ""); //$NON-NLS-1$
                    LOGGER.warn("Anexo inline ignorado na mensagem {} por limite total da captura: {}", domId, relative); //$NON-NLS-1$
                    continue;
                }
                if (!alreadyCounted) {
                    uniqueAttachmentPaths.add(relative);
                    aggregateAttachmentBytes += attachment.size;
                }
                inlineMediaSha256.add(attachment.sha256.toLowerCase(Locale.ROOT));
                attachmentPathBySha.putIfAbsent(attachment.sha256.toLowerCase(Locale.ROOT), relative);
                if (isCensoredAttachment(censor, attachment, relative, staging, censorVerdicts)) messageCensoredSha.add(attachment.sha256);
                if (attachment.bytes != null) attachments.putIfAbsent(relative, attachment.bytes);
                if (messageAttachmentPaths.add(relative)) attachmentList.put(new JSONObject().put("path", relative).put("mime", attachment.mime) //$NON-NLS-1$ //$NON-NLS-2$
                        .put("size", attachment.size).put("sha256", attachment.sha256)); //$NON-NLS-1$ //$NON-NLS-2$
                dataUriToRelative.put(dataUri, relative);
                contentTypes.add(htmlContentKind(attachment.mime));
                attachmentModals.putIfAbsent(relative, renderCaptureAttachmentModal(relative, attachment.mime,
                        attachment.size, attachment.sha256, safeAttachmentName("", attachment.name))); //$NON-NLS-1$
            }
            JSONArray resources = row.optJSONArray("resources"); //$NON-NLS-1$
            if (resources != null) for (int resourceIndex = 0; resourceIndex < resources.length(); resourceIndex++) {
                JSONObject resource = resources.optJSONObject(resourceIndex);
                if (resource == null) throw new IOException("Referência de anexo inválida na mensagem " + domId); //$NON-NLS-1$
                String token = resource.optString("token", ""); //$NON-NLS-1$
                String sha256 = resource.optString("sha256", ""); //$NON-NLS-1$
                if (!token.matches("r[0-9]{1,5}") || !sha256.matches("(?i)[0-9a-f]{64}")) throw new IOException("Referência/hash de anexo inválido na mensagem " + domId); //$NON-NLS-1$ //$NON-NLS-2$
                String normalizedSha256 = sha256.toLowerCase(Locale.ROOT);
                if (skippedCaseAttachments.contains(normalizedSha256)) {
                    continue;
                }
                DecodedHtmlAttachment attachment = resolvedCaseAttachments.get(normalizedSha256);
                if (attachment == null) {
                    try {
                        attachment = resolveCaseAttachment(normalizedSha256);
                        resolvedCaseAttachments.put(normalizedSha256, attachment);
                    } catch (IOException skipped) {
                        skippedCaseAttachments.add(normalizedSha256);
                        LOGGER.warn("Anexo relacionado ignorado na mensagem {} (sha256={}): {}", domId, normalizedSha256, skipped.getMessage()); //$NON-NLS-1$
                        continue;
                    }
                }
                String relative = "attachments/" + attachment.sha256 + "." + attachment.extension; //$NON-NLS-1$ //$NON-NLS-2$
                boolean alreadyCounted = uniqueAttachmentPaths.contains(relative);
                if (!alreadyCounted && (aggregateAttachmentBytes + attachment.size > maxAggregateAttachmentBytes
                        || rows.length() + uniqueAttachmentPaths.size() + 1L > MAX_HTML_MESSAGE_COUNT)) {
                    if (migrationJob != null && migrationJob.stagedReusableAttachmentPaths.remove(relative)) {
                        Files.deleteIfExists(staging.resolve(relative));
                    }
                    resolvedCaseAttachments.remove(normalizedSha256);
                    skippedCaseAttachments.add(normalizedSha256);
                    LOGGER.warn("Anexo relacionado ignorado na mensagem {} por limite total da captura: {}", domId, relative); //$NON-NLS-1$
                    continue;
                }
                if (!alreadyCounted) {
                    uniqueAttachmentPaths.add(relative);
                    aggregateAttachmentBytes += attachment.size;
                }
                attachmentPathBySha.putIfAbsent(attachment.sha256.toLowerCase(Locale.ROOT), relative);
                if (isCensoredAttachment(censor, attachment, relative, staging, censorVerdicts)) messageCensoredSha.add(attachment.sha256);
                if (attachment.bytes != null) attachments.putIfAbsent(relative, attachment.bytes);
                if (messageAttachmentPaths.add(relative)) attachmentList.put(new JSONObject().put("path", relative).put("mime", attachment.mime) //$NON-NLS-1$ //$NON-NLS-2$
                        .put("size", attachment.size).put("sha256", attachment.sha256)); //$NON-NLS-1$ //$NON-NLS-2$
                String label = safeAttachmentName(resource.optString("label", ""), attachment.name); //$NON-NLS-1$
                tokenResources.put(token, new String[] { attachment.sha256, label, relative });
                attachmentModals.putIfAbsent(relative, renderCaptureAttachmentModal(relative, attachment.mime,
                        attachment.size, attachment.sha256, label));
                contentTypes.add(htmlContentKind(attachment.mime));
            }
            String messageType = row.optString("type"); //$NON-NLS-1$
            if (!"system".equals(messageType)) messageType = contentTypes.isEmpty() ? "text" : contentTypes.size() == 1 ? contentTypes.iterator().next() : "mixed"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String natural = row.optString("timestamp") + "\n" + row.optString("sender") + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + row.optString("direction") + "\n" + messageType; //$NON-NLS-1$ //$NON-NLS-2$
            int duplicateOrdinal = duplicateOrdinals.merge(natural, 1, Integer::sum) - 1;
            record.put("domId", domId).put("order", i).put("timestamp", row.optString("timestamp")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .put("sender", row.optString("sender")).put("direction", row.optString("direction")) //$NON-NLS-1$ //$NON-NLS-2$
                    .put("type", messageType).put("duplicateOrdinal", duplicateOrdinal) //$NON-NLS-1$ //$NON-NLS-2$
                    .put("text", row.optString("text")).put("classes", row.optString("classes")).put("attachments", attachmentList); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String key = CaptureIntegrity.sha256Text(conversationKey + "\n" + domId + "\n" + natural + "\n" + duplicateOrdinal); //$NON-NLS-1$
            if (!messageCensoredSha.isEmpty()) {
                censoredMessageCount++;
                for (String sha : messageCensoredSha) censoredKeysBySha.computeIfAbsent(sha, k -> new TreeSet<>()).add(key);
            }
            record.put("key", key).put("messageHash", htmlMessageHash(record)); //$NON-NLS-1$ //$NON-NLS-2$
            records.put(record);
            fragment = sanitizeCaptureMessageFragment(fragment, tokenResources, dataUriToRelative, key);
            fragment = linkInlineCaptureImages(fragment);
            fragment = CaptureCensorship.mark(fragment, messageCensoredSha);
            fragment = replaceInlineCapturePlayers(fragment, attachmentModals);
            String separatorBefore = validateCaptureSeparatorHtml(row.optString("separatorBefore", "")); //$NON-NLS-1$
            if (!separatorBefore.isEmpty()) page.append(separatorBefore);
            page.append(fragment);
            JSONObject annotation = annotationsByMessage.get(domId);
            if (annotation != null) page.append(htmlAnnotationBlock(annotation));
        }
        page.append("</div>"); //$NON-NLS-1$
        for (String modal : attachmentModals.values()) page.append(modal);
        page.append("<script>").append(CAPTURE_MEDIA_STOP_SCRIPT).append("</script></body></html>"); //$NON-NLS-1$
        if (censoredMessageCount > 0) page.insert(viewerCssEnd, CaptureCensorship.css(censorBlurPx));
        if (censor != null) {
            LOGGER.info("Censura html-message-v1: {} imagem(ns) verificada(s), {} casada(s), {} mensagem(ns), {} ms", //$NON-NLS-1$
                    censorVerdicts.size(), censoredKeysBySha.size(), censoredMessageCount,
                    (System.nanoTime() - censorStartNanos) / 1_000_000L);
        }
        String sourceFingerprint = captureSourceFingerprint();
        String capturedHtml = page.toString();
        String capturedHtmlSha256 = CaptureIntegrity.sha256Text(capturedHtml);
        JSONObject publicSourceMetadata = new JSONObject()
                .put("name", safe(sourceMetadata.name)).put("size", sourceMetadata.size) //$NON-NLS-1$ //$NON-NLS-2$
                .put("type", safe(sourceMetadata.type)).put("itemHash", safe(sourceMetadata.hash)) //$NON-NLS-1$ //$NON-NLS-2$
                .put("deleted", sourceMetadata.deleted); //$NON-NLS-1$
        if (sourceMetadata.sha256 != null && !sourceMetadata.sha256.isBlank()) {
            publicSourceMetadata.put("itemSha256", sourceMetadata.sha256.toLowerCase(Locale.ROOT)); //$NON-NLS-1$
        }
        String annotationsSha256 = annotations.isBlank() ? "" : CaptureIntegrity.sha256Text(annotations); //$NON-NLS-1$
        JSONArray publicMessageHashes = new JSONArray();
        for (int i = 0; i < records.length(); i++) {
            JSONObject message = records.getJSONObject(i);
            publicMessageHashes.put(new JSONObject().put("domId", message.optString("domId")).put("key", message.optString("key")).put("messageHash", message.optString("messageHash"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        String legacyAnnotationsJson = ""; //$NON-NLS-1$
        JSONObject migrationMetadata = null;
        if (migrationJob != null && migrationJob.migrateLegacyToHtml && !migrationJob.resetLegacyRecords) {
            legacyAnnotationsJson = readLegacyAnnotationsForMigration(migrationJob);
            migrationMetadata = buildLegacyMigrationMetadata(migrationJob, legacyAnnotationsJson);
            CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("migration.json"), migrationMetadata.toString(2)); //$NON-NLS-1$
            if (!legacyAnnotationsJson.isBlank()) {
                CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("legacy-annotations.json"), legacyAnnotationsJson); //$NON-NLS-1$
            }
        }
        JSONObject manifest = new JSONObject().put("schemaVersion", 1).put("captureFormat", "html-message-v1").put("presentationVersion", 2) //$NON-NLS-1$ //$NON-NLS-2$
                .put("pluginVersion", CHAT_CAPTURE_PLUGIN_VERSION).put("ipedVersion", "4.3.1") //$NON-NLS-1$ //$NON-NLS-2$
                .put("status", "completed").put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString()).put("sourceFingerprint", sourceFingerprint).put("conversationKey", conversationKey) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .put("sourceMetadata", publicSourceMetadata) //$NON-NLS-1$
                .put("anchors", new JSONObject().put("start", resolvedStartMessageId).put("end", captureEndMessageId)) //$NON-NLS-1$ //$NON-NLS-2$
                .put("messageCount", records.length()).put("state", "complete").put("messagesFile", "messages.json") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("messageHashes", publicMessageHashes) //$NON-NLS-1$
                .put("limits", new JSONObject().put("maxAttachmentBytes", MAX_HTML_MESSAGE_ATTACHMENT_BYTES).put("maxCaptureBytes", MAX_HTML_MESSAGE_CAPTURE_BYTES).put("maxMessages", MAX_HTML_MESSAGE_COUNT).put("maxSourceHtmlBytes", 16L * 1024L * 1024L)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("htmlSha256", capturedHtmlSha256).put("annotationsSha256", annotationsSha256) //$NON-NLS-1$ //$NON-NLS-2$
                .put("reconciliationSha256", "") //$NON-NLS-1$ //$NON-NLS-2$
                .put("captureRootHash", htmlCaptureRootHash(records, capturedHtmlSha256, annotationsSha256, "", //$NON-NLS-1$ //$NON-NLS-2$
                        conversationKey, sourceFingerprint, publicSourceMetadata, "4.3.1", CHAT_CAPTURE_PLUGIN_VERSION, resolvedStartMessageId, captureEndMessageId)); //$NON-NLS-1$
        if (censor != null) {
            manifest.put("censorship", CaptureCensorship.manifestRecord(censorSimilarity, censorLevel, censorBlurPx, //$NON-NLS-1$
                    censor.referenceImageCount(), censoredMessageCount, censoredKeysBySha));
        }
        if (migrationMetadata != null) {
            manifest.put("migration", migrationMetadata).put("migrationFile", "migration.json"); //$NON-NLS-1$ //$NON-NLS-2$
            manifest.put("migrationSha256", CaptureIntegrity.sha256(staging.resolve("migration.json"))); //$NON-NLS-1$
        }
        Path activeOriginFolder = this.originAuditFolder;
        Path originBindingFilePath = null;
        if (activeOriginFolder != null) {
            OriginCustodyBinder.OriginBindingResult originResult = OriginCustodyBinder.buildOriginBinding(
                    activeOriginFolder, this.originParentIpedFolder, publicSourceMetadata, records,
                    inlineMediaSha256, attachmentPathBySha,
                    captureDir.getFileName() == null ? "" : captureDir.getFileName().toString(),
                    capturedHtml); //$NON-NLS-1$
            originBindingFilePath = staging.resolve(OriginCustodyBinder.ORIGIN_BINDING_FILE);
            CaptureIntegrity.writeAtomically(outputRoot, originBindingFilePath, originResult.fullBinding.toString(2));
            manifest.put("originBinding", originResult.manifestSummary) //$NON-NLS-1$
                    .put("originBindingFile", OriginCustodyBinder.ORIGIN_BINDING_FILE) //$NON-NLS-1$
                    .put("originBindingSha256", CaptureIntegrity.sha256(originBindingFilePath)); //$NON-NLS-1$
        }
        try {
            String messagesJson = new JSONObject().put("messages", records).toString(2); //$NON-NLS-1$
            long storedBytes = capturedHtml.getBytes(StandardCharsets.UTF_8).length + messagesJson.getBytes(StandardCharsets.UTF_8).length;
            for (byte[] attachment : attachments.values()) storedBytes += attachment.length;
            if (originBindingFilePath != null) {
                storedBytes += CaptureIntegrity.safeRegularFileSize(originBindingFilePath);
            }
            if (migrationJob != null) for (String relative : migrationJob.stagedReusableAttachmentPaths) {
                storedBytes += CaptureIntegrity.safeRegularFileSize(staging.resolve(relative));
            }
            if (storedBytes > MAX_HTML_MESSAGE_CAPTURE_BYTES) throw new IOException("Captura HTML ultrapassa o limite total de 64 MiB."); //$NON-NLS-1$
            for (Map.Entry<String, byte[]> attachment : attachments.entrySet()) {
                Path target = CaptureIntegrity.resolveContained(staging, attachment.getKey());
                CaptureIntegrity.writeBytesAtomically(outputRoot, target, attachment.getValue(), MAX_HTML_MESSAGE_ATTACHMENT_BYTES);
            }
            CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("messages.json"), messagesJson, MAX_HTML_MESSAGE_CAPTURE_BYTES); //$NON-NLS-1$
            if (!annotations.isBlank()) {
                CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("annotations.json"), annotations); //$NON-NLS-1$
            }
            CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("whatsapp.html"), capturedHtml, MAX_HTML_MESSAGE_CAPTURE_BYTES); //$NON-NLS-1$
            CaptureIntegrity.writeAtomically(outputRoot, staging.resolve("manifest.json"), manifest.toString(2)); //$NON-NLS-1$
            CaptureIntegrity.ensureNoSymbolicLinkComponents(outputRoot, captureDir);
            promoteCaptureDirectory(staging, captureDir);
            promoted = true;
            writeArtifactInventory(captureDir);
            return new CaptureRunResult("completed", captureDir); //$NON-NLS-1$
        } catch (Exception e) { deleteDirectoryIfExists(staging); if (promoted) deleteDirectoryIfExists(captureDir); throw e; }
        } catch (Exception failure) {
            if (!promoted) {
                try { deleteDirectoryIfExists(staging); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        } finally {
            if (migrationJob != null) {
                migrationJob.reusableAttachmentStaging = null;
                migrationJob.stagedReusableAttachmentPaths.clear();
            }
        }
    }

    private static final Pattern CSS_FORBIDDEN_CONTENT_PATTERN = Pattern.compile(
            "(?is)</style|@import|expression\\s*\\(|(?:^|[;{])\\s*behavior\\s*:|-moz-binding|javascript\\s*:|vbscript\\s*:"); //$NON-NLS-1$
    private static final Pattern CSS_HEX_ESCAPE_PATTERN = Pattern.compile(
            "\\\\([0-9a-fA-F]{1,6})(?:\\r\\n|[ \\t\\r\\n\\f])?"); //$NON-NLS-1$
    private static final Set<String> BLOCKED_SVG_ELEMENTS = new HashSet<>(Arrays.asList(
            "script", "foreignobject", "iframe", "object", "embed", "style", "animate", "animatemotion", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
            "animatetransform", "set", "mpath")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    private static String normalizeCssForSecurityCheck(String css) throws IOException {
        if (css.indexOf('\0') >= 0) {
            throw new IOException("CSS contém byte nulo."); //$NON-NLS-1$
        }
        String noComments = Pattern.compile("(?s)/\\*.*?\\*/").matcher(css).replaceAll(" "); //$NON-NLS-1$ //$NON-NLS-2$
        Matcher hex = CSS_HEX_ESCAPE_PATTERN.matcher(noComments);
        StringBuffer decoded = new StringBuffer(noComments.length());
        while (hex.find()) {
            int code = Integer.parseInt(hex.group(1), 16);
            if (code <= 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                throw new IOException("Escape CSS inválido."); //$NON-NLS-1$
            }
            hex.appendReplacement(decoded, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        hex.appendTail(decoded);
        StringBuilder out = new StringBuilder(decoded.length());
        for (int i = 0; i < decoded.length(); i++) {
            char ch = decoded.charAt(i);
            if (ch == '\\') {
                if (i + 1 < decoded.length()) {
                    char next = decoded.charAt(++i);
                    if (next == '\0') {
                        throw new IOException("Escape CSS inválido."); //$NON-NLS-1$
                    }
                    out.append(next);
                } else {
                    out.append(ch);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String sanitizeCaptureThemeCss(String css) throws IOException {
        if (css == null || css.isBlank() || css.getBytes(StandardCharsets.UTF_8).length > 2L * 1024L * 1024L) {
            throw new IOException("A folha de estilo do IPED excede o limite ou contém regra ativa não permitida."); //$NON-NLS-1$
        }
        String normalized = normalizeCssForSecurityCheck(css);
        if (CSS_FORBIDDEN_CONTENT_PATTERN.matcher(css).find()
                || CSS_FORBIDDEN_CONTENT_PATTERN.matcher(normalized).find()) {
            throw new IOException("A folha de estilo do IPED excede o limite ou contém regra ativa não permitida."); //$NON-NLS-1$
        }
        Pattern urlPattern = Pattern.compile("(?is)url\\(\\s*(['\\\"]?)(.*?)\\1\\s*\\)"); //$NON-NLS-1$
        String withoutUrls = urlPattern.matcher(css).replaceAll(""); //$NON-NLS-1$
        String withoutNormUrls = urlPattern.matcher(normalized).replaceAll(""); //$NON-NLS-1$
        if (Pattern.compile("(?i)url\\s*\\(").matcher(withoutUrls).find()
                || Pattern.compile("(?i)url\\s*\\(").matcher(withoutNormUrls).find()) {
            throw new IOException("URL CSS malformada na folha do IPED."); //$NON-NLS-1$
        }
        Matcher normUrls = urlPattern.matcher(normalized);
        while (normUrls.find()) {
            if (!Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\\+xml));base64,([a-z0-9+/=\\r\\n]+)$") //$NON-NLS-1$
                    .matcher(normUrls.group(2).trim()).matches()) {
                throw new IOException("A folha CSS contém URL que não é imagem base64 local."); //$NON-NLS-1$
            }
        }
        Matcher urls = urlPattern.matcher(css);
        long aggregate = 0;
        while (urls.find()) {
            String value = urls.group(2).trim();
            Matcher image = Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\\+xml));base64,([a-z0-9+/=\\r\\n]+)$").matcher(value); //$NON-NLS-1$
            if (!image.matches()) throw new IOException("A folha CSS contém URL que não é imagem base64 local."); //$NON-NLS-1$
            byte[] bytes;
            try { bytes = Base64.getMimeDecoder().decode(image.group(2)); }
            catch (IllegalArgumentException invalid) { throw new IOException("Imagem CSS possui base64 inválido.", invalid); } //$NON-NLS-1$
            if (bytes.length == 0 || bytes.length > 8L * 1024L * 1024L) throw new IOException("Imagem CSS excede 8 MiB."); //$NON-NLS-1$
            aggregate += bytes.length;
            if (aggregate > 16L * 1024L * 1024L) throw new IOException("Imagens CSS excedem 16 MiB no total."); //$NON-NLS-1$
            String mime = image.group(1).toLowerCase(Locale.ROOT);
            if ("image/svg+xml".equals(mime)) validateCaptureSvg(bytes); //$NON-NLS-1$
            else if (!captureRasterSignatureMatches(mime, bytes)) throw new IOException("Assinatura de imagem CSS incompatível com o MIME."); //$NON-NLS-1$
        }
        return css.replaceAll("#topbar\\b", "#capture-topbar").replaceAll("#conversation\\b", "#capture-conversation"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean captureRasterSignatureMatches(String mime, byte[] bytes) {
        if ("image/jpg".equals(mime)) mime = "image/jpeg"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("image/png".equals(mime)) return bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10; //$NON-NLS-1$
        if ("image/jpeg".equals(mime)) return bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff; //$NON-NLS-1$
        if ("image/gif".equals(mime)) return bytes.length >= 6 && ("GIF87a".equals(new String(bytes, 0, 6, StandardCharsets.US_ASCII)) || "GIF89a".equals(new String(bytes, 0, 6, StandardCharsets.US_ASCII))); //$NON-NLS-1$ //$NON-NLS-2$
        return "image/webp".equals(mime) && bytes.length >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII)) && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII)); //$NON-NLS-1$
    }

    private static void validateCaptureSvg(byte[] bytes) throws IOException {
        if (bytes.length > 512L * 1024L) throw new IOException("SVG CSS excede 512 KiB."); //$NON-NLS-1$
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if (Pattern.compile("(?i)<!\\s*(?:DOCTYPE|ENTITY)").matcher(xml).find()) throw new IOException("SVG com DTD ou entidade não é permitido."); //$NON-NLS-1$
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); //$NON-NLS-1$
            // Xerces 2.12.2 bundled with IPED 4.3.1 does not support JAXP ACCESS_EXTERNAL_* attributes.
            // DOCTYPE is rejected above and these parser features disable external loading without them.
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            if (document.getDocumentElement() == null || !"svg".equalsIgnoreCase(document.getDocumentElement().getLocalName() == null ? document.getDocumentElement().getTagName() : document.getDocumentElement().getLocalName())) throw new IOException("Raiz SVG inválida."); //$NON-NLS-1$
            NodeList all = document.getElementsByTagName("*"); //$NON-NLS-1$
            if (all.getLength() > 4096) throw new IOException("SVG excede 4.096 elementos."); //$NON-NLS-1$
            Pattern unsafeSvgUrl = Pattern.compile("(?is)url\\s*\\((?>\\s*(?:['\"]\\s*)?)(?!#)"); //$NON-NLS-1$
            for (int i = 0; i < all.getLength(); i++) {
                Element element = (Element) all.item(i);
                String name = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
                if (BLOCKED_SVG_ELEMENTS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("SVG contém elemento ativo."); //$NON-NLS-1$
                }
                NamedNodeMap attributes = element.getAttributes();
                for (int j = 0; j < attributes.getLength(); j++) {
                    Node attribute = attributes.item(j);
                    String attr = attribute.getNodeName().toLowerCase(Locale.ROOT), value = attribute.getNodeValue();
                    String trimmed = value == null ? "" : value.trim(); //$NON-NLS-1$
                    if (attr.startsWith("on") //$NON-NLS-1$
                            || (("href".equals(attr) || attr.endsWith(":href") || "src".equals(attr)) && !trimmed.isEmpty() && !trimmed.startsWith("#")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                            || unsafeSvgUrl.matcher(trimmed).find()) {
                        throw new IOException("SVG contém atributo de evento ou referência externa."); //$NON-NLS-1$
                    }
                    if ("style".equals(attr)) { //$NON-NLS-1$
                        String normStyle = normalizeCssForSecurityCheck(trimmed);
                        if (CSS_FORBIDDEN_CONTENT_PATTERN.matcher(trimmed).find()
                                || CSS_FORBIDDEN_CONTENT_PATTERN.matcher(normStyle).find()
                                || unsafeSvgUrl.matcher(normStyle).find()) {
                            throw new IOException("SVG contém estilo externo ou ativo."); //$NON-NLS-1$
                        }
                    }
                }
            }
        } catch (IOException e) { throw e; }
        catch (Exception invalid) { throw new IOException("SVG CSS inválido.", invalid); } //$NON-NLS-1$
    }

    private static final Set<String> ALLOWED_HEADER_HTML_TAGS = new HashSet<>(Arrays.asList(
            "div", "span", "img", "p", "b", "strong", "i", "em", "u", "small", "br", "hr", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
            "h1", "h2", "h3", "h4", "h5", "h6", "table", "tbody", "tr", "td", "th")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
    private static final Set<String> ALLOWED_SEPARATOR_HTML_TAGS = new HashSet<>(Arrays.asList(
            "div", "span", "p", "b", "strong", "i", "em", "u", "small", "br", "hr")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
    private static final Set<String> VOID_HTML_TAGS = new HashSet<>(Arrays.asList("img", "br", "hr")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final Set<String> ALLOWED_HEADER_ATTRS = new HashSet<>(Arrays.asList(
            "class", "title", "dir", "lang")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static final Set<String> ALLOWED_SEPARATOR_ATTRS = new HashSet<>(Arrays.asList(
            "class", "title", "dir", "lang")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private static String validateCaptureHeaderHtml(String header) throws IOException {
        if (header == null || header.length() > 256 * 1024) {
            throw new IOException("Cabeçalho da conversa ausente ou inválido."); //$NON-NLS-1$
        }
        String trimmed = header.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Cabeçalho da conversa ausente ou inválido."); //$NON-NLS-1$
        }
        return validateAllowedHtmlFragment(trimmed, ALLOWED_HEADER_HTML_TAGS, true,
                "Cabeçalho da conversa ausente ou inválido."); //$NON-NLS-1$
    }

    static String validateCaptureSeparatorHtml(String separator) throws IOException {
        if (separator == null || separator.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        if (separator.length() > 64 * 1024) {
            throw new IOException("Separador de data contém marcação não permitida."); //$NON-NLS-1$
        }
        return validateAllowedHtmlFragment(separator, ALLOWED_SEPARATOR_HTML_TAGS, false,
                "Separador de data contém marcação não permitida."); //$NON-NLS-1$
    }

    private static String validateAllowedHtmlFragment(String input, Set<String> allowedTags, boolean headerMode,
            String errorMessage) throws IOException {
        StringBuilder out = new StringBuilder(input.length());
        Deque<String> openTags = new ArrayDeque<>();
        int i = 0;
        int len = input.length();
        int elementCount = 0;
        boolean rootClosed = false;
        boolean foundRootTopbarId = false;
        while (i < len) {
            int lt = input.indexOf('<', i);
            if (lt < 0) {
                String tail = input.substring(i);
                if (headerMode && rootClosed && !tail.trim().isEmpty()) {
                    throw new IOException(errorMessage);
                }
                out.append(tail);
                break;
            }
            if (lt > i) {
                String text = input.substring(i, lt);
                if (headerMode && (elementCount == 0 || rootClosed) && !text.trim().isEmpty()) {
                    throw new IOException(errorMessage);
                }
                out.append(text);
            }
            if (lt + 1 >= len) {
                throw new IOException(errorMessage);
            }
            char next = input.charAt(lt + 1);
            if (next == '!' || next == '?') {
                throw new IOException(errorMessage);
            }
            int tagEnd = findHtmlTagEndQuoteSafe(input, lt + 1);
            if (tagEnd < 0) {
                throw new IOException(errorMessage);
            }
            String inside = input.substring(lt + 1, tagEnd);
            if (headerMode && rootClosed) {
                throw new IOException(errorMessage);
            }
            if (next == '/') {
                String closing = inside.substring(1).trim().toLowerCase(Locale.ROOT);
                if (!closing.matches("[a-z][a-z0-9]{0,15}") || !allowedTags.contains(closing) //$NON-NLS-1$
                        || VOID_HTML_TAGS.contains(closing) || openTags.isEmpty() || !closing.equals(openTags.pop())) {
                    throw new IOException(errorMessage);
                }
                out.append("</").append(closing).append('>'); //$NON-NLS-1$
                if (headerMode && openTags.isEmpty()) {
                    rootClosed = true;
                }
            } else {
                elementCount++;
                boolean selfClosing = inside.endsWith("/"); //$NON-NLS-1$
                String tokenBody = selfClosing ? inside.substring(0, inside.length() - 1) : inside;
                int pos = 0;
                while (pos < tokenBody.length() && Character.isLetterOrDigit(tokenBody.charAt(pos))) {
                    pos++;
                }
                String tag = tokenBody.substring(0, pos).toLowerCase(Locale.ROOT);
                if (pos == 0 || !allowedTags.contains(tag)) {
                    throw new IOException(errorMessage);
                }
                if (selfClosing && !VOID_HTML_TAGS.contains(tag)) {
                    throw new IOException(errorMessage);
                }
                if (headerMode && elementCount == 1 && !"div".equals(tag)) { //$NON-NLS-1$
                    throw new IOException(errorMessage);
                }
                String attrsPart = tokenBody.substring(pos);
                if (!attrsPart.isEmpty() && !Character.isWhitespace(attrsPart.charAt(0))) {
                    throw new IOException(errorMessage);
                }
                boolean isRootDiv = headerMode && elementCount == 1;
                StringBuilder renderedAttrs = new StringBuilder();
                boolean sawRootTopbarId = sanitizeAllowedHtmlAttributes(attrsPart, tag, headerMode, isRootDiv,
                        renderedAttrs, errorMessage);
                if (isRootDiv) {
                    if (!sawRootTopbarId) {
                        throw new IOException(errorMessage);
                    }
                    foundRootTopbarId = true;
                }
                out.append('<').append(tag).append(renderedAttrs).append('>');
                if (!VOID_HTML_TAGS.contains(tag)) {
                    openTags.push(tag);
                }
            }
            i = tagEnd + 1;
        }
        if (!openTags.isEmpty() || (headerMode && (!foundRootTopbarId || !rootClosed))) {
            throw new IOException(errorMessage);
        }
        return out.toString();
    }

    private static int findHtmlTagEndQuoteSafe(String html, int fromIndex) {
        char quote = 0;
        for (int i = fromIndex; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '<') {
                return -1;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static boolean sanitizeAllowedHtmlAttributes(String attrsPart, String tagName, boolean headerMode,
            boolean isRootHeaderDiv, StringBuilder renderedAttrs, String errorMessage) throws IOException {
        Set<String> seen = new HashSet<>();
        boolean sawTopbarId = false;
        int i = 0;
        int len = attrsPart.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(attrsPart.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            int nameStart = i;
            while (i < len) {
                char ch = attrsPart.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':') {
                    i++;
                } else {
                    break;
                }
            }
            String attr = attrsPart.substring(nameStart, i).toLowerCase(Locale.ROOT);
            if (attr.isEmpty() || !seen.add(attr) || attr.startsWith("on")) { //$NON-NLS-1$
                throw new IOException(errorMessage);
            }
            while (i < len && Character.isWhitespace(attrsPart.charAt(i))) {
                i++;
            }
            if (i >= len || attrsPart.charAt(i) != '=') {
                throw new IOException(errorMessage);
            }
            i++;
            while (i < len && Character.isWhitespace(attrsPart.charAt(i))) {
                i++;
            }
            if (i >= len || (attrsPart.charAt(i) != '"' && attrsPart.charAt(i) != '\'')) {
                throw new IOException(errorMessage);
            }
            char quote = attrsPart.charAt(i++);
            int valStart = i;
            while (i < len && attrsPart.charAt(i) != quote) {
                char ch = attrsPart.charAt(i);
                if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') {
                    throw new IOException(errorMessage);
                }
                i++;
            }
            if (i >= len) {
                throw new IOException(errorMessage);
            }
            String rawValue = attrsPart.substring(valStart, i);
            i++; // consume closing quote
            if (i < len && !Character.isWhitespace(attrsPart.charAt(i))) {
                throw new IOException(errorMessage);
            }
            String decoded = unescapeBasicHtmlEntities(rawValue);
            String emittedValue;
            if (headerMode && "id".equals(attr)) { //$NON-NLS-1$
                if (!isRootHeaderDiv || !"capture-topbar".equals(decoded)) { //$NON-NLS-1$
                    throw new IOException(errorMessage);
                }
                sawTopbarId = true;
                emittedValue = "capture-topbar"; //$NON-NLS-1$
            } else if (headerMode && "src".equals(attr)) { //$NON-NLS-1$
                if (!"img".equals(tagName)) { //$NON-NLS-1$
                    throw new IOException(errorMessage);
                }
                try {
                    DecodedHtmlAttachment image = decodeHtmlAttachment(decoded);
                    if (!isSupportedEmbeddedRasterMime(image.mime)) {
                        throw new IOException(errorMessage);
                    }
                    emittedValue = "data:" + image.mime + ";base64," //$NON-NLS-1$ //$NON-NLS-2$
                            + Base64.getEncoder().encodeToString(image.bytes);
                } catch (IOException invalid) {
                    throw new IOException("Cabeçalho contém imagem não validada.", invalid); //$NON-NLS-1$
                }
            } else if (headerMode && (("width".equals(attr) || "height".equals(attr)) && ("img".equals(tagName) || "td".equals(tagName) || "th".equals(tagName)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    || ("colspan".equals(attr) || "rowspan".equals(attr)) && ("td".equals(tagName) || "th".equals(tagName)))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                if (!decoded.matches("\\d{1,5}")) { //$NON-NLS-1$
                    throw new IOException(errorMessage);
                }
                emittedValue = decoded;
            } else if (headerMode && "alt".equals(attr) && "img".equals(tagName)) { //$NON-NLS-1$ //$NON-NLS-2$
                emittedValue = decoded;
            } else {
                Set<String> allowedAttrs = headerMode ? ALLOWED_HEADER_ATTRS : ALLOWED_SEPARATOR_ATTRS;
                boolean isSafeDataAttr = isSafePassiveHeaderDataAttr(tagName, attr);
                if (!allowedAttrs.contains(attr) && !isSafeDataAttr) {
                    throw new IOException(errorMessage);
                }
                emittedValue = decoded;
            }
            renderedAttrs.append(' ').append(attr).append("=\"").append(escapeHtmlAttr(emittedValue)).append('"'); //$NON-NLS-1$
        }
        return sawTopbarId;
    }

    private static String unescapeBasicHtmlEntities(String value) {
        return value.replace("&quot;", "\"") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&#39;", "'") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&apos;", "'") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&lt;", "<") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&gt;", ">") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String escapeHtmlAttr(String value) {
        return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static final Set<String> ALLOWED_MESSAGE_HTML_TAGS = new HashSet<>(Arrays.asList(
            "div", "span", "p", "strong", "small", "b", "i", "br", "ul", "li", "a", "img", "audio", "video")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$
    private static final Set<String> MESSAGE_VOID_HTML_TAGS = new HashSet<>(Arrays.asList("br", "img")); //$NON-NLS-1$ //$NON-NLS-2$
    private static final Set<String> STRIP_SUBTREE_HTML_TAGS = new HashSet<>(Arrays.asList(
            "script", "template", "style", "svg", "math", "iframe", "object", "embed", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
            "form", "input", "button", "select", "textarea", "meta", "link", "base", "noscript")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
    private static final Set<String> STRIP_SUBTREE_VOID_TAGS = new HashSet<>(Arrays.asList(
            "input", "meta", "link", "base")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static final Map<String, Set<String>> ALLOWED_MESSAGE_TAG_ATTRS = buildAllowedMessageTagAttrs();

    private static Map<String, Set<String>> buildAllowedMessageTagAttrs() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("div", new HashSet<>(Arrays.asList(
                "class", "title", "data-iped-capture-date", "data-time", "data-src1", "data-src2"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        map.put("span", new HashSet<>(Arrays.asList(
                "class", "title", "data-iped-capture-date", "data-time"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        map.put("p", Collections.singleton("class")); //$NON-NLS-1$
        map.put("strong", Collections.emptySet());
        map.put("small", Collections.emptySet());
        map.put("b", Collections.emptySet());
        map.put("i", Collections.emptySet());
        map.put("br", Collections.emptySet());
        map.put("ul", Collections.emptySet());
        map.put("li", Collections.emptySet());
        map.put("a", new HashSet<>(Arrays.asList(
                "href", "class", "title", "download", "target", "rel", "aria-label"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
        map.put("img", new HashSet<>(Arrays.asList(
                "src", "class", "alt", "title", "width", "height"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        map.put("audio", new HashSet<>(Arrays.asList(
                "src", "class", "controls", "preload"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        map.put("video", new HashSet<>(Arrays.asList(
                "src", "class", "controls", "preload", "width", "height"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        return Collections.unmodifiableMap(map);
    }

    static String sanitizeCaptureMessageFragment(String fragment, Map<String, String[]> tokenResources,
            Map<String, String> dataUriToRelative) {
        return sanitizeCaptureMessageFragment(fragment, tokenResources, dataUriToRelative, null);
    }

    static String sanitizeCaptureMessageFragment(String fragment, Map<String, String[]> tokenResources,
            Map<String, String> dataUriToRelative, String messageKey) {
        if (fragment == null || fragment.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder(fragment.length());
        Deque<String> openTags = new ArrayDeque<>();
        boolean rootKeyInjected = false;
        int len = fragment.length();
        int i = 0;
        while (i < len) {
            int lt = fragment.indexOf('<', i);
            if (lt < 0) {
                out.append(fragment, i, len);
                break;
            }
            if (lt > i) {
                out.append(fragment, i, lt);
            }
            if (lt + 1 >= len) {
                out.append("&lt;"); //$NON-NLS-1$
                break;
            }
            char next = fragment.charAt(lt + 1);
            if (next == '!' && fragment.startsWith("<!--", lt)) { //$NON-NLS-1$
                int commentEnd = fragment.indexOf("-->", lt + 4); //$NON-NLS-1$
                i = commentEnd >= 0 ? commentEnd + 3 : len;
                continue;
            }
            if (next == '!' || next == '?') {
                int declEnd = findHtmlTagEndQuoteSafe(fragment, lt + 1);
                if (declEnd < 0) {
                    out.append("&lt;"); //$NON-NLS-1$
                    i = lt + 1;
                } else {
                    i = declEnd + 1;
                }
                continue;
            }
            boolean isClosing = next == '/';
            int nameScanStart = isClosing ? lt + 2 : lt + 1;
            if (nameScanStart >= len) {
                out.append("&lt;"); //$NON-NLS-1$
                break;
            }
            char firstNameChar = fragment.charAt(nameScanStart);
            if (!((firstNameChar >= 'a' && firstNameChar <= 'z')
                    || (firstNameChar >= 'A' && firstNameChar <= 'Z'))) {
                out.append("&lt;"); //$NON-NLS-1$
                i = lt + 1;
                continue;
            }
            int tagEnd = findHtmlTagEndQuoteSafe(fragment, lt + 1);
            if (tagEnd < 0) {
                out.append("&lt;"); //$NON-NLS-1$
                i = lt + 1;
                continue;
            }
            String inside = fragment.substring(lt + 1, tagEnd);
            if (isClosing) {
                String body = inside.substring(1).trim();
                int pos = 0;
                while (pos < body.length() && Character.isLetterOrDigit(body.charAt(pos))) {
                    pos++;
                }
                String closing = body.substring(0, pos).toLowerCase(Locale.ROOT);
                if (ALLOWED_MESSAGE_HTML_TAGS.contains(closing) && !MESSAGE_VOID_HTML_TAGS.contains(closing)
                        && openTags.contains(closing)) {
                    while (!openTags.isEmpty()) {
                        String popped = openTags.pop();
                        out.append("</").append(popped).append('>'); //$NON-NLS-1$
                        if (closing.equals(popped)) {
                            break;
                        }
                    }
                }
                i = tagEnd + 1;
                continue;
            }
            boolean selfClosing = inside.endsWith("/"); //$NON-NLS-1$
            String tokenBody = selfClosing ? inside.substring(0, inside.length() - 1) : inside;
            int pos = 0;
            while (pos < tokenBody.length() && Character.isLetterOrDigit(tokenBody.charAt(pos))) {
                pos++;
            }
            String tag = tokenBody.substring(0, pos).toLowerCase(Locale.ROOT);
            if (STRIP_SUBTREE_HTML_TAGS.contains(tag)) {
                if (selfClosing || STRIP_SUBTREE_VOID_TAGS.contains(tag)) {
                    i = tagEnd + 1;
                } else {
                    int subtreeEnd = skipDisallowedSubtreeEnd(fragment, tag, tagEnd + 1);
                    i = subtreeEnd >= 0 ? subtreeEnd : tagEnd + 1;
                }
                continue;
            }
            if (!ALLOWED_MESSAGE_HTML_TAGS.contains(tag)) {
                i = tagEnd + 1;
                continue;
            }
            String attrsPart = tokenBody.substring(pos);
            Map<String, String> rawAttrs = parseMessageTagAttributes(attrsPart);
            Map<String, String> cleanAttrs = sanitizeMessageTagAttributes(
                    tag, rawAttrs, tokenResources, dataUriToRelative,
                    !rootKeyInjected && "div".equals(tag) && openTags.isEmpty() ? messageKey : null); //$NON-NLS-1$
            if (!rootKeyInjected && cleanAttrs.containsKey("data-capture-message-key")) { //$NON-NLS-1$
                rootKeyInjected = true;
            }
            if ("img".equals(tag) && !cleanAttrs.containsKey("src") //$NON-NLS-1$ //$NON-NLS-2$
                    && (rawAttrs.containsKey("data-capture-src-token") //$NON-NLS-1$
                            || (dataUriToRelative != null && rawAttrs.containsKey("src") //$NON-NLS-1$
                                    && dataUriToRelative.containsKey(rawAttrs.get("src"))))) { //$NON-NLS-1$
                i = tagEnd + 1;
                continue;
            }
            out.append('<').append(tag);
            for (Map.Entry<String, String> attr : cleanAttrs.entrySet()) {
                out.append(' ').append(attr.getKey()).append("=\"") //$NON-NLS-1$
                        .append(escapeHtmlAttr(attr.getValue())).append('"');
            }
            out.append('>');
            if (!MESSAGE_VOID_HTML_TAGS.contains(tag) && !selfClosing) {
                openTags.push(tag);
            }
            i = tagEnd + 1;
        }
        while (!openTags.isEmpty()) {
            out.append("</").append(openTags.pop()).append('>'); //$NON-NLS-1$
        }
        return out.toString();
    }

    private static int skipDisallowedSubtreeEnd(String html, String targetTag, int fromIndex) {
        int len = html.length();
        int depth = 1;
        int i = fromIndex;
        while (i < len) {
            int lt = html.indexOf('<', i);
            if (lt < 0 || lt + 1 >= len) {
                return -1;
            }
            char next = html.charAt(lt + 1);
            if (next == '!' && html.startsWith("<!--", lt)) { //$NON-NLS-1$
                int end = html.indexOf("-->", lt + 4); //$NON-NLS-1$
                i = end >= 0 ? end + 3 : len;
                continue;
            }
            boolean isClosing = next == '/';
            int nameStart = isClosing ? lt + 2 : lt + 1;
            if (nameStart >= len) {
                return -1;
            }
            char c0 = html.charAt(nameStart);
            if (!((c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z'))) {
                i = lt + 1;
                continue;
            }
            int tagEnd = findHtmlTagEndQuoteSafe(html, lt + 1);
            if (tagEnd < 0) {
                i = lt + 1;
                continue;
            }
            String inside = html.substring(lt + 1, tagEnd);
            String body = isClosing ? inside.substring(1).trim() : inside;
            int pos = 0;
            while (pos < body.length() && Character.isLetterOrDigit(body.charAt(pos))) {
                pos++;
            }
            String name = body.substring(0, pos).toLowerCase(Locale.ROOT);
            if (targetTag.equals(name)) {
                if (isClosing) {
                    depth--;
                    if (depth == 0) {
                        return tagEnd + 1;
                    }
                } else if (!inside.endsWith("/")) { //$NON-NLS-1$
                    depth++;
                }
            }
            i = tagEnd + 1;
        }
        return -1;
    }

    private static Map<String, String> parseMessageTagAttributes(String attrsPart) {
        Map<String, String> attrs = new LinkedHashMap<>();
        int len = attrsPart.length();
        int i = 0;
        while (i < len) {
            while (i < len && (Character.isWhitespace(attrsPart.charAt(i)) || attrsPart.charAt(i) == '/')) {
                i++;
            }
            if (i >= len) {
                break;
            }
            int nameStart = i;
            while (i < len) {
                char ch = attrsPart.charAt(i);
                if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':') {
                    i++;
                } else {
                    break;
                }
            }
            if (i == nameStart) {
                i++;
                continue;
            }
            String name = attrsPart.substring(nameStart, i).toLowerCase(Locale.ROOT);
            while (i < len && Character.isWhitespace(attrsPart.charAt(i))) {
                i++;
            }
            String value = ""; //$NON-NLS-1$
            if (i < len && attrsPart.charAt(i) == '=') {
                i++;
                while (i < len && Character.isWhitespace(attrsPart.charAt(i))) {
                    i++;
                }
                if (i < len && (attrsPart.charAt(i) == '"' || attrsPart.charAt(i) == '\'')) {
                    char quote = attrsPart.charAt(i++);
                    int valStart = i;
                    while (i < len && attrsPart.charAt(i) != quote) {
                        i++;
                    }
                    value = unescapeBasicHtmlEntities(attrsPart.substring(valStart, i));
                    if (i < len && attrsPart.charAt(i) == quote) {
                        i++;
                    }
                } else {
                    int valStart = i;
                    while (i < len && !Character.isWhitespace(attrsPart.charAt(i))) {
                        i++;
                    }
                    value = unescapeBasicHtmlEntities(attrsPart.substring(valStart, i));
                }
            }
            attrs.putIfAbsent(name, value);
        }
        return attrs;
    }

    private static Map<String, String> sanitizeMessageTagAttributes(
            String tag,
            Map<String, String> rawAttrs,
            Map<String, String[]> tokenResources,
            Map<String, String> dataUriToRelative,
            String rootMessageKey) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (rootMessageKey != null && !rootMessageKey.isEmpty()) {
            clean.put("data-capture-message-key", stripControlChars(rootMessageKey)); //$NON-NLS-1$
        }
        Set<String> allowed = ALLOWED_MESSAGE_TAG_ATTRS.getOrDefault(tag, Collections.emptySet());
        if ("a".equals(tag) && tokenResources != null && rawAttrs.containsKey("data-capture-link-token")) { //$NON-NLS-1$ //$NON-NLS-2$
            String token = rawAttrs.get("data-capture-link-token"); //$NON-NLS-1$
            String[] info = token == null ? null : tokenResources.get(token);
            if (info != null && info.length >= 2 && info[0] != null && info[0].matches("(?i)[0-9a-f]{64}")) { //$NON-NLS-1$
                clean.put("href", "#preview-" + info[0].toLowerCase(Locale.ROOT)); //$NON-NLS-1$ //$NON-NLS-2$
                clean.put("class", "capture-media-link"); //$NON-NLS-1$ //$NON-NLS-2$
                clean.put("title", stripControlChars(info[1] == null ? "" : info[1])); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        if (("img".equals(tag) || "audio".equals(tag) || "video".equals(tag)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                && tokenResources != null && rawAttrs.containsKey("data-capture-src-token")) { //$NON-NLS-1$
            String token = rawAttrs.get("data-capture-src-token"); //$NON-NLS-1$
            String[] info = token == null ? null : tokenResources.get(token);
            if (info != null && info.length >= 3 && isSafeCaptureAttachmentRelativePath(info[2])) {
                clean.put("src", info[2].trim()); //$NON-NLS-1$
            }
        }
        for (Map.Entry<String, String> entry : rawAttrs.entrySet()) {
            String attr = entry.getKey();
            String val = stripControlChars(entry.getValue());
            if (!allowed.contains(attr) || attr.startsWith("on")) { //$NON-NLS-1$
                continue;
            }
            if ("a".equals(tag) && clean.containsKey(attr)) { //$NON-NLS-1$
                continue;
            }
            if ("src".equals(attr)) { //$NON-NLS-1$
                if (clean.containsKey("src")) { //$NON-NLS-1$
                    continue;
                }
                if (dataUriToRelative != null && dataUriToRelative.containsKey(entry.getValue())) {
                    String mapped = dataUriToRelative.get(entry.getValue());
                    if (isSafeCaptureAttachmentRelativePath(mapped)) {
                        clean.put("src", mapped.trim()); //$NON-NLS-1$
                    }
                    continue;
                }
                if (isSafeCaptureMessageMediaSrc(tag, val)) {
                    clean.put("src", val.trim()); //$NON-NLS-1$
                }
                continue;
            }
            if ("href".equals(attr)) { //$NON-NLS-1$
                String trimmed = val.trim();
                if ((trimmed.startsWith("#") && trimmed.length() > 1) || isSafeCaptureAttachmentRelativePath(trimmed)) { //$NON-NLS-1$
                    clean.put("href", trimmed); //$NON-NLS-1$
                }
                continue;
            }
            if ("width".equals(attr) || "height".equals(attr)) { //$NON-NLS-1$ //$NON-NLS-2$
                String trimmed = val.trim();
                if (trimmed.matches("\\d{1,5}")) { //$NON-NLS-1$
                    clean.put(attr, trimmed);
                }
                continue;
            }
            if ("target".equals(attr)) { //$NON-NLS-1$
                if ("_blank".equals(val.trim())) { //$NON-NLS-1$
                    clean.put("target", "_blank"); //$NON-NLS-1$ //$NON-NLS-2$
                    clean.put("rel", "noopener noreferrer"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                continue;
            }
            if ("rel".equals(attr)) { //$NON-NLS-1$
                clean.putIfAbsent("rel", "noopener noreferrer"); //$NON-NLS-1$ //$NON-NLS-2$
                continue;
            }
            if ("controls".equals(attr)) { //$NON-NLS-1$
                clean.putIfAbsent("controls", ""); //$NON-NLS-1$ //$NON-NLS-2$
                continue;
            }
            if ("preload".equals(attr)) { //$NON-NLS-1$
                if ("none".equals(val.trim())) { //$NON-NLS-1$
                    clean.put("preload", "none"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                continue;
            }
            clean.putIfAbsent(attr, val);
        }
        return clean;
    }

    private static String stripControlChars(String value) {
        if (value == null || value.isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') {
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static boolean isSafeCaptureAttachmentRelativePath(String value) {
        return value != null && value.trim().matches("attachments/[0-9a-f]{64}\\.[a-z0-9]{1,10}"); //$NON-NLS-1$
    }

    private static boolean isSupportedEmbeddedRasterMime(String mime) {
        if (mime == null) {
            return false;
        }
        String normalized = mime.toLowerCase(Locale.ROOT);
        return "image/png".equals(normalized) || "image/jpeg".equals(normalized) //$NON-NLS-1$ //$NON-NLS-2$
                || "image/gif".equals(normalized) || "image/webp".equals(normalized); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean isSafePassiveHeaderDataAttr(String tagName, String attr) {
        if (tagName == null || attr == null || !attr.matches("data-[a-z0-9_-]{1,64}")) { //$NON-NLS-1$
            return false;
        }
        if ("data-capture-message-key".equals(attr) || "data-capture-link-token".equals(attr) //$NON-NLS-1$ //$NON-NLS-2$
                || "data-capture-src-token".equals(attr) || "data-capture-censored".equals(attr)) { //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        return !"img".equals(tagName) && !"br".equals(tagName) && !"hr".equals(tagName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static boolean isSafeCaptureMessageMediaSrc(String tag, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (isSafeCaptureAttachmentRelativePath(trimmed)) {
            return true;
        }
        if ("img".equals(tag) && trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) { //$NON-NLS-1$ //$NON-NLS-2$
            try {
                DecodedHtmlAttachment decoded = decodeHtmlAttachment(trimmed);
                return isSupportedEmbeddedRasterMime(decoded.mime);
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private String captureViewerCss() {
        return ".capture-modal{display:none;position:fixed;inset:0;z-index:2147483000;align-items:center;justify-content:center;padding:18px;background:rgba(0,0,0,.72)}.capture-modal:target{display:flex}.capture-modal-card{box-sizing:border-box;width:min(900px,96vw);max-height:94vh;overflow:auto;background:#f7f4ef;border-radius:10px;box-shadow:0 12px 38px rgba(0,0,0,.45);font:14px Arial,sans-serif;color:#202c33}.capture-modal-header{display:flex;align-items:center;justify-content:space-between;padding:10px 16px;background:#075e54;color:#fff}.capture-modal-header h2{margin:0;font-size:16px}.capture-close-icon{font-size:26px;color:#fff;text-decoration:none;padding:0 6px}.capture-modal-body{padding:14px;max-height:75vh;text-align:center}.capture-preview-image{display:block;max-width:100%;max-height:68vh;margin:auto;object-fit:contain}.capture-preview-pdf{display:block;width:100%;height:68vh;border:0;background:#fff}.capture-modal-body audio{width:min(100%,550px)}.capture-modal-body video{max-width:100%;max-height:68vh}.capture-preview-fallback,.capture-file-details{overflow-wrap:anywhere;color:#53636c}.capture-preview-actions{display:flex;flex-wrap:wrap;gap:8px;padding:10px 14px 14px}.capture-button{display:inline-block;padding:8px 12px;border-radius:5px;background:#128c7e;color:#fff;text-decoration:none}.capture-button:hover{background:#075e54}.capture-close{background:#52636a}.capture-media-link{cursor:pointer}.capture-file-card{margin:6px 0;padding:8px 10px;border-radius:6px;background:#f4f0e8;color:#263238;overflow-wrap:anywhere}.annotation{white-space:pre-wrap;border-left:3px solid #b26a00;background:#fffbe9;margin:.25rem .5rem .75rem;padding:.5rem}.annotation-links{font-size:.9em;color:#555}"; //$NON-NLS-1$
    }

    private String renderCaptureAttachmentModal(String path, String mime, long size, String sha256, String label) {
        String safePath = html(path), safeName = html(safeAttachmentName(label, "anexo")), safeHash = html(sha256); //$NON-NLS-1$
        String lowerMime = mime == null ? "application/octet-stream" : mime.toLowerCase(Locale.ROOT); //$NON-NLS-1$
        String title = lowerMime.startsWith("image/") ? "Imagem" : "application/pdf".equals(lowerMime) ? "PDF" : lowerMime.startsWith("audio/") ? "Áudio" : lowerMime.startsWith("video/") ? "Vídeo" : "Arquivo"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        StringBuilder content = new StringBuilder();
        if (lowerMime.startsWith("image/")) content.append("<img class=\"capture-preview-image\" src=\"").append(safePath).append("\" alt=\"").append(safeName).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
        else if ("application/pdf".equals(lowerMime)) content.append("<iframe class=\"capture-preview-pdf\" title=\"Pré-visualização do PDF\" src=\"").append(safePath).append("\" sandbox=\"\"></iframe><p class=\"capture-preview-fallback\">Se o PDF não aparecer neste painel, use Abrir em nova aba ou Baixar.</p>"); //$NON-NLS-1$ //$NON-NLS-2$
        else if (lowerMime.startsWith("audio/")) content.append("<audio controls preload=\"none\" src=\"").append(safePath).append("\"></audio>"); //$NON-NLS-1$ //$NON-NLS-2$
        else if (lowerMime.startsWith("video/")) content.append("<video controls preload=\"none\" src=\"").append(safePath).append("\"></video>"); //$NON-NLS-1$ //$NON-NLS-2$
        else content.append("<p class=\"capture-file-details\">").append(safeName).append(" · ").append(html(lowerMime)).append(" · ").append(size).append(" bytes · SHA-256 ").append(safeHash).append("</p>"); //$NON-NLS-1$ //$NON-NLS-2$
        String id = "preview-" + sha256; //$NON-NLS-1$
        return "<section class=\"capture-modal\" id=\"" + id + "\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"title-" + sha256 + "\"><div class=\"capture-modal-card\"><header class=\"capture-modal-header\"><h2 id=\"title-" + sha256 + "\">" + title + "</h2><a class=\"capture-close-icon capture-close\" href=\"#\" aria-label=\"Fechar\">×</a></header><div class=\"capture-modal-body\">" + content + "</div><nav class=\"capture-preview-actions\"><a class=\"capture-button\" href=\"" + safePath + "\" download=\"" + safeName + "\">Baixar</a><a class=\"capture-button\" href=\"" + safePath + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir em nova aba</a><a class=\"capture-button capture-close\" href=\"#\">Fechar</a></nav></div></section>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private String linkInlineCaptureImages(String fragment) {
        Matcher images = Pattern.compile("(?is)<img\\b[^>]*>").matcher(fragment); //$NON-NLS-1$
        StringBuffer result = new StringBuffer();
        while (images.find()) {
            String tag = images.group(), before = fragment.substring(0, images.start());
            String source = null;
            Matcher src = Pattern.compile("(?is)\\bsrc=([\\\"])(attachments/[0-9a-f]{64}\\.[a-z0-9]{1,10})\\1").matcher(tag); //$NON-NLS-1$
            if (src.find()) source = src.group(2);
            boolean insideAnchor = isInsideOpenAnchorTag(before);
            if (source != null && !insideAnchor) {
                String hash = source.substring("attachments/".length(), source.lastIndexOf('.')); //$NON-NLS-1$
                String replacement = "<a class=\"capture-media-link\" href=\"#preview-" + hash + "\">" + tag + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$
                images.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else images.appendReplacement(result, Matcher.quoteReplacement(tag));
        }
        images.appendTail(result);
        return result.toString();
    }

    private String replaceInlineCapturePlayers(String fragment, Map<String, String> modals) {
        Matcher players = Pattern.compile("(?is)<(audio|video)\\b[^>]*>.*?</\\1\\s*>").matcher(fragment); //$NON-NLS-1$
        StringBuffer result = new StringBuffer();
        while (players.find()) {
            String player = players.group(), lower = player.toLowerCase(Locale.ROOT);
            Matcher src = Pattern.compile("(?is)\\bsrc=([\\\"])(attachments/([0-9a-f]{64})\\.[a-z0-9]{1,10})\\1").matcher(player); //$NON-NLS-1$
            if (!src.find()) { players.appendReplacement(result, Matcher.quoteReplacement("")); continue; } //$NON-NLS-1$
            String path = src.group(2), hash = src.group(3), kind = lower.startsWith("<audio") ? "Áudio" : "Vídeo"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            String before = fragment.substring(0, players.start());
            boolean insideAnchor = isInsideOpenAnchorTag(before);
            String replacement = insideAnchor ? "<span class=\"capture-file-card\">" + kind + "</span>" //$NON-NLS-1$ //$NON-NLS-2$
                    : "<a class=\"capture-file-card capture-media-link\" href=\"#preview-" + hash + "\">" + kind + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$
            if (!modals.containsKey(path)) replacement = ""; //$NON-NLS-1$
            players.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        players.appendTail(result);
        return result.toString();
    }

    private static DecodedHtmlAttachment decodeHtmlAttachment(String value) throws IOException {
        Matcher matcher = Pattern.compile("(?is)^data:([a-z0-9.+-]+/[a-z0-9.+-]+);base64,([A-Za-z0-9+/=\\r\\n]+)$").matcher(value == null ? "" : value); //$NON-NLS-1$ //$NON-NLS-2$
        if (!matcher.matches()) throw new IOException("Anexo HTML não possui URI data base64 segura."); //$NON-NLS-1$
        if (value.length() > ((MAX_HTML_MESSAGE_ATTACHMENT_BYTES + 2) / 3) * 4 + 128) throw new IOException("Anexo codificado excede o limite antes da decodificação."); //$NON-NLS-1$
        byte[] bytes;
        try { bytes = Base64.getMimeDecoder().decode(matcher.group(2)); }
        catch (IllegalArgumentException invalid) { throw new IOException("Anexo HTML possui base64 inválido.", invalid); }
        if (bytes.length == 0 || bytes.length > MAX_HTML_MESSAGE_ATTACHMENT_BYTES) throw new IOException("Anexo HTML fora do limite de 8 MiB."); //$NON-NLS-1$
        String declaredMime = matcher.group(1).toLowerCase(Locale.ROOT);
        String mime = sniffHtmlAttachmentMime(bytes);
        if (mime == null || !declaredMime.substring(0, declaredMime.indexOf('/')).equals(mime.substring(0, mime.indexOf('/')))) {
            throw new IOException("Tipo de anexo HTML não reconhecido ou incompatível com os bytes."); //$NON-NLS-1$
        }
        return new DecodedHtmlAttachment(mime, bytes, CaptureIntegrity.sha256Bytes(bytes), htmlAttachmentExtension(mime));
    }

    private boolean isCensoredAttachment(SensitiveImageMatcher matcher, DecodedHtmlAttachment attachment, String relative,
            Path staging, Map<String, Boolean> verdicts) throws IOException {
        if (matcher == null || attachment == null || attachment.mime == null || !attachment.mime.startsWith("image/")) return false; //$NON-NLS-1$
        Boolean cached = verdicts.get(attachment.sha256);
        if (cached != null) return cached;
        boolean hit;
        try {
            if (attachment.bytes != null) hit = matcher.matches(attachment.bytes);
            else if (staging != null) hit = matcher.matches(staging.resolve(relative).toFile());
            else hit = false;
        } catch (RuntimeException | LinkageError failure) {
            throw new IOException("Falha ao comparar a imagem com a pasta de referência: " + attachment.sha256, failure); //$NON-NLS-1$
        }
        verdicts.put(attachment.sha256, hit);
        return hit;
    }

    private DecodedHtmlAttachment resolveCaseAttachment(String expectedSha256) throws IOException {
        if (attachSearcher == null || expectedSha256 == null || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Não foi possível resolver a referência de anexo do IPED."); //$NON-NLS-1$
        }
        String query = "sha-256:" + attachSearcher.escapeQuery(expectedSha256); //$NON-NLS-1$
        IItem item = attachSearcher.getItem(query);
        if (item == null) throw new IOException("Anexo relacionado à mensagem não foi encontrado no caso: " + expectedSha256); //$NON-NLS-1$
        Long declaredLength = item.getLength();
        if (declaredLength != null && (declaredLength < 0 || declaredLength > MAX_HTML_MESSAGE_ATTACHMENT_BYTES)) {
            throw new IOException("Anexo relacionado excede o limite de 8 MiB: " + safe(item.getName())); //$NON-NLS-1$
        }
        DecodedHtmlAttachment reused = tryReuseLegacyAttachment(item, expectedSha256, declaredLength);
        if (reused != null) return reused;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(declaredLength == null ? 8192 : Math.min(declaredLength.intValue(), 8192));
        try (InputStream input = item.getBufferedInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + (long) count > MAX_HTML_MESSAGE_ATTACHMENT_BYTES) {
                    throw new IOException("Anexo relacionado excede o limite de 8 MiB durante a leitura: " + safe(item.getName())); //$NON-NLS-1$
                }
                bytes.write(buffer, 0, count);
            }
        }
        byte[] content = bytes.toByteArray();
        String actualSha256 = CaptureIntegrity.sha256Bytes(content);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) throw new IOException("Hash do anexo relacionado diverge da referência do IPED."); //$NON-NLS-1$
        String declaredMime = item.getMediaType() == null ? "" : item.getMediaType().toString().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        int semicolon = declaredMime.indexOf(';');
        if (semicolon >= 0) declaredMime = declaredMime.substring(0, semicolon).trim();
        String extension = item.getExt() == null ? "" : item.getExt().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        while (extension.startsWith(".")) extension = extension.substring(1); //$NON-NLS-1$
        if (!extension.matches("[a-z0-9]{1,10}")) extension = ""; //$NON-NLS-1$ //$NON-NLS-2$
        String mime = caseAttachmentMime(content, declaredMime, extension);
        String safeExtension = htmlAttachmentExtension(mime);
        return new DecodedHtmlAttachment(mime, content, actualSha256, safeExtension);
    }

    private Map<String, List<ReusableAttachment>> buildReusableAttachmentIndex(RecaptureJob job) throws IOException {
        Map<String, List<ReusableAttachment>> indexed = new HashMap<>();
        if (job == null || !job.legacyCapture || !job.resetLegacyRecords || job.folderPath == null) return indexed;
        Path root = job.folderPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
        int files = 0;
        long totalBytes = 0;
        for (String directory : new String[] {"anexos", "attachments"}) { //$NON-NLS-1$ //$NON-NLS-2$
            Path base = root.resolve(directory);
            if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) continue;
            if (CaptureIntegrity.isLinkOrReparse(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A pasta de anexos legados não é um diretório seguro: " + base); //$NON-NLS-1$
            }
            for (Path path : CaptureIntegrity.listRegularFilesSafe(base, 25000)) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.length() > CaptureIntegrity.MAX_RELATIVE_PATH_CHARS) {
                    throw new IOException("Caminho de anexo legado excede o limite seguro."); //$NON-NLS-1$
                }
                CaptureIntegrity.resolveContained(root, relative);
                long size = CaptureIntegrity.safeRegularFileSize(path);
                if (size < 1 || size > MAX_HTML_MESSAGE_ATTACHMENT_BYTES) continue;
                if (++files > 25000) throw new IOException("A captura legada excede o limite de anexos para reuso."); //$NON-NLS-1$
                totalBytes += size;
                if (totalBytes > MAX_HTML_MESSAGE_ATTACHMENT_BYTES * 8L) {
                    throw new IOException("O total de anexos legados excede o orçamento seguro de reuso."); //$NON-NLS-1$
                }
                ReusableAttachment entry = new ReusableAttachment(path, CaptureIntegrity.sha256(path), size);
                indexed.computeIfAbsent(reusableAttachmentKey(entry.sha256, entry.size), key -> new ArrayList<>()).add(entry);
            }
        }
        return indexed;
    }

    private String readLegacyAnnotationsForMigration(RecaptureJob job) throws IOException {
        Path coordinates = job.folderPath.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
        if (!Files.isRegularFile(coordinates, LinkOption.NOFOLLOW_LINKS)) return ""; //$NON-NLS-1$
        String content = CaptureIntegrity.readJsonUtf8Limited(coordinates);
        try {
            JSONObject root = new JSONObject(content);
            JSONObject annotations = root.optJSONObject("annotations"); //$NON-NLS-1$
            if (annotations == null) return ""; //$NON-NLS-1$
            return annotations.toString(2) + System.lineSeparator();
        } catch (RuntimeException invalid) {
            throw new IOException("As anotações legadas estão inválidas; a captura antiga foi preservada.", invalid); //$NON-NLS-1$
        }
    }

    private JSONObject buildLegacyMigrationMetadata(RecaptureJob job, String legacyAnnotationsJson) throws IOException {
        Path oldManifestPath = job.folderPath.resolve("manifest.json"); //$NON-NLS-1$
        String previousManifestSha256 = CaptureIntegrity.sha256(oldManifestPath);
        String previousContentRootHash = ""; //$NON-NLS-1$
        String previousRecordHash = ""; //$NON-NLS-1$
        Path oldIntegrityPath = job.folderPath.resolve("integridade.json"); //$NON-NLS-1$
        if (Files.isRegularFile(oldIntegrityPath, LinkOption.NOFOLLOW_LINKS)) {
            JSONObject previous = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(oldIntegrityPath));
            previousContentRootHash = previous.optString("contentRootHash", ""); //$NON-NLS-1$
            JSONArray events = previous.optJSONArray("events"); //$NON-NLS-1$
            if (events != null && events.length() > 0) {
                JSONObject last = events.optJSONObject(events.length() - 1);
                if (last != null) previousRecordHash = last.optString("recordHash", ""); //$NON-NLS-1$
            }
        }
        return new JSONObject().put("schemaVersion", 1) //$NON-NLS-1$
                .put("action", "legacy_screenshot_capture_migrated_to_html_message_v1") //$NON-NLS-1$
                .put("sourceFormat", "legacy-screenshot") //$NON-NLS-1$
                .put("previousIntegrityContentRootHash", previousContentRootHash) //$NON-NLS-1$
                .put("previousIntegrityRecordHash", previousRecordHash) //$NON-NLS-1$
                .put("previousManifestSha256", previousManifestSha256) //$NON-NLS-1$
                .put("anchorsRecoveredFrom", "manifest-and-whatsapp-coordinates") //$NON-NLS-1$
                .put("legacyAnnotationsPreserved", legacyAnnotationsJson != null && !legacyAnnotationsJson.isBlank()); //$NON-NLS-1$
    }

    private DecodedHtmlAttachment tryReuseLegacyAttachment(IItem item, String expectedSha256, Long declaredLength) {
        RecaptureJob job = activeRecaptureJob;
        if (job == null || !job.resetLegacyRecords || job.reusableAttachmentStaging == null
                || declaredLength == null || declaredLength < 1 || job.reusableAttachmentsByHashAndSize == null) return null;
        List<ReusableAttachment> candidates = job.reusableAttachmentsByHashAndSize
                .get(reusableAttachmentKey(expectedSha256, declaredLength));
        if (candidates == null || candidates.isEmpty()) return null;
        String declaredMime = item.getMediaType() == null ? "" : item.getMediaType().toString().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        int semicolon = declaredMime.indexOf(';');
        if (semicolon >= 0) declaredMime = declaredMime.substring(0, semicolon).trim();
        String sourceExtension = item.getExt() == null ? "" : item.getExt().toLowerCase(Locale.ROOT); //$NON-NLS-1$
        while (sourceExtension.startsWith(".")) sourceExtension = sourceExtension.substring(1); //$NON-NLS-1$
        if (!sourceExtension.matches("[a-z0-9]{1,10}")) sourceExtension = ""; //$NON-NLS-1$ //$NON-NLS-2$
        for (ReusableAttachment candidate : candidates) {
            Path temporary = null;
            Path target = null;
            try {
                if (Files.isSymbolicLink(candidate.path) || !Files.isRegularFile(candidate.path, LinkOption.NOFOLLOW_LINKS)
                        || CaptureIntegrity.safeRegularFileSize(candidate.path) != declaredLength
                        || !CaptureIntegrity.sha256(candidate.path).equalsIgnoreCase(expectedSha256)) continue;
                if (job.folderPath == null) continue;
                CaptureIntegrity.resolveContained(job.folderPath,
                        job.folderPath.toAbsolutePath().normalize().relativize(candidate.path.toAbsolutePath().normalize()).toString());
                byte[] prefix = readFilePrefix(candidate.path, 64);
                String mime = caseAttachmentMime(prefix, declaredMime, sourceExtension);
                String extension = htmlAttachmentExtension(mime);
                String relative = "attachments/" + expectedSha256.toLowerCase(Locale.ROOT) + "." + extension; //$NON-NLS-1$
                target = job.reusableAttachmentStaging.resolve(relative).normalize();
                if (!target.startsWith(job.reusableAttachmentStaging.normalize())) continue;
                if (CaptureIntegrity.isLinkOrReparse(candidate.path) || CaptureIntegrity.isLinkOrReparse(target)) continue;
                CaptureIntegrity.createDirectoriesSafe(job.reusableAttachmentStaging, target.getParent());
                if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        && CaptureIntegrity.safeRegularFileSize(target) == declaredLength
                        && CaptureIntegrity.sha256(target).equalsIgnoreCase(expectedSha256)) {
                    job.stagedReusableAttachmentPaths.add(relative);
                    return new DecodedHtmlAttachment(mime, null, expectedSha256.toLowerCase(Locale.ROOT), extension, declaredLength);
                }
                Files.deleteIfExists(target);
                temporary = Files.createTempFile(target.getParent(), ".reuse-", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
                try (InputStream source = Files.newInputStream(candidate.path, LinkOption.NOFOLLOW_LINKS);
                        java.io.OutputStream destination = Files.newOutputStream(temporary, StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer = new byte[8192];
                    long copied = 0;
                    int count;
                    while ((count = source.read(buffer)) != -1) {
                        copied += count;
                        if (copied > declaredLength) throw new IOException("Anexo reutilizado excedeu o tamanho esperado."); //$NON-NLS-1$
                        destination.write(buffer, 0, count);
                    }
                    if (copied != declaredLength) throw new IOException("Anexo reutilizado terminou antes do tamanho esperado."); //$NON-NLS-1$
                }
                if (CaptureIntegrity.safeRegularFileSize(temporary) != declaredLength
                        || !CaptureIntegrity.sha256(temporary).equalsIgnoreCase(expectedSha256)) continue;
                if (CaptureIntegrity.isLinkOrReparse(target)) {
                    throw new IOException("Symbolic links or junctions are not allowed as attachment target"); //$NON-NLS-1$
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                temporary = null;
                if (CaptureIntegrity.isLinkOrReparse(target)
                        || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        || CaptureIntegrity.safeRegularFileSize(target) != declaredLength
                        || !CaptureIntegrity.sha256(target).equalsIgnoreCase(expectedSha256)) {
                    Files.deleteIfExists(target);
                    continue;
                }
                job.stagedReusableAttachmentPaths.add(relative);
                return new DecodedHtmlAttachment(mime, null, expectedSha256.toLowerCase(Locale.ROOT), extension, declaredLength);
            } catch (Exception unsafeOrChanged) {
                LOGGER.debug("Legacy attachment reuse failed; extracting from the current IPED source", unsafeOrChanged);
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            if (target != null) try { Files.deleteIfExists(target); } catch (IOException ignored) { }
        }
        return null;
    }

    private byte[] readFilePrefix(Path file, int maximum) throws IOException {
        CaptureIntegrity.safeRegularFileSize(file);
        byte[] buffer = new byte[maximum];
        int total = 0;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            while (total < buffer.length) {
                int count = input.read(buffer, total, buffer.length - total);
                if (count < 0) break;
                total += count;
            }
        }
        return total == buffer.length ? buffer : Arrays.copyOf(buffer, total);
    }

    private static String reusableAttachmentKey(String sha256, long size) {
        return (sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT)) + ":" + size; //$NON-NLS-1$
    }

    private String captureSourceFingerprint() throws IOException {
        IItem source = captureSourceItem;
        if (source == null) throw new IOException("O HTML fonte do item atual não está disponível para calcular o fingerprint."); //$NON-NLS-1$
        Long length = source.getLength();
        long maximum = 16L * 1024L * 1024L;
        if (length != null && (length < 0 || length > maximum)) throw new IOException("HTML de origem acima do limite seguro de 16 MiB."); //$NON-NLS-1$
        try (InputStream input = source.getBufferedInputStream()) {
            return CaptureIntegrity.sha256(input, maximum);
        }
    }

    private String caseAttachmentMime(byte[] bytes, String declaredMime, String extension) {
        String sniffed = sniffHtmlAttachmentMime(bytes);
        if (sniffed != null && (sniffed.startsWith("image/") || sniffed.startsWith("audio/") || sniffed.startsWith("video/") || "application/pdf".equals(sniffed))) return sniffed; //$NON-NLS-1$
        if ("application/zip".equals(sniffed)) { //$NON-NLS-1$
            if (declaredMime.startsWith("application/vnd.openxmlformats-officedocument.")) return declaredMime; //$NON-NLS-1$
            return "application/zip"; //$NON-NLS-1$
        }
        if ("application/x-ole-storage".equals(sniffed)) { //$NON-NLS-1$
            if ("application/msword".equals(declaredMime) || "application/vnd.ms-excel".equals(declaredMime) || "application/vnd.ms-powerpoint".equals(declaredMime)) return declaredMime; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if ("doc".equals(extension)) return "application/msword"; //$NON-NLS-1$ //$NON-NLS-2$
            if ("xls".equals(extension)) return "application/vnd.ms-excel"; //$NON-NLS-1$ //$NON-NLS-2$
            if ("ppt".equals(extension)) return "application/vnd.ms-powerpoint"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        if ("application/vnd.rar".equals(sniffed) || "application/x-7z-compressed".equals(sniffed)) return sniffed; //$NON-NLS-1$ //$NON-NLS-2$
        if (("text/plain".equals(declaredMime) || "text/csv".equals(declaredMime)) && isTextAttachment(bytes)) return declaredMime; //$NON-NLS-1$ //$NON-NLS-2$
        if (("txt".equals(extension) || "csv".equals(extension)) && isTextAttachment(bytes)) return "csv".equals(extension) ? "text/csv" : "text/plain"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return "application/octet-stream"; //$NON-NLS-1$
    }

    private boolean isTextAttachment(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
            return false;
        }
    }

    private String htmlContentKind(String mime) {
        if (mime.startsWith("image/")) return "image"; //$NON-NLS-1$
        if (mime.startsWith("audio/")) return "audio"; //$NON-NLS-1$
        if (mime.startsWith("video/")) return "video"; //$NON-NLS-1$
        if ("application/pdf".equals(mime) || mime.startsWith("application/vnd.openxmlformats-officedocument.")) return "document"; //$NON-NLS-1$ //$NON-NLS-2$
        return "file"; //$NON-NLS-1$
    }

    private String safeAttachmentName(String requested, String fallback) {
        String value = sanitizeFileName(requested);
        if (value.isEmpty()) {
            value = sanitizeFileName(fallback);
        }
        return value.isEmpty() ? "anexo.bin" : value; //$NON-NLS-1$
    }

    private static String sniffHtmlAttachmentMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png"; //$NON-NLS-1$
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) return "image/jpeg"; //$NON-NLS-1$
        if (bytes.length >= 6) { String signature = new String(bytes, 0, 6, StandardCharsets.US_ASCII); if ("GIF87a".equals(signature) || "GIF89a".equals(signature)) return "image/gif"; } //$NON-NLS-1$ //$NON-NLS-2$
        if (bytes.length >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII)) && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) return "image/webp"; //$NON-NLS-1$ //$NON-NLS-2$
        if (bytes.length >= 4 && "OggS".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))) return "audio/ogg"; //$NON-NLS-1$
        if (bytes.length >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII)) && "WAVE".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) return "audio/wav"; //$NON-NLS-1$ //$NON-NLS-2$
        if (bytes.length >= 3 && "ID3".equals(new String(bytes, 0, 3, StandardCharsets.US_ASCII))) return "audio/mpeg"; //$NON-NLS-1$
        if (bytes.length >= 2 && (bytes[0] & 255) == 0xff && ((bytes[1] & 0xe0) == 0xe0)) return "audio/mpeg"; //$NON-NLS-1$
        if (bytes.length >= 8 && "ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII))) return "video/mp4"; //$NON-NLS-1$
        if (bytes.length >= 4 && (bytes[0] & 255) == 0x1a && (bytes[1] & 255) == 0x45 && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3) return "video/webm"; //$NON-NLS-1$
        if (bytes.length >= 5 && "%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII))) return "application/pdf"; //$NON-NLS-1$
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)) return "application/zip"; //$NON-NLS-1$
        if (bytes.length >= 8 && (bytes[0] & 255) == 0xd0 && (bytes[1] & 255) == 0xcf && (bytes[2] & 255) == 0x11 && (bytes[3] & 255) == 0xe0 && (bytes[4] & 255) == 0xa1 && (bytes[5] & 255) == 0xb1 && (bytes[6] & 255) == 0x1a && (bytes[7] & 255) == 0xe1) return "application/x-ole-storage"; //$NON-NLS-1$
        if (bytes.length >= 7 && "Rar!\u001a\u0007".equals(new String(bytes, 0, 6, StandardCharsets.US_ASCII))) return "application/vnd.rar"; //$NON-NLS-1$
        if (bytes.length >= 6 && (bytes[0] & 255) == 0x37 && (bytes[1] & 255) == 0x7a && (bytes[2] & 255) == 0xbc && (bytes[3] & 255) == 0xaf && (bytes[4] & 255) == 0x27 && (bytes[5] & 255) == 0x1c) return "application/x-7z-compressed"; //$NON-NLS-1$
        return null;
    }

    private static String htmlAttachmentExtension(String mime) {
        if ("image/jpeg".equals(mime)) return "jpg"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("image/png".equals(mime)) return "png"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("image/gif".equals(mime)) return "gif"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("image/webp".equals(mime)) return "webp"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("audio/ogg".equals(mime)) return "ogg"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("audio/wav".equals(mime)) return "wav"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("audio/mpeg".equals(mime)) return "mp3"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("video/mp4".equals(mime)) return "mp4"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("video/webm".equals(mime)) return "webm"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/pdf".equals(mime)) return "pdf"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/zip".equals(mime)) return "zip"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) return "docx"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mime)) return "xlsx"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mime)) return "pptx"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/msword".equals(mime)) return "doc"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.ms-excel".equals(mime)) return "xls"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.ms-powerpoint".equals(mime)) return "ppt"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("text/plain".equals(mime)) return "txt"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("text/csv".equals(mime)) return "csv"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/vnd.rar".equals(mime)) return "rar"; //$NON-NLS-1$ //$NON-NLS-2$
        if ("application/x-7z-compressed".equals(mime)) return "7z"; //$NON-NLS-1$ //$NON-NLS-2$
        return "bin"; //$NON-NLS-1$
    }

    private String htmlAnnotationBlock(JSONObject item) {
        JSONObject note = item.optJSONObject("note"); //$NON-NLS-1$
        JSONArray links = item.optJSONArray("links"); //$NON-NLS-1$
        if (note == null && (links == null || links.length() == 0)) return ""; //$NON-NLS-1$
        StringBuilder block = new StringBuilder("<aside class=\"annotation\"><strong>Anotações do analista</strong>"); //$NON-NLS-1$
        if (note != null) {
            block.append("<p>").append(html(note.optString("text"))).append("</p>"); //$NON-NLS-1$ //$NON-NLS-2$
            String author = note.optString("author", ""); //$NON-NLS-1$
            String updated = note.optString("updatedAt", ""); //$NON-NLS-1$
            if (!author.isEmpty() || !updated.isEmpty()) block.append("<small>").append(html(author)).append(author.isEmpty() || updated.isEmpty() ? "" : " · ").append(html(updated)).append("</small>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if (links != null && links.length() > 0) {
            block.append("<div class=\"annotation-links\">Conversas vinculadas: "); //$NON-NLS-1$
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.optJSONObject(i);
                if (link == null) continue;
                if (i > 0) block.append("; "); //$NON-NLS-1$
                String title = link.optString("title", link.optString("sourceName", "conversa")); //$NON-NLS-1$ //$NON-NLS-2$
                block.append(html(title));
            }
            block.append("</div>"); //$NON-NLS-1$
        }
        return block.append("</aside>").toString(); //$NON-NLS-1$
    }

    private String htmlMessageHash(JSONObject message) throws IOException {
        return computeHtmlMessageHash(message);
    }

    private String htmlCaptureRootHash(JSONArray messages, String htmlSha256, String annotationsSha256,
            String reconciliationSha256, String conversationKey, String sourceFingerprint, JSONObject sourceMetadata, String ipedVersion,
            String pluginVersion, String startAnchor, String endAnchor) throws IOException {
        return computeHtmlCaptureRootHash(messages, htmlSha256, annotationsSha256, reconciliationSha256,
                conversationKey, sourceFingerprint, sourceMetadata, ipedVersion, pluginVersion, startAnchor, endAnchor);
    }

    private static class DecodedHtmlAttachment {
        final String mime; final byte[] bytes; final String sha256; final String extension; final String name; final long size;
        DecodedHtmlAttachment(String mime, byte[] bytes, String sha256, String extension) {
            this(mime, bytes, sha256, extension, bytes == null ? 0 : bytes.length);
        }
        DecodedHtmlAttachment(String mime, byte[] bytes, String sha256, String extension, long size) {
            this.mime = mime;
            this.bytes = bytes;
            this.sha256 = sha256;
            this.extension = extension;
            this.name = sha256 + "." + extension;
            this.size = size;
        }
    }

    static String computeHtmlMessageHash(JSONObject message) throws IOException {
        StringBuilder canonical = new StringBuilder("html-message-v1-message\n"); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("domId")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, Integer.toString(message.optInt("order", -1))); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("timestamp")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("sender")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("direction")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("type")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, Integer.toString(message.optInt("duplicateOrdinal", -1))); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("text")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, message.optString("classes")); //$NON-NLS-1$
        JSONArray attachments = message.optJSONArray("attachments"); //$NON-NLS-1$
        for (int i = 0; attachments != null && i < attachments.length(); i++) {
            JSONObject attachment = attachments.getJSONObject(i);
            appendHtmlMessageField(canonical, attachment.optString("path")); //$NON-NLS-1$
            appendHtmlMessageField(canonical, attachment.optString("mime")); //$NON-NLS-1$
            appendHtmlMessageField(canonical, Long.toString(attachment.optLong("size", -1))); //$NON-NLS-1$
            appendHtmlMessageField(canonical, attachment.optString("sha256")); //$NON-NLS-1$
        }
        return CaptureIntegrity.sha256Text(canonical.toString());
    }

    static String computeHtmlCaptureRootHash(JSONArray messages, String htmlSha256, String annotationsSha256,
            String reconciliationSha256, String conversationKey, String sourceFingerprint, JSONObject sourceMetadata, String ipedVersion,
            String pluginVersion, String startAnchor, String endAnchor) throws IOException {
        StringBuilder canonical = new StringBuilder("html-message-v1-root\n"); //$NON-NLS-1$
        appendHtmlMessageField(canonical, htmlSha256);
        appendHtmlMessageField(canonical, annotationsSha256);
        appendHtmlMessageField(canonical, reconciliationSha256);
        appendHtmlMessageField(canonical, conversationKey);
        appendHtmlMessageField(canonical, sourceFingerprint);
        appendHtmlMessageField(canonical, sourceMetadata == null ? "" : sourceMetadata.optString("name")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, sourceMetadata == null ? "" : Long.toString(sourceMetadata.optLong("size", -1))); //$NON-NLS-1$
        appendHtmlMessageField(canonical, sourceMetadata == null ? "" : sourceMetadata.optString("type")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, sourceMetadata == null ? "" : sourceMetadata.optString("itemHash")); //$NON-NLS-1$
        appendHtmlMessageField(canonical, sourceMetadata == null ? "" : Boolean.toString(sourceMetadata.optBoolean("deleted", false))); //$NON-NLS-1$
        String itemSha256 = sourceMetadata == null ? "" : sourceMetadata.optString("itemSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
        if (!itemSha256.isEmpty()) {
            appendHtmlMessageField(canonical, itemSha256);
        }
        appendHtmlMessageField(canonical, ipedVersion);
        appendHtmlMessageField(canonical, pluginVersion);
        appendHtmlMessageField(canonical, startAnchor);
        appendHtmlMessageField(canonical, endAnchor);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            appendHtmlMessageField(canonical, message.optString("key")); //$NON-NLS-1$
            appendHtmlMessageField(canonical, message.optString("messageHash")); //$NON-NLS-1$
        }
        return CaptureIntegrity.sha256Text(canonical.toString());
    }

    private static void appendHtmlMessageField(StringBuilder canonical, String value) {
        String field = value == null ? "" : value; //$NON-NLS-1$
        canonical.append(field.length()).append(':').append(field).append('\n');
    }

    static final class SelectiveRepairOutcome {
        final boolean handledInPlace;
        final boolean modified;
        final List<String> restoredPaths;
        final List<String> removedPaths;
        final String summary;

        private SelectiveRepairOutcome(boolean handledInPlace, boolean modified,
                List<String> restoredPaths, List<String> removedPaths, String summary) {
            this.handledInPlace = handledInPlace;
            this.modified = modified;
            this.restoredPaths = Collections.unmodifiableList(new ArrayList<>(restoredPaths));
            this.removedPaths = Collections.unmodifiableList(new ArrayList<>(removedPaths));
            this.summary = summary == null ? "" : summary; //$NON-NLS-1$
        }

        static SelectiveRepairOutcome notHandled() {
            return new SelectiveRepairOutcome(false, false, Collections.emptyList(), Collections.emptyList(), ""); //$NON-NLS-1$
        }

        static SelectiveRepairOutcome unchanged() {
            return new SelectiveRepairOutcome(true, false, Collections.emptyList(), Collections.emptyList(), "sem alterações"); //$NON-NLS-1$
        }

        static SelectiveRepairOutcome repaired(List<String> restored, List<String> removed) {
            String summary = "restaurados=" + restored.size() + ", removidos=" + removed.size(); //$NON-NLS-1$ //$NON-NLS-2$
            return new SelectiveRepairOutcome(true, true, restored, removed, summary);
        }
    }

    private static final class CandidateAttachmentSpec {
        final String relativePath;
        final String sha256;
        final long size;
        final Path candidateFile;

        CandidateAttachmentSpec(String relativePath, String sha256, long size, Path candidateFile) {
            this.relativePath = relativePath;
            this.sha256 = sha256;
            this.size = size;
            this.candidateFile = candidateFile;
        }
    }

    static SelectiveRepairOutcome trySelectiveHtmlCaptureRepair(Path existingDir, Path candidateDir,
            boolean hasActiveOriginDir) throws IOException {
        if (existingDir == null || candidateDir == null) {
            return SelectiveRepairOutcome.notHandled();
        }
        Path existingRoot = OriginCustodyBinder.validateSafeDirectory(existingDir, "captura existente"); //$NON-NLS-1$
        Path candidateRoot = OriginCustodyBinder.validateSafeDirectory(candidateDir, "captura candidata"); //$NON-NLS-1$

        JSONObject candidateManifest = parseJsonObjectSafe(candidateRoot.resolve("manifest.json"), CaptureIntegrity.MAX_JSON_BYTES); //$NON-NLS-1$
        JSONObject candidateWrapper = parseJsonObjectSafe(candidateRoot.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES); //$NON-NLS-1$
        if (candidateManifest == null || candidateWrapper == null
                || !"html-message-v1".equals(candidateManifest.optString("captureFormat")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"completed".equals(candidateManifest.optString("status")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"complete".equals(candidateManifest.optString("state"))) { //$NON-NLS-1$ //$NON-NLS-2$
            return SelectiveRepairOutcome.notHandled();
        }
        JSONArray candidateMessages = candidateWrapper.optJSONArray("messages"); //$NON-NLS-1$
        if (candidateMessages == null || candidateMessages.length() == 0) {
            return SelectiveRepairOutcome.notHandled();
        }
        Path candidateHtmlPath = candidateRoot.resolve("whatsapp.html"); //$NON-NLS-1$
        if (!isSafeRegularFileUnderLimit(candidateHtmlPath, MAX_HTML_MESSAGE_CAPTURE_BYTES)) {
            return SelectiveRepairOutcome.notHandled();
        }
        String candidateHtmlSha = CaptureIntegrity.sha256(candidateHtmlPath);
        Path candidateAnnotationsPath = candidateRoot.resolve("annotations.json"); //$NON-NLS-1$
        String candidateAnnotationsSha = isSafeRegularFileUnderLimit(candidateAnnotationsPath, CaptureIntegrity.MAX_JSON_BYTES)
                ? CaptureIntegrity.sha256(candidateAnnotationsPath)
                : ""; //$NON-NLS-1$
        String conversationKey = candidateManifest.optString("conversationKey", ""); //$NON-NLS-1$ //$NON-NLS-2$
        Map<String, CandidateAttachmentSpec> expectedAttachments = collectCandidateAttachments(candidateRoot, candidateMessages);
        if (expectedAttachments == null) {
            return SelectiveRepairOutcome.notHandled();
        }

        JSONObject oldManifest = parseJsonObjectSafe(existingRoot.resolve("manifest.json"), CaptureIntegrity.MAX_JSON_BYTES); //$NON-NLS-1$
        JSONObject oldWrapper = parseJsonObjectSafe(existingRoot.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES); //$NON-NLS-1$
        boolean oldMessagesValidOnDisk = isExistingMessagesJsonValid(oldWrapper, candidateMessages, conversationKey);
        if (!isEligibleForSelectiveRepair(existingRoot, oldManifest, oldWrapper, oldMessagesValidOnDisk,
                candidateManifest, candidateMessages, candidateAnnotationsSha, candidateHtmlSha)) {
            return SelectiveRepairOutcome.notHandled();
        }

        String oldReconciliationSha = oldManifest == null ? "" : oldManifest.optString("reconciliationSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
        boolean preserveReconciliation = !oldReconciliationSha.isEmpty();
        if (preserveReconciliation) {
            Path recPath = existingRoot.resolve("message-reconciliation.json"); //$NON-NLS-1$
            if (parseJsonObjectSafe(recPath, CaptureIntegrity.MAX_JSON_BYTES) == null
                    || !CaptureIntegrity.sha256(recPath).equalsIgnoreCase(oldReconciliationSha)) {
                return SelectiveRepairOutcome.notHandled();
            }
        }
        boolean preserveMigration = oldManifest != null && oldManifest.optJSONObject("migration") != null; //$NON-NLS-1$
        boolean preserveLegacyAnnotations = preserveMigration
                && oldManifest.optJSONObject("migration").optBoolean("legacyAnnotationsPreserved", false); //$NON-NLS-1$ //$NON-NLS-2$
        if (preserveMigration && (!isSafeRegularFileUnderLimit(existingRoot.resolve("migration.json"), CaptureIntegrity.MAX_JSON_BYTES) //$NON-NLS-1$
                || (preserveLegacyAnnotations && !isSafeRegularFileUnderLimit(existingRoot.resolve("legacy-annotations.json"), CaptureIntegrity.MAX_JSON_BYTES)))) { //$NON-NLS-1$
            return SelectiveRepairOutcome.notHandled();
        }

        Path existingHtmlPath = existingRoot.resolve("whatsapp.html"); //$NON-NLS-1$
        String existingHtmlSha = isSafeRegularFileUnderLimit(existingHtmlPath, MAX_HTML_MESSAGE_CAPTURE_BYTES)
                ? CaptureIntegrity.sha256(existingHtmlPath)
                : ""; //$NON-NLS-1$
        String oldDeclHtmlSha = oldManifest == null ? "" : oldManifest.optString("htmlSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
        boolean htmlMatchesCandidate = !existingHtmlSha.isEmpty() && existingHtmlSha.equalsIgnoreCase(candidateHtmlSha);
        boolean htmlMatchesValidOldManifest = !existingHtmlSha.isEmpty() && oldManifest != null
                && !oldDeclHtmlSha.isEmpty() && existingHtmlSha.equalsIgnoreCase(oldDeclHtmlSha)
                && isManifestRootHashValidFor(oldManifest, candidateMessages, oldDeclHtmlSha,
                        oldManifest.optString("annotationsSha256", ""), oldReconciliationSha); //$NON-NLS-1$
        boolean htmlFileOk = htmlMatchesCandidate || htmlMatchesValidOldManifest;
        String effectiveHtmlSha = htmlFileOk ? existingHtmlSha : candidateHtmlSha;

        Path existingAnnotationsPath = existingRoot.resolve("annotations.json"); //$NON-NLS-1$
        boolean annotationsFileOk = candidateAnnotationsSha.isEmpty()
                || (parseJsonObjectSafe(existingAnnotationsPath, CaptureIntegrity.MAX_JSON_BYTES) != null
                        && CaptureIntegrity.sha256(existingAnnotationsPath).equalsIgnoreCase(candidateAnnotationsSha));

        Path existingOriginPath = existingRoot.resolve(OriginCustodyBinder.ORIGIN_BINDING_FILE);
        Path candidateOriginPath = candidateRoot.resolve(OriginCustodyBinder.ORIGIN_BINDING_FILE);
        String oldDeclOriginSha = oldManifest == null ? "" : oldManifest.optString("originBindingSha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
        boolean originExpectedOnDisk = false;
        boolean writeOriginBinding = false;
        boolean updateManifestOrigin = false;
        if (hasActiveOriginDir) {
            JSONObject candidateOriginObj = parseJsonObjectSafe(candidateOriginPath, CaptureIntegrity.MAX_JSON_BYTES);
            if (candidateOriginObj == null || candidateManifest.optJSONObject("originBinding") == null) { //$NON-NLS-1$
                return SelectiveRepairOutcome.notHandled();
            }
            originExpectedOnDisk = true;
            JSONObject oldOriginObj = parseJsonObjectSafe(existingOriginPath, CaptureIntegrity.MAX_JSON_BYTES);
            String existingOriginSha = oldOriginObj == null ? "" : CaptureIntegrity.sha256(existingOriginPath); //$NON-NLS-1$
            boolean existingOriginValid = !existingOriginSha.isEmpty()
                    && existingOriginSha.equalsIgnoreCase(oldDeclOriginSha)
                    && oldManifest != null && oldManifest.optJSONObject("originBinding") != null; //$NON-NLS-1$
            boolean semanticMatch = existingOriginValid
                    && isSemanticOriginBindingEquivalent(oldManifest, oldOriginObj, candidateManifest, candidateOriginObj);
            if (!semanticMatch) {
                writeOriginBinding = true;
                updateManifestOrigin = true;
            }
        } else if (!oldDeclOriginSha.isEmpty()) {
            if (!isSafeRegularFileUnderLimit(existingOriginPath, CaptureIntegrity.MAX_JSON_BYTES)
                    || !CaptureIntegrity.sha256(existingOriginPath).equalsIgnoreCase(oldDeclOriginSha)) {
                throw new IOException("Artefato origin-binding.json ausente ou divergente; informe Origem Avilla para restaurar o vínculo de custódia."); //$NON-NLS-1$
            }
            originExpectedOnDisk = true;
        } else if (oldManifest == null && Files.exists(existingOriginPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("manifest.json ausente com origin-binding.json presente; informe Origem Avilla para restaurar os metadados."); //$NON-NLS-1$
        }

        List<CandidateAttachmentSpec> attachmentsToRestore = new ArrayList<>();
        for (CandidateAttachmentSpec spec : expectedAttachments.values()) {
            Path target = CaptureIntegrity.resolveContained(existingRoot, spec.relativePath);
            if (!isSafeRegularFileUnderLimit(target, MAX_HTML_MESSAGE_ATTACHMENT_BYTES)
                    || CaptureIntegrity.safeRegularFileSize(target) != spec.size
                    || !CaptureIntegrity.sha256(target).equalsIgnoreCase(spec.sha256)) {
                attachmentsToRestore.add(spec);
            }
        }

        Set<String> expectedTrackableFiles = new HashSet<>(expectedAttachments.keySet());
        expectedTrackableFiles.add("messages.json"); //$NON-NLS-1$
        expectedTrackableFiles.add("whatsapp.html"); //$NON-NLS-1$
        if (!candidateAnnotationsSha.isEmpty()) expectedTrackableFiles.add("annotations.json"); //$NON-NLS-1$
        if (originExpectedOnDisk) expectedTrackableFiles.add(OriginCustodyBinder.ORIGIN_BINDING_FILE);
        if (preserveReconciliation) expectedTrackableFiles.add("message-reconciliation.json"); //$NON-NLS-1$
        if (preserveMigration) expectedTrackableFiles.add("migration.json"); //$NON-NLS-1$
        if (preserveLegacyAnnotations) expectedTrackableFiles.add("legacy-annotations.json"); //$NON-NLS-1$

        Set<String> allowedFiles = new HashSet<>(expectedTrackableFiles);
        allowedFiles.add("manifest.json"); //$NON-NLS-1$
        allowedFiles.add("integridade.json"); //$NON-NLS-1$
        allowedFiles.add(CaptureIntegrity.AUDIT_FILE);

        List<String> extraFilesToDelete = new ArrayList<>();
        Path normalizedExistingRoot = existingRoot.toAbsolutePath().normalize();
        for (Path file : CaptureIntegrity.listRegularFilesSafe(normalizedExistingRoot, CaptureIntegrity.MAX_INVENTORY_FILES)) {
            String rel = normalizedExistingRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            if (!allowedFiles.contains(rel)) {
                extraFilesToDelete.add(rel);
            }
        }

        boolean manifestNeedsWrite = !isManifestValidForTarget(oldManifest, candidateMessages, effectiveHtmlSha,
                candidateAnnotationsSha, oldReconciliationSha, candidateManifest, updateManifestOrigin);

        JSONObject prevIntegrity = parseJsonObjectSafe(existingRoot.resolve("integridade.json"), CaptureIntegrity.MAX_JSON_BYTES); //$NON-NLS-1$
        boolean anyFileChanged = !oldMessagesValidOnDisk || !htmlFileOk || !annotationsFileOk
                || !attachmentsToRestore.isEmpty() || !extraFilesToDelete.isEmpty()
                || writeOriginBinding || manifestNeedsWrite;
        if (!anyFileChanged && isExistingIntegrityValidForDisk(existingRoot, prevIntegrity, expectedTrackableFiles)) {
            return SelectiveRepairOutcome.unchanged();
        }

        List<String> restoredPaths = new ArrayList<>();
        List<String> removedPaths = new ArrayList<>();
        for (String extraRel : extraFilesToDelete) {
            Path extraTarget = CaptureIntegrity.resolveContained(existingRoot, extraRel);
            if (Files.deleteIfExists(extraTarget)) {
                removedPaths.add(extraRel);
                CaptureIntegrity.appendAudit(existingRoot, new JSONObject()
                        .put("action", "unexpected_artifact_removed_by_selective_repair") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("path", extraRel)); //$NON-NLS-1$
            }
        }
        deleteEmptyNonCoreSubdirectories(existingRoot);

        for (CandidateAttachmentSpec spec : attachmentsToRestore) {
            byte[] bytes = Files.readAllBytes(spec.candidateFile);
            if (bytes.length != spec.size || !CaptureIntegrity.sha256Bytes(bytes).equalsIgnoreCase(spec.sha256)) {
                throw new IOException("Anexo candidato divergente durante reparo seletivo: " + spec.relativePath); //$NON-NLS-1$
            }
            Path target = CaptureIntegrity.resolveContained(existingRoot, spec.relativePath);
            CaptureIntegrity.writeBytesAtomically(existingRoot, target, bytes, MAX_HTML_MESSAGE_ATTACHMENT_BYTES);
            restoredPaths.add(spec.relativePath);
        }
        if (!oldMessagesValidOnDisk) {
            copyRegularFileAtomically(candidateRoot.resolve("messages.json"), existingRoot, existingRoot.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES); //$NON-NLS-1$ //$NON-NLS-2$
            restoredPaths.add("messages.json"); //$NON-NLS-1$
        }
        if (!htmlFileOk) {
            copyRegularFileAtomically(candidateHtmlPath, existingRoot, existingHtmlPath, MAX_HTML_MESSAGE_CAPTURE_BYTES);
            restoredPaths.add("whatsapp.html"); //$NON-NLS-1$
        }
        if (!annotationsFileOk && !candidateAnnotationsSha.isEmpty()) {
            copyRegularFileAtomically(candidateAnnotationsPath, existingRoot, existingAnnotationsPath, CaptureIntegrity.MAX_JSON_BYTES);
            restoredPaths.add("annotations.json"); //$NON-NLS-1$
        }
        if (writeOriginBinding) {
            copyRegularFileAtomically(candidateOriginPath, existingRoot, existingOriginPath, CaptureIntegrity.MAX_JSON_BYTES);
            restoredPaths.add(OriginCustodyBinder.ORIGIN_BINDING_FILE);
        }
        if (manifestNeedsWrite) {
            JSONObject updatedManifest = buildUpdatedSelectiveManifest(oldManifest, candidateManifest, candidateMessages,
                    effectiveHtmlSha, candidateAnnotationsSha, oldReconciliationSha, updateManifestOrigin);
            CaptureIntegrity.writeAtomically(existingRoot, existingRoot.resolve("manifest.json"), updatedManifest.toString(2)); //$NON-NLS-1$
            restoredPaths.add("manifest.json"); //$NON-NLS-1$
        }

        boolean prevChainValid = isIntegrityEventChainValid(prevIntegrity);
        JSONObject nextIntegrity = CaptureIntegrity.integrityRecord(existingRoot, prevChainValid ? prevIntegrity : null,
                "recapture_selective_repair", CHAT_CAPTURE_PLUGIN_VERSION); //$NON-NLS-1$
        CaptureIntegrity.writeAtomically(existingRoot, existingRoot.resolve("integridade.json"), nextIntegrity.toString(2)); //$NON-NLS-1$
        restoredPaths.add("integridade.json"); //$NON-NLS-1$

        CaptureIntegrity.appendAudit(existingRoot, new JSONObject()
                .put("action", "recapture_selective_repair") //$NON-NLS-1$ //$NON-NLS-2$
                .put("restoredPaths", new JSONArray(restoredPaths)) //$NON-NLS-1$
                .put("removedPaths", new JSONArray(removedPaths))); //$NON-NLS-1$
        return SelectiveRepairOutcome.repaired(restoredPaths, removedPaths);
    }

    static boolean isReadableHtmlMessagesJson(Path captureDir) {
        if (captureDir == null) return false;
        return parseJsonObjectSafe(captureDir.resolve("messages.json"), MAX_HTML_MESSAGE_CAPTURE_BYTES) != null; //$NON-NLS-1$
    }

    private static JSONObject parseJsonObjectSafe(Path path, long maxBytes) {
        if (path == null || !isSafeRegularFileUnderLimit(path, maxBytes)) {
            return null;
        }
        try {
            return new JSONObject(CaptureIntegrity.readJsonUtf8Limited(path, maxBytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSafeRegularFileUnderLimit(Path path, long maxBytes) {
        try {
            return path != null && !CaptureIntegrity.isLinkOrReparse(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && CaptureIntegrity.safeRegularFileSize(path) <= maxBytes;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyRegularFileAtomically(Path source, Path captureRoot, Path destination, long maxBytes) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        CaptureIntegrity.writeBytesAtomically(captureRoot, destination, bytes, maxBytes);
    }

    private static Map<String, CandidateAttachmentSpec> collectCandidateAttachments(Path candidateRoot, JSONArray candidateMessages)
            throws IOException {
        Map<String, CandidateAttachmentSpec> map = new LinkedHashMap<>();
        for (int i = 0; i < candidateMessages.length(); i++) {
            JSONObject msg = candidateMessages.optJSONObject(i);
            if (msg == null) return null;
            JSONArray atts = msg.optJSONArray("attachments"); //$NON-NLS-1$
            if (atts == null) continue;
            for (int j = 0; j < atts.length(); j++) {
                JSONObject att = atts.optJSONObject(j);
                if (att == null) return null;
                String relPath = att.optString("path", ""); //$NON-NLS-1$ //$NON-NLS-2$
                String sha = att.optString("sha256", ""); //$NON-NLS-1$ //$NON-NLS-2$
                long size = att.optLong("size", -1L); //$NON-NLS-1$
                if (relPath.isEmpty() || sha.isEmpty() || size < 1 || size > MAX_HTML_MESSAGE_ATTACHMENT_BYTES) {
                    return null;
                }
                Path resolved = CaptureIntegrity.resolveContained(candidateRoot, relPath);
                if (!isSafeRegularFileUnderLimit(resolved, MAX_HTML_MESSAGE_ATTACHMENT_BYTES)
                        || CaptureIntegrity.safeRegularFileSize(resolved) != size
                        || !CaptureIntegrity.sha256(resolved).equalsIgnoreCase(sha)) {
                    return null;
                }
                map.putIfAbsent(relPath, new CandidateAttachmentSpec(relPath, sha, size, resolved));
            }
        }
        return map;
    }

    private static boolean isEligibleForSelectiveRepair(Path existingRoot, JSONObject oldManifest, JSONObject oldWrapper,
            boolean oldMessagesValidOnDisk, JSONObject candidateManifest, JSONArray candidateMessages,
            String candidateAnnotationsSha, String candidateHtmlSha) throws IOException {
        if (oldManifest != null) {
            if (oldManifest.has("captureFormat") && !"html-message-v1".equals(oldManifest.optString("captureFormat"))) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
            String oldKey = oldManifest.optString("conversationKey", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String candKey = candidateManifest.optString("conversationKey", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (!oldKey.isEmpty() && !oldKey.equalsIgnoreCase(candKey)) {
                return false;
            }
            JSONObject oldAnchors = oldManifest.optJSONObject("anchors"); //$NON-NLS-1$
            JSONObject candAnchors = candidateManifest.optJSONObject("anchors"); //$NON-NLS-1$
            if (oldAnchors != null && candAnchors != null
                    && (!oldAnchors.optString("start", "").equals(candAnchors.optString("start", "")) //$NON-NLS-1$ //$NON-NLS-2$
                            || !oldAnchors.optString("end", "").equals(candAnchors.optString("end", "")))) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
            if (!censorshipPresentationDigest(oldManifest).equalsIgnoreCase(censorshipPresentationDigest(candidateManifest))) {
                return false;
            }
            if (!oldManifest.optString("annotationsSha256", "").equalsIgnoreCase(candidateAnnotationsSha)) { //$NON-NLS-1$
                return false;
            }
            if (oldMessagesValidOnDisk) {
                return true;
            }
            JSONArray oldPublicHashes = oldManifest.optJSONArray("messageHashes"); //$NON-NLS-1$
            return (oldPublicHashes != null && sameMessageHashSequence(oldPublicHashes, candidateMessages))
                    || sameLegacySenderMessageSequence(oldWrapper, candidateMessages);
        }
        if (!oldMessagesValidOnDisk) {
            return false;
        }
        if (Files.exists(existingRoot.resolve("message-reconciliation.json"), LinkOption.NOFOLLOW_LINKS) //$NON-NLS-1$
                || Files.exists(existingRoot.resolve("migration.json"), LinkOption.NOFOLLOW_LINKS)) { //$NON-NLS-1$
            return false;
        }
        if (!censorshipPresentationDigest(candidateManifest).isEmpty()) {
            Path existingHtmlPath = existingRoot.resolve("whatsapp.html"); //$NON-NLS-1$
            if (!isSafeRegularFileUnderLimit(existingHtmlPath, MAX_HTML_MESSAGE_CAPTURE_BYTES)
                    || !CaptureIntegrity.sha256(existingHtmlPath).equalsIgnoreCase(candidateHtmlSha)) {
                return false;
            }
        }
        return true;
    }

    private static String censorshipPresentationDigest(JSONObject manifest) {
        if (manifest == null) return ""; //$NON-NLS-1$
        JSONObject censorship = manifest.optJSONObject("censorship"); //$NON-NLS-1$
        return censorship == null ? "" : censorship.optString("presentationDigest", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean sameMessageHashSequence(JSONArray left, JSONArray right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        for (int i = 0; i < left.length(); i++) {
            JSONObject a = left.optJSONObject(i);
            JSONObject b = right.optJSONObject(i);
            if (a == null || b == null) return false;
            if (!a.optString("domId", "").equals(b.optString("domId", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("key", "").equalsIgnoreCase(b.optString("key", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("messageHash", "").equalsIgnoreCase(b.optString("messageHash", ""))) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
        }
        return true;
    }

    private static boolean sameLegacySenderMessageSequence(JSONObject oldWrapper, JSONArray candidateMessages) {
        if (oldWrapper == null || candidateMessages == null) {
            return false;
        }
        JSONArray existingMessages = oldWrapper.optJSONArray("messages"); //$NON-NLS-1$
        if (existingMessages == null || existingMessages.length() != candidateMessages.length()) {
            return false;
        }
        for (int i = 0; i < existingMessages.length(); i++) {
            JSONObject a = existingMessages.optJSONObject(i);
            JSONObject b = candidateMessages.optJSONObject(i);
            if (a == null || b == null) return false;
            if (!a.optString("domId", "").equals(b.optString("domId", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || a.optInt("order", -1) != b.optInt("order", -2) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("timestamp", "").equals(b.optString("timestamp", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("direction", "").equals(b.optString("direction", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("type", "").equals(b.optString("type", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("text", "").equals(b.optString("text", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !a.optString("classes", "").equals(b.optString("classes", "")) //$NON-NLS-1$ //$NON-NLS-2$
                    || !CaptureIntegrity.canonicalJson(a.optJSONArray("attachments")) //$NON-NLS-1$
                            .equals(CaptureIntegrity.canonicalJson(b.optJSONArray("attachments")))) { //$NON-NLS-1$
                return false;
            }
            String oldSender = a.optString("sender", ""); //$NON-NLS-1$ //$NON-NLS-2$
            String newSender = b.optString("sender", ""); //$NON-NLS-1$ //$NON-NLS-2$
            if (!oldSender.equals(newSender) && !oldSender.equals(a.optString("text", ""))) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
        }
        return true;
    }

    private static boolean isExistingMessagesJsonValid(JSONObject oldWrapper, JSONArray candidateMessages,
            String conversationKey) {
        if (oldWrapper == null || candidateMessages == null || conversationKey == null || conversationKey.isEmpty()) {
            return false;
        }
        if (oldWrapper.keySet().size() != 1 || !oldWrapper.has("messages")) { //$NON-NLS-1$
            return false;
        }
        JSONArray existingMessages = oldWrapper.optJSONArray("messages"); //$NON-NLS-1$
        if (!sameMessageHashSequence(existingMessages, candidateMessages)) {
            return false;
        }
        Map<String, Integer> ordinals = new HashMap<>();
        try {
            for (int i = 0; i < existingMessages.length(); i++) {
                JSONObject row = existingMessages.optJSONObject(i);
                JSONObject cand = candidateMessages.optJSONObject(i);
                if (row == null || cand == null || row.optInt("order", -1) != i) { //$NON-NLS-1$
                    return false;
                }
                String natural = row.optString("timestamp", "") + "\n" + row.optString("sender", "") + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + row.optString("direction", "") + "\n" + row.optString("type", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                int expectedOrdinal = ordinals.getOrDefault(natural, 0);
                ordinals.put(natural, expectedOrdinal + 1);
                if (row.optInt("duplicateOrdinal", -1) != expectedOrdinal) { //$NON-NLS-1$
                    return false;
                }
                String expectedKey = CaptureIntegrity.sha256Text(
                        conversationKey + "\n" + row.optString("domId", "") + "\n" + natural + "\n" + expectedOrdinal); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (!expectedKey.equalsIgnoreCase(row.optString("key", ""))) { //$NON-NLS-1$
                    return false;
                }
                if (!computeHtmlMessageHash(row).equalsIgnoreCase(row.optString("messageHash", ""))) { //$NON-NLS-1$
                    return false;
                }
                if (!CaptureIntegrity.canonicalJson(row).equals(CaptureIntegrity.canonicalJson(cand))) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isSemanticOriginBindingEquivalent(JSONObject oldManifest, JSONObject oldOrigin,
            JSONObject candidateManifest, JSONObject candidateOrigin) {
        if (oldManifest == null || oldOrigin == null || candidateManifest == null || candidateOrigin == null) {
            return false;
        }
        JSONObject oldSummary = oldManifest.optJSONObject("originBinding"); //$NON-NLS-1$
        JSONObject candSummary = candidateManifest.optJSONObject("originBinding"); //$NON-NLS-1$
        if (oldSummary == null || candSummary == null
                || !CaptureIntegrity.canonicalJson(oldSummary).equals(CaptureIntegrity.canonicalJson(candSummary))) {
            return false;
        }
        String[] trackedSections = new String[] {
                "originBindingVersion", "status", "collectionCode", "originFolderRelativeName", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "custodyFiles", "hashsTxtIndexedEntries", "sourceDatabase", "postExtractionComplement", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "ipedLineage", "attachmentsOrigin", "n3SqliteAudit", "custodyTimelineGap" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        };
        for (String key : trackedSections) {
            if (!CaptureIntegrity.canonicalJson(oldOrigin.opt(key)).equals(CaptureIntegrity.canonicalJson(candidateOrigin.opt(key)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isManifestRootHashValidFor(JSONObject manifest, JSONArray messages, String htmlSha,
            String annotationsSha, String reconciliationSha) {
        if (manifest == null || messages == null) return false;
        JSONObject anchors = manifest.optJSONObject("anchors"); //$NON-NLS-1$
        if (anchors == null) return false;
        try {
            String expected = computeHtmlCaptureRootHash(messages, htmlSha, annotationsSha, reconciliationSha,
                    manifest.optString("conversationKey", ""), manifest.optString("sourceFingerprint", ""), //$NON-NLS-1$ //$NON-NLS-2$
                    manifest.optJSONObject("sourceMetadata"), manifest.optString("ipedVersion", "4.3.1"), //$NON-NLS-1$ //$NON-NLS-2$
                    manifest.optString("pluginVersion", CHAT_CAPTURE_PLUGIN_VERSION),
                    anchors.optString("start", ""), anchors.optString("end", "")); //$NON-NLS-1$ //$NON-NLS-2$
            return expected.equalsIgnoreCase(manifest.optString("captureRootHash", "")); //$NON-NLS-1$
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isManifestValidForTarget(JSONObject oldManifest, JSONArray candidateMessages,
            String effectiveHtmlSha, String candidateAnnotationsSha, String reconciliationSha,
            JSONObject candidateManifest, boolean updateManifestOrigin) {
        if (oldManifest == null || updateManifestOrigin) {
            return false;
        }
        if (oldManifest.optInt("schemaVersion", 0) != 1
                || !"html-message-v1".equals(oldManifest.optString("captureFormat")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"4.3.1".equals(oldManifest.optString("ipedVersion")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"completed".equals(oldManifest.optString("status")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"complete".equals(oldManifest.optString("state")) //$NON-NLS-1$ //$NON-NLS-2$
                || !"messages.json".equals(oldManifest.optString("messagesFile")) //$NON-NLS-1$ //$NON-NLS-2$
                || oldManifest.optInt("messageCount", -1) != candidateMessages.length()) { //$NON-NLS-1$
            return false;
        }
        if (!oldManifest.optString("htmlSha256", "").equalsIgnoreCase(effectiveHtmlSha) //$NON-NLS-1$
                || !oldManifest.optString("annotationsSha256", "").equalsIgnoreCase(candidateAnnotationsSha) //$NON-NLS-1$
                || !oldManifest.optString("reconciliationSha256", "").equalsIgnoreCase(reconciliationSha) //$NON-NLS-1$
                || !oldManifest.optString("conversationKey", "").equalsIgnoreCase(candidateManifest.optString("conversationKey", "")) //$NON-NLS-1$ //$NON-NLS-2$
                || !oldManifest.optString("sourceFingerprint", "").equalsIgnoreCase(candidateManifest.optString("sourceFingerprint", ""))) { //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        JSONObject anchors = oldManifest.optJSONObject("anchors"); //$NON-NLS-1$
        if (anchors == null
                || !anchors.optString("start", "").equals(candidateMessages.getJSONObject(0).optString("domId", "")) //$NON-NLS-1$ //$NON-NLS-2$
                || !anchors.optString("end", "").equals(candidateMessages.getJSONObject(candidateMessages.length() - 1).optString("domId", ""))) { //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        if (oldManifest.optJSONObject("sourceMetadata") == null
                || !sameMessageHashSequence(oldManifest.optJSONArray("messageHashes"), candidateMessages)) {
            return false;
        }
        return isManifestRootHashValidFor(oldManifest, candidateMessages, effectiveHtmlSha, candidateAnnotationsSha, reconciliationSha);
    }

    private static JSONObject buildUpdatedSelectiveManifest(JSONObject oldManifest, JSONObject candidateManifest,
            JSONArray candidateMessages, String effectiveHtmlSha, String candidateAnnotationsSha,
            String oldReconciliationSha, boolean updateManifestOrigin) throws IOException {
        boolean salvageOld = oldManifest != null
                && !oldManifest.optString("conversationKey", "").isEmpty() //$NON-NLS-1$
                && !oldManifest.optString("sourceFingerprint", "").isEmpty() //$NON-NLS-1$
                && oldManifest.optJSONObject("sourceMetadata") != null; //$NON-NLS-1$
        JSONObject target = new JSONObject((salvageOld ? oldManifest : candidateManifest).toString());
        target.put("schemaVersion", 1) //$NON-NLS-1$
                .put("captureFormat", "html-message-v1") //$NON-NLS-1$ //$NON-NLS-2$
                .put("ipedVersion", salvageOld ? oldManifest.optString("ipedVersion", "4.3.1") : candidateManifest.optString("ipedVersion", "4.3.1")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("pluginVersion", salvageOld ? oldManifest.optString("pluginVersion", CHAT_CAPTURE_PLUGIN_VERSION) : candidateManifest.optString("pluginVersion", CHAT_CAPTURE_PLUGIN_VERSION)) //$NON-NLS-1$
                .put("status", "completed") //$NON-NLS-1$ //$NON-NLS-2$
                .put("state", "complete") //$NON-NLS-1$ //$NON-NLS-2$
                .put("messagesFile", "messages.json") //$NON-NLS-1$ //$NON-NLS-2$
                .put("messageCount", candidateMessages.length()) //$NON-NLS-1$
                .put("anchors", new JSONObject(candidateManifest.getJSONObject("anchors").toString())) //$NON-NLS-1$
                .put("htmlSha256", effectiveHtmlSha) //$NON-NLS-1$
                .put("annotationsSha256", candidateAnnotationsSha) //$NON-NLS-1$
                .put("messageHashes", new JSONArray(candidateManifest.getJSONArray("messageHashes").toString())); //$NON-NLS-1$
        if (!oldReconciliationSha.isEmpty()) {
            target.put("reconciliationSha256", oldReconciliationSha); //$NON-NLS-1$
            target.put("reconciliationFile", "message-reconciliation.json"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (updateManifestOrigin) {
            target.put("originBinding", new JSONObject(candidateManifest.getJSONObject("originBinding").toString())) //$NON-NLS-1$
                    .put("originBindingFile", OriginCustodyBinder.ORIGIN_BINDING_FILE) //$NON-NLS-1$
                    .put("originBindingSha256", candidateManifest.getString("originBindingSha256")); //$NON-NLS-1$
        }
        if (target.optString("createdAt", "").isEmpty()) { //$NON-NLS-1$
            target.put("createdAt", candidateManifest.optString("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString())); //$NON-NLS-1$
        }
        JSONObject anchors = target.getJSONObject("anchors"); //$NON-NLS-1$
        target.put("captureRootHash", computeHtmlCaptureRootHash(candidateMessages, effectiveHtmlSha, candidateAnnotationsSha,
                oldReconciliationSha, target.getString("conversationKey"), target.getString("sourceFingerprint"),
                target.getJSONObject("sourceMetadata"), target.getString("ipedVersion"), target.getString("pluginVersion"),
                anchors.getString("start"), anchors.getString("end")));
        return target;
    }

    private static boolean isIntegrityEventChainValid(JSONObject integrityObj) {
        if (integrityObj == null || integrityObj.optInt("schemaVersion", 0) < 2) {
            return false;
        }
        JSONArray artifacts = integrityObj.optJSONArray("artifacts"); //$NON-NLS-1$
        JSONArray events = integrityObj.optJSONArray("events"); //$NON-NLS-1$
        if (artifacts == null || artifacts.length() == 0 || events == null || events.length() == 0) {
            return false;
        }
        try {
            List<JSONObject> sorted = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < artifacts.length(); i++) {
                JSONObject item = artifacts.optJSONObject(i);
                if (item == null) return false;
                String rel = item.optString("path", ""); //$NON-NLS-1$
                String sha = item.optString("sha256", ""); //$NON-NLS-1$
                if (rel.isEmpty() || !seen.add(rel.toLowerCase(Locale.ROOT))
                        || !sha.matches("(?i)[0-9a-f]{64}") || item.optLong("size", -1L) < 0) { //$NON-NLS-1$
                    return false;
                }
                sorted.add(item);
            }
            sorted.sort(Comparator.comparing(x -> x.optString("path", ""))); //$NON-NLS-1$
            StringBuilder inv = new StringBuilder();
            for (JSONObject item : sorted) {
                inv.append(item.optString("path", "")).append('|') //$NON-NLS-1$
                        .append(item.optLong("size", 0)).append('|') //$NON-NLS-1$
                        .append(item.optString("sha256", "")).append('\n'); //$NON-NLS-1$
            }
            String expectedRoot = CaptureIntegrity.sha256Text(inv.toString());
            if (!expectedRoot.equalsIgnoreCase(integrityObj.optString("contentRootHash", ""))) { //$NON-NLS-1$
                return false;
            }
            String prevHash = ""; //$NON-NLS-1$
            String lastRoot = ""; //$NON-NLS-1$
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null || event.optInt("sequence", -1) != i + 1) return false;
                if (!prevHash.equals(event.optString("previousRecordHash", ""))) return false;
                String eventRoot = event.optString("contentRootHash", ""); //$NON-NLS-1$
                String recordHash = event.optString("recordHash", ""); //$NON-NLS-1$
                if (!eventRoot.matches("(?i)[0-9a-f]{64}") || !recordHash.matches("(?i)[0-9a-f]{64}")) { //$NON-NLS-1$
                    return false;
                }
                JSONObject payload = new JSONObject(event.toString());
                payload.remove("recordHash"); //$NON-NLS-1$
                String fmt = event.optString("recordHashFormat", ""); //$NON-NLS-1$
                String canonical = CaptureIntegrity.sha256Text(CaptureIntegrity.canonicalJson(payload));
                boolean hashOk = "canonical-v1".equals(fmt) //$NON-NLS-1$
                        ? recordHash.equalsIgnoreCase(canonical)
                        : (recordHash.equalsIgnoreCase(canonical) || recordHash.equalsIgnoreCase(CaptureIntegrity.sha256Text(payload.toString())));
                if (!hashOk) return false;
                prevHash = recordHash;
                lastRoot = eventRoot;
            }
            return expectedRoot.equalsIgnoreCase(lastRoot);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isExistingIntegrityValidForDisk(Path captureDir, JSONObject integrityObj,
            Set<String> expectedTrackableFiles) {
        if (!isIntegrityEventChainValid(integrityObj)) {
            return false;
        }
        try {
            JSONArray artifacts = integrityObj.optJSONArray("artifacts"); //$NON-NLS-1$
            Set<String> recorded = new HashSet<>();
            for (int i = 0; i < artifacts.length(); i++) {
                JSONObject row = artifacts.optJSONObject(i);
                String rel = row.optString("path", ""); //$NON-NLS-1$
                recorded.add(rel);
                Path file = CaptureIntegrity.resolveContained(captureDir, rel);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || CaptureIntegrity.safeRegularFileSize(file) != row.optLong("size", -1L) //$NON-NLS-1$
                        || !CaptureIntegrity.sha256(file).equalsIgnoreCase(row.optString("sha256", ""))) { //$NON-NLS-1$
                    return false;
                }
            }
            return recorded.containsAll(expectedTrackableFiles);
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteEmptyNonCoreSubdirectories(Path root) throws IOException {
        Files.walkFileTree(root, Collections.emptySet(), 16, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                if (CaptureIntegrity.isLinkOrReparse(dir)) {
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                if (!dir.equals(root) && !CaptureIntegrity.isLinkOrReparse(dir)) {
                    try (Stream<Path> children = Files.list(dir)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(dir);
                        }
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static class ReusableAttachment {
        final Path path;
        final String sha256;
        final long size;
        ReusableAttachment(Path path, String sha256, long size) {
            this.path = path;
            this.sha256 = sha256;
            this.size = size;
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
        // Capturas vinculadas usam nome deterministico (chatRefId), sem timestamp,
        // para que o HTML da origem consiga localizar a pasta sem re-extrair.
        if (linkedCaptureViewer) {
            return name;
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

    /**
     * Quando o usuario habilita "Limpar registros e criar como nova captura" no Re-Extrair,
     * remove a pasta {@code integridade/} e o arquivo {@code integridade.html} da raiz para
     * que o conjunto re-extraido fique limpo como uma captura nova, sem criar backups.
     */
    private void deleteRootIntegrityReportsIfExists(Path outputRoot) {
        if (outputRoot == null || !Files.isDirectory(outputRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(outputRoot)) {
            return;
        }
        Path reportsDir = outputRoot.resolve("integridade"); //$NON-NLS-1$
        try {
            if (Files.exists(reportsDir, LinkOption.NOFOLLOW_LINKS)) {
                if (CaptureIntegrity.isLinkOrReparse(reportsDir)) {
                    Files.deleteIfExists(reportsDir);
                } else if (Files.isDirectory(reportsDir, LinkOption.NOFOLLOW_LINKS)) {
                    deleteDirectoryIfExists(reportsDir);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to delete root integrity folder at {}", reportsDir, e); //$NON-NLS-1$
        }
        Path reportHtml = outputRoot.resolve("integridade.html"); //$NON-NLS-1$
        try {
            if (Files.isRegularFile(reportHtml, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(reportHtml)) {
                Files.deleteIfExists(reportHtml);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to delete root integrity HTML report at {}", reportHtml, e); //$NON-NLS-1$
        }
    }

    private void rebuildCaptureRootIndex(Path outputRoot) throws IOException {
        if (!CaptureIntegrity.isSafeDirectory(outputRoot)) {
            return;
        }
        List<CaptureRootEntry> captures = new ArrayList<>();
        try (Stream<Path> children = Files.list(outputRoot).limit(MAX_RECAPTURE_CANDIDATES * 2L)) {
            children.filter(CaptureIntegrity::isSafeDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("_backup_chat_capture_")) //$NON-NLS-1$
                    .filter(path -> !isRecaptureAuxiliaryFolder(path))
                    .filter(path -> !isReportAuxiliaryFolder(path))
                    .filter(path -> !isLinkedCaptureFolder(path))
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
        sortCaptureRootEntries(captures);
        String rootHash = calculateRootHash(captures);
        this.lastKnownRootHash = rootHash;
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
        String[] chronologicalBounds = new String[] { "", "" }; //$NON-NLS-1$ //$NON-NLS-2$
        if (CaptureIntegrity.isSafeRegularFile(manifest)) {
            if (!populateCaptureRootManifestJson(entry, CaptureIntegrity.readJsonUtf8Limited(manifest), chronologicalBounds)) {
                LOGGER.warn("Invalid manifest JSON in {}", manifest); //$NON-NLS-1$
            }
        }

        Path coordinates = captureDir.resolve("whatsapp-coordinates.json"); //$NON-NLS-1$
        if (((entry.title == null || entry.title.isBlank()) || chronologicalBounds[0].isBlank() || chronologicalBounds[1].isBlank())
                && CaptureIntegrity.isSafeRegularFile(coordinates)) {
            if (!populateCaptureRootCoordinatesJson(entry, CaptureIntegrity.readJsonUtf8Limited(coordinates), chronologicalBounds)) {
                LOGGER.warn("Invalid coordinates JSON in {}", coordinates); //$NON-NLS-1$
            }
        }
        String chronologicalStart = chronologicalBounds[0];
        String chronologicalEnd = chronologicalBounds[1];

        Path text = captureDir.resolve("texto.txt"); //$NON-NLS-1$
        if (CaptureIntegrity.isSafeRegularFile(text)) {
            CaptureTextHeader header = readCaptureTextHeader(text);
            entry.chatId = nonBlank(entry.chatId, header.chatId);
            entry.title = nonBlank(entry.title, header.title);
        }

        entry.title = nonBlank(entry.title, entry.folder);
        entry.chatId = nonBlank(entry.chatId, inferChatId(entry.title));
        fillChronologicalFields(entry, chronologicalStart, chronologicalEnd);
        entry.frameCount = countRegularFiles(CaptureIntegrity.resolveContained(captureDir, "screenshots")); //$NON-NLS-1$
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

    static boolean populateCaptureRootManifestJson(CaptureRootEntry entry, String manifestContent,
            String[] chronologicalBounds) {
        JSONObject manifestJson = parseJsonObjectOrNull(manifestContent);
        if (manifestJson == null) {
            entry.status = "invalid"; //$NON-NLS-1$
            return false;
        }
        // Capturas html-message-v1 usam um esquema de anotações próprio; o campo "version"
        // do manifesto nao indica anotações legadas nessas capturas.
        entry.legacyAnnotations = !"html-message-v1".equals(jsonString(manifestJson, "captureFormat")) //$NON-NLS-1$ //$NON-NLS-2$
                && jsonLong(manifestJson, "version", 1L) < 2L; //$NON-NLS-1$
        entry.status = nonBlank(jsonString(manifestJson, "status"), entry.status); //$NON-NLS-1$
        chronologicalBounds[0] = readAnchor(manifestJson, "startAnchor").date; //$NON-NLS-1$
        chronologicalBounds[1] = readAnchor(manifestJson, "endAnchor").date; //$NON-NLS-1$
        JSONObject annotations = manifestJson.optJSONObject("annotations"); //$NON-NLS-1$
        entry.annotationsEnabled = annotations != null;
        JSONObject counts = entry.annotationsEnabled ? annotations.optJSONObject("counts") : null; //$NON-NLS-1$
        if (counts != null) {
            entry.notesCount = (int) jsonLong(counts, "notes", 0L); //$NON-NLS-1$
            entry.linksCount = (int) jsonLong(counts, "annotatedMessages", 0L); //$NON-NLS-1$
            entry.contactsCount = (int) jsonLong(counts, "contacts", 0L); //$NON-NLS-1$
        }
        JSONObject source = manifestJson.optJSONObject("sourceMetadata"); //$NON-NLS-1$
        entry.title = firstNonBlank(jsonString(source, "nome"), jsonString(source, "name"), jsonString(source, "title")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        entry.chatId = inferChatId(entry.title);
        entry.sizeBytes = jsonLong(source, "size", jsonLong(source, "tamanho", 0L)); //$NON-NLS-1$ //$NON-NLS-2$
        return true;
    }

    static boolean populateCaptureRootCoordinatesJson(CaptureRootEntry entry, String coordContent,
            String[] chronologicalBounds) {
        JSONObject coordJson = parseJsonObjectOrNull(coordContent);
        if (coordJson == null) {
            return false;
        }
        if (entry.title == null || entry.title.isBlank()) {
            JSONObject source = coordJson.optJSONObject("sourceMetadata"); //$NON-NLS-1$
            entry.title = firstNonBlank(jsonString(source, "nome"), jsonString(source, "name"), jsonString(source, "title")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            entry.chatId = inferChatId(entry.title);
            entry.status = nonBlank(jsonString(coordJson, "status"), entry.status); //$NON-NLS-1$
        }
        chronologicalBounds[0] = nonBlank(chronologicalBounds[0], readAnchor(coordJson, "startAnchor").date); //$NON-NLS-1$
        chronologicalBounds[1] = nonBlank(chronologicalBounds[1], readAnchor(coordJson, "endAnchor").date); //$NON-NLS-1$
        return true;
    }

    private void sortCaptureRootEntries(List<CaptureRootEntry> captures) {
        captures.sort(Comparator
                .comparing((CaptureRootEntry entry) -> safe(entry.chronologicalGroup))
                .thenComparing(entry -> safe(entry.chronologicalSortKey))
                .thenComparing(entry -> safe(entry.chronologicalEnd))
                .thenComparing(entry -> safe(entry.folder).toLowerCase()));
    }

    private void fillChronologicalFields(CaptureRootEntry entry, String start, String end) {
        String chronologicalStart = safe(start).trim();
        String chronologicalEnd = safe(end).trim();
        if (chronologicalStart.isBlank()) {
            chronologicalStart = safe(entry.createdAt).trim();
        }
        entry.chronologicalStart = chronologicalStart;
        entry.chronologicalEnd = chronologicalEnd;
        entry.chronologicalRange = chronologicalRange(chronologicalStart, chronologicalEnd);
        entry.chronologicalGroup = buildChronologicalGroup(entry);
        entry.chronologicalSortKey = normalizeChronologicalSortKey(firstNonBlank(chronologicalStart, entry.createdAt));
    }

    private String chronologicalRange(String start, String end) {
        if (safe(start).isBlank() && safe(end).isBlank()) {
            return "-"; //$NON-NLS-1$
        }
        if (safe(end).isBlank() || safe(start).equals(safe(end))) {
            return safe(start);
        }
        return safe(start) + " -> " + safe(end); //$NON-NLS-1$
    }

    private String buildChronologicalGroup(CaptureRootEntry entry) {
        String id = safe(entry.chatId).trim();
        String title = safe(entry.title).trim();
        return (id + "|" + title).trim().toLowerCase(); //$NON-NLS-1$
    }

    private String normalizeChronologicalSortKey(String value) {
        String text = safe(value).trim();
        if (text.isBlank()) {
            return ""; //$NON-NLS-1$
        }
        DateTimeFormatter[] offsetFormats = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"), //$NON-NLS-1$
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z") //$NON-NLS-1$
        };
        for (DateTimeFormatter formatter : offsetFormats) {
            try {
                return OffsetDateTime.parse(text, formatter).toInstant().toString();
            } catch (Exception e) {
                // Try the next supported timestamp shape.
            }
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'UTC'")) //$NON-NLS-1$
                    .atOffset(ZoneOffset.UTC).toInstant().toString();
        } catch (Exception e) {
            // Fall back below.
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toString(); //$NON-NLS-1$
        } catch (Exception e) {
            return text;
        }
    }

    /**
 * Pasta que guarda as capturas de chat vinculado. Ela nunca entra no indice geral
 * das capturas, porque suas capturas ja aparecem referenciadas dentro da captura de
 * origem, e uma entrada solta nao faria sentido para o analista.
 */
    private boolean isLinkedCaptureFolder(Path folder) {
        return folder != null && LINKED_FOLDER.equals(folder.getFileName().toString());
    }

    private boolean isCaptureFolder(Path captureDir) {
        // a pasta vinculado e um container de capturas, nunca uma captura
        if (!CaptureIntegrity.isSafeDirectory(captureDir) || isLinkedCaptureFolder(captureDir)) {
            return false;
        }
        return CaptureIntegrity.isSafeRegularFile(captureDir.resolve("manifest.json")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(captureDir.resolve("whatsapp.html")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(captureDir.resolve("relatorio.htm")) //$NON-NLS-1$
                || CaptureIntegrity.isSafeRegularFile(captureDir.resolve("texto.txt")); //$NON-NLS-1$
    }

    static boolean isReportAuxiliaryFolder(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString();
        return CustodyCaseRecord.LAUDO_DIR_NAME.equalsIgnoreCase(name)
                || "integridade".equalsIgnoreCase(name) //$NON-NLS-1$
                || "verificador-integridade".equalsIgnoreCase(name); //$NON-NLS-1$
    }

    static boolean isRecaptureAuxiliaryFolder(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.equals(RECAPTURE_TRASH_FOLDER) || name.startsWith("_backup_integridade_") //$NON-NLS-1$
                || name.startsWith(".__capture_staging_") //$NON-NLS-1$
                || name.contains(".__old_") || name.contains(".__reextract_tmp_") //$NON-NLS-1$ //$NON-NLS-2$
                || name.contains(".__reextract_failed_") //$NON-NLS-1$
                || name.contains(".__migration_html_backup"); //$NON-NLS-1$
    }

    private void cleanupRecaptureAuxiliaryFolders(Path outputRoot) {
        if (!CaptureIntegrity.isSafeDirectory(outputRoot)) {
            return;
        }
        try (Stream<Path> children = Files.list(outputRoot).limit(MAX_RECAPTURE_CANDIDATES * 2L)) {
            children.filter(CaptureIntegrity::isSafeDirectory).filter(HtmlLinkViewer::isRecaptureAuxiliaryFolder).forEach(path -> {
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
        if (oldFolder == null || outputRoot == null || !Files.exists(oldFolder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path trashRoot = outputRoot.resolve(RECAPTURE_TRASH_FOLDER);
        try {
            if (CaptureIntegrity.isLinkOrReparse(outputRoot) || CaptureIntegrity.isLinkOrReparse(oldFolder)) {
                throw new IOException("Symbolic links or junctions are not allowed during recapture cleanup"); //$NON-NLS-1$
            }
            CaptureIntegrity.createDirectoriesSafe(outputRoot, trashRoot);
            try {
                Files.setAttribute(trashRoot, "dos:hidden", Boolean.TRUE); //$NON-NLS-1$
            } catch (Exception e) {
                LOGGER.debug("Unable to hide re-extract trash folder {}", trashRoot, e); //$NON-NLS-1$
            }
            String suffix = batchStamp == null || batchStamp.isBlank()
                    ? DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()) : batchStamp; //$NON-NLS-1$
            Path trashTarget = uniqueSibling(trashRoot.resolve(oldFolder.getFileName().toString() + "." + suffix)); //$NON-NLS-1$
            if (CaptureIntegrity.isLinkOrReparse(trashTarget)) {
                throw new IOException("Symbolic links or junctions are not allowed as trash target"); //$NON-NLS-1$
            }
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
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3 && Files.exists(path, LinkOption.NOFOLLOW_LINKS); attempt++) {
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
        Path root = path.toAbsolutePath().normalize();
        if (CaptureIntegrity.isLinkOrReparse(root)) {
            Files.deleteIfExists(root);
            return;
        }
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                if (!dir.equals(root) && (attrs.isSymbolicLink() || attrs.isOther() || CaptureIntegrity.isLinkOrReparse(dir))) {
                    Files.deleteIfExists(dir);
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                try {
                    file.toFile().setWritable(true);
                } catch (Exception e) {
                    LOGGER.debug("Unable to mark file writable before delete {}", file, e); //$NON-NLS-1$
                }
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                try {
                    dir.toFile().setWritable(true);
                } catch (Exception e) {
                    LOGGER.debug("Unable to mark directory writable before delete {}", dir, e); //$NON-NLS-1$
                }
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private FolderHash calculateFolderHash(Path folder) throws IOException {
        List<Path> files = CaptureIntegrity.listRegularFilesSafe(folder, CaptureIntegrity.MAX_INVENTORY_FILES);
        files.sort(Comparator.comparing(path -> folder.relativize(path).toString().replace('\\', '/').toLowerCase()));
        FolderHash result = new FolderHash();
        MessageDigest canonical;
        try {
            canonical = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
        } catch (Exception e) {
            throw new IOException(e);
        }
        for (Path file : files) {
            String relative = folder.relativize(file).toString().replace('\\', '/');
            long size = CaptureIntegrity.safeRegularFileSize(file);
            String sha256 = digest(file, "SHA-256"); //$NON-NLS-1$
            canonical.update((relative + '|' + size + '|' + sha256 + '\n').getBytes(StandardCharsets.UTF_8));
            result.fileCount++;
            result.sizeBytes += size;
        }
        result.hash = toHex(canonical.digest());
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

    static int countRegularFiles(Path folder) throws IOException {
        if (folder == null || !Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        if (!CaptureIntegrity.isSafeDirectory(folder)) {
            throw new IOException("Symbolic links or junctions are not allowed in capture directories: " + folder); //$NON-NLS-1$
        }
        return CaptureIntegrity.listRegularFilesSafe(folder, CaptureIntegrity.MAX_INVENTORY_FILES).size();
    }

    private String readDirectoryCreationDate(Path folder) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(folder, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
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
        if (!CaptureIntegrity.isSafeRegularFile(textPath)) {
            return header;
        }
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(Files.newInputStream(textPath, LinkOption.NOFOLLOW_LINKS), StandardCharsets.UTF_8))) {
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

    private static String inferChatId(String value) {
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return ""; //$NON-NLS-1$
    }

    private static JSONObject parseJsonObjectOrNull(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        try {
            return new JSONObject(jsonText);
        } catch (org.json.JSONException invalid) {
            return null;
        }
    }

    private static String jsonString(JSONObject object, String key) {
        if (object == null || key == null || object.isNull(key)) {
            return ""; //$NON-NLS-1$
        }
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL || value instanceof JSONObject || value instanceof JSONArray) {
            return ""; //$NON-NLS-1$
        }
        return String.valueOf(value);
    }

    private static long jsonLong(JSONObject object, String key, long fallback) {
        if (object == null || key == null || object.isNull(key)) {
            return fallback;
        }
        Object value = object.opt(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                String trimmed = ((String) value).trim();
                return trimmed.isEmpty() ? fallback : Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double jsonDouble(JSONObject object, String key, double fallback) {
        if (object == null || key == null || object.isNull(key)) {
            return fallback;
        }
        Object value = object.opt(key);
        if (value instanceof Number) {
            double parsed = ((Number) value).doubleValue();
            return Double.isFinite(parsed) ? parsed : fallback;
        }
        if (value instanceof String) {
            try {
                String trimmed = ((String) value).trim();
                if (trimmed.isEmpty()) {
                    return fallback;
                }
                double parsed = Double.parseDouble(trimmed);
                return Double.isFinite(parsed) ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static CaptureAnchor readAnchor(JSONObject parent, String field) {
        return readAnchor(parent == null ? null : parent.optJSONObject(field));
    }

    static CaptureAnchor readAnchor(JSONObject object) {
        if (object == null) {
            return new CaptureAnchor();
        }
        CaptureAnchor anchor = new CaptureAnchor();
        anchor.type = jsonString(object, "type"); //$NON-NLS-1$
        anchor.id = jsonString(object, "id"); //$NON-NLS-1$
        anchor.text = jsonString(object, "text"); //$NON-NLS-1$
        anchor.date = jsonString(object, "date"); //$NON-NLS-1$
        anchor.hash = jsonString(object, "hash"); //$NON-NLS-1$
        anchor.documentTop = jsonDouble(object, "documentTop", -1); //$NON-NLS-1$
        anchor.documentBottom = jsonDouble(object, "documentBottom", -1); //$NON-NLS-1$
        return anchor.normalized();
    }

    private static CaptureAnchor enrichAnchorFromCoordinates(CaptureAnchor anchor, CaptureAnchor coordinateAnchor) {
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

    static CaptureAnchor firstBlockAnchor(String coordinatesJson, String preferredId) {
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

    static CaptureAnchor lastBlockAnchor(String coordinatesJson, String preferredId) {
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

    static List<CaptureAnchor> readCoordinateAnchors(String coordinatesJson) {
        List<CaptureAnchor> anchors = new ArrayList<>();
        String safe = safe(coordinatesJson);
        // As ancoras reais ficam dentro de "frames[].blocks". startAnchor/endAnchor do
        // topo do arquivo sao metadados e nao devem ser contados (evita +2 espurios).
        // Usa parser JSON real para nao quebrar com texto contendo ']' ou braces.
        try {
            JSONObject root = new JSONObject(safe);
            JSONArray frames = root.optJSONArray("frames"); //$NON-NLS-1$
            if (frames != null) {
                for (int i = 0; i < frames.length(); i++) {
                    JSONObject frame = frames.optJSONObject(i);
                    if (frame != null) {
                        addCoordinateAnchorsFromBlocks(frame.optJSONArray("blocks"), anchors); //$NON-NLS-1$
                    }
                }
            }
            // Fallback: blocks no nivel raiz (formatos alternativos).
            if (anchors.isEmpty()) {
                addCoordinateAnchorsFromBlocks(root.optJSONArray("blocks"), anchors); //$NON-NLS-1$
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to parse coordinate blocks JSON: {}", e.getMessage()); //$NON-NLS-1$
        }
        return anchors;
    }

    private static void addCoordinateAnchorsFromBlocks(JSONArray blocks, List<CaptureAnchor> anchors) {
        if (blocks == null) {
            return;
        }
        for (int j = 0; j < blocks.length(); j++) {
            JSONObject b = blocks.optJSONObject(j);
            if (b == null) {
                continue;
            }
            CaptureAnchor anchor = new CaptureAnchor();
            anchor.id = jsonString(b, "id"); //$NON-NLS-1$
            anchor.type = jsonString(b, "type"); //$NON-NLS-1$
            anchor.text = jsonString(b, "text"); //$NON-NLS-1$
            anchor.hash = jsonString(b, "hash"); //$NON-NLS-1$
            anchor.date = extractMessageDate(anchor.text);
            anchor.documentTop = jsonDouble(b, "documentTop", -1); //$NON-NLS-1$
            anchor.documentBottom = jsonDouble(b, "documentBottom", -1); //$NON-NLS-1$
            anchors.add(anchor.normalized());
        }
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

    private static String extractMessageDate(String text) {
        Matcher matcher = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?)").matcher(safe(text)); //$NON-NLS-1$
        return matcher.find() ? matcher.group(1).trim() : ""; //$NON-NLS-1$
    }

    private void writeCaptureRootIndexJson(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        writeAtomicallyWithPrinter(outputRoot, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
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
                out.println("      \"chronologicalStart\": " + json(entry.chronologicalStart) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"chronologicalEnd\": " + json(entry.chronologicalEnd) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"chronologicalRange\": " + json(entry.chronologicalRange) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"chronologicalGroup\": " + json(entry.chronologicalGroup) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"chronologicalSortKey\": " + json(entry.chronologicalSortKey) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"folderHash\": " + json(entry.folderHash) + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"fileCount\": " + entry.fileCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"attachmentCount\": " + entry.attachmentCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"frameCount\": " + entry.frameCount + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"sizeBytes\": " + entry.sizeBytes + ","); //$NON-NLS-1$ //$NON-NLS-2$
                out.println("      \"status\": " + json(entry.status) + ","); //$NON-NLS-1$
            out.println("      \"annotations\": {\"enabled\": " + entry.annotationsEnabled //$NON-NLS-1$ //$NON-NLS-2$
                    + ", \"legacy\": " + entry.legacyAnnotations //$NON-NLS-1$
                    + ", \"notes\": " + entry.notesCount //$NON-NLS-1$
                    + ", \"annotatedMessages\": " + entry.linksCount //$NON-NLS-1$
                    + ", \"contacts\": " + entry.contactsCount + "},"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-2$
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
        });
    }

    private void writeCaptureRootIndexHtml(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        String indexScript = captures.isEmpty() ? "" : buildCaptureRootIndexScript(); //$NON-NLS-1$
        String scriptSrc = captures.isEmpty() ? "'none'" : "'sha256-" + sha256Base64(indexScript) + "'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String indexCsp = "default-src 'none'; style-src 'unsafe-inline'; script-src " + scriptSrc //$NON-NLS-1$
                + "; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"; //$NON-NLS-1$
        writeAtomicallyWithPrinter(outputRoot, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + indexCsp + "\"><title>&Iacute;ndice de Capturas</title>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<style>body{margin:0;background:#f2efe9;color:#1f2a30;font-family:Arial,sans-serif}.hero{background:#102b35;color:#fff;padding:26px 34px}.hero h1{margin:0 0 8px;font-size:28px}.hero p{margin:4px 0;color:#d7e6ea}.hero .hash{display:inline-block;background:#06171d;color:#f7fbfd;border:1px solid #6fa9b8;box-shadow:inset 0 0 0 1px rgba(255,255,255,.08)}.wrap{padding:24px 34px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px}.card{background:white;border:1px solid #d8d1c8;border-radius:10px;padding:16px;box-shadow:0 2px 10px rgba(0,0,0,.06)}.card h2{font-size:18px;margin:0 0 8px;overflow-wrap:normal;word-break:normal;hyphens:none}.meta{font-size:13px;color:#52646c;line-height:1.45}.hash{font-family:Consolas,monospace;font-size:11px;word-break:break-all;background:#f5f5f5;padding:8px;border-radius:6px}.btn{display:inline-block;margin:8px 6px 0 0;padding:8px 10px;background:#0b6b5d;color:white;text-decoration:none;border-radius:5px;font-size:13px}.btn.alt{background:#46545a}.hero{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;flex-wrap:wrap}.hero-main{flex:1 1 320px}.hero-side{flex:0 1 320px;display:flex;flex-direction:column;gap:10px}.hero-side input[type=search]{width:100%;box-sizing:border-box;padding:9px 11px;border-radius:6px;border:1px solid #6fa9b8;background:#06171d;color:#f7fbfd;font-size:13px}.hero-side input[type=search]::placeholder{color:#8fb0bb}.tgl{display:flex;align-items:center;gap:8px;font-size:13px;color:#d7e6ea;background:#06171d;border:1px solid #6fa9b8;border-radius:6px;padding:9px 11px;cursor:pointer}.tgl input{cursor:pointer}.noresult{display:none;margin:10px 0;font-size:13px;color:#52646c}.card.hidden{display:none}.empty{background:white;border:1px solid #ddd;padding:18px;border-radius:8px}</style>"); //$NON-NLS-1$
out.println("</head><body><section class=\"hero\"><div class=\"hero-main\"><h1>&Iacute;ndice de Capturas de Chat</h1>"); //$NON-NLS-1$
            out.println("<p><b>Pasta raiz:</b> " + html(outputRoot.toAbsolutePath().toString()) + "</p>"); //$NON-NLS-1$
            out.println("<p><b>Total de capturas:</b> <span id=\"count\">" + captures.size() //$NON-NLS-1$ //$NON-NLS-2$
                    + "</span> &nbsp; <b>Hash geral:</b> <span class=\"hash\">" + html(rootHash) + "</span></p>"); //$NON-NLS-1$
            Path laudoDir = outputRoot.resolve(CustodyCaseRecord.LAUDO_DIR_NAME);
            if (CaptureIntegrity.isSafeDirectory(laudoDir)) {
                StringBuilder laudoLinks = new StringBuilder();
                if (CaptureIntegrity.isSafeRegularFile(laudoDir.resolve(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE))) {
                    laudoLinks.append("<a class=\"btn\" href=\"laudo/").append(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE).append("\">Laudo T&eacute;cnico (PDF)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (CaptureIntegrity.isSafeRegularFile(laudoDir.resolve(CustodyCaseRecord.REPORT_HTML_FILE))) {
                    laudoLinks.append("<a class=\"btn\" href=\"laudo/").append(CustodyCaseRecord.REPORT_HTML_FILE).append("\">Laudo T&eacute;cnico (HTML)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (CaptureIntegrity.isSafeRegularFile(laudoDir.resolve(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE))) {
                    laudoLinks.append("<a class=\"btn alt\" href=\"laudo/").append(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE).append("\">report.pdf Original</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (CaptureIntegrity.isSafeRegularFile(laudoDir.resolve(CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE))) {
                    laudoLinks.append("<a class=\"btn alt\" href=\"laudo/").append(CustodyCaseRecord.REPORT_UNIFIED_PDF_FILE).append("\">PDF Unificado</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (laudoLinks.length() > 0) {
                    out.println("<p>" + laudoLinks + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            out.println("</div>"); //$NON-NLS-1$
            out.println("<div class=\"hero-side\">" //$NON-NLS-1$
                    + "<input id=\"q\" type=\"search\" placeholder=\"Pesquisar chat, ID, pasta ou data\" aria-label=\"Pesquisar capturas\">" //$NON-NLS-1$
                    + "<label class=\"tgl\"><input type=\"checkbox\" id=\"chrono\" checked> Ordenar por cronologia</label>" //$NON-NLS-1$
                    + "</div></section><main class=\"wrap\">"); //$NON-NLS-1$
            if (captures.isEmpty()) {
                out.println("<div class=\"empty\">Nenhuma captura encontrada nesta pasta.</div>"); //$NON-NLS-1$
            } else {
                out.println("<div class=\"grid\" id=\"grid\">"); //$NON-NLS-1$
                for (CaptureRootEntry entry : captures) {
                    out.println("<article class=\"card\" data-chrono=\"" + html(entry.chronologicalSortKey) + "\" data-folder=\"" //$NON-NLS-1$ //$NON-NLS-2$
                            + html(entry.folder) + "\"><h2>" + htmlBreakableName(entry.folder) + "</h2>"); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<div class=\"meta\"><b>Chat:</b> " + html(entry.title) + "<br><b>ID:</b> " + html(entry.chatId) //$NON-NLS-1$ //$NON-NLS-2$
                            + "<br><b>Data:</b> " + html(entry.createdAt) + "<br><b>Cronologia:</b> " + html(nonBlank(entry.chronologicalRange, "-")) + "<br><b>Status:</b> " + html(entry.status) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            + "<br><b>Frames:</b> " + entry.frameCount + " &nbsp; <b>Anexos:</b> " + entry.attachmentCount //$NON-NLS-1$ //$NON-NLS-2$
                            + "<br><b>Arquivos:</b> " + entry.fileCount + " &nbsp; <b>Tamanho:</b> " + formatBytes(entry.sizeBytes) + "</div>"); //$NON-NLS-1$ //$NON-NLS-2$
                    out.println("<p class=\"hash\">" + html(entry.folderHash) + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
                    String annotationLine = annotationSummaryHtml(entry);
                    if (!annotationLine.isEmpty()) {
                        out.println(annotationLine); //$NON-NLS-1$
                    }
                    out.println("<a class=\"btn\" href=\"" + html(entry.linksWhatsapp) + "\">Abrir WhatsApp</a><a class=\"btn\" href=\"" + html(entry.linksReport) + "\">Abrir Relat&oacute;rio</a><a class=\"btn alt\" href=\"" + html(entry.linksText) + "\">Texto</a><a class=\"btn alt\" href=\"" + html(entry.linksManifest) + "\">Manifest</a></article>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
}
                out.println("</div>"); //$NON-NLS-1$
                out.println("<div class=\"noresult\" id=\"noresult\">Nenhuma captura corresponde a busca.</div>"); //$NON-NLS-1$
                out.println(writeCaptureRootIndexScript());
            }
            out.println("</main></body></html>"); //$NON-NLS-1$
        });
    }

    /**
     * Busca por texto e alternancia de ordenacao no indice.
     *
     * Os cards ja saem em ordem cronologica; o toggle troca para mais recente
     * primeiro usando a chave cronologica ISO, que ordena lexicograficamente.
     */
    private String writeCaptureRootIndexScript() {
        return "<script>" + buildCaptureRootIndexScript() + "</script>"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String buildCaptureRootIndexScript() {
        return "(function(){var cards=Array.prototype.slice.call(document.querySelectorAll('.card'));"
                + "var grid=document.getElementById('grid');var q=document.getElementById('q');"
                + "var chrono=document.getElementById('chrono');var count=document.getElementById('count');"
                + "var none=document.getElementById('noresult');"
                + "function norm(s){return String(s||'').toLowerCase().replace(/_/g,' ').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,' ').trim();}"
                + "function byChrono(a,b){var ka=a.getAttribute('data-chrono')||'';var kb=b.getAttribute('data-chrono')||'';"
                + "if(ka===kb)return a.getAttribute('data-folder').localeCompare(b.getAttribute('data-folder'));"
                + "if(ka&&kb)return ka<kb?-1:1;if(ka)return -1;if(kb)return 1;"
                + "return a.getAttribute('data-folder').localeCompare(b.getAttribute('data-folder'));}"
                + "function applyOrder(){var sorted=cards.slice().sort(byChrono);"
                + "if(chrono&&chrono.checked){sorted.forEach(function(c,i){c.style.order=String(i);});}"
                + "else{cards.slice().sort(function(a,b){return byChrono(b,a);}).forEach(function(c,i){c.style.order=String(i);});}}"
                + "function applyFilter(){var t=norm(q?q.value:'');var shown=0;"
                + "cards.forEach(function(c){var hit=!t||norm(c.textContent).indexOf(t)>=0||norm(c.getAttribute('data-folder')).indexOf(t)>=0;"
                + "c.classList.toggle('hidden',!hit);if(hit)shown++;});"
                + "if(count)count.textContent=String(shown);if(none)none.style.display=shown?'none':'block';}"
                + "if(q)q.addEventListener('input',applyFilter);"
                + "if(chrono)chrono.addEventListener('change',function(){applyOrder();applyFilter();});"
                + "applyOrder();applyFilter();})();"; //$NON-NLS-1$
    }

    /**
 * Linha de resumo das anotacoes, exibida somente quando houver notas ou links.
 * Capturas antigas ficam visualmente identicas a antes.
 */
    private String annotationSummaryHtml(CaptureRootEntry entry) {
        if (!entry.annotationsEnabled || (entry.notesCount == 0 && entry.linksCount == 0)) {
            return ""; //$NON-NLS-1$
        }
        return "<div class=\"meta\"><b>Anota&ccedil;&otilde;es do analista (n&atilde;o s&atilde;o evid&ecirc;ncia):</b> " //$NON-NLS-1$
                + entry.notesCount + " nota(s), " + entry.linksCount + " mensagem(ns) com link, " //$NON-NLS-1$ //$NON-NLS-2$
                + entry.contactsCount + " contato(s)</div>"; //$NON-NLS-1$
    }

    private void writeCaptureRootReport(Path path, Path outputRoot, String rootHash, List<CaptureRootEntry> captures) throws IOException {
        writeAtomicallyWithPrinter(outputRoot, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'\"><title>Relat&oacute;rio Geral de Capturas</title>"); //$NON-NLS-1$
            out.println("<style>body{font-family:Arial,sans-serif;color:#222;margin:24px}.columnHead{background:#c0c0c0;border:1px solid #777;text-align:left;padding:4px}.clrBkgrnd{background:#e8e8e8}.mono{font-family:Consolas,monospace;font-size:12px;word-break:break-all}table{border-collapse:collapse;width:100%;margin-top:12px}td,th{border:1px solid #ccc;padding:6px;vertical-align:top;font-size:13px}th{background:#ddd}.summary{border:1px solid #ccc;padding:12px;background:#fafafa}</style></head><body>"); //$NON-NLS-1$
            out.println("<table><tbody><tr><th class=\"columnHead\" colspan=\"1\" style=\"font-size:16px\">Relat&oacute;rio Geral de Capturas</th></tr><tr><td class=\"clrBkgrnd\"><span style=\"font-weight:bold\">Coment&aacute;rios: </span>&Iacute;ndice pai das capturas existentes na pasta raiz.</td></tr></tbody></table>"); //$NON-NLS-1$
            StringBuilder rootLinks = new StringBuilder("<a href=\"index.html\">Abrir index.html</a> &nbsp; <a href=\"capturas-index.json\">Abrir capturas-index.json</a>"); //$NON-NLS-1$
            Path laudoDirReport = outputRoot.resolve(CustodyCaseRecord.LAUDO_DIR_NAME);
            if (CaptureIntegrity.isSafeDirectory(laudoDirReport)) {
                if (CaptureIntegrity.isSafeRegularFile(laudoDirReport.resolve(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE))) {
                    rootLinks.append(" &nbsp; <a href=\"laudo/").append(CustodyCaseRecord.REPORT_PLUGIN_PDF_FILE).append("\">Abrir Laudo (PDF)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (CaptureIntegrity.isSafeRegularFile(laudoDirReport.resolve(CustodyCaseRecord.REPORT_HTML_FILE))) {
                    rootLinks.append(" &nbsp; <a href=\"laudo/").append(CustodyCaseRecord.REPORT_HTML_FILE).append("\">Abrir Laudo (HTML)</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (CaptureIntegrity.isSafeRegularFile(laudoDirReport.resolve(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE))) {
                    rootLinks.append(" &nbsp; <a href=\"laudo/").append(CustodyCaseRecord.REPORT_ORIGIN_PDF_FILE).append("\">Abrir report.pdf Original</a>"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            out.println("<div class=\"summary\"><p><b>Pasta raiz:</b> " + html(outputRoot.toAbsolutePath().toString()) + "</p><p><b>Quantidade de capturas:</b> " + captures.size() + "</p><p><b>Hash geral da raiz:</b> <span class=\"mono\">" + html(rootHash) + "</span></p><p>" + rootLinks + "</p></div>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            out.println("<table><thead><tr><th>Pasta</th><th>Chat</th><th>Data</th><th>Cronol&oacute;gica tempo</th><th>Status</th><th>Arquivos</th><th>Anexos</th><th>Hash da pasta</th><th>Links</th></tr></thead><tbody>"); //$NON-NLS-1$
            if (captures.isEmpty()) {
                out.println("<tr><td colspan=\"9\">Nenhuma captura encontrada.</td></tr>"); //$NON-NLS-1$
            } else {
                for (CaptureRootEntry entry : captures) {
                    out.println("<tr><td>" + html(entry.folder) + "</td><td>" + html(entry.title) + "<br><span class=\"mono\">" + html(entry.chatId) + "</span></td><td>" + html(entry.createdAt) + "</td><td>" + html(nonBlank(entry.chronologicalRange, "-")) + "</td><td>" + html(entry.status) + "</td><td>" + entry.fileCount + "</td><td>" + entry.attachmentCount + "</td><td class=\"mono\">" + html(entry.folderHash) + "</td><td><a href=\"" + html(entry.linksReport) + "\">relatorio.htm</a><br><a href=\"" + html(entry.linksWhatsapp) + "\">whatsapp.html</a><br><a href=\"" + html(entry.linksText) + "\">texto.txt</a></td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
                }
            }
            out.println("</tbody></table></body></html>"); //$NON-NLS-1$
        });
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
                Path groupDir = CaptureIntegrity.resolveContained(attachmentsDir, groupFolder(item));
                CaptureIntegrity.createDirectoriesSafe(attachmentsDir, groupDir);
                String name = safeName(item.getName());
                if (name.isBlank()) {
                    name = hash;
                }
                Path candidate = CaptureIntegrity.resolveContained(groupDir, name);
                Path target = uniquePath(candidate);
                copyFileCreateNew(source.toPath(), target);
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
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
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
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
    }

    private ExportedFile hashFile(Path path, Map<String, ExportedFile> exported, String relativePath, String chatHash, IItem sourceItem) throws IOException {
        ExportedFile file = new ExportedFile();
        file.relativePath = relativePath.replace('\\', '/');
        file.absolutePath = path.toAbsolutePath().toString();
        file.chatHash = chatHash;
        file.size = CaptureIntegrity.safeRegularFileSize(path);
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
            if (!CaptureIntegrity.isSafeRegularFile(path)) {
                throw new IOException("Symbolic links or junctions are not allowed: " + path); //$NON-NLS-1$
            }
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
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
        if (!CaptureIntegrity.isSafeRegularFile(path)) {
            throw new IOException("Symbolic links or junctions are not allowed: " + path); //$NON-NLS-1$
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                    DigestInputStream din = new DigestInputStream(in, digest)) {
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

    private void writeHashes(Path captureDir, Path path, Map<String, ExportedFile> exported) throws IOException {
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            for (ExportedFile file : exported.values()) {
                out.println("Algorithm : SHA256"); //$NON-NLS-1$
                out.println("Hash      : " + safe(file.sha256)); //$NON-NLS-1$
                out.println("Path      : " + safe(file.absolutePath)); //$NON-NLS-1$
                out.println();
            }
        });
    }

    private void writeFileListCsv(Path captureDir, Path path, Map<String, ExportedFile> exported) throws IOException {
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            out.println(csv("Nome") + ";" + csv("Caminho Relativo") + ";" + csv("Caminho Absoluto") + ";" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    + csv("Tipo") + ";" + csv("Tamanho") + ";" + csv("MD5") + ";" + csv("SHA256") + ";" + csv("Hash do Chat")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            for (ExportedFile file : exported.values()) {
                out.println(csv(displayName(file)) + ";" + csv(file.relativePath) + ";" + csv(file.absolutePath) + ";" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + csv(fileType(file)) + ";" + file.size + ";" + csv(file.md5) + ";" + csv(file.sha256) + ";" + csv(file.chatHash)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
        });
    }

    void createReportThumbnails(Path captureDir, Map<String, ExportedFile> exported) {
        for (ExportedFile file : exported.values()) {
            if (!isImageForReport(file)) {
                continue;
            }
            try {
                Path source = CaptureIntegrity.resolveContained(captureDir, file.relativePath);
                if (CaptureIntegrity.isLinkOrReparse(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                BufferedImage image = SensitiveImageMatcher.readBoundedImageFile(source);
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
                String thumbBase = sha.isBlank() ? safeName(fileName(file.relativePath)) : safeName(sha);
                if (thumbBase.isBlank()) {
                    thumbBase = "thumb"; //$NON-NLS-1$
                }
                Path thumbPath = CaptureIntegrity.resolveContained(captureDir,
                        "thumbs/" + first + "/" + second + "/" + thumbBase + ".jpg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                ByteArrayOutputStream thumbBytes = new ByteArrayOutputStream();
                ImageIO.write(thumb, "jpg", thumbBytes); //$NON-NLS-1$
                CaptureIntegrity.writeBytesAtomically(captureDir, thumbPath, thumbBytes.toByteArray(),
                        MAX_HTML_MESSAGE_ATTACHMENT_BYTES);
                file.thumbRelativePath = captureDir.relativize(thumbPath).toString().replace('\\', '/');
            } catch (Exception e) {
                LOGGER.debug("Unable to create report thumbnail for {}", file.relativePath, e); //$NON-NLS-1$
            }
        }
    }

    private void writeInteractiveCaptureReport(Path captureDir, Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported) throws IOException {
        String reportScript = "function showSection(id){document.querySelectorAll('.section').forEach(function(s){s.classList.remove('active')});var el=document.getElementById(id);if(el)el.classList.add('active');document.querySelectorAll('#menu a[data-section]').forEach(function(a){a.classList.toggle('active-link',a.getAttribute('data-section')===id)});return false;}window.onload=function(){var menu=document.getElementById('menu');if(menu){menu.addEventListener('click',function(e){var link=e.target&&e.target.closest?e.target.closest('a[data-section]'):null;if(link){e.preventDefault();showSection(link.getAttribute('data-section'));}});}showSection('info');};"; //$NON-NLS-1$
        String reportCsp = "default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self'; media-src 'self'; script-src 'sha256-" + sha256Base64(reportScript) + "'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"; //$NON-NLS-1$
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            out.println("<!DOCTYPE html>"); //$NON-NLS-1$
            out.println("<html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + reportCsp + "\"><title>Relat&oacute;rio de Captura de Chat</title>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"res/common.css\"><link rel=\"stylesheet\" type=\"text/css\" href=\"res/bookmarks.css\">"); //$NON-NLS-1$
            out.println("<style>body{margin:0;font-family:Arial,sans-serif;background:#fff;color:#222}#menu{position:fixed;left:0;top:0;bottom:0;width:280px;background:#263238;color:white;padding:16px;overflow:auto;box-sizing:border-box}#menu h1{font-size:18px;margin:0 0 14px}#menu h2{font-size:15px;margin:18px 0 8px;color:#d9f3ff}#menu a{color:white;display:block;padding:4px 0;text-decoration:none;cursor:pointer}.sub{padding-left:18px!important;color:#dce7ea!important}.active-link{font-weight:bold;color:#fff59d!important}#content{margin-left:310px;padding:24px}.section{display:none}.section.active{display:block}.report-page{background:white}.card{background:white;border:1px solid #ddd;padding:16px;margin-bottom:16px}.columnHead{background:#c0c0c0;border:1px solid #777;text-align:left;color:#000;padding:4px}.clrBkgrnd{background:#e8e8e8}.bkmkLblFiles{background:#c0c0c0;border:1px solid #777;font-weight:bold;margin-top:12px;padding:3px}.bkmkSeparator{height:8px;margin-top:8px}.bkmkValue{font-size:13px}.row{display:flex;width:100%;clear:both}.bkmkColLeft{display:inline-block;width:220px;box-sizing:border-box;padding:3px 5px;border-bottom:1px solid #fff;font-weight:normal}.bkmkColRight{display:inline-block;flex:1;box-sizing:border-box;padding:3px 5px;border-bottom:1px solid #eee;word-break:break-word}.labelBorderless{border-left:0;border-right:0}.thumb{width:auto;height:auto;max-width:112px;max-height:112px}.mono{font-family:Consolas,monospace;font-size:12px;word-break:break-all}.links a{margin-right:14px}.empty{color:#777;font-style:italic}.help-page{max-width:980px;line-height:1.45}.help-page h2{font-size:22px;margin:0 0 16px}.help-page h3{font-size:18px;margin:28px 0 10px}.help-page p{margin:10px 0 10px 28px}.help-page .item{margin-left:28px}.help-page .subitem{margin-left:56px}.help-page .cmd{font-family:Consolas,monospace;background:#f4f4f4;border:1px solid #ddd;padding:6px 8px;display:inline-block}</style>"); //$NON-NLS-1$
            out.println("<script>" + reportScript + "</script>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("</head><body><div id=\"menu\"><h1>Relat&oacute;rio de Captura</h1>"); //$NON-NLS-1$
            out.println("<h2>Informa&ccedil;&otilde;es</h2><a href=\"#\" data-section=\"info\">Informa&ccedil;&otilde;es</a><a href=\"#\" data-section=\"busca\">Busca por palavras-chave</a>"); //$NON-NLS-1$
            out.println("<h2>Categorias</h2><a class=\"sub\" href=\"#\" data-section=\"captura\">Captura</a><a class=\"sub\" href=\"#\" data-section=\"audios\">&Aacute;udios</a><a class=\"sub\" href=\"#\" data-section=\"pdfs\">Documentos PDF</a><a class=\"sub\" href=\"#\" data-section=\"imagens\">Outras Imagens</a><a class=\"sub\" href=\"#\" data-section=\"scans\">Poss&iacute;veis Digitaliza&ccedil;&otilde;es</a><a class=\"sub\" href=\"#\" data-section=\"videos\">V&iacute;deos</a><a class=\"sub\" href=\"#\" data-section=\"whatsapp\">WhatsApp</a>"); //$NON-NLS-1$
            out.println("<h2>Ajuda</h2><a class=\"sub\" href=\"#\" data-section=\"ajuda\">Relat&oacute;rio e Anexo</a></div><div id=\"content\">"); //$NON-NLS-1$
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
        });
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

    private void writeCaptureReport(Path captureDir, Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported) throws IOException {
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
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
        });
    }

    private void writeManifest(Path captureDir, Path path, String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported, CaptureSourceMetadata sourceMetadata) throws IOException {
        CaptureAnchor startAnchor = anchorForMessage(frames, captureStartMessageId, false);
        CaptureAnchor endAnchor = anchorForMessage(frames, captureEndMessageId, true);
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_JSON_BYTES, out -> {
out.println("{"); //$NON-NLS-1$
            out.println("  \"version\": " + MANIFEST_VERSION + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"pluginVersion\": " + json(CHAT_CAPTURE_PLUGIN_VERSION) + ","); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("  \"status\": " + json(status) + ","); //$NON-NLS-1$
            out.println("  \"startedAt\": " + json(Instant.now().toString()) + ","); //$NON-NLS-1$
            out.println("  \"startAnchor\": " + buildAnchorJson(startAnchor) + ","); //$NON-NLS-1$
            out.println("  \"endAnchor\": " + buildAnchorJson(endAnchor) + ","); //$NON-NLS-1$
            out.println("  \"sourceMetadata\": " + buildInlineMetadataJson(sourceMetadata) + ","); //$NON-NLS-1$
            out.println("  \"captureFingerprint\": " + buildCaptureFingerprint(sourceMetadata, startAnchor, endAnchor) + ","); //$NON-NLS-1$
            out.println("  \"artifactGenerators\": " + artifactGeneratorsJson() + ","); //$NON-NLS-1$
            writeAnnotationsFields(out);
            out.println("  \"frames\": ["); //$NON-NLS-1$
            for (int i = 0; i < frames.size(); i++) {
                CaptureFrame frame = frames.get(i);
                out.println("    {\"sequence\": " + frame.sequence + ", \"image\": " + json(frame.image) + ", \"framePngSha256\": " + json(frame.pngSha256) + ", \"semanticSignature\": " + frame.semanticSignature + ", \"messageSignatures\": " + frame.messageSignatures + ", \"firstVisibleMessageId\": " + json(frame.firstMessageId) + ", \"firstVisibleMessageIdRaw\": " + json(frame.firstVisibleMessageIdRaw) + ", \"residualTopBlockId\": " + json(frame.residualTopBlockId) + ", \"oversizedContinuation\": " + frame.oversizedContinuation + ", \"exportDocumentTop\": " + frame.exportDocumentTop + ", \"exportDocumentBottom\": " + frame.exportDocumentBottom + ", \"cropTopCss\": " + frame.cropTopCss + ", \"lastVisibleMessageId\": " + json(frame.lastMessageId) + ", \"visibleMediaHashes\": " + jsonArray(frame.hashes) + "}" + (i + 1 == frames.size() ? "" : ",")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$
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
        });
    }

    private String artifactGeneratorsJson() {
        return new JSONObject()
                .put("whatsapp.html", HTML_GENERATOR_VERSION)
                .put("whatsapp-coordinates.json", COORDINATES_SCHEMA_VERSION)
                .put("manifest.json", MANIFEST_VERSION)
                .put("relatorio.htm", 1)
                .put("texto.txt", 1)
                .toString();
    }

    private JSONObject buildCaptureFingerprint(CaptureSourceMetadata metadata, CaptureAnchor start, CaptureAnchor end) throws IOException {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintField(canonical, "capture-fingerprint-v1");
        appendFingerprintField(canonical, metadata == null ? "" : metadata.hash);
        appendAnchorFingerprint(canonical, start);
        appendAnchorFingerprint(canonical, end);
        appendFingerprintField(canonical, Integer.toString(sensitiveSimilarityPercent));
        appendFingerprintField(canonical, Integer.toString(sensitiveBlurPercent));
        appendFingerprintField(canonical, buildAnnotationsJson());
        appendFingerprintField(canonical, Integer.toString(CaptureIntegrity.SIGNATURE_VERSION));
        boolean complete = metadata != null && metadata.hash != null && !metadata.hash.isBlank()
                && start != null && start.isUsable() && end != null && end.isUsable();
        return new JSONObject()
                .put("algorithm", CaptureIntegrity.HASH_ALGORITHM)
                .put("complete", complete)
                .put("value", CaptureIntegrity.sha256Text(canonical.toString()))
                .put("signatureVersion", CaptureIntegrity.SIGNATURE_VERSION);
    }

    private void appendAnchorFingerprint(StringBuilder canonical, CaptureAnchor anchor) {
        appendFingerprintField(canonical, anchor == null ? "" : anchor.id);
        appendFingerprintField(canonical, anchor == null ? "" : anchor.type);
        appendFingerprintField(canonical, anchor == null ? "" : anchor.text);
        appendFingerprintField(canonical, anchor == null ? "" : anchor.date);
        appendFingerprintField(canonical, anchor == null ? "" : anchor.hash);
    }

    private void appendFingerprintField(StringBuilder canonical, String value) {
        String field = value == null ? "" : value;
        canonical.append(field.length()).append(':').append(field).append((char) 10);
    }

    private void writeArtifactInventory(Path captureDir) throws IOException {
        Path manifestPath = captureDir.resolve("manifest.json"); //$NON-NLS-1$
        JSONObject manifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(manifestPath));
        manifest.put("artifactGenerators", new JSONObject(artifactGeneratorsJson()));
        JSONArray artifacts = CaptureIntegrity.artifactInventory(captureDir);
        manifest.put("artifacts", artifacts);
        CaptureIntegrity.writeAtomically(captureDir, manifestPath, manifest.toString(2) + System.lineSeparator());
        JSONObject integrity = CaptureIntegrity.integrityRecord(captureDir, null, "capture_created", CHAT_CAPTURE_PLUGIN_VERSION);
        Path integrityPath = captureDir.resolve("integridade.json"); //$NON-NLS-1$
        CaptureIntegrity.writeAtomically(captureDir, integrityPath, integrity.toString(2) + System.lineSeparator());
    }

    private void appendIntegrityHistory(Path newCapture, Path previousCapture) throws IOException {
        appendIntegrityHistory(newCapture, previousCapture, "recapture_completed"); //$NON-NLS-1$
    }

    private void appendIntegrityHistory(Path newCapture, Path previousCapture, String action) throws IOException {
        Path oldPath = previousCapture.resolve("integridade.json"); //$NON-NLS-1$
        JSONObject previous = null;
        if (CaptureIntegrity.isSafeRegularFile(oldPath)) {
            try {
                previous = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(oldPath));
            } catch (RuntimeException e) {
                LOGGER.warn("Previous integrity history is invalid for {}; keeping a new local history", previousCapture, e); //$NON-NLS-1$
            }
        }
        JSONObject next = CaptureIntegrity.integrityRecord(newCapture, previous, action, CHAT_CAPTURE_PLUGIN_VERSION);
        Path target = newCapture.resolve("integridade.json"); //$NON-NLS-1$
        CaptureIntegrity.writeAtomically(newCapture, target, next.toString(2) + System.lineSeparator());
    }
    private void writeText(Path captureDir, Path path, String content) throws IOException {
        CaptureIntegrity.writeAtomically(captureDir, path, content, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES);
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
        metadata.sha256 = resolveItemSha256(item, metadata.hash, tikaMetadata);
        metadata.title = firstMetadataValue(tikaMetadata, "common:dc:title", "dc:title", "title"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        metadata.metadataText = buildTikaMetadataText(tikaMetadata, metadata);
        return metadata;
    }

    private String resolveItemSha256(IItem item, String itemHash, Metadata tikaMetadata) {
        if (itemHash != null && itemHash.trim().matches("(?i)[0-9a-f]{64}")) { //$NON-NLS-1$
            return itemHash.trim().toLowerCase(Locale.ROOT);
        }
        String fromMeta = firstMetadataValue(tikaMetadata, "hash:sha-256", "sha-256", "hash:sha256", "sha256"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if (fromMeta != null && fromMeta.trim().matches("(?i)[0-9a-f]{64}")) { //$NON-NLS-1$
            return fromMeta.trim().toLowerCase(Locale.ROOT);
        }
        if (item != null && (item.getLength() == null || item.getLength() <= CaptureIntegrity.MAX_HTML_CAPTURE_BYTES)) {
            try (InputStream stream = item.getBufferedInputStream()) {
                if (stream != null) {
                    return CaptureIntegrity.sha256(stream, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES);
                }
            } catch (Exception ignored) {
                LOGGER.debug("Nao foi possivel calcular SHA-256 do item fonte {}", safe(item.getName())); //$NON-NLS-1$
            }
        }
        return ""; //$NON-NLS-1$
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
        appendAnnotationsText(out);
        out.append("METADADOS: \r\n"); //$NON-NLS-1$
        out.append(safe(metadata.metadataText));
        out.append("----------------------------------- \r\n"); //$NON-NLS-1$
        return out.toString();
    }

    /** Secao do analista, escrita apenas quando o recurso foi usado. */
    private void appendAnnotationsText(StringBuilder out) {
        ChatAnnotationSet set = pendingAnnotations;
        if (set == null) {
            return;
        }
        out.append("----------------------------------- \r\n"); //$NON-NLS-1$
        out.append("ANOTACOES DO ANALISTA (nao sao evidencia original) \r\n"); //$NON-NLS-1$
        out.append("Nota: ").append(set.notesCount()).append(" | Links: ").append(set.linksCount()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .append(" (").append(set.contactsCount()).append(" chats) | Fora do intervalo ignoradas: ") //$NON-NLS-1$ //$NON-NLS-2$
                .append(set.ignoredOutOfRange).append("\r\n\r\n"); //$NON-NLS-1$
        for (ChatAnnotation item : set.items) {
            CaptureAnchor anchor = anchorForMessages(item.messageId);
            out.append("- [msg ").append(item.messageId); //$NON-NLS-1$
            if (anchor != null && !anchor.date.isEmpty()) {
                out.append(" | ").append(anchor.date); //$NON-NLS-1$
            }
            out.append("]"); //$NON-NLS-1$
            if (item.hasNote()) {
                out.append(" NOTA (").append(safe(item.noteAuthor)).append("): ").append(item.noteText) //$NON-NLS-1$ //$NON-NLS-2$
                        .append("\r\n"); //$NON-NLS-1$
            }
            for (LinkedChatRef link : item.links) {
                out.append("    CHAT: ").append(safe(link.title)); //$NON-NLS-1$
                out.append(link.hasCapture() ? " -> " + link.captureFolder : " -> sem captura vinculada"); //$NON-NLS-1$
                out.append("\r\n"); //$NON-NLS-1$
            }
        }
        out.append("----------------------------------- \r\n\r\n"); //$NON-NLS-1$
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

    private JSONArray buildFrameMessageSignatures(CaptureFrame frame, Map<String, ExportedFile> exported) throws IOException {
        JSONArray signatures = new JSONArray();
        for (int i = 0; i < frame.blocks.size(); i++) {
            CaptureBlock block = frame.blocks.get(i);
            ExportedFile linkedFile = findExportedByHash(exported, block.hash);
            String type = coordinateBlockType(block, linkedFile);
            List<String> mediaHashes = new ArrayList<>();
            if (linkedFile != null && linkedFile.sha256 != null && !linkedFile.sha256.isBlank()) {
                mediaHashes.add(linkedFile.sha256);
            }
            boolean audioOrVideo = "audio".equalsIgnoreCase(type) || "video".equalsIgnoreCase(type);
            String identityText = audioOrVideo ? "" : block.text;
            JSONObject signature = CaptureIntegrity.messageSignature(block.id, type, identityText,
                    captureTimestamp(block.text), i, mediaHashes, null);
            if (audioOrVideo) {
                signature.put("transcriptionPolicy", "excluded-from-identity");
            }
            if (linkedFile != null) {
                signature.put("mediaPath", linkedFile.relativePath);
            }
            signatures.put(signature);
        }
        frame.messageSignatures = signatures;
        frame.semanticSignature = CaptureIntegrity.frameSignature(frame.sequence, signatures);
        return signatures;
    }

    private String captureTimestamp(String text) {
        if (text == null || text.isBlank()) {
            return ""; //$NON-NLS-1$
        }
        Matcher matcher = Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s+[-+]\\d{2}:?\\d{2})?").matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        matcher = Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}").matcher(text);
        return matcher.find() ? matcher.group() : ""; //$NON-NLS-1$
    }
    private String buildWhatsAppCoordinatesJson(String status, List<CaptureFrame> frames, Map<String, ExportedFile> exported, CaptureSourceMetadata metadata) throws IOException {
        CaptureAnchor startAnchor = anchorForMessage(frames, captureStartMessageId, false);
        CaptureAnchor endAnchor = anchorForMessage(frames, captureEndMessageId, true);
        StringBuilder out = new StringBuilder();
out.append("{\n"); //$NON-NLS-1$
        out.append("  \"version\": " + COORDINATES_SCHEMA_VERSION + ",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"pluginVersion\": ").append(json(CHAT_CAPTURE_PLUGIN_VERSION)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        out.append("  \"status\": ").append(json(status)).append(",\n"); //$NON-NLS-1$
        out.append("  \"generatedAt\": ").append(json(Instant.now().toString())).append(",\n"); //$NON-NLS-1$
        out.append("  \"mode\": \"vertical-image-chat\",\n"); //$NON-NLS-1$
        out.append("  \"startAnchor\": ").append(buildAnchorJson(startAnchor)).append(",\n"); //$NON-NLS-1$
        out.append("  \"endAnchor\": ").append(buildAnchorJson(endAnchor)).append(",\n"); //$NON-NLS-1$
        out.append("  \"sourceMetadata\": ").append(buildInlineMetadataJson(metadata)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
        String annotationsJson = buildAnnotationsJson();
        if (!annotationsJson.isEmpty()) {
            out.append("  ").append(annotationsJson).append(",\n"); //$NON-NLS-1$
        }
        out.append("  \"frames\": [\n"); //$NON-NLS-1$
        for (int i = 0; i < frames.size(); i++) {
            CaptureFrame frame = frames.get(i);
            JSONArray messageSignatures = buildFrameMessageSignatures(frame, exported);
            out.append("    {\"sequence\": ").append(frame.sequence) //$NON-NLS-1$
                    .append(", \"image\": ").append(json(frame.image)) //$NON-NLS-1$
                    .append(", \"framePngSha256\": ").append(json(frame.pngSha256)) //$NON-NLS-1$
                    .append(", \"semanticSignature\": ").append(frame.semanticSignature) //$NON-NLS-1$
                    .append(", \"messageSignatures\": ").append(messageSignatures) //$NON-NLS-1$
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
                        .append(", \"mediaX\": ").append(block.mediaX) //$NON-NLS-1$
                        .append(", \"mediaY\": ").append(block.mediaY) //$NON-NLS-1$
                        .append(", \"mediaWidth\": ").append(block.mediaWidth) //$NON-NLS-1$
                        .append(", \"mediaHeight\": ").append(block.mediaHeight) //$NON-NLS-1$
                        .append(", \"textX\": ").append(block.textX) //$NON-NLS-1$
                        .append(", \"textY\": ").append(block.textY) //$NON-NLS-1$
                        .append(", \"textWidth\": ").append(block.textWidth) //$NON-NLS-1$
                        .append(", \"textHeight\": ").append(block.textHeight) //$NON-NLS-1$
                        .append(", \"documentTop\": ").append(block.documentTop) //$NON-NLS-1$
                        .append(", \"documentBottom\": ").append(block.documentBottom) //$NON-NLS-1$
                        .append(", \"text\": ").append(json(block.text)) //$NON-NLS-1$
                        .append(", \"hash\": ").append(json(block.hash)) //$NON-NLS-1$
                        .append(", \"file\": ").append(json(linkedFile == null ? null : linkedFile.relativePath)) //$NON-NLS-1$
                        .append(", \"identitySignature\": ").append(messageSignatures.optJSONObject(j)) //$NON-NLS-1$
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

/**
 * Escreve os campos opcionais de anotacoes no manifesto. Nao escreve nada quando o
 * recurso nao foi usado, mantendo o formato da versao 1.
 */
    private void writeAnnotationsFields(PrintWriter out) {
        String annotations = buildAnnotationsJson();
        if (!annotations.isEmpty()) {
            out.println("  " + annotations + ","); //$NON-NLS-1$
        }
        AnnotationPendingLink link = pendingLink;
        if (link != null && !link.originMessageId.isEmpty()) {
            out.println("  \"linkedFrom\": {\"chatKey\": " + json(link.originChatKey) //$NON-NLS-1$
                    + ", \"messageId\": " + json(link.originMessageId) //$NON-NLS-1$
                    + ", \"chatRefId\": " + json(link.chatRefId) //$NON-NLS-1$
                    + ", \"chatTitle\": " + json(link.chatTitle) + "},"); //$NON-NLS-1$
        }
    }

    /**
     * Bloco opcional de anotacoes. Devolve string vazia quando o recurso nao foi
     * usado nesta captura, o que mantem a saida identica a da versao 1.
     */
    private String buildAnnotationsJson() {
        ChatAnnotationSet set = pendingAnnotations;
        if (set == null) {
            return ""; //$NON-NLS-1$
        }
        return resolveLinkedCaptures(set);
    }

    /** Resolve {@code captureFolder} de cada link contra as capturas da raiz. */
    /**
 * Descobre em qual pasta de captura foi gravado cada chat vinculado.
 *
 * A varredura inclui a subpasta {@code vinculado}, porque as capturas vinculadas
 * ficam um nivel abaixo da raiz. A correspondencia e pelo nome do chat, ja que
 * esse e o unico dado que o vinculo carrega de forma estavel.
 */
    private String resolveLinkedCaptures(ChatAnnotationSet set) {
        List<CaptureRootEntry> known = listCaptureEntries();
        for (ChatAnnotation item : set.items) {
            for (LinkedChatRef link : item.links) {
                CaptureRootEntry entry = matchLinkedEntry(known, link);
                if (entry != null) {
                    link.captureFolder = entry.folder;
                }
            }
        }
        return setToAnnotationsJson(set);
    }

    /** Todas as capturas conhecidas: as da raiz e as da subpasta vinculado. */
    private List<CaptureRootEntry> listCaptureEntries() {
        List<CaptureRootEntry> entries = new ArrayList<>();
        Path outputRoot = currentCaptureOutputRoot;
        if (!CaptureIntegrity.isSafeDirectory(outputRoot)) {
            return entries;
        }
        try (Stream<Path> children = Files.list(outputRoot).limit(MAX_RECAPTURE_CANDIDATES * 2L)) {
            List<Path> roots = children.filter(CaptureIntegrity::isSafeDirectory)
                    .filter(path -> !isRecaptureAuxiliaryFolder(path))
                    .filter(path -> !isReportAuxiliaryFolder(path))
                    .sorted().collect(java.util.stream.Collectors.toList());
            for (Path folder : roots) {
                CaptureRootEntry entry = readCaptureRootEntry(outputRoot, folder);
                if (entry != null) {
                    entries.add(entry);
                }
                // capturas vinculadas moram um nivel abaixo
                Path linkedDir = CaptureIntegrity.resolveContained(folder, LINKED_FOLDER);
                if (isLinkedCaptureFolder(folder) || !CaptureIntegrity.isSafeDirectory(linkedDir)) {
                    continue;
                }
                try (Stream<Path> linked = Files.list(linkedDir).limit(MAX_RECAPTURE_CANDIDATES)) {
                    List<Path> linkedFolders = linked.filter(CaptureIntegrity::isSafeDirectory).sorted()
                            .collect(java.util.stream.Collectors.toList());
                    for (Path linkedFolder : linkedFolders) {
                        CaptureRootEntry linkedEntry = readCaptureRootEntry(outputRoot, linkedFolder);
                        if (linkedEntry != null) {
                            entries.add(linkedEntry);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Unable to list linked captures in {}", folder, e); //$NON-NLS-1$
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to resolve linked captures", e); //$NON-NLS-1$
        }
        return entries;
    }

    private CaptureRootEntry matchLinkedEntry(List<CaptureRootEntry> entries, LinkedChatRef link) {
        if (link == null || link.title.isBlank()) {
            return null;
        }
        String wanted = ChatAnnotationStore.normalizeKey(link.title);
        CaptureRootEntry fallback = null;
        for (CaptureRootEntry entry : entries) {
            String folderName = lastPathSegment(entry.folder);
            if (ChatAnnotationStore.normalizeKey(entry.chatId).equals(wanted)
                    || ChatAnnotationStore.normalizeKey(entry.title).equals(wanted)) {
                return entry;
            }
            // o nome da pasta e "<titulo>_msg<id>-<timestamp>"
            String base = ChatAnnotationStore.normalizeKey(chatTitleFromFolderName(folderName));
            if (!base.isEmpty() && (base.equals(wanted) || wanted.startsWith(base))) {
                if (fallback == null) {
                    fallback = entry;
                }
            }
        }
        return fallback;
    }

    private String chatTitleFromFolderName(String folderName) {
        String name = folderName;
        int msg = name.indexOf("_msg"); //$NON-NLS-1$
        if (msg > 0) {
            name = name.substring(0, msg);
        }
        return name.trim();
    }

    private static String lastPathSegment(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ""; //$NON-NLS-1$
        }
        String path = relativePath.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String setToAnnotationsJson(ChatAnnotationSet set) {
        StringBuilder out = new StringBuilder();
        out.append("{\n"); //$NON-NLS-1$
        out.append("    \"version\": 2,\n"); //$NON-NLS-1$
        out.append("    \"enabled\": true,\n"); //$NON-NLS-1$
        out.append("    \"notOriginalEvidence\": true,\n"); //$NON-NLS-1$
        out.append("    \"authoredBy\": \"analista\",\n"); //$NON-NLS-1$
        out.append("    \"generatedAt\": ").append(json(Instant.now().toString())).append(",\n"); //$NON-NLS-1$
        out.append("    \"pluginVersion\": ").append(json(CHAT_CAPTURE_PLUGIN_VERSION)).append(",\n"); //$NON-NLS-1$
        out.append("    \"toggleState\": ").append(json(set.toggleState)).append(",\n"); //$NON-NLS-1$
        out.append("    \"counts\": {\"notes\": ").append(set.notesCount()) //$NON-NLS-1$
                .append(", \"annotatedMessages\": ").append(set.annotatedMessagesCount()) //$NON-NLS-1$
                .append(", \"contacts\": ").append(set.contactsCount()) //$NON-NLS-1$
                .append(", \"inRange\": ").append(set.items.size()) //$NON-NLS-1$
                .append(", \"ignoredOutOfRange\": ").append(set.ignoredOutOfRange) //$NON-NLS-1$
                .append(", \"orphans\": ").append(set.orphans).append("},\n"); //$NON-NLS-1$
        out.append("    \"items\": [\n"); //$NON-NLS-1$
        for (int i = 0; i < set.items.size(); i++) {
            ChatAnnotation item = set.items.get(i);
            CaptureAnchor anchor = anchorForMessages(item.messageId);
            out.append("      {\"messageId\": ").append(json(item.messageId)) //$NON-NLS-1$
                    .append(", \"date\": ").append(json(anchor == null ? "" : anchor.date)) //$NON-NLS-1$ //$NON-NLS-2$
                    .append(", \"text\": ").append(json(anchor == null ? "" : anchor.text)); //$NON-NLS-1$ //$NON-NLS-2$
            if (item.hasNote()) {
                out.append(", \"note\": {\"text\": ").append(json(item.noteText)) //$NON-NLS-1$
                        .append(", \"author\": ").append(json(item.noteAuthor)) //$NON-NLS-1$
                        .append(", \"createdAt\": ").append(json(item.noteCreatedAt)) //$NON-NLS-1$
                        .append(", \"updatedAt\": ").append(json(item.noteUpdatedAt)).append("}"); //$NON-NLS-1$
            }
            out.append(", \"links\": ["); //$NON-NLS-1$
            for (int j = 0; j < item.links.size(); j++) {
                LinkedChatRef link = item.links.get(j);
                if (j > 0) {
                    out.append(", "); //$NON-NLS-1$
                }
                out.append("{\"chatRefId\": ").append(json(link.chatRefId)) //$NON-NLS-1$
                        .append(", \"title\": ").append(json(link.title)) //$NON-NLS-1$ //$NON-NLS-2$
                        .append(", \"origin\": ").append(json(link.origin)) //$NON-NLS-1$
                        .append(", \"sourceName\": ").append(json(link.sourceName)) //$NON-NLS-1$
                        .append(", \"chatKey\": ").append(json(link.chatKey)) //$NON-NLS-1$
                        // captureFolder e usado pelo JS para montar "../<pasta>/"
                        .append(", \"captureFolder\": ").append(json(link.captureFolder)) //$NON-NLS-1$ //$NON-NLS-2$
                        .append(", \"hasCapture\": ").append(link.hasCapture()) //$NON-NLS-1$
                        .append(", \"frames\": ").append(linkedFramesJson(link)) //$NON-NLS-1$
                        .append(", \"excerptFile\": \"texto.txt\"}"); //$NON-NLS-1$
            }
            out.append("]}"); //$NON-NLS-1$
            if (i + 1 < set.items.size()) {
                out.append(','); //$NON-NLS-1$
            }
            out.append('\n'); //$NON-NLS-1$
        }
        out.append("    ]"); //$NON-NLS-1$
        out.append("\n  }"); //$NON-NLS-1$
        return out.toString();
    }

    /** Frames da captura vinculada, em referencia relativa. */
    private String linkedFramesJson(LinkedChatRef link) {
        if (!link.hasCapture()) {
            return "[]"; //$NON-NLS-1$
        }
        List<String> frames = new ArrayList<>();
        for (String relativeFrame : safeLinkedFramePaths(currentCaptureOutputRoot, link.captureFolder)) {
            frames.add(json(relativeFrame));
        }
        return "[" + String.join(", ", frames) + "]"; //$NON-NLS-1$
    }

    static List<String> safeLinkedFramePaths(Path outputRoot, String captureFolder) {
        List<String> frames = new ArrayList<>();
        if (outputRoot == null || captureFolder == null || captureFolder.isBlank()
                || !CaptureIntegrity.isSafeDirectory(outputRoot)) {
            return frames;
        }
        Path folder;
        Path screenshots;
        try {
            folder = CaptureIntegrity.resolveContained(outputRoot, captureFolder);
            if (!CaptureIntegrity.isSafeDirectory(folder)) {
                return frames;
            }
            screenshots = CaptureIntegrity.resolveContained(folder, "screenshots"); //$NON-NLS-1$
            if (!CaptureIntegrity.isSafeDirectory(screenshots)) {
                return frames;
            }
        } catch (IOException unsafe) {
            return frames;
        }
        try (Stream<Path> stream = Files.list(screenshots).limit(MAX_RECAPTURE_CANDIDATES)) {
            stream.filter(CaptureIntegrity::isSafeRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".png") || name.endsWith(".jpg"); //$NON-NLS-1$ //$NON-NLS-2$
            }).sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    // o JS monta "../<captureFolder>/" + frame, entao o caminho precisa
                    // incluir o "screenshots/" que existe dentro da pasta da captura
                    .forEach(path -> frames.add("screenshots/" + path.getFileName().toString())); //$NON-NLS-1$
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return frames;
    }

    private CaptureAnchor anchorForMessages(String messageId) {
        for (CaptureFrame frame : currentCaptureFrames) {
            for (CaptureBlock block : frame.blocks) {
                if (messageId.equals(block.id)) {
                    CaptureAnchor anchor = new CaptureAnchor();
                    anchor.id = block.id;
                    anchor.type = block.type;
                    anchor.text = block.text;
                    return anchor.normalized();
                }
            }
        }
        CaptureAnchor anchor = new CaptureAnchor();
        anchor.id = messageId;
        return anchor;
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

/** Avatar generico para a lista de chats, lido do jar do parser. */
    private String avatarBase64() {
        if (avatarBase64Cache != null) {
            return avatarBase64Cache;
        }
        String encoded = "";
        try (InputStream in = HtmlLinkViewer.class.getClassLoader()
                .getResourceAsStream("iped/parsers/whatsapp/img/avatar.png")) { //$NON-NLS-1$
            if (in != null) {
                encoded = java.util.Base64.getEncoder().encodeToString(in.readAllBytes());
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to load contact avatar", e); //$NON-NLS-1$
        }
        avatarBase64Cache = encoded;
        return encoded;
    }

    private static String sha256Base64(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            return Base64.getEncoder().encodeToString(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); //$NON-NLS-1$
        }
    }

    static boolean isSafeCoordinatesRelativePath(String value, boolean singleSegment) {
        if (value == null) {
            return false;
        }
        String s = value.trim();
        if (s.isEmpty() || s.length() > 4096 || s.indexOf(':') >= 0
                || s.startsWith("/") || s.startsWith("\\") || s.contains("\\") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || s.contains("//")) { //$NON-NLS-1$
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                return false;
            }
        }
        String[] parts = s.split("/", -1); //$NON-NLS-1$
        if (singleSegment && parts.length != 1) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) { //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
        }
        return true;
    }

    private static void validateOptionalCoordinatesPath(JSONObject obj, String key, boolean singleSegment) throws IOException {
        Object raw = obj.opt(key);
        if (raw == null || raw == JSONObject.NULL) {
            return;
        }
        if (!(raw instanceof String)) {
            throw new IOException("Coordenadas legadas contêm caminho inválido ou esquema não permitido."); //$NON-NLS-1$
        }
        String value = ((String) raw).trim();
        if (!value.isEmpty() && !isSafeCoordinatesRelativePath(value, singleSegment)) {
            throw new IOException("Coordenadas legadas contêm caminho inválido ou esquema não permitido."); //$NON-NLS-1$
        }
    }

    static void validateWhatsAppCoordinatesData(JSONObject data) throws IOException {
        if (data == null) {
            return;
        }
        JSONArray frames = data.optJSONArray("frames"); //$NON-NLS-1$
        if (frames != null) {
            for (int i = 0; i < frames.length(); i++) {
                JSONObject frame = frames.optJSONObject(i);
                if (frame == null) {
                    continue;
                }
                validateOptionalCoordinatesPath(frame, "image", false); //$NON-NLS-1$
                JSONArray blocks = frame.optJSONArray("blocks"); //$NON-NLS-1$
                if (blocks != null) {
                    for (int j = 0; j < blocks.length(); j++) {
                        JSONObject block = blocks.optJSONObject(j);
                        if (block != null) {
                            validateOptionalCoordinatesPath(block, "file", false); //$NON-NLS-1$
                        }
                    }
                }
            }
        }
        JSONObject annotations = data.optJSONObject("annotations"); //$NON-NLS-1$
        JSONArray items = annotations == null ? null : annotations.optJSONArray("items"); //$NON-NLS-1$
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                JSONArray links = item == null ? null : item.optJSONArray("links"); //$NON-NLS-1$
                if (links == null) {
                    continue;
                }
                for (int k = 0; k < links.length(); k++) {
                    JSONObject link = links.optJSONObject(k);
                    if (link == null) {
                        continue;
                    }
                    validateOptionalCoordinatesPath(link, "captureFolder", false); //$NON-NLS-1$
                    validateOptionalCoordinatesPath(link, "chatRefId", true); //$NON-NLS-1$
                    validateOptionalCoordinatesPath(link, "excerptFile", false); //$NON-NLS-1$
                    JSONArray linkedFrames = link.optJSONArray("frames"); //$NON-NLS-1$
                    if (linkedFrames != null) {
                        for (int m = 0; m < linkedFrames.length(); m++) {
                            Object fRaw = linkedFrames.opt(m);
                            if (fRaw == null || fRaw == JSONObject.NULL) {
                                continue;
                            }
                            if (!(fRaw instanceof String)
                                    || (!((String) fRaw).trim().isEmpty() && !isSafeCoordinatesRelativePath((String) fRaw, false))) {
                                throw new IOException("Coordenadas legadas contêm caminho inválido ou esquema não permitido."); //$NON-NLS-1$
                            }
                        }
                    }
                }
            }
        }
    }

    private String buildWhatsAppMainScript() {
        return "var DATA=JSON.parse(document.getElementById('coords').textContent);var ANN=(DATA.annotations&&DATA.annotations.enabled)?DATA.annotations:null;var ANN_BY_MSG={};(ANN&&ANN.items||[]).forEach(function(a){ANN_BY_MSG[a.messageId]=a;});var chat=document.getElementById('chat'),side=document.getElementById('side'),modal=document.getElementById('modal'),modalCard=document.getElementById('modalCard');var AVATAR=chat?String(chat.getAttribute('data-avatar')||''):'';function esc(s){return String(s||'').replace(/[&<>\\\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\\\"':'&quot;',\"'\":'&#39;'}[c];});}function safeRelPath(p){var s=String(p||'').trim().replace(/\\\\/g,'/');if(!s||s.length>4096||s.indexOf(':')>=0||s.charAt(0)==='/'||s.indexOf('//')===0||/[\\x00-\\x1f\\x7f]/.test(s))return '';var parts=s.split('/');for(var i=0;i<parts.length;i++){if(!parts[i]||parts[i]==='.'||parts[i]==='..')return '';}return s;}function safeLinkedUrl(p){var s=String(p||'').trim();if(s.indexOf('../')!==0)return '';var rest=safeRelPath(s.substring(3));return rest?('../'+rest):'';}function pct(v,total){return total?((v*100/total)+'%'):'0%';}function isImageFile(p){return /\\.(png|jpe?g|webp|gif|bmp)$/i.test(String(p||'').split('?')[0]);}function itemTitle(b){if(b.type==='date')return 'Data';if(b.type==='audio')return 'Audio';if(b.type==='video')return 'Video';if(b.type==='image'||isImageFile(b.file))return 'Imagem';if(b.type==='file')return 'Arquivo';return 'Mensagem';}function openSide(html){side.innerHTML=html;side.classList.add('open');loadExtras(side);}function closeSide(){side.classList.remove('open');}function copyText(){var t=document.getElementById('copyText');if(!t)return;t.select();document.execCommand('copy');}function showText(b){openSide('<h2>'+itemTitle(b)+'</h2><textarea id=\"copyText\">'+esc(b.text||'')+'</textarea><br><button class=\"btn js-copy-text\" type=\"button\">Copiar</button><button class=\"btn secondary js-close-side\" type=\"button\">Fechar</button>');}function showFile(b){var f=safeRelPath(b.file);if(!f){showText(b);return;}openSide('<h2>'+itemTitle(b)+'</h2><p class=\"muted\">'+esc(b.text||f)+'</p><a class=\"btn\" href=\"'+esc(f)+'\" download>Baixar</a><a class=\"btn secondary\" href=\"'+esc(f)+'\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir</a><button class=\"btn secondary js-close-side\" type=\"button\">Fechar</button>');}function closeModalNow(){modal.querySelectorAll('audio,video').forEach(function(m){try{m.pause();m.removeAttribute('src');m.load();}catch(e){}});modalCard.innerHTML='';modal.classList.remove('open');}function openModal(html){closeModalNow();modalCard.innerHTML=html+'<br><button class=\"btn secondary js-close-modal\" type=\"button\">Fechar</button>';modal.classList.add('open');}function closeModal(e){if(e.target===modal)closeModalNow();}modal.addEventListener('click',function(e){closeModal(e);});function showAnnotation(a){openSide(renderAnnotation(a));}var ANN_CURRENT=null;function showAnnotationMenu(a){ANN_CURRENT=a;var links=a.links||[];var opts='';if(a.note)opts+='<button class=\"an-opt\" data-kind=\"note\">📝 Ver nota do analista</button>';links.forEach(function(l,i){opts+='<button class=\"an-opt\" data-kind=\"chat\" data-idx=\"'+i+'\">🔗 Chat vinculado: '+esc(l.title)+'</button>';});if(!opts)opts='<p class=\"an-empty\">Sem nota e sem chat vinculado.</p>';openModal('<h2>Anotação do analista</h2>'+(a.date?'<p class=\"muted\">Mensagem em '+esc(a.date)+'</p>':'')+'<div class=\"an-menu\">'+opts+'</div>');}function showAnnotationOption(kind,idx){var a=ANN_CURRENT;if(!a)return;if(kind==='note'){if(!a.note)return;openModal('<h2>Nota do analista</h2><div class=\"an-note\"><b>'+esc(a.note.author||'analista')+'</b><br>'+esc(a.note.text)+'</div>');return;}var l=(a.links||[])[parseInt(idx,10)];if(l)openModal('<h2>Chat vinculado</h2>'+linkRowHtml(l,false)+excerptHtml(l));}function linkRowHtml(l,clickable){var h='<div class=\"an-link\"'+(clickable?' data-link=\"'+esc(l.chatRefId)+'\"':'')+'><img src=\"'+AVATAR+'\" alt=\"\"><span>'+esc(l.title)+(l.sourceName?('<br><span class=\"muted\">'+esc(l.sourceName)+'</span>'):'')+((l.hasCapture||l.chatRefId)?'':'<br><span class=\"muted\">sem captura vinculada</span>')+'</span></div>';return h;}function excerptHtml(l){if(!(l.hasCapture||l.chatRefId))return '<div class=\"an-empty\">Sem captura vinculada para este chat.</div>';var cap=safeRelPath(l.captureFolder),ref=safeRelPath(l.chatRefId);if(cap&&cap.indexOf('/')>=0&&cap.indexOf('vinculado/')!==0)cap='';if(ref&&ref.indexOf('/')>=0)ref='';var base=cap?('../'+cap+'/'):(ref?('../vinculado/'+ref+'/'):'');if(!base)return '<div class=\"an-empty\">Caminho da captura vinculada indisponivel.</div>';var s='<div class=\"an-excerpt\"><b>Trecho capturado em '+esc(l.title||'')+'</b>';var fr=l.frames||[];if(!fr.length){var ex=safeRelPath(l.excerptFile||'texto.txt')||'texto.txt';s+='<pre class=\"an-pre\" data-excerpt=\"'+esc(base+ex)+'\"></pre>';}else{fr.forEach(function(f){var sf=safeRelPath(f);if(sf)s+='<img src=\"'+esc(base+sf)+'\" alt=\"\" data-zoom=\"'+esc(base+sf)+'\">';});}return s+'</div>';}function loadExtras(root){root.querySelectorAll('pre[data-excerpt]').forEach(function(p){var u=safeLinkedUrl(p.getAttribute('data-excerpt'));if(!u){p.textContent='Trecho nao encontrado.';return;}fetch(u).then(function(r){return r.ok?r.text():'';}).then(function(t){p.textContent=t;}).catch(function(){p.textContent='Trecho nao encontrado.';});});}function renderAnnotation(a){var h='<h2>Anotação do analista</h2>';if(a.date)h+='<p class=\"muted\">Mensagem em '+esc(a.date)+'</p>';if(a.note){h+='<div class=\"an-note\"><b>Nota</b> ('+esc(a.note.author||'analista')+'):<br>'+esc(a.note.text)+'</div>';}var links=a.links||[];if(!links.length){if(!a.note)return h+'<div class=\"an-empty\">Sem nota e sem link.</div>';return h;}h+='<div style=\"margin-top:12px\"><b>'+(links.length===1?'Chat vinculado':'Chats vinculados')+'</b></div>';if(links.length===1){return h+linkRowHtml(links[0],false)+excerptHtml(links[0]);}links.forEach(function(l){h+=linkRowHtml(l,true);});return h+'<div id=\"anExcerpt\"></div>';}function openLink(chatRefId){var a=null;(ANN&&ANN.items||[]).forEach(function(x){(x.links||[]).forEach(function(l){if(l.chatRefId===chatRefId)a=l;});});if(!a)return;document.getElementById('anExcerpt').innerHTML=excerptHtml(a);loadExtras(document.getElementById('anExcerpt'));}document.getElementById('side').addEventListener('click',function(e){if(e.target.closest('.js-copy-text')){copyText();return;}if(e.target.closest('.js-close-side')){closeSide();return;}var row=e.target.closest('[data-link]');if(row){openLink(row.getAttribute('data-link'));return;}var z=e.target.closest('[data-zoom]');if(z){var zs=safeLinkedUrl(z.getAttribute('data-zoom'));if(zs)openModal('<img src=\"'+esc(zs)+'\">');}});modalCard.addEventListener('click',function(e){if(e.target.closest('.js-close-modal')){closeModalNow();return;}var o=e.target.closest('.an-opt');if(!o)return;showAnnotationOption(o.getAttribute('data-kind'),o.getAttribute('data-idx'));});function activate(b){if(ANN&&ANN_BY_MSG[b.id]){showAnnotation(ANN_BY_MSG[b.id]);return;}var f=safeRelPath(b.file);if(b.type==='audio'&&f){openModal('<h2>Audio</h2><audio controls autoplay src=\"'+esc(f)+'\"></audio>');return;}if(b.type==='video'&&f){openModal('<h2>Video</h2><video controls autoplay src=\"'+esc(f)+'\"></video>');return;}if(f&&(b.type==='image'||isImageFile(f))){openModal('<h2>Imagem</h2><img src=\"'+esc(f)+'\"><br><a class=\"btn\" href=\"'+esc(f)+'\" download>Baixar imagem</a><a class=\"btn secondary\" href=\"'+esc(f)+'\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir em nova aba</a>');return;}if(b.type==='file'){showFile(b);return;}showText(b);}DATA.frames.forEach(function(f){var box=document.createElement('div');box.className='frame';box.style.maxWidth=f.width+'px';var img=document.createElement('img');var frameSrc=safeRelPath(f.image);if(frameSrc)img.src=frameSrc;img.alt='Frame '+f.sequence;box.appendChild(img);(f.blocks||[]).forEach(function(b){var h=document.createElement('button');h.className='hotspot';h.type='button';h.dataset.type=b.type;h.title=itemTitle(b);h.style.left=pct(b.x,f.width);h.style.top=pct(b.y,f.height);h.style.width=pct(b.width,f.width);h.style.height=pct(b.height,f.height);var ann=ANN_BY_MSG[b.id];if(ann){if(ann.note)h.className+=' has-note';if((ann.links||[]).length)h.className+=' has-link';}h.onclick=function(ev){ev.preventDefault();activate(b);};box.appendChild(h);if((b.type==='audio'||b.type==='video'||b.type==='image'||(b.file&&/\\.(png|jpe?g|webp|gif|bmp)$/i.test(String(b.file||''))))&&String(b.text||'').trim()){var tb=document.createElement('button');tb.className='transcript-btn';tb.type='button';tb.style.top=pct(b.y,f.height);tb.style.right='3px';tb.title='Transcricao';tb.textContent='T';tb.onclick=function(ev){ev.preventDefault();ev.stopPropagation();showText(b);};box.appendChild(tb);}if(ann&&(ann.note||(ann.links||[]).length)){var ic=document.createElement('button');ic.className='an-icon';ic.type='button';ic.style.top=pct(b.y,f.height);ic.title='Anotação do analista';ic.textContent=(ann.note?'📝':'')+((ann.links||[]).length?'🔗':'');ic.onclick=function(ev){ev.preventDefault();ev.stopPropagation();showAnnotationMenu(ANN_BY_MSG[b.id]);};box.appendChild(ic);}});chat.appendChild(box);});buildRail();function buildRail(){if(!ANN||!ANN.items||!ANN.items.length)return;var rail=document.getElementById('rail');if(!rail)return;ANN.items.forEach(function(a){if(!a.note&&!(a.links&&a.links.length))return;var card=document.createElement('div');card.className='rail-card '+(a.note?'note':'link');var h=document.createElement('h3');h.textContent=a.note?'Nota':'Chat vinculado';card.appendChild(h);var p=document.createElement('p');p.textContent=(a.date? (a.date+' - '):'')+ (a.note?a.note.text:((a.links||[]).map(function(l){return l.title;}).join(', ')));card.appendChild(p);var arrow=document.createElement('span');arrow.className='arrow';card.appendChild(arrow);card.onclick=function(){showAnnotation(a);};rail.appendChild(card);});}";
    }

    private void writeWhatsAppHtml(Path captureDir, Path path, String coordinatesJson) throws IOException {
        JSONObject parsedCoords;
        try {
            parsedCoords = new JSONObject(coordinatesJson);
        } catch (RuntimeException e) {
            throw new IOException("Coordenadas JSON inválidas.", e); //$NON-NLS-1$
        }
        validateWhatsAppCoordinatesData(parsedCoords);
        String mainScript = buildWhatsAppMainScript();
        String searchScript = buildWhatsAppSearchScript();
        String csp = "default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-" //$NON-NLS-1$
                + sha256Base64(mainScript) + "' 'sha256-" + sha256Base64(searchScript) //$NON-NLS-1$
                + "'; connect-src 'self' file:; object-src 'none'; form-action 'none'; base-uri 'none'"; //$NON-NLS-1$
        writeAtomicallyWithPrinter(captureDir, path, CaptureIntegrity.MAX_HTML_CAPTURE_BYTES, out -> {
            out.println("<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + csp + "\"><title>WhatsApp</title>"); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("<style>body{margin:0;background:#efe7dc;font-family:Arial,sans-serif;color:#222}.top{position:sticky;top:0;z-index:5;background:#075e54;color:white;padding:12px 18px;box-shadow:0 1px 4px rgba(0,0,0,.25)}.top h1{font-size:18px;margin:0}.wrap{max-width:960px;margin:0 auto;padding:18px 12px 80px}.frame{position:relative;margin:0 auto 6px;line-height:0;background:#ddd;box-shadow:0 1px 3px rgba(0,0,0,.15)}.frame img{width:100%;height:auto;display:block}.hotspot{position:absolute;border:2px solid transparent;background:rgba(37,211,102,.001);cursor:pointer;box-sizing:border-box}.hotspot:hover{border-color:#25d366;background:rgba(37,211,102,.16)}.hotspot[data-type='audio'],.hotspot[data-type='video'],.hotspot[data-type='image'],.hotspot[data-type='file']{background:rgba(3,169,244,.001)}.hotspot[data-type='audio']:hover,.hotspot[data-type='video']:hover,.hotspot[data-type='image']:hover,.hotspot[data-type='file']:hover{border-color:#03a9f4;background:rgba(3,169,244,.16)}#side{position:fixed;right:-360px;top:0;bottom:0;width:320px;background:#fff;z-index:20;box-shadow:-2px 0 10px rgba(0,0,0,.25);transition:right .2s ease;padding:18px;overflow:auto}#side.open{right:0}#side h2{font-size:18px;margin:0 0 12px}#side textarea{width:100%;height:180px;box-sizing:border-box}.btn{display:inline-block;margin:8px 6px 0 0;padding:8px 12px;border:0;background:#075e54;color:white;text-decoration:none;cursor:pointer}.btn.secondary{background:#555}#modal{position:fixed;inset:0;display:none;align-items:center;justify-content:center;background:rgba(0,0,0,.74);z-index:30;padding:24px;box-sizing:border-box}#modal.open{display:flex}.modal-card{max-width:min(920px,96vw);max-height:92vh;background:#111;color:white;padding:14px;box-sizing:border-box}.modal-card img,.modal-card video{max-width:100%;max-height:75vh}.modal-card audio{width:min(720px,88vw)}.muted{color:#666;font-size:13px}#side .an-note{background:#fff8e1;border-left:4px solid #f9a825;padding:8px 10px;margin:8px 0;white-space:pre-wrap;overflow-wrap:anywhere}#side .an-link{display:flex;align-items:center;gap:10px;padding:8px 4px;border-top:1px solid #eee;cursor:pointer}#side .an-link:hover{background:#f2f7f6}#side .an-link img{width:36px;height:36px;border-radius:50%;object-fit:cover;flex:0 0 auto}#side .an-excerpt{margin-top:10px;border-top:1px solid #ddd;padding-top:10px}#side .an-excerpt img{width:100%;border:1px solid #ddd;margin-bottom:8px;cursor:zoom-in}#side .an-empty{color:#777;font-size:13px;margin:8px 0;overflow-wrap:anywhere}#side .an-pre{white-space:pre-wrap;font-size:12px;max-height:260px;overflow:auto;background:#fafafa;padding:8px;border:1px solid #eee}.an-icon{position:absolute;left:3px;min-width:26px;height:26px;padding:0 5px;margin:0;font-size:14px;line-height:24px;background:#fffbe9;border:2px solid #f9a825;border-radius:6px;cursor:pointer;box-shadow:0 1px 4px rgba(0,0,0,.35);z-index:3}.an-icon:hover{background:#fff3c4}.an-menu{display:flex;flex-direction:column;gap:8px;margin-top:10px;max-width:420px}.an-opt{display:block;width:100%;text-align:left;padding:11px 13px;margin:0;font-size:14px;background:#0b6b5d;color:#fff;border:0;border-radius:6px;cursor:pointer;overflow-wrap:anywhere}.an-opt:hover{background:#0d8a77}.hotspot.has-note{box-shadow:inset 0 0 0 2px #f9a825}.hotspot.has-link{box-shadow:inset 0 0 0 2px #25d366}.hotspot.has-note.has-link{box-shadow:inset 0 0 0 2px #b26a00}.layout{display:flex;gap:18px;max-width:1220px;margin:0 auto;padding:18px 12px 80px;align-items:flex-start}.centerCol{flex:1 1 auto;min-width:0;display:flex;flex-direction:column;gap:10px}.rail{width:250px;flex:0 0 250px;position:sticky;top:70px;display:flex;flex-direction:column;gap:10px}.rail-card{background:#fff;border:1px solid #d8d1c8;border-radius:8px;padding:8px 10px;box-shadow:0 1px 4px rgba(0,0,0,.12);cursor:pointer;position:relative}.rail-card:hover{background:#f7f4ee}.rail-card .arrow{position:absolute;right:-12px;top:14px;border:6px solid transparent;border-left-color:#d8d1c8}.rail-card.note{border-left:4px solid #f9a825}.rail-card.link{border-left:4px solid #25d366}.rail-card h3{margin:0 0 4px;font-size:13px}.rail-card p{margin:0;font-size:12px;color:#555;overflow-wrap:anywhere}.transcript-btn{position:absolute;right:3px;top:3px;min-width:24px;height:24px;padding:0 4px;margin:0;font-size:12px;line-height:22px;background:#e3f2fd;border:2px solid #1976d2;border-radius:6px;cursor:pointer;box-shadow:0 1px 4px rgba(0,0,0,.35);z-index:4}.transcript-btn:hover{background:#bbdefb}.iped-capture-running .transcript-btn{display:none !important}@media print{.transcript-btn{display:none !important}}</style>"); //$NON-NLS-1$
            out.println("</head><body><div class=\"top\"><h1>WhatsApp - visualiza&ccedil;&atilde;o por imagens</h1></div><div class=\"layout\"><aside id=\"rail\"></aside><div class=\"centerCol\"><div class=\"wrap\" id=\"chat\" data-avatar=\"data:image/png;base64," + html(avatarBase64()) + "\"></div></div></div><aside id=\"side\"></aside><div id=\"modal\"><div class=\"modal-card\" id=\"modalCard\"></div></div>"); //$NON-NLS-1$
            out.println("<script id=\"coords\" type=\"application/json\">" + coordinatesJson.replace("</", "<\\/") + "</script>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            out.println("<script>" + mainScript + "</script>"); //$NON-NLS-1$ //$NON-NLS-2$
            writeWhatsAppSearchHtml(out, searchScript);
            out.println("</body></html>"); //$NON-NLS-1$
        });
    }

    private void writeWhatsAppSearchHtml(PrintWriter out) {
        writeWhatsAppSearchHtml(out, buildWhatsAppSearchScript());
    }

    private String buildWhatsAppSearchScript() {
        return "(function(){"
                + "if(!window.DATA||typeof esc!=='function')return;"
                + "var bar=document.createElement('div');bar.id='searchBar';"
                + "var inp=document.createElement('input');inp.type='search';"
                + "inp.placeholder='Pesquisar texto nas mensagens';inp.autocomplete='off';"
                + "var cnt=document.createElement('span');cnt.id='searchCount';"
                + "bar.appendChild(inp);bar.appendChild(cnt);var chat=document.getElementById('chat');if(chat&&chat.parentNode){chat.parentNode.insertBefore(bar,chat);}else{document.body.insertBefore(bar,document.body.firstChild);}"
                + "var panel=document.createElement('div');panel.id='searchResults';var chat2=document.getElementById('chat');if(chat2&&chat2.parentNode){chat2.parentNode.insertBefore(panel,chat2);}else{document.body.appendChild(panel);}"
                + "var INDEX=[];DATA.frames.forEach(function(f,fi){(f.blocks||[]).forEach(function(b){"
                + "var t=String(b.text||'').replace(/\\s+/g,' ').trim();if(t)INDEX.push({fi:fi,seq:f.sequence,id:b.id,text:t,type:b.type});});});"
                + "function norm(s){var v=String(s||'').toLowerCase().trim();try{v=v.normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}catch(e){}return v;}"
                + "function snip(t,term){var n=norm(t),p=n.indexOf(term);if(p<0)return t.length>90?t.substring(0,90)+'...':t;"
                + "var s=Math.max(0,p-40),e=Math.min(t.length,p+term.length+60);return (s>0?'...':'')+t.substring(s,e)+(e<t.length?'...':'');}"
                + "function hotspotFor(fi,id){var fr=document.querySelectorAll('#chat .frame')[fi];if(!fr)return null;"
                + "var hs=fr.querySelectorAll('.hotspot'),blocks=(DATA.frames[fi].blocks||[]);"
                + "for(var i=0;i<blocks.length;i++){if(blocks[i].id===id)return hs[i];}return null;}"
                + "function go(it){var fr=document.querySelectorAll('#chat .frame')[it.fi];if(!fr)return;fr.scrollIntoView();"
                + "var old=document.querySelectorAll('.hotspot.found');for(var i=0;i<old.length;i++)old[i].classList.remove('found');"
                + "var h=hotspotFor(it.fi,it.id);if(h){h.classList.add('found');(function(n){setTimeout(function(){n.classList.remove('found');},2600);})(h);}"
                + "if(typeof showText==='function')showText({text:it.text,type:it.type});}"
                + "var timer=null;function run(){var q=norm(inp.value);panel.innerHTML='';"
                + "if(!q){panel.classList.remove('open');cnt.textContent='';return;}"
                + "var terms=q.split(/\\s+/).filter(function(t){return t;});var found=[];"
                + "for(var i=0;i<INDEX.length&&found.length<300;i++){var n=norm(INDEX[i].text),ok=true;"
                + "for(var t=0;t<terms.length;t++){if(n.indexOf(terms[t])<0){ok=false;break;}}if(ok)found.push(INDEX[i]);}"
                + "if(!found.length){cnt.textContent='nenhum resultado';panel.classList.remove('open');return;}"
                + "cnt.textContent=found.length+(found.length>1?' resultados':' resultado')"
                + "+(found.length>=300?' (limite)':'');panel.classList.add('open');"
                + "found.forEach(function(it){var d=document.createElement('div');d.className='res';"
                + "var head=document.createElement('span');head.className='res-seq';"
                + "head.textContent='Frame '+it.seq+' - '+itemTitle({type:it.type});"
                + "var body=document.createElement('div');body.textContent=snip(it.text,terms[0]);"
                + "d.appendChild(head);d.appendChild(body);"
                + "d.onclick=function(){var old=panel.querySelectorAll('.res.selected');for(var i=0;i<old.length;i++)old[i].classList.remove('selected');d.classList.add('selected');go(it);};panel.appendChild(d);});}"
                + "inp.addEventListener('input',function(){if(timer)clearTimeout(timer);timer=setTimeout(run,300);});"
                + "inp.addEventListener('keydown',function(e){if(e.key==='Escape'){inp.value='';run();}});"
                + "})();";
    }

    /**
     * Busca por texto no HTML gerado.
     *
     * O {@code whatsapp.html} mostra o chat por imagens, entao nao ha texto no DOM
     * para o navegador pesquisar. O indice vem do {@code blocks} de cada frame, que
     * guarda o texto de cada mensagem no momento da captura. O resultado rola ate o
     * frame, destaca o bloco e mostra o texto na lateral.
     */
    private void writeWhatsAppSearchHtml(PrintWriter out, String searchScript) {
        out.println("<style>#searchBar{position:sticky;top:0;z-index:100;background:#075e54;padding:8px 18px;"
                + "display:flex;gap:8px;align-items:center;box-shadow:0 1px 4px rgba(0,0,0,.25);margin:0 auto 10px;max-width:960px;width:100%;box-sizing:border-box}"
                + "#searchBar input{flex:1;min-width:140px;padding:6px 10px;border:0;border-radius:4px;font-size:14px}"
                + "#searchCount{color:#cfe9df;font-size:13px;white-space:nowrap}"
                + "#searchResults{position:relative;width:100%;max-width:960px;margin:8px auto 0;max-height:58vh;overflow:auto;"
                + "background:#fff;border-radius:6px;box-shadow:0 2px 12px rgba(0,0,0,.3);display:none;z-index:25}"
                + "#searchResults .res.selected{background:#cfe9df;border-left:4px solid #075e54}"
                + "#searchResults.open{display:block}#searchResults .res{padding:8px 10px;border-bottom:1px solid #eee;"
                + "cursor:pointer;font-size:13px;line-height:1.3}#searchResults .res:hover{background:#f0f8f4}"
                + "#searchResults .res-seq{display:block;color:#075e54;font-weight:bold;font-size:11px}"
                + ".hotspot.found{border-color:#1565c0!important;background:rgba(21,101,192,.30)!important;"
                + "box-shadow:0 0 0 3px rgba(21,101,192,.5)!important}</style>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        out.println("<script>" + searchScript + "</script>"); //$NON-NLS-1$ //$NON-NLS-2$
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
        setAnnotationBadgesVisible(enabled);
        if (enabled) {
            executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.enableInteraction();}"); //$NON-NLS-1$
            setHtmlViewerMouseTransparent(false);
        } else {
            // o realce da busca ficaria gravado no PNG do frame
            clearChatSearchHighlight();
            setHtmlViewerMouseTransparent(true);
            executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.disableInteraction();}"); //$NON-NLS-1$
        }
    }

    private void setAnnotationBadgesVisible(boolean visible) {
        executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.hideAnnotationBadges(" //$NON-NLS-1$
                + (visible ? "false" : "true") + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
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

    private void closeSensitiveImageMatcher() {
        SensitiveImageMatcher existing = sensitiveImageMatcher;
        sensitiveImageMatcher = null;
        if (existing != null) {
            existing.close();
        }
    }

    private void prepareSensitiveImageMatcher() {
        sensitiveBlurHashes.clear();
        closeSensitiveImageMatcher();
        if (!CaptureIntegrity.isSafeDirectory(sensitiveImagesFolder)) {
            return;
        }
        SensitiveImageMatcher matcher = null;
        try {
            matcher = new SensitiveImageMatcher(sensitiveImagesFolder, sensitiveSimilarityPercent);
            if (matcher.hasReferences()) {
                sensitiveImageMatcher = matcher;
                matcher = null;
                chatCapturePanel.setStatusText("Blur sensivel ativo (" + sensitiveSimilarityPercent + "%): " //$NON-NLS-1$ //$NON-NLS-2$
                        + sensitiveImagesFolder);
            } else {
                // Pasta de referencia vazia ou sem imagens validas: trata como sem censura.
                LOGGER.warn("Sensitive image folder has no usable references; disabling blur: {}", sensitiveImagesFolder); //$NON-NLS-1$
                chatCapturePanel.setStatusText("Blur sensivel desativado: pasta sem imagens validas."); //$NON-NLS-1$
                sensitiveImagesFolder = null;
            }
        } catch (Throwable e) {
            LOGGER.warn("Unable to initialize sensitive image blur from {}", sensitiveImagesFolder, e); //$NON-NLS-1$
            chatCapturePanel.setStatusText("Blur sensivel desativado: " + e.getMessage()); //$NON-NLS-1$
            sensitiveImagesFolder = null;
        } finally {
            if (matcher != null) {
                matcher.close();
            }
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
        if (job == null || !CaptureIntegrity.isSafeDirectory(job.folderPath) || hash == null || hash.isBlank()
                || matcher == null) {
            return false;
        }
        String wanted = hash.trim().toUpperCase();
        try {
            Path coordinates = CaptureIntegrity.resolveContained(job.folderPath, "whatsapp-coordinates.json"); //$NON-NLS-1$
            if (!CaptureIntegrity.isSafeRegularFile(coordinates)) {
                return false;
            }
            return matchesSensitiveImageInCoordinatesJson(job.folderPath, CaptureIntegrity.readJsonUtf8Limited(coordinates),
                    wanted, matcher);
        } catch (Exception e) {
            LOGGER.debug("Unable to match sensitive image from previous capture for hash {}", hash, e); //$NON-NLS-1$
        }
        return false;
    }

    static boolean matchesSensitiveImageInCoordinatesJson(Path captureRoot, String coordinatesJson, String wantedHash,
            SensitiveImageMatcher matcher) {
        return matchesSensitiveImageInCoordinatesJson(captureRoot, coordinatesJson, wantedHash,
                matcher == null ? null : (java.util.function.Predicate<File>) matcher::matches);
    }

    static boolean matchesSensitiveImageInCoordinatesJson(Path captureRoot, String coordinatesJson, String wantedHash,
            java.util.function.Predicate<File> matcher) {
        if (captureRoot == null || matcher == null || wantedHash == null || wantedHash.isBlank()) {
            return false;
        }
        JSONObject root = parseJsonObjectOrNull(coordinatesJson);
        if (root == null) {
            return false;
        }
        String wanted = wantedHash.trim().toUpperCase();
        JSONArray frames = root.optJSONArray("frames"); //$NON-NLS-1$
        if (frames != null) {
            for (int i = 0; i < frames.length(); i++) {
                JSONObject frame = frames.optJSONObject(i);
                if (frame == null) {
                    continue;
                }
                String frameFile = firstNonBlank(jsonString(frame, "file"), jsonString(frame, "image")); //$NON-NLS-1$ //$NON-NLS-2$
                if (matchesCoordinateBlockArray(captureRoot, frame.optJSONArray("blocks"), frameFile, wanted, matcher)) { //$NON-NLS-1$
                    return true;
                }
            }
        }
        return matchesCoordinateBlockArray(captureRoot, root.optJSONArray("blocks"), "", wanted, matcher); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean matchesCoordinateBlockArray(Path captureRoot, JSONArray blocks, String frameFile, String wantedHash,
            java.util.function.Predicate<File> matcher) {
        if (blocks == null) {
            return false;
        }
        for (int j = 0; j < blocks.length(); j++) {
            JSONObject block = blocks.optJSONObject(j);
            if (block == null) {
                continue;
            }
            String blockHash = jsonString(block, "hash").trim().toUpperCase(); //$NON-NLS-1$
            if (!wantedHash.equals(blockHash)) {
                continue;
            }
            String file = firstNonBlank(jsonString(block, "file"), frameFile); //$NON-NLS-1$
            if (file.isBlank()) {
                continue;
            }
            Path candidate;
            try {
                candidate = CaptureIntegrity.resolveContained(captureRoot, file);
            } catch (IOException unsafe) {
                LOGGER.warn("Ignoring unsafe previous capture frame path {}: {}", file, unsafe.getMessage()); //$NON-NLS-1$
                continue;
            }
            if (CaptureIntegrity.isSafeRegularFile(candidate) && matcher.test(candidate.toFile())) {
                LOGGER.info("Sensitive blur matched previous capture file {} for hash {}", candidate, wantedHash); //$NON-NLS-1$
                return true;
            }
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
                if (fields.length > 12) block.mediaCssLeft = parseDoubleOrZero(fields[12]);
                if (fields.length > 13) block.mediaCssTop = parseDoubleOrZero(fields[13]);
                if (fields.length > 14) block.mediaCssWidth = parseDoubleOrZero(fields[14]);
                if (fields.length > 15) block.mediaCssHeight = parseDoubleOrZero(fields[15]);
                if (fields.length > 16) block.textCssLeft = parseDoubleOrZero(fields[16]);
                if (fields.length > 17) block.textCssTop = parseDoubleOrZero(fields[17]);
                if (fields.length > 18) block.textCssWidth = parseDoubleOrZero(fields[18]);
                if (fields.length > 19) block.textCssHeight = parseDoubleOrZero(fields[19]);
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

    private double parseDoubleOrZero(String value) {
        try {
            String v = safe(value).trim();
            if (v.isEmpty()) {
                return 0d;
            }
            return Double.parseDouble(v);
        } catch (Exception e) {
            return 0d;
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
            if (block.mediaCssWidth > 0 && block.mediaCssHeight > 0) {
                block.mediaX = Math.max(0, Math.min(plan.imageWidth, block.x + (int) Math.round(block.mediaCssLeft * CHAT_CAPTURE_SCALE)));
                block.mediaY = Math.max(0, Math.min(plan.imageHeight, block.y + (int) Math.round(block.mediaCssTop * CHAT_CAPTURE_SCALE)));
                block.mediaWidth = Math.max(1, Math.min(plan.imageWidth - block.mediaX, (int) Math.round(block.mediaCssWidth * CHAT_CAPTURE_SCALE)));
                block.mediaHeight = Math.max(1, Math.min(plan.imageHeight - block.mediaY, (int) Math.round(block.mediaCssHeight * CHAT_CAPTURE_SCALE)));
            }
            if (block.textCssWidth > 0 && block.textCssHeight > 0) {
                block.textX = Math.max(0, Math.min(plan.imageWidth, block.x + (int) Math.round(block.textCssLeft * CHAT_CAPTURE_SCALE)));
                block.textY = Math.max(0, Math.min(plan.imageHeight, block.y + (int) Math.round(block.textCssTop * CHAT_CAPTURE_SCALE)));
                block.textWidth = Math.max(1, Math.min(plan.imageWidth - block.textX, (int) Math.round(block.textCssWidth * CHAT_CAPTURE_SCALE)));
                block.textHeight = Math.max(1, Math.min(plan.imageHeight - block.textY, (int) Math.round(block.textCssHeight * CHAT_CAPTURE_SCALE)));
            }
        }
    }

    private void runOffEdtIfNeeded(Runnable task) {
        if (task == null || disposed || uiScriptExecutor.isShutdown()) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread() && !Platform.isFxApplicationThread()) {
            try {
                uiScriptExecutor.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        } else {
            task.run();
        }
    }

    private Object executeScriptAndWait(String script) {
        if (Platform.isFxApplicationThread()) {
            return webEngine.executeScript(script);
        }
        if (SwingUtilities.isEventDispatchThread()) {
            LOGGER.warn("executeScriptAndWait called on Swing EDT; risking FX/EDT deadlock"); //$NON-NLS-1$
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
                        + "var s=document.createElement('style');s.textContent='.iped-capture-start:not(.iped-capture-hide-marks){outline:3px solid #2e7d32!important}.iped-capture-end:not(.iped-capture-hide-marks){outline:3px solid #c62828!important}.iped-capture-range:not(.iped-capture-hide-marks){background-color:rgba(255,235,59,.18)!important}.iped-capture-running .iped-capture-start,.iped-capture-running .iped-capture-end,.iped-capture-running .iped-capture-range{outline:none!important;box-shadow:none!important;background-color:inherit!important}.iped-capture-no-interaction,.iped-capture-no-interaction *{cursor:none!important;pointer-events:none!important;user-select:none!important;-webkit-user-select:none!important}.iped-capture-no-interaction *{animation:none!important;transition:none!important;caret-color:transparent!important}.iped-annotation-badge{color:#b26a00!important;background:#fffbe9!important;border:1px solid #f9a825!important;border-radius:6px;padding:1px 6px!important;font-size:11px!important;font-family:Arial,sans-serif!important;cursor:pointer!important}.iped-capture-hide-badges .iped-annotation-badge,.iped-capture-running .iped-annotation-badge,.iped-capture-no-interaction .iped-annotation-badge{display:none!important}" + ".iped-search-hit{outline:2px solid #1565c0!important;outline-offset:-2px!important;background-color:rgba(21,101,192,.08)!important}" + ".iped-capture-running .iped-search-hit,.iped-capture-no-interaction .iped-search-hit,.iped-capture-hide-badges .iped-search-hit{outline:none!important;background-color:inherit!important}" + "';(document.head||document.documentElement).appendChild(s);" //$NON-NLS-1$
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
                        + "getMessageIndex:function(id){var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){if(items[i].id===id)return i;}return -1;}," //$NON-NLS-1$
                        + "getMessageCount:function(){return this.getOrderedMessages().length;}," //$NON-NLS-1$
                        + "getChatParticipants:function(){var out=[],seen={},self=this;var push=function(name,src){var n=self.norm(name);if(!n||seen[n]||n.length<2)return;seen[n]=1;out.push(encodeURIComponent(String(name).replace(/\\s+/g,' ').trim())+'|'+src);};var h=document.getElementById('topbar');if(h){var t=h.querySelector('.left');if(t)push(t.innerText||t.textContent||'','chat');}document.querySelectorAll(this.messageSelector).forEach(function(el){Array.prototype.forEach.call(el.querySelectorAll('.name,.name_call'),function(n){push(n.innerText||n.textContent||'','message');});});return out.join(';');}," //$NON-NLS-1$
                        + "setAnnotationBadges:function(rows){this.clearAnnotationBadges();var dec=decodeURIComponent;String(rows||'').split(';').forEach(function(row){if(!row)return;var p=row.split('|');if(p.length<3)return;var el=document.getElementById(dec(p[0]));if(!el)return;var b=document.createElement('span');b.className='iped-annotation-badge';b.setAttribute('data-note',p[1]);b.setAttribute('data-link',p[2]);b.style.cssText='float:left;display:inline-block;margin:2px 6px 2px 0;padding:1px 6px;font-size:11px;line-height:16px;font-family:Arial,sans-serif;color:#b26a00;background:#fffbe9;border:1px solid #f9a825;border-radius:6px;cursor:pointer;user-select:none;-webkit-user-select:none';b.textContent=(p[1]==='1'?'[nota]':'')+(p[2]==='1'?'[link]':'');b.addEventListener('click',function(ev){ev.preventDefault();ev.stopPropagation();try{if(window.app&&window.app.annotationAction){window.app.annotationAction(dec(p[0]));}}catch(e){}});el.insertBefore(b,el.firstChild);});}," //$NON-NLS-1$
                        + "clearAnnotationBadges:function(){Array.prototype.forEach.call(document.querySelectorAll('.iped-annotation-badge'),function(b){if(b.parentNode)b.parentNode.removeChild(b);});}," //$NON-NLS-1$
                        + "hideAnnotationBadges:function(hidden){document.documentElement.classList.toggle('iped-capture-hide-badges',!!hidden);}," //$NON-NLS-1$
                        + "clearSearchHighlight:function(){document.querySelectorAll('.iped-search-hit').forEach(function(e){e.classList.remove('iped-search-hit');});}," //$NON-NLS-1$
                        + "highlightSearchMessage:function(id){this.clearSearchHighlight();var el=document.getElementById(id);if(!el)return false;el.classList.add('iped-search-hit');return true;}," //$NON-NLS-1$
                        + "goToSearchMessage:function(id){var el=document.getElementById(id);if(!el)return false;var ok=this.highlightSearchMessage(id);this.scrollToMessageTop(id,this.getCaptureViewport().top);return ok;}," //$NON-NLS-1$
                        + "searchText:function(el){var t=el.textContent||'';return String(t).replace(/\\s+/g,' ').trim();}," //$NON-NLS-1$
                        + "snippetFor:function(text,term){var raw=String(text||'');var norm=this.norm(raw);var pos=norm.indexOf(term);if(pos<0)return raw.length>110?raw.substring(0,110)+'...':raw;var start=Math.max(0,pos-40),end=Math.min(raw.length,pos+term.length+70),out=(start>0?'...':'')+raw.substring(start,end)+(end<raw.length?'...':'');return out;}," //$NON-NLS-1$
                        + "searchMessages:function(term,limit){var q=this.norm(term);if(!q)return '0|';var terms=q.split(' ').filter(function(t){return t.length>0;});if(!terms.length)return '0|';var cap=limit>0?limit:400,total=0,rows=[],enc=encodeURIComponent,self=this;var items=this.getOrderedMessages();for(var i=0;i<items.length;i++){var el=items[i];if(!el||!el.id)continue;var raw=self.searchText(el);if(!raw)continue;var n=self.norm(raw),ok=true;for(var t=0;t<terms.length;t++){if(n.indexOf(terms[t])<0){ok=false;break;}}if(!ok)continue;total++;if(rows.length<cap)rows.push(enc(el.id)+','+enc(self.blockType(el))+','+enc(self.snippetFor(raw,terms[0])));}return total+'|'+rows.join(';');}," //$NON-NLS-1$
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
                        + "getVisibleCaptureBlocks:function(){var viewport=this.getCaptureViewport();var blocks=[];var sy=window.scrollY||window.pageYOffset||0;document.querySelectorAll(this.messageSelector).forEach((function(el){if(!el.id)return;var r=this.visualRect(el);if(r.bottom<viewport.top||r.top>viewport.bottom||r.width<=0||r.height<=0)return;var top=r.top;var bottom=r.bottom;var visibleTop=Math.max(top,viewport.top);var visibleBottom=Math.min(bottom,viewport.bottom);var visibleHeight=Math.max(0,visibleBottom-visibleTop);blocks.push({id:el.id,type:this.blockType(el),left:r.left,top:top,right:r.right,bottom:bottom,width:r.width,height:bottom-top,documentTop:sy+top,documentBottom:sy+bottom,complete:top>=viewport.top&&bottom<=viewport.bottom,hash:this.blockHash(el),text:this.blockText(el),visibleHeight:visibleHeight,media:(function(){var m=el.querySelector('audio,video,.audioImg,.videoImg,.iped-audio,.iped-video');if(!m)return null;var mr=m.getBoundingClientRect();return {l:mr.left-r.left,t:mr.top-r.top,w:mr.width,h:mr.height};})(),textRect:(function(){var media=el.querySelector('audio,video,.audioImg,.videoImg,.iped-audio,.iped-video,img.thumb,img');var r0=el.getBoundingClientRect();var minL=1e9,minT=1e9,maxR=-1,maxB=-1;try{var w=document.createTreeWalker(el,NodeFilter.SHOW_TEXT,null,false);var node;while(node=w.nextNode()){if(!node.nodeValue||!node.nodeValue.trim())continue;var p=node.parentNode;if(!p||p===media||(media&&media.contains(p)))continue;if(p.classList&&(p.classList.contains('time')||p.classList.contains('name')))continue;var range=document.createRange();range.selectNodeContents(node);var rects=range.getClientRects();for(var i=0;i<rects.length;i++){var nr=rects[i];if(nr.width<=0||nr.height<=0)continue;minL=Math.min(minL,nr.left);minT=Math.min(minT,nr.top);maxR=Math.max(maxR,nr.right);maxB=Math.max(maxB,nr.bottom);}}}catch(e){}if(maxR<0)return null;return {l:minL-r0.left,t:minT-r0.top,w:maxR-minL,h:maxB-minT};})()});}).bind(this));return blocks;}," //$NON-NLS-1$
                        + "getAllCaptureBlocks:function(){var blocks=[],self=this;document.querySelectorAll(this.messageSelector).forEach(function(el){if(!el.id)return;blocks.push({id:el.id,type:self.blockType(el),text:self.blockText(el),hash:self.blockHash(el),el:el});});return blocks;}," //$NON-NLS-1$
                        + "extractTimestamp:function(text){var s=String(text||''),m=s.match(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?/);if(m)return m[0];m=s.match(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}/);if(m)return m[0];return '';}," //$NON-NLS-1$
                        + "blockTimestamp:function(blocks,index){var b=blocks[index];if(!b)return '';var direct=this.extractTimestamp(b.text||'');if(direct)return direct;for(var d=-2;d<=2;d++){if(d===0)continue;var n=blocks[index+d];if(!n)continue;var near=this.extractTimestamp(n.text||'');if(near)return near;}return '';}," //$NON-NLS-1$
                        + "cleanMessageText:function(text){var s=this.norm(text||'');s=s.replace(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s+[-+]\\d{2}:?\\d{2})?/g,' ');s=s.replace(/\\d{4}[-\\/]\\d{2}[-\\/]\\d{2}/g,' ');s=s.replace(/\\b\\d{10,}\\b/g,' ');s=s.replace(/\\s+/g,' ').trim();return s;}," //$NON-NLS-1$
                        + "sameMessageText:function(blockText,anchorText){var a=this.cleanMessageText(anchorText),b=this.cleanMessageText(blockText);if(!a||!b)return false;if(a===b)return true;if(b.indexOf(a)>=0)return true;if(a.indexOf(b)>=0)return true;return false;}," //$NON-NLS-1$
                        + "sameDateText:function(blockText,anchorText){var a=this.norm(anchorText||''),b=this.norm(blockText||'');if(!a||!b)return false;if(a===b)return true;if(b.indexOf(a)>=0)return true;if(a.indexOf(b)>=0)return true;var ak=this.dateKey(a),bk=this.dateKey(b);return !!(ak&&bk&&ak===bk);}," //$NON-NLS-1$
                        + "sameRecaptureAnchor:function(blocks,index,anchor){var b=blocks[index];if(!b||!anchor)return false;if(anchor.id&&!/^iped-capture-date-/.test(anchor.id)&&b.id===anchor.id)return true;var wantedType=this.norm(anchor.type),blockType=this.norm(b.type);if(blockType==='date'&&wantedType!=='date')return false;if(wantedType==='date')return this.sameDateText(b.text,anchor.text||anchor.date);var blockTime=this.dateKey(this.blockTimestamp(blocks,index)),anchorTime=this.dateKey(anchor.date||anchor.text);if(!blockTime||!anchorTime||blockTime!==anchorTime)return false;var anchorHash=this.norm(anchor.hash);if(anchorHash)return this.norm(b.hash)===anchorHash;if(!this.cleanMessageText(anchor.text))return !wantedType||wantedType==='message'||wantedType===blockType;return this.sameMessageText(b.text,anchor.text);}," //$NON-NLS-1$
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
                        + "hideAnnotationBadgesState:function(){return document.documentElement.classList.contains('iped-capture-hide-badges');}," //$NON-NLS-1$
                    + "getCapturePlan:function(includeHeader,previousLastCompleteId){var viewport=this.getCaptureViewport();var blocks=this.getVisibleCaptureBlocks();var left=999999,right=0,safeBottom=viewport.top,firstRaw='',lastVisible='',lastComplete='',found=false,lastBottom=0,nextTop=0,scrollAdvance=0,nextMessageId='',firstEligible='',residualTopBlockId='',residualVisibleHeight=0,oversizedContinuation=false,headerBottom=0;blocks.forEach(function(b){left=Math.min(left,b.left);right=Math.max(right,b.right);if(!firstRaw)firstRaw=b.id;lastVisible=b.id;if(b.complete){found=true;lastComplete=b.id;lastBottom=Math.max(lastBottom,b.bottom);safeBottom=Math.max(safeBottom,b.bottom);}});if(found){blocks.forEach(function(b){if(b.id!==lastComplete&&b.top>=lastBottom&&(nextTop===0||b.top<nextTop))nextTop=b.top;});if(nextTop>lastBottom){safeBottom=lastBottom+Math.floor((nextTop-lastBottom)/2);}nextMessageId=this.getNextMessageAfter(lastComplete)||'';scrollAdvance=safeBottom-viewport.top;}if(!found){safeBottom=Math.max(viewport.top+1,viewport.top+Math.floor(viewport.height*.80));blocks.forEach(function(b){if(!firstRaw)firstRaw=b.id;lastVisible=b.id;left=Math.min(left,b.left);right=Math.max(right,b.right);});scrollAdvance=safeBottom-viewport.top;}if(blocks.length>0){var firstBlock=blocks[0];var sameAsPrevious=!!(previousLastCompleteId&&firstBlock&&firstBlock.id===previousLastCompleteId);var isOversized=!!(firstBlock&&firstBlock.height>viewport.height);var tinyResidual=!!(firstBlock&&firstBlock.visibleHeight>0&&(firstBlock.visibleHeight<=24||firstBlock.visibleHeight<=Math.max(12,firstBlock.height*.1)));if(sameAsPrevious&&tinyResidual&&!isOversized){residualTopBlockId=firstBlock.id;residualVisibleHeight=firstBlock.visibleHeight;}else if(sameAsPrevious&&isOversized){oversizedContinuation=true;}}var exportBlocks=blocks;firstEligible=exportBlocks.length?exportBlocks[0].id:firstRaw;if(includeHeader){var h=document.getElementById('topbar');if(h){var hr=h.getBoundingClientRect();if(hr.width>0&&hr.height>0){headerBottom=Math.max(0,Math.min(viewport.bottom,hr.bottom));safeBottom=Math.max(safeBottom,headerBottom);}}}var conv=document.getElementById('conversation'),topbar=document.getElementById('topbar'),base=conv||topbar;if(base){var br=base.getBoundingClientRect();if(br.width>0){left=br.left;right=br.right;}}if(left===999999||right<=left){left=0;right=window.innerWidth;}var pad=8;left=Math.max(0,Math.floor(left-pad));right=Math.min(window.innerWidth,Math.ceil(right+pad));safeBottom=Math.max(viewport.top+1,Math.min(viewport.bottom,Math.floor(safeBottom)));scrollAdvance=Math.max(1,Math.min(viewport.height,Math.floor(scrollAdvance||safeBottom-viewport.top)));var rectTop=includeHeader?0:viewport.top;var rectHeight=Math.max(1,safeBottom-rectTop);var sy=window.scrollY||window.pageYOffset||0;var documentTop=sy+rectTop;var documentBottom=sy+rectTop+rectHeight;var enc=encodeURIComponent;var blockData=exportBlocks.filter(function(b){return b.bottom>rectTop&&b.top<safeBottom;}).map(function(b){return [enc(b.id),enc(b.type),Math.round(b.left),Math.round(b.top),Math.round(b.width),Math.round(b.height),Math.round(b.visibleHeight),Math.round(b.documentTop),Math.round(b.documentBottom),b.complete?'1':'0',enc(b.hash||''),enc(b.text||''),b.media?Math.round(b.media.l):'',b.media?Math.round(b.media.t):'',b.media?Math.round(b.media.w):'',b.media?Math.round(b.media.h):'',b.textRect?Math.round(b.textRect.l):'',b.textRect?Math.round(b.textRect.t):'',b.textRect?Math.round(b.textRect.w):'',b.textRect?Math.round(b.textRect.h):''].join(',');}).join(';');return [left,rectTop,Math.max(1,right-left),rectHeight,documentTop,documentBottom,firstRaw,lastVisible,safeBottom,scrollAdvance,lastComplete,nextMessageId,firstEligible,residualTopBlockId,residualVisibleHeight,oversizedContinuation?'1':'0',blockData,headerBottom].join('|');}" //$NON-NLS-1$
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

    // ------------------------------------------------------------ pesquisa

    /**
     * Procura um texto no chat e devolve os resultados ao painel.
     *
     * Roda em thread propria porque {@link #executeScriptAndWait(String)} bloqueia
     * a thread que chama: invocar direto da EDT travaria a interface.
     */
    void searchChat(String term) {
        final String query = term == null ? "" : term.trim(); //$NON-NLS-1$
        if (query.isEmpty()) {
            clearChatSearchResults();
            return;
        }
        final int gen;
        synchronized (searchLock) {
            gen = searchGeneration.incrementAndGet();
        }
        if (disposed || uiScriptExecutor.isShutdown()) {
            return;
        }
        try {
            uiScriptExecutor.execute(() -> {
                if (disposed || gen != searchGeneration.get()) {
                    return;
                }
                Object raw = executeScriptAndWait("if(window.ipedChatCapture){window.ipedChatCapture.searchMessages(" //$NON-NLS-1$
                        + js(query) + "," + SEARCH_RESULT_LIMIT + ");}else{'';}"); //$NON-NLS-1$ //$NON-NLS-2$
                ChatSearchResult result = parseSearchResult(raw == null ? "" : raw.toString()); //$NON-NLS-1$
                List<ChatSearchHit> hitsCopy;
                synchronized (searchLock) {
                    if (gen != searchGeneration.get()) {
                        return;
                    }
                    searchHits.clear();
                    searchHits.addAll(result.hits);
                    searchTotal = result.total;
                    searchIndex = -1;
                    hitsCopy = new ArrayList<>(result.hits);
                }
                chatCapturePanel.setSearchResults(query, result.total, hitsCopy);
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    private static class ChatSearchResult {

        int total;
        final List<ChatSearchHit> hits = new ArrayList<>();

        ChatSearchResult(int total) {
            this.total = total;
        }
    }

    private ChatSearchResult parseSearchResult(String value) {
        ChatSearchResult result = new ChatSearchResult(0);
        int sep = value.indexOf('|');
        if (sep < 0) {
            return result;
        }
        try {
            result.total = Integer.parseInt(value.substring(0, sep).trim());
        } catch (NumberFormatException e) {
            result.total = 0;
        }
        for (String row : value.substring(sep + 1).split(";", -1)) { //$NON-NLS-1$ //$NON-NLS-2$
            if (row.isEmpty()) {
                continue;
            }
            String[] fields = row.split(",", -1); //$NON-NLS-1$ //$NON-NLS-2$
            if (fields.length < 3) {
                continue;
            }
            String id = decodeCaptureField(fields[0]);
            if (!id.isEmpty()) {
                result.hits.add(new ChatSearchHit(id, decodeCaptureField(fields[1]),
                        decodeCaptureField(fields[2])));
            }
        }
        return result;
    }

    private void clearChatSearchResults() {
        synchronized (searchLock) {
            searchGeneration.incrementAndGet();
            searchHits.clear();
            searchTotal = 0;
            searchIndex = -1;
        }
        chatCapturePanel.setSearchResults("", 0, new ArrayList<>()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void clearChatSearchHighlight() {
        searchIndex = -1;
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.clearSearchHighlight();}"); //$NON-NLS-1$
    }

    /** Vai para o resultado na posicao informada, realcando a mensagem. */
    void gotoChatSearchHit(int index) {
        List<ChatSearchHit> snapshot;
        synchronized (searchLock) {
            snapshot = new ArrayList<>(searchHits);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        int position = Math.floorMod(index, snapshot.size());
        ChatSearchHit hit = snapshot.get(position);
        runScript("if(window.ipedChatCapture){window.ipedChatCapture.goToSearchMessage(" //$NON-NLS-1$
                + js(hit.messageId) + ");}"); //$NON-NLS-1$ //$NON-NLS-2$
        searchIndex = position;
        chatCapturePanel.setCurrentSearchHit(position, snapshot.size());
    }

    void nextChatSearchHit() {
        gotoChatSearchHit(searchIndex + 1);
    }

    void previousChatSearchHit() {
        gotoChatSearchHit(searchIndex <= 0 ? 0 : searchIndex - 1);
    }

    int getChatSearchResultCount() {
        synchronized (searchLock) {
            return searchHits.size();
        }
    }

    static String json(String value) {
        if (value == null) {
            return "null"; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '"':
                out.append("\\\""); //$NON-NLS-1$
                break;
            case '\\':
                out.append("\\\\"); //$NON-NLS-1$
                break;
            case '\b':
                out.append("\\b"); //$NON-NLS-1$
                break;
            case '\f':
                out.append("\\f"); //$NON-NLS-1$
                break;
            case '\n':
                out.append("\\n"); //$NON-NLS-1$
                break;
            case '\r':
                out.append("\\r"); //$NON-NLS-1$
                break;
            case '\t':
                out.append("\\t"); //$NON-NLS-1$
                break;
            case '<':
                out.append("\\u003c"); //$NON-NLS-1$
                break;
            case '\u2028':
                out.append("\\u2028"); //$NON-NLS-1$
                break;
            case '\u2029':
                out.append("\\u2029"); //$NON-NLS-1$
                break;
            default:
                if (c < 0x20) {
                    out.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); //$NON-NLS-1$
                } else {
                    out.append(c);
                }
                break;
            }
        }
        return out.append('"').toString();
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

    private static String safe(String value) {
        return value == null ? "" : value; //$NON-NLS-1$
    }

    private static String nonBlank(String value, String fallback) {
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
        return sanitizeCsvCell(safe(value));
    }

    static String sanitizeCsvCell(String value) {
        String text = value == null ? "" : value; //$NON-NLS-1$
        boolean needsPrefix = false;
        if (!text.isEmpty()) {
            char first = text.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r' || first == '\n') {
                needsPrefix = true;
            } else {
                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    if (Character.isWhitespace(ch)) {
                        if (ch == '\t' || ch == '\r' || ch == '\n') {
                            needsPrefix = true;
                            break;
                        }
                        continue;
                    }
                    needsPrefix = (ch == '=' || ch == '+' || ch == '-' || ch == '@');
                    break;
                }
            }
        }
        String safeCell = needsPrefix ? "'" + text : text; //$NON-NLS-1$
        return "\"" + safeCell.replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private String html(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                .replace("\"", "&quot;").replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private String htmlBreakableName(String value) {
        String escaped = html(value);
        return escaped.replace("_", "_<wbr>").replace("-", "-<wbr>").replace(" ", " <wbr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
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

    private static final Pattern WINDOWS_RESERVED_DEVICE_PATTERN = Pattern.compile(
            "^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

    /**
     * Normaliza um nome de arquivo de segmento único seguro para Windows/POSIX,
     * retornando string vazia para entradas nulas ou equivalentes a {@code .}/{@code ..}.
     */
    static String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return ""; //$NON-NLS-1$
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String cleaned = slash >= 0 ? value.substring(slash + 1) : value;
        cleaned = cleaned.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]+", "_").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        cleaned = stripTrailingDotsAndSpaces(cleaned);
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) { //$NON-NLS-1$ //$NON-NLS-2$
            return ""; //$NON-NLS-1$
        }
        if (cleaned.length() > 180) {
            int dot = cleaned.lastIndexOf('.');
            if (dot > 0 && cleaned.length() - dot <= 16) {
                String ext = cleaned.substring(dot);
                String base = stripTrailingDotsAndSpaces(cleaned.substring(0, 180 - ext.length()));
                cleaned = base.isEmpty() ? ext : (base + ext);
            } else {
                cleaned = stripTrailingDotsAndSpaces(cleaned.substring(0, 180));
            }
            if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) { //$NON-NLS-1$ //$NON-NLS-2$
                return ""; //$NON-NLS-1$
            }
        }
        int firstDot = cleaned.indexOf('.');
        String basePart = (firstDot >= 0 ? cleaned.substring(0, firstDot) : cleaned).trim();
        if (WINDOWS_RESERVED_DEVICE_PATTERN.matcher(basePart).matches()) {
            cleaned = "_" + cleaned; //$NON-NLS-1$
            if (cleaned.length() > 180) {
                cleaned = stripTrailingDotsAndSpaces(cleaned.substring(0, 180));
            }
        }
        return cleaned;
    }

    private static String stripTrailingDotsAndSpaces(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == ' ')) {
            end--;
        }
        return value.substring(0, end).trim();
    }

    /** Copia arquivo falhando atomicamente se o destino já existir. */
    static void copyFileCreateNew(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source) || CaptureIntegrity.isLinkOrReparse(source)
                || CaptureIntegrity.isLinkOrReparse(target)
                || (target.getParent() != null && CaptureIntegrity.isLinkOrReparse(target.getParent()))) {
            throw new IOException("Symbolic links or junctions are not allowed during file copy"); //$NON-NLS-1$
        }
        try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
                java.io.OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
        }
    }

    private String safeName(String value) {
        return sanitizeFileName(value);
    }

    static class CaptureRootEntry {
        String folder;
        String title;
        String chatId;
        String createdAt;
        String chronologicalStart;
        String chronologicalEnd;
        String chronologicalRange;
        String chronologicalGroup;
        String chronologicalSortKey;
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
        int notesCount;
        int linksCount;
        int contactsCount;
        boolean annotationsEnabled;
        boolean legacyAnnotations;
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

    private static class CaptureRangeValidation {
        final CaptureBoundaryResolver.Resolution start;
        final CaptureBoundaryResolver.Resolution end;
        final String errorCode;

        private CaptureRangeValidation(CaptureBoundaryResolver.Resolution start,
                CaptureBoundaryResolver.Resolution end, String errorCode) {
            this.start = start;
            this.end = end;
            this.errorCode = errorCode == null ? "" : errorCode; //$NON-NLS-1$
        }

        static CaptureRangeValidation success(CaptureBoundaryResolver.Resolution start,
                CaptureBoundaryResolver.Resolution end) {
            return new CaptureRangeValidation(start, end, ""); //$NON-NLS-1$
        }

        static CaptureRangeValidation error(String errorCode) {
            return new CaptureRangeValidation(null, null, errorCode);
        }

        boolean valid() {
            return errorCode.isEmpty() && start != null && end != null;
        }
    }

    static class RecaptureJob {
        String folder = ""; //$NON-NLS-1$
        Path folderPath;
        String title = ""; //$NON-NLS-1$
        String chatId = ""; //$NON-NLS-1$
        String status = ""; //$NON-NLS-1$
        String startMessageId = ""; //$NON-NLS-1$
        String endMessageId = ""; //$NON-NLS-1$
        CaptureAnchor startAnchor = new CaptureAnchor();
        CaptureAnchor endAnchor = new CaptureAnchor();
        String startAnchorLabel;
        String endAnchorLabel;
        String sourceHash = ""; //$NON-NLS-1$
        String sourcePath = ""; //$NON-NLS-1$
        String sourceName = ""; //$NON-NLS-1$
        String sourceTitle = ""; //$NON-NLS-1$
        String luceneQuery = ""; //$NON-NLS-1$
        List<String> luceneQueries = new ArrayList<>();
String invalidReason;
        boolean valid;
        boolean htmlMessageCapture;
        boolean legacyCapture;
        boolean migrateLegacyToHtml;
        boolean resetLegacyRecords;
        boolean legacyAnnotations = true;
        Map<String, List<ReusableAttachment>> reusableAttachmentsByHashAndSize;
        Path reusableAttachmentStaging;
        final Set<String> stagedReusableAttachmentPaths = new HashSet<>();
        JSONObject captureFingerprint;
        JSONObject artifactGenerators;
        JSONArray expectedArtifacts;
        String priorCensorshipDigest = ""; //$NON-NLS-1$

        boolean targetHtmlMessageCapture() {
            return htmlMessageCapture || (legacyCapture && migrateLegacyToHtml);
        }

        String displayLabel() {
            String base = folder + " | " + (title == null || title.isBlank() ? chatId : title) //$NON-NLS-1$
                    + " | " + (startAnchorLabel != null ? startAnchorLabel : startAnchor == null ? "(âncora inicial indisponível)" : startAnchor.display()) //$NON-NLS-1$
                    + " -> " + (endAnchorLabel != null ? endAnchorLabel : endAnchor == null ? "(âncora final indisponível)" : endAnchor.display()); //$NON-NLS-1$ //$NON-NLS-2$
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

    static class CaptureAnchor {
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
            if (!id.isBlank()) {
                return true;
            }
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
        String pngSha256 = ""; //$NON-NLS-1$
        JSONObject semanticSignature = new JSONObject();
        JSONArray messageSignatures = new JSONArray();
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
        int mediaX = -1;
        int mediaY = -1;
        int mediaWidth = 0;
        int mediaHeight = 0;
        int textX = -1;
        int textY = -1;
        int textWidth = 0;
        int textHeight = 0;
        double mediaCssLeft;
        double mediaCssTop;
        double mediaCssWidth;
        double mediaCssHeight;
        double textCssLeft;
        double textCssTop;
        double textCssWidth;
        double textCssHeight;
    }

    private static class VisibleMediaBlock {
        final String id;
        final String hash;

        VisibleMediaBlock(String id, String hash) {
            this.id = id;
            this.hash = hash;
        }
    }

/**
     * Vinculo pendente: o analista pediu para abrir o chat de um contato a partir de
     * uma anotacao. A proxima captura recebe o {@code linkedFrom} no manifesto.
     */
    private static class AnnotationPendingLink {
        final String originChatKey;
        final String originMessageId;
        final String chatRefId;
        final String chatTitle;

        AnnotationPendingLink(String originChatKey, String originMessageId, String chatRefId, String chatTitle) {
            this.originChatKey = originChatKey == null ? "" : originChatKey; //$NON-NLS-1$
            this.originMessageId = originMessageId == null ? "" : originMessageId; //$NON-NLS-1$
            this.chatRefId = chatRefId == null ? "" : chatRefId; //$NON-NLS-1$
            this.chatTitle = chatTitle == null ? "" : chatTitle; //$NON-NLS-1$
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
        String sha256;
        String path;
        String text;
        String title;
        String metadataText;
    }

    static class ExportedFile {
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

    static class SensitiveImageMatcher implements AutoCloseable {
        static final int MAX_SENSITIVE_IMAGE_BYTES = 32 * 1024 * 1024;
        static final int MAX_REFERENCE_IMAGES = 512;
        static final int MAX_IMAGE_DIMENSION = 8192;
        static final long MAX_IMAGE_PIXELS = 33_554_432L;
        private static final int MIN_FEATURES = 20;
        private static final int MIN_GOOD_MATCHES = 12;
        private static final double LOWE_RATIO = 0.72d;
        private static final float MAX_HAMMING_DISTANCE = 60f;
        private static volatile boolean opencvLoaded;

        enum ImageFormat {
            PNG("png"), //$NON-NLS-1$
            JPEG("jpeg"), //$NON-NLS-1$
            BMP("bmp"), //$NON-NLS-1$
            GIF("gif"), //$NON-NLS-1$
            WEBP("webp"); //$NON-NLS-1$

            private final String formatName;

            ImageFormat(String formatName) {
                this.formatName = formatName;
            }

            String formatName() {
                return formatName;
            }
        }

        private final ORB orb;
        private final DescriptorMatcher matcher;
        private final double minSimilarityRatio;
        private final Set<String> referenceFileHashes = new HashSet<>();
        private final List<Mat> referenceDescriptors = new ArrayList<>();
        private int referenceImageCount;
        private boolean closed;

        SensitiveImageMatcher() {
            this.orb = null;
            this.matcher = null;
            this.minSimilarityRatio = 0.90d;
        }

        SensitiveImageMatcher(Path folder, int similarityPercent) throws IOException {
            if (!CaptureIntegrity.isSafeDirectory(folder)) {
                throw new IOException("Sensitive images folder is missing or is a symbolic link/junction: " + folder); //$NON-NLS-1$
            }
            loadOpenCv();
            minSimilarityRatio = Math.max(0.50d, Math.min(0.90d, similarityPercent / 100.0d));
            try {
                orb = ORB.create(3000);
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
                try (Stream<Path> stream = Files.list(folder).limit(10_000L)) {
                    stream.filter(CaptureIntegrity::isSafeRegularFile).filter(SensitiveImageMatcher::isSupportedImage).limit(MAX_REFERENCE_IMAGES).forEach(path -> {
                        byte[] content = readBoundedImageBytes(path.toFile());
                        if (content == null || detectSupportedImageFormat(content) == null) {
                            return;
                        }
                        addReferenceFileHash(content);
                        referenceImageCount++;
                        Mat descriptors = descriptors(content);
                        if (descriptors != null && !descriptors.empty()) {
                            referenceDescriptors.add(descriptors);
                        } else {
                            releaseQuietly(descriptors);
                        }
                    });
                }
            } catch (IOException | RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        synchronized boolean hasReferences() {
            return !closed && (!referenceDescriptors.isEmpty() || !referenceFileHashes.isEmpty());
        }

        synchronized int referenceDescriptorCount() {
            return referenceDescriptors.size();
        }

        int referenceImageCount() {
            return referenceImageCount;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Mat descriptors : referenceDescriptors) {
                releaseQuietly(descriptors);
            }
            referenceDescriptors.clear();
            referenceFileHashes.clear();
        }

        private static void releaseQuietly(Mat mat) {
            if (mat != null) {
                try {
                    mat.release();
                } catch (RuntimeException ignored) {
                }
            }
        }

        private synchronized boolean matchesDescriptors(Mat candidateDescriptors) {
            if (closed || candidateDescriptors == null || candidateDescriptors.empty()) {
                return false;
            }
            for (Mat reference : referenceDescriptors) {
                if (isSimilar(reference, candidateDescriptors)) {
                    return true;
                }
            }
            return false;
        }

        boolean matches(File candidate) {
            byte[] content = readBoundedImageBytes(candidate);
            if (content == null) {
                return false;
            }
            return matches(content);
        }

        boolean matches(byte[] content) {
            if (content == null || content.length == 0 || content.length > MAX_SENSITIVE_IMAGE_BYTES) {
                return false;
            }
            if (detectSupportedImageFormat(content) == null) {
                return false;
            }
            if (matchesReferenceFileHash(content)) {
                return true;
            }
            Mat candidate = descriptors(content);
            try {
                return matchesDescriptors(candidate);
            } finally {
                releaseQuietly(candidate);
            }
        }

        static ImageFormat detectSupportedImageFormat(byte[] content) {
            if (content == null || content.length < 2 || content.length > MAX_SENSITIVE_IMAGE_BYTES) {
                return null;
            }
            if (content.length >= 8
                    && (content[0] & 0xFF) == 0x89
                    && content[1] == 0x50
                    && content[2] == 0x4E
                    && content[3] == 0x47
                    && content[4] == 0x0D
                    && content[5] == 0x0A
                    && content[6] == 0x1A
                    && content[7] == 0x0A) {
                return ImageFormat.PNG;
            }
            if (content.length >= 3
                    && (content[0] & 0xFF) == 0xFF
                    && (content[1] & 0xFF) == 0xD8
                    && (content[2] & 0xFF) == 0xFF) {
                return ImageFormat.JPEG;
            }
            if (content[0] == 'B' && content[1] == 'M') {
                return ImageFormat.BMP;
            }
            if (content.length >= 6
                    && content[0] == 'G'
                    && content[1] == 'I'
                    && content[2] == 'F'
                    && content[3] == '8'
                    && (content[4] == '7' || content[4] == '9')
                    && content[5] == 'a') {
                return ImageFormat.GIF;
            }
            if (content.length >= 12
                    && content[0] == 'R'
                    && content[1] == 'I'
                    && content[2] == 'F'
                    && content[3] == 'F'
                    && content[8] == 'W'
                    && content[9] == 'E'
                    && content[10] == 'B'
                    && content[11] == 'P') {
                return ImageFormat.WEBP;
            }
            return null;
        }

        static boolean areValidImageDimensions(int width, int height) {
            if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                return false;
            }
            return ((long) width) * ((long) height) <= MAX_IMAGE_PIXELS;
        }

        static byte[] readBoundedImageBytes(File file) {
            return file == null ? null : readBoundedImageBytes(file.toPath());
        }

        static byte[] readBoundedImageBytes(Path path) {
            if (path == null) {
                return null;
            }
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther() || !attrs.isRegularFile() || CaptureIntegrity.isLinkOrReparse(path)) {
                    return null;
                }
                long declaredSize = attrs.size();
                if (declaredSize <= 0L || declaredSize > MAX_SENSITIVE_IMAGE_BYTES) {
                    return null;
                }
                try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(declaredSize, 65536L));
                    byte[] buffer = new byte[8192];
                    long total = 0L;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_SENSITIVE_IMAGE_BYTES) {
                            return null;
                        }
                        out.write(buffer, 0, read);
                    }
                    return total == 0L ? null : out.toByteArray();
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to read image file {}", path, e); //$NON-NLS-1$
                return null;
            }
        }

        static int[] readValidatedDimensions(byte[] content) {
            ImageFormat format = detectSupportedImageFormat(content);
            if (format == null) {
                return null;
            }
            if (format == ImageFormat.WEBP) {
                return parseWebpDimensions(content);
            }
            return readImageIoDimensionsAndOptionalDecode(content, format, null);
        }

        private static int[] parseWebpDimensions(byte[] content) {
            if (content == null || content.length < 25) {
                return null;
            }
            if (content[12] == 'V' && content[13] == 'P' && content[14] == '8' && content[15] == 'X') {
                if (content.length < 30) {
                    return null;
                }
                int width = 1 + ((content[24] & 0xFF) | ((content[25] & 0xFF) << 8) | ((content[26] & 0xFF) << 16));
                int height = 1 + ((content[27] & 0xFF) | ((content[28] & 0xFF) << 8) | ((content[29] & 0xFF) << 16));
                return areValidImageDimensions(width, height) ? new int[] { width, height } : null;
            }
            if (content[12] == 'V' && content[13] == 'P' && content[14] == '8' && content[15] == 'L') {
                if (content[20] != 0x2F) {
                    return null;
                }
                long bits = (content[21] & 0xFFL)
                        | ((content[22] & 0xFFL) << 8)
                        | ((content[23] & 0xFFL) << 16)
                        | ((content[24] & 0xFFL) << 24);
                int width = 1 + (int) (bits & 0x3FFFL);
                int height = 1 + (int) ((bits >> 14) & 0x3FFFL);
                return areValidImageDimensions(width, height) ? new int[] { width, height } : null;
            }
            if (content[12] == 'V' && content[13] == 'P' && content[14] == '8' && content[15] == ' ') {
                if (content.length < 30
                        || (content[20] & 0x01) != 0
                        || (content[23] & 0xFF) != 0x9D
                        || content[24] != 0x01
                        || content[25] != 0x2A) {
                    return null;
                }
                int width = ((content[26] & 0xFF) | ((content[27] & 0xFF) << 8)) & 0x3FFF;
                int height = ((content[28] & 0xFF) | ((content[29] & 0xFF) << 8)) & 0x3FFF;
                return areValidImageDimensions(width, height) ? new int[] { width, height } : null;
            }
            return null;
        }

        static Mat decodeToGrayMat(byte[] content) {
            ImageFormat format = detectSupportedImageFormat(content);
            if (format == null) {
                return null;
            }
            if (format == ImageFormat.WEBP) {
                return decodeWebpWithOpenCv(content);
            }
            BufferedImage image = decodeImageIoWithBounds(content, format);
            if (image == null) {
                return null;
            }
            return toGrayCvMat(image);
        }

        private static BufferedImage decodeImageIoWithBounds(byte[] content, ImageFormat format) {
            BufferedImage[] decoded = new BufferedImage[1];
            if (readImageIoDimensionsAndOptionalDecode(content, format, decoded) == null) {
                return null;
            }
            return decoded[0];
        }

        static BufferedImage readBoundedImageFile(Path source) {
            byte[] content = readBoundedImageBytes(source);
            if (content == null || detectSupportedImageFormat(content) == null) {
                return null;
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(content);
                    ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
                BufferedImage[] decoded = new BufferedImage[1];
                if (readBoundedImageFromStream(iis, null, decoded, source.getFileName().toString()) == null) {
                    return null;
                }
                return decoded[0];
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Rejected malformed or oversized report image {}", source, e); //$NON-NLS-1$
                return null;
            }
        }

        private static int[] readImageIoDimensionsAndOptionalDecode(
                byte[] content, ImageFormat format, BufferedImage[] decodedOut) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(content);
                    ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
                return readBoundedImageFromStream(iis, format.formatName(), decodedOut, format.formatName());
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Rejected malformed {} image before OpenCV", format.formatName(), e); //$NON-NLS-1$
                return null;
            }
        }

        private static int[] readBoundedImageFromStream(
                ImageInputStream iis, String preferredFormatName, BufferedImage[] decodedOut, String label)
                throws IOException {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = preferredFormatName != null
                    ? ImageIO.getImageReadersByFormatName(preferredFormatName)
                    : ImageIO.getImageReaders(iis);
            ImageReader reader = selectPreferredImageReader(readers);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!areValidImageDimensions(width, height)) {
                    LOGGER.debug("Rejected oversized image {} ({}x{})", label, width, height); //$NON-NLS-1$
                    return null;
                }
                if (decodedOut != null) {
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null || !areValidImageDimensions(decoded.getWidth(), decoded.getHeight())) {
                        return null;
                    }
                    decodedOut[0] = decoded;
                }
                return new int[] { width, height };
            } finally {
                reader.dispose();
            }
        }

        private static ImageReader selectPreferredImageReader(Iterator<ImageReader> readers) {
            ImageReader preferred = null;
            ImageReader fallback = null;
            while (readers.hasNext()) {
                ImageReader candidate = readers.next();
                String moduleName = candidate.getClass().getModule() != null
                        ? candidate.getClass().getModule().getName()
                        : null;
                if (preferred == null && "java.desktop".equals(moduleName)) { //$NON-NLS-1$
                    preferred = candidate;
                } else if (fallback == null && preferred == null) {
                    fallback = candidate;
                } else {
                    candidate.dispose();
                }
            }
            if (preferred != null) {
                if (fallback != null) {
                    fallback.dispose();
                }
                return preferred;
            }
            return fallback;
        }

        private static Mat toGrayCvMat(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (!areValidImageDimensions(width, height)) {
                return null;
            }
            BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = gray.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            byte[] pixels = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
            if (pixels.length != width * height) {
                return null;
            }
            Mat mat = new Mat(height, width, CvType.CV_8UC1);
            try {
                mat.put(0, 0, pixels);
                Mat result = mat;
                mat = null;
                return result;
            } finally {
                releaseQuietly(mat);
            }
        }

        private static Mat decodeWebpWithOpenCv(byte[] content) {
            if (parseWebpDimensions(content) == null) {
                return null;
            }
            MatOfByte encoded = new MatOfByte(content);
            Mat image = null;
            try {
                image = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_GRAYSCALE);
                if (image == null || image.empty() || !areValidImageDimensions(image.cols(), image.rows())) {
                    releaseQuietly(image);
                    return null;
                }
                Mat result = image;
                image = null;
                return result;
            } finally {
                releaseQuietly(image);
                releaseQuietly(encoded);
            }
        }

        boolean matchesReferenceFileHash(byte[] content) {
            if (referenceFileHashes.isEmpty()) {
                return false;
            }
            return referenceFileHashes.contains(hex(content, "SHA-256")); //$NON-NLS-1$
        }

        private static String hex(byte[] content, String algorithm) {
            try {
                StringBuilder sb = new StringBuilder();
                for (byte b : MessageDigest.getInstance(algorithm).digest(content)) {
                    sb.append(String.format("%02X", b)); //$NON-NLS-1$
                }
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        void addReferenceFileHash(byte[] content) {
            String sha256 = hex(content, "SHA-256"); //$NON-NLS-1$
            if (!sha256.isBlank()) {
                referenceFileHashes.add(sha256);
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
            try {
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
            } finally {
                for (MatOfDMatch mat : knn) {
                    releaseQuietly(mat);
                }
            }
        }

        private Mat descriptors(byte[] content) {
            Mat img = decodeToGrayMat(content);
            if (img == null) {
                return null;
            }
            try {
                return descriptorsOf(img);
            } finally {
                releaseQuietly(img);
            }
        }

        Mat descriptorsOf(Mat img) {
            if (img == null || img.empty()) {
                return null;
            }
            boolean ownsGray = img.channels() > 1;
            Mat gray = ownsGray ? new Mat() : img;
            Mat mask = new Mat();
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            Mat descriptors = new Mat();
            try {
                if (ownsGray) {
                    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
                }
                orb.detectAndCompute(gray, mask, keypoints, descriptors);
                Mat result = descriptors;
                descriptors = null;
                return result;
            } finally {
                releaseQuietly(descriptors);
                releaseQuietly(keypoints);
                releaseQuietly(mask);
                if (ownsGray) {
                    releaseQuietly(gray);
                }
            }
        }

        private static boolean isSupportedImage(Path path) {
            String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(); //$NON-NLS-1$
            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    || name.endsWith(".bmp"); //$NON-NLS-1$
        }

        private static synchronized void loadOpenCv() throws IOException {
            loadOpenCv(null);
        }

        static synchronized void loadOpenCv(Path codeSourceOverride) throws IOException {
            if (opencvLoaded) {
                return;
            }
            Path verifiedDll = resolveVerifiedOpenCvDll(
                    codeSourceOverride != null ? codeSourceOverride : viewerCodeSourcePath());
            System.load(verifiedDll.toString());
            opencvLoaded = true;
            LOGGER.info("OpenCV loaded for sensitive image blur (verified SHA-256): {}", Core.VERSION); //$NON-NLS-1$
        }
    }

    private static volatile boolean loggedMissingOpenItem;
    private static final Pattern OPEN_ANCHOR_TAG_PATTERN = Pattern.compile("(?i)<a(?=[\\s>/])"); //$NON-NLS-1$
    private static final Pattern CLOSE_ANCHOR_TAG_PATTERN = Pattern.compile("(?i)</a\\s*>"); //$NON-NLS-1$

    static boolean isInsideOpenAnchorTag(String beforeHtml) {
        if (beforeHtml == null || beforeHtml.isEmpty()) {
            return false;
        }
        int lastOpen = -1;
        Matcher openMatcher = OPEN_ANCHOR_TAG_PATTERN.matcher(beforeHtml);
        while (openMatcher.find()) {
            lastOpen = openMatcher.start();
        }
        if (lastOpen < 0) {
            return false;
        }
        int lastClose = -1;
        Matcher closeMatcher = CLOSE_ANCHOR_TAG_PATTERN.matcher(beforeHtml);
        while (closeMatcher.find()) {
            lastClose = closeMatcher.start();
        }
        return lastOpen > lastClose;
    }

    private static boolean openSearcherItem(AttachmentSearcher searcher, String query, String highlightTerm,
            String sourceName) {
        if (searcher == null || query == null) {
            return false;
        }
        try {
            java.lang.reflect.Method open3 = searcher.getClass().getMethod("openItem", String.class, String.class,
                    String.class);
            if (!open3.canAccess(searcher)) {
                open3.setAccessible(true);
            }
            Object result = open3.invoke(searcher, query, highlightTerm, sourceName);
            return Boolean.TRUE.equals(result);
        } catch (NoSuchMethodException ignored3) {
            try {
                java.lang.reflect.Method open2 = searcher.getClass().getMethod("openItem", String.class, String.class);
                if (!open2.canAccess(searcher)) {
                    open2.setAccessible(true);
                }
                Object result = open2.invoke(searcher, query, highlightTerm);
                return Boolean.TRUE.equals(result);
            } catch (NoSuchMethodException ignored2) {
                if (!loggedMissingOpenItem) {
                    loggedMissingOpenItem = true;
                    LOGGER.warn("AttachmentSearcher.openItem indisponivel nesta versao base do IPED 4.3.1."); //$NON-NLS-1$
                }
                return false;
            } catch (Exception e) {
                LOGGER.warn("Falha ao invocar AttachmentSearcher.openItem(query, highlight)", e); //$NON-NLS-1$
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("Falha ao invocar AttachmentSearcher.openItem(query, highlight, sourceName)", e); //$NON-NLS-1$
            return false;
        }
    }

    private static final String OPENCV_DLL_NAME = "opencv_java4120.dll"; //$NON-NLS-1$
    static final String OPENCV_DLL_SHA256 = "ACA5D9A9BC8067434D793402269A18AA18A4E033C96D44707686D30FF162ABB9"; //$NON-NLS-1$

    private static Path viewerCodeSourcePath() {
        try {
            java.security.CodeSource cs = HtmlLinkViewer.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            return Path.of(cs.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    static List<Path> openCvCandidatePaths(Path codeSourceLocation) {
        if (codeSourceLocation == null) {
            return Arrays.asList();
        }
        Path normalized = codeSourceLocation.toAbsolutePath().normalize();
        Path baseDir = Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) ? normalized.getParent() : normalized;
        if (baseDir == null) {
            return Arrays.asList();
        }
        String baseName = baseDir.getFileName() == null ? "" : baseDir.getFileName().toString(); //$NON-NLS-1$
        Path ipedRoot = (baseDir.getParent() != null
                && ("lib".equalsIgnoreCase(baseName) || "plugins".equalsIgnoreCase(baseName))) //$NON-NLS-1$ //$NON-NLS-2$
                        ? baseDir.getParent()
                        : baseDir;
        return Arrays.asList(
                ipedRoot.resolve("plugins").resolve(OPENCV_DLL_NAME), //$NON-NLS-1$
                ipedRoot.resolve(OPENCV_DLL_NAME)); //$NON-NLS-1$
    }

    static Path resolveVerifiedOpenCvDll(Path codeSourceLocation) throws IOException {
        List<Path> candidates = openCvCandidatePaths(codeSourceLocation);
        if (candidates.isEmpty()) {
            throw new IOException("Localizacao do plugin indisponivel para carregar " + OPENCV_DLL_NAME); //$NON-NLS-1$
        }
        Path ipedRoot = candidates.get(1).getParent();
        for (Path candidate : candidates) {
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            BasicFileAttributes attrs = Files.readAttributes(candidate, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther()) {
                throw new IOException("opencv_java4120.dll invalida ou link simbolico bloqueado: " + candidate); //$NON-NLS-1$
            }
            CaptureIntegrity.ensureNoSymbolicLinkComponents(ipedRoot, candidate);
            Path realCandidate = candidate.toRealPath();
            Path realRoot = ipedRoot.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw new IOException("opencv_java4120.dll resolve fora da pasta do IPED: " + candidate); //$NON-NLS-1$
            }
            String actualHash = CaptureIntegrity.sha256(realCandidate);
            if (!OPENCV_DLL_SHA256.equalsIgnoreCase(actualHash)) {
                throw new IOException("Falha na verificacao SHA-256 de " + OPENCV_DLL_NAME + ": " + candidate); //$NON-NLS-1$
            }
            return realCandidate;
        }
        throw new IOException("opencv_java4120.dll nao encontrada na pasta do IPED ou plugins"); //$NON-NLS-1$
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

    private volatile boolean disposed;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void dispose() {
        disposed = true;
        closeSensitiveImageMatcher();
        uiScriptExecutor.shutdownNow();
        super.dispose();
    }

    @Override
    protected int getMaxHtmlSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        if (disposed) {
            return;
        }
        captureSourceItem = content instanceof IItem ? (IItem) content : null;
        if (content != null) {
            attachSearcher.updateSelectionCache();
        }
        super.loadFile(content, terms);
    }

    public class AttachmentHandler extends FileHandler {

        /**
         * Usada pelo JavaScript do chat: {@code window.app.annotationAction(id)}.
         * Precisa ser publica e simples para a ponte do JavaFX conseguir invocar.
         */
        public void annotationAction(String messageId) {
            HtmlLinkViewer.this.annotationAction(messageId);
        }

        private void showAttachNotFound(final String luceneQuery) {
            LOGGER.info("Attachment not found by query " + luceneQuery); //$NON-NLS-1$
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, ChatCapturePanel
                            .plainMessageComponent(Messages.getString("HtmlLinkViewer.AttachNotFound"))); //$NON-NLS-1$
                }
            });
        }

        public void open(final String luceneQuery) {
            if (luceneQuery == null || luceneQuery.isBlank() || attachSearcher == null) {
                showAttachNotFound(luceneQuery);
                return;
            }
            IItem item = attachSearcher.getItem(luceneQuery);
            if (item == null) {
                showAttachNotFound(luceneQuery);
                return;
            }
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
                showAttachNotFound(luceneQuery);

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
        if (hash == null || !mediaHashesInView.contains(hash))
            return;
        String script = "var x = document.getElementsByName(" + js(hash) + ");"
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
