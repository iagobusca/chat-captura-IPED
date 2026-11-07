import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

/** Regression check: verifier hashes must match the plugin's index formulas. */
public final class IndexHashParityTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("capture-index-parity");
        try {
            createCapture(root,"captura-a","alpha" );
            createCapture(root,"captura-b","beta" );
            Method discover=IntegrityVerifier.class.getDeclaredMethod("discover",Path.class);discover.setAccessible(true);
            @SuppressWarnings("unchecked") List<Object> captures=(List<Object>)discover.invoke(null,root);
            Method verify=IntegrityVerifier.class.getDeclaredMethod("verify",captures.get(0).getClass());verify.setAccessible(true);
            for(Object c:captures)verify.invoke(null,c);
            Method setHash=IntegrityVerifier.class.getDeclaredMethod("setHash",List.class);setHash.setAccessible(true);
            String actual=(String)setHash.invoke(null,captures);
            String expected=pluginRootHash(root,captures);
            if(!expected.equals(actual))throw new AssertionError("Verifier root hash differs from index formula: expected="+expected+" actual="+actual);
            Method write=IntegrityVerifier.class.getDeclaredMethod("writeReports",Path.class,List.class,String.class,String.class,String.class);write.setAccessible(true);
            write.invoke(null,root,captures,actual,"2026-10-31T12:00:00Z","HISTORICO_PUBLICO_V1\nSHA256|"+actual+"\n");
            String html=new String(Files.readAllBytes(root.resolve("integridade.html")),StandardCharsets.UTF_8);
            for(String expectedText:new String[]{"Diverge do hash geral calculado.","Diverge do hash da captura calculado.","#8b0000","TODAS_AS_CAPTURAS","compareList()","function copyValue","data-capture=","out=row.querySelector('.cmp')"})
                if(!html.contains(expectedText))throw new AssertionError("Missing HTML behavior/text: "+expectedText);
            if(html.contains("list-cmp"))throw new AssertionError("A second per-capture status is still rendered.");
            if(!Files.isRegularFile(root.resolve("integridade/verificacao-integridade.json"))
                    ||!Files.isRegularFile(root.resolve("integridade/verificacao-integridade.txt"))
                    ||!Files.isRegularFile(root.resolve("integridade/hashes-capturas.txt")))
                throw new AssertionError("Expected verifier outputs were not written under integridade/.");
            @SuppressWarnings("unchecked") List<Object> after=(List<Object>)discover.invoke(null,root);
            if(after.size()!=captures.size())throw new AssertionError("The integridade/ output folder was misdetected as a capture.");
            System.out.println("PASS: per-capture and collection hashes match the index algorithm; divergence labels are dark red.");
        } finally { deleteTree(root); }
    }
    private static void createCapture(Path root,String name,String value)throws Exception{
        Path dir=Files.createDirectories(root.resolve(name));byte[] data=value.getBytes(StandardCharsets.UTF_8);Files.write(dir.resolve("data.txt"),data);
        String entry="{\"path\":\"data.txt\",\"size\":"+data.length+",\"sha256\":\""+sha(data)+"\"}";
        Files.write(dir.resolve("manifest.json"),("{\"artifacts\":["+entry+"]}").getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("integridade.json"),("{\"artifacts\":["+entry+"]}").getBytes(StandardCharsets.UTF_8));
    }
    private static String pluginRootHash(Path root,List<Object> captures)throws Exception{
        List<Object> sorted=new ArrayList<>(captures);Field name=field(sorted.get(0),"name"),dir=field(sorted.get(0),"dir");
        sorted.sort(Comparator.comparing(o->{try{return ((String)name.get(o)).toLowerCase(Locale.ROOT);}catch(Exception e){throw new RuntimeException(e);}}));
        StringBuilder canonical=new StringBuilder();
        for(Object c:sorted){Path folder=(Path)dir.get(c);List<Path> files=new ArrayList<>();try(Stream<Path>s=Files.walk(folder)){s.filter(Files::isRegularFile).forEach(files::add);}
            files.sort(Comparator.comparing(p->folder.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT)));
            StringBuilder folderData=new StringBuilder();long bytes=0;int count=0;for(Path f:files){String rel=folder.relativize(f).toString().replace('\\','/');long size=Files.size(f);folderData.append(rel).append('|').append(size).append('|').append(sha(Files.readAllBytes(f))).append('\n');bytes+=size;count++;}
            canonical.append(folder.getFileName()).append('|').append(sha(folderData.toString().getBytes(StandardCharsets.UTF_8))).append('|').append(bytes).append('|').append(count).append('\n');
        }
        return sha(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static Field field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private static String sha(byte[] b)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format("%02x",x&255));return s.toString();}
    private static void deleteTree(Path root)throws Exception{if(root==null||!Files.exists(root))return;try(Stream<Path>s=Files.walk(root)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){throw new RuntimeException(e);}});}}
}
