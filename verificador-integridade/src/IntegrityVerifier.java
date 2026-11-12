import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Image;
import javax.swing.ImageIcon;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.*;
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
    private static final String VERIFIER_VERSION = "1.3.5";
    private static final Pattern CAPTURE_TIMESTAMP_SUFFIX = Pattern.compile("\\A.*-(\\d{8}-\\d{6})\\z", Pattern.DOTALL);
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
    private static final long MAX_SELECTED_METADATA_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_REPORT_ESTIMATED_CHARS = 4L * 1024L * 1024L;
    private static final long MAX_HTML_CAPTURE_BYTES = 64L * 1024L * 1024L;
    private static Image verifierIcon() {
        // tenta carregar de "imagem doc/verificador-icon.png" relativo ao jar/dir de execucao
        String[] candidates = new String[] {
            "imagem doc/verificador-icon.png",
            "verificador-icon.png",
            "../imagem doc/verificador-icon.png" };
        for (String c : candidates) {
            try {
                java.io.File f = new java.io.File(c);
                if (f.isFile()) return Toolkit.getDefaultToolkit().getImage(f.getAbsolutePath());
            } catch (Exception ignored) { }
        }
        try {
            java.net.URL u = IntegrityVerifier.class.getResource("/imagem doc/verificador-icon.png");
            if (u != null) return Toolkit.getDefaultToolkit().getImage(u);
            u = IntegrityVerifier.class.getResource("/verificador-icon.png");
            if (u != null) return Toolkit.getDefaultToolkit().getImage(u);
        } catch (Exception ignored) { }
        return null;
    }
    private static JFrame newWindow(String title, boolean resizable) {
        JFrame frame = new JFrame(title);
        Image ic = verifierIcon();
        if (ic != null) frame.setIconImage(ic);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setResizable(resizable);
        return frame;
    }
    private static final class Item {
        String path, expected; long size, bytes; String actual, state;
    }
    private static final class Capture {
        Path dir; String name, hash, historyHash = "", contentRootHash = "", integrityFileHash = "", historyStatus = "registro ausente/legado", error = ""; JSONArray historyEvents = new JSONArray(); List<Item> items = new ArrayList<>(); long bytes, metadataBytes; int fileCount;
    }
    private IntegrityVerifier() {}

    public static void main(String[] args) throws Exception {
        boolean verifyOnly = args.length > 0 && "--verify-only".equalsIgnoreCase(args[0]);
        boolean exportHistory = args.length > 0 && "--export-history".equalsIgnoreCase(args[0]);
        Path root = args.length > (verifyOnly || exportHistory ? 1 : 0) ? Paths.get(args[verifyOnly || exportHistory ? 1 : 0]) : applicationFolder();
        root = root.toAbsolutePath().normalize();
        List<Capture> captures = (verifyOnly||exportHistory)?discover(root):discoverInteractive(root);
        if (captures.isEmpty()) { JOptionPane pane=new JOptionPane("Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta:\n" + root,JOptionPane.INFORMATION_MESSAGE);JDialog notice=pane.createDialog(null,"Integridade");Image ic=verifierIcon();if(ic!=null)notice.setIconImage(ic);notice.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){System.exit(0);}});notice.setVisible(true);System.exit(0);return; }
        if (verifyOnly) {
            boolean ok=true; long totalItems=0,totalMetadata=0;
            for(Capture c:captures){try{long metadata=candidateMetadataBytes(c);if(totalMetadata+metadata>MAX_SELECTED_METADATA_BYTES)throw new IOException("O limite global de metadados selecionados foi excedido; verificação interrompida.");verify(c,(int)Math.max(0,MAX_SELECTED_ITEMS-totalItems));totalMetadata+=metadata;totalItems+=c.items.size();}catch(Exception e){c.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();ok=false;}System.out.println(c.name+" | "+(c.error.isEmpty()?c.historyStatus:"ERRO: "+c.error)+" | content="+c.hash+" | history="+c.historyHash);ok &= c.historyStatus.startsWith("consistente com registro local");if(c.error.contains("interrompida")||c.error.contains("limite global"))break;}
            if(!ok) throw new IOException("Uma ou mais capturas divergiram do registro local.");
            return;
        }
        if(exportHistory){verifyAllBounded(captures);enforceReportBudget(captures);String collectionHash=setHash(captures);String report=historyReport(captures,collectionHash);String publicReport=publicHistoryReport(captures,collectionHash);Path out=root.resolve("integridade");Files.createDirectories(out);atomic(out.resolve("historico-capturas.txt"),report);System.out.println(out.resolve("historico-capturas.txt"));System.out.println("SHA-256_PUBLICO="+hashText(normalizePublicRecord(report)));System.out.println("SHA-256_HISTORICO_PUBLICO="+hashText(normalizePublicRecord(publicReport)));return;}
        int mode=chooseMode();
        if(mode<0){System.exit(0);return;}
        List<Capture> selected = chooseCaptures(captures);
        if (selected.isEmpty()) { System.exit(0); return; }
        try { verifyAllBounded(selected); enforceReportBudget(selected); }
        catch (Exception e) { JOptionPane.showMessageDialog(null,e.getMessage()==null?"Não foi possível verificar o conjunto selecionado.":e.getMessage(),"Limite ou erro de verificação",JOptionPane.ERROR_MESSAGE); return; }
        String setHash = setHash(selected);
        String generated = OffsetDateTime.now().toString();
        String history = historyReport(selected, setHash);
        String publicHistory = publicHistoryReport(selected, setHash);
        String historyFullHash = hashText(normalizePublicRecord(history));
        String historyPublicHash = hashText(normalizePublicRecord(publicHistory));
        writeReports(root, selected, setHash, generated, history);
        String hashList=publicHashList(selected,setHash);
        Path historyFile=root.resolve("integridade").resolve("historico-capturas.txt");
        if(mode==1)comparePublicHistory(history,historyFullHash,publicHistory,historyPublicHash);
        showSummaryDialog(root,selected,setHash,history,historyFullHash,publicHistory,historyPublicHash,hashList,historyFile,mode);
        System.exit(0);
    }
    private static List<Capture> discoverInteractive(Path root)throws Exception{
        final JDialog[] dialogRef=new JDialog[1];
        final JLabel[] statusRef=new JLabel[1];
        final SwingWorker<List<Capture>,Integer>[] workerRef=new SwingWorker[1];
        SwingUtilities.invokeAndWait(()->{
            JDialog dialog=new JDialog((java.awt.Frame)null,"Verificador de Integridade — procurando capturas",true);
            JLabel status=new JLabel("Procurando… 0 capturas encontradas");JButton cancel=new JButton("Cancelar busca");
            JPanel panel=new JPanel(new java.awt.BorderLayout(10,10));panel.setBorder(BorderFactory.createEmptyBorder(14,16,14,16));panel.add(status,java.awt.BorderLayout.CENTER);panel.add(cancel,java.awt.BorderLayout.SOUTH);dialog.setContentPane(panel);dialog.pack();dialog.setLocationRelativeTo(null);
            SwingWorker<List<Capture>,Integer> worker=new SwingWorker<List<Capture>,Integer>(){
                protected List<Capture> doInBackground()throws Exception{return discover(root,n->publish(n));}
                protected void process(List<Integer> values){if(!values.isEmpty())status.setText("Procurando… "+values.get(values.size()-1)+" capturas encontradas");}
                protected void done(){if(!isCancelled())status.setText("Busca concluída");dialog.dispose();}
            };
            cancel.addActionListener(e->{worker.cancel(true);dialog.dispose();});dialogRef[0]=dialog;statusRef[0]=status;workerRef[0]=worker;worker.execute();dialog.setVisible(true);
        });
        try{return workerRef[0].get();}catch(CancellationException e){throw new IOException("Busca de capturas cancelada.",e);}catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("Falha durante a busca de capturas.",cause);}
    }
    private static long candidateMetadataBytes(Capture c) throws IOException {
        long total=0;for(String name:new String[]{"integridade.json","manifest.json"}){Path p=c.dir.resolve(name);if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){long size=Files.size(p);if(size>MAX_METADATA_BYTES)throw new IOException("Arquivo de metadados excede o limite: "+p);total+=size;}}
        return total;
    }
    private static void verifyAllBounded(List<Capture> captures) throws Exception {
        long totalItems=0,totalMetadata=0;
        for(Capture c:captures){long metadata=candidateMetadataBytes(c);if(totalMetadata+metadata>MAX_SELECTED_METADATA_BYTES)throw new IOException("O conjunto selecionado excede o orçamento global de metadados de "+MAX_SELECTED_METADATA_BYTES+" bytes; nenhum relatório foi concluído.");try{verify(c,(int)Math.max(0,MAX_SELECTED_ITEMS-totalItems));}catch(Exception e){throw new IOException("Falha controlada ao verificar a captura '"+c.name+"': "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),e);}totalMetadata+=metadata;totalItems+=c.items.size();}
    }
    private static void enforceReportBudget(List<Capture> captures) throws IOException {
        long estimate=4096;
        for(Capture c:captures){estimate+=1024L+Math.min(32768,c.name==null?0:c.name.length())*8L;for(Item i:c.items){estimate+=512L+Math.min(MAX_RELATIVE_PATH_CHARS,i.path==null?0:i.path.length())*8L;}for(int i=0;i<c.historyEvents.length();i++){JSONObject event=c.historyEvents.optJSONObject(i);if(event==null)continue;estimate+=1024L+event.optString("timestampUtc","").length()*8L+event.optString("action","").length()*8L;JSONArray changes=event.optJSONArray("changes");if(changes!=null)for(int j=0;j<changes.length();j++){JSONObject change=changes.optJSONObject(j);if(change!=null)estimate+=512L+change.optString("action","").length()*8L+change.optString("path","").length()*8L;}}}
        if(estimate>MAX_REPORT_ESTIMATED_CHARS)throw new IOException("O relatório estimado excede o limite seguro de "+MAX_REPORT_ESTIMATED_CHARS+" caracteres. Reduza o conjunto selecionado.");
    }
    private static void showSummaryDialog(Path root,List<Capture> selected,String setHash,String history,String historyFullHash,String publicHistory,String historyPublicHash,String hashList,Path historyFile,int mode) throws Exception {
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(new JLabel(mode==0?"MODO: INTEGRIDADE LOCAL — arquivos comparados com o registro local.":"MODO: INTEGRIDADE PÚBLICO — referência externa comparada."));
        panel.add(new JLabel("Capturas verificadas: "+selected.size()));panel.add(Box.createVerticalStrut(10));
        panel.add(hashValueRow("Hash geral do conjunto",setHash,"Hash geral do conjunto copiado."));
        panel.add(Box.createVerticalStrut(8));
        panel.add(hashValueRow("SHA-256 do histórico completo",historyFullHash,"Hash SHA-256 do histórico completo copiado."));
        panel.add(Box.createVerticalStrut(8));
        panel.add(hashValueRow("SHA-256 do histórico completo público",historyPublicHash,"Hash SHA-256 do histórico completo público copiado."));
        panel.add(Box.createVerticalStrut(8));
        JPanel actions=new JPanel(new java.awt.GridLayout(0,2,6,6));
        JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Resultado",true);
        JButton copyList=new JButton("Copiar lista de hashes");copyList.addActionListener(e->copyToClipboard(frame,hashList,"Lista de hashes copiada."));
        JButton copyHistory=new JButton("Copiar histórico completo");copyHistory.addActionListener(e->copyToClipboard(frame,history,"Histórico completo copiado."));
        JButton copyPublicHistory=new JButton("Copiar histórico completo público");copyPublicHistory.addActionListener(e->copyToClipboard(frame,publicHistory,"Histórico completo público copiado."));
        JButton openHistory=new JButton("Abrir histórico");openHistory.addActionListener(e->openFile(historyFile));
        JButton openHtml=new JButton("Abrir relatório HTML");openHtml.addActionListener(e->openInBrowser(root.resolve("integridade.html")));
        final CountDownLatch latch=new CountDownLatch(1);
        JButton close=new JButton("Fechar");close.addActionListener(e->latch.countDown());
        actions.add(copyList);actions.add(copyHistory);actions.add(copyPublicHistory);actions.add(openHistory);actions.add(openHtml);actions.add(close);panel.add(actions);
        frame.setContentPane(panel);
        frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){latch.countDown();}});
        frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);
        latch.await();
        frame.dispose();
    }
    private static JPanel hashValueRow(String label,String value,String copyMessage){
        JPanel block=new JPanel();block.setLayout(new BoxLayout(block,BoxLayout.Y_AXIS));block.add(new JLabel(label+":"));
        JPanel row=new JPanel(new java.awt.BorderLayout(8,0));JButton copy=new JButton("Copiar");copy.addActionListener(e->copyToClipboard(SwingUtilities.getWindowAncestor(row),value,copyMessage));
        JTextArea text=new JTextArea(value,2,38);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);text.setFocusable(true);
        row.add(copy,java.awt.BorderLayout.WEST);row.add(text,java.awt.BorderLayout.CENTER);block.add(row);return block;
    }
    private static int chooseMode() throws Exception {
        final int[] selected={-1};
        final CountDownLatch latch=new CountDownLatch(1);
        JButton local=new JButton("Integridade Local");JButton external=new JButton("Integridade Público");
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(new JLabel("Escolha a referência para verificar:"));panel.add(Box.createVerticalStrut(10));panel.add(new JLabel("Local: compara os arquivos com o integridade.json da captura."));panel.add(new JLabel("Público: compara com hash ou histórico recebido de fora."));panel.add(Box.createVerticalStrut(12));JPanel buttons=new JPanel();buttons.add(local);buttons.add(external);panel.add(buttons);
        JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" ("+System.getProperty("os.name")+")",false);
        local.addActionListener(e->{selected[0]=0;frame.dispose();latch.countDown();});
        external.addActionListener(e->{selected[0]=1;frame.dispose();latch.countDown();});
        frame.setContentPane(panel);
        frame.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){System.exit(0);}});
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        latch.await();
        frame.dispose();
        return selected[0];
    }
    private static Path applicationFolder() throws Exception {
        Path location=Paths.get(IntegrityVerifier.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        Path start=Files.isRegularFile(location)?location.getParent():location;
        for(Path p=start;p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("index.html")))return p;
        return start;
    }
    private static void openInBrowser(Path html) {
        try {
            if (!Files.isRegularFile(html)) throw new IOException("O relatório HTML não foi encontrado: " + html);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException("Este sistema não disponibilizou a abertura do navegador padrão.");
            Desktop.getDesktop().browse(html.toUri());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Não foi possível abrir o navegador padrão. Abra este arquivo manualmente:\n" + html + "\n\nDetalhe: " + e.getMessage(), "Abrir relatório", JOptionPane.ERROR_MESSAGE);
        }
    }
    private static void openFile(Path file) { try { if(!Desktop.isDesktopSupported())throw new IOException("A abertura de arquivos não está disponível.");Desktop.getDesktop().open(file.toFile()); } catch(Exception e){JOptionPane.showMessageDialog(null,"Não foi possível abrir o arquivo. Ele foi salvo em:\n"+file+"\n\n"+e.getMessage(),"Abrir histórico",JOptionPane.ERROR_MESSAGE);} }
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
        JTextField publicHash=new JTextField(44);
        JTextArea publicRecord=new JTextArea(12,52);
        publicRecord.setLineWrap(false);
        JLabel result=new JLabel("Informe o SHA-256 público ou cole o histórico completo (com pastas ou anonimizado CAPTURA|*-data-hora) para conferir.");
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

        final CountDownLatch latch=new CountDownLatch(1);
        final JFrame frame2=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Integridade Público",true);
        frame2.addWindowListener(new java.awt.event.WindowAdapter(){
            @Override public void windowClosing(java.awt.event.WindowEvent e){latch.countDown();}
        });

        JPanel hashInputRow=new JPanel(new java.awt.BorderLayout(8,0));
        hashInputRow.add(publicHash,java.awt.BorderLayout.CENTER);
        hashInputRow.add(compareHash,java.awt.BorderLayout.EAST);

        JPanel sectionHash=new JPanel(new java.awt.BorderLayout(0,6));
        sectionHash.add(new JLabel("1. Hash SHA-256 público (de historico-capturas.txt):"),java.awt.BorderLayout.NORTH);
        sectionHash.add(hashInputRow,java.awt.BorderLayout.CENTER);

        JPanel recordButtonRow=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,0,0));
        recordButtonRow.add(compareRecord);

        JPanel sectionRecord=new JPanel(new java.awt.BorderLayout(0,6));
        JPanel recordHeader=new JPanel();
        recordHeader.setLayout(new BoxLayout(recordHeader,BoxLayout.Y_AXIS));
        recordHeader.add(new JLabel("2. Histórico de capturas completo (historico-capturas.txt):"));
        JLabel formatHint=new JLabel("Aceita formato completo (CAPTURA|pasta) ou público anonimizado (CAPTURA|*-YYYYMMDD-HHMMSS).");
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
        latch.await();
        frame2.dispose();
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
        Map<String,String> localByKey=publicMessageMap(localRows),referenceByKey=publicMessageMap(referenceRows),localById=publicMessageDomMap(localRows),referenceById=publicMessageDomMap(referenceRows);
        String computed=htmlCaptureRootHash(referenceRows,reference.optString("htmlSha256",""),reference.optString("annotationsSha256",""),reconciliation,reference);
        if(!computed.equalsIgnoreCase(reference.optString("captureRootHash","")))throw new IOException("Hash raiz do manifesto público não confere com o resumo por mensagem.");
        int same=0,changed=0,notInReference=0,referenceOnly=0;StringBuilder output=new StringBuilder();
        for(Map.Entry<String,String> entry:localById.entrySet()){
            String other=referenceById.get(entry.getKey());String state;
            if(other==null){state="ausente_na_referencia";notInReference++;}
            else if(other.equalsIgnoreCase(entry.getValue())){state="integra";same++;}
            else{state="hash_divergente";changed++;}
            output.append(entry.getKey()).append('|').append(state).append('\n');
        }
        for(String id:referenceById.keySet())if(!localById.containsKey(id)){referenceOnly++;output.append(id).append("|somente_na_referencia\n");}
        return "Captura local: "+localName+"\nIntegras: "+same+" | divergentes: "+changed+" | ausentes na referência: "+notInReference+" | somente na referência: "+referenceOnly+"\nA ausência descreve a comparação; não identifica causa nem autoria.\n\n"+output.toString();
    }
    private static Map<String,String> publicMessageMap(JSONArray rows)throws IOException{
        Map<String,String> result=new TreeMap<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)throw new IOException("Entrada inválida no resumo público.");String id=row.optString("domId",""),key=row.optString("key",""),hash=row.optString("messageHash","");if(!id.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")||!key.matches("(?i)[0-9a-f]{64}")||!hash.matches("(?i)[0-9a-f]{64}")||result.put(key,hash)!=null)throw new IOException("ID, chave ou hash inválido/duplicado no resumo público.");}return result;
    }
    private static Map<String,String> publicMessageDomMap(JSONArray rows)throws IOException{Map<String,String> result=new TreeMap<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);String id=row==null?"":row.optString("domId",""),hash=row==null?"":row.optString("messageHash","");if(!id.matches("(?i)-?[0-9]{1,32}(?:_[0-9a-z:._-]{1,96})?")||!hash.matches("(?i)[0-9a-f]{64}")||result.put(id,hash)!=null)throw new IOException("ID DOM inválido ou duplicado no resumo público.");}return result;}
    private static String normalizePublicRecord(String s){String n=s.replace("\r\n","\n").replace('\r','\n');while(n.endsWith("\n"))n=n.substring(0,n.length()-1);return n+"\n";}
    private static String publicHashList(List<Capture> captures,String rootHash){StringBuilder out=new StringBuilder("TODAS_AS_CAPTURAS|").append(rootHash).append('\n');List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));for(Capture c:ordered)out.append(c.name).append('|').append(c.hash).append('\n');return out.toString();}
    private static void copyToClipboard(java.awt.Component owner,String value,String message){
        Thread worker=new Thread(()->{
            String error=null;
            try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);}
            catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final String failure=error;
            SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(owner,failure==null?message:"Não foi possível copiar automaticamente.\n"+failure,failure==null?"Hashes":"Cópia",failure==null?JOptionPane.INFORMATION_MESSAGE:JOptionPane.ERROR_MESSAGE));
        },"integridade-verifier-clipboard");
        worker.setDaemon(true);worker.start();
    }
    private static List<Capture> discover(Path root) throws IOException {return discover(root,null);}
    private static List<Capture> discover(Path root,IntConsumer progress) throws IOException {
        List<Capture> out=new ArrayList<>();
        try(java.util.stream.Stream<Path> s=Files.list(root)) {java.util.Iterator<Path> it=s.iterator();long scanned=0;while(it.hasNext()){if(Thread.currentThread().isInterrupted())throw new CancellationException("Busca cancelada");Path p=it.next();scanned++;if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;String n=p.getFileName().toString();if(n.startsWith("_backup_chat_capture_")||n.equals("vinculado")||n.equals(".iped-reextract-trash")||n.contains(".__old_")||n.contains(".__reextract_tmp_")||n.contains(".__reextract_failed_"))continue;if(Files.isRegularFile(p.resolve("manifest.json"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("whatsapp.html"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("relatorio.htm"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(p.resolve("texto.txt"),LinkOption.NOFOLLOW_LINKS)){if(out.size()>=MAX_CAPTURE_CANDIDATES)throw new IOException("A pasta excede o limite de "+MAX_CAPTURE_CANDIDATES+" capturas candidatas; a busca foi interrompida.");Capture c=new Capture();c.dir=p;c.name=n;out.add(c);if(progress!=null&&out.size()%100==0)progress.accept(out.size());}}if(progress!=null)progress.accept(out.size());}
        out.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT))); return out;
    }
    private static List<Capture> chooseCaptures(List<Capture> captures) throws Exception {
        final int pageSize=100; int page=0; final boolean[] allFuture={false};Set<String> selectedIds=new HashSet<>();
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        final List<Capture> out=new ArrayList<>();
        while(true){int start=page*pageSize,end=Math.min(captures.size(),start+pageSize);JPanel list=new JPanel();list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));List<JCheckBox> boxes=new ArrayList<>();
            for(int i=start;i<end;i++){Capture c=captures.get(i);String id=c.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);JCheckBox b=new JCheckBox(c.name,allFuture[0]||selectedIds.contains(id));boxes.add(b);list.add(b);}
            JLabel status=new JLabel("Exibindo "+(start+1)+"–"+end+" de "+captures.size()+" capturas");JButton bulk=new JButton(allFuture[0]?"Desmarcar todos":"Marcar todos");
            bulk.addActionListener(e->{allFuture[0]=!allFuture[0];selectedIds.clear();for(JCheckBox b:boxes)b.setSelected(allFuture[0]);bulk.setText(allFuture[0]?"Desmarcar todos":"Marcar todos");});
            for(int i=0;i<boxes.size();i++){final Capture c=captures.get(start+i);boxes.get(i).addActionListener(e->{String id=c.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);boolean checked=((JCheckBox)e.getSource()).isSelected();if(allFuture[0]){for(Capture existing:captures){String existingId=existing.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);if(!existingId.equals(id))selectedIds.add(existingId);}allFuture[0]=false;bulk.setText("Marcar todas");}if(checked)selectedIds.add(id);else selectedIds.remove(id);});}
            JPanel head=new JPanel(new java.awt.BorderLayout());head.add(status,java.awt.BorderLayout.CENTER);head.add(bulk,java.awt.BorderLayout.EAST);JPanel panel=new JPanel(new java.awt.BorderLayout(6,6));panel.add(head,java.awt.BorderLayout.NORTH);JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(Math.min(800,screen.width-80),Math.min(560,screen.height-160)));panel.add(scroll,java.awt.BorderLayout.CENTER);
            JButton prev=new JButton("Anterior");prev.setEnabled(page>0);JButton next=new JButton("Próximo");next.setEnabled(end<captures.size());Object[] options={prev,next,"Confirmar","Cancelar"};int choice=JOptionPane.showOptionDialog(null,panel,"Verificador de Integridade - v"+VERIFIER_VERSION+" - Selecione as capturas",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,options,options[2]);
            for(int i=0;i<boxes.size();i++){String id=captures.get(start+i).dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);if(boxes.get(i).isSelected())selectedIds.add(id);else selectedIds.remove(id);}
            if(choice==0){page=Math.max(0,page-1);continue;}if(choice==1){page=Math.min((captures.size()-1)/pageSize,page+1);continue;}if(choice!=2)return out;
            for(Capture c:captures)if(allFuture[0]||selectedIds.contains(c.dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT)))out.add(c);return out;
        }
    }
    private static void verify(Capture c) throws Exception {verify(c,MAX_CAPTURE_FILES);}
    private static void verify(Capture c,int maxItemsAllowed) throws Exception {
        Path formatManifest=c.dir.resolve("manifest.json");
        JSONObject format=null;
        if(Files.isRegularFile(formatManifest,LinkOption.NOFOLLOW_LINKS)){
            format=parseBoundedJson(readUtf8Limited(formatManifest),formatManifest);
        }
        Path integrityPath=c.dir.resolve("integridade.json");
        JSONObject integrity=null;
        if(Files.isRegularFile(integrityPath, LinkOption.NOFOLLOW_LINKS)) {
            c.integrityFileHash=hash(integrityPath);
            try { integrity=parseBoundedJson(readUtf8Limited(integrityPath),integrityPath); }
            catch(RuntimeException invalid) { c.historyStatus="registro inválido"; }
        }
        if(format!=null&&"html-message-v1".equals(format.optString("captureFormat"))){
            verifyMessageCapture(c,format);
            String messageManifestStatus=c.historyStatus;
            if(integrity!=null&&integrity.optInt("schemaVersion",0)>=2){
                verifyHistory(c,integrity);
                validateMigrationIntegrity(c,format,integrity);
                String localHistoryStatus=c.historyStatus;
                c.historyStatus=localHistoryStatus+"; "+messageManifestStatus;
            }else{
                if(format.has("migration"))
                    throw new IOException("Uma captura migrada precisa de uma cadeia local de integridade para validar migration.json.");
                if(Files.exists(c.dir.resolve("migration.json"),LinkOption.NOFOLLOW_LINKS)
                        ||Files.exists(c.dir.resolve("legacy-annotations.json"),LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Artefato legado de migração sem metadados no manifesto.");
            }
            return;
        }
        if(integrity!=null && integrity.optInt("schemaVersion",0)>=2) verifyHistory(c,integrity);
        Path inventoryFile=Files.isRegularFile(c.dir.resolve("integridade.json"),LinkOption.NOFOLLOW_LINKS)?c.dir.resolve("integridade.json"):c.dir.resolve("manifest.json");
        JSONObject inventory=integrity!=null?integrity:parseBoundedJson(readUtf8Limited(inventoryFile),inventoryFile);
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
            i.actual=hash(f);i.bytes=Files.size(f);i.state=i.expected.equalsIgnoreCase(i.actual)&&i.size==i.bytes?"integro":"alterado";c.items.add(i);
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
        for(Path f:allFiles){String rel=c.dir.relativize(f).toString().replace('\\','/');long size=Files.size(f);String sha=hash(f);String line=rel+'|'+size+'|'+sha+'\n';canonical.update(line.getBytes(StandardCharsets.UTF_8));c.bytes+=size;c.fileCount++;if(!rel.equals("manifest.json")&&!rel.equals("integridade.json")&&!rel.equals("reextracao-auditoria.jsonl")&&!seen.contains(rel)){Item x=new Item();x.path=rel;x.actual=sha;x.bytes=size;x.state="extra";c.items.add(x);}}
        c.hash=hex(canonical.digest());
        c.items.sort(Comparator.comparing(i->i.path));
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
                ||Files.size(migrationPath)>MAX_METADATA_BYTES||!hash(migrationPath).equalsIgnoreCase(migrationSha))
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
        if(migrationEntry==null||migrationEntry.optLong("size",-1)!=Files.size(migrationPath)
                ||!migrationEntry.optString("sha256","").equalsIgnoreCase(hash(migrationPath)))
            throw new IOException("migration.json não está vinculado ao inventário da cadeia local de integridade.");
        addIntegrityArtifactItem(c,"migration.json",migrationEntry,"registro de migração íntegro");

        boolean annotationsPreserved=migration.optBoolean("legacyAnnotationsPreserved",false);
        boolean annotationsExist=annotationsPath!=null&&Files.isRegularFile(annotationsPath,LinkOption.NOFOLLOW_LINKS);
        if(annotationsPreserved!=annotationsExist)throw new IOException("Arquivo de anotações legadas diverge dos metadados da migração.");
        if(annotationsPreserved){
            if(Files.size(annotationsPath)>MAX_METADATA_BYTES)throw new IOException("Anotações legadas excedem o limite de metadados.");
            parseBoundedJson(readUtf8Limited(annotationsPath),annotationsPath);
            JSONObject annotationEntry=findIntegrityArtifact(artifacts,"legacy-annotations.json");
            if(annotationEntry==null||annotationEntry.optLong("size",-1)!=Files.size(annotationsPath)
                    ||!annotationEntry.optString("sha256","").equalsIgnoreCase(hash(annotationsPath)))
                throw new IOException("legacy-annotations.json não está vinculado à cadeia local de integridade.");
            addIntegrityArtifactItem(c,"legacy-annotations.json",annotationEntry,"anotações legadas preservadas");
        }else if(findIntegrityArtifact(artifacts,"legacy-annotations.json")!=null){
            throw new IOException("Inventário declara anotações legadas que o manifesto não marcou como preservadas.");
        }
        c.fileCount+=(annotationsPreserved?2:1);
        c.bytes+=Files.size(migrationPath)+(annotationsPreserved?Files.size(annotationsPath):0);
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
        item.actual=hash(file);item.bytes=Files.size(file);item.state=item.actual.equalsIgnoreCase(item.expected)&&item.bytes==item.size?state:"alterado";c.items.add(item);
    }

    private static void verifyMessageCapture(Capture c,JSONObject manifest) throws Exception {
        if(manifest.optInt("schemaVersion",0)!=1||!"4.3.1".equals(manifest.optString("ipedVersion"))||!"completed".equals(manifest.optString("status"))||!"complete".equals(manifest.optString("state")))throw new IOException("Formato de captura HTML, estado ou versão IPED incompatível.");
        JSONObject sourceMetadata=manifest.optJSONObject("sourceMetadata"),anchors=manifest.optJSONObject("anchors");
        if(sourceMetadata==null||anchors==null||sourceMetadata.optString("name","").length()>1024||sourceMetadata.optLong("size",-1)<0||sourceMetadata.optString("type","").length()>256||sourceMetadata.optString("itemHash","").length()>128||!manifest.optString("sourceFingerprint","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("conversationKey","").matches("(?i)[0-9a-f]{64}")||!manifest.optString("captureRootHash","").matches("(?i)[0-9a-f]{64}"))throw new IOException("Metadados de origem, âncoras ou hashes do manifesto são inválidos.");
        String messagesName=manifest.optString("messagesFile","");if(!"messages.json".equals(messagesName))throw new IOException("Arquivo de mensagens inválido.");
        Path messagesPath=resolve(c.dir,messagesName);if(messagesPath==null)throw new IOException("Caminho do manifesto de mensagens inválido.");
        JSONObject wrapper=parseBoundedJson(readUtf8Limited(messagesPath),messagesPath);JSONArray messages=wrapper.optJSONArray("messages");
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
                Path file=resolve(c.dir,path);if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=size||!hash(file).equalsIgnoreCase(sha)||!mimeSignatureMatches(mime,file))throw new IOException("Anexo divergente ou MIME incompatível na mensagem "+id+": "+path);
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
        Path htmlPath=c.dir.resolve("whatsapp.html");if(!Files.isRegularFile(htmlPath,LinkOption.NOFOLLOW_LINKS))throw new IOException("HTML da captura não encontrado.");
        if(Files.size(htmlPath)>MAX_METADATA_BYTES*4L)throw new IOException("HTML da captura excede o limite de leitura segura.");
        String html=readUtf8Limited(htmlPath);String htmlSha=manifest.optString("htmlSha256","");if(!htmlSha.matches("(?i)[0-9a-f]{64}")||!hash(htmlPath).equalsIgnoreCase(htmlSha))throw new IOException("Hash do HTML ausente ou divergente.");
        validateRenderedCaptureHtml(html,attachmentPaths,attachmentMimeByPath,keys,manifest.optInt("presentationVersion",0));
        String annotationsSha=manifest.optString("annotationsSha256","");if(!annotationsSha.isEmpty()){if(!annotationsSha.matches("(?i)[0-9a-f]{64}"))throw new IOException("Hash de anotações inválido.");Path annotationPath=resolve(c.dir,"annotations.json");if(annotationPath==null||!Files.isRegularFile(annotationPath,LinkOption.NOFOLLOW_LINKS)||Files.size(annotationPath)>MAX_METADATA_BYTES||!hash(annotationPath).equalsIgnoreCase(annotationsSha))throw new IOException("Arquivo de anotações ausente, excessivo ou divergente.");parseBoundedJson(readUtf8Limited(annotationPath),annotationPath);Item annotationsItem=new Item();annotationsItem.path="annotations.json";annotationsItem.expected=annotationsSha;annotationsItem.actual=annotationsSha;annotationsItem.bytes=Files.size(annotationPath);annotationsItem.state="anotações íntegras";c.items.add(annotationsItem);}else if(Files.exists(c.dir.resolve("annotations.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Arquivo de anotações sem hash no manifesto.");
        String reconciliationSha=manifest.optString("reconciliationSha256","");
        Map<String,String> reconciliationStates=verifyMessageReconciliation(c,manifest,currentMessages,reconciliationSha);
        for(Item item:c.items){if(item.path.startsWith("mensagem:")){String id=item.path.substring("mensagem:".length());String state=reconciliationStates.get(id);if(state!=null)item.state=state;}}
        c.hash=htmlCaptureRootHash(messages,htmlSha,annotationsSha,reconciliationSha,manifest);
        c.items.sort(Comparator.comparing(i->i.path));c.fileCount=messages.length()+attachmentPaths.size()+2+(annotationsSha.isEmpty()?0:1)+(reconciliationSha.isEmpty()?0:1);c.bytes=Files.size(htmlPath)+Files.size(messagesPath)+Files.size(c.dir.resolve("manifest.json"))+attachmentBytes+(annotationsSha.isEmpty()?0:Files.size(c.dir.resolve("annotations.json")))+(reconciliationSha.isEmpty()?0:Files.size(c.dir.resolve("message-reconciliation.json")));if(c.bytes>MAX_HTML_CAPTURE_BYTES||c.fileCount>MAX_CAPTURE_FILES)throw new IOException("Captura HTML ultrapassa os limites totais de bytes ou arquivos.");
        c.historyStatus=manifest.optString("captureRootHash","").equalsIgnoreCase(c.hash)?"consistente com registro local (manifesto de mensagens)":"manifesto de mensagens requer referência pública";
    }

    private static void validateRenderedCaptureHtml(String html,Set<String> attachmentPaths,Map<String,String> mimeByPath,Set<String> messageKeys,int presentationVersion)throws Exception{
        if(presentationVersion==0){validateLegacyRenderedHtml(html,attachmentPaths,messageKeys);return;}
        if(presentationVersion!=2)throw new IOException("Versão de apresentação HTML desconhecida.");
        String csp=validateCaptureMeta(html,true);
        Matcher scripts=Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script\\s*>").matcher(html);int scriptCount=0;String script="";while(scripts.find()){scriptCount++;if(!scripts.group(1).trim().isEmpty())throw new IOException("Atributos de script não são permitidos.");script=scripts.group(2);}Matcher scriptOpen=Pattern.compile("(?is)<script\\b").matcher(html);int scriptOpenCount=0;while(scriptOpen.find())scriptOpenCount++;if(scriptCount!=1||scriptOpenCount!=1)throw new IOException("HTML deve conter somente um script fixo de mídia.");boolean currentScript=CAPTURE_MEDIA_STOP_SCRIPT.equals(script),legacyScript=LEGACY_CAPTURE_MEDIA_STOP_SCRIPT.equals(script);if(!currentScript&&!legacyScript)throw new IOException("HTML contém script diferente dos scripts fixos autorizados.");if(currentScript&&!CAPTURE_CSP.equals(csp)||legacyScript&&!LEGACY_CAPTURE_CSP.equals(csp))throw new IOException("A CSP não corresponde ao script fixo autorizado.");
        validateHtmlStructure(html,attachmentPaths,mimeByPath,messageKeys,true);
        Matcher styles=Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style\\s*>").matcher(html);int styleCount=0;while(styles.find()){styleCount++;validateCaptureCss(styles.group(1));}if(styleCount!=2)throw new IOException("HTML deve conter o tema IPED e uma folha de estilos do visualizador.");
    }

    private static void validateLegacyRenderedHtml(String html,Set<String> attachmentPaths,Set<String> messageKeys)throws Exception{
        validateCaptureMeta(html,false);
        if(Pattern.compile("(?is)<(script|iframe|object|embed|form|link|base|svg|math)\\b|\\son[a-z0-9_-]+\\s*=|javascript:").matcher(html).find())throw new IOException("HTML da captura possui conteúdo ativo ou dependência externa.");
        validateHtmlStructure(html,attachmentPaths,Collections.emptyMap(),messageKeys,false);
        Matcher styles=Pattern.compile("(?is)<style\\b[^>]*>(.*?)</style\\s*>").matcher(html);while(styles.find())validateCaptureCss(styles.group(1));
    }

    private static String validateCaptureMeta(String html,boolean modern)throws IOException{
        Matcher tags=Pattern.compile("(?is)<meta\\b([^>]*)>").matcher(html);int count=0,charset=0,csp=0;
        String legacyCsp="default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'";
        String cspContent="";
        while(tags.find()){
            count++;Map<String,String> attrs=parseHtmlAttributes(tags.group(1));
            if(attrs.containsKey("charset")){
                if(attrs.size()!=1||!"utf-8".equalsIgnoreCase(attrs.get("charset")))throw new IOException("Meta charset fora da allowlist.");charset++;
            }else if(attrs.containsKey("http-equiv")){
                String content=attrs.get("content");boolean allowed=modern?(CAPTURE_CSP.equals(content)||LEGACY_CAPTURE_CSP.equals(content)):legacyCsp.equals(content);
                if(attrs.size()!=2||!"content-security-policy".equalsIgnoreCase(attrs.get("http-equiv"))||!allowed)throw new IOException("Política CSP ausente ou não canônica.");csp++;cspContent=content;
            }else throw new IOException("Meta não permitido no HTML da captura.");
        }
        if(modern?(count!=2||charset!=1||csp!=1):!((count==0)||(count==2&&charset==1&&csp==1)))throw new IOException("Quantidade de metadados HTML fora da allowlist.");
        return cspContent;
    }

    private static Map<String,String> parseHtmlAttributes(String input)throws IOException{
        Map<String,String> attrs=new HashMap<>();Matcher matcher=Pattern.compile("(?is)([a-z_:][a-z0-9_:.-]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?").matcher(input);int end=0;
        while(matcher.find()){String gap=input.substring(end,matcher.start()).trim();if(!gap.isEmpty()&&!gap.matches("(?is)/?"))throw new IOException("Atributo HTML malformado.");String name=matcher.group(1).toLowerCase(Locale.ROOT);String value=matcher.group(2)!=null?matcher.group(2):matcher.group(3)!=null?matcher.group(3):matcher.group(4)!=null?matcher.group(4):"";if(attrs.put(name,value)!=null)throw new IOException("Atributo HTML duplicado.");end=matcher.end();}
        String tail=input.substring(end).trim();if(!tail.isEmpty()&&!tail.matches("(?is)/?"))throw new IOException("Atributo HTML sem valor entre aspas.");return attrs;
    }

    private static void validateHtmlStructure(String html,Set<String> attachmentPaths,Map<String,String> mimeByPath,Set<String> messageKeys,boolean modern)throws Exception{
        Matcher tags=Pattern.compile("(?is)<([a-z][a-z0-9]*)\\b([^>]*)>").matcher(html);Set<String> ids=new HashSet<>(),renderedKeys=new HashSet<>(),resources=new HashSet<>();List<String> pendingFragments=new ArrayList<>();
        while(tags.find()){
            String tag=tags.group(1).toLowerCase(Locale.ROOT);Map<String,String> attrs=parseHtmlAttributes(tags.group(2));
            if("meta".equals(tag)||"script".equals(tag))continue;
            if("object".equals(tag)||"embed".equals(tag)||"form".equals(tag)||"link".equals(tag)||"base".equals(tag)||"svg".equals(tag)||"math".equals(tag))throw new IOException("HTML contém elemento ativo ou não permitido.");
            for(String name:attrs.keySet())if(name.startsWith("on")||"style".equals(name)||"srcdoc".equals(name)||"action".equals(name)||"formaction".equals(name))throw new IOException("HTML contém atributo ativo não permitido.");
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
        if(!renderedKeys.equals(messageKeys))throw new IOException("HTML não corresponde exatamente às mensagens do manifesto.");
        if(!resources.equals(attachmentPaths))throw new IOException("Recursos locais do HTML não correspondem aos anexos do manifesto.");
        for(String fragment:pendingFragments)if(!ids.contains(fragment))throw new IOException("Link de fragmento sem destino único: #"+fragment);
    }

    private static boolean validateEmbeddedImage(String uri)throws IOException{
        Matcher m=Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp));base64,([a-z0-9+/=\\r\\n]+)$").matcher(uri);if(!m.matches())return false;byte[] bytes;try{bytes=Base64.getMimeDecoder().decode(m.group(2));}catch(IllegalArgumentException invalid){return false;}if(bytes.length==0||bytes.length>8*1024*1024)return false;String mime=m.group(1).toLowerCase(Locale.ROOT);if(captureRasterSignatureMatches(mime,bytes))return true;return "image/jpg".equals(mime)&&captureRasterSignatureMatches("image/png",bytes);
    }

    private static void validateCaptureCss(String css)throws Exception{
        if(css.getBytes(StandardCharsets.UTF_8).length>2*1024*1024||Pattern.compile("(?i)</style|@import|expression\\s*\\(|(?:^|[;{])\\s*behavior\\s*:|-moz-binding|javascript\\s*:").matcher(css).find())throw new IOException("CSS da captura contém regra ativa ou excede o limite.");
        Pattern urlPattern=Pattern.compile("(?is)url\\(\\s*([\\\"']?)(.*?)\\1\\s*\\)");Matcher urls=urlPattern.matcher(css);String clean=urlPattern.matcher(css).replaceAll("");if(Pattern.compile("(?i)url\\s*\\(").matcher(clean).find())throw new IOException("URL CSS malformada.");long total=0;
        while(urls.find()){Matcher image=Pattern.compile("(?is)^data:(image/(?:png|jpe?g|gif|webp|svg\\+xml));base64,([a-z0-9+/=\\r\\n]+)$").matcher(urls.group(2).trim());if(!image.matches())throw new IOException("CSS contém dependência externa ou URL não permitida.");byte[] bytes;try{bytes=Base64.getMimeDecoder().decode(image.group(2));}catch(IllegalArgumentException invalid){throw new IOException("Base64 inválido em recurso CSS.",invalid);}if(bytes.length==0||bytes.length>8*1024*1024)throw new IOException("Imagem CSS acima de 8 MiB.");total+=bytes.length;if(total>16*1024*1024)throw new IOException("Imagens CSS acima de 16 MiB no total.");String mime=image.group(1).toLowerCase(Locale.ROOT);if("image/svg+xml".equals(mime))validateCaptureSvg(bytes);else if(!captureRasterSignatureMatches(mime,bytes))throw new IOException("Assinatura/MIME divergente em imagem CSS.");}
    }

    private static boolean captureRasterSignatureMatches(String mime,byte[] b){if("image/jpg".equals(mime))mime="image/jpeg";if("image/png".equals(mime))return b.length>=8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;if("image/jpeg".equals(mime))return b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;if("image/gif".equals(mime))return b.length>=6&&("GIF87a".equals(new String(b,0,6,StandardCharsets.US_ASCII))||"GIF89a".equals(new String(b,0,6,StandardCharsets.US_ASCII)));return "image/webp".equals(mime)&&b.length>=12&&"RIFF".equals(new String(b,0,4,StandardCharsets.US_ASCII))&&"WEBP".equals(new String(b,8,4,StandardCharsets.US_ASCII));}

    private static void validateCaptureSvg(byte[] bytes)throws Exception{
        if(bytes.length>512*1024)throw new IOException("SVG CSS acima de 512 KiB.");String xml=new String(bytes,StandardCharsets.UTF_8);if(Pattern.compile("(?i)<!\\s*(?:DOCTYPE|ENTITY)").matcher(xml).find())throw new IOException("SVG não pode conter DTD ou entidade.");DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");Document d=f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));Element root=d.getDocumentElement();if(root==null||!"svg".equalsIgnoreCase(root.getLocalName()==null?root.getTagName():root.getLocalName()))throw new IOException("Raiz SVG inválida.");NodeList all=d.getElementsByTagName("*");if(all.getLength()>4096)throw new IOException("SVG excede 4.096 elementos.");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);String name=e.getLocalName()==null?e.getTagName():e.getLocalName();if("script".equalsIgnoreCase(name)||"foreignObject".equalsIgnoreCase(name)||"iframe".equalsIgnoreCase(name)||"object".equalsIgnoreCase(name)||"embed".equalsIgnoreCase(name))throw new IOException("SVG contém elemento ativo.");NamedNodeMap attrs=e.getAttributes();for(int j=0;j<attrs.getLength();j++){Node a=attrs.item(j);String n=a.getNodeName().toLowerCase(Locale.ROOT),v=a.getNodeValue();if(n.startsWith("on")||(("href".equals(n)||n.endsWith(":href")||"src".equals(n))&&!v.startsWith("#"))||Pattern.compile("(?i)url\\s*\\(\\s*(?:https?:|file:|javascript:|//)").matcher(v).find())throw new IOException("SVG contém referência externa ou evento.");}}
    }

    private static String htmlMessageHash(JSONObject message)throws Exception{StringBuilder canonical=new StringBuilder("html-message-v1-message\n");appendHtmlField(canonical,message.optString("domId",""));appendHtmlField(canonical,Integer.toString(message.optInt("order",-1)));appendHtmlField(canonical,message.optString("timestamp",""));appendHtmlField(canonical,message.optString("sender",""));appendHtmlField(canonical,message.optString("direction",""));appendHtmlField(canonical,message.optString("type",""));appendHtmlField(canonical,Integer.toString(message.optInt("duplicateOrdinal",-1)));appendHtmlField(canonical,message.optString("text",""));appendHtmlField(canonical,message.optString("classes",""));JSONArray attachments=message.optJSONArray("attachments");for(int i=0;attachments!=null&&i<attachments.length();i++){JSONObject a=attachments.getJSONObject(i);appendHtmlField(canonical,a.optString("path",""));appendHtmlField(canonical,a.optString("mime",""));appendHtmlField(canonical,Long.toString(a.optLong("size",-1)));appendHtmlField(canonical,a.optString("sha256",""));}return hashText(canonical.toString());}
    private static boolean mimeSignatureMatches(String mime,Path file)throws IOException{
        byte[] b=new byte[12];int length=0;
        try(InputStream in=Files.newInputStream(file)){while(length<b.length){int n=in.read(b,length,b.length-length);if(n<0)break;length+=n;}}
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
        if(!reconciliationSha.matches("(?i)[0-9a-f]{64}")||!"message-reconciliation.json".equals(reconciliationName)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_METADATA_BYTES||!hash(path).equalsIgnoreCase(reconciliationSha))throw new IOException("Arquivo de reconciliação ausente, excessivo ou divergente.");
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
        Item report=new Item();report.path="message-reconciliation.json";report.expected=reconciliationSha;report.actual=reconciliationSha;report.bytes=Files.size(path);report.state="reconciliação por mensagem íntegra";c.items.add(report);return statesById;
    }
    private static String htmlCaptureRootHash(JSONArray messages,String htmlSha,String annotationsSha,String reconciliationSha,JSONObject manifest)throws Exception{StringBuilder canonical=new StringBuilder("html-message-v1-root\n");appendHtmlField(canonical,htmlSha);appendHtmlField(canonical,annotationsSha);appendHtmlField(canonical,reconciliationSha);appendHtmlField(canonical,manifest.optString("conversationKey",""));appendHtmlField(canonical,manifest.optString("sourceFingerprint",""));JSONObject source=manifest.optJSONObject("sourceMetadata");appendHtmlField(canonical,source==null?"":source.optString("name",""));appendHtmlField(canonical,source==null?"":Long.toString(source.optLong("size",-1)));appendHtmlField(canonical,source==null?"":source.optString("type",""));appendHtmlField(canonical,source==null?"":source.optString("itemHash",""));appendHtmlField(canonical,source==null?"":Boolean.toString(source.optBoolean("deleted",false)));appendHtmlField(canonical,manifest.optString("ipedVersion",""));appendHtmlField(canonical,manifest.optString("pluginVersion",""));JSONObject anchors=manifest.optJSONObject("anchors");appendHtmlField(canonical,anchors==null?"":anchors.optString("start",""));appendHtmlField(canonical,anchors==null?"":anchors.optString("end",""));for(int i=0;i<messages.length();i++){JSONObject message=messages.getJSONObject(i);appendHtmlField(canonical,message.optString("key",""));appendHtmlField(canonical,message.optString("messageHash",""));}return hashText(canonical.toString());}
    private static void appendHtmlField(StringBuilder canonical,String value){String field=value==null?"":value;canonical.append(field.length()).append(':').append(field).append('\n');}
    private static void verifyHistory(Capture c,JSONObject record) throws Exception {
        JSONArray artifacts=record.optJSONArray("artifacts"), events=record.optJSONArray("events");
        if(artifacts==null||events==null||events.length()==0){c.historyStatus="registro incompleto";return;}
        StringBuilder content=new StringBuilder();
        List<JSONObject> sorted=new ArrayList<>();
        for(int i=0;i<artifacts.length();i++){JSONObject item=artifacts.optJSONObject(i);if(item==null){c.historyStatus="inventário inválido";return;}sorted.add(item);}
        sorted.sort(Comparator.comparing(x->x.optString("path","")));
        for(JSONObject item:sorted)content.append(item.optString("path","")).append('|').append(item.optLong("size",0)).append('|').append(item.optString("sha256","")).append('\n');
        String inventoryRoot=hashText(content.toString());
        c.contentRootHash=record.optString("contentRootHash","");
        c.historyEvents=events;
        if(!inventoryRoot.equalsIgnoreCase(record.optString("contentRootHash",""))){c.historyStatus="raiz do registro divergente";return;}
        String previous="";
        for(int i=0;i<events.length();i++){
            JSONObject event=events.optJSONObject(i);if(event==null||event.optInt("sequence",-1)!=i+1||!previous.equals(event.optString("previousRecordHash",""))){c.historyStatus="cadeia do histórico quebrada";return;}
            String expected=event.optString("recordHash","");JSONObject payload=new JSONObject(event.toString());payload.remove("recordHash");String actual=hashText(payload.toString());
            if(!expected.equalsIgnoreCase(actual)){c.historyStatus="hash de evento inválido";return;}previous=expected;
        }
        c.historyHash=previous;
        StringBuilder current=new StringBuilder();for(JSONObject item:sorted){String path=item.optString("path","");Path file=resolve(c.dir,path);if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){c.historyStatus="arquivo ausente: "+path;return;}String actual=hash(file);if(!actual.equalsIgnoreCase(item.optString("sha256",""))||Files.size(file)!=item.optLong("size",-1)){c.historyStatus="arquivo divergente: "+path;return;}current.append(path).append('|').append(Files.size(file)).append('|').append(actual).append('\n');}
        c.historyStatus=hashText(current.toString()).equalsIgnoreCase(record.optString("contentRootHash",""))?"consistente com registro local":"conteúdo diverge do registro local";
    }
    private static Path resolve(Path root,String rel){try{if(rel==null||rel.isEmpty()||rel.length()>MAX_RELATIVE_PATH_CHARS||rel.indexOf('\0')>=0||rel.indexOf(':')>=0)return null;Path normalizedRoot=root.toAbsolutePath().normalize();Path r=normalizedRoot.getFileSystem().getPath(rel.replace('\\','/'));if(r.isAbsolute()||r.getNameCount()==0)return null;for(Path p:r)if(p.toString().equals("..")||p.toString().equals("."))return null;Path f=normalizedRoot.resolve(r).normalize();if(!f.startsWith(normalizedRoot)||f.equals(normalizedRoot))return null;Path q=normalizedRoot;for(Path p:normalizedRoot.relativize(f)){q=q.resolve(p);if(Files.isSymbolicLink(q))return null;}return f;}catch(Exception e){return null;}}
    private static String field(String s,String name){Matcher m=Pattern.compile("\\\""+Pattern.quote(name)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(s);return m.find()?m.group(1).replace("\\/","/").replace("\\\"","\"").replace("\\\\","\\"):null;}
    private static long number(String s,String n){Matcher m=Pattern.compile("\\\""+n+"\\\"\\s*:\\s*(\\d+)").matcher(s);return m.find()?Long.parseLong(m.group(1)): -1;}
    private static String hash(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance(ALG);try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[]b=new byte[32768];int n;while((n=in.read(b))>0)d.update(b,0,n);}return hex(d.digest());}
    private static String readUtf8Limited(Path p)throws IOException{long size=Files.size(p);if(size>MAX_METADATA_BYTES)throw new IOException("Arquivo de metadados excede o limite de "+MAX_METADATA_BYTES+" bytes: "+p);try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream((int)size)){byte[]buffer=new byte[8192];int n;int total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_METADATA_BYTES)throw new IOException("Arquivo de metadados excede o limite de "+MAX_METADATA_BYTES+" bytes: "+p);out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static JSONObject parseBoundedJson(String json,Path source)throws IOException{int depth=0;boolean quoted=false,escaped=false;for(int i=0;i<json.length();i++){char ch=json.charAt(i);if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;continue;}if(ch=='"'){quoted=true;continue;}if(ch=='{'||ch=='['){if(++depth>MAX_JSON_DEPTH)throw new IOException("JSON excede a profundidade máxima de "+MAX_JSON_DEPTH+" níveis: "+source);}else if(ch=='}'||ch==']'){if(--depth<0)throw new IOException("JSON malformado: "+source);}}if(quoted||depth!=0)throw new IOException("JSON incompleto: "+source);try{return new JSONObject(json);}catch(RuntimeException invalid){throw new IOException("JSON inválido: "+source,invalid);}}
    private static List<Path> listCaptureFiles(Path root)throws IOException{List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.walk(root)){java.util.Iterator<Path> iterator=stream.iterator();while(iterator.hasNext()){Path p=iterator.next();if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){if(files.size()>=MAX_CAPTURE_FILES)throw new IOException("Captura excede o limite de "+MAX_CAPTURE_FILES+" arquivos: "+root);files.add(p);}}}return files;}
    private static String quietHash(Path p){try{return hash(p);}catch(Exception e){return null;}}
    private static long quietSize(Path p){try{return Files.size(p);}catch(Exception e){return 0;}}
    private static String hashText(String s)throws Exception{return hex(MessageDigest.getInstance(ALG).digest(s.getBytes(StandardCharsets.UTF_8)));}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    private static String setHash(List<Capture> cs)throws Exception{List<Capture>x=new ArrayList<>(cs);x.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));StringBuilder b=new StringBuilder();for(Capture c:x)b.append(c.name).append('|').append(c.hash).append('|').append(c.bytes).append('|').append(c.fileCount).append('\n');return hashText(b.toString());}
    static String anonymizeCaptureName(String captureName){if(captureName==null||captureName.trim().isEmpty())return "*";Matcher m=CAPTURE_TIMESTAMP_SUFFIX.matcher(captureName);return m.matches()?"*-"+m.group(1):"*";}
    private static String historyReport(List<Capture> captures,String collectionHash)throws IOException{return historyReport(captures,collectionHash,false);}
    static String publicHistoryReport(List<Capture> captures,String collectionHash)throws IOException{return historyReport(captures,collectionHash,true);}
    private static String historyReport(List<Capture> captures,String collectionHash,boolean anonymizeNames)throws IOException{
        enforceReportBudget(captures);
        StringBuilder out=new StringBuilder("HISTORICO_PUBLICO_V1\nSHA256|").append(collectionHash).append("\n");
        List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));
        for(Capture c:ordered){
            String displayName=anonymizeNames?anonymizeCaptureName(c.name):c.name;
            out.append("\nCAPTURA|").append(recordField(displayName)).append("\nSTATUS|").append(c.historyStatus).append("\nHASH_CONTEUDO|").append(c.contentRootHash.isEmpty()?c.hash:c.contentRootHash).append("\nHASH_INTEGRIDADE_JSON|").append(c.integrityFileHash.isEmpty()?"indisponivel":c.integrityFileHash).append("\nHASH_FINAL_HISTORICO|").append(c.historyHash.isEmpty()?"indisponivel":c.historyHash).append("\nEVENTOS|").append(c.historyEvents.length()).append('\n');
            for(int i=0;i<c.historyEvents.length();i++){JSONObject e=c.historyEvents.optJSONObject(i);if(e==null)continue;out.append("EVENTO|").append(e.optInt("sequence",i+1)).append('|').append(recordField(e.optString("timestampUtc","sem timestamp"))).append('|').append(recordField(e.optString("action","ação desconhecida"))).append("\nANTERIOR|").append(e.optString("previousRecordHash","")).append("\nRAIZ_EVENTO|").append(e.optString("contentRootHash","")).append("\nHASH_EVENTO|").append(e.optString("recordHash","")).append('\n');JSONArray changes=e.optJSONArray("changes");if(changes!=null)for(int j=0;j<changes.length();j++){JSONObject change=changes.optJSONObject(j);if(change!=null)out.append("ALTERACAO|").append(recordField(change.optString("action","mudança"))).append('|').append(recordField(change.optString("path",""))).append('|').append(change.optString("previousSha256","—")).append('|').append(change.optString("sha256","—")).append('\n');}}
            out.append("INVENTARIO_ARQUIVOS|").append(c.items.size()).append('\n');for(Item item:c.items)out.append("ARQUIVO|").append(recordField(item.path)).append('|').append(item.size).append('|').append(item.expected==null?"—":item.expected).append('|').append(item.bytes).append('|').append(item.actual==null?"—":item.actual).append('|').append(recordField(item.state==null?"desconhecido":item.state)).append('\n');
        }
        out.append("\nNota|O hash público compara exatamente este registro estável. A integridade dos arquivos também precisa estar consistente localmente. Use uma referência obtida fora da pasta verificada.\n");return out.toString();
    }
    private static String recordField(String s){return s==null?"":s.replace("\\","\\\\").replace("|","\\|").replace("\r","\\r").replace("\n","\\n");}
    private static void writeReports(Path root,List<Capture> cs,String rootHash,String time,String history)throws Exception{
        enforceReportBudget(cs);
        StringBuilder html=new StringBuilder("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Integridade das capturas</title><style>body{font:16px Arial;margin:2em;color:#20313a}table{border-collapse:collapse;width:100%;margin-bottom:1em}td,th{border:1px solid #bbb;padding:.5em;text-align:left;overflow-wrap:anywhere}.ok{color:green}.bad,.diverge{color:#8b0000;font-weight:700}.match{color:#006400;font-weight:700}input{padding:.5em;width:min(40em,90%)}code{overflow-wrap:anywhere}</style><h1>Relatório de integridade</h1><p>Verificação e relatório gerados em: ").append(esc(time)).append("</p><p>Hash geral das capturas selecionadas: <code>").append(rootHash).append("</code></p><label>Hash público geral do conjunto: <input id=\"publicHash\" placeholder=\"Cole aqui o hash geral recebido\"></label> <button onclick=\"compareHash()\">Comparar hash geral</button><p id=\"comparison\">Nenhum hash geral informado.</p>");
        StringBuilder txt=new StringBuilder("Verificado: ").append(time).append("\nTODAS_AS_CAPTURAS|").append(rootHash).append("\n");StringBuilder json=new StringBuilder("{\"generatedAt\":\"").append(time).append("\",\"rootHash\":\"").append(rootHash).append("\",\"captures\":[");
        html.append("<label for=\"hashList\">Cole a lista de hashes compartilhada pelo autor (formato captura|hash):</label><br><textarea id=\"hashList\" rows=\"6\" style=\"width:min(70em,95%)\" placeholder=\"TODAS_AS_CAPTURAS|hash-geral\ncaptura-chat-...|hash-da-captura\"></textarea><br><button onclick=\"compareList()\">Comparar lista em todas as capturas</button> <span id=\"listResult\"></span><table><tr><th>Captura</th><th>Hash calculado</th><th>Arquivos</th><th>Comparar hash desta captura</th></tr>");
        boolean first=true;for(Capture c:cs){long good=c.items.stream().filter(i->"integro".equals(i.state)).count();
            html.append("<tr class=\"capture-row\" data-capture=\"").append(esc(c.name)).append("\"><td>").append(esc(c.name)).append("</td><td><code>").append(c.hash).append("</code> <button data-value=\"").append(c.hash).append("\" onclick=\"copyValue(this)\">Copiar hash</button></td><td>").append(good).append(" íntegros / ").append(c.items.size()).append(" verificados<br>Histórico: ").append(esc(c.historyStatus)).append(c.historyHash.isEmpty()?"":"<br>Hash do registro: <code>"+esc(c.historyHash)+"</code> <button data-value=\""+esc(c.historyHash)+"\" onclick=\"copyValue(this)\">Copiar</button>").append("</td><td><label>Hash público: <input class=\"capture-hash\" data-expected=\"").append(c.hash).append("\" placeholder=\"Cole o hash desta captura\"></label> <button onclick=\"compareCapture(this)\">Comparar captura</button><br><span class=\"cmp\">Não comparado</span></td></tr>");
            txt.append(recordField(c.name)).append(" | ").append(c.hash).append("\n");
            if(!first)json.append(',');first=false;json.append("{\"name\":\"").append(jsonEsc(c.name)).append("\",\"hash\":\"").append(c.hash).append("\",\"files\":[");boolean f=true;for(Item i:c.items){if(!f)json.append(',');f=false;json.append("{\"path\":\"").append(jsonEsc(i.path)).append("\",\"status\":\"").append(jsonEsc(i.state)).append("\",\"expected\":\"").append(jsonEsc(i.expected==null?"":i.expected)).append("\",\"actual\":\"").append(jsonEsc(i.actual==null?"":i.actual)).append("\"}");}json.append("]}");
        }
        html.append("</table><h2>Arquivos</h2>");for(Capture c:cs){html.append("<h3>").append(esc(c.name)).append("</h3><table><tr><th>Arquivo</th><th>Estado</th><th>Esperado</th><th>Encontrado</th></tr>");for(Item i:c.items)html.append("<tr><td>").append(esc(i.path)).append("</td><td class=\"").append("integro".equals(i.state)?"ok":"bad").append("\">").append(esc(i.state)).append("</td><td><code>").append(esc(i.expected)).append("</code></td><td><code>").append(esc(i.actual)).append("</code></td></tr>");html.append("</table>");}html.append("<p>Este relatório é um retrato desta execução. O hash é um código de comparação, não uma assinatura digital.</p><p>Arquivos: <a href=\"integridade/hashes-capturas.txt\">lista de hashes</a> · <a href=\"integridade/verificacao-integridade.txt\">resumo TXT</a> · <a href=\"integridade/verificacao-integridade.json\">detalhes JSON</a></p><script>function show(el,text,ok){el.className=ok?'match':'diverge';el.textContent=text;}function compareHash(){var v=document.getElementById('publicHash').value.trim().toLowerCase(),r=document.getElementById('comparison'),ok=v==='" ).append(rootHash).append("';if(!v){r.className='';r.textContent='Informe um hash público.';return;}show(r,ok?'Confere com o hash geral calculado.':'Diverge do hash geral calculado.',ok);}function compareCapture(b){var p=b.parentNode,i=p.querySelector('input'),v=i.value.trim().toLowerCase(),ok=v===i.getAttribute('data-expected').toLowerCase(),r=p.querySelector('.cmp');if(!v){r.className='cmp';r.textContent='Informe um hash público.';return;}show(r,ok?'Confere com o hash da captura calculado.':'Diverge do hash da captura calculado.',ok);}function compareList(){var map={},lines=document.getElementById('hashList').value.split('\\n');lines.forEach(function(line){line=line.replace('\\r','').trim();var n=line.indexOf('|');if(n>0)map[line.substring(0,n).trim().toLowerCase()]=line.substring(n+1).trim().toLowerCase();});var root=map['todas_as_capturas']||map['root'];var rr=document.getElementById('listResult');if(root){var ok=root==='" ).append(rootHash).append("';show(rr,ok?'Hash geral confere.':'Diverge do hash geral calculado.',ok);document.getElementById('publicHash').value=root;}else{rr.className='';rr.textContent='A lista não contém a linha TODAS_AS_CAPTURAS|hash.';}document.querySelectorAll('.capture-row').forEach(function(row){var name=row.getAttribute('data-capture').toLowerCase(),expected=row.querySelector('.capture-hash').getAttribute('data-expected').toLowerCase(),given=map[name],out=row.querySelector('.cmp');if(!given){out.className='cmp';out.textContent='Sem hash desta captura na lista.';}else{var match=given===expected;row.querySelector('.capture-hash').value=given;show(out,match?'Hash da captura confere.':'Diverge do hash calculado para esta captura.',match);}});}function copyValue(b){var t=document.createElement('textarea');t.value=b.getAttribute('data-value');document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}</script></html>");json.append("]}");Path reportDir=root.resolve("integridade");Files.createDirectories(reportDir);Map<Path,String> outputs=new LinkedHashMap<>();outputs.put(root.resolve("integridade.html"),html.toString());outputs.put(reportDir.resolve("verificacao-integridade.txt"),txt.toString());outputs.put(reportDir.resolve("verificacao-integridade.json"),json.toString());outputs.put(reportDir.resolve("hashes-capturas.txt"),publicHashList(cs,rootHash));outputs.put(reportDir.resolve("historico-capturas.txt"),history);atomicBatch(outputs);
    }
    private static void atomic(Path p,String s)throws IOException{byte[]data=s.getBytes(StandardCharsets.UTF_8);if(data.length>MAX_METADATA_BYTES)throw new IOException("Relatório excede o limite de "+MAX_METADATA_BYTES+" bytes; o arquivo anterior foi preservado.");Path target=p.toAbsolutePath().normalize();Path parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+p);Files.createDirectories(parent);rejectLinks(parent);Path t=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");try{try(OutputStream out=Files.newOutputStream(t,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){out.write(data);}try{Files.move(t,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(t,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(t);}}
    private static void atomicBatch(Map<Path,String> outputs)throws IOException{
        Map<Path,Path> staged=new LinkedHashMap<>(),backups=new LinkedHashMap<>();List<Path> promoted=new ArrayList<>();
        try{
            for(Map.Entry<Path,String> entry:outputs.entrySet()){
                Path target=entry.getKey().toAbsolutePath().normalize(),parent=target.getParent();if(parent==null)throw new IOException("Caminho de relatório sem diretório pai: "+target);
                byte[] data=entry.getValue().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_METADATA_BYTES)throw new IOException("Relatório excede o limite de "+MAX_METADATA_BYTES+" bytes; relatórios anteriores foram preservados.");
                Files.createDirectories(parent);rejectLinks(parent);if(Files.isSymbolicLink(target))throw new IOException("O destino do relatório é um link simbólico: "+target);
                Path temp=Files.createTempFile(parent,"."+target.getFileName()+".",".tmp");staged.put(target,temp);
                try(OutputStream out=Files.newOutputStream(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){out.write(data);}
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("O destino do relatório não é arquivo regular: "+target);Path backup=Files.createTempFile(parent,"."+target.getFileName()+".",".bak");Files.copy(target,backup,StandardCopyOption.REPLACE_EXISTING);backups.put(target,backup);}
            }
            for(Map.Entry<Path,Path> entry:staged.entrySet()){
                if(Files.isSymbolicLink(entry.getKey()))throw new IOException("O destino do relatório mudou para link simbólico: "+entry.getKey());
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
    private static void rejectLinks(Path directory)throws IOException{Path current=directory.getRoot();for(Path part:directory){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new IOException("Diretórios de saída com link simbólico não são permitidos: "+current);}}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String jsonEsc(String value){if(value==null)return "";StringBuilder out=new StringBuilder(value.length()+16);for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}return out.toString();}
}
