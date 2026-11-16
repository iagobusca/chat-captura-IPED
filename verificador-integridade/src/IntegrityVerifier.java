import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ImageIcon;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Independent, read-only checker for Chat Capture folders (Java 8+). */
public final class IntegrityVerifier {
    private static final String ALG = "SHA-256";
    private static final String VERIFIER_VERSION = "1.4.5";
    private static final long MAX_SCAN_ENTRIES = 100_000L;
    private static final Pattern CAPTURE_TIMESTAMP_SUFFIX = Pattern.compile("\\A.*-(\\d{8}-\\d{6})\\z", Pattern.DOTALL);
    private static final class HtmlElementToken {
        final String tag;
        final boolean closing;
        final Map<String, String> attrs;
        final int start;
        final int end;
        final String body;
        HtmlElementToken(String tag, boolean closing, Map<String, String> attrs, int start, int end, String body) {
            this.tag = tag;
            this.closing = closing;
            this.attrs = attrs;
            this.start = start;
            this.end = end;
            this.body = body;
        }
    }
    private static final Map<String, Set<String>> ALLOWED_HTML_ATTRIBUTES = buildAllowedHtmlAttributes();

    private static Map<String, Set<String>> buildAllowedHtmlAttributes() {
        Map<String, Set<String>> map = new HashMap<>();
        Set<String> passiveAttrs = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class", "title", "dir", "lang")));
        Set<String> cellAttrs = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class", "title", "dir", "lang", "colspan", "rowspan", "width", "height")));
        map.put("html", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("lang"))));
        map.put("head", Collections.emptySet());
        map.put("title", Collections.emptySet());
        map.put("body", Collections.emptySet());
        map.put("header", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class"))));
        map.put("nav", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class"))));
        map.put("section", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "class", "role", "aria-modal", "aria-labelledby"))));
        map.put("aside", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class"))));
        map.put("h1", passiveAttrs);
        map.put("h2", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "class", "title", "dir", "lang"))));
        map.put("h3", passiveAttrs);
        map.put("h4", passiveAttrs);
        map.put("h5", passiveAttrs);
        map.put("h6", passiveAttrs);
        map.put("div", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "class", "title", "dir", "lang", "data-capture-message-key", "data-iped-capture-date", "data-time", "data-src1", "data-src2"))));
        map.put("span", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class", "title", "dir", "lang", "data-iped-capture-date", "data-time"))));
        map.put("p", passiveAttrs);
        map.put("strong", passiveAttrs);
        map.put("small", passiveAttrs);
        map.put("b", passiveAttrs);
        map.put("i", passiveAttrs);
        map.put("em", passiveAttrs);
        map.put("u", passiveAttrs);
        map.put("br", Collections.emptySet());
        map.put("hr", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("class", "title"))));
        map.put("ul", Collections.emptySet());
        map.put("li", Collections.emptySet());
        map.put("table", passiveAttrs);
        map.put("tbody", passiveAttrs);
        map.put("tr", passiveAttrs);
        map.put("td", cellAttrs);
        map.put("th", cellAttrs);
        map.put("a", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("href", "class", "title", "download", "target", "rel", "aria-label", "data-capture-censored"))));
        map.put("img", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("src", "class", "alt", "title", "dir", "lang", "width", "height", "data-capture-censored"))));
        map.put("audio", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("src", "class", "controls", "preload"))));
        map.put("video", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("src", "class", "controls", "preload", "width", "height"))));
        map.put("iframe", Collections.unmodifiableSet(new HashSet<>(Arrays.asList("src", "class", "title", "sandbox"))));
        return Collections.unmodifiableMap(map);
    }

    private static final Set<String> PASSIVE_DATA_ATTR_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "div", "span", "p", "b", "strong", "i", "em", "u", "small", "h1", "h2", "h3", "h4", "h5", "h6",
            "table", "tbody", "tr", "td", "th")));

    private static boolean isAllowedSafeDataAttr(String tag, String attr) {
        if (!PASSIVE_DATA_ATTR_TAGS.contains(tag) || attr == null || !attr.matches("data-[a-z0-9_-]{1,64}")) {
            return false;
        }
        return !"data-capture-link-token".equals(attr)
                && !"data-capture-src-token".equals(attr)
                && !"data-capture-censored".equals(attr)
                && (!"data-capture-message-key".equals(attr) || "div".equals(tag));
    }
    private static final String CAPTURE_MEDIA_STOP_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();";
    private static final String LEGACY_CAPTURE_MEDIA_STOP_SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}window.addEventListener('hashchange',function(){if(location.hash.indexOf('#preview-')!==0)stop();});document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-close'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-close'))stop();});})();";
    private static final String CAPTURE_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-Pq7vIvwsQpte7OdNyOYYE9cKIEPmIFR7E/G7kB1JaJI='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    private static final String LEGACY_CAPTURE_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-sKvM90tshBmGWhuFsz3phQvtsDNpZr1gyyGXQe4mJDE='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CAPTURE_FILES = 25010;
    private static final int MAX_RELATIVE_PATH_CHARS = 4096;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_CAPTURE_CANDIDATES = 10000;
    private static final long MAX_SELECTED_ITEMS = 25000L;
    private static final long MAX_HTML_CAPTURE_BYTES = 64L * 1024L * 1024L;
    private static Image verifierIcon() {
        try {
            java.net.URL u = IntegrityVerifier.class.getResource("/imagem doc/verificador-icon.png");
            if (u != null) return Toolkit.getDefaultToolkit().getImage(u);
            u = IntegrityVerifier.class.getResource("/verificador-icon.png");
            if (u != null) return Toolkit.getDefaultToolkit().getImage(u);
        } catch (Exception ignored) { }
        return null;
    }
    private static void runOnEdt(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeAndWait(task);
        }
    }
    private static void applyWindowDefaults(Window window) {
        Image ic = verifierIcon();
        if (ic != null) window.setIconImage(ic);
        window.setAlwaysOnTop(true);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    window.toFront();
                    window.requestFocus();
                });
            }
        });
    }
    private static JFrame newWindow(String title, boolean resizable) {
        JFrame frame = new JFrame(title);
        applyWindowDefaults(frame);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setResizable(resizable);
        return frame;
    }
    private static void showTopMessage(Component parent, Object message, String title, int messageType) {
        final Object safeMessage = message instanceof String ? plainMessageComponent((String) message) : message;
        Runnable display = () -> {
            JOptionPane pane = new JOptionPane(safeMessage, messageType);
            Window owner = parent instanceof Window ? (Window) parent
                    : (parent == null ? null : SwingUtilities.getWindowAncestor(parent));
            JDialog dialog = pane.createDialog(owner, title);
            applyWindowDefaults(dialog);
            dialog.setModal(true);
            dialog.setVisible(true);
            dialog.dispose();
        };
        try {
            runOnEdt(display);
        } catch (Exception e) {
            System.err.println(title + ": " + message);
        }
    }
    private static final class Item {
        String path, expected; long size, bytes; String actual, state;
    }
    static final class Capture {
        Path dir; String name, hash, historyHash = "", contentRootHash = "", integrityFileHash = "", historyStatus = "registro ausente/legado", error = ""; JSONArray historyEvents = new JSONArray(); List<Item> items = new ArrayList<>(); Set<String> allowedFiles = new HashSet<>(); long bytes, metadataBytes; int fileCount;
    }
    private IntegrityVerifier() {}

    static JLabel plainLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    static JCheckBox plainCheckBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.putClientProperty("html.disable", Boolean.TRUE);
        return box;
    }

    static JComponent plainMessageComponent(String text) {
        String safe = text == null ? "" : text;
        if (!safe.contains("\n") && safe.length() <= 140) {
            return plainLabel(safe);
        }
        JTextArea area = new JTextArea(safe);
        area.putClientProperty("html.disable", Boolean.TRUE);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setColumns(52);
        int lines = 1;
        for (int i = 0; i < safe.length(); i++) {
            if (safe.charAt(i) == '\n') lines++;
        }
        area.setRows(Math.min(18, Math.max(2, lines + (safe.length() / 64))));
        if (lines <= 8 && safe.length() <= 500) {
            return area;
        }
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(560, 260));
        return scroll;
    }

    private static boolean isConsistentLocalHistoryStatus(String historyStatus) {
        return "consistente com registro local".equals(historyStatus)
                || "consistente com registro local (manifesto de mensagens)".equals(historyStatus)
                || "consistente com registro local; consistente com registro local (manifesto de mensagens)".equals(historyStatus);
    }

    static boolean isHealthyVerifierItemState(String state) {
        if (state == null) {
            return false;
        }
        switch (state) {
            case "integro":
            case "anexo íntegro":
            case "mensagem estruturada":
            case "anotações íntegras":
            case "reconciliação por mensagem íntegra":
            case "registro de migração íntegro":
            case "anotações legadas preservadas":
            case "inalterada desde a captura anterior":
            case "alterada desde a captura anterior":
            case "adicionada desde a captura anterior":
                return true;
            default:
                return false;
        }
    }

    private static final class CliOptions {
        boolean verifyOnly;
        boolean exportHistory;
        boolean showVersion;
        boolean showHelp;
        boolean selfTest;
        boolean tooManyArgs;
        String unknownFlag;
        String positionalFolder;
    }

    private static CliOptions parseCliOptions(String[] args) {
        CliOptions options = new CliOptions();
        if (args == null) {
            return options;
        }
        for (String rawArg : args) {
            if (rawArg == null) {
                continue;
            }
            String arg = rawArg.trim();
            if ("--verify-only".equalsIgnoreCase(arg)) {
                options.verifyOnly = true;
            } else if ("--export-history".equalsIgnoreCase(arg)) {
                options.exportHistory = true;
            } else if ("--version".equalsIgnoreCase(arg) || "-v".equalsIgnoreCase(arg)) {
                options.showVersion = true;
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)
                    || "-?".equals(arg) || "/?".equals(arg)) {
                options.showHelp = true;
            } else if ("--self-test".equalsIgnoreCase(arg)
                    || arg.toLowerCase(Locale.ROOT).startsWith("--self-test-hold-ms=")) {
                options.selfTest = true;
            } else if (arg.startsWith("-")) {
                options.unknownFlag = arg;
                break;
            } else if (options.positionalFolder == null) {
                options.positionalFolder = rawArg;
            } else {
                options.tooManyArgs = true;
            }
        }
        return options;
    }

    private static String cliUsage() {
        return "Uso: integridade-verifier [--verify-only [pasta] | --export-history [pasta] | --version | --self-test | --help]\n"
                + "  --verify-only [pasta]     Verifica capturas em modo CLI (sem interface grafica)\n"
                + "  --export-history [pasta]  Gera integridade/historico-capturas.txt em modo CLI\n"
                + "  --version, -v             Exibe a versao do verificador\n"
                + "  --self-test               Executa autodiagnostico rapido de runtime e limites\n"
                + "  --help, -h                Exibe esta ajuda";
    }

    private static void runSelfTest() throws Exception {
        MessageDigest.getInstance(ALG);
        if (MAX_METADATA_BYTES <= 0 || MAX_HTML_CAPTURE_BYTES < MAX_METADATA_BYTES || MAX_JSON_DEPTH < 16) {
            throw new IllegalStateException("Limites internos inconsistentes no verificador.");
        }
        JSONObject parsed = parseBoundedJson("{\"ok\":true,\"nested\":{\"v\":1}}", Paths.get("self-test.json"));
        if (!parsed.optBoolean("ok", false)) {
            throw new IllegalStateException("Falha ao interpretar JSON no autodiagnostico.");
        }
        StringBuilder deep = new StringBuilder("{\"d\":");
        for (int i = 0; i <= MAX_JSON_DEPTH + 1; i++) {
            deep.append('[');
        }
        for (int i = 0; i <= MAX_JSON_DEPTH + 1; i++) {
            deep.append(']');
        }
        deep.append('}');
        boolean depthRejected = false;
        try {
            parseBoundedJson(deep.toString(), Paths.get("self-test-depth.json"));
        } catch (IOException expected) {
            depthRejected = true;
        }
        if (!depthRejected) {
            throw new IllegalStateException("Falha ao rejeitar JSON com profundidade excessiva.");
        }
        System.out.println("SELF_TEST_OK " + VERIFIER_VERSION);
    }

    public static void main(String[] args) throws Exception {
        CliOptions cli = parseCliOptions(args);
        if (cli.unknownFlag != null) {
            System.err.println("Opção não reconhecida: " + cli.unknownFlag);
            System.err.println(cliUsage());
            throw new IOException("Opção de linha de comando inválida: " + cli.unknownFlag);
        }
        if (cli.tooManyArgs) {
            System.err.println("Argumentos posicionais em excesso. Informe apenas uma pasta de capturas.");
            System.err.println(cliUsage());
            throw new IOException("Argumentos de linha de comando inválidos.");
        }
        if (cli.verifyOnly && cli.exportHistory) {
            System.err.println("As opções --verify-only e --export-history são mutuamente exclusivas.");
            System.err.println(cliUsage());
            throw new IOException("Opções de linha de comando incompatíveis.");
        }
        if (cli.showHelp) {
            System.out.println(cliUsage());
            return;
        }
        if (cli.showVersion) {
            System.out.println("Verificador de Integridade " + VERIFIER_VERSION);
            return;
        }
        if (cli.selfTest) {
            runSelfTest();
            return;
        }
        boolean verifyOnly = cli.verifyOnly;
        boolean exportHistory = cli.exportHistory;
        boolean cliMode = verifyOnly || exportHistory;
        Path root = cli.positionalFolder != null ? Paths.get(cli.positionalFolder) : applicationFolder();
        root = root.toAbsolutePath().normalize();
        if (cliMode) {
            try {
                validateRootDirectory(root);
            } catch (IOException invalidRoot) {
                System.err.println(invalidRoot.getMessage());
                throw invalidRoot;
            }
            List<Capture> captures = discover(root);
            if (captures.isEmpty()) {
                System.err.println("Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta: " + root);
                throw new IOException("Nenhuma captura encontrada na pasta: " + root);
            }
            if (verifyOnly) {
                boolean ok=true;
                for(Capture c:captures){
                    try{
                        candidateMetadataBytes(c);
                        verify(c,MAX_CAPTURE_FILES);
                    }catch(Exception e){
                        c.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                        ok=false;
                    }
                    boolean hasBadItem=false;
                    for(Item item:c.items){
                        if("extra".equals(item.state)
                                ||"alterado".equals(item.state)
                                ||"ausente/invalid_path".equals(item.state)
                                ||"não encontrada na fonte atual".equals(item.state)){
                            hasBadItem=true;
                            break;
                        }
                    }
                    System.out.println(c.name+" | "+(c.error.isEmpty()?c.historyStatus:"ERRO: "+c.error)+" | content="+c.hash+" | history="+c.historyHash);
                    ok &= c.error.isEmpty() && !hasBadItem && isConsistentLocalHistoryStatus(c.historyStatus);
                    if(c.error.contains("interrompida")||c.error.contains("limite global"))break;
                }
                if(!ok) throw new IOException("Uma ou mais capturas divergiram do registro local.");
                return;
            }
            verifyAllBounded(captures);enforceReportBudget(captures);String collectionHash=setHash(captures);String report=historyReport(captures,collectionHash);String publicReport=publicHistoryReport(captures,collectionHash);Path out=root.resolve("integridade");createDirectoriesSafe(root,out);atomic(root,out.resolve("historico-capturas.txt"),report);System.out.println(out.resolve("historico-capturas.txt"));System.out.println("SHA-256_PUBLICO="+hashText(normalizePublicRecord(report)));System.out.println("SHA-256_HISTORICO_PUBLICO="+hashText(normalizePublicRecord(publicReport)));return;
        }
        try {
            runGuiFlow(root);
        } catch (Throwable t) {
            String msg = t.getMessage() == null || t.getMessage().trim().isEmpty()
                    ? t.getClass().getSimpleName()
                    : t.getMessage();
            showTopMessage(null, msg, "Erro no verificador de integridade", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static final class VerificationBundle {
        final List<Capture> selected;
        final String setHash, history, publicHistory, historyFullHash, historyPublicHash;
        final Path historyFile;
        VerificationBundle(List<Capture> selected, String setHash, String history, String publicHistory,
                String historyFullHash, String historyPublicHash, Path historyFile) {
            this.selected = selected;
            this.setHash = setHash;
            this.history = history;
            this.publicHistory = publicHistory;
            this.historyFullHash = historyFullHash;
            this.historyPublicHash = historyPublicHash;
            this.historyFile = historyFile;
        }
    }

    private static void runGuiFlow(Path root) throws Exception {
        validateRootDirectory(root);
        int mode = chooseMode();
        if (mode < 0) {
            System.exit(0);
            return;
        }
        VerificationBundle bundle;
        if (mode == 0) {
            List<Capture> captures = discoverInteractive(root);
            if (captures == null) {
                System.exit(0);
                return;
            }
            if (captures.isEmpty()) {
                showTopMessage(null, "Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta:\n" + root,
                        "Integridade", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
            List<Capture> selected = chooseCaptures(captures);
            if (selected.isEmpty()) {
                System.exit(0);
                return;
            }
            bundle = runVerificationWithProgress(root, selected,
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Verificando capturas", false);
        } else {
            bundle = runVerificationWithProgress(root, null,
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Integridade Público", true);
            if (bundle == null) {
                showTopMessage(null, "Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta:\n" + root,
                        "Integridade", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
        }
        if (bundle == null) {
            System.exit(0);
            return;
        }
        if (mode == 1) {
            comparePublicHistory(bundle.history, bundle.historyFullHash, bundle.publicHistory, bundle.historyPublicHash);
        }
        showSummaryDialog(root, bundle.selected, bundle.history, bundle.publicHistory, bundle.historyPublicHash, bundle.historyFile, mode);
        System.exit(0);
    }

    private static VerificationBundle buildVerificationBundle(Path root, List<Capture> selected,
            Consumer<String> statusUpdate, IntConsumer stepUpdate) throws Exception {
        verifyAllBounded(selected, statusUpdate, stepUpdate);
        if (statusUpdate != null) statusUpdate.accept("Gerando relatórios e hashes...");
        enforceReportBudget(selected);
        String setHash = setHash(selected);
        String generated = OffsetDateTime.now().toString();
        String history = historyReport(selected, setHash);
        String publicHistory = publicHistoryReport(selected, setHash);
        String historyFullHash = hashText(normalizePublicRecord(history));
        String historyPublicHash = hashText(normalizePublicRecord(publicHistory));
        writeReports(root, selected, setHash, generated, history, publicHistory, historyFullHash, historyPublicHash);
        if (stepUpdate != null) stepUpdate.accept(selected.size() + 1);
        Path historyFile = root.resolve("integridade").resolve("historico-capturas.txt");
        return new VerificationBundle(selected, setHash, history, publicHistory, historyFullHash, historyPublicHash, historyFile);
    }

    private static String truncateStatus(String name) {
        if (name == null) return "";
        return name.length() > 48 ? name.substring(0, 45) + "..." : name;
    }

    private static VerificationBundle runVerificationWithProgress(Path root, List<Capture> preselected,
            String title, boolean autoDiscoverAll) throws Exception {
        final SwingWorker<VerificationBundle, String>[] workerRef = new SwingWorker[1];
        final int initialMax = preselected == null ? 1 : Math.max(1, preselected.size() + 1);
        runOnEdt(() -> {
            JDialog dialog = new JDialog((java.awt.Frame) null, title, true);
            applyWindowDefaults(dialog);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JLabel status = plainLabel(autoDiscoverAll
                    ? "Selecionando todas as capturas na pasta..."
                    : "Iniciando verificação de " + preselected.size() + " captura(s)...");
            JProgressBar bar = new JProgressBar(0, initialMax);
            bar.setIndeterminate(autoDiscoverAll);
            bar.setStringPainted(!autoDiscoverAll);
            JButton cancel = new JButton("Cancelar");
            JPanel panel = new JPanel(new java.awt.BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            panel.setPreferredSize(new Dimension(480, 125));
            panel.add(status, java.awt.BorderLayout.NORTH);
            panel.add(bar, java.awt.BorderLayout.CENTER);
            JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            south.add(cancel);
            panel.add(south, java.awt.BorderLayout.SOUTH);
            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            SwingWorker<VerificationBundle, String> worker = new SwingWorker<VerificationBundle, String>() {
                @Override
                protected VerificationBundle doInBackground() throws Exception {
                    List<Capture> target = preselected;
                    if (autoDiscoverAll) {
                        publish("PROG|I|Selecionando todas as capturas na pasta...");
                        target = discover(root, n -> publish("PROG|I|Procurando capturas... " + n + " encontradas"));
                        if (target.isEmpty()) {
                            return null;
                        }
                        publish("MAX|" + (target.size() + 1) + "|Selecionadas " + target.size() + " captura(s). Verificando...");
                    }
                    return buildVerificationBundle(
                            root,
                            target,
                            msg -> publish("TXT|" + msg),
                            step -> publish("VAL|" + step));
                }
                @Override
                protected void process(List<String> chunks) {
                    for (String c : chunks) {
                        if (c.startsWith("MAX|")) {
                            String[] parts = c.split("\\|", 3);
                            int max = Integer.parseInt(parts[1]);
                            bar.setIndeterminate(false);
                            bar.setMaximum(max);
                            bar.setValue(0);
                            bar.setStringPainted(true);
                            if (parts.length > 2) status.setText(parts[2]);
                        } else if (c.startsWith("PROG|I|")) {
                            bar.setIndeterminate(true);
                            bar.setStringPainted(false);
                            status.setText(c.substring(7));
                        } else if (c.startsWith("VAL|")) {
                            int v = Integer.parseInt(c.substring(4));
                            bar.setValue(Math.min(bar.getMaximum(), v));
                        } else if (c.startsWith("TXT|")) {
                            status.setText(c.substring(4));
                        }
                    }
                }
                @Override
                protected void done() {
                    dialog.dispose();
                }
            };
            cancel.addActionListener(e -> { worker.cancel(true); dialog.dispose(); });
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { worker.cancel(true); dialog.dispose(); }
            });
            workerRef[0] = worker;
            worker.execute();
            dialog.setVisible(true);
        });
        try {
            return workerRef[0].get();
        } catch (CancellationException e) {
            System.exit(0);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null && cause.getMessage() != null
                    ? cause.getMessage()
                    : "Não foi possível verificar o conjunto selecionado.";
            showTopMessage(null, msg, "Limite ou erro de verificação", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        }
    }

    private static List<Capture> discoverInteractive(Path root)throws Exception{
        final SwingWorker<List<Capture>,Integer>[] workerRef=new SwingWorker[1];
        runOnEdt(()->{
            JDialog dialog=new JDialog((java.awt.Frame)null,"Verificador de Integridade — procurando capturas",true);
            applyWindowDefaults(dialog);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JLabel status=plainLabel("Procurando… 0 capturas encontradas");JButton cancel=new JButton("Cancelar busca");
            JPanel panel=new JPanel(new java.awt.BorderLayout(10,10));panel.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));panel.setPreferredSize(new Dimension(420,105));panel.add(status,java.awt.BorderLayout.CENTER);panel.add(cancel,java.awt.BorderLayout.SOUTH);dialog.setContentPane(panel);dialog.pack();dialog.setLocationRelativeTo(null);
            SwingWorker<List<Capture>,Integer> worker=new SwingWorker<List<Capture>,Integer>(){
                protected List<Capture> doInBackground()throws Exception{return discover(root,n->publish(n));}
                protected void process(List<Integer> values){if(!values.isEmpty())status.setText("Procurando… "+values.get(values.size()-1)+" capturas encontradas");}
                protected void done(){if(!isCancelled())status.setText("Busca concluída");dialog.dispose();}
            };
            cancel.addActionListener(e->{worker.cancel(true);dialog.dispose();});
            dialog.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){worker.cancel(true);dialog.dispose();}});
            workerRef[0]=worker;worker.execute();dialog.setVisible(true);
        });
        try{return workerRef[0].get();}catch(CancellationException e){return null;}catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("Falha durante a busca de capturas.",cause);}
    }
    private static long candidateMetadataBytes(Capture c) throws IOException {
        long total=0;for(String name:new String[]{"integridade.json","manifest.json"}){Path p=c.dir.resolve(name);if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){long size=safeRegularFileSize(p);if(size>MAX_METADATA_BYTES)throw new IOException("Arquivo de metadados excede o limite: "+p);total+=size;}}
        return total;
    }
    private static void verifyAllBounded(List<Capture> captures) throws Exception { verifyAllBounded(captures, null, null); }
    private static void verifyAllBounded(List<Capture> captures, Consumer<String> statusUpdate, IntConsumer stepUpdate) throws Exception {
        int total=captures.size();
        for(int idx=0;idx<total;idx++){
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Verificação cancelada.");
            Capture c=captures.get(idx);
            if(statusUpdate!=null)statusUpdate.accept("Verificando ("+(idx+1)+"/"+total+"): "+truncateStatus(c.name));
            candidateMetadataBytes(c);
            try{verify(c,MAX_CAPTURE_FILES);}catch(Exception e){throw new IOException("Falha controlada ao verificar a captura '"+c.name+"': "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),e);}
            if(stepUpdate!=null)stepUpdate.accept(idx+1);
        }
    }
    private static void enforceReportBudget(List<Capture> captures) throws IOException {
        if(captures==null)throw new IOException("Lista de capturas nula.");
    }
    private static void showSummaryDialog(Path root,List<Capture> selected,String history,String publicHistory,String historyPublicHash,Path historyFile,int mode) throws Exception {
        final CountDownLatch latch=new CountDownLatch(1);
        final JFrame[] frameRef=new JFrame[1];
        runOnEdt(()->{
            JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
            panel.add(plainLabel(mode==0?"MODO: INTEGRIDADE LOCAL — arquivos comparados com o registro local.":"MODO: INTEGRIDADE PÚBLICO — referência externa comparada."));
            panel.add(plainLabel("Capturas verificadas: "+selected.size()));panel.add(Box.createVerticalStrut(10));
            panel.add(hashValueRow("SHA-256 do histórico completo público",historyPublicHash,"Hash SHA-256 do histórico completo público copiado."));
            panel.add(Box.createVerticalStrut(8));
            JPanel gridActions=new JPanel(new java.awt.GridLayout(2,2,6,6));
            JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Resultado",true);
            frameRef[0]=frame;
            JButton copyHistory=new JButton("Copiar histórico completo");copyHistory.addActionListener(e->copyToClipboard(frame,history,"Histórico completo copiado."));
            JButton copyPublicHistory=new JButton("Copiar histórico completo público");copyPublicHistory.addActionListener(e->copyToClipboard(frame,publicHistory,"Histórico completo público copiado."));
            JButton openHistory=new JButton("Abrir histórico");openHistory.addActionListener(e->openFile(frame,historyFile));
            JButton openHtml=new JButton("Abrir relatório HTML");openHtml.addActionListener(e->openInBrowser(frame,root.resolve("integridade.html")));
            JButton close=new JButton("Fechar");close.addActionListener(e->latch.countDown());
            gridActions.add(copyHistory);gridActions.add(copyPublicHistory);gridActions.add(openHistory);gridActions.add(openHtml);
            JPanel actions=new JPanel(new java.awt.BorderLayout(0,6));
            actions.add(gridActions,java.awt.BorderLayout.CENTER);
            actions.add(close,java.awt.BorderLayout.SOUTH);
            panel.add(actions);
            frame.setContentPane(panel);
            frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){latch.countDown();}});
            frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);
        });
        latch.await();
        runOnEdt(()->{if(frameRef[0]!=null)frameRef[0].dispose();});
    }
    private static JPanel hashValueRow(String label,String value,String copyMessage){
        JPanel block=new JPanel();block.setLayout(new BoxLayout(block,BoxLayout.Y_AXIS));block.add(plainLabel(label+":"));
        JPanel row=new JPanel(new java.awt.BorderLayout(8,0));JButton copy=new JButton("Copiar");copy.addActionListener(e->copyToClipboard(SwingUtilities.getWindowAncestor(row),value,copyMessage));
        JTextArea text=new JTextArea(value,2,38);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);text.setFocusable(true);
        row.add(copy,java.awt.BorderLayout.WEST);row.add(text,java.awt.BorderLayout.CENTER);block.add(row);return block;
    }
    private static int chooseMode() throws Exception {
        final int[] selected={-1};
        final CountDownLatch latch=new CountDownLatch(1);
        final JFrame[] frameRef=new JFrame[1];
        runOnEdt(()->{
            JButton local=new JButton("Integridade Local");JButton external=new JButton("Integridade Público");
            JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(plainLabel("Escolha a referência para verificar:"));panel.add(Box.createVerticalStrut(10));panel.add(plainLabel("Local: compara os arquivos com o integridade.json da captura."));panel.add(plainLabel("Público: verifica todas as capturas e compara com hash público ou histórico."));panel.add(Box.createVerticalStrut(12));JPanel buttons=new JPanel();buttons.add(local);buttons.add(external);panel.add(buttons);
            JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" ("+System.getProperty("os.name")+")",false);
            frameRef[0]=frame;
            local.addActionListener(e->{selected[0]=0;frame.dispose();latch.countDown();});
            external.addActionListener(e->{selected[0]=1;frame.dispose();latch.countDown();});
            frame.setContentPane(panel);
            frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){System.exit(0);}});
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        latch.await();
        runOnEdt(()->{if(frameRef[0]!=null)frameRef[0].dispose();});
        return selected[0];
    }
    private static Path applicationFolder() throws Exception {
        Path location=Paths.get(IntegrityVerifier.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        Path start=Files.isRegularFile(location,LinkOption.NOFOLLOW_LINKS)?location.getParent():location;
        for(Path p=start;p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("index.html"),LinkOption.NOFOLLOW_LINKS))return p;
        return start;
    }
    private static void openInBrowser(Component owner,Path html) {
        try {
            if (html == null || isLinkOrReparse(html) || !Files.isRegularFile(html, LinkOption.NOFOLLOW_LINKS)) throw new IOException("O relatório HTML não foi encontrado ou é inválido: " + html);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException("Este sistema não disponibilizou a abertura do navegador padrão.");
            Desktop.getDesktop().browse(html.toUri());
        } catch (Exception e) {
            showTopMessage(owner, "Não foi possível abrir o navegador padrão. Abra este arquivo manualmente:\n" + html + "\n\nDetalhe: " + e.getMessage(), "Abrir relatório", JOptionPane.ERROR_MESSAGE);
        }
    }
    /** Validates that a local report/history file is a regular file with no symlink/reparse component before opening. */
    private static void validateOpenableRegularFile(Path file) throws IOException {
        if (file == null || isLinkOrReparse(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("O arquivo não foi encontrado ou é inválido: " + file);
        }
        Path parent = file.getParent();
        if (parent != null && isLinkOrReparse(parent)) {
            throw new IOException("Link simbólico ou junção não permitido no caminho do arquivo: " + parent);
        }
    }
    private static void openFile(Component owner,Path file) {
        try {
            validateOpenableRegularFile(file);
            if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.OPEN))throw new IOException("A abertura de arquivos não está disponível.");
            Desktop.getDesktop().open(file.toFile());
        } catch(Exception e){showTopMessage(owner,"Não foi possível abrir o arquivo. Ele foi salvo em:\n"+file+"\n\n"+e.getMessage(),"Abrir histórico",JOptionPane.ERROR_MESSAGE);}
    }
    static String classifyPublicHashMatch(String supplied, String localFullHash, String localPublicHash) {
        String trimmed = supplied == null ? "" : supplied.trim();
        if (trimmed.isEmpty()) return "EMPTY";
        if (!trimmed.matches("(?i)[0-9a-f]{64}")) return "INVALID";
        if (trimmed.equalsIgnoreCase(localFullHash)) return "MATCH_FULL";
        if (trimmed.equalsIgnoreCase(localPublicHash)) return "MATCH_PUBLIC";
        return "DIVERGE";
    }
    static String classifyPublicHistoryMatch(String supplied, String localFullHistory, String localPublicHistory) {
        if (supplied == null || supplied.trim().isEmpty()) return "EMPTY";
        String normalized = normalizePublicRecord(supplied);
        if (normalized.equals(normalizePublicRecord(localFullHistory))) return "MATCH_FULL";
        if (normalized.equals(normalizePublicRecord(localPublicHistory))) return "MATCH_PUBLIC";
        return "DIVERGE";
    }
    private static void comparePublicHistory(String localFullHistory,String localFullHash,String localPublicHistory,String localPublicHash) throws Exception {
        final CountDownLatch latch=new CountDownLatch(1);
        final JFrame[] frameRef=new JFrame[1];
        runOnEdt(()->{
            JTextField publicHash=new JTextField(44);
            JTextArea publicRecord=new JTextArea(12,52);
            publicRecord.setLineWrap(false);
            JLabel result=plainLabel("Informe um dos 3 formatos: SHA-256 público (completo ou anonimizado) ou historico-capturas.txt (normal ou anonimizado).");
            result.setForeground(java.awt.Color.DARK_GRAY);

            JButton compareHash=new JButton("Comparar hash público");
            compareHash.addActionListener(e->{
                String status=classifyPublicHashMatch(publicHash.getText(),localFullHash,localPublicHash);
                boolean ok="MATCH_FULL".equals(status)||"MATCH_PUBLIC".equals(status);
                if("EMPTY".equals(status))result.setText("Informe o hash público.");
                else if("INVALID".equals(status))result.setText("Formato inválido: informe 64 caracteres hexadecimais.");
                else if("MATCH_FULL".equals(status))result.setText("CONFERE: o hash público corresponde ao historico-capturas.txt completo calculado.");
                else if("MATCH_PUBLIC".equals(status))result.setText("CONFERE: o hash público corresponde ao histórico completo público calculado.");
                else result.setText("DIVERGE: o hash público não corresponde ao historico-capturas.txt calculado.");
                result.setForeground("EMPTY".equals(status)?java.awt.Color.DARK_GRAY:ok?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));
            });

            JButton compareRecord=new JButton("Comparar histórico completo");
            compareRecord.addActionListener(e->{
                String status=classifyPublicHistoryMatch(publicRecord.getText(),localFullHistory,localPublicHistory);
                boolean ok="MATCH_FULL".equals(status)||"MATCH_PUBLIC".equals(status);
                if("EMPTY".equals(status))result.setText("Cole o conteúdo de historico-capturas.txt.");
                else if("MATCH_FULL".equals(status))result.setText("CONFERE: o texto informado corresponde integralmente ao historico-capturas.txt completo calculado.");
                else if("MATCH_PUBLIC".equals(status))result.setText("CONFERE: o histórico completo público (anonimizado) corresponde integralmente às capturas calculadas.");
                else result.setText("DIVERGE: o texto informado não corresponde ao historico-capturas.txt (completo ou público) calculado.");
                result.setForeground("EMPTY".equals(status)?java.awt.Color.DARK_GRAY:ok?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));
            });

            final JFrame frame2=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Integridade Público",true);
            frameRef[0]=frame2;
            frame2.addWindowListener(new WindowAdapter(){
                @Override public void windowClosing(WindowEvent e){latch.countDown();}
            });

            JPanel hashInputRow=new JPanel(new java.awt.BorderLayout(8,0));
            hashInputRow.add(publicHash,java.awt.BorderLayout.CENTER);
            hashInputRow.add(compareHash,java.awt.BorderLayout.EAST);

            JPanel sectionHash=new JPanel(new java.awt.BorderLayout(0,6));
            sectionHash.add(plainLabel("1. Hash SHA-256 público (de historico-capturas.txt):"),java.awt.BorderLayout.NORTH);
            sectionHash.add(hashInputRow,java.awt.BorderLayout.CENTER);

            JPanel recordButtonRow=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,0,0));
            recordButtonRow.add(compareRecord);

            JPanel sectionRecord=new JPanel(new java.awt.BorderLayout(0,6));
            JPanel recordHeader=new JPanel();
            recordHeader.setLayout(new BoxLayout(recordHeader,BoxLayout.Y_AXIS));
            recordHeader.add(plainLabel("2. Histórico de capturas completo (historico-capturas.txt):"));
            JLabel formatHint=plainLabel("Aceita formato completo (CAPTURA|pasta) ou público anonimizado (CAPTURA|*-YYYYMMDD-HHMMSS).");
            formatHint.setForeground(java.awt.Color.DARK_GRAY);
            recordHeader.add(formatHint);
            sectionRecord.add(recordHeader,java.awt.BorderLayout.NORTH);
            sectionRecord.add(new JScrollPane(publicRecord),java.awt.BorderLayout.CENTER);
            sectionRecord.add(recordButtonRow,java.awt.BorderLayout.SOUTH);

            JButton closeBtn=new JButton("Continuar para o resumo");
            closeBtn.addActionListener(e->latch.countDown());
            JPanel footerRight=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,0,0));
            footerRight.add(closeBtn);

            JPanel footer=new JPanel(new java.awt.BorderLayout(8,8));
            footer.add(result,java.awt.BorderLayout.CENTER);
            footer.add(footerRight,java.awt.BorderLayout.EAST);

            JPanel content=new JPanel(new java.awt.BorderLayout(10,12));
            content.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
            content.add(sectionHash,java.awt.BorderLayout.NORTH);
            content.add(sectionRecord,java.awt.BorderLayout.CENTER);
            content.add(footer,java.awt.BorderLayout.SOUTH);

            frame2.setContentPane(content);
            frame2.pack();
            frame2.setLocationRelativeTo(null);
            frame2.setVisible(true);
        });
        latch.await();
        runOnEdt(()->{if(frameRef[0]!=null)frameRef[0].dispose();});
    }
    static String comparePublicMessageManifest(Path localPath,String localName,String supplied)throws Exception{
        JSONObject local=parseBoundedJson(readUtf8Limited(localPath),localPath);
        if(!"html-message-v1".equals(local.optString("captureFormat")))throw new IOException("A captura selecionada não usa html-message-v1.");
        if(supplied==null||supplied.trim().isEmpty())throw new IOException("Cole o manifest.json público da captura.");
        if(supplied.getBytes(StandardCharsets.UTF_8).length>MAX_METADATA_BYTES)throw new IOException("O manifesto público excede 16 MiB.");
        JSONObject reference=parseBoundedJson(supplied,Paths.get("manifesto-publico.json"));
        if(reference.optInt("schemaVersion",0)!=1||!"html-message-v1".equals(reference.optString("captureFormat"))||!local.optString("ipedVersion").equals(reference.optString("ipedVersion")))throw new IOException("Formato, esquema ou versão do IPED não corresponde à captura local.");
        if(!local.optString("conversationKey").equals(reference.optString("conversationKey")))throw new IOException("O manifesto público pertence a outra origem ou conversa.");
        JSONArray localRows=local.optJSONArray("messageHashes"),referenceRows=reference.optJSONArray("messageHashes");
        if(localRows==null||referenceRows==null||localRows.length()!=local.optInt("messageCount",-1)||referenceRows.length()!=reference.optInt("messageCount",-1)||referenceRows.length()>MAX_SELECTED_ITEMS)throw new IOException("Resumo público de mensagens ausente ou acima do limite.");
        String reconciliation=reference.optString("reconciliationSha256","");if(!reconciliation.isEmpty()&&!reconciliation.matches("(?i)[0-9a-f]{64}"))throw new IOException("Hash de reconciliação pública inválido.");
        if(!reference.optString("htmlSha256","").matches("(?i)[0-9a-f]{64}")||!reference.optString("sourceFingerprint","").matches("(?i)[0-9a-f]{64}")||!reference.optString("captureRootHash","").matches("(?i)[0-9a-f]{64}")||reference.optJSONObject("sourceMetadata")==null||reference.optJSONObject("anchors")==null)throw new IOException("Campos necessários para validar o manifesto público estão ausentes.");
        PublicMessageIndex localIndex=parsePublicMessageIndex(localRows),referenceIndex=parsePublicMessageIndex(referenceRows);
        String computed=htmlCaptureRootHash(referenceRows,reference.optString("htmlSha256",""),reference.optString("annotationsSha256",""),reconciliation,reference);
        if(!computed.equalsIgnoreCase(reference.optString("captureRootHash","")))throw new IOException("Hash raiz do manifesto público não confere com o resumo por mensagem.");
        int same=0,changed=0,notInReference=0,referenceOnly=0;StringBuilder output=new StringBuilder();
        for(Map.Entry<String,String> entry:localIndex.byId.entrySet()){
            String id=entry.getKey();
            String localHash=entry.getValue();
            String localKey=localIndex.idToKey.get(id);
            String other=referenceIndex.byId.get(id);
            String refKey=referenceIndex.idToKey.get(id);
            String refHashByKey=localKey==null?null:referenceIndex.byKey.get(localKey);
            String localHashByKey=localKey==null?null:localIndex.byKey.get(localKey);
            String state;
            if(other==null){state="ausente_na_referencia";notInReference++;}
            else if(localKey==null||!localKey.equals(refKey)||refHashByKey==null||localHashByKey==null){state="chave_divergente";changed++;}
            else if(other.equalsIgnoreCase(localHash)&&refHashByKey.equalsIgnoreCase(localHashByKey)){state="integra";same++;}
            else{state="hash_divergente";changed++;}
            output.append(id).append('|').append(state).append('\n');
        }
        for(String id:referenceIndex.byId.keySet())if(!localIndex.byId.containsKey(id)){referenceOnly++;output.append(id).append("|somente_na_referencia\n");}
        return "Captura local: "+localName+"\nIntegras: "+same+" | divergentes: "+changed+" | ausentes na referência: "+notInReference+" | somente na referência: "+referenceOnly+"\nA ausência descreve a comparação; não identifica causa nem autoria.\n\n"+output.toString();
    }
    private static final class PublicMessageIndex{
        final Map<String,String> byKey=new TreeMap<>();
        final Map<String,String> byId=new TreeMap<>();
        final Map<String,String> idToKey=new TreeMap<>();
    }
    private static PublicMessageIndex parsePublicMessageIndex(JSONArray rows)throws IOException{
        PublicMessageIndex index=new PublicMessageIndex();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)throw new IOException("Entrada inválida no resumo público.");
            String id=row.optString("domId","");
            String key=row.optString("key","").toLowerCase(Locale.ROOT);
            String hash=row.optString("messageHash","").toLowerCase(Locale.ROOT);
            if(!id.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")
                    ||!key.matches("[0-9a-f]{64}")
                    ||!hash.matches("[0-9a-f]{64}")
                    ||index.byKey.put(key,hash)!=null
                    ||index.byId.put(id,hash)!=null){
                throw new IOException("ID, chave ou hash inválido/duplicado no resumo público.");
            }
            index.idToKey.put(id,key);
        }
        return index;
    }
    private static Map<String,String> publicMessageMap(JSONArray rows)throws IOException{return parsePublicMessageIndex(rows).byKey;}
    private static Map<String,String> publicMessageDomMap(JSONArray rows)throws IOException{return parsePublicMessageIndex(rows).byId;}
    private static String normalizePublicRecord(String s){String n=s.replace("\r\n","\n").replace('\r','\n');while(n.endsWith("\n"))n=n.substring(0,n.length()-1);return n+"\n";}
    private static String publicHashList(List<Capture> captures,String rootHash){StringBuilder out=new StringBuilder("TODAS_AS_CAPTURAS|").append(rootHash).append('\n');List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));for(Capture c:ordered)out.append(recordField(c.name)).append('|').append(c.hash).append('\n');return out.toString();}
    private static void copyToClipboard(java.awt.Component owner,String value,String message){
        Thread worker=new Thread(()->{
            String error=null;
            try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);}
            catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final String failure=error;
            SwingUtilities.invokeLater(()->showTopMessage(owner,failure==null?message:"Não foi possível copiar automaticamente.\n"+failure,failure==null?"Hashes":"Cópia",failure==null?JOptionPane.INFORMATION_MESSAGE:JOptionPane.ERROR_MESSAGE));
        },"integridade-verifier-clipboard");
        worker.setDaemon(true);worker.start();
    }
    static boolean isIgnoredCaptureDirectoryName(String name) {
        if (name == null || name.isEmpty()) return true;
        return name.startsWith("_backup_chat_capture_") || name.startsWith("_backup_integridade_")
                || name.equals("vinculado") || name.equals(".iped-reextract-trash")
                || name.startsWith(".__capture_staging_") || name.contains(".__old_")
                || name.contains(".__reextract_tmp_") || name.contains(".__reextract_failed_")
                || name.contains(".__migration_html_backup");
    }
    static List<Capture> discover(Path root) throws IOException {return discover(root,null);}
    private static List<Capture> discover(Path root,IntConsumer progress) throws IOException {
        validateRootDirectory(root);
        List<Capture> out=new ArrayList<>();
        try(java.util.stream.Stream<Path> s=Files.list(root).limit(MAX_SCAN_ENTRIES + 1L)) {java.util.Iterator<Path> it=s.iterator();long scanned=0;while(it.hasNext()){if(Thread.currentThread().isInterrupted())throw new CancellationException("Busca cancelada");Path p=it.next();scanned++;if(scanned>MAX_SCAN_ENTRIES)throw new IOException("A pasta excede o limite de "+MAX_SCAN_ENTRIES+" entradas escaneadas; a busca foi interrompida.");if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||isLinkOrReparse(p))continue;String n=p.getFileName().toString();if(isIgnoredCaptureDirectoryName(n))continue;if(Files.isRegularFile(p.resolve("manifest.json"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("whatsapp.html"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("relatorio.htm"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("texto.txt"),LinkOption.NOFOLLOW_LINKS)){if(out.size()>=MAX_CAPTURE_CANDIDATES)throw new IOException("A pasta excede o limite de "+MAX_CAPTURE_CANDIDATES+" capturas candidatas; a busca foi interrompida.");Capture c=new Capture();c.dir=p;c.name=n;out.add(c);if(progress!=null&&out.size()%100==0)progress.accept(out.size());}}if(progress!=null)progress.accept(out.size());}
        out.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT))); return out;
    }
    private static List<Capture> chooseCaptures(List<Capture> captures) throws Exception {
        final int pageSize = 100;
        final int[] page = {0};
        final boolean[] allFuture = {false};
        final Set<String> selectedIds = new HashSet<>();
        final List<Capture> out = new ArrayList<>();
        final boolean[] cancelled = {true};
        final Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        runOnEdt(() -> {
            JDialog dialog = new JDialog((java.awt.Frame) null,
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Selecione as capturas", true);
            applyWindowDefaults(dialog);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            JLabel status = plainLabel("");
            JButton bulk = new JButton("Marcar todos");
            JPanel head = new JPanel(new java.awt.BorderLayout());
            head.add(status, java.awt.BorderLayout.CENTER);
            head.add(bulk, java.awt.BorderLayout.EAST);

            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(Math.min(800, screen.width - 80), Math.min(560, screen.height - 160)));

            JButton prev = new JButton("Anterior");
            JButton next = new JButton("Próximo");
            JButton confirm = new JButton("Confirmar");
            JButton cancel = new JButton("Cancelar");
            JPanel foot = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4));
            foot.add(prev);
            foot.add(next);
            foot.add(confirm);
            foot.add(cancel);

            JPanel panel = new JPanel(new java.awt.BorderLayout(8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            panel.add(head, java.awt.BorderLayout.NORTH);
            panel.add(scroll, java.awt.BorderLayout.CENTER);
            panel.add(foot, java.awt.BorderLayout.SOUTH);

            final List<JCheckBox> currentBoxes = new ArrayList<>();
            final Runnable[] renderPage = new Runnable[1];
            renderPage[0] = () -> {
                int total = captures.size();
                int maxPage = Math.max(0, (total - 1) / pageSize);
                page[0] = Math.max(0, Math.min(maxPage, page[0]));
                int start = page[0] * pageSize;
                int end = Math.min(total, start + pageSize);
                list.removeAll();
                currentBoxes.clear();
                for (int i = start; i < end; i++) {
                    Capture c = captures.get(i);
                    String id = c.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
                    JCheckBox box = plainCheckBox(c.name, allFuture[0] || selectedIds.contains(id));
                    box.addActionListener(e -> {
                        boolean checked = box.isSelected();
                        if (allFuture[0]) {
                            for (Capture existing : captures) {
                                String existingId = existing.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
                                if (!existingId.equals(id)) selectedIds.add(existingId);
                            }
                            allFuture[0] = false;
                            bulk.setText("Marcar todos");
                        }
                        if (checked) selectedIds.add(id);
                        else selectedIds.remove(id);
                    });
                    currentBoxes.add(box);
                    list.add(box);
                }
                status.setText("Exibindo " + (start + 1) + "–" + end + " de " + total + " capturas");
                bulk.setText(allFuture[0] ? "Desmarcar todos" : "Marcar todos");
                prev.setEnabled(page[0] > 0);
                next.setEnabled(end < total);
                list.revalidate();
                list.repaint();
                scroll.getVerticalScrollBar().setValue(0);
            };

            bulk.addActionListener(e -> {
                allFuture[0] = !allFuture[0];
                selectedIds.clear();
                for (JCheckBox b : currentBoxes) b.setSelected(allFuture[0]);
                bulk.setText(allFuture[0] ? "Desmarcar todos" : "Marcar todos");
            });
            prev.addActionListener(e -> { page[0] = Math.max(0, page[0] - 1); renderPage[0].run(); });
            next.addActionListener(e -> { page[0] = page[0] + 1; renderPage[0].run(); });
            confirm.addActionListener(e -> { cancelled[0] = false; dialog.dispose(); });
            cancel.addActionListener(e -> { cancelled[0] = true; dialog.dispose(); });
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { cancelled[0] = true; dialog.dispose(); }
            });

            renderPage[0].run();
            dialog.setContentPane(panel);
            dialog.getRootPane().setDefaultButton(confirm);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
        if (cancelled[0]) return out;
        for (Capture c : captures) {
            String id = c.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (allFuture[0] || selectedIds.contains(id)) out.add(c);
        }
        return out;
    }
    private static void verify(Capture c) throws Exception {verify(c,MAX_CAPTURE_FILES);}
    private static void verify(Capture c,int maxItemsAllowed) throws Exception {
        Path rawManifest=c.dir.resolve("manifest.json");
        if(Files.exists(rawManifest,LinkOption.NOFOLLOW_LINKS)&&isLinkOrReparse(rawManifest))throw new IOException("Junção ou link não permitido na captura: "+rawManifest);
        Path formatManifest=resolve(c.dir,"manifest.json");
        JSONObject format=null;
        if(formatManifest!=null&&Files.isRegularFile(formatManifest,LinkOption.NOFOLLOW_LINKS)){
            format=parseBoundedJson(readUtf8Limited(formatManifest),formatManifest);
        }
        Path rawIntegrity=c.dir.resolve("integridade.json");
        if(Files.exists(rawIntegrity,LinkOption.NOFOLLOW_LINKS)&&isLinkOrReparse(rawIntegrity))throw new IOException("Junção ou link não permitido na captura: "+rawIntegrity);
        Path integrityPath=resolve(c.dir,"integridade.json");
        JSONObject integrity=null;
        boolean integrityJsonInvalid=false;
        if(integrityPath!=null&&Files.isRegularFile(integrityPath, LinkOption.NOFOLLOW_LINKS)) {
            c.integrityFileHash=hash(integrityPath);
            try { integrity=parseBoundedJson(readUtf8Limited(integrityPath),integrityPath); }
            catch(InvalidJsonException invalid) { integrityJsonInvalid=true; c.historyStatus="registro inválido"; }
        }
        if(format!=null&&"html-message-v1".equals(format.optString("captureFormat"))){
            verifyMessageCapture(c,format);
            String messageManifestStatus=c.historyStatus;
            if(integrityJsonInvalid){
                if(format.has("migration"))
                    throw new IOException("Uma captura migrada precisa de uma cadeia local de integridade para validar migration.json.");
                c.allowedFiles.add("integridade.json");
                c.historyStatus="registro inválido; "+messageManifestStatus;
            }else if(integrity!=null&&integrity.optInt("schemaVersion",0)>=2){
                c.allowedFiles.add("integridade.json");
                verifyHistory(c,integrity);
                validateMigrationIntegrity(c,format,integrity);
                JSONObject migration=format.optJSONObject("migration");
                if(migration!=null){
                    c.allowedFiles.add("migration.json");
                    if(migration.optBoolean("legacyAnnotationsPreserved",false))
                        c.allowedFiles.add("legacy-annotations.json");
                }
                if("consistente com registro local".equals(c.historyStatus)){
                    Set<String> recordedArtifacts=new HashSet<>();
                    JSONArray recordedArray=integrity.optJSONArray("artifacts");
                    for(int i=0;recordedArray!=null&&i<recordedArray.length();i++){
                        JSONObject row=recordedArray.optJSONObject(i);
                        if(row!=null)recordedArtifacts.add(row.optString("path",""));
                    }
                    for(String expectedPath:c.allowedFiles){
                        if("manifest.json".equals(expectedPath)
                                ||"integridade.json".equals(expectedPath)
                                ||"reextracao-auditoria.jsonl".equals(expectedPath)){
                            continue;
                        }
                        if(!recordedArtifacts.contains(expectedPath)){
                            c.historyStatus="inventário incompleto na captura html-message-v1: "+expectedPath;
                            break;
                        }
                    }
                }
                String localHistoryStatus=c.historyStatus;
                c.historyStatus=localHistoryStatus+"; "+messageManifestStatus;
            }else if(integrity!=null){
                throw new IOException("Versão do registro integridade.json incompatível com captura html-message-v1.");
            }else{
                if(format.has("migration"))
                    throw new IOException("Uma captura migrada precisa de uma cadeia local de integridade para validar migration.json.");
                if(Files.exists(c.dir.resolve("migration.json"),LinkOption.NOFOLLOW_LINKS)
                        ||Files.exists(c.dir.resolve("legacy-annotations.json"),LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Artefato legado de migração sem metadados no manifesto.");
            }
            checkUnexpectedHtmlCaptureFiles(c,maxItemsAllowed);
            return;
        }
        if(integrity!=null && integrity.optInt("schemaVersion",0)>=2) verifyHistory(c,integrity);
        JSONObject inventory=integrity;
        if(inventory==null){
            if(integrityJsonInvalid){
                inventory=format;
                if(inventory==null){ c.hash="";return; }
            }else{
                Path inventoryFile=Files.isRegularFile(c.dir.resolve("integridade.json"),LinkOption.NOFOLLOW_LINKS)?c.dir.resolve("integridade.json"):c.dir.resolve("manifest.json");
                inventory=parseBoundedJson(readUtf8Limited(inventoryFile),inventoryFile);
            }
        }
        JSONArray artifacts=inventory.optJSONArray("artifacts");
        if(artifacts==null){ c.hash="";return; }
        if(artifacts.length()>MAX_CAPTURE_FILES)throw new IOException("Inventário excede o limite de "+MAX_CAPTURE_FILES+" arquivos.");
        if(artifacts.length()>maxItemsAllowed)throw new IOException("O limite global de itens selecionados foi excedido antes de validar o inventário.");
        Map<String,Item> expected=new HashMap<>();Set<String> foldedPaths=new HashSet<>();
        for(int n=0;n<artifacts.length();n++) {
            JSONObject row=artifacts.optJSONObject(n);if(row==null)throw new IOException("Entrada inválida no inventário, índice "+n);
            String path=row.optString("path",null),sha=row.optString("sha256",null);long size=row.optLong("size",-1);
            if(path==null||sha==null||!sha.matches("(?i)[0-9a-f]{64}")||size<0)throw new IOException("Metadados inválidos no inventário, índice "+n);
            if(path.length()>MAX_RELATIVE_PATH_CHARS)throw new IOException("Caminho excessivamente longo no inventário: "+path.length());
            String folded=path.toLowerCase(Locale.ROOT);if(!foldedPaths.add(folded))throw new IOException("Caminho duplicado no inventário: "+path);
            Item i=new Item();i.path=path;i.expected=sha;i.size=size;expected.put(path,i);
        }
        for(Item i:expected.values()){
            Path f=resolve(c.dir,i.path);if(f==null||!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)){i.state="ausente/invalid_path";c.items.add(i);continue;}
            i.actual=hash(f);i.bytes=safeRegularFileSize(f);i.state=i.expected.equalsIgnoreCase(i.actual)&&i.size==i.bytes?"integro":"alterado";c.items.add(i);
        }
        Set<String> seen=expected.keySet();
        // Must match HtmlLinkViewer.calculateFolderHash: all regular files, including
        // manifest.json and integridade.json; canonical path|size|SHA-256, sorted by lowercase path.
        List<Path> allFiles=listCaptureFiles(c.dir);
        if(allFiles.size()>MAX_CAPTURE_FILES)throw new IOException("Captura excede o limite de "+MAX_CAPTURE_FILES+" arquivos: "+c.dir);
        long extraCount=0;for(Path f:allFiles){String rel=c.dir.relativize(f).toString().replace('\\','/');if(!rel.equals("manifest.json")&&!rel.equals("integridade.json")&&!rel.equals("reextracao-auditoria.jsonl")&&!seen.contains(rel))extraCount++;}
        if((long)expected.size()+extraCount>maxItemsAllowed)throw new IOException("O limite global de itens selecionados foi excedido antes de montar os resultados.");
        allFiles.sort(Comparator.comparing(p->c.dir.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT)));
        MessageDigest canonical=MessageDigest.getInstance(ALG);
        for(Path f:allFiles){String rel=c.dir.relativize(f).toString().replace('\\','/');long size=safeRegularFileSize(f);String sha=hash(f);String line=rel+'|'+size+'|'+sha+'\n';canonical.update(line.getBytes(StandardCharsets.UTF_8));c.bytes+=size;c.fileCount++;if(!rel.equals("manifest.json")&&!rel.equals("integridade.json")&&!rel.equals("reextracao-auditoria.jsonl")&&!seen.contains(rel)){Item x=new Item();x.path=rel;x.actual=sha;x.bytes=size;x.state="extra";c.items.add(x);}}
        c.hash=hex(canonical.digest());
        c.items.sort(Comparator.comparing(i->i.path));
        if(extraCount>0){
            if(c.historyStatus!=null&&c.historyStatus.startsWith("registro inválido")){
                c.historyStatus="registro inválido; arquivos extras na captura: "+extraCount;
            }else if(c.historyStatus!=null&&c.historyStatus.startsWith("consistente com registro local")){
                c.historyStatus="arquivos extras na captura: "+extraCount;
            }else if(c.historyStatus==null||c.historyStatus.isEmpty()){
                c.historyStatus="arquivos extras na captura: "+extraCount;
            }else{
                c.historyStatus=c.historyStatus+"; arquivos extras na captura: "+extraCount;
            }
        }
    }

    private static void validateMigrationIntegrity(Capture c,JSONObject manifest,JSONObject integrity)throws Exception{
        JSONObject migration=manifest.optJSONObject("migration");
        Path migrationPath=resolve(c.dir,"migration.json");
        Path annotationsPath=resolve(c.dir,"legacy-annotations.json");
        if(migration==null){
            if(Files.exists(c.dir.resolve("migration.json"),LinkOption.NOFOLLOW_LINKS)
                    ||Files.exists(c.dir.resolve("legacy-annotations.json"),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Artefato legado de migração sem metadados no manifesto.");
            return;
        }
        if(migration.optInt("schemaVersion",0)!=1
                ||!"legacy_screenshot_capture_migrated_to_html_message_v1".equals(migration.optString("action"))
                ||!"legacy-screenshot".equals(migration.optString("sourceFormat"))
                ||!migration.optString("previousManifestSha256","").matches("(?i)[0-9a-f]{64}")
                ||!migration.optString("anchorsRecoveredFrom","").equals("manifest-and-whatsapp-coordinates")
                ||(!migration.optString("previousIntegrityContentRootHash","").isEmpty()
                    &&!migration.optString("previousIntegrityContentRootHash").matches("(?i)[0-9a-f]{64}"))
                ||(!migration.optString("previousIntegrityRecordHash","").isEmpty()
                    &&!migration.optString("previousIntegrityRecordHash").matches("(?i)[0-9a-f]{64}")))
            throw new IOException("Metadados da migração legada são inválidos.");
        String migrationFile=manifest.optString("migrationFile","");
        String migrationSha=manifest.optString("migrationSha256","");
        if(!"migration.json".equals(migrationFile)||!migrationSha.matches("(?i)[0-9a-f]{64}")
                ||migrationPath==null||!Files.isRegularFile(migrationPath,LinkOption.NOFOLLOW_LINKS)
                ||safeRegularFileSize(migrationPath)>MAX_METADATA_BYTES||!hash(migrationPath).equalsIgnoreCase(migrationSha))
            throw new IOException("Arquivo migration.json ausente ou divergente do manifesto.");
        JSONObject migrationFileJson=parseBoundedJson(readUtf8Limited(migrationPath),migrationPath);
        if(migrationFileJson.optInt("schemaVersion",0)!=1||!migration.keySet().equals(migrationFileJson.keySet()))
            throw new IOException("Schema ou campos de migration.json divergem do manifesto.");
        String[] stringFields={"action","sourceFormat","previousIntegrityContentRootHash","previousIntegrityRecordHash",
                "previousManifestSha256","anchorsRecoveredFrom"};
        for(String name:stringFields)if(!migration.optString(name,"").equals(migrationFileJson.optString(name,"")))
            throw new IOException("manifest.json e migration.json divergem no campo "+name+".");
        if(migration.optBoolean("legacyAnnotationsPreserved",false)!=migrationFileJson.optBoolean("legacyAnnotationsPreserved",false))
            throw new IOException("Manifesto e migration.json divergem sobre as anotações preservadas.");

        JSONArray artifacts=integrity.optJSONArray("artifacts");
        JSONObject migrationEntry=findIntegrityArtifact(artifacts,"migration.json");
        if(migrationEntry==null||migrationEntry.optLong("size",-1)!=safeRegularFileSize(migrationPath)
                ||!migrationEntry.optString("sha256","").equalsIgnoreCase(hash(migrationPath)))
            throw new IOException("migration.json não está vinculado ao inventário da cadeia local de integridade.");
        addIntegrityArtifactItem(c,"migration.json",migrationEntry,"registro de migração íntegro");

        boolean annotationsPreserved=migration.optBoolean("legacyAnnotationsPreserved",false);
        boolean annotationsExist=annotationsPath!=null&&Files.isRegularFile(annotationsPath,LinkOption.NOFOLLOW_LINKS);
        if(annotationsPreserved!=annotationsExist)throw new IOException("Arquivo de anotações legadas diverge dos metadados da migração.");
        if(annotationsPreserved){
            if(safeRegularFileSize(annotationsPath)>MAX_METADATA_BYTES)throw new IOException("Anotações legadas excedem o limite de metadados.");
            parseBoundedJson(readUtf8Limited(annotationsPath),annotationsPath);
            JSONObject annotationEntry=findIntegrityArtifact(artifacts,"legacy-annotations.json");
            if(annotationEntry==null||annotationEntry.optLong("size",-1)!=safeRegularFileSize(annotationsPath)
                    ||!annotationEntry.optString("sha256","").equalsIgnoreCase(hash(annotationsPath)))
                throw new IOException("legacy-annotations.json não está vinculado à cadeia local de integridade.");
            addIntegrityArtifactItem(c,"legacy-annotations.json",annotationEntry,"anotações legadas preservadas");
        }else if(findIntegrityArtifact(artifacts,"legacy-annotations.json")!=null){
            throw new IOException("Inventário declara anotações legadas que o manifesto não marcou como preservadas.");
        }
        c.fileCount+=(annotationsPreserved?2:1);
        c.bytes+=safeRegularFileSize(migrationPath)+(annotationsPreserved?safeRegularFileSize(annotationsPath):0);
        if(c.fileCount>MAX_CAPTURE_FILES||c.bytes>MAX_HTML_CAPTURE_BYTES)
            throw new IOException("Artefatos de migração excedem os limites totais da captura.");
    }

    private static JSONObject findIntegrityArtifact(JSONArray artifacts,String path){
        for(int i=0;artifacts!=null&&i<artifacts.length();i++){
            JSONObject row=artifacts.optJSONObject(i);
            if(row!=null&&path.equals(row.optString("path","")))return row;
        }
        return null;
    }

    private static void addIntegrityArtifactItem(Capture c,String path,JSONObject expected,String state)throws Exception{
        Item item=new Item();item.path=path;item.expected=expected.optString("sha256","");item.size=expected.optLong("size",-1);
        Path file=resolve(c.dir,path);if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Artefato local ausente: "+path);
        item.actual=hash(file);item.bytes=safeRegularFileSize(file);item.state=item.actual.equalsIgnoreCase(item.expected)&&item.bytes==item.size?state:"alterado";c.items.add(item);
    }

    private static void verifyMessageCapture(Capture c,JSONObject manifest) throws Exception {
        if(manifest.optInt("schemaVersion",0)!=1||!"4.3.1".equals(manifest.optString("ipedVersion"))||!"completed".equals(manifest.optString("status"))||!"complete".equals(manifest.optString("state")))throw new IOException("Formato de captura HTML, estado ou versão IPED incompatível.");
        JSONObject sourceMetadata=manifest.optJSONObject("sourceMetadata"),anchors=manifest.optJSONObject("anchors");
        if(sourceMetadata==null||anchors==null||sourceMetadata.optString("name","").length()>1024||sourceMetadata.optLong("size",-1)<0||sourceMetadata.optString("type","").length()>256||sourceMetadata.optString("itemHash","").length()>128||!manifest.optString("sourceFingerprint","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("conversationKey","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("captureRootHash","").matches("(?i)[0-9a-f]{64}"))throw new IOException("Metadados de origem, âncoras ou hashes do manifesto são inválidos.");
        String messagesName=manifest.optString("messagesFile","");if(!"messages.json".equals(messagesName))throw new IOException("Arquivo de mensagens inválido.");
        Path messagesPath=resolve(c.dir,messagesName);if(messagesPath==null)throw new IOException("Caminho do manifesto de mensagens inválido.");
        JSONObject wrapper=parseBoundedJson(readUtf8Limited(messagesPath,MAX_HTML_CAPTURE_BYTES),messagesPath);JSONArray messages=wrapper.optJSONArray("messages");
        if(messages==null||messages.length()!=manifest.optInt("messageCount",-1))throw new IOException("Contagem de mensagens diverge do manifesto.");
        if(messages.length()>MAX_SELECTED_ITEMS)throw new IOException("Captura excede o limite global de mensagens.");
        Set<String> ids=new HashSet<>(),keys=new HashSet<>(),attachmentPaths=new HashSet<>();Map<String,String> attachmentMimeByPath=new HashMap<>();Map<String,JSONObject> currentMessages=new HashMap<>();Map<String,Integer> naturalOrdinals=new HashMap<>();long attachmentBytes=0;
        for(int n=0;n<messages.length();n++){
            JSONObject row=messages.optJSONObject(n);if(row==null||row.optInt("order",-1)!=n)throw new IOException("Ordem/estrutura inválida em messages.json, índice "+n);
            String id=row.optString("domId",""),key=row.optString("key",""),messageHash=row.optString("messageHash","");
            if(!id.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")||!key.matches("(?i)[0-9a-f]{64}")||!messageHash.matches("(?i)[0-9a-f]{64}")||!ids.add(id)||!keys.add(key))throw new IOException("ID/chave duplicada ou hash inválido na mensagem "+n);
            String conversation=manifest.optString("conversationKey","");if(!conversation.matches("(?i)[0-9a-f]{64}"))throw new IOException("Chave de conversa inválida.");
            String natural=row.optString("timestamp","")+"\n"+row.optString("sender","")+"\n"+row.optString("direction","")+"\n"+row.optString("type","");
            int expectedOrdinal=naturalOrdinals.containsKey(natural)?naturalOrdinals.get(natural):0;naturalOrdinals.put(natural,expectedOrdinal+1);
            if(row.optInt("duplicateOrdinal",-1)!=expectedOrdinal||row.optString("text","").length()>1024*1024)throw new IOException("Ordinal duplicado ou texto excessivo na mensagem "+id);
            String expectedKey=hashText(conversation+"\n"+id+"\n"+natural+"\n"+expectedOrdinal);if(!key.equalsIgnoreCase(expectedKey))throw new IOException("Chave de identidade divergente na mensagem "+id);
            JSONArray attachments=row.optJSONArray("attachments");
            if(attachments==null)throw new IOException("Lista de anexos ausente na mensagem "+id);
            for(int i=0;i<attachments.length();i++){
                JSONObject attachment=attachments.optJSONObject(i);if(attachment==null)throw new IOException("Anexo inválido na mensagem "+id);
                String path=attachment.optString("path",""),mime=attachment.optString("mime",""),sha=attachment.optString("sha256","");long size=attachment.optLong("size",-1);
                if(!path.matches("attachments/[0-9a-fA-F]{64}\\.(?:png|jpe?g|gif|webp|ogg|wav|mp3|mp4|webm|pdf|zip|docx|xlsx|pptx|doc|xls|ppt|txt|csv|rar|7z|bin)")||!mime.matches("(?i)(?:image/(?:png|jpeg|gif|webp)|audio/(?:ogg|wav|mpeg)|video/(?:mp4|webm)|application/(?:pdf|zip|msword|vnd\\.ms-(?:excel|powerpoint)|vnd\\.openxmlformats-officedocument\\.(?:wordprocessingml\\.document|spreadsheetml\\.sheet|presentationml\\.presentation)|vnd\\.rar|x-7z-compressed|octet-stream)|text/(?:plain|csv))")||!sha.matches("(?i)[0-9a-f]{64}")||!path.substring("attachments/".length()).split("\\.",2)[0].equalsIgnoreCase(sha)||size<1||size>MAX_METADATA_BYTES)throw new IOException("Metadado de anexo inválido na mensagem "+id);
                Path file=resolve(c.dir,path);if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||safeRegularFileSize(file)!=size||!hash(file).equalsIgnoreCase(sha)||!mimeSignatureMatches(mime,file))throw new IOException("Anexo divergente ou MIME incompatível na mensagem "+id+": "+path);
                String oldMime=attachmentMimeByPath.putIfAbsent(path,mime);if(oldMime!=null&&!oldMime.equalsIgnoreCase(mime))throw new IOException("Um mesmo anexo possui MIME divergente entre mensagens: "+path);
                if(attachmentPaths.add(path)){attachmentBytes+=size;if(attachmentBytes>MAX_METADATA_BYTES*4L)throw new IOException("Anexos da captura excedem o orçamento seguro.");Item attachmentItem=new Item();attachmentItem.path=path;attachmentItem.expected=sha;attachmentItem.actual=sha;attachmentItem.bytes=size;attachmentItem.state="anexo íntegro";c.items.add(attachmentItem);}
            }
            if(!messageHash.equalsIgnoreCase(htmlMessageHash(row)))throw new IOException("Hash da mensagem diverge: "+id);
            currentMessages.put(key,row);
            Item item=new Item();item.path="mensagem:"+id;item.expected=messageHash;item.actual=messageHash;item.state="mensagem estruturada";item.bytes=row.optString("text","").getBytes(StandardCharsets.UTF_8).length;c.items.add(item);
        }
        if(messages.length()==0||!anchors.optString("start","").equals(messages.getJSONObject(0).optString("domId"))||!anchors.optString("end","").equals(messages.getJSONObject(messages.length()-1).optString("domId")))throw new IOException("Âncoras do manifesto não correspondem aos limites da captura.");
        if((long)messages.length()+attachmentPaths.size()>MAX_SELECTED_ITEMS)throw new IOException("Mensagens e anexos excedem o limite global de itens.");
        JSONArray publicHashes=manifest.optJSONArray("messageHashes");if(publicHashes==null||publicHashes.length()!=messages.length())throw new IOException("Resumo público de hashes por mensagem ausente ou divergente.");for(int i=0;i<messages.length();i++){JSONObject summary=publicHashes.optJSONObject(i),message=messages.getJSONObject(i);if(summary==null||!summary.optString("domId","").equals(message.optString("domId"))||!summary.optString("key","").equals(message.optString("key"))||!summary.optString("messageHash","").equalsIgnoreCase(message.optString("messageHash")))throw new IOException("Resumo público de hash diverge da mensagem "+message.optString("domId"));}
        Path htmlPath=resolve(c.dir,"whatsapp.html");if(htmlPath==null||!Files.isRegularFile(htmlPath,LinkOption.NOFOLLOW_LINKS))throw new IOException("HTML da captura não encontrado.");
        if(safeRegularFileSize(htmlPath)>MAX_HTML_CAPTURE_BYTES)throw new IOException("HTML da captura excede o limite de leitura segura.");
        String html=readUtf8Limited(htmlPath,MAX_HTML_CAPTURE_BYTES);String htmlSha=manifest.optString("htmlSha256","");if(!htmlSha.matches("(?i)[0-9a-f]{64}")||!hash(htmlPath).equalsIgnoreCase(htmlSha))throw new IOException("Hash do HTML ausente ou divergente.");
        validateRenderedCaptureHtml(html,attachmentPaths,attachmentMimeByPath,keys,manifest.optInt("presentationVersion",0));
        String annotationsSha=manifest.optString("annotationsSha256","");if(!annotationsSha.isEmpty()){if(!annotationsSha.matches("(?i)[0-9a-f]{64}"))throw new IOException("Hash de anotações inválido.");Path annotationPath=resolve(c.dir,"annotations.json");if(annotationPath==null||!Files.isRegularFile(annotationPath,LinkOption.NOFOLLOW_LINKS)||safeRegularFileSize(annotationPath)>MAX_METADATA_BYTES||!hash(annotationPath).equalsIgnoreCase(annotationsSha))throw new IOException("Arquivo de anotações ausente, excessivo ou divergente.");parseBoundedJson(readUtf8Limited(annotationPath),annotationPath);Item annotationsItem=new Item();annotationsItem.path="annotations.json";annotationsItem.expected=annotationsSha;annotationsItem.actual=annotationsSha;annotationsItem.bytes=safeRegularFileSize(annotationPath);annotationsItem.state="anotações íntegras";c.items.add(annotationsItem);}else if(Files.exists(c.dir.resolve("annotations.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Arquivo de anotações sem hash no manifesto.");
        String reconciliationSha=manifest.optString("reconciliationSha256","");
        Map<String,String> reconciliationStates=verifyMessageReconciliation(c,manifest,currentMessages,reconciliationSha);
        for(Item item:c.items){if(item.path.startsWith("mensagem:")){String id=item.path.substring("mensagem:".length());String state=reconciliationStates.get(id);if(state!=null)item.state=state;}}
        c.hash=htmlCaptureRootHash(messages,htmlSha,annotationsSha,reconciliationSha,manifest);
        c.items.sort(Comparator.comparing(i->i.path));c.fileCount=messages.length()+attachmentPaths.size()+2+(annotationsSha.isEmpty()?0:1)+(reconciliationSha.isEmpty()?0:1);c.bytes=safeRegularFileSize(htmlPath)+safeRegularFileSize(messagesPath)+safeRegularFileSize(c.dir.resolve("manifest.json"))+attachmentBytes+(annotationsSha.isEmpty()?0:safeRegularFileSize(c.dir.resolve("annotations.json")))+(reconciliationSha.isEmpty()?0:safeRegularFileSize(c.dir.resolve("message-reconciliation.json")));if(c.bytes>MAX_HTML_CAPTURE_BYTES||c.fileCount>MAX_CAPTURE_FILES)throw new IOException("Captura HTML ultrapassa os limites totais de bytes ou arquivos.");
        c.allowedFiles.clear();
        c.allowedFiles.add("manifest.json");
        c.allowedFiles.add(messagesName);
        c.allowedFiles.add("whatsapp.html");
        c.allowedFiles.add("reextracao-auditoria.jsonl");
        c.allowedFiles.addAll(attachmentPaths);
        if(!annotationsSha.isEmpty())c.allowedFiles.add("annotations.json");
        if(!reconciliationSha.isEmpty())c.allowedFiles.add("message-reconciliation.json");
        String declaredCaptureRoot=manifest.optString("captureRootHash","");
        if(!declaredCaptureRoot.equalsIgnoreCase(c.hash)){
            throw new IOException("Hash raiz do manifesto de mensagens não confere com a captura verificada.");
        }
        c.historyStatus="consistente com registro local (manifesto de mensagens)";
    }

    private static void checkUnexpectedHtmlCaptureFiles(Capture c,int maxItemsAllowed)throws Exception{
        List<Path> allFiles=listCaptureFiles(c.dir);
        if(allFiles.size()>MAX_CAPTURE_FILES)throw new IOException("Captura excede o limite de "+MAX_CAPTURE_FILES+" arquivos: "+c.dir);
        allFiles.sort(Comparator.comparing(p->c.dir.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT)));
        List<Path> extras=new ArrayList<>();
        for(Path f:allFiles){
            String rel=c.dir.relativize(f).toString().replace('\\','/');
            if(!c.allowedFiles.contains(rel))extras.add(f);
        }
        if(extras.isEmpty())return;
        if((long)c.items.size()+extras.size()>maxItemsAllowed)throw new IOException("O limite global de itens selecionados foi excedido antes de montar os resultados.");
        for(Path f:extras){
            String rel=c.dir.relativize(f).toString().replace('\\','/');
            long size=safeRegularFileSize(f);
            String sha=hash(f);
            Item x=new Item();
            x.path=rel;
            x.actual=sha;
            x.bytes=size;
            x.state="extra";
            c.items.add(x);
            c.fileCount++;
            c.bytes+=size;
        }
        if(c.fileCount>MAX_CAPTURE_FILES||c.bytes>MAX_HTML_CAPTURE_BYTES)throw new IOException("Captura HTML ultrapassa os limites totais de bytes ou arquivos.");
        c.items.sort(Comparator.comparing(i->i.path));
        c.historyStatus=(c.historyStatus!=null&&c.historyStatus.startsWith("registro inválido"))
                ?("registro inválido; arquivos extras na captura: "+extras.size())
                :("arquivos extras na captura: "+extras.size());
    }

    private static void validateRenderedCaptureHtml(String html,Set<String> attachmentPaths,Map<String,String> mimeByPath,Set<String> messageKeys,int presentationVersion)throws Exception{
        String lowerHtml=html.toLowerCase(Locale.ROOT);
        if(lowerHtml.contains("<!--")||lowerHtml.contains("-->")||lowerHtml.contains("<![cdata[")||lowerHtml.contains("<?")){
            throw new IOException("HTML da captura contém comentários, CDATA ou instruções de processamento não permitidos.");
        }
        if(presentationVersion!=0&&presentationVersion!=2)throw new IOException("Versão de apresentação HTML desconhecida.");
        boolean modern=presentationVersion==2;
        if(!modern&&Pattern.compile("(?is)<(script|iframe|object|embed|form|link|base|svg|math|template|input|button|select|textarea)\\b|(?:[\\s/])on[a-z0-9_-]+\\s*=|javascript\\s*:|vbscript\\s*:").matcher(html).find()){
            throw new IOException("HTML da captura possui conteúdo ativo ou dependência externa.");
        }
        List<HtmlElementToken> tokens=scanHtmlElements(html);
        validateCaptureHtmlTokens(tokens,attachmentPaths,mimeByPath,messageKeys,modern);
    }

    private static final Pattern CSS_FORBIDDEN_CONTENT_PATTERN = Pattern.compile("(?is)</style|@import|expression\\s*\\(|(?:^|[;{])\\s*behavior\\s*:|-moz-binding|javascript\\s*:|vbscript\\s*:");
    private static final Pattern CSS_HEX_ESCAPE_PATTERN = Pattern.compile("\\\\([0-9a-fA-F]{1,6})(?:\\r\\n|[ \\t\\r\\n\\f])?");
    private static final Set<String> BLOCKED_SVG_ELEMENTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "script", "foreignobject", "iframe", "object", "embed", "style", "animate", "animatemotion", "animatetransform", "set", "mpath"
    )));

    private static boolean isAsciiHtmlLetter(char c){return (c>='a'&&c<='z')||(c>='A'&&c<='Z');}
    private static boolean isHtmlTagNameChar(char c){return isAsciiHtmlLetter(c)||(c>='0'&&c<='9');}
    private static boolean isHtmlAttrStart(char c){return isAsciiHtmlLetter(c)||c=='_'||c==':';}
    private static boolean isHtmlAttrChar(char c){return isHtmlAttrStart(c)||(c>='0'&&c<='9')||c=='.'||c=='-';}
    private static int indexOfIgnoreCase(String s,String needle,int from){int n=needle.length(),max=s.length()-n;for(int i=from;i<=max;i++)if(s.regionMatches(true,i,needle,0,n))return i;return -1;}

    /** Tokenizes HTML tags with strict attribute-state rules so unclosed or single-quote-broken tags cannot hide elements from validation. */
    private static List<HtmlElementToken> scanHtmlElements(String html)throws IOException{
        List<HtmlElementToken> tokens=new ArrayList<>();
        int len=html.length(),i=0;
        while(i<len){
            int lt=html.indexOf('<',i);
            if(lt<0)break;
            if(lt+1>=len)throw new IOException("Tag HTML malformada ou truncada.");
            char next=html.charAt(lt+1);
            if(next=='!'){
                if(!html.regionMatches(true,lt,"<!doctype",0,9))throw new IOException("Declaração HTML não permitida.");
                if(lt+9>=len||(!Character.isWhitespace(html.charAt(lt+9))&&html.charAt(lt+9)!='>'))throw new IOException("Declaração DOCTYPE HTML malformada.");
                int p=lt+9;
                while(p<len){
                    char dc=html.charAt(p);
                    if(dc=='>')break;
                    if(dc=='<'||dc=='"'||dc=='\'')throw new IOException("Declaração DOCTYPE HTML malformada.");
                    p++;
                }
                if(p>=len)throw new IOException("Declaração DOCTYPE HTML não fechada.");
                i=p+1;
                continue;
            }
            if(next=='?')throw new IOException("Instrução de processamento HTML não permitida.");
            if(next=='/'){
                int nameStart=lt+2,pos=nameStart;
                if(nameStart>=len||!isAsciiHtmlLetter(html.charAt(nameStart)))throw new IOException("Tag de fechamento HTML malformada.");
                while(pos<len&&isHtmlTagNameChar(html.charAt(pos)))pos++;
                int p=pos;
                while(p<len&&Character.isWhitespace(html.charAt(p)))p++;
                if(p>=len||html.charAt(p)!='>')throw new IOException("Tag de fechamento HTML malformada.");
                tokens.add(new HtmlElementToken(html.substring(nameStart,pos).toLowerCase(Locale.ROOT),true,Collections.emptyMap(),lt,p+1,""));
                i=p+1;
                continue;
            }
            if(!isAsciiHtmlLetter(next)){
                i=lt+1;
                continue;
            }
            int pos=lt+1;
            while(pos<len&&isHtmlTagNameChar(html.charAt(pos)))pos++;
            String tag=html.substring(lt+1,pos).toLowerCase(Locale.ROOT);
            if(pos<len){
                char boundary=html.charAt(pos);
                if(!Character.isWhitespace(boundary)&&boundary!='>'&&boundary!='/')throw new IOException("Nome ou separador de tag HTML inválido.");
            }
            Map<String,String> attrs=new LinkedHashMap<>();
            int tagClosePos=parseHtmlTagAttributesStrict(html,pos,attrs);
            if("script".equals(tag)||"style".equals(tag)){
                String closeNeedle="</"+tag;
                int search=tagClosePos,closeLt=-1,closeEnd=-1;
                while(search<len){
                    int cand=indexOfIgnoreCase(html,closeNeedle,search);
                    if(cand<0)throw new IOException("HTML contém elemento <"+tag+"> malformado ou não fechado.");
                    int p=cand+closeNeedle.length();
                    while(p<len&&Character.isWhitespace(html.charAt(p)))p++;
                    if(p<len&&html.charAt(p)=='>'){closeLt=cand;closeEnd=p+1;break;}
                    search=cand+2;
                }
                if(closeLt<0)throw new IOException("HTML contém elemento <"+tag+"> malformado ou não fechado.");
                tokens.add(new HtmlElementToken(tag,false,attrs,lt,closeEnd,html.substring(tagClosePos,closeLt)));
                i=closeEnd;
            }else{
                tokens.add(new HtmlElementToken(tag,false,attrs,lt,tagClosePos,""));
                i=tagClosePos;
            }
        }
        return tokens;
    }

    private static int parseHtmlTagAttributesStrict(String html,int startPos,Map<String,String> attrs)throws IOException{
        int len=html.length(),i=startPos;
        while(i<len){
            while(i<len&&Character.isWhitespace(html.charAt(i)))i++;
            if(i>=len)throw new IOException("Tag HTML não fechada.");
            char c=html.charAt(i);
            if(c=='>')return i+1;
            if(c=='/'){
                if(i+1<len&&html.charAt(i+1)=='>')return i+2;
                throw new IOException("Atributo HTML malformado.");
            }
            if(!isHtmlAttrStart(c))throw new IOException("Atributo HTML malformado.");
            int nameStart=i++;
            while(i<len&&isHtmlAttrChar(html.charAt(i)))i++;
            String attrName=html.substring(nameStart,i).toLowerCase(Locale.ROOT);
            while(i<len&&Character.isWhitespace(html.charAt(i)))i++;
            String rawValue="";
            if(i<len&&html.charAt(i)=='='){
                i++;
                while(i<len&&Character.isWhitespace(html.charAt(i)))i++;
                if(i>=len)throw new IOException("Atributo HTML sem valor.");
                char q=html.charAt(i);
                if(q=='"'||q=='\''){
                    i++;
                    int close=html.indexOf(q,i);
                    if(close<0)throw new IOException("Atributo HTML com aspas não fechadas.");
                    rawValue=html.substring(i,close);
                    i=close+1;
                }else{
                    int valStart=i;
                    while(i<len&&!Character.isWhitespace(html.charAt(i))&&html.charAt(i)!='>'){
                        char vc=html.charAt(i);
                        if(vc=='"'||vc=='\''||vc=='`'||vc=='<'||vc=='=')throw new IOException("Atributo HTML sem aspas malformado.");
                        i++;
                    }
                    if(i==valStart)throw new IOException("Atributo HTML sem valor entre aspas.");
                    rawValue=html.substring(valStart,i);
                }
            }
            if(attrs.put(attrName,decodeHtmlAttributeValue(rawValue))!=null)throw new IOException("Atributo HTML duplicado.");
        }
        throw new IOException("Tag HTML não fechada.");
    }

    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&(?:(amp|lt|gt|quot|apos)|#([0-9]{1,7})|#[xX]([0-9a-fA-F]{1,6}));");

    private static String decodeHtmlAttributeValue(String raw) {
        if (raw == null || raw.indexOf('&') < 0) return raw;
        Matcher m = HTML_ENTITY_PATTERN.matcher(raw);
        if (!m.find()) return raw;
        StringBuffer out = new StringBuffer(raw.length());
        do {
            String replacement = m.group(0);
            String named = m.group(1);
            if (named != null) {
                String lower = named.toLowerCase(Locale.ROOT);
                if ("amp".equals(lower)) replacement = "&";
                else if ("lt".equals(lower)) replacement = "<";
                else if ("gt".equals(lower)) replacement = ">";
                else if ("quot".equals(lower)) replacement = "\"";
                else if ("apos".equals(lower)) replacement = "'";
            } else {
                String dec = m.group(2);
                try {
                    int cp = dec != null ? Integer.parseInt(dec, 10) : Integer.parseInt(m.group(3), 16);
                    if (cp >= 0 && cp <= 0x10FFFF && (cp < 0xD800 || cp > 0xDFFF)) {
                        replacement = new String(Character.toChars(cp));
                    } else {
                        replacement = "\uFFFD";
                    }
                } catch (NumberFormatException ignored) {
                    replacement = "\uFFFD";
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        } while (m.find());
        m.appendTail(out);
        return out.toString();
    }

    private static void validateCaptureHtmlTokens(List<HtmlElementToken> tokens,Set<String> attachmentPaths,Map<String,String> mimeByPath,Set<String> messageKeys,boolean modern)throws Exception{
        int metaCount=0,charsetCount=0,cspMetaCount=0,scriptCount=0,styleCount=0,headStart=-1,headEnd=-1,headOpenCount=0,headCloseCount=0;
        String cspContent="",legacyCsp="default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'";
        Set<String> ids=new HashSet<>(),renderedKeys=new HashSet<>(),resources=new HashSet<>();
        List<String> pendingFragments=new ArrayList<>();
        for(HtmlElementToken t:tokens){
            if(t.closing){
                if("head".equals(t.tag)){headEnd=t.end;headCloseCount++;}
                continue;
            }
            String tag=t.tag;
            Map<String,String> attrs=t.attrs;
            if("head".equals(tag)){headStart=t.start;headOpenCount++;}
            if("meta".equals(tag)){
                metaCount++;
                if(attrs.containsKey("charset")){
                    if(attrs.size()!=1||!"utf-8".equalsIgnoreCase(attrs.get("charset")))throw new IOException("Meta charset fora da allowlist.");
                    charsetCount++;
                }else if(attrs.containsKey("http-equiv")){
                    String content=attrs.get("content");
                    boolean allowed=modern?(CAPTURE_CSP.equals(content)||LEGACY_CAPTURE_CSP.equals(content)):legacyCsp.equals(content);
                    if(attrs.size()!=2||!"content-security-policy".equalsIgnoreCase(attrs.get("http-equiv"))||!allowed)throw new IOException("Política CSP ausente ou não canônica.");
                    cspMetaCount++;cspContent=content;
                }else throw new IOException("Meta não permitido no HTML da captura.");
                continue;
            }
            if("script".equals(tag)){
                if(!modern)throw new IOException("HTML da captura possui conteúdo ativo ou dependência externa.");
                scriptCount++;
                if(!attrs.isEmpty())throw new IOException("Atributos de script não são permitidos.");
                boolean currentScript=CAPTURE_MEDIA_STOP_SCRIPT.equals(t.body),legacyScript=LEGACY_CAPTURE_MEDIA_STOP_SCRIPT.equals(t.body);
                if(!currentScript&&!legacyScript)throw new IOException("HTML contém script diferente dos scripts fixos autorizados.");
                continue;
            }
            if("style".equals(tag)){
                styleCount++;
                if(!attrs.isEmpty())throw new IOException("Atributos de estilo não são permitidos.");
                validateCaptureCss(t.body);
                continue;
            }
            Set<String> allowedAttrs=ALLOWED_HTML_ATTRIBUTES.get(tag);
            if(allowedAttrs==null)throw new IOException("HTML contém elemento ativo ou não permitido: "+tag);
            for(String name:attrs.keySet())if(!allowedAttrs.contains(name)&&!isAllowedSafeDataAttr(tag,name))throw new IOException("HTML contém atributo não permitido: "+name);
            for(String dimAttr:new String[]{"width","height","colspan","rowspan"}){String dimVal=attrs.get(dimAttr);if(dimVal!=null&&!dimVal.matches("\\d{1,5}"))throw new IOException("Atributo de dimensão HTML inválido: "+dimAttr);}
            String id=attrs.get("id");if(id!=null&&!ids.add(id))throw new IOException("HTML contém ID duplicado.");
            String key=attrs.get("data-capture-message-key");if(key!=null&&!renderedKeys.add(key))throw new IOException("HTML contém chave de mensagem duplicada.");
            if("iframe".equals(tag)){
                if(!modern||!attrs.containsKey("src")||!attrs.containsKey("sandbox")||attrs.size()>4||!Arrays.asList("class","title","src","sandbox").containsAll(attrs.keySet())||!"".equals(attrs.get("sandbox"))||!"application/pdf".equals(mimeByPath.get(attrs.get("src"))))throw new IOException("Pré-visualização PDF não está confinada por sandbox vazio.");
            }
            if(("audio".equals(tag)||"video".equals(tag))&&modern){String prefix="audio".equals(tag)?"audio/":"video/";if(!attrs.containsKey("controls")||attrs.containsKey("autoplay")||!"none".equals(attrs.get("preload")))throw new IOException("Controles de mídia exigem preload none e sem autoplay.");String mediaPath=attrs.get("src");if(mediaPath!=null&&!mimeByPath.getOrDefault(mediaPath,"").startsWith(prefix))throw new IOException("MIME da mídia não corresponde ao player.");}
            for(String attr:new String[]{"src","href","poster","action","formaction"}){
                String value=attrs.get(attr);if(value==null)continue;
                if("href".equals(attr)&&value.startsWith("#")){String fragment=value.substring(1);if(!fragment.isEmpty())pendingFragments.add(fragment);continue;}
                if(value.startsWith("data:")){
                    if(!modern||!"src".equals(attr)||!"img".equals(tag)||!validateEmbeddedImage(value))throw new IOException("Imagem embutida HTML não validada.");continue;
                }
                if(!("src".equals(attr)||"href".equals(attr))||!attachmentPaths.contains(value))throw new IOException("HTML contém URL externa, absoluta ou recurso não inventariado: "+attr);
                resources.add(value);
                String mime=mimeByPath.get(value);
                if(modern&&"img".equals(tag)&&"src".equals(attr)&&(mime==null||!mime.startsWith("image/")))throw new IOException("Imagem HTML não corresponde ao MIME inventariado.");
                if("iframe".equals(tag)&&!"application/pdf".equals(mime))throw new IOException("Iframe só pode referenciar PDF inventariado.");
            }
            if(attrs.containsKey("target")&&(!"_blank".equals(attrs.get("target"))||!Arrays.asList(attrs.getOrDefault("rel","").toLowerCase(Locale.ROOT).split("\\s+")).containsAll(Arrays.asList("noopener","noreferrer"))))throw new IOException("Link em nova aba sem noopener/noreferrer.");
        }
        if(modern?(metaCount!=2||charsetCount!=1||cspMetaCount!=1):!((metaCount==0)||(metaCount==2&&charsetCount==1&&cspMetaCount==1)))throw new IOException("Quantidade de metadados HTML fora da allowlist.");
        if(modern){
            if(scriptCount!=1)throw new IOException("HTML deve conter somente um script fixo de mídia.");
            for(HtmlElementToken t:tokens)if(!t.closing&&"script".equals(t.tag)){
                boolean currentScript=CAPTURE_MEDIA_STOP_SCRIPT.equals(t.body),legacyScript=LEGACY_CAPTURE_MEDIA_STOP_SCRIPT.equals(t.body);
                if(currentScript&&!CAPTURE_CSP.equals(cspContent)||legacyScript&&!LEGACY_CAPTURE_CSP.equals(cspContent))throw new IOException("A CSP não corresponde ao script fixo autorizado.");
            }
            if(styleCount!=2)throw new IOException("HTML deve conter o tema IPED e uma folha de estilos do visualizador.");
        }else if(styleCount>0){
            if(headOpenCount!=1||headCloseCount!=1||headStart<0||headEnd<0)throw new IOException("Elemento de estilo não é permitido no corpo do HTML legado.");
            for(HtmlElementToken t:tokens)if(!t.closing&&"style".equals(t.tag)&&(t.start<headStart||t.end>headEnd))throw new IOException("Elemento de estilo não é permitido no corpo do HTML legado.");
        }
        if(!renderedKeys.equals(messageKeys))throw new IOException("HTML não corresponde exatamente às mensagens do manifesto.");
        if(!resources.equals(attachmentPaths))throw new IOException("Recursos locais do HTML não correspondem aos anexos do manifesto.");
        for(String fragment:pendingFragments)if(!ids.contains(fragment))throw new IOException("Link de fragmento sem destino único: #"+fragment);
    }

    private static boolean validateEmbeddedImage(String uri)throws IOException{
        Matcher m=Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp));base64,([a-z0-9+/=\\r\\n]+)$").matcher(uri);if(!m.matches())return false;byte[] bytes;try{bytes=Base64.getMimeDecoder().decode(m.group(2));}catch(IllegalArgumentException invalid){return false;}if(bytes.length==0||bytes.length>8*1024*1024)return false;String mime=m.group(1).toLowerCase(Locale.ROOT);if(captureRasterSignatureMatches(mime,bytes))return true;return "image/jpg".equals(mime)&&captureRasterSignatureMatches("image/png",bytes);
    }

    private static String normalizeCssForSecurityCheck(String css)throws IOException{
        if(css.indexOf('\0')>=0)throw new IOException("CSS contém byte nulo.");
        String noComments=Pattern.compile("(?s)/\\*.*?\\*/").matcher(css).replaceAll(" ");
        Matcher hex=CSS_HEX_ESCAPE_PATTERN.matcher(noComments);
        StringBuffer decoded=new StringBuffer(noComments.length());
        while(hex.find()){
            int code=Integer.parseInt(hex.group(1),16);
            if(code<=0||code>0x10FFFF||(code>=0xD800&&code<=0xDFFF))throw new IOException("Escape CSS inválido.");
            hex.appendReplacement(decoded,Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        hex.appendTail(decoded);
        StringBuilder out=new StringBuilder(decoded.length());
        for(int i=0;i<decoded.length();i++){
            char ch=decoded.charAt(i);
            if(ch=='\\'){
                if(i+1<decoded.length()){
                    char next=decoded.charAt(++i);
                    if(next=='\0')throw new IOException("Escape CSS inválido.");
                    out.append(next);
                }else{
                    out.append(ch);
                }
            }else{
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static void validateCaptureCss(String css)throws Exception{
        if(css.getBytes(StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("CSS da captura contém regra ativa ou excede o limite.");
        String normalized=normalizeCssForSecurityCheck(css);
        if(CSS_FORBIDDEN_CONTENT_PATTERN.matcher(css).find()||CSS_FORBIDDEN_CONTENT_PATTERN.matcher(normalized).find())throw new IOException("CSS da captura contém regra ativa ou excede o limite.");
        Pattern urlPattern=Pattern.compile("(?is)url\\(\\s*([\\\"']?)(.*?)\\1\\s*\\)");
        String cleanRaw=urlPattern.matcher(css).replaceAll("");
        String cleanNorm=urlPattern.matcher(normalized).replaceAll("");
        if(Pattern.compile("(?i)url\\s*\\(").matcher(cleanRaw).find()||Pattern.compile("(?i)url\\s*\\(").matcher(cleanNorm).find())throw new IOException("URL CSS malformada.");
        Matcher normUrls=urlPattern.matcher(normalized);
        while(normUrls.find()){if(!Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\\+xml));base64,([a-z0-9+/=\\r\\n]+)$").matcher(normUrls.group(2).trim()).matches())throw new IOException("CSS contém dependência externa ou URL não permitida.");}
        Matcher urls=urlPattern.matcher(css);long total=0;
        while(urls.find()){Matcher image=Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\\+xml));base64,([a-z0-9+/=\\r\\n]+)$").matcher(urls.group(2).trim());if(!image.matches())throw new IOException("CSS contém dependência externa ou URL não permitida.");byte[] bytes;try{bytes=Base64.getMimeDecoder().decode(image.group(2));}catch(IllegalArgumentException invalid){throw new IOException("Base64 inválido em recurso CSS.",invalid);}if(bytes.length==0||bytes.length>8*1024*1024)throw new IOException("Imagem CSS acima de 8 MiB.");total+=bytes.length;if(total>16*1024*1024)throw new IOException("Imagens CSS acima de 16 MiB no total.");String mime=image.group(1).toLowerCase(Locale.ROOT);if("image/svg+xml".equals(mime))validateCaptureSvg(bytes);else if(!captureRasterSignatureMatches(mime,bytes))throw new IOException("Assinatura/MIME divergente em imagem CSS.");}
    }

    private static boolean captureRasterSignatureMatches(String mime,byte[] b){if("image/jpg".equals(mime))mime="image/jpeg";if("image/png".equals(mime))return b.length>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;if("image/jpeg".equals(mime))return b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;if("image/gif".equals(mime))return b.length>=6&&("GIF87a".equals(new String(b,0,6,StandardCharsets.US_ASCII))||"GIF89a".equals(new String(b,0,6,StandardCharsets.US_ASCII)));return "image/webp".equals(mime)&&b.length>=12&&"RIFF".equals(new String(b,0,4,StandardCharsets.US_ASCII))&&"WEBP".equals(new String(b,8,4,StandardCharsets.US_ASCII));}

    private static void validateCaptureSvg(byte[] bytes)throws Exception{
        if(bytes.length>512*1024)throw new IOException("SVG CSS acima de 512 KiB.");String xml=new String(bytes,StandardCharsets.UTF_8);if(Pattern.compile("(?i)<!\\s*(?:DOCTYPE|ENTITY)").matcher(xml).find())throw new IOException("SVG não pode conter DTD ou entidade.");DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");Document d=f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));Element root=d.getDocumentElement();if(root==null||!"svg".equalsIgnoreCase(root.getLocalName()==null?root.getTagName():root.getLocalName()))throw new IOException("Raiz SVG inválida.");NodeList all=d.getElementsByTagName("*");if(all.getLength()>4096)throw new IOException("SVG excede 4.096 elementos.");Pattern unsafeSvgUrl=Pattern.compile("(?is)url\\s*\\((?>\\s*(?:['\"]\\s*)?)(?!#)");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);String name=e.getLocalName()==null?e.getTagName():e.getLocalName();if(BLOCKED_SVG_ELEMENTS.contains(name.toLowerCase(Locale.ROOT)))throw new IOException("SVG contém elemento ativo.");NamedNodeMap attrs=e.getAttributes();for(int j=0;j<attrs.getLength();j++){Node a=attrs.item(j);String n=a.getNodeName().toLowerCase(Locale.ROOT),v=a.getNodeValue();String trimmed=v==null?"":v.trim();if(n.startsWith("on")||(("href".equals(n)||n.endsWith(":href")||"src".equals(n))&&!trimmed.isEmpty()&&!trimmed.startsWith("#"))||unsafeSvgUrl.matcher(trimmed).find())throw new IOException("SVG contém referência externa ou evento.");if("style".equals(n)){String normStyle=normalizeCssForSecurityCheck(trimmed);if(CSS_FORBIDDEN_CONTENT_PATTERN.matcher(trimmed).find()||CSS_FORBIDDEN_CONTENT_PATTERN.matcher(normStyle).find()||unsafeSvgUrl.matcher(normStyle).find())throw new IOException("SVG contém estilo externo ou ativo.");}}}
    }

    private static String htmlMessageHash(JSONObject message)throws Exception{StringBuilder canonical=new StringBuilder("html-message-v1-message\n");appendHtmlField(canonical,message.optString("domId",""));appendHtmlField(canonical,Integer.toString(message.optInt("order",-1)));appendHtmlField(canonical,message.optString("timestamp",""));appendHtmlField(canonical,message.optString("sender",""));appendHtmlField(canonical,message.optString("direction",""));appendHtmlField(canonical,message.optString("type",""));appendHtmlField(canonical,Integer.toString(message.optInt("duplicateOrdinal",-1)));appendHtmlField(canonical,message.optString("text",""));appendHtmlField(canonical,message.optString("classes",""));JSONArray attachments=message.optJSONArray("attachments");for(int i=0;attachments!=null&&i<attachments.length();i++){JSONObject a=attachments.getJSONObject(i);appendHtmlField(canonical,a.optString("path",""));appendHtmlField(canonical,a.optString("mime",""));appendHtmlField(canonical,Long.toString(a.optLong("size",-1)));appendHtmlField(canonical,a.optString("sha256",""));}return hashText(canonical.toString());}
    private static boolean mimeSignatureMatches(String mime,Path file)throws IOException{
        if(file==null||isLinkOrReparse(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Anexo inválido ou link não permitido: "+file);
        byte[] b=new byte[12];int length=0;
        try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){while(length<b.length){int n=in.read(b,length,b.length-length);if(n<0)break;length+=n;}}
        if("image/png".equalsIgnoreCase(mime))return length>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;
        if("image/jpeg".equalsIgnoreCase(mime))return length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;
        if("image/gif".equalsIgnoreCase(mime)){String s=length<6?"":new String(b,0,6,StandardCharsets.US_ASCII);return "GIF87a".equals(s)||"GIF89a".equals(s);}
        if("image/webp".equalsIgnoreCase(mime))return length>=12&&"RIFF".equals(new String(b,0,4,StandardCharsets.US_ASCII))&&"WEBP".equals(new String(b,8,4,StandardCharsets.US_ASCII));
        if("audio/ogg".equalsIgnoreCase(mime))return length>=4&&"OggS".equals(new String(b,0,4,StandardCharsets.US_ASCII));
        if("audio/wav".equalsIgnoreCase(mime))return length>=12&&"RIFF".equals(new String(b,0,4,StandardCharsets.US_ASCII))&&"WAVE".equals(new String(b,8,4,StandardCharsets.US_ASCII));
        if("audio/mpeg".equalsIgnoreCase(mime)){boolean id3=length>=3&&"ID3".equals(new String(b,0,3,StandardCharsets.US_ASCII));boolean frame=length>=2&&(b[0]&255)==0xff&&(b[1]&0xe0)==0xe0;return id3||frame;}
        if("video/mp4".equalsIgnoreCase(mime))return length>=8&&"ftyp".equals(new String(b,4,4,StandardCharsets.US_ASCII));
        if("video/webm".equalsIgnoreCase(mime))return length>=4&&(b[0]&255)==0x1a&&(b[1]&255)==0x45&&(b[2]&255)==0xdf&&(b[3]&255)==0xa3;
        if("application/pdf".equalsIgnoreCase(mime))return length>=5&&"%PDF-".equals(new String(b,0,5,StandardCharsets.US_ASCII));
        if("application/zip".equalsIgnoreCase(mime)||mime.toLowerCase(Locale.ROOT).startsWith("application/vnd.openxmlformats-officedocument."))return length>=4&&b[0]=='P'&&b[1]=='K'&&(b[2]==3||b[2]==5||b[2]==7);
        if("application/msword".equalsIgnoreCase(mime)||"application/vnd.ms-excel".equalsIgnoreCase(mime)||"application/vnd.ms-powerpoint".equalsIgnoreCase(mime))return length>=8&&(b[0]&255)==0xd0&&(b[1]&255)==0xcf&&(b[2]&255)==0x11&&(b[3]&255)==0xe0&&(b[4]&255)==0xa1&&(b[5]&255)==0xb1&&(b[6]&255)==0x1a&&(b[7]&255)==0xe1;
        if("application/vnd.rar".equalsIgnoreCase(mime))return length>=7&&"Rar!\u001a\u0007".equals(new String(b,0,6,StandardCharsets.US_ASCII));
        if("application/x-7z-compressed".equalsIgnoreCase(mime))return length>=6&&(b[0]&255)==0x37&&(b[1]&255)==0x7a&&(b[2]&255)==0xbc&&(b[3]&255)==0xaf&&(b[4]&255)==0x27&&(b[5]&255)==0x1c;
        if("application/octet-stream".equalsIgnoreCase(mime))return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin");
        if("text/plain".equalsIgnoreCase(mime)||"text/csv".equalsIgnoreCase(mime)){for(int i=0;i<length;i++)if(b[i]==0)return false;return true;}
        return false;
    }
    private static Map<String,String> verifyMessageReconciliation(Capture c,JSONObject manifest,Map<String,JSONObject> currentMessages,String reconciliationSha)throws Exception{
        Map<String,String> statesById=new HashMap<>();String reconciliationName=manifest.optString("reconciliationFile","");Path path=c.dir.resolve("message-reconciliation.json");
        if(reconciliationSha.isEmpty()){
            if(!reconciliationName.isEmpty()||Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Arquivo de reconciliação sem hash no manifesto.");
            return statesById;
        }
        if(!reconciliationSha.matches("(?i)[0-9a-f]{64}")||!"message-reconciliation.json".equals(reconciliationName)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||safeRegularFileSize(path)>MAX_METADATA_BYTES||!hash(path).equalsIgnoreCase(reconciliationSha))throw new IOException("Arquivo de reconciliação ausente, excessivo ou divergente.");
        JSONObject reconciliation=parseBoundedJson(readUtf8Limited(path),path);JSONArray entries=reconciliation.optJSONArray("entries");
        if(reconciliation.optInt("schemaVersion",0)!=1||!"html-message-v1".equals(reconciliation.optString("captureFormat"))||entries==null||entries.length()>MAX_SELECTED_ITEMS*2)throw new IOException("Estrutura de reconciliação inválida.");
        String previousRoot=reconciliation.optString("previousCaptureRootHash","");String previousReconciliation=reconciliation.optString("previousReconciliationSha256","");String source=reconciliation.optString("currentSourceFingerprint","");
        if(!previousRoot.matches("(?i)[0-9a-f]{64}")||(!previousReconciliation.isEmpty()&&!previousReconciliation.matches("(?i)[0-9a-f]{64}"))||!source.equalsIgnoreCase(manifest.optString("sourceFingerprint","")))throw new IOException("Origem ou hash anterior inválido na reconciliação.");
        Set<String> seen=new HashSet<>();int unchanged=0,changed=0,missing=0,added=0;
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i);if(entry==null)throw new IOException("Registro de reconciliação inválido.");
            String key=entry.optString("key",""),state=entry.optString("state",""),previous=entry.optString("previousMessageHash",""),current=entry.optString("currentMessageHash",""),id=entry.optString("domId","");
            if(!key.matches("(?i)[0-9a-f]{64}")||!seen.add(key)||!id.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?"))throw new IOException("Chave/ID duplicado ou inválido na reconciliação.");
            JSONObject currentMessage=currentMessages.get(key);String actualCurrent=currentMessage==null?"":currentMessage.optString("messageHash","");
            if(!actualCurrent.equalsIgnoreCase(current))throw new IOException("Hash atual da reconciliação não corresponde à captura: "+id);
            if("unchanged".equals(state)){if(!previous.matches("(?i)[0-9a-f]{64}")||!previous.equalsIgnoreCase(current)||currentMessage==null)throw new IOException("Estado unchanged inválido na mensagem "+id);unchanged++;statesById.put(id,"inalterada desde a captura anterior");}
            else if("changed".equals(state)){if(!previous.matches("(?i)[0-9a-f]{64}")||!current.matches("(?i)[0-9a-f]{64}")||previous.equalsIgnoreCase(current)||currentMessage==null)throw new IOException("Estado changed inválido na mensagem "+id);changed++;statesById.put(id,"alterada desde a captura anterior");}
            else if("missing_in_current_source".equals(state)){if(!previous.matches("(?i)[0-9a-f]{64}")||!current.isEmpty()||currentMessage!=null)throw new IOException("Estado missing inválido na mensagem "+id);missing++;Item absent=new Item();absent.path="mensagem-ausente:"+id;absent.expected=previous;absent.actual="";absent.state="não encontrada na fonte atual";c.items.add(absent);}
            else if("new_in_interval".equals(state)){if(!previous.isEmpty()||!current.matches("(?i)[0-9a-f]{64}")||currentMessage==null)throw new IOException("Estado new inválido na mensagem "+id);added++;statesById.put(id,"adicionada desde a captura anterior");}
            else throw new IOException("Estado desconhecido na reconciliação: "+state);
        }
        for(Map.Entry<String,JSONObject> current:currentMessages.entrySet())if(!seen.contains(current.getKey()))throw new IOException("Mensagem atual ausente da reconciliação: "+current.getValue().optString("domId",""));
        JSONObject counts=reconciliation.optJSONObject("counts");if(counts==null||counts.optInt("unchanged",-1)!=unchanged||counts.optInt("changed",-1)!=changed||counts.optInt("missingInCurrentSource",-1)!=missing||counts.optInt("newInInterval",-1)!=added)throw new IOException("Contagens da reconciliação divergem dos registros.");
        Item report=new Item();report.path="message-reconciliation.json";report.expected=reconciliationSha;report.actual=reconciliationSha;report.bytes=safeRegularFileSize(path);report.state="reconciliação por mensagem íntegra";c.items.add(report);return statesById;
    }
    private static String htmlCaptureRootHash(JSONArray messages,String htmlSha,String annotationsSha,String reconciliationSha,JSONObject manifest)throws Exception{StringBuilder canonical=new StringBuilder("html-message-v1-root\n");appendHtmlField(canonical,htmlSha);appendHtmlField(canonical,annotationsSha);appendHtmlField(canonical,reconciliationSha);appendHtmlField(canonical,manifest.optString("conversationKey",""));appendHtmlField(canonical,manifest.optString("sourceFingerprint",""));JSONObject source=manifest.optJSONObject("sourceMetadata");appendHtmlField(canonical,source==null?"":source.optString("name",""));appendHtmlField(canonical,source==null?"":Long.toString(source.optLong("size",-1)));appendHtmlField(canonical,source==null?"":source.optString("type",""));appendHtmlField(canonical,source==null?"":source.optString("itemHash",""));appendHtmlField(canonical,source==null?"":Boolean.toString(source.optBoolean("deleted",false)));appendHtmlField(canonical,manifest.optString("ipedVersion",""));appendHtmlField(canonical,manifest.optString("pluginVersion",""));JSONObject anchors=manifest.optJSONObject("anchors");appendHtmlField(canonical,anchors==null?"":anchors.optString("start",""));appendHtmlField(canonical,anchors==null?"":anchors.optString("end",""));for(int i=0;i<messages.length();i++){JSONObject message=messages.getJSONObject(i);appendHtmlField(canonical,message.optString("key",""));appendHtmlField(canonical,message.optString("messageHash",""));}return hashText(canonical.toString());}
    private static void appendHtmlField(StringBuilder canonical,String value){String field=value==null?"":value;canonical.append(field.length()).append(':').append(field).append('\n');}
    private static boolean hasControlChars(String s){
        for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(ch<0x20||ch==0x7f)return true;}
        return false;
    }
    private static boolean isOptionalSha256OrDash(String s){
        return s.isEmpty()||"—".equals(s)||s.matches("(?i)[0-9a-f]{64}");
    }
    private static void verifyHistory(Capture c,JSONObject record) throws Exception {
        JSONArray artifacts=record.optJSONArray("artifacts"), events=record.optJSONArray("events");
        if(artifacts==null||artifacts.length()==0||events==null||events.length()==0){c.historyStatus="registro incompleto";return;}
        StringBuilder content=new StringBuilder();
        List<JSONObject> sorted=new ArrayList<>();
        Set<String> seenPaths=new HashSet<>();
        for(int i=0;i<artifacts.length();i++){
            JSONObject item=artifacts.optJSONObject(i);
            if(item==null){c.historyStatus="inventário inválido";return;}
            String itemPath=item.optString("path","");
            String itemSha=item.optString("sha256","");
            if(itemPath.isEmpty()||itemPath.length()>MAX_RELATIVE_PATH_CHARS||hasControlChars(itemPath)||!seenPaths.add(itemPath.toLowerCase(Locale.ROOT))||!itemSha.matches("(?i)[0-9a-f]{64}")||item.optLong("size",-1)<0){
                c.historyStatus="inventário inválido";return;
            }
            sorted.add(item);
        }
        sorted.sort(Comparator.comparing(x->x.optString("path","")));
        for(JSONObject item:sorted)content.append(item.optString("path","")).append('|').append(item.optLong("size",0)).append('|').append(item.optString("sha256","")).append('\n');
        String inventoryRoot=hashText(content.toString());
        String declaredRoot=record.optString("contentRootHash","");
        c.contentRootHash=declaredRoot;
        c.historyEvents=events;
        if(!declaredRoot.matches("(?i)[0-9a-f]{64}")||!inventoryRoot.equalsIgnoreCase(declaredRoot)){c.historyStatus="raiz do registro divergente";return;}
        String previous="";
        String lastEventRoot="";
        for(int i=0;i<events.length();i++){
            JSONObject event=events.optJSONObject(i);
            String prevHash=event==null?"":event.optString("previousRecordHash","");
            String eventRoot=event==null?"":event.optString("contentRootHash","");
            String expected=event==null?"":event.optString("recordHash","");
            if(event==null||event.optInt("sequence",-1)!=i+1||!previous.equals(prevHash)||!eventRoot.matches("(?i)[0-9a-f]{64}")||!expected.matches("(?i)[0-9a-f]{64}")){c.historyStatus="cadeia do histórico quebrada";return;}
            JSONArray changes=event.optJSONArray("changes");
            if(changes!=null){
                for(int j=0;j<changes.length();j++){
                    JSONObject ch=changes.optJSONObject(j);
                    if(ch==null||!isOptionalSha256OrDash(ch.optString("previousSha256",""))||!isOptionalSha256OrDash(ch.optString("sha256",""))){
                        c.historyStatus="registro inválido";return;
                    }
                }
            }
            String format=event.optString("recordHashFormat","");
            JSONObject payload=new JSONObject(event.toString());payload.remove("recordHash");
            String canonicalActual=hashText(canonicalJson(payload));
            boolean validHash="canonical-v1".equals(format)
                    ?expected.equalsIgnoreCase(canonicalActual)
                    :(expected.equalsIgnoreCase(canonicalActual)||expected.equalsIgnoreCase(hashText(payload.toString())));
            if(!validHash){c.historyStatus="hash de evento inválido";return;}previous=expected;lastEventRoot=eventRoot;
        }
        c.historyHash=previous;
        if(!lastEventRoot.equalsIgnoreCase(declaredRoot)){c.historyStatus="cadeia do histórico divergente do estado atual";return;}
        StringBuilder current=new StringBuilder();for(JSONObject item:sorted){String path=item.optString("path","");Path file=resolve(c.dir,path);if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){c.historyStatus="arquivo ausente: "+path;return;}long fileSize=safeRegularFileSize(file);String actual=hash(file);if(!actual.equalsIgnoreCase(item.optString("sha256",""))||fileSize!=item.optLong("size",-1)){c.historyStatus="arquivo divergente: "+path;return;}current.append(path).append('|').append(fileSize).append('|').append(actual).append('\n');}
        c.historyStatus=hashText(current.toString()).equalsIgnoreCase(declaredRoot)?"consistente com registro local":"conteúdo diverge do registro local";
    }
    static boolean isLinkOrReparse(Path p)throws IOException{
        if(Files.isSymbolicLink(p))return true;
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return false;
        return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther();
    }
    static long safeRegularFileSize(Path p)throws IOException{
        if(p==null)throw new IOException("Caminho de arquivo nulo");
        BasicFileAttributes attrs=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.isSymbolicLink()||attrs.isOther()||!attrs.isRegularFile())throw new IOException("Arquivo inválido ou link simbólico: "+p);
        return attrs.size();
    }
    private static void validateRootDirectory(Path root)throws IOException{
        if(root==null||!Files.exists(root,LinkOption.NOFOLLOW_LINKS)||isLinkOrReparse(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)){
            throw new IOException("Pasta de capturas inválida, link simbólico ou junção não permitida: "+root);
        }
    }
    static Path resolve(Path root,String rel){
        try{
            if(rel==null||rel.isEmpty()||rel.length()>MAX_RELATIVE_PATH_CHARS||rel.indexOf('\0')>=0||rel.indexOf(':')>=0)return null;
            Path normalizedRoot=root.toAbsolutePath().normalize();
            Path r=normalizedRoot.getFileSystem().getPath(rel.replace('\\','/'));
            if(r.isAbsolute()||r.getNameCount()==0)return null;
            for(Path p:r)if(p.toString().equals("..")||p.toString().equals("."))return null;
            Path f=normalizedRoot.resolve(r).normalize();
            if(!f.startsWith(normalizedRoot)||f.equals(normalizedRoot))return null;
            Path q=normalizedRoot;
            for(Path p:normalizedRoot.relativize(f)){
                q=q.resolve(p);
                if(isLinkOrReparse(q))return null;
            }
            if(Files.exists(f,LinkOption.NOFOLLOW_LINKS)){
                Path realRoot=normalizedRoot.toRealPath();
                Path realFile=f.toRealPath();
                if(!realFile.startsWith(realRoot)||realFile.equals(realRoot))return null;
            }
            return f;
        }catch(Exception e){return null;}
    }
    private static String hash(Path p)throws Exception{if(p==null||isLinkOrReparse(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Arquivo inválido ou link simbólico para SHA-256: "+p);MessageDigest d=MessageDigest.getInstance(ALG);try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[]b=new byte[32768];int n;while((n=in.read(b))!=-1){if(n>0)d.update(b,0,n);}}return hex(d.digest());}
    private static final class InvalidJsonException extends IOException {
        InvalidJsonException(String message){super(message);}
        InvalidJsonException(String message,Throwable cause){super(message,cause);}
    }

    private static String canonicalQuote(String s){
        if(s==null)return "null";
        StringBuilder out=new StringBuilder(s.length()+2);
        out.append('"');
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\b':out.append("\\b");break;
                case '\f':out.append("\\f");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<0x20)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String canonicalJson(Object value){
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){
            JSONObject obj=(JSONObject)value;
            TreeSet<String> keys=new TreeSet<>(obj.keySet());
            StringBuilder out=new StringBuilder("{");
            boolean first=true;
            for(String key:keys){
                if(!first)out.append(',');
                first=false;
                out.append(canonicalQuote(key)).append(':').append(canonicalJson(obj.opt(key)));
            }
            return out.append('}').toString();
        }
        if(value instanceof JSONArray){
            JSONArray arr=(JSONArray)value;
            StringBuilder out=new StringBuilder("[");
            for(int i=0;i<arr.length();i++){
                if(i>0)out.append(',');
                out.append(canonicalJson(arr.opt(i)));
            }
            return out.append(']').toString();
        }
        if(value instanceof String)return canonicalQuote((String)value);
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        return canonicalQuote(value.toString());
    }

    private static String readUtf8Limited(Path p)throws IOException{return readUtf8Limited(p,MAX_METADATA_BYTES);}
    private static String readUtf8Limited(Path p,long maxBytes)throws IOException{if(maxBytes<=0||maxBytes>MAX_HTML_CAPTURE_BYTES)throw new IllegalArgumentException("Limite de leitura inválido: "+maxBytes);long size=safeRegularFileSize(p);if(size>maxBytes)throw new IOException("Arquivo de metadados excede o limite de "+maxBytes+" bytes: "+p);try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream((int)Math.min(size,maxBytes))){byte[]buffer=new byte[8192];int n;long total=0;while((n=in.read(buffer))!=-1){if(n==0)continue;total+=n;if(total>maxBytes)throw new IOException("Arquivo de metadados excede o limite de "+maxBytes+" bytes: "+p);out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static JSONObject parseBoundedJson(String json,Path source)throws IOException{int depth=0;boolean quoted=false,escaped=false;for(int i=0;i<json.length();i++){char ch=json.charAt(i);if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;continue;}if(ch=='"'){quoted=true;continue;}if(ch=='{'||ch=='['){if(++depth>MAX_JSON_DEPTH)throw new InvalidJsonException("JSON excede a profundidade máxima de "+MAX_JSON_DEPTH+" níveis: "+source);}else if(ch=='}'||ch==']'){if(--depth<0)throw new InvalidJsonException("JSON malformado: "+source);}}if(quoted||depth!=0)throw new InvalidJsonException("JSON incompleto: "+source);try{return new JSONObject(json);}catch(RuntimeException invalid){throw new InvalidJsonException("JSON inválido: "+source,invalid);}}
    static List<Path> listCaptureFiles(Path root)throws IOException{
        Path normalizedRoot=root.toAbsolutePath().normalize();
        Path realRoot=normalizedRoot.toRealPath();
        List<Path> files=new ArrayList<>();
        Files.walkFileTree(normalizedRoot,new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException{
                if(!dir.equals(normalizedRoot)){
                    if(attrs.isSymbolicLink()||attrs.isOther()||isLinkOrReparse(dir))throw new IOException("Junção ou link não permitido na captura: "+dir);
                    if(!dir.toRealPath().startsWith(realRoot))throw new IOException("Diretório fora da raiz da captura: "+dir);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{
                if(attrs.isSymbolicLink()||attrs.isOther()||isLinkOrReparse(file))throw new IOException("Junção ou link não permitido na captura: "+file);
                if(attrs.isRegularFile()){
                    if(!file.toRealPath().startsWith(realRoot))throw new IOException("Arquivo fora da raiz da captura: "+file);
                    if(files.size()>=MAX_CAPTURE_FILES)throw new IOException("Captura excede o limite de "+MAX_CAPTURE_FILES+" arquivos: "+root);
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
    private static String quietHash(Path p){try{return hash(p);}catch(Exception e){return null;}}
    private static long quietSize(Path p){try{return safeRegularFileSize(p);}catch(Exception e){return 0;}}
    private static String hashText(String s)throws Exception{return hex(MessageDigest.getInstance(ALG).digest(s.getBytes(StandardCharsets.UTF_8)));}
    private static String sha256Base64(String s)throws Exception{return Base64.getEncoder().encodeToString(MessageDigest.getInstance(ALG).digest(s.getBytes(StandardCharsets.UTF_8)));}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    private static String setHash(List<Capture> cs)throws Exception{List<Capture>x=new ArrayList<>(cs);x.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));StringBuilder b=new StringBuilder();for(Capture c:x)b.append(c.name).append('|').append(c.hash).append('|').append(c.bytes).append('|').append(c.fileCount).append('\n');return hashText(b.toString());}
    static String anonymizeCaptureName(String captureName){if(captureName==null||captureName.trim().isEmpty())return "*";Matcher m=CAPTURE_TIMESTAMP_SUFFIX.matcher(captureName);return m.matches()?"*-"+m.group(1):"*";}
    static String captureHistoryBlock(Capture c,boolean anonymizeNames){
        StringBuilder out=new StringBuilder();
        String displayName=anonymizeNames?anonymizeCaptureName(c.name):c.name;
        out.append("CAPTURA|").append(recordField(displayName))
                .append("\nSTATUS|").append(recordField(c.historyStatus))
                .append("\nHASH_CONTEUDO|").append(recordField(c.contentRootHash.isEmpty()?c.hash:c.contentRootHash))
                .append("\nHASH_INTEGRIDADE_JSON|").append(recordField(c.integrityFileHash.isEmpty()?"indisponivel":c.integrityFileHash))
                .append("\nHASH_FINAL_HISTORICO|").append(recordField(c.historyHash.isEmpty()?"indisponivel":c.historyHash))
                .append("\nEVENTOS|").append(c.historyEvents.length()).append('\n');
        for(int i=0;i<c.historyEvents.length();i++){
            JSONObject e=c.historyEvents.optJSONObject(i);if(e==null)continue;
            out.append("EVENTO|").append(e.optInt("sequence",i+1)).append('|')
                    .append(recordField(e.optString("timestampUtc","sem timestamp"))).append('|')
                    .append(recordField(e.optString("action","ação desconhecida")))
                    .append("\nANTERIOR|").append(recordField(e.optString("previousRecordHash","")))
                    .append("\nRAIZ_EVENTO|").append(recordField(e.optString("contentRootHash","")))
                    .append("\nHASH_EVENTO|").append(recordField(e.optString("recordHash",""))).append('\n');
            JSONArray changes=e.optJSONArray("changes");
            if(changes!=null)for(int j=0;j<changes.length();j++){
                JSONObject change=changes.optJSONObject(j);
                if(change!=null)out.append("ALTERACAO|").append(recordField(change.optString("action","mudança"))).append('|')
                        .append(recordField(change.optString("path",""))).append('|')
                        .append(recordField(change.optString("previousSha256","—"))).append('|')
                        .append(recordField(change.optString("sha256","—"))).append('\n');
            }
        }
        out.append("INVENTARIO_ARQUIVOS|").append(c.items.size());
        for(Item item:c.items)out.append("\nARQUIVO|").append(recordField(item.path)).append('|').append(item.size).append('|')
                .append(recordField(item.expected==null?"—":item.expected)).append('|').append(item.bytes).append('|')
                .append(recordField(item.actual==null?"—":item.actual)).append('|')
                .append(recordField(item.state==null?"desconhecido":item.state));
        return out.toString();
    }
    private static String historyReport(List<Capture> captures,String collectionHash)throws IOException{return historyReport(captures,collectionHash,false);}
    static String publicHistoryReport(List<Capture> captures,String collectionHash)throws IOException{return historyReport(captures,collectionHash,true);}
    private static String historyReport(List<Capture> captures,String collectionHash,boolean anonymizeNames)throws IOException{
        enforceReportBudget(captures);
        StringBuilder out=new StringBuilder("HISTORICO_PUBLICO_V1\nSHA256|").append(recordField(collectionHash)).append("\n");
        List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));
        for(Capture c:ordered){
            out.append('\n').append(captureHistoryBlock(c,anonymizeNames)).append('\n');
        }
        out.append("\nNota|O hash público compara exatamente este registro estável. A integridade dos arquivos também precisa estar consistente localmente. Use uma referência obtida fora da pasta verificada.\n");return out.toString();
    }
    private static String recordField(String s){return s==null?"":s.replace("\\","\\\\").replace("|","\\|").replace("\r","\\r").replace("\n","\\n");}
    private static void writeReports(Path root,List<Capture> cs,String rootHash,String time,String history)throws Exception{
        String publicHistory=publicHistoryReport(cs,rootHash);
        String historyFullHash=hashText(normalizePublicRecord(history));
        String historyPublicHash=hashText(normalizePublicRecord(publicHistory));
        writeReports(root,cs,rootHash,time,history,publicHistory,historyFullHash,historyPublicHash);
    }
    @FunctionalInterface
    private interface ReportWriter{void writeTo(java.io.BufferedWriter out)throws IOException;}
    private static void writeReports(Path root,List<Capture> cs,String rootHash,String time,String history,String publicHistory,String historyFullHash,String historyPublicHash)throws Exception{
        enforceReportBudget(cs);
        StringBuilder reportScript=new StringBuilder();
        reportScript.append("var HISTORY_PUBLIC_HASH='").append(historyPublicHash).append("',HISTORY_FULL_HASH='").append(historyFullHash).append("';");
        reportScript.append("function show(el,text,ok){el.className=ok?'match':'diverge';el.textContent=text;}");
        reportScript.append("function showCmp(el,text,ok){el.className='cmp '+(ok?'match':'diverge');el.textContent=text;}");
        reportScript.append("function sha256Utf8(str){var s=unescape(encodeURIComponent(str)),K=[1116352408,1899447441,3049323471,3921009573,961987163,1508970993,2453635748,2870763221,3624381080,310598401,607225278,1426881987,1925078388,2162078206,2614888103,3248222580,3835390401,4022224774,264347078,604807628,770255983,1249150122,1555081692,1996064986,2554220882,2821834349,2952996808,3210313671,3336571891,3584528711,113926993,338241895,666307205,773529912,1294757372,1396182291,1695183700,1986661051,2177026350,2456956037,2730485921,2820302411,3259730800,3345764771,3516065817,3600352804,4094571909,275423344,430227734,506948616,659060556,883997877,958139571,1322822218,1537002063,1747873779,1955562222,2024104815,2227730452,2361852424,2428436474,2756734187,3204031479,3329325298],H=[1779033703,3144134277,1013904242,2773480762,1359893119,2600822924,528734635,1541459225],w=[],l=s.length,i,j,a,b,c,d,e,f,g,h,t1,t2,S0,S1,ch,maj,s0,s1,r=function(x,n){return(x>>>n)|(x<<(32-n));};for(i=0;i<l;i++)w[i>>2]|=(s.charCodeAt(i)&255)<<(24-(i&3)*8);w[l>>2]|=0x80<<(24-(l&3)*8);var bl=(((l+8)>>6)+1)*16;for(i=0;i<bl;i++)if(!w[i])w[i]=0;w[bl-1]=(l*8)&0xffffffff;w[bl-2]=Math.floor(l/536870912);for(i=0;i<bl;i+=16){var M=w.slice(i,i+16);for(j=16;j<64;j++){s0=r(M[j-15],7)^r(M[j-15],18)^(M[j-15]>>>3);s1=r(M[j-2],17)^r(M[j-2],19)^(M[j-2]>>>10);M[j]=(M[j-16]+s0+M[j-7]+s1)|0;}a=H[0];b=H[1];c=H[2];d=H[3];e=H[4];f=H[5];g=H[6];h=H[7];for(j=0;j<64;j++){S1=r(e,6)^r(e,11)^r(e,25);ch=(e&f)^((~e)&g);t1=(h+S1+ch+K[j]+M[j])|0;S0=r(a,2)^r(a,13)^r(a,22);maj=(a&b)^(a&c)^(b&c);t2=(S0+maj)|0;h=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0;}H[0]=(H[0]+a)|0;H[1]=(H[1]+b)|0;H[2]=(H[2]+c)|0;H[3]=(H[3]+d)|0;H[4]=(H[4]+e)|0;H[5]=(H[5]+f)|0;H[6]=(H[6]+g)|0;H[7]=(H[7]+h)|0;}var out='';for(i=0;i<8;i++)out+=('00000000'+(H[i]>>>0).toString(16)).slice(-8);return out;}");
        reportScript.append("function compareHash(){var v=document.getElementById('publicHash').value.trim().toLowerCase(),r=document.getElementById('comparison');if(!v){r.className='';r.textContent='Informe um hash geral.';return;}if(v===HISTORY_PUBLIC_HASH){show(r,'CONFERE com o Hash geral Público calculado.',true);return;}if(v===HISTORY_FULL_HASH){show(r,'CONFERE com o SHA-256 do histórico completo calculado.',true);return;}show(r,'DIVERGE do Hash geral Público e do histórico completo calculados.',false);}");
        reportScript.append("function extractCaptureBlocks(text){var norm=text.replace(/\\r\\n/g,'\\n').replace(/\\r/g,'\\n'),lines=norm.split('\\n'),blocks=[],cur=null,i,line;for(i=0;i<lines.length;i++){line=lines[i];if(line.indexOf('CAPTURA|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=[line];}else if(line.indexOf('Nota|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=null;break;}else if(cur){cur.push(line);}}if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}return blocks;}");
        reportScript.append("function compareHistory(){var raw=document.getElementById('historyInput').value,rr=document.getElementById('historyResult');if(!raw||!raw.trim()){rr.className='';rr.textContent='Cole o conteúdo de historico-capturas.txt (completo ou público).';document.querySelectorAll('.capture-row .cmp').forEach(function(el){el.className='cmp';el.textContent='Não comparado';});return;}var blocks=extractCaptureBlocks(raw),matched={};blocks.forEach(function(b){matched[sha256Utf8(b)]=true;});var okCount=0,failCount=0;document.querySelectorAll('.capture-row').forEach(function(row){var bf=row.getAttribute('data-block-full'),bp=row.getAttribute('data-block-public'),out=row.querySelector('.cmp'),ok=!!(matched[bf]||matched[bp]);if(ok)okCount++;else failCount++;showCmp(out,ok?'OK':'FALHA',ok);});show(rr,failCount===0?'Histórico confere: '+okCount+'/'+(okCount+failCount)+' capturas OK.':'Divergência ou captura ausente: '+okCount+' OK, '+failCount+' FALHA.',failCount===0);}");
        reportScript.append("function copyValue(b){var t=document.createElement('textarea');t.value=b.getAttribute('data-value');document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}");
        reportScript.append("var btnCmp=document.getElementById('btnCompareHash');if(btnCmp&&btnCmp.addEventListener)btnCmp.addEventListener('click',compareHash);");
        reportScript.append("var btnHist=document.getElementById('btnCompareHistory');if(btnHist&&btnHist.addEventListener)btnHist.addEventListener('click',compareHistory);");
        reportScript.append("var cpBtns=document.querySelectorAll('.copy-btn');for(var ci=0;ci<cpBtns.length;ci++){if(cpBtns[ci]&&cpBtns[ci].addEventListener){(function(b){b.addEventListener('click',function(){copyValue(b);});})(cpBtns[ci]);}}");
        final String scriptStr=reportScript.toString();
        final String reportCsp="default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"+sha256Base64(scriptStr)+"'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
        Path reportDir=root.resolve("integridade");createDirectoriesSafe(root,reportDir);
        Map<Path,ReportWriter> outputs=new LinkedHashMap<>();
        outputs.put(root.resolve("integridade.html"),html->{
            html.write("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"");
            html.write(reportCsp);
            html.write("\"><title>Integridade das capturas</title><style>body{font:16px Arial;margin:2em;color:#20313a}table{border-collapse:collapse;width:100%;margin-bottom:1em}td,th{border:1px solid #bbb;padding:.5em;text-align:left;overflow-wrap:anywhere}.ok{color:green}.bad,.diverge{color:#8b0000;font-weight:700}.match{color:#006400;font-weight:700}input{padding:.5em;width:min(40em,90%)}code{overflow-wrap:anywhere}</style><h1>Relatório de integridade</h1><p>Verificação e relatório gerados em: ");
            html.write(esc(time));
            html.write("</p><p>Hash geral Público: <code id=\"publicSummaryHash\">");
            html.write(historyPublicHash);
            html.write("</code> <button class=\"copy-btn\" data-value=\"");
            html.write(historyPublicHash);
            html.write("\">Copiar hash público</button></p><label>Hash geral Público (ou SHA-256 do histórico completo): <input id=\"publicHash\" placeholder=\"Cole aqui o SHA-256 recebido\"></label> <button id=\"btnCompareHash\">Comparar hash geral</button><p id=\"comparison\">Nenhum hash geral informado.</p>");
            html.write("<label for=\"historyInput\">Cole o histórico de capturas completo ou público (historico-capturas.txt):</label><br><textarea id=\"historyInput\" rows=\"7\" style=\"width:min(70em,95%)\" placeholder=\"HISTORICO_PUBLICO_V1&#10;SHA256|...&#10;&#10;CAPTURA|... (ou CAPTURA|*-YYYYMMDD-HHMMSS)\"></textarea><br><button id=\"btnCompareHistory\">Comparar histórico em todas as capturas</button> <span id=\"historyResult\"></span><table><tr><th>Captura</th><th>Hash calculado</th><th>Arquivos</th><th>Comparação com histórico</th></tr>");
            for(Capture c:cs){
                long good=c.items.stream().filter(i->isHealthyVerifierItemState(i.state)).count();
                String blockFullSha,blockPublicSha;
                try{blockFullSha=hashText(captureHistoryBlock(c,false));blockPublicSha=hashText(captureHistoryBlock(c,true));}
                catch(Exception e){throw new IOException("Falha ao calcular hash de bloco do histórico.",e);}
                html.write("<tr class=\"capture-row\" data-capture=\"");html.write(esc(c.name));html.write("\" data-block-full=\"");html.write(blockFullSha);html.write("\" data-block-public=\"");html.write(blockPublicSha);html.write("\"><td>");html.write(esc(c.name));html.write("</td><td><code>");html.write(c.hash);html.write("</code> <button class=\"copy-btn\" data-value=\"");html.write(c.hash);html.write("\">Copiar hash</button></td><td>");html.write(Long.toString(good));html.write(" íntegros / ");html.write(Integer.toString(c.items.size()));html.write(" verificados<br>Histórico: ");html.write(esc(c.historyStatus));
                if(!c.historyHash.isEmpty()){html.write("<br>Hash do registro: <code>");html.write(esc(c.historyHash));html.write("</code> <button class=\"copy-btn\" data-value=\"");html.write(esc(c.historyHash));html.write("\">Copiar</button>");}
                html.write("</td><td><span class=\"cmp\">Não comparado</span></td></tr>");
            }
            html.write("</table><h2>Arquivos</h2>");
            for(Capture c:cs){
                html.write("<h3>");html.write(esc(c.name));html.write("</h3><table><tr><th>Arquivo</th><th>Estado</th><th>Esperado</th><th>Encontrado</th></tr>");
                for(Item i:c.items){html.write("<tr><td>");html.write(esc(i.path));html.write("</td><td class=\"");html.write(isHealthyVerifierItemState(i.state)?"ok":"bad");html.write("\">");html.write(esc(i.state));html.write("</td><td><code>");html.write(esc(i.expected));html.write("</code></td><td><code>");html.write(esc(i.actual));html.write("</code></td></tr>");}
                html.write("</table>");
            }
            html.write("<p>Este relatório é um retrato desta execução. O hash é um código de comparação, não uma assinatura digital.</p><p>Arquivos: <a href=\"integridade/hashes-capturas.txt\">lista de hashes</a> · <a href=\"integridade/verificacao-integridade.txt\">resumo TXT</a> · <a href=\"integridade/verificacao-integridade.json\">detalhes JSON</a></p><script>");
            html.write(scriptStr);html.write("</script></html>");
        });
        outputs.put(reportDir.resolve("verificacao-integridade.txt"),txt->{
            txt.write("Verificado: ");txt.write(time);txt.write("\nTODAS_AS_CAPTURAS|");txt.write(rootHash);txt.write("\n");
            for(Capture c:cs){txt.write(recordField(c.name));txt.write(" | ");txt.write(c.hash);txt.write("\n");}
        });
        outputs.put(reportDir.resolve("verificacao-integridade.json"),json->{
            json.write("{\"generatedAt\":\"");json.write(jsonEsc(time));json.write("\",\"rootHash\":\"");json.write(rootHash);json.write("\",\"historyPublicHash\":\"");json.write(historyPublicHash);json.write("\",\"captures\":[");
            boolean first=true;
            for(Capture c:cs){
                if(!first)json.write(',');first=false;
                json.write("{\"name\":\"");json.write(jsonEsc(c.name));json.write("\",\"hash\":\"");json.write(c.hash);json.write("\",\"files\":[");
                boolean f=true;
                for(Item i:c.items){if(!f)json.write(',');f=false;json.write("{\"path\":\"");json.write(jsonEsc(i.path));json.write("\",\"status\":\"");json.write(jsonEsc(i.state));json.write("\",\"expected\":\"");json.write(jsonEsc(i.expected==null?"":i.expected));json.write("\",\"actual\":\"");json.write(jsonEsc(i.actual==null?"":i.actual));json.write("\"}");}
                json.write("]}");
            }
            json.write("]}");
        });
        outputs.put(reportDir.resolve("hashes-capturas.txt"),w->w.write(publicHashList(cs,rootHash)));
        outputs.put(reportDir.resolve("historico-capturas.txt"),w->w.write(history));
        atomicBatchStreaming(root,outputs);
    }
    private static void atomic(Path root,Path p,String s)throws IOException{byte[]data=s.getBytes(StandardCharsets.UTF_8);Path target=p.toAbsolutePath().normalize();Path parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+p);createDirectoriesSafe(root,parent);if(isLinkOrReparse(target))throw new IOException("O destino do relatório é link ou junção: "+target);Path t=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");try{try(OutputStream out=Files.newOutputStream(t,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){out.write(data);}if(isLinkOrReparse(target))throw new IOException("O destino do relatório mudou para link ou junção: "+target);moveReplace(t,target);}finally{Files.deleteIfExists(t);}}
    private static void atomicBatch(Map<Path,String> outputs)throws IOException{
        if(outputs==null||outputs.isEmpty())return;
        Path common=null;
        for(Path p:outputs.keySet()){
            Path parent=p.toAbsolutePath().normalize().getParent();
            if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+p);
            if(common==null)common=parent;
            else while(common!=null&&!parent.startsWith(common))common=common.getParent();
        }
        if(common==null)throw new IOException("Caminhos de relatório sem raiz comum.");
        atomicBatch(common,outputs);
    }
    private static void atomicBatch(Path root,Map<Path,String> outputs)throws IOException{
        if(outputs==null||outputs.isEmpty())return;
        Map<Path,ReportWriter> writers=new LinkedHashMap<>();
        for(Map.Entry<Path,String> entry:outputs.entrySet()){final String content=entry.getValue();writers.put(entry.getKey(),w->w.write(content));}
        atomicBatchStreaming(root,writers);
    }
    private static void atomicBatchStreaming(Path root,Map<Path,ReportWriter> outputs)throws IOException{
        if(outputs==null||outputs.isEmpty())return;
        Map<Path,Path> staged=new LinkedHashMap<>(),backups=new LinkedHashMap<>();List<Path> promoted=new ArrayList<>();
        try{
            for(Map.Entry<Path,ReportWriter> entry:outputs.entrySet()){
                Path target=entry.getKey().toAbsolutePath().normalize(),parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+target);
                createDirectoriesSafe(root,parent);if(isLinkOrReparse(target))throw new IOException("O destino do relatório é link ou junção: "+target);
                Path temp=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");staged.put(target,temp);
                try(OutputStream raw=Files.newOutputStream(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
                        java.io.BufferedWriter writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(raw,StandardCharsets.UTF_8),32768)){entry.getValue().writeTo(writer);}
                if(isLinkOrReparse(target))throw new IOException("O destino do relatório mudou para link ou junção: "+target);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("O destino do relatório não é arquivo regular: "+target);Path backup=Files.createTempFile(parent,"."+target.getFileName()+".",".bak");Files.copy(target,backup,StandardCopyOption.REPLACE_EXISTING,LinkOption.NOFOLLOW_LINKS);backups.put(target,backup);}
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet()){
                if(isLinkOrReparse(entry.getKey())||isLinkOrReparse(entry.getValue())||(Files.exists(entry.getKey(),LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(entry.getKey(),LinkOption.NOFOLLOW_LINKS)))throw new IOException("O destino do relatório mudou para link, junção ou diretório inválido: "+entry.getKey());
                moveReplace(entry.getValue(),entry.getKey());promoted.add(entry.getKey());
            }
        }catch(IOException failure){
            for(int i=promoted.size()-1;i>=0;i--){Path target=promoted.get(i),backup=backups.get(target);try{if(backup!=null){moveReplace(backup,target);backups.remove(target);}else Files.deleteIfExists(target);}catch(IOException rollback){failure.addSuppressed(rollback);}}
            throw failure;
        }finally{
            for(Path temp:staged.values())Files.deleteIfExists(temp);
            for(Path backup:backups.values())Files.deleteIfExists(backup);
        }
    }
    private static void moveReplace(Path source,Path target)throws IOException{try{Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}}
    private static void createDirectoriesSafe(Path root,Path directory)throws IOException{
        Path normalizedRoot=root.toAbsolutePath().normalize(),normalizedDir=directory.toAbsolutePath().normalize();
        rejectLinks(normalizedRoot,normalizedDir);
        if(!Files.exists(normalizedRoot,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(normalizedRoot);
        if(isLinkOrReparse(normalizedRoot)||!Files.isDirectory(normalizedRoot,LinkOption.NOFOLLOW_LINKS))throw new IOException("Raiz de saída é link ou junção: "+normalizedRoot);
        if(!normalizedDir.equals(normalizedRoot)){
            Path current=normalizedRoot;
            for(Path part:normalizedRoot.relativize(normalizedDir)){
                current=current.resolve(part);
                if(isLinkOrReparse(current))throw new IOException("Diretórios de saída com link ou junção não são permitidos: "+current);
                if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){try{Files.createDirectory(current);}catch(java.nio.file.FileAlreadyExistsException ignored){}}
                if(isLinkOrReparse(current)||!Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS))throw new IOException("Diretórios de saída com link ou junção não são permitidos: "+current);
            }
        }
        Path realRoot=normalizedRoot.toRealPath();
        if(!normalizedDir.toRealPath().startsWith(realRoot))throw new IOException("Diretório de saída escapa da raiz permitida: "+normalizedDir);
    }
    private static void rejectLinks(Path root,Path directory)throws IOException{
        Path normalizedRoot=root.toAbsolutePath().normalize(),normalizedDir=directory.toAbsolutePath().normalize();
        if(!normalizedDir.startsWith(normalizedRoot))throw new IOException("Diretório de saída fora da raiz permitida: "+normalizedDir);
        Path fsRoot=normalizedRoot.getRoot();
        if(fsRoot!=null&&!normalizedRoot.equals(fsRoot)){
            Path current=fsRoot;
            for(Path part:fsRoot.relativize(normalizedRoot)){
                current=current.resolve(part);
                if(Files.isSymbolicLink(current))throw new IOException("Diretórios de saída com link simbólico não são permitidos: "+current);
            }
        }
        if(isLinkOrReparse(normalizedRoot))throw new IOException("Raiz de saída é link ou junção: "+normalizedRoot);
        if(!normalizedDir.equals(normalizedRoot)){
            Path current=normalizedRoot;
            for(Path part:normalizedRoot.relativize(normalizedDir)){
                current=current.resolve(part);
                if(isLinkOrReparse(current))throw new IOException("Diretórios de saída com link ou junção não são permitidos: "+current);
            }
        }
        if(Files.exists(normalizedDir,LinkOption.NOFOLLOW_LINKS)){
            Path realRoot=normalizedRoot.toRealPath();
            if(!normalizedDir.toRealPath().startsWith(realRoot))throw new IOException("Diretório de saída escapa da raiz permitida: "+normalizedDir);
        }
    }
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String jsonEsc(String value){if(value==null)return "";StringBuilder out=new StringBuilder(value.length()+16);for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.toString();}
}
