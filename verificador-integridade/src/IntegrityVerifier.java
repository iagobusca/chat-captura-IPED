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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
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
    private static final String VERIFIER_VERSION = "1.5.12";
    private static final long MAX_SCAN_ENTRIES = 100_000L;
    private static final int MAX_ORIGIN_WALK_FILES = 50_000;
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
            case "vínculo de origem íntegro":
            case "artefato de relatório íntegro":
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
        boolean auditOrigin;
        boolean showVersion;
        boolean showHelp;
        boolean selfTest;
        boolean tooManyArgs;
        String unknownFlag;
        String originFolder;
        String parentIpedFolder;
        String subcaseFolder;
        String positionalFolder;
    }

    private static CliOptions parseCliOptions(String[] args) {
        CliOptions options = new CliOptions();
        if (args == null) {
            return options;
        }
        for (int i = 0; i < args.length; i++) {
            String rawArg = args[i];
            if (rawArg == null) {
                continue;
            }
            String arg = rawArg.trim();
            if ("--verify-only".equalsIgnoreCase(arg)) {
                options.verifyOnly = true;
            } else if ("--export-history".equalsIgnoreCase(arg)) {
                options.exportHistory = true;
            } else if ("--audit-origin".equalsIgnoreCase(arg)) {
                options.auditOrigin = true;
            } else if ("--origin".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    options.unknownFlag = "--origin (caminho ausente)";
                    break;
                }
                options.originFolder = args[++i];
            } else if ("--parent-iped".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    options.unknownFlag = "--parent-iped (caminho ausente)";
                    break;
                }
                options.parentIpedFolder = args[++i];
            } else if ("--subcase".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    options.unknownFlag = "--subcase (caminho ausente)";
                    break;
                }
                options.subcaseFolder = args[++i];
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
        return "Uso: integridade-verifier [--verify-only [pasta] | --export-history [pasta] | --audit-origin [pasta] --origin <pasta-avila> [--parent-iped <caso-pai>] | --version | --self-test | --help]\n"
                + "  --verify-only [pasta]     Verifica capturas em modo CLI (sem interface grafica)\n"
                + "  --export-history [pasta]  Gera integridade/historico-capturas.txt em modo CLI\n"
                + "  --audit-origin [pasta]    Audita origem Avilla (--origin obrigatorio, --parent-iped opcional)\n"
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
        if ((cli.verifyOnly ? 1 : 0) + (cli.exportHistory ? 1 : 0) + (cli.auditOrigin ? 1 : 0) > 1) {
            System.err.println("As opções --verify-only, --export-history e --audit-origin são mutuamente exclusivas.");
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
        boolean auditOrigin = cli.auditOrigin;
        boolean cliMode = verifyOnly || exportHistory || auditOrigin;
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
            if (auditOrigin) {
                if (cli.originFolder == null || cli.originFolder.trim().isEmpty()) {
                    throw new IOException("Informe --origin <pasta-avila> ao usar --audit-origin.");
                }
                verifyAllBounded(captures);
                enforceReportBudget(captures);
                Path originDir = Paths.get(cli.originFolder).toAbsolutePath().normalize();
                Path parentIpedDir = (cli.parentIpedFolder == null || cli.parentIpedFolder.trim().isEmpty())
                        ? null : Paths.get(cli.parentIpedFolder).toAbsolutePath().normalize();
                Path subcaseDir = (cli.subcaseFolder == null || cli.subcaseFolder.trim().isEmpty())
                        ? null : Paths.get(cli.subcaseFolder).toAbsolutePath().normalize();
                OriginAuditResult res = runOriginAudit(root, captures, originDir, parentIpedDir, subcaseDir);
                System.out.println(res.reportFile);
                System.out.println("SHA-256_AUDITORIA_ORIGEM=" + res.reportSha256);
                System.out.println("ORIGEM_AUDITORIA_STATUS=" + res.overallStatus);
                if (!res.passed) {
                    throw new IOException("Auditoria de origem apontou divergência ou arquivo não localizado: " + res.overallStatus);
                }
                return;
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
                LaudoLogAuditResult logAudit = auditLaudoChainedLog(root);
                if (logAudit.applicable) {
                    System.out.println("LAUDO_LOG_TXT | " + logAudit.status + " | " + logAudit.detail);
                    ok &= logAudit.passed;
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
        if (mode == 2) {
            VerificationBundle auditBundle = runVerificationWithProgress(root, null,
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Auditoria de origem", true, false);
            if (auditBundle == null) {
                showTopMessage(null, "Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta:\n" + root,
                        "Integridade", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
            showOriginAuditWorkspace(null, root, auditBundle.selected);
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
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Verificando capturas", false, true);
        } else {
            bundle = runVerificationWithProgress(root, null,
                    "Verificador de Integridade - v" + VERIFIER_VERSION + " - Integridade Público", true, true);
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
            comparePublicHistory(bundle.selected, bundle.setHash, bundle.history, bundle.historyFullHash, bundle.publicHistory, bundle.historyPublicHash);
        }
        showSummaryDialog(root, bundle.selected, bundle.history, bundle.publicHistory, bundle.historyPublicHash, bundle.historyFile, mode);
        System.exit(0);
    }

    private static VerificationBundle buildVerificationBundle(Path root, List<Capture> selected,
            Consumer<String> statusUpdate, IntConsumer stepUpdate, boolean writeReportsToDisk) throws Exception {
        verifyAllBounded(selected, statusUpdate, stepUpdate);
        if (statusUpdate != null) statusUpdate.accept(writeReportsToDisk ? "Gerando relatórios e hashes..." : "Consolidando hashes e metadados...");
        enforceReportBudget(selected);
        String setHash = setHash(selected);
        String generated = OffsetDateTime.now().toString();
        String history = historyReport(selected, setHash);
        String publicHistory = publicHistoryReport(selected, setHash);
        String historyFullHash = hashText(normalizePublicRecord(history));
        String historyPublicHash = hashText(normalizePublicRecord(publicHistory));
        if (writeReportsToDisk) {
            writeReports(root, selected, setHash, generated, history, publicHistory, historyFullHash, historyPublicHash);
        }
        if (stepUpdate != null) stepUpdate.accept(selected.size() + 1);
        Path historyFile = root.resolve("integridade").resolve("historico-capturas.txt");
        return new VerificationBundle(selected, setHash, history, publicHistory, historyFullHash, historyPublicHash, historyFile);
    }

    private static String truncateStatus(String name) {
        if (name == null) return "";
        return name.length() > 48 ? name.substring(0, 45) + "..." : name;
    }

    private static VerificationBundle runVerificationWithProgress(Path root, List<Capture> preselected,
            String title, boolean autoDiscoverAll, boolean writeReportsToDisk) throws Exception {
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
                            step -> publish("VAL|" + step),
                            writeReportsToDisk);
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
        final LaudoLogAuditResult logAudit = auditLaudoChainedLog(root);
        runOnEdt(()->{
            JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
            panel.add(plainLabel(mode==0?"MODO: INTEGRIDADE LOCAL — arquivos comparados com o registro local.":"MODO: INTEGRIDADE PÚBLICO — referência externa comparada."));
            panel.add(plainLabel("Capturas verificadas: "+selected.size()));
            if(logAudit!=null&&logAudit.applicable){
                JLabel logLbl=plainLabel("Log Cronológico (laudo/Log.txt): "+logAudit.status+" — "+logAudit.detail);
                if(!logAudit.passed)logLbl.setForeground(new java.awt.Color(150,0,0));
                else if(logAudit.hasWarning)logLbl.setForeground(new java.awt.Color(140,85,0));
                else logLbl.setForeground(new java.awt.Color(0,110,50));
                panel.add(logLbl);
            }
            panel.add(Box.createVerticalStrut(10));
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
            JPanel bottomRow=new JPanel(new java.awt.GridLayout(1,1,6,6));
            bottomRow.add(close);
            JPanel actions=new JPanel(new java.awt.BorderLayout(0,6));
            actions.add(gridActions,java.awt.BorderLayout.CENTER);
            actions.add(bottomRow,java.awt.BorderLayout.SOUTH);
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
            JButton local=new JButton("Integridade Local");JButton external=new JButton("Integridade Público");JButton originAudit=new JButton("Auditoria de origem...");
            JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(plainLabel("Escolha a referência para verificar:"));panel.add(Box.createVerticalStrut(10));panel.add(plainLabel("Local: compara os arquivos com o integridade.json da captura."));panel.add(plainLabel("Público: verifica todas as capturas e compara com hash público ou histórico."));panel.add(plainLabel("Auditoria de origem: valida capturas contra a extração Avilla e caso IPED pai (opcional)."));panel.add(Box.createVerticalStrut(12));JPanel buttons=new JPanel();buttons.add(local);buttons.add(external);buttons.add(originAudit);panel.add(buttons);
            JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" ("+System.getProperty("os.name")+")",false);
            frameRef[0]=frame;
            local.addActionListener(e->{selected[0]=0;frame.dispose();latch.countDown();});
            external.addActionListener(e->{selected[0]=1;frame.dispose();latch.countDown();});
            originAudit.addActionListener(e->{selected[0]=2;frame.dispose();latch.countDown();});
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
        return classifyPublicHashMatch(supplied, localFullHash, localPublicHash, "");
    }
    static String classifyPublicHashMatch(String supplied, String localFullHash, String localPublicHash, String localSetHash) {
        String trimmed = supplied == null ? "" : supplied.trim();
        if (trimmed.isEmpty()) return "EMPTY";
        if (!trimmed.matches("(?i)[0-9a-f]{64}")) return "INVALID";
        if (trimmed.equalsIgnoreCase(localFullHash)) return "MATCH_FULL";
        if (trimmed.equalsIgnoreCase(localPublicHash)) return "MATCH_PUBLIC";
        if (localSetHash != null && !localSetHash.isEmpty() && trimmed.equalsIgnoreCase(localSetHash)) return "MATCH_SET";
        return "DIVERGE";
    }
    static String classifyPublicHistoryMatch(String supplied, String localFullHistory, String localPublicHistory) {
        if (supplied == null || supplied.trim().isEmpty()) return "EMPTY";
        String normalized = normalizePublicRecord(supplied);
        if (normalized.equals(normalizePublicRecord(localFullHistory))) return "MATCH_FULL";
        if (normalized.equals(normalizePublicRecord(localPublicHistory))) return "MATCH_PUBLIC";
        return "DIVERGE";
    }
    private static final class ParsedSuppliedBlock {
        final String displayName;
        final String blockSha256;
        final String contentRootHash;
        final String contentDigest;
        final String itemsDigest;
        final int messageCount;
        final int attachmentCount;
        ParsedSuppliedBlock(String displayName, String blockSha256, String contentRootHash,
                String contentDigest, String itemsDigest, int messageCount, int attachmentCount) {
            this.displayName = displayName;
            this.blockSha256 = blockSha256;
            this.contentRootHash = contentRootHash;
            this.contentDigest = contentDigest;
            this.itemsDigest = itemsDigest;
            this.messageCount = messageCount;
            this.attachmentCount = attachmentCount;
        }
    }
    static final class PublicHistoryComparisonResult {
        final String overallKind;
        final int localTotal;
        final int suppliedTotal;
        final int exactCount;
        final int sameRootCount;
        final int contentMatchCount;
        final int divergedCount;
        final int missingLocalCount;
        final int unhealthyLocalCount;
        final String summaryLine;
        final String detailsText;
        PublicHistoryComparisonResult(String overallKind, int localTotal, int suppliedTotal,
                int exactCount, int sameRootCount, int contentMatchCount, int divergedCount,
                int missingLocalCount, int unhealthyLocalCount, String summaryLine, String detailsText) {
            this.overallKind = overallKind;
            this.localTotal = localTotal;
            this.suppliedTotal = suppliedTotal;
            this.exactCount = exactCount;
            this.sameRootCount = sameRootCount;
            this.contentMatchCount = contentMatchCount;
            this.divergedCount = divergedCount;
            this.missingLocalCount = missingLocalCount;
            this.unhealthyLocalCount = unhealthyLocalCount;
            this.summaryLine = summaryLine;
            this.detailsText = detailsText;
        }
        boolean isVerified() {
            return "MATCH_FULL".equals(overallKind)
                    || "MATCH_PUBLIC".equals(overallKind)
                    || "MATCH_CONTENT_EQUIVALENT".equals(overallKind);
        }
    }
    private static String unescapeRecordField(String s) {
        if (s == null || s.indexOf('\\') < 0) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                if (next == 'n') out.append('\n');
                else if (next == 'r') out.append('\r');
                else out.append(next);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
    private static List<String> splitPipeEscaped(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (esc) { cur.append('\\').append(ch); esc = false; continue; }
            if (ch == '\\') { esc = true; continue; }
            if (ch == '|') { parts.add(unescapeRecordField(cur.toString())); cur.setLength(0); continue; }
            cur.append(ch);
        }
        if (esc) cur.append('\\');
        parts.add(unescapeRecordField(cur.toString()));
        return parts;
    }
    private static String computeContentDigest(String captureDisplayName, List<String> entries) {
        if (entries.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder("CAPTURE_CONTENT_V1\n");
        sb.append(anonymizeCaptureName(captureDisplayName)).append('\n');
        for (String e : sorted) sb.append(e).append('\n');
        try { return hashText(sb.toString()); } catch (Exception e) { return ""; }
    }
    static String captureContentDigest(Capture c) {
        if (c == null || !c.error.isEmpty()) return "";
        List<String> entries = new ArrayList<>();
        for (Item item : c.items) {
            if (!isHealthyVerifierItemState(item.state)) return "";
            String p = item.path == null ? "" : item.path;
            String act = item.actual == null ? "" : item.actual;
            if (!act.matches("(?i)[0-9a-f]{64}")) continue;
            if (p.startsWith("mensagem:")) {
                entries.add("M|" + p + "|" + act.toLowerCase(Locale.ROOT));
            } else if (p.startsWith("attachments/")) {
                entries.add("A|" + p + "|" + item.bytes + "|" + act.toLowerCase(Locale.ROOT));
            }
        }
        return computeContentDigest(c.name, entries);
    }
    private static String computeItemsDigest(String captureDisplayName, List<String> allItemLines) {
        if (allItemLines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("CAPTURE_ITEMS_V1\n");
        sb.append(anonymizeCaptureName(captureDisplayName)).append('\n');
        for (String line : allItemLines) sb.append(line).append('\n');
        try { return hashText(sb.toString()); } catch (Exception e) { return ""; }
    }
    static String captureItemsDigest(Capture c) {
        if (c == null || !c.error.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (Item item : c.items) {
            if (!isHealthyVerifierItemState(item.state)) return "";
            String row = "ARQUIVO|" + recordField(item.path) + "|" + item.size + "|"
                    + recordField(item.expected == null ? "—" : item.expected) + "|" + item.bytes + "|"
                    + recordField(item.actual == null ? "—" : item.actual) + "|"
                    + recordField(item.state == null ? "desconhecido" : item.state);
            lines.add(row);
        }
        return computeItemsDigest(c.name, lines);
    }
    private static ParsedSuppliedBlock finalizeSuppliedBlock(List<String> blockLines) throws Exception {
        while (!blockLines.isEmpty() && blockLines.get(blockLines.size() - 1).isEmpty()) {
            blockLines.remove(blockLines.size() - 1);
        }
        String joined = String.join("\n", blockLines);
        String blockSha = hashText(joined);
        String displayName = "";
        String contentRoot = "";
        List<String> entries = new ArrayList<>();
        List<String> rawItemLines = new ArrayList<>();
        int msgCount = 0;
        int attCount = 0;
        boolean broken = false;
        for (String ln : blockLines) {
            if (ln.startsWith("CAPTURA|")) {
                displayName = unescapeRecordField(ln.substring("CAPTURA|".length()));
            } else if (ln.startsWith("HASH_CONTEUDO|")) {
                contentRoot = unescapeRecordField(ln.substring("HASH_CONTEUDO|".length())).trim().toLowerCase(Locale.ROOT);
            } else if (ln.startsWith("ARQUIVO|")) {
                rawItemLines.add(ln);
                List<String> cols = splitPipeEscaped(ln);
                if (cols.size() >= 7) {
                    String path = cols.get(1);
                    String actual = cols.get(5);
                    String state = cols.get(6);
                    long bytes = -1L;
                    try { bytes = Long.parseLong(cols.get(4)); } catch (NumberFormatException ignored) {}
                    if (!isHealthyVerifierItemState(state)) {
                        broken = true;
                    } else if (actual.matches("(?i)[0-9a-f]{64}")) {
                        if (path.startsWith("mensagem:")) {
                            entries.add("M|" + path + "|" + actual.toLowerCase(Locale.ROOT));
                            msgCount++;
                        } else if (path.startsWith("attachments/") && bytes >= 0) {
                            entries.add("A|" + path + "|" + bytes + "|" + actual.toLowerCase(Locale.ROOT));
                            attCount++;
                        }
                    }
                } else {
                    broken = true;
                }
            }
        }
        String digest = broken ? "" : computeContentDigest(displayName, entries);
        String itemsDigest = broken ? "" : computeItemsDigest(displayName, rawItemLines);
        return new ParsedSuppliedBlock(displayName, blockSha, contentRoot, digest, itemsDigest, msgCount, attCount);
    }
    private static List<ParsedSuppliedBlock> parseSuppliedHistoryBlocks(String supplied) throws Exception {
        List<ParsedSuppliedBlock> out = new ArrayList<>();
        String norm = normalizePublicRecord(supplied);
        String[] lines = norm.split("\n", -1);
        List<String> cur = null;
        for (String line : lines) {
            if (line.startsWith("CAPTURA|")) {
                if (cur != null) out.add(finalizeSuppliedBlock(cur));
                cur = new ArrayList<>();
                cur.add(line);
            } else if (line.startsWith("Nota|")) {
                if (cur != null) out.add(finalizeSuppliedBlock(cur));
                cur = null;
                break;
            } else if (cur != null) {
                cur.add(line);
            }
        }
        if (cur != null) out.add(finalizeSuppliedBlock(cur));
        return out;
    }
    static PublicHistoryComparisonResult comparePublicHistoryDetailed(List<Capture> localCaptures, String setHash,
            String localFullHistory, String localPublicHistory, String supplied) throws Exception {
        String exactStatus = classifyPublicHistoryMatch(supplied, localFullHistory, localPublicHistory);
        int localCount = localCaptures == null ? 0 : localCaptures.size();
        if ("EMPTY".equals(exactStatus)) {
            return new PublicHistoryComparisonResult("EMPTY", localCount, 0, 0, 0, 0, 0, 0, 0,
                    "Cole o conteúdo de historico-capturas.txt.", "");
        }
        if ("MATCH_FULL".equals(exactStatus)) {
            return new PublicHistoryComparisonResult("MATCH_FULL", localCount, localCount, localCount, 0, 0, 0, 0, 0,
                    "CONFERE: o texto informado corresponde integralmente ao historico-capturas.txt completo calculado.",
                    "Correspondência integral exata (" + localCount + "/" + localCount + " capturas).");
        }
        if ("MATCH_PUBLIC".equals(exactStatus)) {
            return new PublicHistoryComparisonResult("MATCH_PUBLIC", localCount, localCount, localCount, 0, 0, 0, 0, 0,
                    "CONFERE: o histórico completo público (anonimizado) corresponde integralmente às capturas calculadas.",
                    "Correspondência integral pública exata (" + localCount + "/" + localCount + " capturas).");
        }
        List<ParsedSuppliedBlock> suppliedBlocks = parseSuppliedHistoryBlocks(supplied);
        if (localCaptures == null || localCaptures.isEmpty() || suppliedBlocks.isEmpty()) {
            return new PublicHistoryComparisonResult("DIVERGE", localCount, suppliedBlocks.size(), 0, 0, 0, localCount,
                    suppliedBlocks.size(), 0,
                    "DIVERGE: o texto informado não corresponde ao historico-capturas.txt (completo ou público) calculado.",
                    "Nenhum bloco CAPTURA válido encontrado para comparar.");
        }
        boolean[] usedSupplied = new boolean[suppliedBlocks.size()];
        int exact = 0, sameRoot = 0, contentMatch = 0, diverged = 0, unhealthy = 0;
        StringBuilder details = new StringBuilder();
        details.append("Capturas locais: ").append(localCount).append(" | Blocos na referência: ").append(suppliedBlocks.size()).append("\n\n");
        for (Capture c : localCaptures) {
            boolean captureHealthy = c.error.isEmpty() && isConsistentLocalHistoryStatus(c.historyStatus);
            for (Item it : c.items) {
                if (!isHealthyVerifierItemState(it.state)) { captureHealthy = false; break; }
            }
            String fullSha = hashText(captureHistoryBlock(c, false));
            String pubSha = hashText(captureHistoryBlock(c, true));
            String anonName = anonymizeCaptureName(c.name);
            String localRoot = (c.contentRootHash.isEmpty() ? c.hash : c.contentRootHash).toLowerCase(Locale.ROOT);
            String localContentDigest = captureHealthy ? captureContentDigest(c) : "";
            String localItemsDigest = captureHealthy ? captureItemsDigest(c) : "";

            int matchedIdx = -1;
            String matchTier = "DIVERGE";
            for (int pass = 0; pass < 3 && matchedIdx < 0; pass++) {
                for (int i = 0; i < suppliedBlocks.size(); i++) {
                    if (usedSupplied[i]) continue;
                    ParsedSuppliedBlock sb = suppliedBlocks.get(i);
                    boolean nameMatches = sb.displayName.equals(c.name) || sb.displayName.equals(anonName);
                    if (!nameMatches) continue;
                    if (pass == 0 && (sb.blockSha256.equals(fullSha) || sb.blockSha256.equals(pubSha))) {
                        matchedIdx = i; matchTier = "EXACT_BLOCK"; break;
                    }
                    if (pass == 1 && !localRoot.isEmpty() && sb.contentRootHash.equals(localRoot)
                            && !localItemsDigest.isEmpty() && sb.itemsDigest.equals(localItemsDigest)) {
                        matchedIdx = i; matchTier = "SAME_CONTENT_ROOT"; break;
                    }
                    if (pass == 2 && !localContentDigest.isEmpty() && !sb.contentDigest.isEmpty()
                            && sb.contentDigest.equals(localContentDigest)) {
                        matchedIdx = i; matchTier = "CONTENT_MATCH"; break;
                    }
                }
            }
            if (!captureHealthy) {
                unhealthy++;
                diverged++;
                details.append("[LOCAL_IRREGULAR] ").append(c.name).append(" -> ").append(c.historyStatus).append('\n');
            } else if (matchedIdx >= 0) {
                usedSupplied[matchedIdx] = true;
                ParsedSuppliedBlock sb = suppliedBlocks.get(matchedIdx);
                if ("EXACT_BLOCK".equals(matchTier)) {
                    exact++;
                    details.append("[EXATA] ").append(anonName).append('\n');
                } else if ("SAME_CONTENT_ROOT".equals(matchTier)) {
                    sameRoot++;
                    details.append("[MESMA_RAIZ_CONTEUDO] ").append(anonName).append(" (arquivos idênticos; apenas evento/timestamp distinto)\n");
                } else {
                    contentMatch++;
                    details.append("[CONTEUDO_INTEGRO] ").append(anonName).append(" (")
                            .append(sb.messageCount).append(" mensagens e ").append(sb.attachmentCount)
                            .append(" anexos com hashes idênticos; metadados/origem atualizados)\n");
                }
            } else {
                diverged++;
                details.append("[DIVERGE_OU_AUSENTE] ").append(c.name).append('\n');
            }
        }
        int missingLocal = 0;
        for (int i = 0; i < suppliedBlocks.size(); i++) {
            if (!usedSupplied[i]) {
                missingLocal++;
                details.append("[SOMENTE_NA_REFERENCIA] ").append(suppliedBlocks.get(i).displayName).append('\n');
            }
        }
        int matchedTotal = exact + sameRoot + contentMatch;
        boolean allClean = diverged == 0 && unhealthy == 0 && missingLocal == 0 && matchedTotal == localCount && localCount > 0;
        String kind = allClean ? "MATCH_CONTENT_EQUIVALENT" : "DIVERGE";
        String summary;
        if (allClean) {
            summary = "CONFERE POR CONTEÚDO CRIPTOGRÁFICO: " + matchedTotal + "/" + localCount
                    + " capturas íntegras e equivalentes em mensagens/anexos ("
                    + exact + " exatas, " + sameRoot + " mesma raiz HASH_CONTEUDO, "
                    + contentMatch + " com metadados/origem atualizados).";
        } else {
            summary = "DIVERGE: " + matchedTotal + "/" + localCount + " capturas coincidem ("
                    + exact + " exatas, " + sameRoot + " raiz, " + contentMatch + " conteúdo), "
                    + diverged + " divergiram/sem par, " + missingLocal + " da referência ausentes nesta pasta.";
        }
        return new PublicHistoryComparisonResult(kind, localCount, suppliedBlocks.size(), exact, sameRoot,
                contentMatch, diverged, missingLocal, unhealthy, summary, details.toString());
    }
    private static void comparePublicHistory(List<Capture> selectedCaptures, String localSetHash,
            String localFullHistory, String localFullHash, String localPublicHistory, String localPublicHash) throws Exception {
        final CountDownLatch latch=new CountDownLatch(1);
        final JFrame[] frameRef=new JFrame[1];
        runOnEdt(()->{
            JTextField publicHash=new JTextField(44);
            JTextArea publicRecord=new JTextArea(12,52);
            publicRecord.setLineWrap(false);
            JTextArea result=new JTextArea("Informe o SHA-256 público ou carregue/cole o historico-capturas.txt (normal, anonimizado ou versão anterior).",2,44);
            result.setEditable(false);
            result.setLineWrap(true);
            result.setWrapStyleWord(true);
            result.setOpaque(false);
            result.setForeground(java.awt.Color.DARK_GRAY);
            final String[] lastDetails = { "" };
            JButton detailsBtn=new JButton("Detalhes...");
            detailsBtn.setEnabled(false);

            JButton compareHash=new JButton("Comparar hash público");
            compareHash.addActionListener(e->{
                String status=classifyPublicHashMatch(publicHash.getText(),localFullHash,localPublicHash,localSetHash);
                boolean ok="MATCH_FULL".equals(status)||"MATCH_PUBLIC".equals(status)||"MATCH_SET".equals(status);
                lastDetails[0]="";
                detailsBtn.setEnabled(false);
                if("EMPTY".equals(status))result.setText("Informe o hash público.");
                else if("INVALID".equals(status))result.setText("Formato inválido: informe 64 caracteres hexadecimais.");
                else if("MATCH_FULL".equals(status))result.setText("CONFERE: o hash público corresponde ao historico-capturas.txt completo calculado.");
                else if("MATCH_PUBLIC".equals(status))result.setText("CONFERE: o hash público corresponde ao histórico completo público calculado.");
                else if("MATCH_SET".equals(status))result.setText("CONFERE: o hash informado corresponde ao SHA256 de conjunto do historico-capturas.txt.");
                else result.setText("DIVERGE: o hash público não corresponde ao historico-capturas.txt calculado.");
                result.setForeground("EMPTY".equals(status)?java.awt.Color.DARK_GRAY:ok?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));
            });

            Runnable runRecordCompare = () -> {
                try {
                    PublicHistoryComparisonResult cmp = comparePublicHistoryDetailed(
                            selectedCaptures, localSetHash, localFullHistory, localPublicHistory, publicRecord.getText());
                    boolean ok = cmp.isVerified();
                    result.setText(cmp.summaryLine);
                    result.setForeground("EMPTY".equals(cmp.overallKind)
                            ? java.awt.Color.DARK_GRAY
                            : ok ? new java.awt.Color(0, 100, 0) : new java.awt.Color(139, 0, 0));
                    lastDetails[0] = cmp.detailsText == null ? "" : cmp.detailsText;
                    detailsBtn.setEnabled(!lastDetails[0].isEmpty());
                } catch (Exception ex) {
                    result.setText("ERRO ao comparar histórico: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                    result.setForeground(new java.awt.Color(139, 0, 0));
                    lastDetails[0] = "";
                    detailsBtn.setEnabled(false);
                }
            };

            JButton compareRecord=new JButton("Comparar histórico completo");
            compareRecord.addActionListener(e -> runRecordCompare.run());

            final JFrame frame2=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Integridade Público",true);
            frameRef[0]=frame2;
            frame2.addWindowListener(new WindowAdapter(){
                @Override public void windowClosing(WindowEvent e){latch.countDown();}
            });

            detailsBtn.addActionListener(e -> {
                JTextArea dArea = new JTextArea(lastDetails[0], 18, 68);
                dArea.putClientProperty("html.disable", Boolean.TRUE);
                dArea.setEditable(false);
                dArea.setCaretPosition(0);
                JScrollPane detailsScroll = new JScrollPane(dArea);
                showTopMessage(frame2, detailsScroll, "Detalhes da comparação de histórico", JOptionPane.INFORMATION_MESSAGE);
            });

            JButton loadFileBtn=new JButton("Carregar arquivo .txt...");
            loadFileBtn.addActionListener(e -> {
                JFileChooser ch = new JFileChooser();
                ch.setDialogTitle("Selecione historico-capturas.txt ou referência salva");
                if (ch.showOpenDialog(frame2) == JFileChooser.APPROVE_OPTION) {
                    try {
                        Path chosen = ch.getSelectedFile().toPath().toAbsolutePath().normalize();
                        validateOpenableRegularFile(chosen);
                        String txt = readUtf8Limited(chosen, MAX_HTML_CAPTURE_BYTES);
                        publicRecord.setText(txt);
                        publicRecord.setCaretPosition(0);
                        runRecordCompare.run();
                    } catch (Exception ex) {
                        result.setText("ERRO ao ler arquivo: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                        result.setForeground(new java.awt.Color(139, 0, 0));
                    }
                }
            });

            JPanel hashInputRow=new JPanel(new java.awt.BorderLayout(8,0));
            hashInputRow.add(publicHash,java.awt.BorderLayout.CENTER);
            hashInputRow.add(compareHash,java.awt.BorderLayout.EAST);

            JPanel sectionHash=new JPanel(new java.awt.BorderLayout(0,6));
            sectionHash.add(plainLabel("1. Hash SHA-256 público (de historico-capturas.txt):"),java.awt.BorderLayout.NORTH);
            sectionHash.add(hashInputRow,java.awt.BorderLayout.CENTER);

            JPanel recordButtonRow=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,8,0));
            recordButtonRow.add(detailsBtn);
            recordButtonRow.add(loadFileBtn);
            recordButtonRow.add(compareRecord);

            JPanel sectionRecord=new JPanel(new java.awt.BorderLayout(0,6));
            JPanel recordHeader=new JPanel();
            recordHeader.setLayout(new BoxLayout(recordHeader,BoxLayout.Y_AXIS));
            recordHeader.add(plainLabel("2. Histórico de capturas completo (historico-capturas.txt):"));
            JLabel formatHint=plainLabel("Aceita formato completo (CAPTURA|pasta), público anonimizado (CAPTURA|*-YYYYMMDD-HHMMSS) e versões anteriores compatíveis em conteúdo.");
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
    private static String normalizePublicRecord(String s){String n=s;if(n.startsWith("\uFEFF"))n=n.substring(1);n=n.replace("\r\n","\n").replace('\r','\n');while(n.startsWith("\n"))n=n.substring(1);while(n.endsWith("\n"))n=n.substring(0,n.length()-1);return n+"\n";}
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
                || name.equalsIgnoreCase("laudo")
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
                validateOriginBindingIntegrity(c,format,integrity);
                validateCaptureReportPackageIntegrity(c,integrity);
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
                validateOriginBindingIntegrity(c,format,null);
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
        String itemSha256Meta=sourceMetadata==null?"":sourceMetadata.optString("itemSha256","");
        if(sourceMetadata==null||anchors==null||sourceMetadata.optString("name","").length()>1024||sourceMetadata.optLong("size",-1)<0||sourceMetadata.optString("type","").length()>256||sourceMetadata.optString("itemHash","").length()>128||(!itemSha256Meta.isEmpty()&&!itemSha256Meta.matches("(?i)[0-9a-f]{64}"))||!manifest.optString("sourceFingerprint","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("conversationKey","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("captureRootHash","").matches("(?i)[0-9a-f]{64}"))throw new IOException("Metadados de origem, âncoras ou hashes do manifesto são inválidos.");
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
    private static String htmlCaptureRootHash(JSONArray messages,String htmlSha,String annotationsSha,String reconciliationSha,JSONObject manifest)throws Exception{StringBuilder canonical=new StringBuilder("html-message-v1-root\n");appendHtmlField(canonical,htmlSha);appendHtmlField(canonical,annotationsSha);appendHtmlField(canonical,reconciliationSha);appendHtmlField(canonical,manifest.optString("conversationKey",""));appendHtmlField(canonical,manifest.optString("sourceFingerprint",""));JSONObject source=manifest.optJSONObject("sourceMetadata");appendHtmlField(canonical,source==null?"":source.optString("name",""));appendHtmlField(canonical,source==null?"":Long.toString(source.optLong("size",-1)));appendHtmlField(canonical,source==null?"":source.optString("type",""));appendHtmlField(canonical,source==null?"":source.optString("itemHash",""));appendHtmlField(canonical,source==null?"":Boolean.toString(source.optBoolean("deleted",false)));String itemSha256=source==null?"":source.optString("itemSha256","");if(!itemSha256.isEmpty())appendHtmlField(canonical,itemSha256);appendHtmlField(canonical,manifest.optString("ipedVersion",""));appendHtmlField(canonical,manifest.optString("pluginVersion",""));JSONObject anchors=manifest.optJSONObject("anchors");appendHtmlField(canonical,anchors==null?"":anchors.optString("start",""));appendHtmlField(canonical,anchors==null?"":anchors.optString("end",""));for(int i=0;i<messages.length();i++){JSONObject message=messages.getJSONObject(i);appendHtmlField(canonical,message.optString("key",""));appendHtmlField(canonical,message.optString("messageHash",""));}return hashText(canonical.toString());}
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
        reportScript.append("function extractCaptureBlocks(text){var norm=text.replace(/^\\uFEFF/,'').replace(/\\r\\n/g,'\\n').replace(/\\r/g,'\\n'),lines=norm.split('\\n'),blocks=[],cur=null,i,line;for(i=0;i<lines.length;i++){line=lines[i];if(line.indexOf('CAPTURA|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=[line];}else if(line.indexOf('Nota|')===0){if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}cur=null;break;}else if(cur){cur.push(line);}}if(cur){while(cur.length&&cur[cur.length-1]==='')cur.pop();blocks.push(cur.join('\\n'));}return blocks;}");
        reportScript.append("function blockContentDigest(block){var lines=block.split('\\n'),name='',entries=[],broken=false,i,l,cols,m;for(i=0;i<lines.length;i++){l=lines[i];if(l.indexOf('CAPTURA|')===0){name=l.slice(8);m=/^(?:.*)-(\\d{8}-\\d{6})$/.exec(name);name=m?'*-'+m[1]:'*';}else if(l.indexOf('ARQUIVO|')===0){cols=l.split('|');if(cols.length>=7){var p=cols[1],act=cols[5],st=cols[6],bytes=cols[4];if(!(st==='íntegro'||st==='integro'||st==='anexo íntegro'||st==='mensagem estruturada'||st==='anotações íntegras'||st==='reconciliação por mensagem íntegra'||st==='inalterada desde a captura anterior'||st==='alterada desde a captura anterior'||st==='adicionada desde a captura anterior'||st==='não encontrada na fonte atual'||st==='registro de migração íntegro'||st==='anotações legadas preservadas'||st==='vínculo de origem íntegro'||st==='artefato de relatório íntegro')){broken=true;}else if(/^[0-9a-f]{64}$/i.test(act)){if(p.indexOf('mensagem:')===0)entries.push('M|'+p+'|'+act.toLowerCase());else if(p.indexOf('attachments/')===0)entries.push('A|'+p+'|'+bytes+'|'+act.toLowerCase());}}}}if(broken||!entries.length)return '';entries.sort();return sha256Utf8('CAPTURE_CONTENT_V1\\n'+name+'\\n'+entries.join('\\n')+'\\n');}");
        reportScript.append("function compareHistory(){var raw=document.getElementById('historyInput').value,rr=document.getElementById('historyResult');if(!raw||!raw.trim()){rr.className='';rr.textContent='Cole o conteúdo de historico-capturas.txt (completo ou público).';document.querySelectorAll('.capture-row .cmp').forEach(function(el){el.className='cmp';el.textContent='Não comparado';});return;}var blocks=extractCaptureBlocks(raw),matched={},matchedContent={};blocks.forEach(function(b){matched[sha256Utf8(b)]=true;var cd=blockContentDigest(b);if(cd)matchedContent[cd]=true;});var okCount=0,contentOnlyCount=0,failCount=0;document.querySelectorAll('.capture-row').forEach(function(row){var bf=row.getAttribute('data-block-full'),bp=row.getAttribute('data-block-public'),bc=row.getAttribute('data-block-content'),out=row.querySelector('.cmp'),exact=!!(matched[bf]||matched[bp]),byContent=!exact&&!!(bc&&matchedContent[bc]),ok=exact||byContent;if(ok){okCount++;if(byContent)contentOnlyCount++;}else failCount++;showCmp(out,ok?'OK':'FALHA',ok);});show(rr,failCount===0?(contentOnlyCount>0?'Histórico confere por conteúdo criptográfico: '+okCount+'/'+(okCount+failCount)+' capturas OK ('+contentOnlyCount+' com metadados/origem atualizados).':'Histórico confere: '+okCount+'/'+(okCount+failCount)+' capturas OK.'):'Divergência ou captura ausente: '+okCount+' OK, '+failCount+' FALHA.',failCount===0);}");
        reportScript.append("function copyValue(b){var t=document.createElement('textarea');t.value=b.getAttribute('data-value');document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}");
        reportScript.append("var btnCmp=document.getElementById('btnCompareHash');if(btnCmp&&btnCmp.addEventListener)btnCmp.addEventListener('click',compareHash);");
        reportScript.append("var btnHist=document.getElementById('btnCompareHistory');if(btnHist&&btnHist.addEventListener)btnHist.addEventListener('click',compareHistory);");
        reportScript.append("var cpBtns=document.querySelectorAll('.copy-btn');for(var ci=0;ci<cpBtns.length;ci++){if(cpBtns[ci]&&cpBtns[ci].addEventListener){(function(b){b.addEventListener('click',function(){copyValue(b);});})(cpBtns[ci]);}}");
        final String scriptStr=reportScript.toString();
        final String reportCsp="default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"+sha256Base64(scriptStr)+"'; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
        final LaudoLogAuditResult logAudit=auditLaudoChainedLog(root);
        Path reportDir=root.resolve("integridade");createDirectoriesSafe(root,reportDir);
        Map<Path,ReportWriter> outputs=new LinkedHashMap<>();
        outputs.put(root.resolve("integridade.html"),html->{
            html.write("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta http-equiv=\"Content-Security-Policy\" content=\"");
            html.write(reportCsp);
            html.write("\"><title>Integridade das capturas</title><style>body{font:16px Arial;margin:2em;color:#20313a}table{border-collapse:collapse;width:100%;margin-bottom:1em}td,th{border:1px solid #bbb;padding:.5em;text-align:left;overflow-wrap:anywhere}.ok{color:green}.warn{color:#8a5600;font-weight:700}.bad,.diverge{color:#8b0000;font-weight:700}.match{color:#006400;font-weight:700}input{padding:.5em;width:min(40em,90%)}code{overflow-wrap:anywhere}</style><h1>Relatório de integridade</h1><p>Verificação e relatório gerados em: ");
            html.write(esc(time));
            html.write("</p><p>Hash geral Público: <code id=\"publicSummaryHash\">");
            html.write(historyPublicHash);
            html.write("</code> <button class=\"copy-btn\" data-value=\"");
            html.write(historyPublicHash);
            html.write("\">Copiar hash público</button></p>");
            if(logAudit!=null&&logAudit.applicable){
                String cls=!logAudit.passed?"bad":(logAudit.hasWarning?"warn":"ok");
                html.write("<p><b>Log Cronológico Encadeado (<code>laudo/Log.txt</code> vs <code>laudo/report-plugin.pdf</code>):</b> <span class=\""+cls+"\">");
                html.write(esc(logAudit.status));
                html.write("</span> — ");
                html.write(esc(logAudit.detail));
                html.write("</p>");
            }
            html.write("<label>Hash geral Público (ou SHA-256 do histórico completo): <input id=\"publicHash\" placeholder=\"Cole aqui o SHA-256 recebido\"></label> <button id=\"btnCompareHash\">Comparar hash geral</button><p id=\"comparison\">Nenhum hash geral informado.</p>");
            html.write("<label for=\"historyInput\">Cole o histórico de capturas completo ou público (historico-capturas.txt):</label><br><textarea id=\"historyInput\" rows=\"7\" style=\"width:min(70em,95%)\" placeholder=\"HISTORICO_PUBLICO_V1&#10;SHA256|...&#10;&#10;CAPTURA|... (ou CAPTURA|*-YYYYMMDD-HHMMSS)\"></textarea><br><button id=\"btnCompareHistory\">Comparar histórico em todas as capturas</button> <span id=\"historyResult\"></span><table><tr><th>Captura</th><th>Hash calculado</th><th>Arquivos</th><th>Comparação com histórico</th></tr>");
            for(Capture c:cs){
                long good=c.items.stream().filter(i->isHealthyVerifierItemState(i.state)).count();
                String blockFullSha,blockPublicSha,blockContentSha;
                try{blockFullSha=hashText(captureHistoryBlock(c,false));blockPublicSha=hashText(captureHistoryBlock(c,true));blockContentSha=captureContentDigest(c);}
                catch(Exception e){throw new IOException("Falha ao calcular hash de bloco do histórico.",e);}
                html.write("<tr class=\"capture-row\" data-capture=\"");html.write(esc(c.name));html.write("\" data-block-full=\"");html.write(blockFullSha);html.write("\" data-block-public=\"");html.write(blockPublicSha);html.write("\" data-block-content=\"");html.write(blockContentSha);html.write("\"><td>");html.write(esc(c.name));html.write("</td><td><code>");html.write(c.hash);html.write("</code> <button class=\"copy-btn\" data-value=\"");html.write(c.hash);html.write("\">Copiar hash</button></td><td>");html.write(Long.toString(good));html.write(" íntegros / ");html.write(Integer.toString(c.items.size()));html.write(" verificados<br>Histórico: ");html.write(esc(c.historyStatus));
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
            if(logAudit!=null&&logAudit.applicable){
                txt.write("LAUDO_LOG_TXT|");txt.write(recordField(logAudit.status));txt.write("|");txt.write(recordField(logAudit.detail));txt.write("\n");
            }
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
        cleanupObsoleteReportBakFiles(root,reportDir);
    }
    private static void atomic(Path root,Path p,String s)throws IOException{byte[]data=s.getBytes(StandardCharsets.UTF_8);Path target=p.toAbsolutePath().normalize();Path parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+p);createDirectoriesSafe(root,parent);if(isLinkOrReparse(target))throw new IOException("O destino do relatório é link ou junção: "+target);if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("O destino do relatório não é arquivo regular: "+target);Path t=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");try{try(OutputStream out=Files.newOutputStream(t,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){out.write(data);}if(isLinkOrReparse(target))throw new IOException("O destino do relatório mudou para link ou junção: "+target);moveReplace(t,target);}finally{try{Files.deleteIfExists(t);}catch(IOException ignored){}}}
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
        Map<Path,Path> staged=new LinkedHashMap<>();
        try{
            for(Map.Entry<Path,ReportWriter> entry:outputs.entrySet()){
                Path target=entry.getKey().toAbsolutePath().normalize(),parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+target);
                createDirectoriesSafe(root,parent);if(isLinkOrReparse(target))throw new IOException("O destino do relatório é link ou junção: "+target);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("O destino do relatório não é arquivo regular: "+target);
                Path temp=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");staged.put(target,temp);
                try(OutputStream raw=Files.newOutputStream(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
                        java.io.BufferedWriter writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(raw,StandardCharsets.UTF_8),32768)){entry.getValue().writeTo(writer);}
                if(isLinkOrReparse(target))throw new IOException("O destino do relatório mudou para link ou junção: "+target);
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet()){
                if(isLinkOrReparse(entry.getKey())||isLinkOrReparse(entry.getValue())||(Files.exists(entry.getKey(),LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(entry.getKey(),LinkOption.NOFOLLOW_LINKS)))throw new IOException("O destino do relatório mudou para link, junção ou diretório inválido: "+entry.getKey());
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet()){
                Path target=entry.getKey();
                Path temp=entry.getValue();
                if(isSameReportContent(target,temp)){
                    Files.deleteIfExists(temp);
                    continue;
                }
                moveReplace(temp,target);
            }
        }finally{
            for(Path temp:staged.values()){
                try{Files.deleteIfExists(temp);}catch(IOException ignored){}
            }
        }
    }
    private static final Pattern HTML_GENERATED_AT_PATTERN = Pattern.compile("<p>Verificação e relatório gerados em: [^<]*</p>");
    private static final Pattern OBSOLETE_REPORT_BAK_PATTERN = Pattern.compile("^\\.(integridade\\.html|verificacao-integridade\\.(?:txt|json)|hashes-capturas\\.txt|historico-capturas\\.txt)\\.\\d+\\.bak$");

    private static boolean isSameReportContent(Path target, Path stagedTemp) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || isLinkOrReparse(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        long existingSize = safeRegularFileSize(target);
        long newSize = safeRegularFileSize(stagedTemp);
        String name = target.getFileName() == null ? "" : target.getFileName().toString();
        if ("integridade.html".equalsIgnoreCase(name)) {
            if (existingSize <= MAX_HTML_CAPTURE_BYTES && newSize <= MAX_HTML_CAPTURE_BYTES) {
                String existingHtml = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
                String newHtml = new String(Files.readAllBytes(stagedTemp), StandardCharsets.UTF_8);
                if (existingHtml.equals(newHtml)) {
                    return true;
                }
                String normExisting = HTML_GENERATED_AT_PATTERN.matcher(existingHtml)
                        .replaceFirst("<p>Verificação e relatório gerados em: __TIME__</p>");
                String normNew = HTML_GENERATED_AT_PATTERN.matcher(newHtml)
                        .replaceFirst("<p>Verificação e relatório gerados em: __TIME__</p>");
                return normExisting.equals(normNew);
            }
        }
        if (existingSize != newSize) {
            return false;
        }
        return sameFileBytes(target, stagedTemp);
    }

    private static boolean sameFileBytes(Path a, Path b) throws IOException {
        byte[] bufA = new byte[32768];
        byte[] bufB = new byte[32768];
        try (InputStream inA = Files.newInputStream(a, LinkOption.NOFOLLOW_LINKS);
             InputStream inB = Files.newInputStream(b, LinkOption.NOFOLLOW_LINKS)) {
            while (true) {
                int rA = readFullyChunk(inA, bufA);
                int rB = readFullyChunk(inB, bufB);
                if (rA != rB) {
                    return false;
                }
                if (rA == 0) {
                    return true;
                }
                for (int i = 0; i < rA; i++) {
                    if (bufA[i] != bufB[i]) {
                        return false;
                    }
                }
            }
        }
    }

    private static int readFullyChunk(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        return off;
    }

    private static void cleanupObsoleteReportBakFiles(Path root, Path reportDir) {
        for (Path dir : new Path[] { root, reportDir }) {
            if (dir == null) {
                continue;
            }
            try {
                if (isLinkOrReparse(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                List<Path> candidates = new ArrayList<>();
                try (java.util.stream.Stream<Path> list = Files.list(dir)) {
                    list.forEach(p -> {
                        Path fname = p.getFileName();
                        if (fname != null && OBSOLETE_REPORT_BAK_PATTERN.matcher(fname.toString()).matches()) {
                            candidates.add(p);
                        }
                    });
                }
                for (Path p : candidates) {
                    try {
                        if (!isLinkOrReparse(p) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                            clearReadOnlyAttribute(p);
                            Files.deleteIfExists(p);
                        }
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void clearReadOnlyAttribute(Path target) {
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !isLinkOrReparse(target)) {
                Files.setAttribute(target, "dos:readonly", Boolean.FALSE, LinkOption.NOFOLLOW_LINKS);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void overwriteRegularTargetInPlace(Path source, Path target) throws IOException {
        if (isLinkOrReparse(source) || isLinkOrReparse(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Destino inválido para atualização direta: " + target);
        }
        clearReadOnlyAttribute(target);
        byte[] buf = new byte[32768];
        try (InputStream in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             OutputStream out = Files.newOutputStream(target,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     LinkOption.NOFOLLOW_LINKS)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
        Files.deleteIfExists(source);
    }

    private static void moveReplace(Path source,Path target)throws IOException{
        FileSystemException lastError=null;
        for(int attempt=0;attempt<4;attempt++){
            if(isLinkOrReparse(source)||isLinkOrReparse(target))throw new IOException("O destino do relatório é link ou junção: "+target);
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("O destino do relatório não é arquivo regular: "+target);
                clearReadOnlyAttribute(target);
            }
            try{
                Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                return;
            }catch(AtomicMoveNotSupportedException e){
                try{
                    clearReadOnlyAttribute(target);
                    Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);
                    return;
                }catch(FileSystemException lockEx){
                    lastError=lockEx;
                }
            }catch(java.nio.file.AccessDeniedException deniedEx){
                try{
                    clearReadOnlyAttribute(target);
                    Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);
                    return;
                }catch(FileSystemException lockEx){
                    lastError=lockEx;
                }
                if(attempt==3&&Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)){
                    overwriteRegularTargetInPlace(source,target);
                    return;
                }
            }catch(FileSystemException lockEx){
                lastError=lockEx;
            }
            if(attempt<3){
                try{Thread.sleep(60L);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw new IOException("Gravação de relatório interrompida: "+target,ie);}
            }
        }
        String detail=lastError==null?"":lastError.getReason();
        throw new IOException("Não foi possível gravar o relatório (verifique se o arquivo não está em uso por outro programa): "+target+(detail==null||detail.isEmpty()?"":" ("+detail+")"),lastError);
    }
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
    private static void validateOriginBindingIntegrity(Capture c, JSONObject manifest, JSONObject integrity) throws Exception {
        Path rawBinding = c.dir.resolve("origin-binding.json");
        boolean hasDeclared = manifest.has("originBinding") || manifest.has("originBindingFile") || manifest.has("originBindingSha256");
        if (!hasDeclared) {
            if (Files.exists(rawBinding, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Artefato origin-binding.json sem metadados no manifesto.");
            }
            return;
        }
        String bindingFile = manifest.optString("originBindingFile", "");
        String bindingSha = manifest.optString("originBindingSha256", "");
        JSONObject bindingSummary = manifest.optJSONObject("originBinding");
        Path bindingPath = resolve(c.dir, "origin-binding.json");
        if (!"origin-binding.json".equals(bindingFile) || !bindingSha.matches("(?i)[0-9a-f]{64}")
                || bindingPath == null || isLinkOrReparse(bindingPath) || !Files.isRegularFile(bindingPath, LinkOption.NOFOLLOW_LINKS)
                || safeRegularFileSize(bindingPath) > MAX_METADATA_BYTES || !hash(bindingPath).equalsIgnoreCase(bindingSha)) {
            throw new IOException("Arquivo origin-binding.json ausente, inválido ou divergente do manifesto.");
        }
        JSONObject fullJson = parseBoundedJson(readUtf8Limited(bindingPath), bindingPath);
        if (fullJson.optInt("originBindingVersion", 0) < 1) {
            throw new IOException("Versão inválida em origin-binding.json.");
        }
        if (bindingSummary != null) {
            String[] checkKeys = {"status", "collectionCode", "custodyHashsTxtSha256", "reportPdfSha256"};
            for (String key : checkKeys) {
                if (bindingSummary.has(key)) {
                    String expectedVal = bindingSummary.optString(key, "");
                    if ("custodyHashsTxtSha256".equals(key)) {
                        String actualTxt = findCustodyEntrySha(fullJson.optJSONArray("custodyFiles"), "Hashs.txt");
                        if (!expectedVal.equalsIgnoreCase(actualTxt)) throw new IOException("Resumo de vínculo de origem diverge em Hashs.txt.");
                    } else if ("reportPdfSha256".equals(key)) {
                        String actualPdf = findCustodyEntrySha(fullJson.optJSONArray("custodyFiles"), "report.pdf");
                        if (!expectedVal.equalsIgnoreCase(actualPdf)) throw new IOException("Resumo de vínculo de origem diverge em report.pdf.");
                    } else if (!expectedVal.equals(fullJson.optString(key, ""))) {
                        throw new IOException("Resumo de origin-binding.json diverge do manifesto no campo " + key + ".");
                    }
                }
            }
        }
        if (integrity != null) {
            JSONArray artifacts = integrity.optJSONArray("artifacts");
            JSONObject entry = findIntegrityArtifact(artifacts, "origin-binding.json");
            if (entry == null || entry.optLong("size", -1) != safeRegularFileSize(bindingPath)
                    || !entry.optString("sha256", "").equalsIgnoreCase(bindingSha)) {
                throw new IOException("origin-binding.json não está vinculado ao inventário de integridade.json.");
            }
            addIntegrityArtifactItem(c, "origin-binding.json", entry, "vínculo de origem íntegro");
        } else {
            Item item = new Item();
            item.path = "origin-binding.json";
            item.expected = bindingSha;
            item.actual = bindingSha;
            item.bytes = safeRegularFileSize(bindingPath);
            item.size = item.bytes;
            item.state = "vínculo de origem íntegro";
            c.items.add(item);
        }
        c.allowedFiles.add("origin-binding.json");
        c.fileCount += 1;
        c.bytes += safeRegularFileSize(bindingPath);
        if (c.fileCount > MAX_CAPTURE_FILES || c.bytes > MAX_HTML_CAPTURE_BYTES) {
            throw new IOException("Artefato origin-binding.json excede os limites da captura.");
        }
    }

    private static final Pattern ALLOWED_AUX_REPORT_SUBPATH = Pattern.compile(
            "^relatorio/(?:[A-Za-z0-9._-]+/)?[A-Za-z0-9._-]+\\.(?:htm|html|css)$");
    private static final Set<String> ALLOWED_AUX_HTML_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "html", "head", "meta", "title", "link", "frameset", "frame", "noframes", "body",
            "div", "span", "header", "footer", "nav", "section", "h1", "h2", "h3", "h4",
            "p", "br", "hr", "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
            "a", "img", "b", "strong", "i", "em", "code", "pre", "small"
    )));
    private static final Set<String> ALLOWED_AUX_HTML_ATTRS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lang", "charset", "http-equiv", "content", "rel", "href", "src", "name",
            "cols", "rows", "target", "class", "alt", "title", "colspan", "rowspan"
    )));
    private static final Pattern CSS_URL_PATTERN = Pattern.compile("(?is)url\\s*\\(");

    static boolean isAllowedCaptureReportPackagePath(String relPath) {
        if (relPath == null || relPath.isEmpty() || relPath.contains("..") || relPath.indexOf('\\') >= 0 || relPath.indexOf(':') >= 0) {
            return false;
        }
        return "Lista de Arquivos.csv".equals(relPath)
                || "chat-captura-IPED.log".equals(relPath)
                || "relatorio.htm".equals(relPath)
                || ALLOWED_AUX_REPORT_SUBPATH.matcher(relPath).matches();
    }

    private static void validateCaptureReportPackageIntegrity(Capture c, JSONObject integrity) throws Exception {
        if (integrity == null) {
            return;
        }
        JSONArray artifacts = integrity.optJSONArray("artifacts");
        if (artifacts == null || artifacts.length() == 0) {
            return;
        }
        List<JSONObject> reportEntries = new ArrayList<>();
        for (int i = 0; i < artifacts.length(); i++) {
            JSONObject row = artifacts.optJSONObject(i);
            if (row == null) continue;
            String relPath = row.optString("path", "");
            if (isAllowedCaptureReportPackagePath(relPath)) {
                reportEntries.add(row);
            }
        }
        if (reportEntries.isEmpty()) {
            return;
        }
        reportEntries.sort(Comparator.comparing(x -> x.optString("path", "")));
        for (JSONObject row : reportEntries) {
            String relPath = row.optString("path", "");
            Path file = resolve(c.dir, relPath);
            if (file == null || isLinkOrReparse(file)) {
                throw new IOException("Caminho de relatório inválido ou link não permitido: " + relPath);
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                Item missing = new Item();
                missing.path = relPath;
                missing.expected = row.optString("sha256", "");
                missing.actual = "";
                missing.size = row.optLong("size", -1L);
                missing.bytes = 0L;
                missing.state = "ausente";
                c.items.add(missing);
                c.allowedFiles.add(relPath);
                continue;
            }
            long fileSize = safeRegularFileSize(file);
            if (fileSize > MAX_METADATA_BYTES) {
                throw new IOException("Artefato de relatório excede o limite de leitura segura: " + relPath);
            }
            String lower = relPath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".htm") || lower.endsWith(".html")) {
                String html = readUtf8Limited(file, MAX_METADATA_BYTES);
                validateAuxiliaryReportHtml(c.dir, relPath, html);
            } else if (lower.endsWith(".css")) {
                String css = readUtf8Limited(file, MAX_METADATA_BYTES);
                validateAuxiliaryReportCss(relPath, css);
            }
            addIntegrityArtifactItem(c, relPath, row, "artefato de relatório íntegro");
            c.allowedFiles.add(relPath);
            c.fileCount += 1;
            c.bytes += fileSize;
            if (c.fileCount > MAX_CAPTURE_FILES || c.bytes > MAX_HTML_CAPTURE_BYTES) {
                throw new IOException("Pacote de relatório excede os limites totais da captura.");
            }
        }
        c.items.sort(Comparator.comparing(i -> i.path));
    }

    static void validateAuxiliaryReportCss(String relPath, String css) throws IOException {
        if (css == null || css.indexOf('\0') >= 0) {
            throw new IOException("CSS de relatório inválido (byte nulo): " + relPath);
        }
        String normalized = normalizeCssForSecurityCheck(css);
        if (CSS_FORBIDDEN_CONTENT_PATTERN.matcher(normalized).find()
                || CSS_URL_PATTERN.matcher(normalized).find()) {
            throw new IOException("CSS de relatório contém diretiva ou referência url() não permitida: " + relPath);
        }
    }

    static void validateAuxiliaryReportHtml(Path captureRoot, String relPath, String html) throws IOException {
        if (html == null || html.indexOf('\0') >= 0) {
            throw new IOException("HTML de relatório inválido (byte nulo): " + relPath);
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("<!--") || lower.contains("-->") || lower.contains("<![cdata[") || lower.contains("<?")) {
            throw new IOException("HTML de relatório contém comentários, CDATA ou instruções não permitidas: " + relPath);
        }
        List<HtmlElementToken> tokens = scanHtmlElements(html);
        Path normalizedRoot = captureRoot.toAbsolutePath().normalize();
        Path resolvedFile = normalizedRoot.resolve(relPath).normalize();
        Path parentDir = resolvedFile.getParent();
        if (parentDir == null || !parentDir.startsWith(normalizedRoot)) {
            throw new IOException("Diretório pai inválido no HTML de relatório: " + relPath);
        }
        for (HtmlElementToken token : tokens) {
            if (!ALLOWED_AUX_HTML_TAGS.contains(token.tag)) {
                throw new IOException("Elemento HTML não permitido em relatório auxiliar (<" + token.tag + ">): " + relPath);
            }
            if (token.closing) {
                continue;
            }
            for (Map.Entry<String, String> attr : token.attrs.entrySet()) {
                String attrName = attr.getKey().toLowerCase(Locale.ROOT);
                String attrVal = attr.getValue() == null ? "" : attr.getValue().trim();
                if (attrName.startsWith("on") || !ALLOWED_AUX_HTML_ATTRS.contains(attrName)) {
                    throw new IOException("Atributo HTML não permitido em relatório auxiliar (" + attrName + "): " + relPath);
                }
                if ("meta".equals(token.tag) && "http-equiv".equals(attrName)) {
                    if (!"content-security-policy".equalsIgnoreCase(attrVal)) {
                        throw new IOException("Meta http-equiv não permitido em relatório auxiliar (" + attrVal + "): " + relPath);
                    }
                }
                if ("href".equals(attrName) || "src".equals(attrName)) {
                    validateAuxiliaryRelativeUrl(normalizedRoot, parentDir, relPath, token.tag, attrVal);
                }
            }
            if ("link".equals(token.tag)) {
                String relAttr = token.attrs.get("rel");
                String hrefAttr = token.attrs.get("href");
                if (relAttr == null || !"stylesheet".equalsIgnoreCase(relAttr.trim())
                        || hrefAttr == null || !hrefAttr.trim().toLowerCase(Locale.ROOT).endsWith(".css")) {
                    throw new IOException("Tag <link> em relatório auxiliar deve apontar apenas para folha CSS local: " + relPath);
                }
            }
        }
    }

    private static void validateAuxiliaryRelativeUrl(Path captureRoot, Path htmlParentDir, String relPath, String tag, String rawUrl)
            throws IOException {
        if (rawUrl == null || rawUrl.isEmpty()) {
            throw new IOException("Referência vazia em <" + tag + "> no relatório auxiliar: " + relPath);
        }
        String trimmed = rawUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("//") || lower.indexOf(':') >= 0 || trimmed.indexOf('\\') >= 0
                || trimmed.indexOf('\0') >= 0 || trimmed.startsWith("/")) {
            throw new IOException("URL externa ou esquema não permitido em <" + tag + "> (" + trimmed + "): " + relPath);
        }
        // Only allow %20 space encoding (used for Lista%20de%20Arquivos.csv)
        String decoded = trimmed.replace("%20", " ").replace("%20", " ");
        if (decoded.indexOf('%') >= 0 || decoded.indexOf('#') >= 0 || decoded.indexOf('?') >= 0) {
            throw new IOException("Codificação ou fragmento não permitido em URL do relatório auxiliar (" + trimmed + "): " + relPath);
        }
        Path target = htmlParentDir.resolve(decoded).normalize();
        if (!target.startsWith(captureRoot) || target.equals(captureRoot)) {
            throw new IOException("URL escapa da pasta da captura em <" + tag + "> (" + trimmed + "): " + relPath);
        }
    }

    private static String findCustodyEntrySha(JSONArray custodyFiles, String name) {
        if (custodyFiles == null) return "";
        for (int i = 0; i < custodyFiles.length(); i++) {
            JSONObject row = custodyFiles.optJSONObject(i);
            if (row != null && name.equalsIgnoreCase(row.optString("name", ""))) {
                return row.optString("sha256", "");
            }
        }
        return "";
    }

    private static final class OriginAuditResult {
        final Path reportFile;
        final String reportText;
        final String reportSha256;
        final String overallStatus;
        final boolean passed;
        OriginAuditResult(Path reportFile, String reportText, String reportSha256, String overallStatus, boolean passed) {
            this.reportFile = reportFile;
            this.reportText = reportText;
            this.reportSha256 = reportSha256;
            this.overallStatus = overallStatus;
            this.passed = passed;
        }
    }

    private static final class DualDigest {
        final String sha256;
        final String md5;
        DualDigest(String sha256, String md5) {
            this.sha256 = sha256;
            this.md5 = md5;
        }
    }

    private static DualDigest dualHash(Path p) throws Exception {
        if (p == null || isLinkOrReparse(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Arquivo inválido para auditoria de origem: " + p);
        }
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS), 32768)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) {
                    sha.update(buf, 0, n);
                    md5.update(buf, 0, n);
                }
            }
        }
        return new DualDigest(hex(sha.digest()), hex(md5.digest()));
    }

    private static java.nio.charset.Charset detectTextBomCharset(Path p) throws IOException {
        byte[] bom = new byte[4];
        int r = 0;
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            while (r < 4) {
                int k = in.read(bom, r, 4 - r);
                if (k < 0) break;
                r += k;
            }
        }
        if (r >= 2) {
            int b0 = bom[0] & 0xFF, b1 = bom[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) return StandardCharsets.UTF_16LE;
            if (b0 == 0xFE && b1 == 0xFF) return StandardCharsets.UTF_16BE;
        }
        return StandardCharsets.UTF_8;
    }

    private static final class ParsedHashsTxt {
        String encoding = "";
        String fileSha256 = "";
        String fileMd5 = "";
        int count;
        final Map<String, String> shaToRelPath = new HashMap<>();
        final Set<String> cryptBackups = new LinkedHashSet<>();
    }

    private static ParsedHashsTxt parseOriginHashsTxt(Path hashsTxt) throws Exception {
        ParsedHashsTxt res = new ParsedHashsTxt();
        if (hashsTxt == null) return res;
        DualDigest dh = dualHash(hashsTxt);
        res.fileSha256 = dh.sha256;
        res.fileMd5 = dh.md5;
        java.nio.charset.Charset cs = detectTextBomCharset(hashsTxt);
        res.encoding = cs.name();
        String curAlg = "", curHash = "";
        StringBuilder curPath = new StringBuilder();
        boolean inPath = false;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(Files.newInputStream(hashsTxt, LinkOption.NOFOLLOW_LINKS)), cs), 32768)) {
            String raw;
            while ((raw = br.readLine()) != null) {
                String line = (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') ? raw.substring(1) : raw;
                if (line.trim().isEmpty()) {
                    flushOriginHashEntry(res, curAlg, curHash, curPath.toString());
                    curAlg = ""; curHash = ""; curPath.setLength(0); inPath = false;
                    continue;
                }
                if (inPath && (line.startsWith("            ") || line.startsWith("\t"))) {
                    curPath.append(line.trim());
                    continue;
                }
                String t = line.trim();
                int colon = t.indexOf(':');
                if (colon > 0) {
                    String k = t.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String v = t.substring(colon + 1).trim();
                    if ("algorithm".equals(k)) {
                        flushOriginHashEntry(res, curAlg, curHash, curPath.toString());
                        curAlg = v.toUpperCase(Locale.ROOT); curHash = ""; curPath.setLength(0); inPath = false;
                    } else if ("hash".equals(k)) {
                        curHash = v.toLowerCase(Locale.ROOT); inPath = false;
                    } else if ("path".equals(k)) {
                        curPath.setLength(0); curPath.append(v); inPath = true;
                    }
                }
            }
            flushOriginHashEntry(res, curAlg, curHash, curPath.toString());
        }
        return res;
    }

    private static void flushOriginHashEntry(ParsedHashsTxt res, String alg, String h, String rawPath) {
        if (h == null || !h.matches("[0-9a-f]{64}")) return;
        if (!alg.isEmpty() && !alg.contains("SHA256") && !alg.contains("SHA-256")) return;
        String rel = normalizeOriginRelPath(rawPath);
        if (res.shaToRelPath.putIfAbsent(h, rel) == null) res.count++;
        if (rel.toLowerCase(Locale.ROOT).matches(".*(?:msgstore|wa)(?:-[^/]+)?\\.db\\.crypt\\d+$")) {
            res.cryptBackups.add(h);
        }
    }

    private static String normalizeOriginRelPath(String rawPath) {
        if (rawPath == null) return "";
        String norm = rawPath.trim().replace('\\', '/');
        int idx = norm.toLowerCase(Locale.ROOT).indexOf("/sdcard/");
        if (idx >= 0) return norm.substring(idx + 1);
        Matcher m = Pattern.compile("(?i)(?:^|/)CopyAll/[^/]+/(.+)$").matcher(norm);
        if (m.find()) return m.group(1);
        int last = norm.lastIndexOf('/');
        return last >= 0 ? norm.substring(last + 1) : norm;
    }

    private static Path findChildIgnoreCase(Path dir, String name) throws IOException {
        Path direct = dir.resolve(name);
        if (Files.exists(direct, LinkOption.NOFOLLOW_LINKS)) {
            if (isLinkOrReparse(direct) || !Files.isRegularFile(direct, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Arquivo inválido: " + direct);
            return direct;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase(name)
                        && !isLinkOrReparse(p) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static Path findOriginDatabaseSafe(Path originDir) throws IOException {
        String[] cands = {
            "sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases/msgstore.db",
            "sdcard/com.whatsapp/databases/msgstore.db",
            "databases/msgstore.db",
            "msgstore.db"
        };
        for (String rel : cands) {
            Path p = resolve(originDir, rel);
            if (p != null && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) return p;
        }
        final Path[] found = new Path[1];
        final int[] visited = { 0 };
        Files.walkFileTree(originDir, Collections.<FileVisitOption>emptySet(), 10, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                if (!d.equals(originDir) && (a.isSymbolicLink() || a.isOther() || isLinkOrReparse(d))) return FileVisitResult.SKIP_SUBTREE;
                String n = d.getFileName() == null ? "" : d.getFileName().toString().toLowerCase(Locale.ROOT);
                if ("media".equals(n) || "thumbs".equals(n) || "cache".equals(n)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (++visited[0] > MAX_ORIGIN_WALK_FILES) return FileVisitResult.TERMINATE;
                if (!a.isSymbolicLink() && !a.isOther() && a.isRegularFile() && f.getFileName() != null
                        && "msgstore.db".equalsIgnoreCase(f.getFileName().toString())) {
                    found[0] = f;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
    }

    private static void validateSafeOriginTree(Path rootDir) throws IOException {
        validateRootDirectory(rootDir);
        Path norm = rootDir.toAbsolutePath().normalize();
        Path fsRoot = norm.getRoot();
        if (fsRoot != null && !norm.equals(fsRoot)) {
            Path cur = fsRoot;
            for (Path part : fsRoot.relativize(norm)) {
                cur = cur.resolve(part);
                if (isLinkOrReparse(cur)) throw new IOException("Link simbólico ou junção não permitido no caminho: " + cur);
            }
        }
    }

    private static Set<String> collectExportedInlineBlobs(Path searchRoot, Set<String> targetItemSha256s) throws Exception {
        Set<String> blobs = new HashSet<>();
        if (searchRoot == null || targetItemSha256s == null || targetItemSha256s.isEmpty()) return blobs;
        String rootName = searchRoot.getFileName() == null ? "" : searchRoot.getFileName().toString();
        Path exp = "Exportados".equalsIgnoreCase(rootName) ? searchRoot : searchRoot.resolve("Exportados");
        if (!Files.exists(exp, LinkOption.NOFOLLOW_LINKS) || isLinkOrReparse(exp) || !Files.isDirectory(exp, LinkOption.NOFOLLOW_LINKS)) return blobs;
        Pattern dataUri = Pattern.compile("data:(?:image|audio|video)/[^;\"'\\s]+;base64,([A-Za-z0-9+/=]+)", Pattern.CASE_INSENSITIVE);
        final Path realExp = exp.toRealPath();
        final int[] visited = { 0 };
        Files.walkFileTree(exp, Collections.<FileVisitOption>emptySet(), 6, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                if (!d.equals(exp) && (a.isSymbolicLink() || a.isOther() || isLinkOrReparse(d) || !d.toRealPath().startsWith(realExp))) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                if (++visited[0] > MAX_ORIGIN_WALK_FILES) return FileVisitResult.TERMINATE;
                if (a.isSymbolicLink() || a.isOther() || isLinkOrReparse(f) || !a.isRegularFile()) return FileVisitResult.CONTINUE;
                String name = f.getFileName() == null ? "" : f.getFileName().toString().toLowerCase(Locale.ROOT);
                if ((!name.endsWith(".html") && !name.endsWith(".htm")) || a.size() > MAX_HTML_CAPTURE_BYTES) return FileVisitResult.CONTINUE;
                try {
                    String fileSha256 = hash(f).toLowerCase(Locale.ROOT);
                    if (targetItemSha256s.contains(fileSha256)) {
                        String html = readUtf8Limited(f, MAX_HTML_CAPTURE_BYTES);
                        Matcher m = dataUri.matcher(html);
                        while (m.find()) {
                            try {
                                byte[] decoded = Base64.getDecoder().decode(m.group(1));
                                blobs.add(hex(MessageDigest.getInstance(ALG).digest(decoded)));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
                return FileVisitResult.CONTINUE;
            }
        });
        return blobs;
    }

    private static OriginAuditResult runOriginAudit(Path capturesRoot, List<Capture> captures, Path originDir, Path parentIpedDir) throws Exception {
        return runOriginAudit(capturesRoot, captures, originDir, parentIpedDir, null);
    }

    private static OriginAuditResult runOriginAudit(Path capturesRoot, List<Capture> captures, Path originDir, Path parentIpedDir, Path subcaseDir) throws Exception {
        return runOriginAudit(capturesRoot, captures, originDir, parentIpedDir, subcaseDir, null);
    }

    private static void auditStep(Consumer<String> progress, String msg) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Auditoria de origem cancelada.");
        if (progress != null) progress.accept(msg);
    }

    private static OriginAuditResult runOriginAudit(Path capturesRoot, List<Capture> captures, Path originDir, Path parentIpedDir, Path subcaseDir, Consumer<String> progress) throws Exception {
        auditStep(progress, "Etapa 1/9: validando segurança das pastas (links/junções)...");
        validateSafeOriginTree(originDir);
        if (parentIpedDir != null) validateSafeOriginTree(parentIpedDir);
        if (subcaseDir != null) validateSafeOriginTree(subcaseDir);

        auditStep(progress, "Etapa 2/9: lendo Hashs.txt da origem (pode conter dezenas de milhares de entradas)...");
        Path hashsTxtPath = findChildIgnoreCase(originDir, "Hashs.txt");
        Path hashsAvillaPath = findChildIgnoreCase(originDir, "Hashs.avilla");
        Path reportPdfPath = findChildIgnoreCase(originDir, "report.pdf");
        ParsedHashsTxt hashsIdx = parseOriginHashsTxt(hashsTxtPath);
        auditStep(progress, "Etapa 3/9: calculando SHA-256 de Hashs.avilla e report.pdf...");
        String avillaSha = hashsAvillaPath == null ? "" : hash(hashsAvillaPath);
        String pdfSha = reportPdfPath == null ? "" : hash(reportPdfPath);

        auditStep(progress, "Etapa 4/9: localizando e calculando hash do msgstore.db (arquivo grande, aguarde)...");
        Path dbPath = findOriginDatabaseSafe(originDir);
        DualDigest dbPre = dbPath == null ? null : dualHash(dbPath);
        boolean dbInHashs = dbPre != null && hashsIdx.shaToRelPath.containsKey(dbPre.sha256);

        // Coleta itemHashes (preferindo SHA-256; MD5 apenas informativo) das capturas + anexos unicos
        Set<String> captureHtmlSha256s = new HashSet<>();
        Set<String> captureHtmlMd5s = new HashSet<>();
        Map<String, Long> neededAttachmentSizes = new HashMap<>();
        for (Capture c : captures) {
            Path mp = resolve(c.dir, "manifest.json");
            if (mp != null && Files.isRegularFile(mp, LinkOption.NOFOLLOW_LINKS)) {
                JSONObject m = parseBoundedJson(readUtf8Limited(mp), mp);
                JSONObject sm = m.optJSONObject("sourceMetadata");
                if (sm != null) {
                    String iSha = sm.optString("itemSha256", "").trim().toLowerCase(Locale.ROOT);
                    if (iSha.matches("[0-9a-f]{64}")) captureHtmlSha256s.add(iSha);
                    String ih = sm.optString("itemHash", "").trim().toLowerCase(Locale.ROOT);
                    if (ih.matches("[0-9a-f]{64}")) captureHtmlSha256s.add(ih);
                    else if (ih.matches("[0-9a-f]{32}")) captureHtmlMd5s.add(ih);
                }
            }
            for (Item it : c.items) {
                if (it.path != null && it.path.startsWith("attachments/") && it.actual != null && it.actual.matches("(?i)[0-9a-f]{64}")) {
                    neededAttachmentSizes.putIfAbsent(it.actual.toLowerCase(Locale.ROOT), it.bytes);
                }
            }
        }

        // Busca parent IPED CSV (somente SHA-256 tem valor decisorio; MD5 e apenas informativo) e HTMLs exportados
        auditStep(progress, "Etapa 5/9: correlacionando com Lista de Arquivos.csv do caso (se informado)...");
        Set<String> matchedParentHtmls = new HashSet<>();
        Set<String> matchedParentHtmlsMd5Info = new HashSet<>();
        boolean matchedParentDb = false;
        boolean matchedParentDbMd5Info = false;
        if (parentIpedDir != null) {
            Path csv = resolve(parentIpedDir, "Lista de Arquivos.csv");
            if (csv != null && Files.isRegularFile(csv, LinkOption.NOFOLLOW_LINKS)) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(Files.newInputStream(csv, LinkOption.NOFOLLOW_LINKS)), StandardCharsets.UTF_8), 65536)) {
                    String hdr = br.readLine();
                    if (hdr != null) {
                        char d = hdr.indexOf(';') >= 0 ? ';' : ',';
                        String cleanHdr = (!hdr.isEmpty() && hdr.charAt(0) == '\uFEFF') ? hdr.substring(1) : hdr;
                        List<String> cols = parseCsvLineSimple(cleanHdr, d);
                        int md5Col = csvCol(cols, "md5"), shaCol = csvCol(cols, "sha256", "sha-256"), nameCol = csvCol(cols, "nome", "name");
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.isEmpty()) continue;
                            List<String> row = parseCsvLineSimple(line, d);
                            String md5v = md5Col >= 0 && md5Col < row.size() ? row.get(md5Col).trim().toLowerCase(Locale.ROOT) : "";
                            String shav = shaCol >= 0 && shaCol < row.size() ? row.get(shaCol).trim().toLowerCase(Locale.ROOT) : "";
                            String nmv = nameCol >= 0 && nameCol < row.size() ? row.get(nameCol).trim().toLowerCase(Locale.ROOT) : "";
                            if (!shav.isEmpty() && captureHtmlSha256s.contains(shav)) {
                                matchedParentHtmls.add(shav);
                            } else if (!md5v.isEmpty() && captureHtmlMd5s.contains(md5v)) {
                                matchedParentHtmlsMd5Info.add(md5v);
                            }
                            if (dbPre != null && nmv.contains("msgstore")) {
                                if (!shav.isEmpty() && dbPre.sha256.equalsIgnoreCase(shav)) {
                                    matchedParentDb = true;
                                } else if (!md5v.isEmpty() && dbPre.md5.equalsIgnoreCase(md5v)) {
                                    matchedParentDbMd5Info = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Blobs inline base64 de HTMLs exportados encontrados no caso pai, subcaso ou pasta raiz (somente SHA-256)
        auditStep(progress, "Etapa 6/9: lendo HTMLs exportados e mídias embutidas (base64)...");
        Set<String> exportedInlineBlobs = new HashSet<>();
        if (parentIpedDir != null) exportedInlineBlobs.addAll(collectExportedInlineBlobs(parentIpedDir, captureHtmlSha256s));
        if (subcaseDir != null) exportedInlineBlobs.addAll(collectExportedInlineBlobs(subcaseDir, captureHtmlSha256s));
        exportedInlineBlobs.addAll(collectExportedInlineBlobs(capturesRoot, captureHtmlSha256s));
        if (capturesRoot.getParent() != null) exportedInlineBlobs.addAll(collectExportedInlineBlobs(capturesRoot.getParent(), captureHtmlSha256s));

        // Pre-filtro por tamanho na origem somente para anexos nao resolvidos em Hashs.txt / blobs inline
        Set<Long> missingSizes = new HashSet<>();
        for (Map.Entry<String, Long> e : neededAttachmentSizes.entrySet()) {
            String s = e.getKey();
            if (!hashsIdx.shaToRelPath.containsKey(s) && !exportedInlineBlobs.contains(s) && e.getValue() > 0) {
                missingSizes.add(e.getValue());
            }
        }
        Map<String, String> diskOnlyShaToRel = new HashMap<>();
        final boolean[] originWalkTruncated = { false };
        if (!missingSizes.isEmpty()) {
            auditStep(progress, "Etapa 7/9: procurando na origem " + missingSizes.size() + " anexo(s) fora do Hashs.txt (pré-filtro por tamanho)...");
            Path realOrig = originDir.toRealPath();
            final int MAX_ORIGIN_WALK_DEPTH = 12;
            final int[] scannedCount = new int[1];
            Files.walkFileTree(originDir, Collections.emptySet(), MAX_ORIGIN_WALK_DEPTH, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                    if (!d.equals(originDir) && (a.isSymbolicLink() || a.isOther() || isLinkOrReparse(d) || !d.toRealPath().startsWith(realOrig))) return FileVisitResult.SKIP_SUBTREE;
                    String name = d.getFileName() == null ? "" : d.getFileName().toString().toLowerCase(Locale.ROOT);
                    if ("cache".equals(name) || "code_cache".equals(name) || "app_webview".equals(name) || name.startsWith(".thumb")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    if (++scannedCount[0] > MAX_ORIGIN_WALK_FILES) {
                        originWalkTruncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    if (!a.isSymbolicLink() && !a.isOther() && a.isRegularFile() && missingSizes.contains(a.size())) {
                        try {
                            String fh = hash(f).toLowerCase(Locale.ROOT);
                            if (neededAttachmentSizes.containsKey(fh)) {
                                diskOnlyShaToRel.putIfAbsent(fh, originDir.relativize(f).toString().replace('\\', '/'));
                            }
                        } catch (Exception ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        StringBuilder rpt = new StringBuilder();
        rpt.append("AUDITORIA_ORIGEM_V1\n");
        rpt.append("VERIFICADOR|").append(VERIFIER_VERSION).append("\n");
        rpt.append("DATA_UTC|").append(OffsetDateTime.now().toString()).append("\n");
        rpt.append("N0_HASHS_TXT_SHA256|").append(hashsIdx.fileSha256.isEmpty() ? "ausente" : hashsIdx.fileSha256).append("\n");
        rpt.append("N0_HASHS_TXT_ENCODING|").append(hashsIdx.encoding).append("|ENTRADAS_SHA256|").append(hashsIdx.count).append("\n");
        rpt.append("N0_HASHS_AVILLA_SHA256|").append(avillaSha.isEmpty() ? "ausente" : avillaSha).append("\n");
        rpt.append("N0_REPORT_PDF_SHA256|").append(pdfSha.isEmpty() ? "ausente" : pdfSha).append("\n");
        boolean laudoOriginPdfDiverged = false;
        String[] laudoOriginPdfCandidates = new String[] { "laudo/report.pdf", "laudo/report-origem.pdf" };
        for (String relLaudoPdf : laudoOriginPdfCandidates) {
            Path laudoCopiedPdf = resolve(capturesRoot, relLaudoPdf);
            if (laudoCopiedPdf != null && !isLinkOrReparse(laudoCopiedPdf)
                    && Files.isRegularFile(laudoCopiedPdf, LinkOption.NOFOLLOW_LINKS)) {
                String copiedSha = hash(laudoCopiedPdf);
                if (!pdfSha.isEmpty()) {
                    boolean pdfCopyOk = copiedSha.equalsIgnoreCase(pdfSha);
                    rpt.append("LAUDO_REPORT_ORIGEM_PDF|").append(relLaudoPdf).append('|')
                       .append(pdfCopyOk ? "CONFERE" : "DIVERGE_ORIGEM_ERRADA")
                       .append("|SHA256=").append(copiedSha).append("\n");
                    if (!pdfCopyOk) {
                        laudoOriginPdfDiverged = true;
                    }
                } else {
                    rpt.append("LAUDO_REPORT_ORIGEM_PDF|").append(relLaudoPdf)
                       .append("|PRESENTE_SEM_PDF_NA_ORIGEM|SHA256=").append(copiedSha).append("\n");
                }
            }
        }
        LaudoLogAuditResult laudoLogAudit = auditLaudoChainedLog(capturesRoot);
        if (laudoLogAudit.applicable) {
            rpt.append("LAUDO_LOG_TXT|").append(laudoLogAudit.status).append('|').append(laudoLogAudit.detail).append('\n');
        }
        rpt.append("N1_MSGSTORE_SHA256|").append(dbPre == null ? "ausente" : dbPre.sha256).append("|NO_HASHS_TXT|").append(dbInHashs).append("|BACKUPS_CRYPT_NO_HASHS|").append(hashsIdx.cryptBackups.size()).append("\n");
        if (parentIpedDir != null) {
            rpt.append("N1_CASO_IPED_PAI|HTML_MATCH|").append(matchedParentHtmls.size()).append('/').append(captureHtmlSha256s.size())
               .append("|MSGSTORE_MATCH|").append(matchedParentDb);
            if (!matchedParentHtmlsMd5Info.isEmpty() || matchedParentDbMd5Info) {
                rpt.append("|MD5_INFORMATIVO_SEM_SHA256|html=").append(matchedParentHtmlsMd5Info.size())
                   .append(",msgstore=").append(matchedParentDbMd5Info);
            }
            rpt.append('\n');
        }
        if (originWalkTruncated[0]) {
            rpt.append("SCAN_ORIGEM_TRUNCADO|true|LIMITE_ARQUIVOS=").append(MAX_ORIGIN_WALK_FILES).append('\n');
        }

        boolean allPassed = !hashsIdx.fileSha256.isEmpty() && !laudoOriginPdfDiverged && laudoLogAudit.passed;
        int totalCust = 0, totalEmb = 0, totalOrigNoHash = 0, totalMissing = 0, capIdx = 0;
        int totalN3Diverged = 0, totalBindingDiverged = 0;

        List<Capture> sortedCaps = new ArrayList<>(captures);
        sortedCaps.sort(Comparator.comparing(c -> c.name.toLowerCase(Locale.ROOT)));
        Path n3TempDir = null;
        Path n3TempDb = null;
        boolean sqliteDriverChecked = false;
        boolean sqliteDriverAvailable = false;
        try {
            for (Capture c : sortedCaps) {
                capIdx++;
                auditStep(progress, "Etapa 8/9: auditando captura " + capIdx + "/" + sortedCaps.size() + "...");
                String anon = anonymizeCaptureName(c.name);
                Path bp = resolve(c.dir, "origin-binding.json");
                JSONObject binding = (bp != null && Files.isRegularFile(bp, LinkOption.NOFOLLOW_LINKS)) ? parseBoundedJson(readUtf8Limited(bp), bp) : null;

                String bindingMatchStatus = "SEM_VINCULO_NO_MANIFESTO";
                if (binding != null) {
                    JSONArray cf = binding.optJSONArray("custodyFiles");
                    String bTxt = findCustodyEntrySha(cf, "Hashs.txt");
                    String bAvilla = findCustodyEntrySha(cf, "Hashs.avilla");
                    String bPdf = findCustodyEntrySha(cf, "report.pdf");
                    JSONObject bDb = binding.optJSONObject("sourceDatabase");
                    String bDbSha = bDb == null ? "" : bDb.optString("sha256", "");

                    boolean hasAnchor = false;
                    boolean diverge = false;

                    if (!bTxt.isEmpty()) {
                        hasAnchor = true;
                        if (hashsIdx.fileSha256.isEmpty() || !bTxt.equalsIgnoreCase(hashsIdx.fileSha256)) {
                            diverge = true;
                        }
                    } else if (!hashsIdx.fileSha256.isEmpty()) {
                        diverge = true;
                    }

                    if (!bAvilla.isEmpty()) {
                        hasAnchor = true;
                        if (avillaSha.isEmpty() || !bAvilla.equalsIgnoreCase(avillaSha)) {
                            diverge = true;
                        }
                    } else if (!avillaSha.isEmpty()) {
                        diverge = true;
                    }

                    if (!bDbSha.isEmpty()) {
                        hasAnchor = true;
                        if (dbPre == null || !bDbSha.equalsIgnoreCase(dbPre.sha256)) {
                            diverge = true;
                        }
                    }

                    if (!bPdf.isEmpty()) {
                        if (pdfSha.isEmpty() || !bPdf.equalsIgnoreCase(pdfSha)) {
                            diverge = true;
                        }
                    }

                    if (!hasAnchor) {
                        bindingMatchStatus = "SEM_REFERENCIA_CRIPTO";
                        allPassed = false;
                        totalBindingDiverged++;
                    } else if (diverge) {
                        bindingMatchStatus = "DIVERGE_ORIGEM_ERRADA";
                        allPassed = false;
                        totalBindingDiverged++;
                    } else {
                        bindingMatchStatus = "CONFERE";
                    }
                }

                Set<String> inlineForCap = new HashSet<>(exportedInlineBlobs);
                if (binding != null && binding.optJSONObject("attachmentsOrigin") != null) {
                    JSONArray bi = binding.optJSONObject("attachmentsOrigin").optJSONArray("items");
                    for (int i = 0; bi != null && i < bi.length(); i++) {
                        JSONObject row = bi.optJSONObject(i);
                        if (row != null && "EMBUTIDO_NO_HTML_IPED".equals(row.optString("status", ""))) {
                            String shaInline = row.optString("sha256", "").toLowerCase(Locale.ROOT);
                            if (shaInline.matches("[0-9a-f]{64}")) {
                                inlineForCap.add(shaInline);
                            }
                        }
                    }
                }

                int cCust = 0, cEmb = 0, cOrig = 0, cMiss = 0;
                StringBuilder itemLines = new StringBuilder();
                for (Item it : c.items) {
                    if (it.path == null || !it.path.startsWith("attachments/") || it.actual == null) continue;
                    String s = it.actual.toLowerCase(Locale.ROOT);
                    String st;
                    if (hashsIdx.shaToRelPath.containsKey(s)) { st = "CUSTODIADO_AVILLA"; cCust++; }
                    else if (inlineForCap.contains(s)) { st = "EMBUTIDO_NO_HTML_IPED"; cEmb++; }
                    else if (diskOnlyShaToRel.containsKey(s)) { st = "EM_ORIGEM_SEM_HASH_TXT"; cOrig++; }
                    else { st = "NAO_LOCALIZADO"; cMiss++; }
                    itemLines.append("  ANEXO|").append(s).append('|').append(st).append('\n');
                }
                totalCust += cCust; totalEmb += cEmb; totalOrigNoHash += cOrig; totalMissing += cMiss;
                if (cMiss > 0) allPassed = false;

                rpt.append("\nCAPTURA_AUDITADA|").append(capIdx).append('|').append(recordField(anon))
                   .append("|VINCULO_ORIGEM|").append(bindingMatchStatus)
                   .append("|COLLECTION_CODE|").append(binding == null ? "" : recordField(binding.optString("collectionCode", "")))
                   .append('\n');
                if (binding != null && binding.optJSONObject("custodyTimelineGap") != null) {
                    JSONObject gap = binding.optJSONObject("custodyTimelineGap");
                    rpt.append("GAP_CUSTODIA|").append(recordField(gap.optString("status", "")))
                       .append('|').append(recordField(gap.optString("pericialStatementPtBr", ""))).append('\n');
                }
                IndependentN3Outcome independentN3 = null;
                if (dbPath != null && Files.isRegularFile(dbPath, LinkOption.NOFOLLOW_LINKS) && !isLinkOrReparse(dbPath)) {
                    Path messagesFile = resolve(c.dir, "messages.json");
                    if (messagesFile != null && Files.isRegularFile(messagesFile, LinkOption.NOFOLLOW_LINKS)) {
                        if (!sqliteDriverChecked) {
                            sqliteDriverAvailable = isSqliteDriverAvailable();
                            sqliteDriverChecked = true;
                        }
                        if (sqliteDriverAvailable && n3TempDb == null) {
                            n3TempDir = Files.createTempDirectory("verifier-n3-");
                            n3TempDb = copyOriginSqliteToTempDir(dbPath, n3TempDir);
                        }
                        independentN3 = runIndependentN3VerificationIfPossible(n3TempDb, messagesFile, sqliteDriverAvailable);
                    }
                }
                boolean revalidatedN3Ok = independentN3 != null
                        && "CONFERE".equals(independentN3.status)
                        && independentN3.diverged == 0
                        && independentN3.matchedActive > 0;
                if (binding != null && binding.optJSONObject("n3SqliteAudit") != null) {
                    JSONObject n3 = binding.optJSONObject("n3SqliteAudit");
                    String n3St = n3.optString("status", "");
                    int n3Div = n3.optInt("diverged", 0);
                    boolean prePostMatch = n3.optBoolean("prePostCopyOriginSha256Match", true);
                    if (revalidatedN3Ok && prePostMatch && "COM_DIVERGENCIAS".equalsIgnoreCase(n3St)) {
                        rpt.append("N3_MENSAGENS_PLUGIN|COM_DIVERGENCIAS_REVALIDADO_CONFERE_PELO_VERIFICADOR")
                           .append("|ATIVAS=").append(independentN3.matchedActive)
                           .append("|RECUPERADAS=").append(independentN3.absentOrNonTable)
                           .append("|DIVERGENTES=0\n");
                    } else {
                        if (n3Div > 0
                                || "COM_DIVERGENCIAS".equalsIgnoreCase(n3St)
                                || !prePostMatch
                                || "ALTERACAO_ORIGEM_DETECTADA".equalsIgnoreCase(n3St)
                                || "erro_sqlite_leitura".equalsIgnoreCase(n3St)
                                || "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO".equalsIgnoreCase(n3St)) {
                            allPassed = false;
                            totalN3Diverged += (n3Div > 0 ? n3Div : 1);
                        }
                        rpt.append("N3_MENSAGENS_PLUGIN|").append(recordField(n3St))
                           .append("|ATIVAS=").append(n3.optInt("matchedActive", 0))
                           .append("|RECUPERADAS=").append(n3.optInt("recoveredOrNonTable", 0))
                           .append("|DIVERGENTES=").append(n3Div).append('\n');
                    }
                }
                if (independentN3 != null) {
                    rpt.append("N3_MENSAGENS_VERIFICADOR|").append(independentN3.toReportLine()).append('\n');
                    if (independentN3.isFailure()) {
                        allPassed = false;
                        totalN3Diverged += (independentN3.diverged > 0 ? independentN3.diverged : 1);
                    }
                }
                rpt.append("N2_ANEXOS|CUSTODIADO_AVILLA=").append(cCust)
                   .append("|EMBUTIDO_NO_HTML_IPED=").append(cEmb)
                   .append("|EM_ORIGEM_SEM_HASH_TXT=").append(cOrig)
                   .append("|NAO_LOCALIZADO=").append(cMiss).append('\n');
                rpt.append(itemLines);
            }
        } finally {
            deleteTempDirQuietly(n3TempDir);
        }

        boolean originUntouched = true;
        if (dbPre != null) {
            auditStep(progress, "Etapa 9/9: reconferindo msgstore.db (prova de que a origem não foi alterada) e gravando relatório...");
            DualDigest dbPost = dualHash(dbPath);
            originUntouched = dbPre.sha256.equalsIgnoreCase(dbPost.sha256);
            rpt.append("\nORIGEM_MSGSTORE_INTACTA_POS_AUDITORIA|").append(originUntouched).append('\n');
            if (!originUntouched) allPassed = false;
        }

        List<String> failReasons = new ArrayList<>();
        if (totalMissing > 0) failReasons.add("naoLocalizados=" + totalMissing);
        if (originWalkTruncated[0] && totalMissing > 0) failReasons.add("scanOrigemTruncado=true");
        if (totalOrigNoHash > 0) failReasons.add("origSemHash=" + totalOrigNoHash);
        if (totalN3Diverged > 0) failReasons.add("divergenciasN3=" + totalN3Diverged);
        if (totalBindingDiverged > 0) failReasons.add("divergenciasVinculo=" + totalBindingDiverged);
        if (dbPre != null && !originUntouched) failReasons.add("origemAlterada=true");
        if (hashsIdx.fileSha256.isEmpty()) failReasons.add("hashsTxtAusente=true");

        String overallStatus = allPassed ? "CONFERE (" + totalCust + " Avilla + " + totalEmb + " base64 HTML)"
                : ("ATENCAO (" + (failReasons.isEmpty() ? "falhaDeAuditoria" : String.join(", ", failReasons)) + ")");
        rpt.append("RESUMO_AUDITORIA_ORIGEM|").append(overallStatus).append('\n');

        String reportText = rpt.toString();
        String reportSha = hashText(reportText);
        Path outDir = capturesRoot.resolve("integridade");
        createDirectoriesSafe(capturesRoot, outDir);
        Path outFile = outDir.resolve("auditoria-origem.txt");
        atomic(capturesRoot, outFile, reportText);
        return new OriginAuditResult(outFile, reportText, reportSha, overallStatus, allPassed);
    }

    private static List<String> parseCsvLineSimple(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else q = false;
                } else sb.append(c);
            } else if (c == '"') q = true;
            else if (c == delim) { out.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        out.add(sb.toString());
        return out;
    }

    private static int csvCol(List<String> hdrs, String... keys) {
        for (int i = 0; i < hdrs.size(); i++) {
            String h = hdrs.get(i).trim().toLowerCase(Locale.ROOT);
            for (String k : keys) if (h.equals(k)) return i;
        }
        return -1;
    }

    private static boolean isSqliteDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Path copyOriginSqliteToTempDir(Path originDbPath, Path tempDir) throws IOException {
        Path tempDb = tempDir.resolve("msgstore.db");
        Files.copy(originDbPath, tempDb, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        Path originParent = originDbPath.getParent();
        if (originParent != null) {
            String baseName = originDbPath.getFileName().toString();
            for (String suffix : new String[] { "-wal", "-shm" }) {
                Path sidecar = originParent.resolve(baseName + suffix);
                if (Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS) && !isLinkOrReparse(sidecar)) {
                    Files.copy(sidecar, tempDir.resolve(baseName + suffix), StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
        return tempDb;
    }

    private static void deleteTempDirQuietly(Path dir) {
        if (dir == null) return;
        try {
            final List<Path> all = new ArrayList<>();
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    all.add(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    all.add(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            Collections.reverse(all);
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {}
    }

    static final class IndependentN3Outcome {
        final String status;
        final int matchedActive;
        final int absentOrNonTable;
        final int diverged;
        final String detail;

        IndependentN3Outcome(String status, int matchedActive, int absentOrNonTable, int diverged, String detail) {
            this.status = status;
            this.matchedActive = matchedActive;
            this.absentOrNonTable = absentOrNonTable;
            this.diverged = diverged;
            this.detail = detail == null ? "" : detail;
        }

        boolean isFailure() {
            return diverged > 0
                    || "COM_DIVERGENCIAS".equals(status)
                    || "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO".equals(status)
                    || "ERRO_LEITURA_SQLITE".equals(status)
                    || "SCHEMA_DESCONHECIDO".equals(status);
        }

        String toReportLine() {
            if ("CONFERE".equals(status) || "COM_DIVERGENCIAS".equals(status) || "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO".equals(status)) {
                return status + "|ATIVAS=" + matchedActive + "|RECUPERADAS=" + absentOrNonTable + "|DIVERGENTES=" + diverged;
            }
            if (!detail.isEmpty()) {
                return status + "|" + recordField(detail);
            }
            return status;
        }
    }

    private static IndependentN3Outcome runIndependentN3VerificationIfPossible(Path tempDbPath, Path messagesJsonFile,
            boolean sqliteDriverAvailable) {
        try {
            if (!sqliteDriverAvailable || tempDbPath == null) {
                return new IndependentN3Outcome("DRIVER_SQLITE_INDISPONIVEL_VALIDADO_VIA_BINDING", 0, 0, 0, "");
            }
            JSONObject wrapper = parseBoundedJson(readUtf8Limited(messagesJsonFile), messagesJsonFile);
            JSONArray msgs = wrapper.optJSONArray("messages");
            if (msgs == null || msgs.isEmpty()) return new IndependentN3Outcome("SEM_MENSAGENS_JSON", 0, 0, 0, "");
            Map<String, String> htmlBlockByKey = Collections.emptyMap();
            Path parentDir = messagesJsonFile.getParent();
            if (parentDir != null) {
                Path htmlFile = resolve(parentDir, "whatsapp.html");
                if (htmlFile != null && Files.isRegularFile(htmlFile, LinkOption.NOFOLLOW_LINKS) && !isLinkOrReparse(htmlFile)) {
                    try {
                        htmlBlockByKey = extractCapturedHtmlBlocksByKey(readUtf8Limited(htmlFile));
                    } catch (Exception ignored) {}
                }
            }
            List<Long> positiveIds = new ArrayList<>();
            Map<Long, JSONObject> recById = new HashMap<>();
            int nonPos = 0;
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String domId = m.optString("domId", "").trim();
                try {
                    long id = Long.parseLong(domId);
                    if (id > 0) {
                        positiveIds.add(id);
                        recById.put(id, m);
                    } else nonPos++;
                } catch (NumberFormatException nfe) {
                    nonPos++;
                }
            }
            if (positiveIds.isEmpty()) return new IndependentN3Outcome("SEM_MENSAGENS_TABELA_ATIVA", 0, nonPos, 0, "");
            int matched = 0, diverged = 0, absent = nonPos;

            Class.forName("org.sqlite.JDBC");
            Class<?> dmCls = Class.forName("java.sql.DriverManager");
            Method getConn = dmCls.getMethod("getConnection", String.class);
            AutoCloseable con = (AutoCloseable) getConn.invoke(null, "jdbc:sqlite:" + tempDbPath.toAbsolutePath());
            try {
                boolean hasMsg = checkSqliteTable(con, "message");
                boolean hasMsgs = !hasMsg && checkSqliteTable(con, "messages");
                if (!hasMsg && !hasMsgs) return new IndependentN3Outcome("SCHEMA_DESCONHECIDO", 0, nonPos, 0, "");
                boolean hasMessageType = hasMsg
                        ? checkSqliteColumn(con, "message", "message_type")
                        : checkSqliteColumn(con, "messages", "media_wa_type");
                String sqlCols;
                if (hasMsg) {
                    sqlCols = hasMessageType
                            ? "SELECT _id, text_data, message_type FROM message WHERE _id IN ("
                            : "SELECT _id, text_data, 0 FROM message WHERE _id IN (";
                } else {
                    sqlCols = hasMessageType
                            ? "SELECT _id, data, media_wa_type FROM messages WHERE _id IN ("
                            : "SELECT _id, data, 0 FROM messages WHERE _id IN (";
                }
                Method prepStmt = con.getClass().getMethod("prepareStatement", String.class);
                for (int offset = 0; offset < positiveIds.size(); offset += 400) {
                    List<Long> chunk = positiveIds.subList(offset, Math.min(positiveIds.size(), offset + 400));
                    StringBuilder q = new StringBuilder(sqlCols);
                    for (int k = 0; k < chunk.size(); k++) {
                        if (k > 0) q.append(',');
                        q.append('?');
                    }
                    q.append(')');
                    Map<Long, String> found = new HashMap<>();
                    Map<Long, Integer> foundTypes = new HashMap<>();
                    AutoCloseable ps = (AutoCloseable) prepStmt.invoke(con, q.toString());
                    try {
                        Method setLong = ps.getClass().getMethod("setLong", int.class, long.class);
                        for (int k = 0; k < chunk.size(); k++) setLong.invoke(ps, k + 1, chunk.get(k));
                        Method execQ = ps.getClass().getMethod("executeQuery");
                        AutoCloseable rs = (AutoCloseable) execQ.invoke(ps);
                        try {
                            Method next = rs.getClass().getMethod("next");
                            Method getL = rs.getClass().getMethod("getLong", int.class);
                            Method getS = rs.getClass().getMethod("getString", int.class);
                            Method getI = rs.getClass().getMethod("getInt", int.class);
                            while ((Boolean) next.invoke(rs)) {
                                long id = (Long) getL.invoke(rs, 1);
                                String txt = (String) getS.invoke(rs, 2);
                                int msgType = (Integer) getI.invoke(rs, 3);
                                found.put(id, txt == null ? "" : txt);
                                foundTypes.put(id, msgType);
                            }
                        } finally {
                            rs.close();
                        }
                    } finally {
                        ps.close();
                    }
                    for (Long id : chunk) {
                        if (!found.containsKey(id)) { absent++; continue; }
                        String dbTxt = found.get(id);
                        int sqliteMsgType = foundTypes.containsKey(id) ? foundTypes.get(id) : 0;
                        JSONObject rec = recById.get(id);
                        String capTxt = rec != null ? rec.optString("text", "") : "";
                        String sender = rec != null ? rec.optString("sender", "") : "";
                        String time = rec != null ? rec.optString("timestamp", "") : "";
                        String recType = rec != null ? rec.optString("type", "") : "";
                        String recKey = rec != null ? rec.optString("key", "").toLowerCase(Locale.ROOT) : "";
                        JSONArray atts = rec != null ? rec.optJSONArray("attachments") : null;
                        boolean hasAtt = atts != null && atts.length() > 0;
                        String htmlChunk = !recKey.isEmpty() ? htmlBlockByKey.get(recKey) : null;
                        if (matchesForensicMessageRecord(sqliteMsgType, dbTxt, capTxt, sender, time, htmlChunk, hasAtt, recType)) {
                            matched++;
                        } else {
                            diverged++;
                        }
                    }
                }
            } finally {
                con.close();
            }
            String st;
            if (diverged > 0) st = "COM_DIVERGENCIAS";
            else if (matched > 0) st = "CONFERE";
            else st = "MENSAGENS_NAO_ENCONTRADAS_NO_BANCO";
            return new IndependentN3Outcome(st, matched, absent, diverged, "");
        } catch (ClassNotFoundException noSqlite) {
            return new IndependentN3Outcome("DRIVER_SQLITE_INDISPONIVEL_VALIDADO_VIA_BINDING", 0, 0, 0, "");
        } catch (Exception e) {
            return new IndependentN3Outcome("ERRO_LEITURA_SQLITE", 0, 0, 0, e.getMessage());
        }
    }

    private static boolean checkSqliteColumn(AutoCloseable con, String table, String column) {
        if (!"message".equals(table) && !"messages".equals(table)) return false;
        try {
            Method prep = con.getClass().getMethod("prepareStatement", String.class);
            AutoCloseable ps = (AutoCloseable) prep.invoke(con, "PRAGMA table_info(" + table + ")");
            try {
                Method exec = ps.getClass().getMethod("executeQuery");
                AutoCloseable rs = (AutoCloseable) exec.invoke(ps);
                try {
                    Method next = rs.getClass().getMethod("next");
                    Method getS = rs.getClass().getMethod("getString", String.class);
                    while ((Boolean) next.invoke(rs)) {
                        String col = (String) getS.invoke(rs, "name");
                        if (column.equalsIgnoreCase(col)) return true;
                    }
                    return false;
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkSqliteTable(AutoCloseable con, String table) {
        try {
            Method prep = con.getClass().getMethod("prepareStatement", String.class);
            AutoCloseable ps = (AutoCloseable) prep.invoke(con, "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?");
            try {
                Method setS = ps.getClass().getMethod("setString", int.class, String.class);
                setS.invoke(ps, 1, table);
                Method exec = ps.getClass().getMethod("executeQuery");
                AutoCloseable rs = (AutoCloseable) exec.invoke(ps);
                try {
                    Method next = rs.getClass().getMethod("next");
                    return (Boolean) next.invoke(rs);
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    static boolean matchesForensicMessageText(String dbRaw, String capRaw, String senderRaw, String timeRaw) {
        String db = normalizeWhitespace(dbRaw);
        String cap = normalizeWhitespace(capRaw);
        if (db.isEmpty() && cap.isEmpty()) return true;
        if (db.isEmpty() || cap.isEmpty()) return false;
        if (canonicalTextSha256Equals(db, cap)) return true;
        String stripped = cap;
        String sender = normalizeWhitespace(senderRaw);
        if (!sender.isEmpty() && !sender.equals(stripped) && stripped.startsWith(sender)) {
            stripped = stripped.substring(sender.length()).trim();
        }
        String time = normalizeWhitespace(timeRaw);
        if (!time.isEmpty() && stripped.endsWith(time)) stripped = stripped.substring(0, stripped.length() - time.length()).trim();
        if (canonicalTextSha256Equals(db, stripped)) return true;
        String noTimeSuffix = stripped.replaceAll("\\s+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?$", "").trim();
        if (canonicalTextSha256Equals(db, noTimeSuffix)) return true;
        String noIsoSuffix = stripped.replaceAll(
                "\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?"
                        + "(?:\\s+Editada\\s+em\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?(?:\\s*[-+]\\d{2}:?\\d{2})?)?$",
                "").trim();
        if (canonicalTextSha256Equals(db, noIsoSuffix)) return true;
        String noTimePrefix = stripped.replaceFirst("^\\d{1,2}:\\d{2}(?::\\d{2})?\\s+", "").trim();
        if (canonicalTextSha256Equals(db, noTimePrefix)) return true;

        String dbNoFmt = stripWhatsAppFormatting(db);
        String strippedNoFmt = stripWhatsAppFormatting(noIsoSuffix);
        if (!dbNoFmt.isEmpty() && canonicalTextSha256Equals(dbNoFmt, strippedNoFmt)) return true;
        return false;
    }

    static boolean matchesForensicMessageRecord(int sqliteMessageType, String dbRaw, String capRaw,
            String senderRaw, String timeRaw, String htmlChunk, boolean hasAttachment, String recordType) {
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
            return sqliteMessageType != 0 || hasAttachment || isSystem;
        }

        if (htmlChunk != null && !htmlChunk.isEmpty()) {
            String fullHtmlInner = extractFullHtmlBubbleInnerText(htmlChunk);
            boolean capConsistentWithHtml = canonicalTextSha256Equals(cap, fullHtmlInner)
                    || canonicalTextSha256Equals(stripNonAlphanumeric(cap), stripNonAlphanumeric(fullHtmlInner));
            if (capConsistentWithHtml) {
                String cleanedHtml = cleanHtmlBubbleToText(htmlChunk);
                if (matchesForensicMessageText(db, cleanedHtml, "", timeRaw)) {
                    return true;
                }
                String dbNoFmt = stripWhatsAppFormatting(db);
                String cleanedNoFmt = stripWhatsAppFormatting(cleanedHtml);
                if (!dbNoFmt.isEmpty() && canonicalTextSha256Equals(dbNoFmt, cleanedNoFmt)) {
                    return true;
                }
                String dbAlnum = stripNonAlphanumeric(dbNoFmt);
                String cleanedAlnum = stripNonAlphanumeric(cleanedNoFmt);
                if (!dbAlnum.isEmpty() && canonicalTextSha256Equals(dbAlnum, cleanedAlnum)) {
                    return true;
                }
                if (isSystem) {
                    return true;
                }
                if (sqliteMessageType != 0) {
                    if (!dbNoFmt.isEmpty()
                            && (cleanedNoFmt.contains(dbNoFmt) || (!dbAlnum.isEmpty() && cleanedAlnum.contains(dbAlnum)))) {
                        return true;
                    }
                    if (sqliteMessageType == 9 && (hasAttachment || htmlChunk.contains("<a"))) {
                        return true;
                    }
                }
            }
            return false;
        }
        String dbNoFmt = stripWhatsAppFormatting(db);
        String capNoFmt = stripWhatsAppFormatting(cap);
        if (!dbNoFmt.isEmpty() && matchesForensicMessageText(dbNoFmt, capNoFmt, stripWhatsAppFormatting(senderRaw), timeRaw)) {
            return true;
        }
        if (isSystem) {
            return true;
        }
        if (sqliteMessageType != 0) {
            if (!dbNoFmt.isEmpty() && capNoFmt.contains(dbNoFmt)) {
                return true;
            }
            String dbAlnum = stripNonAlphanumeric(dbNoFmt);
            String capAlnum = stripNonAlphanumeric(capNoFmt);
            if (!dbAlnum.isEmpty() && capAlnum.contains(dbAlnum)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern HTML_BLOCK_START_PATTERN = Pattern.compile(
            "<div\\b[^>]*\\bdata-(?:capture-)?message-key=\"([0-9a-fA-F]{64})\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOJI_IMG_CLASS_PATTERN = Pattern.compile(
            "<img\\b[^>]*\\bclass=\"e([0-9a-fA-F_]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FWD_IMG_CLASS_PATTERN = Pattern.compile(
            "\\bclass=\"[^\"]*\\bfwd\\b[^\"]*\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN_META_REMOVE_PATTERN = Pattern.compile(
            "<span\\b[^>]*\\bclass=\"(?:name|name_call|time|recovered)\"[^>]*>.*?</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_REMOVE_PATTERN = Pattern.compile(
            "<script\\b[^>]*>.*?</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BLOCK_BREAK_TAG_PATTERN = Pattern.compile(
            "<(?:br|/div|/p|/li)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern DIV_OPEN_CLOSE_TOKEN_PATTERN = Pattern.compile(
            "<div\\b[^>]*>|</div>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_ENTITY_PATTERN = Pattern.compile("&#x([0-9a-fA-F]+);");
    private static final Pattern DEC_ENTITY_PATTERN = Pattern.compile("&#(\\d+);");

    static Map<String, String> extractCapturedHtmlBlocksByKey(String capturedHtml) {
        if (capturedHtml == null || capturedHtml.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        Matcher m = HTML_BLOCK_START_PATTERN.matcher(capturedHtml);
        List<int[]> spans = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        while (m.find()) {
            keys.add(m.group(1).toLowerCase(Locale.ROOT));
            spans.add(new int[] { m.start(), m.end() });
        }
        for (int i = 0; i < spans.size(); i++) {
            int bodyStart = spans.get(i)[1];
            int bodyEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : capturedHtml.length();
            if (bodyEnd <= bodyStart) continue;
            String chunk = capturedHtml.substring(bodyStart, bodyEnd);
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
        if (htmlChunk == null || htmlChunk.isEmpty()) return "";
        String s = SCRIPT_REMOVE_PATTERN.matcher(htmlChunk).replaceAll(" ");
        s = decodeIpedEmojiImgs(s);
        boolean isForwarded = FWD_IMG_CLASS_PATTERN.matcher(s).find();
        s = removeBalancedDivByClass(s, "(?:quoteFrom|quoteTo|quote|date)");
        s = SPAN_META_REMOVE_PATTERN.matcher(s).replaceAll(" ");
        s = BLOCK_BREAK_TAG_PATTERN.matcher(s).replaceAll(" ");
        s = ANY_HTML_TAG_PATTERN.matcher(s).replaceAll(" ");
        s = unescapeBasicHtmlEntities(s);
        s = normalizeWhitespace(s);
        if (isForwarded) {
            s = s.replaceFirst("(?i)^(?:Encaminhada|Forwarded)\\s+", "").trim();
        }
        return s;
    }

    static String extractFullHtmlBubbleInnerText(String htmlChunk) {
        if (htmlChunk == null || htmlChunk.isEmpty()) return "";
        String s = removeBalancedDivByClass(htmlChunk, "date");
        s = SCRIPT_REMOVE_PATTERN.matcher(s).replaceAll(" ");
        s = BLOCK_BREAK_TAG_PATTERN.matcher(s).replaceAll(" ");
        s = ANY_HTML_TAG_PATTERN.matcher(s).replaceAll(" ");
        s = unescapeBasicHtmlEntities(s);
        return normalizeWhitespace(s);
    }

    private static String decodeIpedEmojiImgs(String html) {
        Matcher m = EMOJI_IMG_CLASS_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String hexSeq = m.group(1);
            StringBuilder emoji = new StringBuilder();
            String[] parts = hexSeq.split("_");
            for (String p : parts) {
                try {
                    int cp = Integer.parseInt(p, 16);
                    if (Character.isValidCodePoint(cp)) {
                        emoji.append(new String(Character.toChars(cp)));
                    }
                } catch (NumberFormatException ignored) {}
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(emoji.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String removeBalancedDivByClass(String html, String classRegex) {
        Pattern startPat = Pattern.compile("<div\\b[^>]*\\bclass=\"[^\"]*\\b" + classRegex + "\\b[^\"]*\"[^>]*>",
                Pattern.CASE_INSENSITIVE);
        String current = html;
        int guard = 0;
        while (guard++ < 32) {
            Matcher m = startPat.matcher(current);
            if (!m.find()) break;
            int startIdx = m.start();
            int depth = 1;
            int pos = m.end();
            Matcher tok = DIV_OPEN_CLOSE_TOKEN_PATTERN.matcher(current);
            tok.region(pos, current.length());
            int endIdx = current.length();
            while (tok.find()) {
                String tag = tok.group().toLowerCase(Locale.ROOT);
                if (tag.startsWith("</div")) {
                    depth--;
                    if (depth == 0) {
                        endIdx = tok.end();
                        break;
                    }
                } else {
                    depth++;
                }
            }
            current = current.substring(0, startIdx) + " " + current.substring(endIdx);
        }
        return current;
    }

    private static String unescapeBasicHtmlEntities(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        if (s.indexOf("&#") >= 0) {
            Matcher hexM = HEX_ENTITY_PATTERN.matcher(s);
            StringBuffer sbHex = new StringBuffer();
            while (hexM.find()) {
                try {
                    int cp = Integer.parseInt(hexM.group(1), 16);
                    if (Character.isValidCodePoint(cp)) {
                        hexM.appendReplacement(sbHex, Matcher.quoteReplacement(new String(Character.toChars(cp))));
                    }
                } catch (NumberFormatException ignored) {}
            }
            hexM.appendTail(sbHex);
            s = sbHex.toString();

            Matcher decM = DEC_ENTITY_PATTERN.matcher(s);
            StringBuffer sbDec = new StringBuffer();
            while (decM.find()) {
                try {
                    int cp = Integer.parseInt(decM.group(1), 10);
                    if (Character.isValidCodePoint(cp)) {
                        decM.appendReplacement(sbDec, Matcher.quoteReplacement(new String(Character.toChars(cp))));
                    }
                } catch (NumberFormatException ignored) {}
            }
            decM.appendTail(sbDec);
            s = sbDec.toString();
        }
        return s;
    }

    private static String stripWhatsAppFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return normalizeWhitespace(text.replaceAll("[*_~`\\uFE0F]+", ""));
    }

    private static String stripNonAlphanumeric(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static boolean canonicalTextSha256Equals(String canonicalA, String canonicalB) {
        if (canonicalA == null || canonicalB == null || canonicalA.isEmpty() || canonicalB.isEmpty()) return false;
        try {
            MessageDigest md = MessageDigest.getInstance(ALG);
            byte[] da = md.digest(canonicalA.getBytes(StandardCharsets.UTF_8));
            md.reset();
            byte[] db = md.digest(canonicalB.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(da, db);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) return "";
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        return nfc.replaceAll("[\\s\\u00A0\\u2007\\u202F]+", " ").trim();
    }

    private static void showOriginAuditWorkspace(Component parent, Path capturesRoot, List<Capture> selected) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        runOnEdt(() -> openOriginAuditWorkspaceWindow(parent, capturesRoot, selected, latch::countDown));
        latch.await();
    }

    private static void openOriginAuditWorkspaceWindow(Component parent, Path capturesRoot, List<Capture> selected, Runnable onClosed) {
        final JFrame win = newWindow("Verificador de Integridade - v" + VERIFIER_VERSION + " - Auditoria de origem", true);
        final boolean[] closedFlag = { false };
        final Runnable finish = () -> {
            if (!closedFlag[0]) {
                closedFlag[0] = true;
                win.dispose();
                if (onClosed != null) onClosed.run();
            }
        };
        win.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { finish.run(); }
            @Override public void windowClosed(WindowEvent e) { finish.run(); }
        });

        final JTextField originField = new JTextField(42);
        originField.setToolTipText("Obrigatório: pasta custodiada Avilla (com Hashs.txt / report.pdf / msgstore.db)");
        final JButton browseOriginBtn = new JButton("Selecionar...");
        browseOriginBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("Selecione a pasta de origem (Avilla - com Hashs.txt / report.pdf)");
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String cur = originField.getText().trim();
            if (!cur.isEmpty()) {
                File f = new File(cur);
                if (f.isDirectory()) ch.setCurrentDirectory(f);
            }
            if (ch.showOpenDialog(win) == JFileChooser.APPROVE_OPTION) {
                originField.setText(ch.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
            }
        });

        final JTextField caseField = new JTextField(42);
        caseField.setToolTipText("Opcional: pasta do caso IPED pai ou subcaso (com Lista de Arquivos.csv e/ou Exportados)");
        final JButton browseCaseBtn = new JButton("Selecionar...");
        browseCaseBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setDialogTitle("OPCIONAL: Pasta do caso IPED (com Lista de Arquivos.csv / Exportados)");
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String cur = caseField.getText().trim();
            if (!cur.isEmpty()) {
                File f = new File(cur);
                if (f.isDirectory()) ch.setCurrentDirectory(f);
            }
            if (ch.showOpenDialog(win) == JFileChooser.APPROVE_OPTION) {
                caseField.setText(ch.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
            }
        });

        final JButton validateBtn = new JButton("Validar Auditoria");
        validateBtn.setFont(validateBtn.getFont().deriveFont(java.awt.Font.BOLD));

        final JLabel statusLabel = plainLabel("Informe a Pasta de origem e clique em \"Validar Auditoria\" (" + selected.size() + " captura(s) carregadas).");
        statusLabel.setForeground(java.awt.Color.DARK_GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD));

        final JTextArea reportArea = new JTextArea(18, 76);
        reportArea.setEditable(false);
        reportArea.setLineWrap(false);
        reportArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        reportArea.putClientProperty("html.disable", Boolean.TRUE);
        reportArea.setText("Execute \"Validar Auditoria\" para exibir todo o conteúdo da auditoria aqui (leitura e cópia sem precisar exportar).");

        final JButton copyBtn = new JButton("Copiar conteúdo");
        copyBtn.setEnabled(false);
        final JButton exportBtn = new JButton("Exportar Auditoria");
        exportBtn.setEnabled(false);
        final JButton closeBtn = new JButton("Fechar");
        closeBtn.addActionListener(e -> finish.run());

        final OriginAuditResult[] lastRes = new OriginAuditResult[1];
        final Path[] lastOrigin = new Path[1];
        final Path[] lastCase = new Path[1];

        copyBtn.addActionListener(e -> copyToClipboard(win, reportArea.getText(), "Conteúdo da auditoria copiado."));
        exportBtn.addActionListener(e -> {
            OriginAuditResult res = lastRes[0];
            if (res == null) return;
            JFileChooser ch = new JFileChooser(capturesRoot.toFile());
            ch.setDialogTitle("Exportar Auditoria (Auditoria_relatorio.htm)");
            ch.setSelectedFile(capturesRoot.resolve("Auditoria_relatorio.htm").toFile());
            if (ch.showSaveDialog(win) != JFileChooser.APPROVE_OPTION) return;
            try {
                Path target = ch.getSelectedFile().toPath().toAbsolutePath().normalize();
                if (target.getFileName() == null || !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".htm")
                        && !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html")) {
                    target = target.resolveSibling(target.getFileName() + ".htm");
                }
                Path parentDir = target.getParent();
                if (parentDir == null) throw new IOException("Pasta de destino inválida para Auditoria_relatorio.htm.");
                validateSafeOriginTree(parentDir);
                if (isLinkOrReparse(target)) throw new IOException("Link simbólico ou junção não permitido: " + target);
                String html = buildOriginAuditHtml(res, lastOrigin[0], lastCase[0], capturesRoot, selected.size());
                atomic(parentDir, target, html);
                String htmlSha = hashText(html);
                statusLabel.setForeground(res.passed ? new java.awt.Color(0, 110, 0) : new java.awt.Color(139, 0, 0));
                statusLabel.setText((res.passed ? "VÁLIDO" : "ATENÇÃO") + " — Exportado: " + target.getFileName());
                if (!reportArea.getText().contains(htmlSha)) {
                    reportArea.setText("HTML exportado: " + target + "\nSHA-256 HTML: " + htmlSha + "\n\n" + reportArea.getText());
                    reportArea.setCaretPosition(0);
                }
                openInBrowser(win, target);
            } catch (Exception ex) {
                statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                statusLabel.setText("ERRO ao exportar Auditoria_relatorio.htm: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        });

        validateBtn.addActionListener(e -> {
            final String originTxt = stripSurroundingQuotes(originField.getText());
            final String caseTxt = stripSurroundingQuotes(caseField.getText());
            if (originTxt.isEmpty()) {
                statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                statusLabel.setText("ERRO: Informe a Pasta de origem (Avilla) antes de validar.");
                originField.requestFocusInWindow();
                return;
            }
            final Path originDir;
            final Path caseDir;
            try {
                originDir = Paths.get(originTxt).toAbsolutePath().normalize();
                caseDir = caseTxt.isEmpty() ? null : Paths.get(caseTxt).toAbsolutePath().normalize();
            } catch (InvalidPathException ex) {
                statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                statusLabel.setText("ERRO: Caminho inválido informado: " + ex.getInput());
                return;
            }

            validateBtn.setEnabled(false);
            statusLabel.setForeground(java.awt.Color.DARK_GRAY);
            statusLabel.setText("Conferindo pastas e arquivos obrigatórios para a auditoria... aguarde.");
            win.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

            new SwingWorker<OriginPrecheck, Void>() {
                @Override protected OriginPrecheck doInBackground() {
                    return precheckOriginAuditFolders(originDir, caseDir);
                }
                @Override protected void done() {
                    win.setCursor(java.awt.Cursor.getDefaultCursor());
                    validateBtn.setEnabled(true);
                    OriginPrecheck pc;
                    try {
                        pc = get();
                    } catch (Exception ex) {
                        statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                        statusLabel.setText("ERRO ao conferir as pastas: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                        return;
                    }
                    boolean start = showOriginPrecheckDialog(win, pc);
                    if (!start) {
                        statusLabel.setForeground(pc.canStart() ? java.awt.Color.DARK_GRAY : new java.awt.Color(139, 0, 0));
                        statusLabel.setText(pc.canStart()
                                ? "Pastas válidas. Clique em \"Validar Auditoria\" novamente para começar."
                                : "Pasta(s) inválida(s): corrija o caminho indicado e clique em \"Validar Auditoria\" novamente.");
                        return;
                    }
                    runOriginAuditWithProgressDialog(win, capturesRoot, selected, originDir, caseDir, res -> {
                        lastRes[0] = res;
                        lastOrigin[0] = originDir;
                        lastCase[0] = caseDir;
                        if (res.passed) {
                            statusLabel.setForeground(new java.awt.Color(0, 110, 0));
                            statusLabel.setText("VÁLIDO — Tudo íntegro e válido: " + res.overallStatus);
                        } else {
                            statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                            statusLabel.setText("DIVERGÊNCIA / ATENÇÃO — " + res.overallStatus);
                        }
                        reportArea.setText("SHA-256 (auditoria-origem.txt): " + res.reportSha256 + "\nArquivo salvo em: " + res.reportFile + "\n\n" + res.reportText);
                        reportArea.setCaretPosition(0);
                        copyBtn.setEnabled(true);
                        exportBtn.setEnabled(true);
                    }, err -> {
                        lastRes[0] = null;
                        copyBtn.setEnabled(false);
                        exportBtn.setEnabled(false);
                        statusLabel.setForeground(new java.awt.Color(139, 0, 0));
                        statusLabel.setText(err);
                    });
                }
            }.execute();
        });

        JPanel originRow = new JPanel(new java.awt.BorderLayout(6, 0));
        originRow.add(originField, java.awt.BorderLayout.CENTER);
        originRow.add(browseOriginBtn, java.awt.BorderLayout.EAST);

        JPanel caseRow = new JPanel(new java.awt.BorderLayout(6, 0));
        caseRow.add(caseField, java.awt.BorderLayout.CENTER);
        caseRow.add(browseCaseBtn, java.awt.BorderLayout.EAST);

        JPanel formGrid = new JPanel();
        formGrid.setLayout(new BoxLayout(formGrid, BoxLayout.Y_AXIS));
        JLabel trustChainLabel = plainLabel("Cadeia de confiança: confira o SHA-256 deste verificador (EXE/JAR) contra o GitHub / CHECKSUMS.md ANTES de executar (driver SQLite embutido).");
        trustChainLabel.setForeground(new java.awt.Color(90, 50, 0));
        trustChainLabel.setFont(trustChainLabel.getFont().deriveFont(java.awt.Font.BOLD));
        JPanel trustWrap = new JPanel(new java.awt.BorderLayout());
        trustWrap.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 2));
        trustWrap.add(trustChainLabel, java.awt.BorderLayout.CENTER);
        JPanel r1 = new JPanel(new java.awt.BorderLayout(6, 4));
        r1.add(plainLabel("Pasta de origem:"), java.awt.BorderLayout.NORTH);
        r1.add(originRow, java.awt.BorderLayout.CENTER);
        JPanel r2 = new JPanel(new java.awt.BorderLayout(6, 4));
        r2.add(plainLabel("Pasta do caso (opcional):"), java.awt.BorderLayout.NORTH);
        r2.add(caseRow, java.awt.BorderLayout.CENTER);
        JPanel validateRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 4));
        validateRow.add(validateBtn);

        JPanel statusWrap = new JPanel(new java.awt.BorderLayout());
        statusWrap.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        statusWrap.add(statusLabel, java.awt.BorderLayout.CENTER);

        formGrid.add(trustWrap);
        formGrid.add(r1);
        formGrid.add(Box.createVerticalStrut(6));
        formGrid.add(r2);
        formGrid.add(Box.createVerticalStrut(6));
        formGrid.add(validateRow);
        formGrid.add(statusWrap);

        JPanel bottomBtns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        bottomBtns.add(copyBtn);
        bottomBtns.add(exportBtn);
        bottomBtns.add(closeBtn);

        JPanel rootPanel = new JPanel(new java.awt.BorderLayout(8, 8));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        rootPanel.add(formGrid, java.awt.BorderLayout.NORTH);
        rootPanel.add(new JScrollPane(reportArea), java.awt.BorderLayout.CENTER);
        rootPanel.add(bottomBtns, java.awt.BorderLayout.SOUTH);

        win.setContentPane(rootPanel);
        win.pack();
        win.setLocationRelativeTo(parent);
        win.setVisible(true);
    }

    static String buildOriginAuditHtml(OriginAuditResult res, Path originDir, Path caseDir, Path capturesRoot, int capturesCount) {
        StringBuilder h = new StringBuilder(8192);
        h.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">");
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        h.append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline';\">");
        h.append("<title>Auditoria de Origem - Chat Captura IPED</title>");
        h.append("<style>body{font:15px/1.45 Arial,sans-serif;margin:24px;color:#20313a;background:#f8fafb}");
        h.append(".banner{padding:14px 18px;border-radius:6px;margin-bottom:16px;font-weight:700;font-size:16px}");
        h.append(".banner-ok{background:#edf7ed;border:2px solid #2e7d32;color:#1b5e20}");
        h.append(".banner-bad{background:#fdecea;border:2px solid #8b0000;color:#8b0000}");
        h.append("table{border-collapse:collapse;width:100%;margin:10px 0 18px;background:#fff}");
        h.append("th,td{border:1px solid #cbd5e1;padding:7px 10px;text-align:left;overflow-wrap:anywhere;vertical-align:top}");
        h.append("th{background:#eef3f6}code,pre{font-family:Consolas,monospace;font-size:12.5px}");
        h.append("pre{background:#fff;border:1px solid #cbd5e1;padding:12px;overflow:auto;white-space:pre-wrap}");
        h.append(".st-ok{color:#1b5e20;font-weight:700}.st-warn{color:#9a6700;font-weight:700}.st-bad{color:#8b0000;font-weight:700}");
        h.append("</style></head><body>");
        h.append("<h1>Relatório de Auditoria de Origem — Chat Captura IPED</h1>");
        h.append("<div class=\"banner ").append(res.passed ? "banner-ok" : "banner-bad").append("\">");
        h.append(res.passed ? "VÁLIDO — AUDITORIA DE ORIGEM ÍNTEGRA: " : "ATENÇÃO / DIVERGÊNCIA NA AUDITORIA DE ORIGEM: ");
        h.append(esc(res.overallStatus)).append("</div>");

        h.append("<table><tr><th>Campo</th><th>Valor</th></tr>");
        h.append("<tr><td>Verificador</td><td>").append(esc(VERIFIER_VERSION)).append("</td></tr>");
        h.append("<tr><td>Pasta das capturas auditadas</td><td><code>").append(esc(capturesRoot == null ? "" : capturesRoot.toString())).append("</code> (").append(capturesCount).append(" captura(s))</td></tr>");
        h.append("<tr><td>Pasta de origem (Avilla)</td><td><code>").append(esc(originDir == null ? "" : originDir.toString())).append("</code></td></tr>");
        h.append("<tr><td>Pasta do caso (opcional)</td><td><code>").append(esc(caseDir == null ? "(não informada)" : caseDir.toString())).append("</code></td></tr>");
        h.append("<tr><td>Relatório técnico TXT</td><td><code>").append(esc(res.reportFile == null ? "" : res.reportFile.toString())).append("</code></td></tr>");
        h.append("<tr><td>SHA-256 do relatório TXT</td><td><code>").append(esc(res.reportSha256)).append("</code></td></tr>");
        h.append("</table>");

        h.append("<h2>Detalhamento por Linha de Custódia</h2>");
        h.append("<table><tr><th>Registro</th><th>Conteúdo</th></tr>");
        String[] lines = res.reportText.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String trimmed = line.trim();
            int bar = trimmed.indexOf('|');
            String tag = bar > 0 ? trimmed.substring(0, bar) : trimmed;
            String rest = bar > 0 ? trimmed.substring(bar + 1) : "";
            String cls = "";
            boolean isN3DivNonZero = trimmed.contains("DIVERGENTES=") && !trimmed.contains("DIVERGENTES=0");
            if ((trimmed.contains("NAO_LOCALIZADO") && !trimmed.contains("NAO_LOCALIZADO=0"))
                    || trimmed.contains("DIVERGE_ORIGEM_ERRADA")
                    || trimmed.contains("INTACTA_POS_AUDITORIA|false")
                    || (trimmed.contains("COM_DIVERGENCIAS") && !trimmed.contains("COM_DIVERGENCIAS_REVALIDADO_CONFERE_PELO_VERIFICADOR"))
                    || trimmed.contains("ALTERACAO_ORIGEM_DETECTADA")
                    || trimmed.contains("SEM_REFERENCIA_CRIPTO")
                    || trimmed.contains("MENSAGENS_NAO_ENCONTRADAS_NO_BANCO")
                    || trimmed.contains("ERRO_LEITURA_SQLITE")
                    || trimmed.contains("erro_sqlite_leitura")
                    || trimmed.contains("SCHEMA_DESCONHECIDO")
                    || trimmed.contains("FALHA_AUDITORIA")
                    || isN3DivNonZero) {
                cls = "st-bad";
            } else if ((trimmed.contains("EM_ORIGEM_SEM_HASH_TXT") && !trimmed.contains("EM_ORIGEM_SEM_HASH_TXT=0"))
                    || trimmed.contains("SEM_VINCULO_NO_MANIFESTO")
                    || trimmed.contains("DRIVER_SQLITE_INDISPONIVEL")
                    || trimmed.contains("SCAN_ORIGEM_TRUNCADO")) {
                cls = "st-warn";
            } else if (trimmed.contains("RESUMO_AUDITORIA_ORIGEM|CONFERE") || trimmed.contains("CUSTODIADO_AVILLA") || trimmed.contains("EMBUTIDO_NO_HTML_IPED") || trimmed.contains("|CONFERE") || trimmed.contains("COM_DIVERGENCIAS_REVALIDADO_CONFERE_PELO_VERIFICADOR")) cls = "st-ok";
            h.append("<tr><td><strong>").append(esc(tag)).append("</strong></td><td class=\"").append(cls).append("\"><code>").append(esc(rest)).append("</code></td></tr>");
        }
        h.append("</table>");

        h.append("<h2>Conteúdo Integral (auditoria-origem.txt)</h2>");
        h.append("<pre>").append(esc(res.reportText)).append("</pre>");
        h.append("<p>Gerado localmente pelo Verificador de Integridade v").append(esc(VERIFIER_VERSION)).append(".</p>");
        h.append("</body></html>");
        return h.toString();
    }

    static final class OriginPrecheckRow {
        final String level; // OK | AVISO | ERRO | INFO
        final String text;
        OriginPrecheckRow(String level, String text) { this.level = level; this.text = text; }
    }

    static final class OriginPrecheck {
        final List<OriginPrecheckRow> originRows = new ArrayList<>();
        final List<OriginPrecheckRow> caseRows = new ArrayList<>();
        Path originDir;
        Path caseDir;
        boolean originValid;
        boolean caseProvided;
        boolean caseValid;
        boolean canStart() { return originValid && (!caseProvided || caseValid); }
    }

    private static String stripSurroundingQuotes(String value) {
        if (value == null) return "";
        String t = value.trim();
        while (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static Path findFileNearby(Path base, String fileName, int maxDepth) {
        if (base == null || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) return null;
        final Path[] found = new Path[1];
        try {
            Files.walkFileTree(base, Collections.<FileVisitOption>emptySet(), maxDepth, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                    if (!d.equals(base) && (a.isSymbolicLink() || a.isOther() || isLinkOrReparse(d))) return FileVisitResult.SKIP_SUBTREE;
                    String n = d.getFileName() == null ? "" : d.getFileName().toString().toLowerCase(Locale.ROOT);
                    if ("media".equals(n) || "sdcard".equals(n) || "exportados".equals(n) || "indexador".equals(n)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (a.isRegularFile() && f.getFileName() != null && f.getFileName().toString().equalsIgnoreCase(fileName)) {
                        found[0] = f;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
            });
        } catch (IOException ignored) {}
        return found[0];
    }

    private static String humanBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    /** Read-only pre-check of the folders chosen for the origin audit (no hashing, no writes). */
    static OriginPrecheck precheckOriginAuditFolders(Path originDir, Path caseDir) {
        OriginPrecheck pc = new OriginPrecheck();
        pc.originDir = originDir;
        pc.caseDir = caseDir;

        // ---------- Pasta de origem (obrigatória) ----------
        boolean originOk = true;
        if (originDir == null || !Files.isDirectory(originDir, LinkOption.NOFOLLOW_LINKS)) {
            pc.originRows.add(new OriginPrecheckRow("ERRO", "Pasta não encontrada ou não é uma pasta: " + originDir));
            originOk = false;
        } else {
            try {
                validateSafeOriginTree(originDir);
            } catch (Exception ex) {
                pc.originRows.add(new OriginPrecheckRow("ERRO", "Caminho não permitido (link simbólico/junção): " + ex.getMessage()));
                originOk = false;
            }
        }
        if (originOk) {
            String[] required = { "Hashs.txt", "Hashs.avilla" };
            for (String name : required) {
                try {
                    Path p = findChildIgnoreCase(originDir, name);
                    if (p == null) {
                        pc.originRows.add(new OriginPrecheckRow("ERRO", name + " não encontrado (obrigatório)."));
                        originOk = false;
                    } else {
                        long sz = safeRegularFileSize(p);
                        if (sz <= 0) {
                            pc.originRows.add(new OriginPrecheckRow("ERRO", name + " está vazio (0 bytes)."));
                            originOk = false;
                        } else {
                            pc.originRows.add(new OriginPrecheckRow("OK", name + " encontrado (" + humanBytes(sz) + ")."));
                        }
                    }
                } catch (Exception ex) {
                    pc.originRows.add(new OriginPrecheckRow("ERRO", name + " inválido (link ou arquivo não regular)."));
                    originOk = false;
                }
            }
            try {
                Path pdf = findChildIgnoreCase(originDir, "report.pdf");
                if (pdf == null) pc.originRows.add(new OriginPrecheckRow("AVISO", "report.pdf não encontrado (recomendado): o SHA-256 do relatório de coleta não será registrado."));
                else pc.originRows.add(new OriginPrecheckRow("OK", "report.pdf encontrado (" + humanBytes(safeRegularFileSize(pdf)) + ")."));
            } catch (Exception ex) {
                pc.originRows.add(new OriginPrecheckRow("AVISO", "report.pdf inválido (link ou arquivo não regular)."));
            }
            try {
                Path db = findOriginDatabaseSafe(originDir);
                if (db == null) pc.originRows.add(new OriginPrecheckRow("AVISO", "msgstore.db não encontrado (recomendado): a conferência N1 do banco WhatsApp será omitida."));
                else pc.originRows.add(new OriginPrecheckRow("OK", "msgstore.db encontrado: " + originDir.relativize(db).toString().replace('\\', '/') + " (" + humanBytes(safeRegularFileSize(db)) + ")."));
            } catch (Exception ex) {
                pc.originRows.add(new OriginPrecheckRow("AVISO", "Não foi possível procurar o msgstore.db: " + ex.getMessage()));
            }
            if (!originOk) {
                Path hint = findFileNearby(originDir, "Hashs.txt", 4);
                if (hint != null && hint.getParent() != null && !hint.getParent().equals(originDir)) {
                    pc.originRows.add(new OriginPrecheckRow("INFO", "Hashs.txt foi encontrado em subpasta. Selecione esta pasta como origem: " + hint.getParent()));
                } else {
                    try {
                        if (findChildIgnoreCase(originDir, "Lista de Arquivos.csv") != null) {
                            pc.originRows.add(new OriginPrecheckRow("INFO", "Esta pasta parece ser um CASO IPED (tem Lista de Arquivos.csv). Use-a em \"Pasta do caso\" e escolha a extração Avilla em \"Pasta de origem\"."));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        pc.originValid = originOk;

        // ---------- Pasta do caso (opcional) ----------
        if (caseDir == null) {
            pc.caseProvided = false;
            pc.caseValid = true;
            pc.caseRows.add(new OriginPrecheckRow("INFO", "Não informada (opcional): a correlação com Lista de Arquivos.csv e Exportados do caso será omitida."));
            return pc;
        }
        pc.caseProvided = true;
        boolean caseOk = true;
        if (!Files.isDirectory(caseDir, LinkOption.NOFOLLOW_LINKS)) {
            pc.caseRows.add(new OriginPrecheckRow("ERRO", "Pasta não encontrada ou não é uma pasta: " + caseDir));
            caseOk = false;
        } else {
            try {
                validateSafeOriginTree(caseDir);
            } catch (Exception ex) {
                pc.caseRows.add(new OriginPrecheckRow("ERRO", "Caminho não permitido (link simbólico/junção): " + ex.getMessage()));
                caseOk = false;
            }
        }
        if (caseOk && originDir != null && caseDir.equals(originDir)) {
            pc.caseRows.add(new OriginPrecheckRow("ERRO", "A Pasta do caso não pode ser a mesma Pasta de origem."));
            caseOk = false;
        }
        if (caseOk) {
            boolean hasCsv = false;
            boolean hasExp = false;
            try {
                Path csv = findChildIgnoreCase(caseDir, "Lista de Arquivos.csv");
                if (csv != null) {
                    hasCsv = true;
                    pc.caseRows.add(new OriginPrecheckRow("OK", "Lista de Arquivos.csv encontrada (" + humanBytes(safeRegularFileSize(csv)) + ")."));
                } else {
                    pc.caseRows.add(new OriginPrecheckRow("AVISO", "Lista de Arquivos.csv não encontrada."));
                }
            } catch (Exception ex) {
                pc.caseRows.add(new OriginPrecheckRow("AVISO", "Lista de Arquivos.csv inválida (link ou arquivo não regular)."));
            }
            String caseName = caseDir.getFileName() == null ? "" : caseDir.getFileName().toString();
            Path exp = "Exportados".equalsIgnoreCase(caseName) ? caseDir : caseDir.resolve("Exportados");
            boolean expIsDir;
            try { expIsDir = Files.isDirectory(exp, LinkOption.NOFOLLOW_LINKS) && !isLinkOrReparse(exp); }
            catch (IOException ex) { expIsDir = false; }
            if (expIsDir) {
                hasExp = true;
                pc.caseRows.add(new OriginPrecheckRow("OK", "Pasta Exportados encontrada."));
            } else {
                pc.caseRows.add(new OriginPrecheckRow("AVISO", "Pasta Exportados não encontrada."));
            }
            if (!hasCsv && !hasExp) {
                pc.caseRows.add(new OriginPrecheckRow("ERRO", "Nenhum arquivo obrigatório do caso foi encontrado (é necessário Lista de Arquivos.csv e/ou Exportados)."));
                caseOk = false;
                Path hint = findFileNearby(caseDir, "Lista de Arquivos.csv", 3);
                if (hint != null && hint.getParent() != null) {
                    pc.caseRows.add(new OriginPrecheckRow("INFO", "Lista de Arquivos.csv foi encontrada em subpasta. Selecione esta pasta como caso: " + hint.getParent()));
                }
            }
        }
        pc.caseValid = caseOk;
        return pc;
    }

    private static JPanel precheckSection(String title, boolean valid, boolean optionalNotProvided, List<OriginPrecheckRow> rows, Path path) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        String state = optionalNotProvided ? "NÃO INFORMADA (opcional)" : (valid ? "VÁLIDA" : "INVÁLIDA");
        javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder(title + " — " + state);
        tb.setTitleColor(optionalNotProvided ? java.awt.Color.DARK_GRAY : (valid ? new java.awt.Color(0, 110, 0) : new java.awt.Color(139, 0, 0)));
        tb.setTitleFont(tb.getTitleFont() == null ? new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 13) : tb.getTitleFont().deriveFont(java.awt.Font.BOLD, 13f));
        box.setBorder(BorderFactory.createCompoundBorder(tb, BorderFactory.createEmptyBorder(4, 8, 6, 8)));
        if (path != null) {
            JLabel p = plainLabel(path.toString());
            p.setForeground(java.awt.Color.DARK_GRAY);
            box.add(p);
            box.add(Box.createVerticalStrut(4));
        }
        for (OriginPrecheckRow r : rows) {
            JLabel l = plainLabel("[" + r.level + "] " + r.text);
            switch (r.level) {
                case "OK": l.setForeground(new java.awt.Color(0, 110, 0)); break;
                case "AVISO": l.setForeground(new java.awt.Color(154, 103, 0)); break;
                case "ERRO": l.setForeground(new java.awt.Color(139, 0, 0)); l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD)); break;
                default: l.setForeground(new java.awt.Color(30, 70, 130)); break;
            }
            box.add(l);
        }
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    /** Shows the folder pre-check window. Returns true only if the user clicked "Começar Auditoria". */
    private static boolean showOriginPrecheckDialog(java.awt.Window owner, OriginPrecheck pc) {
        final boolean[] start = { false };
        final JDialog dialog = new JDialog(owner, "Verificador de Integridade - v" + VERIFIER_VERSION + " - Conferência das pastas", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        applyWindowDefaults(dialog);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        JLabel head = plainLabel("Conferência das pastas antes de começar a Auditoria de origem:");
        head.setFont(head.getFont().deriveFont(java.awt.Font.BOLD));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(head);
        content.add(Box.createVerticalStrut(8));
        content.add(precheckSection("Pasta de origem", pc.originValid, false, pc.originRows, pc.originDir));
        content.add(Box.createVerticalStrut(8));
        content.add(precheckSection("Pasta do caso", pc.caseValid, !pc.caseProvided, pc.caseRows, pc.caseDir));
        content.add(Box.createVerticalStrut(10));

        boolean ok = pc.canStart();
        JLabel summary = plainLabel(ok
                ? "Tudo certo: as pastas contêm os arquivos necessários. Clique em \"Começar Auditoria\"."
                : "Pasta(s) incorreta(s): feche esta janela, selecione a pasta correta e clique em \"Validar Auditoria\" novamente.");
        summary.setForeground(ok ? new java.awt.Color(0, 110, 0) : new java.awt.Color(139, 0, 0));
        summary.setFont(summary.getFont().deriveFont(java.awt.Font.BOLD));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(summary);

        JButton action = new JButton(ok ? "Começar Auditoria" : "Fechar");
        action.setFont(action.getFont().deriveFont(java.awt.Font.BOLD));
        action.addActionListener(e -> { start[0] = ok; dialog.dispose(); });
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        south.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        south.add(action);

        JPanel rootPanel = new JPanel(new java.awt.BorderLayout());
        rootPanel.add(content, java.awt.BorderLayout.CENTER);
        rootPanel.add(south, java.awt.BorderLayout.SOUTH);
        dialog.setContentPane(rootPanel);
        dialog.getRootPane().setDefaultButton(action);
        dialog.pack();
        if (dialog.getWidth() > 980) dialog.setSize(980, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return start[0];
    }

    /** Runs the origin audit in background showing a modal loading window with the current step and elapsed time. */
    private static void runOriginAuditWithProgressDialog(java.awt.Window owner, Path capturesRoot, List<Capture> selected,
            Path originDir, Path caseDir, Consumer<OriginAuditResult> onSuccess, Consumer<String> onError) {
        final JDialog dialog = new JDialog(owner, "Verificador de Integridade - v" + VERIFIER_VERSION + " - Executando Auditoria de origem", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        applyWindowDefaults(dialog);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final JLabel stage = plainLabel("Iniciando auditoria de origem (" + selected.size() + " captura(s))...");
        final JLabel elapsed = plainLabel("Tempo decorrido: 00:00");
        elapsed.setForeground(java.awt.Color.DARK_GRAY);
        final JLabel hint = plainLabel("Arquivos grandes (ex.: msgstore.db) podem levar alguns minutos. A janela continua respondendo.");
        hint.setForeground(java.awt.Color.DARK_GRAY);
        final JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        final JButton cancel = new JButton("Cancelar");

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(stage);
        north.add(Box.createVerticalStrut(4));
        north.add(elapsed);
        JPanel south = new JPanel(new java.awt.BorderLayout(8, 0));
        south.add(hint, java.awt.BorderLayout.CENTER);
        south.add(cancel, java.awt.BorderLayout.EAST);
        JPanel panel = new JPanel(new java.awt.BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        panel.setPreferredSize(new Dimension(640, 150));
        panel.add(north, java.awt.BorderLayout.NORTH);
        panel.add(bar, java.awt.BorderLayout.CENTER);
        panel.add(south, java.awt.BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        final long t0 = System.currentTimeMillis();
        final javax.swing.Timer clock = new javax.swing.Timer(1000, ev -> {
            long s = (System.currentTimeMillis() - t0) / 1000L;
            elapsed.setText(String.format(Locale.ROOT, "Tempo decorrido: %02d:%02d", s / 60, s % 60));
        });

        final SwingWorker<OriginAuditResult, String> worker = new SwingWorker<OriginAuditResult, String>() {
            @Override protected OriginAuditResult doInBackground() throws Exception {
                return runOriginAudit(capturesRoot, selected, originDir, caseDir, null, msg -> publish(msg));
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) stage.setText(chunks.get(chunks.size() - 1));
            }
            @Override protected void done() {
                clock.stop();
                dialog.dispose();
                if (isCancelled()) {
                    onError.accept("Auditoria de origem cancelada pelo usuário.");
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    onError.accept("ERRO na auditoria: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
                } catch (Exception ex) {
                    onError.accept("ERRO na auditoria: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                }
            }
        };
        cancel.addActionListener(e -> { cancel.setEnabled(false); stage.setText("Cancelando..."); worker.cancel(true); });
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cancel.setEnabled(false); stage.setText("Cancelando..."); worker.cancel(true); }
        });
        clock.start();
        worker.execute();
        dialog.setVisible(true);
    }

    private static final String LAUDO_LOG_ANCHOR_MARKER_V1 = "CHATCAPTURA_LOGANCHOR_V1;";
    private static final String LAUDO_LOG_ANCHOR_MARKER_V2 = "CHATCAPTURA_LOGANCHOR_V2;";
    private static final String LAUDO_LOG_ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final long MAX_LAUDO_PDF_ANCHOR_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_LAUDO_LOG_TXT_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_LAUDO_LOG_TXT_LINES = 100000;

    static final class PdfLogAnchor {
        boolean present;
        boolean duplicatedConflict;
        int version = 0; // 1 = V1 (line range), 2 = V2 (single file SHA-256)
        String createdUtc = "";
        int lastLine = -1;
        long byteLength = -1L;
        int sessions = 0;
        String fileSha256 = "";
        String prefixSha256 = "";
        String headHash = "";
        String status = "";
    }

    static final class LaudoLogAuditResult {
        boolean applicable;
        boolean passed = true;
        boolean hasWarning;
        String status = "NAO_APLICAVEL";
        String detail = "";
        String createdAtLocal = "";
        String createdAtUtc = "";
        int anchoredLastLine = -1;
        int actualLastLine = -1;
        String anchoredPrefixSha256 = "";
        String anchoredHeadHash = "";
        String actualPrefixSha256 = "";
        String actualHeadHash = "";
        String pdfAnchorStatus = "";
    }

    static PdfLogAnchor extractPdfLogAnchorWithoutPdfBox(Path pdfPath) throws IOException {
        PdfLogAnchor anchor = new PdfLogAnchor();
        if (pdfPath == null || !Files.exists(pdfPath, LinkOption.NOFOLLOW_LINKS)) {
            return anchor;
        }
        if (isLinkOrReparse(pdfPath) || !Files.isRegularFile(pdfPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("PDF de laudo invalido (link/juncao): " + pdfPath);
        }
        long size = safeRegularFileSize(pdfPath);
        if (size <= 0 || size > MAX_LAUDO_PDF_ANCHOR_BYTES) {
            throw new IOException("PDF de laudo vazio ou excede limite de 64 MiB: " + size);
        }
        byte[] pdfBytes = new byte[(int) size];
        try (InputStream in = Files.newInputStream(pdfPath, LinkOption.NOFOLLOW_LINKS)) {
            int off = 0;
            while (off < pdfBytes.length) {
                int r = in.read(pdfBytes, off, pdfBytes.length - off);
                if (r < 0) break;
                off += r;
            }
        }
        String latin = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Set<String> distinctTokens = new LinkedHashSet<>();
        for (String marker : new String[] { LAUDO_LOG_ANCHOR_MARKER_V2, LAUDO_LOG_ANCHOR_MARKER_V1 }) {
            int pos = 0;
            while ((pos = latin.indexOf(marker, pos)) >= 0) {
                int end = pos + marker.length();
                while (end < latin.length()) {
                    char c = latin.charAt(end);
                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                            || c == '_' || c == ';' || c == '=' || c == '.' || c == '+' || c == ':' || c == '-') {
                        end++;
                    } else {
                        break;
                    }
                }
                distinctTokens.add(latin.substring(pos, end));
                pos = end;
            }
        }
        if (distinctTokens.isEmpty()) {
            return anchor;
        }
        if (distinctTokens.size() > 1) {
            anchor.present = true;
            anchor.duplicatedConflict = true;
            return anchor;
        }
        String token = distinctTokens.iterator().next();
        if (token.startsWith(LAUDO_LOG_ANCHOR_MARKER_V2)) {
            anchor.version = 2;
            String body = token.substring(LAUDO_LOG_ANCHOR_MARKER_V2.length());
            for (String kv : body.split(";")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq).trim();
                String v = kv.substring(eq + 1).trim();
                if ("created".equals(k)) {
                    anchor.createdUtc = v;
                } else if ("bytes".equals(k)) {
                    try { anchor.byteLength = Long.parseLong(v); } catch (NumberFormatException ignored) { anchor.byteLength = -1L; }
                } else if ("sessions".equals(k)) {
                    try { anchor.sessions = Integer.parseInt(v); } catch (NumberFormatException ignored) { anchor.sessions = 0; }
                } else if ("sha256".equals(k) && v.matches("(?i)[0-9a-f]{64}")) {
                    anchor.fileSha256 = v.toLowerCase(Locale.ROOT);
                    anchor.prefixSha256 = anchor.fileSha256;
                    anchor.headHash = anchor.fileSha256;
                } else if ("status".equals(k)) {
                    anchor.status = v;
                }
            }
            if (anchor.byteLength > 0L && anchor.fileSha256.length() == 64) {
                anchor.present = true;
            }
            return anchor;
        }

        anchor.version = 1;
        String body = token.substring(LAUDO_LOG_ANCHOR_MARKER_V1.length());
        for (String kv : body.split(";")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String k = kv.substring(0, eq).trim();
            String v = kv.substring(eq + 1).trim();
            if ("created".equals(k)) {
                anchor.createdUtc = v;
            } else if ("lines".equals(k) && v.startsWith("0..")) {
                try {
                    anchor.lastLine = Integer.parseInt(v.substring(3));
                } catch (NumberFormatException ignored) {
                    anchor.lastLine = -1;
                }
            } else if ("prefix".equals(k) && v.matches("(?i)[0-9a-f]{64}")) {
                anchor.prefixSha256 = v.toLowerCase(Locale.ROOT);
            } else if ("head".equals(k) && v.matches("(?i)[0-9a-f]{64}")) {
                anchor.headHash = v.toLowerCase(Locale.ROOT);
            } else if ("status".equals(k)) {
                anchor.status = v;
            }
        }
        if (anchor.lastLine >= 0 && anchor.prefixSha256.length() == 64 && anchor.headHash.length() == 64) {
            anchor.present = true;
        }
        return anchor;
    }

    static LaudoLogAuditResult auditLaudoChainedLog(Path capturesRoot) {
        LaudoLogAuditResult res = new LaudoLogAuditResult();
        if (capturesRoot == null) {
            return res;
        }
        try {
            Path laudoDir = resolve(capturesRoot, "laudo");
            if (laudoDir == null || !Files.exists(laudoDir, LinkOption.NOFOLLOW_LINKS)) {
                return res;
            }
            if (isLinkOrReparse(laudoDir) || !Files.isDirectory(laudoDir, LinkOption.NOFOLLOW_LINKS)) {
                res.applicable = true;
                res.passed = false;
                res.status = "FALHA_LOG_ADULTERADO";
                res.detail = "Pasta laudo/ invalida (link simbolico ou juncao nao permitida).";
                return res;
            }
            Path pdfPath = resolve(laudoDir, "report-plugin.pdf");
            Path logPath = resolve(laudoDir, "Log.txt");
            boolean pdfExists = pdfPath != null && Files.exists(pdfPath, LinkOption.NOFOLLOW_LINKS);
            boolean logExists = logPath != null && Files.exists(logPath, LinkOption.NOFOLLOW_LINKS);
            if (!pdfExists && !logExists) {
                return res;
            }

            PdfLogAnchor anchor = pdfExists ? extractPdfLogAnchorWithoutPdfBox(pdfPath) : new PdfLogAnchor();
            if (anchor.duplicatedConflict) {
                res.applicable = true;
                res.passed = false;
                res.status = "FALHA_ANCORA_PDF_DUPLICADA";
                res.detail = "Multiplas ancoras CHATCAPTURA_LOGANCHOR divergentes em laudo/report-plugin.pdf.";
                return res;
            }
            if (anchor.present) {
                res.anchoredLastLine = anchor.lastLine;
                res.anchoredPrefixSha256 = anchor.prefixSha256;
                res.anchoredHeadHash = anchor.headHash;
                res.pdfAnchorStatus = anchor.status;
                res.createdAtUtc = anchor.createdUtc;
            }

            if (!logExists) {
                if (anchor.present) {
                    res.applicable = true;
                    res.passed = false;
                    res.status = "FALHA_LOG_AUSENTE";
                    res.detail = anchor.version == 2
                            ? ("laudo/Log.txt ausente, mas laudo/report-plugin.pdf ancora SHA-256 " + anchor.fileSha256.substring(0, 12) + "... (" + anchor.byteLength + " bytes).")
                            : ("laudo/Log.txt ausente, mas laudo/report-plugin.pdf ancora linhas 0.." + anchor.lastLine + ".");
                }
                return res;
            }

            res.applicable = true;
            if (isLinkOrReparse(logPath) || !Files.isRegularFile(logPath, LinkOption.NOFOLLOW_LINKS)) {
                res.passed = false;
                res.status = "FALHA_LOG_ADULTERADO";
                res.detail = "laudo/Log.txt invalido (link simbolico ou juncao).";
                return res;
            }
            long logSize = safeRegularFileSize(logPath);
            if (logSize <= 0 || logSize > MAX_LAUDO_LOG_TXT_BYTES) {
                res.passed = false;
                res.status = "FALHA_LOG_ADULTERADO";
                res.detail = "laudo/Log.txt vazio ou excede limite de 16 MiB (" + logSize + " bytes).";
                return res;
            }

            // V2 Single-SHA-256 verification path (or session-formatted Log.txt)
            byte[] rawBytes = Files.readAllBytes(logPath);
            String fullFileSha = hex(MessageDigest.getInstance(ALG).digest(rawBytes));
            String headText = new String(rawBytes, 0, Math.min(rawBytes.length, 512), StandardCharsets.UTF_8);
            boolean isSessionFormat = headText.contains("=== LOG CRONOLOGICO");
            if (anchor.version == 2 || (isSessionFormat && (!anchor.present || anchor.version != 1))) {
                res.actualPrefixSha256 = fullFileSha;
                res.actualHeadHash = fullFileSha;
                String fullText = new String(rawBytes, StandardCharsets.UTF_8);
                for (String ln : fullText.split("\\r?\\n")) {
                    if (ln.startsWith("CRIADO_EM:")) {
                        String v = ln.substring("CRIADO_EM:".length()).trim();
                        int u = v.indexOf("(UTC:");
                        if (u > 0) {
                            res.createdAtLocal = v.substring(0, u).trim();
                            String rest = v.substring(u + 5).trim();
                            if (rest.endsWith(")")) rest = rest.substring(0, rest.length() - 1).trim();
                            res.createdAtUtc = rest;
                        } else {
                            res.createdAtLocal = v;
                        }
                        break;
                    }
                }
                if (!anchor.present) {
                    res.passed = true;
                    res.hasWarning = true;
                    res.status = "AVISO_PDF_SEM_ANCORA";
                    res.detail = "laudo/Log.txt presente (" + rawBytes.length + " bytes, SHA-256=" + fullFileSha.substring(0, 12)
                            + "...), mas laudo/report-plugin.pdf nao contem ancora CHATCAPTURA_LOGANCHOR.";
                    return res;
                }
                if (rawBytes.length < anchor.byteLength) {
                    res.passed = false;
                    res.status = "FALHA_LOG_TRUNCADO";
                    res.detail = "laudo/Log.txt truncado: PDF ancora " + anchor.byteLength + " bytes, mas arquivo possui apenas " + rawBytes.length + " bytes.";
                    return res;
                }
                MessageDigest prefixMd = MessageDigest.getInstance(ALG);
                prefixMd.update(rawBytes, 0, (int) anchor.byteLength);
                String computedAnchoredSha = hex(prefixMd.digest());
                if (!computedAnchoredSha.equalsIgnoreCase(anchor.fileSha256)) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "SHA-256 de laudo/Log.txt diverge da ancora gravada em laudo/report-plugin.pdf.";
                    return res;
                }
                if (rawBytes.length > anchor.byteLength) {
                    long extraBytes = rawBytes.length - anchor.byteLength;
                    res.passed = true;
                    res.hasWarning = true;
                    res.status = "CONFERE_COM_LINHAS_POSTERIORES";
                    res.detail = "SHA-256 inicial (" + anchor.byteLength + " bytes) confere com report-plugin.pdf; existem "
                            + extraBytes + " bytes de sessoes posteriores ainda nao ancoradas no PDF.";
                    return res;
                }
                if ("RECRIADO_POR_SOLICITACAO_DO_USUARIO".equalsIgnoreCase(anchor.status)) {
                    res.passed = true;
                    res.hasWarning = true;
                    res.status = "CONFERE_RECRIADO_NO_PDF";
                    res.detail = "SHA-256 de laudo/Log.txt confere com report-plugin.pdf (AVISO: PDF registra que Log.txt foi recriado por solicitacao do usuario; criado em "
                            + res.createdAtLocal + ").";
                    return res;
                }
                res.passed = true;
                res.hasWarning = false;
                res.status = "CONFERE";
                res.detail = "SHA-256 unico de laudo/Log.txt (" + fullFileSha.substring(0, 12) + "..., "
                        + anchor.sessions + " sessoes, " + rawBytes.length + " bytes) confere integralmente com laudo/report-plugin.pdf.";
                return res;
            }

            List<String> lines = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
                String ln;
                while ((ln = br.readLine()) != null) {
                    if (lines.isEmpty() && !ln.isEmpty() && ln.charAt(0) == '\uFEFF') {
                        ln = ln.substring(1);
                    }
                    if (ln.isEmpty()) continue;
                    lines.add(ln);
                    if (lines.size() > MAX_LAUDO_LOG_TXT_LINES) {
                        res.passed = false;
                        res.status = "FALHA_LOG_ADULTERADO";
                        res.detail = "laudo/Log.txt excede limite maximo de linhas.";
                        return res;
                    }
                }
            }
            if (lines.isEmpty()) {
                res.passed = false;
                res.status = "FALHA_LOG_ADULTERADO";
                res.detail = "laudo/Log.txt nao contem registros validos.";
                return res;
            }

            String expectedPrev = LAUDO_LOG_ZERO_HASH;
            String anchoredLineComputedHash = "";
            MessageDigest fullPrefixMd = MessageDigest.getInstance(ALG);
            MessageDigest anchorPrefixMd = anchor.present ? MessageDigest.getInstance(ALG) : null;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int hashIdx = line.lastIndexOf("|hash=");
                if (hashIdx <= 0) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Linha " + i + " de laudo/Log.txt sem sufixo |hash= valido.";
                    return res;
                }
                String payload = line.substring(0, hashIdx);
                String declaredHash = line.substring(hashIdx + 6).trim().toLowerCase(Locale.ROOT);
                if (!declaredHash.matches("[0-9a-f]{64}")) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Linha " + i + " de laudo/Log.txt com hash SHA-256 invalido.";
                    return res;
                }
                String[] parts = payload.split("\\|", -1);
                if (parts.length != 8) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Linha " + i + " de laudo/Log.txt com estrutura invalida (" + parts.length + " colunas).";
                    return res;
                }
                int seq;
                try {
                    seq = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException nfe) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Linha " + i + " de laudo/Log.txt com indice nao numerico.";
                    return res;
                }
                if (seq != i) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Sequencia quebrada em laudo/Log.txt na linha " + i + " (encontrado " + seq + ").";
                    return res;
                }
                if (!parts[7].startsWith("prev=")) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Linha " + i + " de laudo/Log.txt sem campo prev=.";
                    return res;
                }
                String declaredPrev = parts[7].substring(5).trim().toLowerCase(Locale.ROOT);
                if (!expectedPrev.equals(declaredPrev)) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Cadeia prev quebrada em laudo/Log.txt na linha " + i + ".";
                    return res;
                }
                String computedHash = hashText(payload);
                if (!computedHash.equalsIgnoreCase(declaredHash)) {
                    res.passed = false;
                    res.status = "FALHA_LOG_ADULTERADO";
                    res.detail = "Hash SHA-256 divergente em laudo/Log.txt na linha " + i + ".";
                    return res;
                }
                if (i == 0) {
                    res.createdAtLocal = parts[1].trim();
                    res.createdAtUtc = parts[2].trim();
                }
                byte[] normLineBytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                fullPrefixMd.update(normLineBytes);
                if (anchorPrefixMd != null && i <= anchor.lastLine) {
                    anchorPrefixMd.update(normLineBytes);
                    if (i == anchor.lastLine) {
                        anchoredLineComputedHash = declaredHash;
                    }
                }
                expectedPrev = declaredHash;
            }

            res.actualLastLine = lines.size() - 1;
            res.actualPrefixSha256 = hex(fullPrefixMd.digest());
            res.actualHeadHash = expectedPrev;

            if (!anchor.present) {
                res.passed = true;
                res.hasWarning = true;
                res.status = "AVISO_PDF_SEM_ANCORA";
                res.detail = "Cadeia interna de laudo/Log.txt integra (linhas 0.." + res.actualLastLine
                        + "), mas laudo/report-plugin.pdf nao contem ancora CHATCAPTURA_LOGANCHOR_V1.";
                return res;
            }

            if (res.actualLastLine < anchor.lastLine) {
                res.passed = false;
                res.status = "FALHA_LOG_TRUNCADO";
                res.detail = "laudo/Log.txt truncado: PDF ancora linhas 0.." + anchor.lastLine
                        + ", mas arquivo possui apenas 0.." + res.actualLastLine + ".";
                return res;
            }

            String computedAnchorPrefix = hex(anchorPrefixMd.digest());
            if (!computedAnchorPrefix.equalsIgnoreCase(anchor.prefixSha256)
                    || !anchoredLineComputedHash.equalsIgnoreCase(anchor.headHash)) {
                res.passed = false;
                res.status = "FALHA_PREFIXO_DIVERGENTE";
                res.detail = "Prefixo 0.." + anchor.lastLine + " de laudo/Log.txt diverge da ancora gravada em laudo/report-plugin.pdf.";
                return res;
            }

            if (res.actualLastLine > anchor.lastLine) {
                int extra = res.actualLastLine - anchor.lastLine;
                res.passed = true;
                res.hasWarning = true;
                res.status = "CONFERE_COM_LINHAS_POSTERIORES";
                res.detail = "Prefixo 0.." + anchor.lastLine + " confere com report-plugin.pdf; existem " + extra
                        + " linha(s) posterior(es) valida(s) (0.." + res.actualLastLine + ") ainda nao ancoradas no PDF.";
                return res;
            }

            if ("RECRIADO_POR_SOLICITACAO_DO_USUARIO".equalsIgnoreCase(anchor.status)) {
                res.passed = true;
                res.hasWarning = true;
                res.status = "CONFERE_RECRIADO_NO_PDF";
                res.detail = "Prefixo 0.." + anchor.lastLine + " confere com report-plugin.pdf (AVISO: PDF registra que Log.txt foi recriado por solicitacao do usuario; criado em "
                        + res.createdAtLocal + ").";
                return res;
            }

            res.passed = true;
            res.hasWarning = false;
            res.status = "CONFERE";
            res.detail = "Cadeia e prefixo 0.." + anchor.lastLine + " conferem integralmente com laudo/report-plugin.pdf (criado em "
                    + res.createdAtLocal + ").";
            return res;
        } catch (Exception ex) {
            res.applicable = true;
            res.passed = false;
            res.status = "FALHA_LOG_ADULTERADO";
            res.detail = "Erro ao auditar laudo/Log.txt: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            return res;
        }
    }

    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String jsonEsc(String value){if(value==null)return "";StringBuilder out=new StringBuilder(value.length()+16);for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.toString();}
}
