import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent, read-only checker for Chat Capture folders (Java 11). */
public final class IntegrityVerifier {
    private static final String ALG = "SHA-256";
    private static final class Item {
        String path, expected; long size, bytes; String actual, state;
    }
    private static final class Capture {
        Path dir; String name, hash, historyHash = "", contentRootHash = "", integrityFileHash = "", historyStatus = "registro ausente/legado"; JSONArray historyEvents = new JSONArray(); List<Item> items = new ArrayList<>(); long bytes; int fileCount;
    }
    private IntegrityVerifier() {}

    public static void main(String[] args) throws Exception {
        boolean verifyOnly = args.length > 0 && "--verify-only".equalsIgnoreCase(args[0]);
        boolean exportHistory = args.length > 0 && "--export-history".equalsIgnoreCase(args[0]);
        Path root = args.length > (verifyOnly || exportHistory ? 1 : 0) ? Paths.get(args[verifyOnly || exportHistory ? 1 : 0]) : applicationFolder();
        root = root.toAbsolutePath().normalize();
        List<Capture> captures = discover(root);
        if (captures.isEmpty()) { JOptionPane.showMessageDialog(null, "Nenhuma captura com integridade.json ou manifest.json foi encontrada na pasta:\n" + root, "Integridade", JOptionPane.INFORMATION_MESSAGE); return; }
        if (verifyOnly) {
            boolean ok=true;
            for(Capture c:captures){verify(c);System.out.println(c.name+" | "+c.historyStatus+" | content="+c.hash+" | history="+c.historyHash);ok &= c.historyStatus.startsWith("consistente com registro local");}
            if(!ok) throw new IOException("Uma ou mais capturas divergiram do registro local.");
            return;
        }
        if(exportHistory){for(Capture c:captures)verify(c);String report=historyReport(captures,setHash(captures));Path out=root.resolve("integridade");Files.createDirectories(out);atomic(out.resolve("historico-capturas.txt"),report);System.out.println(out.resolve("historico-capturas.txt"));System.out.println("SHA-256_PUBLICO="+hashText(normalizePublicRecord(report)));return;}
        int mode=chooseMode();
        if(mode<0)return;
        List<Capture> selected = chooseCaptures(captures);
        if (selected.isEmpty()) return;
        for (Capture c : selected) verify(c);
        String setHash = setHash(selected);
        String generated = OffsetDateTime.now().toString();
        String history = historyReport(selected, setHash);
        String historyPublicHash = hashText(normalizePublicRecord(history));
        writeReports(root, selected, setHash, generated, history);
        String hashList=publicHashList(selected,setHash);
        Path historyFile=root.resolve("integridade").resolve("historico-capturas.txt");
        if(mode==1)comparePublicHistory(history,historyPublicHash);
        showSummaryDialog(root,selected,setHash,history,historyPublicHash,hashList,historyFile,mode);
    }
    private static void showSummaryDialog(Path root,List<Capture> selected,String setHash,String history,String historyPublicHash,String hashList,Path historyFile,int mode){
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(new JLabel(mode==0?"MODO: INTEGRIDADE LOCAL — arquivos comparados com o registro local.":"MODO: INTEGRIDADE PÚBLICO — referência externa comparada."));
        panel.add(new JLabel("Capturas verificadas: "+selected.size()));panel.add(Box.createVerticalStrut(10));
        panel.add(hashValueRow("Hash geral do conjunto",setHash,"Hash geral do conjunto copiado."));
        panel.add(Box.createVerticalStrut(8));
        panel.add(hashValueRow("SHA-256 do histórico completo",historyPublicHash,"Hash SHA-256 do histórico completo copiado."));
        panel.add(Box.createVerticalStrut(8));
        JPanel actions=new JPanel(new java.awt.GridLayout(0,2,6,6));
        JButton copyList=new JButton("Copiar lista de hashes");copyList.addActionListener(e->copyToClipboard(hashList,"Lista de hashes copiada."));
        JButton copyHistory=new JButton("Copiar histórico completo");copyHistory.addActionListener(e->copyToClipboard(history,"Histórico completo copiado."));
        JButton openHistory=new JButton("Abrir histórico");openHistory.addActionListener(e->openFile(historyFile));
        JButton openHtml=new JButton("Abrir relatório HTML");openHtml.addActionListener(e->openInBrowser(root.resolve("integridade.html")));
        JButton close=new JButton("Fechar");close.addActionListener(e->{java.awt.Window w=SwingUtilities.getWindowAncestor(close);if(w!=null)w.dispose();});
        actions.add(copyList);actions.add(copyHistory);actions.add(openHistory);actions.add(openHtml);actions.add(close);panel.add(actions);
        JOptionPane option=new JOptionPane(panel,JOptionPane.PLAIN_MESSAGE,JOptionPane.DEFAULT_OPTION);JDialog dialog=option.createDialog(null,"Resultado da verificação");dialog.setResizable(true);dialog.setVisible(true);
    }
    private static JPanel hashValueRow(String label,String value,String copyMessage){
        JPanel block=new JPanel();block.setLayout(new BoxLayout(block,BoxLayout.Y_AXIS));block.add(new JLabel(label+":"));
        JPanel row=new JPanel(new java.awt.BorderLayout(8,0));JButton copy=new JButton("Copiar");copy.addActionListener(e->copyToClipboard(value,copyMessage));
        JTextArea text=new JTextArea(value,2,38);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setOpaque(false);text.setFocusable(true);
        row.add(copy,java.awt.BorderLayout.WEST);row.add(text,java.awt.BorderLayout.CENTER);block.add(row);return block;
    }
    private static int chooseMode(){
        JButton local=new JButton("Integridade Local");JButton external=new JButton("Integridade Público");final int[] selected={-1};
        local.addActionListener(e->{selected[0]=0;((java.awt.Window)SwingUtilities.getWindowAncestor(local)).dispose();});
        external.addActionListener(e->{selected[0]=1;((java.awt.Window)SwingUtilities.getWindowAncestor(external)).dispose();});
        JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(new JLabel("Escolha a referência para verificar:"));panel.add(Box.createVerticalStrut(10));panel.add(new JLabel("Local: compara os arquivos com o integridade.json da captura."));panel.add(new JLabel("Público: compara com hash ou histórico recebido de fora."));panel.add(Box.createVerticalStrut(12));JPanel buttons=new JPanel();buttons.add(local);buttons.add(external);panel.add(buttons);
        JOptionPane option=new JOptionPane(panel,JOptionPane.PLAIN_MESSAGE,JOptionPane.DEFAULT_OPTION);JDialog dialog=option.createDialog(null,"Verificar integridade");dialog.setModal(true);dialog.setVisible(true);return selected[0];
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
    private static void comparePublicHistory(String localHistory,String localHash) {
        JTextField publicHash=new JTextField(36);JTextArea publicRecord=new JTextArea(14,44);publicRecord.setLineWrap(false);JLabel result=new JLabel("Cole o SHA-256 público ou o histórico completo recebido por canal confiável.");
        JButton compareHash=new JButton("Comparar hash público");compareHash.addActionListener(e->{String supplied=publicHash.getText().trim();boolean match=supplied.matches("(?i)[0-9a-f]{64}")&&supplied.equalsIgnoreCase(localHash);result.setText(supplied.isEmpty()?"Informe o hash público.":!supplied.matches("(?i)[0-9a-f]{64}")?"Formato inválido: informe 64 caracteres hexadecimais.":match?"CONFERE: o hash público corresponde ao histórico completo calculado.":"DIVERGE: o hash público não corresponde ao histórico calculado.");result.setForeground(supplied.isEmpty()?java.awt.Color.DARK_GRAY:match?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));});
        JButton compareRecord=new JButton("Comparar histórico completo");compareRecord.addActionListener(e->{String supplied=publicRecord.getText();boolean match=!supplied.trim().isEmpty()&&normalizePublicRecord(supplied).equals(normalizePublicRecord(localHistory));result.setText(supplied.trim().isEmpty()?"Cole o histórico público completo.":match?"CONFERE: o registro público corresponde integralmente ao histórico calculado.":"DIVERGE: o registro público não corresponde ao histórico calculado.");result.setForeground(supplied.trim().isEmpty()?java.awt.Color.DARK_GRAY:match?new java.awt.Color(0,100,0):new java.awt.Color(139,0,0));});
        JButton copyHash=new JButton("Copiar hash calculado");copyHash.addActionListener(e->copyToClipboard(localHash,"SHA-256 público do histórico copiado."));
        JPanel panel=new JPanel();panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(new JLabel("Cole o hash público ou copie o hash calculado:"));JPanel hashRow=new JPanel(new java.awt.BorderLayout(8,0));hashRow.add(copyHash,java.awt.BorderLayout.WEST);hashRow.add(publicHash,java.awt.BorderLayout.CENTER);panel.add(hashRow);panel.add(compareHash);panel.add(Box.createVerticalStrut(8));panel.add(new JLabel("Ou cole abaixo o arquivo completo historico-capturas.txt:"));panel.add(new JScrollPane(publicRecord));panel.add(compareRecord);panel.add(result);
        JOptionPane.showMessageDialog(null,panel,"Integridade Público — comparar referência",JOptionPane.PLAIN_MESSAGE);
    }
    private static String normalizePublicRecord(String s){String n=s.replace("\r\n","\n").replace('\r','\n');while(n.endsWith("\n"))n=n.substring(0,n.length()-1);return n+"\n";}
    private static String publicHashList(List<Capture> captures,String rootHash){StringBuilder out=new StringBuilder("TODAS_AS_CAPTURAS|").append(rootHash).append('\n');List<Capture> ordered=new ArrayList<>(captures);ordered.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));for(Capture c:ordered)out.append(c.name).append('|').append(c.hash).append('\n');return out.toString();}
    private static void copyToClipboard(String value,String message){try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);JOptionPane.showMessageDialog(null,message,"Hashes",JOptionPane.INFORMATION_MESSAGE);}catch(Exception e){JOptionPane.showMessageDialog(null,"Não foi possível copiar automaticamente.\n"+e.getMessage(),"Cópia",JOptionPane.ERROR_MESSAGE);}}
    private static List<Capture> discover(Path root) throws IOException {
        List<Capture> out=new ArrayList<>();
        try(java.util.stream.Stream<Path> s=Files.list(root)) { s.filter(Files::isDirectory).forEach(p->{String n=p.getFileName().toString();if(n.startsWith("_backup_chat_capture_")||n.equals("vinculado")||n.equals(".iped-reextract-trash")||n.contains(".__old_")||n.contains(".__reextract_tmp_")||n.contains(".__reextract_failed_"))return;if(Files.isRegularFile(p.resolve("manifest.json"))||Files.isRegularFile(p.resolve("whatsapp.html"))||Files.isRegularFile(p.resolve("relatorio.htm"))||Files.isRegularFile(p.resolve("texto.txt"))){Capture c=new Capture();c.dir=p;c.name=n;out.add(c);} }); }
        out.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT))); return out;
    }
    private static List<Capture> chooseCaptures(List<Capture> captures) {
        JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));JCheckBox all=new JCheckBox("Desmarcar todas",true);panel.add(all);List<JCheckBox> boxes=new ArrayList<>();
        for(Capture c:captures){JCheckBox b=new JCheckBox(c.name,true);boxes.add(b);panel.add(b);} all.addActionListener(e->{boolean selected=all.isSelected();boxes.forEach(b->b.setSelected(selected));all.setText(selected?"Desmarcar todas":"Marcar todas");});
        int r=JOptionPane.showConfirmDialog(null,new JScrollPane(panel),"Selecione as capturas",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(r!=JOptionPane.OK_OPTION)return Collections.emptyList();
        List<Capture> out=new ArrayList<>();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isSelected())out.add(captures.get(i));return out;
    }
    private static void verify(Capture c) throws Exception {
        Path integrityPath=c.dir.resolve("integridade.json");
        JSONObject integrity=null;
        if(Files.isRegularFile(integrityPath)) {
            c.integrityFileHash=hash(integrityPath);
            try { integrity=new JSONObject(new String(Files.readAllBytes(integrityPath),StandardCharsets.UTF_8)); }
            catch(RuntimeException invalid) { c.historyStatus="registro inválido"; }
        }
        if(integrity!=null && integrity.optInt("schemaVersion",0)>=2) verifyHistory(c,integrity);
        Path inventoryFile=Files.isRegularFile(c.dir.resolve("integridade.json"))?c.dir.resolve("integridade.json"):c.dir.resolve("manifest.json");
        String json=integrity!=null?integrity.toString():new String(Files.readAllBytes(inventoryFile),StandardCharsets.UTF_8);
        Matcher array=Pattern.compile("\\\"artifacts\\\"\\s*:\\s*\\[(.*?)\\]",Pattern.DOTALL).matcher(json);
        if(!array.find()){ c.hash="";return; }
        Matcher rows=Pattern.compile("\\{([^{}]*)\\}").matcher(array.group(1));Map<String,Item> expected=new HashMap<>();
        while(rows.find()) {String row=rows.group(1);String path=field(row,"path"),sha=field(row,"sha256");if(path==null||sha==null)continue;Item i=new Item();i.path=path;i.expected=sha;i.size=number(row,"size");expected.put(path,i);}
        for(Item i:expected.values()){
            Path f=resolve(c.dir,i.path);if(f==null||!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)){i.state="ausente/invalid_path";c.items.add(i);continue;}
            i.actual=hash(f);i.bytes=Files.size(f);i.state=i.expected.equalsIgnoreCase(i.actual)&&i.size==i.bytes?"integro":"alterado";c.items.add(i);
        }
        Set<String> seen=expected.keySet();try(java.util.stream.Stream<Path>s=Files.walk(c.dir)){s.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).forEach(p->{String rel=c.dir.relativize(p).toString().replace('\\','/');if(!rel.equals("manifest.json")&&!rel.equals("integridade.json")&&!rel.equals("reextracao-auditoria.jsonl")&&!seen.contains(rel)){Item x=new Item();x.path=rel;x.actual=quietHash(p);x.bytes=quietSize(p);x.state="extra";c.items.add(x);}});}
        c.items.sort(Comparator.comparing(i->i.path));
        // Must match HtmlLinkViewer.calculateFolderHash: all regular files, including
        // manifest.json and integridade.json; canonical path|size|SHA-256, sorted by lowercase path.
        List<Path> allFiles=new ArrayList<>();try(java.util.stream.Stream<Path>s=Files.walk(c.dir)){s.filter(Files::isRegularFile).forEach(allFiles::add);}
        allFiles.sort(Comparator.comparing(p->c.dir.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT)));
        StringBuilder canonical=new StringBuilder();for(Path f:allFiles){String rel=c.dir.relativize(f).toString().replace('\\','/');long size=Files.size(f);String sha=hash(f);canonical.append(rel).append('|').append(size).append('|').append(sha).append('\n');c.bytes+=size;c.fileCount++;}
        c.hash=hashText(canonical.toString());
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
        c.historyEvents=new JSONArray(events.toString());
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
    private static Path resolve(Path root,String rel){try{Path r=Paths.get(rel.replace('\\','/'));if(r.isAbsolute())return null;for(Path p:r)if(p.toString().equals(".."))return null;Path f=root.resolve(r).normalize();if(!f.startsWith(root))return null;Path q=root;for(Path p:root.relativize(f)){q=q.resolve(p);if(Files.isSymbolicLink(q))return null;}return f;}catch(Exception e){return null;}}
    private static String field(String s,String name){Matcher m=Pattern.compile("\\\""+Pattern.quote(name)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(s);return m.find()?m.group(1).replace("\\/","/").replace("\\\"","\"").replace("\\\\","\\"):null;}
    private static long number(String s,String n){Matcher m=Pattern.compile("\\\""+n+"\\\"\\s*:\\s*(\\d+)").matcher(s);return m.find()?Long.parseLong(m.group(1)): -1;}
    private static String hash(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance(ALG);try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[32768];int n;while((n=in.read(b))>0)d.update(b,0,n);}return hex(d.digest());}
    private static String quietHash(Path p){try{return hash(p);}catch(Exception e){return null;}}
    private static long quietSize(Path p){try{return Files.size(p);}catch(Exception e){return 0;}}
    private static String hashText(String s)throws Exception{return hex(MessageDigest.getInstance(ALG).digest(s.getBytes(StandardCharsets.UTF_8)));}
    private static String hex(byte[]b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    private static String setHash(List<Capture> cs)throws Exception{List<Capture>x=new ArrayList<>(cs);x.sort(Comparator.comparing(c->c.name.toLowerCase(Locale.ROOT)));StringBuilder b=new StringBuilder();for(Capture c:x)b.append(c.name).append('|').append(c.hash).append('|').append(c.bytes).append('|').append(c.fileCount).append('\n');return hashText(b.toString());}
    private static String historyReport(List<Capture> captures,String collectionHash){
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
        StringBuilder html=new StringBuilder("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Integridade das capturas</title><style>body{font:16px Arial;margin:2em;color:#20313a}table{border-collapse:collapse;width:100%;margin-bottom:1em}td,th{border:1px solid #bbb;padding:.5em;text-align:left;overflow-wrap:anywhere}.ok{color:green}.bad,.diverge{color:#8b0000;font-weight:700}.match{color:#006400;font-weight:700}input{padding:.5em;width:min(40em,90%)}code{overflow-wrap:anywhere}</style><h1>Relatório de integridade</h1><p>Verificação e relatório gerados em: ").append(esc(time)).append("</p><p>Hash geral das capturas selecionadas: <code>").append(rootHash).append("</code></p><label>Hash público geral do conjunto: <input id=\"publicHash\" placeholder=\"Cole aqui o hash geral recebido\"></label> <button onclick=\"compareHash()\">Comparar hash geral</button><p id=\"comparison\">Nenhum hash geral informado.</p>");
        StringBuilder txt=new StringBuilder("Verificado: ").append(time).append("\nTODAS_AS_CAPTURAS|").append(rootHash).append("\n");StringBuilder json=new StringBuilder("{\"generatedAt\":\"").append(time).append("\",\"rootHash\":\"").append(rootHash).append("\",\"captures\":[");
        html.append("<label for=\"hashList\">Cole a lista de hashes compartilhada pelo autor (formato captura|hash):</label><br><textarea id=\"hashList\" rows=\"6\" style=\"width:min(70em,95%)\" placeholder=\"TODAS_AS_CAPTURAS|hash-geral\ncaptura-chat-...|hash-da-captura\"></textarea><br><button onclick=\"compareList()\">Comparar lista em todas as capturas</button> <span id=\"listResult\"></span><table><tr><th>Captura</th><th>Hash calculado</th><th>Arquivos</th><th>Comparar hash desta captura</th></tr>");
        boolean first=true;for(Capture c:cs){long good=c.items.stream().filter(i->"integro".equals(i.state)).count();
            html.append("<tr class=\"capture-row\" data-capture=\"").append(esc(c.name)).append("\"><td>").append(esc(c.name)).append("</td><td><code>").append(c.hash).append("</code> <button data-value=\"").append(c.hash).append("\" onclick=\"copyValue(this)\">Copiar hash</button></td><td>").append(good).append(" íntegros / ").append(c.items.size()).append(" verificados<br>Histórico: ").append(esc(c.historyStatus)).append(c.historyHash.isEmpty()?"":"<br>Hash do registro: <code>"+esc(c.historyHash)+"</code> <button data-value=\""+esc(c.historyHash)+"\" onclick=\"copyValue(this)\">Copiar</button>").append("</td><td><label>Hash público: <input class=\"capture-hash\" data-expected=\"").append(c.hash).append("\" placeholder=\"Cole o hash desta captura\"></label> <button onclick=\"compareCapture(this)\">Comparar captura</button><br><span class=\"cmp\">Não comparado</span></td></tr>");
            txt.append(c.name).append(" | ").append(c.hash).append("\n");
            if(!first)json.append(',');first=false;json.append("{\"name\":\"").append(esc(c.name)).append("\",\"hash\":\"").append(c.hash).append("\",\"files\":[");boolean f=true;for(Item i:c.items){if(!f)json.append(',');f=false;json.append("{\"path\":\"").append(esc(i.path)).append("\",\"status\":\"").append(i.state).append("\",\"expected\":\"").append(i.expected==null?"":i.expected).append("\",\"actual\":\"").append(i.actual==null?"":i.actual).append("\"}");}json.append("]}");
        }
        html.append("</table><h2>Arquivos</h2>");for(Capture c:cs){html.append("<h3>").append(esc(c.name)).append("</h3><table><tr><th>Arquivo</th><th>Estado</th><th>Esperado</th><th>Encontrado</th></tr>");for(Item i:c.items)html.append("<tr><td>").append(esc(i.path)).append("</td><td class=\"").append("integro".equals(i.state)?"ok":"bad").append("\">").append(esc(i.state)).append("</td><td><code>").append(esc(i.expected)).append("</code></td><td><code>").append(esc(i.actual)).append("</code></td></tr>");html.append("</table>");}html.append("<p>Este relatório é um retrato desta execução. O hash é um código de comparação, não uma assinatura digital.</p><p>Arquivos: <a href=\"integridade/hashes-capturas.txt\">lista de hashes</a> · <a href=\"integridade/verificacao-integridade.txt\">resumo TXT</a> · <a href=\"integridade/verificacao-integridade.json\">detalhes JSON</a></p><script>function show(el,text,ok){el.className=ok?'match':'diverge';el.textContent=text;}function compareHash(){var v=document.getElementById('publicHash').value.trim().toLowerCase(),r=document.getElementById('comparison'),ok=v==='" ).append(rootHash).append("';if(!v){r.className='';r.textContent='Informe um hash público.';return;}show(r,ok?'Confere com o hash geral calculado.':'Diverge do hash geral calculado.',ok);}function compareCapture(b){var p=b.parentNode,i=p.querySelector('input'),v=i.value.trim().toLowerCase(),ok=v===i.getAttribute('data-expected').toLowerCase(),r=p.querySelector('.cmp');if(!v){r.className='cmp';r.textContent='Informe um hash público.';return;}show(r,ok?'Confere com o hash da captura calculado.':'Diverge do hash da captura calculado.',ok);}function compareList(){var map={},lines=document.getElementById('hashList').value.split('\\n');lines.forEach(function(line){line=line.replace('\\r','').trim();var n=line.indexOf('|');if(n>0)map[line.substring(0,n).trim().toLowerCase()]=line.substring(n+1).trim().toLowerCase();});var root=map['todas_as_capturas']||map['root'];var rr=document.getElementById('listResult');if(root){var ok=root==='" ).append(rootHash).append("';show(rr,ok?'Hash geral confere.':'Diverge do hash geral calculado.',ok);document.getElementById('publicHash').value=root;}else{rr.className='';rr.textContent='A lista não contém a linha TODAS_AS_CAPTURAS|hash.';}document.querySelectorAll('.capture-row').forEach(function(row){var name=row.getAttribute('data-capture').toLowerCase(),expected=row.querySelector('.capture-hash').getAttribute('data-expected').toLowerCase(),given=map[name],out=row.querySelector('.cmp');if(!given){out.className='cmp';out.textContent='Sem hash desta captura na lista.';}else{var match=given===expected;row.querySelector('.capture-hash').value=given;show(out,match?'Hash da captura confere.':'Diverge do hash calculado para esta captura.',match);}});}function copyValue(b){var t=document.createElement('textarea');t.value=b.getAttribute('data-value');document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();}</script></html>");json.append("]}");Path reportDir=root.resolve("integridade");Files.createDirectories(reportDir);atomic(root.resolve("integridade.html"),html.toString());atomic(reportDir.resolve("verificacao-integridade.txt"),txt.toString());atomic(reportDir.resolve("verificacao-integridade.json"),json.toString());atomic(reportDir.resolve("hashes-capturas.txt"),publicHashList(cs,rootHash));atomic(reportDir.resolve("historico-capturas.txt"),history);
    }
    private static void atomic(Path p,String s)throws IOException{Path t=p.resolveSibling(p.getFileName()+".tmp");Files.write(t,s.getBytes(StandardCharsets.UTF_8));try{Files.move(t,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(t,p,StandardCopyOption.REPLACE_EXISTING);}}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
