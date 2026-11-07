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
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent, read-only checker for Chat Capture folders (Java 8+). */
public final class IntegrityVerifier {
    private static final String ALG = "SHA-256";
    private static final String VERIFIER_VERSION = "1.2.8";
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CAPTURE_FILES = 25010;
    private static final int MAX_RELATIVE_PATH_CHARS = 4096;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_CAPTURE_CANDIDATES = 10000;
    private static final long MAX_SELECTED_ITEMS = 25000L;
    private static final long MAX_SELECTED_METADATA_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_REPORT_ESTIMATED_CHARS = 4L * 1024L * 1024L;
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
        if(exportHistory){verifyAllBounded(captures);enforceReportBudget(captures);String report=historyReport(captures,setHash(captures));Path out=root.resolve("integridade");Files.createDirectories(out);atomic(out.resolve("historico-capturas.txt"),report);System.out.println(out.resolve("historico-capturas.txt"));System.out.println("SHA-256_PUBLICO="+hashText(normalizePublicRecord(report)));return;}
        int mode=chooseMode();
        if(mode<0){System.exit(0);return;}
        List<Capture> selected = chooseCaptures(captures);
        if (selected.isEmpty()) { System.exit(0); return; }
        try { verifyAllBounded(selected); enforceReportBudget(selected); }
        catch (Exception e) { JOptionPane.showMessageDialog(null,e.getMessage()==null?"Não foi possível verificar o conjunto selecionado.":e.getMessage(),"Limite ou erro de verificação",JOptionPane.ERROR_MESSAGE); return; }
        String setHash = setHash(selected);
        String generated = OffsetDateTime.now().toString();
        String history = historyReport(selected, setHash);
        String historyPublicHash = hashText(normalizePublicRecord(history));
        writeReports(root, selected, setHash, generated, history);
        String hashList=publicHashList(selected,setHash);
        Path historyFile=root.resolve("integridade").resolve("historico-capturas.txt");
        if(mode==1)comparePublicHistory(history,historyPublicHash);
        showSummaryDialog(root,selected,setHash,history,historyPublicHash,hashList,historyFile,mode);
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
    private static void showSummaryDialog(Path root,List<Capture> selected,String setHash,String history,String historyPublicHash,String hashList,Path historyFile,int mode) throws Exception {
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(new JLabel(mode==0?"MODO: INTEGRIDADE LOCAL — arquivos comparados com o registro local.":"MODO: INTEGRIDADE PÚBLICO — referência externa comparada."));
        panel.add(new JLabel("Capturas verificadas: "+selected.size()));panel.add(Box.createVerticalStrut(10));
        panel.add(hashValueRow("Hash geral do conjunto",setHash,"Hash geral do conjunto copiado."));
        panel.add(Box.createVerticalStrut(8));
        panel.add(hashValueRow("SHA-256 do histórico completo",historyPublicHash,"Hash SHA-256 do histórico completo copiado."));
        panel.add(Box.createVerticalStrut(8));
        JPanel actions=new JPanel(new java.awt.GridLayout(0,2,6,6));
        JFrame frame=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Resultado",true);
        JButton copyList=new JButton("Copiar lista de hashes");copyList.addActionListener(e->copyToClipboard(frame,hashList,"Lista de hashes copiada."));
        JButton copyHistory=new JButton("Copiar histórico completo");copyHistory.addActionListener(e->copyToClipboard(frame,history,"Histórico completo copiado."));
        JButton openHistory=new JButton("Abrir histórico");openHistory.addActionListener(e->openFile(historyFile));
        JButton openHtml=new JButton("Abrir relatório HTML");openHtml.addActionListener(e->openInBrowser(root.resolve("integridade.html")));
        final CountDownLatch latch=new CountDownLatch(1);
        JButton close=new JButton("Fechar");close.addActionListener(e->latch.countDown());
        actions.add(copyList);actions.add(copyHistory);actions.add(openHistory);actions.add(openHtml);actions.add(close);panel.add(actions);
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
    private static void comparePublicHistory(String localHistory,String localHash) throws Exception {
        JTextField publicHash=new JTextField(36);JTextArea publicRecord=new JTextArea(14,44);publicRecord.setLineWrap(false);JLabel result=new JLabel("Cole o SHA-256 público ou o histórico completo recebido por canal confiável.");
        JButton compareHash=new JButton("Comparar hash público");compareHash.addActionListener(e->{String supplied=publicHash.getText().trim();boolean match=supplied.matches("(?i)[0-9a-f]{64}")&&supplied.equalsIgnoreCase(localHash);result.setText(supplied.isEmpty()?"Informe o hash público.":!supplied.matches("(?i)[0-9a-f]{64}")?"Formato inválido: informe 64 caracteres hexadecimais.":match?"CONFERE: o hash público corresponde ao histórico completo calculado.":"DIVERGE: o hash público não corresponde ao histórico calculado.");result.setForeground(supplied.isEmpty()?java.awt.Color.DARK_GRAY:match?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));});
        JButton compareRecord=new JButton("Comparar histórico completo");compareRecord.addActionListener(e->{String supplied=publicRecord.getText();boolean match=!supplied.trim().isEmpty()&&normalizePublicRecord(supplied).equals(normalizePublicRecord(localHistory));result.setText(supplied.trim().isEmpty()?"Cole o histórico público completo.":match?"CONFERE: o registro público corresponde integralmente ao histórico calculado.":"DIVERGE: o registro público não corresponde ao histórico calculado.");result.setForeground(supplied.trim().isEmpty()?java.awt.Color.DARK_GRAY:match?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));});
        JButton copyHash=new JButton("Copiar hash calculado");
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(new JLabel("Cole o hash público ou copie o hash calculado:"));JPanel hashRow=new JPanel(new java.awt.BorderLayout(8,0));hashRow.add(copyHash,java.awt.BorderLayout.WEST);hashRow.add(publicHash,java.awt.BorderLayout.CENTER);panel.add(hashRow);panel.add(compareHash);panel.add(Box.createVerticalStrut(8));panel.add(new JLabel("Ou cole abaixo o arquivo completo historico-capturas.txt:"));panel.add(new JScrollPane(publicRecord));panel.add(compareRecord);panel.add(result);
        final CountDownLatch latch=new CountDownLatch(1);
        JFrame frame2=newWindow("Verificador de Integridade - v"+VERIFIER_VERSION+" - Integridade Público",true);
        copyHash.addActionListener(e->copyToClipboard(frame2,localHash,"SHA-256 público do histórico copiado."));
        frame2.setContentPane(panel);
        frame2.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){latch.countDown();}});
        frame2.pack();frame2.setLocationRelativeTo(null);frame2.setVisible(true);
        latch.await();
        frame2.dispose();
    }
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
        Path integrityPath=c.dir.resolve("integridade.json");
        JSONObject integrity=null;
        if(Files.isRegularFile(integrityPath, LinkOption.NOFOLLOW_LINKS)) {
            c.integrityFileHash=hash(integrityPath);
            try { integrity=parseBoundedJson(readUtf8Limited(integrityPath),integrityPath); }
            catch(RuntimeException invalid) { c.historyStatus="registro inválido"; }
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
    private static String historyReport(List<Capture> captures,String collectionHash)throws IOException{
        enforceReportBudget(captures);
        StringBuilder out=new StringBuilder("HISTORICO_PUBLICO_V1\nSHA256|").append(collectionHash).append("\n");
        List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));
        for(Capture c:ordered){
            out.append("\nCAPTURA|").append(recordField(c.name)).append("\nSTATUS|").append(c.historyStatus).append("\nHASH_CONTEUDO|").append(c.contentRootHash.isEmpty()?c.hash:c.contentRootHash).append("\nHASH_INTEGRIDADE_JSON|").append(c.integrityFileHash.isEmpty()?"indisponivel":c.integrityFileHash).append("\nHASH_FINAL_HISTORICO|").append(c.historyHash.isEmpty()?"indisponivel":c.historyHash).append("\nEVENTOS|").append(c.historyEvents.length()).append('\n');
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
