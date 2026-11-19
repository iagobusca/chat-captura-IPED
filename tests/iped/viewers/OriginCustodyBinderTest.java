package iped.viewers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** End-to-end synthetic tests for OriginCustodyBinder (Avilla UTF-16LE Hashs.txt, IPED CSV, SQLite N3, blobs). */
public final class OriginCustodyBinderTest {

    private OriginCustodyBinderTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("origin-custody-binder-test-");
        try {
            runStrictTextMatchingDirectTest();
            runFullLineageScenario(tempRoot);
            runTextMismatchScenario(tempRoot);
            runMd5OnlyRejectedAndMissingHashsTxtFailClosedTest(tempRoot);
            System.out.println("OriginCustodyBinderTest: PASS");
        } finally {
            deleteTree(tempRoot);
        }
    }

    private static void runStrictTextMatchingDirectTest() {
        check(!OriginCustodyBinder.matchesForensicMessageText("Ola", "Ola eu vou matar ele", "", ""), "substring deve falhar");
        check(!OriginCustodyBinder.matchesForensicMessageText("Ola eu vou matar ele", "Ola", "", ""), "substring inversa deve falhar");
        check(!OriginCustodyBinder.matchesForensicMessageText("", "qualquer", "", ""), "banco vazio com captura nao-vazia deve falhar");
        check(!OriginCustodyBinder.matchesForensicMessageText("qualquer", "", "", ""), "captura vazia com banco nao-vazio deve falhar");
        check(!OriginCustodyBinder.matchesForensicMessageText("Msg original", "Msg original 2026-11-16 10:00:00 texto forjado", "", ""), "bypass apos data ISO nao pode conferir");
        check(OriginCustodyBinder.matchesForensicMessageText("", "", "", ""), "ambos vazios deve conferir");
        check(OriginCustodyBinder.matchesForensicMessageText("Mensagem exata", "Mensagem exata", "", ""), "exato deve conferir");
        check(OriginCustodyBinder.matchesForensicMessageText("Caf\u00E9", "Cafe\u0301", "", ""), "normalizacao NFC deve conferir");
        check(OriginCustodyBinder.matchesForensicMessageText("Mensagem exata", "Remetente Mensagem exata 10:00", "Remetente", "10:00"), "metadados envelopados devem conferir");
        check(OriginCustodyBinder.matchesForensicMessageText(
                "Mensagem editada",
                "Joao Mensagem editada 2026-11-16 10:00:00 -03:00 Editada em 2026-11-16 10:05:00 -03:00",
                "Joao",
                ""), "sufixo Editada em com data ISO deve conferir sem liberar texto arbitrario");
        check(OriginCustodyBinder.matchesForensicMessageText(
                "Mensagem recebida",
                "Mensagem recebida 2026-11-16 10:00:00 -03:00",
                "Mensagem recebida 2026-11-16 10:00:00 -03:00",
                "2026-11-16 10:00:00 -03:00"),
                "guard !sender.equals(stripped) nao pode zerar texto quando sender legacy igualava cap");

        String bubbleHtml = "<div class=\"captured-message-block\" data-message-key=\"" + "a".repeat(64) + "\">"
                + "<div class=\"linha\"><div class=\"buble\"><div class=\"incoming from\">"
                + "<span class=\"name\">Maria</span><br>"
                + "<div class=\"quoteFrom\"><div><span class=\"quoteUser\">Joao</span><br>"
                + "<span class=\"quoteMsg\">Pergunta anterior</span></div></div>"
                + "<img class=\"fwd\"> Encaminhada <br>"
                + "Resposta <b>importante</b> com emoji <img class=\"e1f923\"> e <img class=\"e1f44d_1f3ff\">"
                + "<span class=\"time\">2026-11-16 10:00:00 -03:00</span>"
                + "</div></div></div></div>";
        Map<String, String> blocks = OriginCustodyBinder.extractCapturedHtmlBlocksByKey(bubbleHtml);
        String chunk = blocks.get("a".repeat(64));
        check(chunk != null, "bloco HTML capturado deve ser extraido por data-message-key");
        String capInner = "Maria Joao Pergunta anterior Encaminhada Resposta importante com emoji e 2026-11-16 10:00:00 -03:00";
        String dbText = "Resposta *importante* com emoji \uD83E\uDD23 e \uD83D\uDC4D\uD83C\uDFFF";
        check(OriginCustodyBinder.matchesForensicMessageRecord(
                0, dbText, capInner, capInner, "2026-11-16 10:00:00 -03:00", chunk, false, "text"),
                "bolha HTML com quoteFrom, fwd, negrito e img emoji eHEX deve conferir com text_data do SQLite");
        check(!OriginCustodyBinder.matchesForensicMessageRecord(
                0, "Outro texto adulterado", capInner, capInner, "2026-11-16 10:00:00 -03:00", chunk, false, "text"),
                "bolha HTML com texto divergente no SQLite deve continuar reprovando");
        check(OriginCustodyBinder.matchesForensicMessageRecord(
                2, "", "Maria Mensagem de audio (0:15) 2026-11-16 10:00:00 -03:00", "Maria", "2026-11-16 10:00:00 -03:00", null, false, "text"),
                "mensagem de midia/audio (message_type=2) com text_data NULL no banco deve conferir");
        check(!OriginCustodyBinder.matchesForensicMessageRecord(
                0, "", "Texto forjado sem anexo em message_type=0", "Maria", "2026-11-16 10:00:00 -03:00", null, false, "text"),
                "mensagem de texto puro (message_type=0) com banco vazio e texto nao-vazio deve reprovar");
    }

    private static void runTextMismatchScenario(Path tempRoot) throws Exception {
        Path originDir = tempRoot.resolve("CaseMismatch").resolve("CopyAll").resolve("dev456");
        Path tarDbDir = originDir.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases");
        Files.createDirectories(tarDbDir);
        Path dbFile = tarDbDir.resolve("msgstore.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, timestamp INTEGER, text_data TEXT)");
            st.execute("INSERT INTO message (_id, chat_row_id, timestamp, text_data) VALUES (201, 1, 1730720000000, 'Original no banco')");
        }
        JSONArray tamperedRecords = new JSONArray()
                .put(new JSONObject().put("domId", "201").put("text", "Original no banco e texto forjado extra")
                        .put("attachments", new JSONArray()));

        OriginCustodyBinder.OriginBindingResult res = OriginCustodyBinder.buildOriginBinding(
                originDir, null, null, tamperedRecords, Collections.emptySet(), Collections.emptyMap(), "tamper_test");
        JSONObject full = res.fullBinding;
        JSONObject n3 = full.optJSONObject("n3SqliteAudit");
        check("COM_DIVERGENCIAS".equals(n3.optString("status")), "divergencia de texto deve marcar COM_DIVERGENCIAS: " + n3);
        check(n3.optInt("diverged") == 1, "esperado 1 divergente");
        check(n3.optInt("matchedActive") == 0, "esperado 0 ativos conferidos");
        check("DIVERGENCIA_CUSTODIA_N3".equals(full.optString("status")), "status global deve reprovar com DIVERGENCIA_CUSTODIA_N3: " + full.optString("status"));
    }

    private static void runFullLineageScenario(Path tempRoot) throws Exception {
        Path originDir = tempRoot.resolve("Case001").resolve("CopyAll").resolve("dev123");
        Path mediaDir = originDir.resolve("sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images");
        Path cryptDir = originDir.resolve("sdcard/Android/media/com.whatsapp/WhatsApp/Databases");
        Path tarDbDir = originDir.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases");
        Path tarFilesDir = originDir.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/files");
        Files.createDirectories(mediaDir);
        Files.createDirectories(cryptDir);
        Files.createDirectories(tarDbDir);
        Files.createDirectories(tarFilesDir);

        byte[] custMediaBytes = new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };
        Path custMediaFile = mediaDir.resolve("IMG-20261104-WA0001.jpg");
        Files.write(custMediaFile, custMediaBytes);
        String custMediaSha = sha256(custMediaBytes);

        byte[] orphanMediaBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        Path orphanMediaFile = originDir.resolve("sdcard/extra-media.bin");
        Files.createDirectories(orphanMediaFile.getParent());
        Files.write(orphanMediaFile, orphanMediaBytes);
        String orphanMediaSha = sha256(orphanMediaBytes);

        byte[] inlineThumbBytes = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };
        String inlineThumbSha = sha256(inlineThumbBytes);

        byte[] cryptBytes = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04 };
        Path cryptFile = cryptDir.resolve("msgstore.db.crypt14");
        Files.write(cryptFile, cryptBytes);
        String cryptSha = sha256(cryptBytes);
        String cryptMd5 = md5(cryptBytes);

        byte[] keyBytes = new byte[] { 10, 20, 30, 40 };
        Files.write(tarFilesDir.resolve("key"), keyBytes);

        // Synthetic SQLite msgstore.db with modern 'message' schema (not listed in Hashs.txt -> tests post-tar gap).
        Path dbFile = tarDbDir.resolve("msgstore.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, timestamp INTEGER, text_data TEXT)");
            st.execute("INSERT INTO message (_id, chat_row_id, timestamp, text_data) VALUES (101, 7, 1730720000000, 'Ola teste pericial')");
        }
        String dbSha = sha256(Files.readAllBytes(dbFile));
        String dbMd5 = md5(Files.readAllBytes(dbFile));

        // UTF-16LE BOM Hashs.txt with wrapped Path continuation line
        StringBuilder hashsTxtContent = new StringBuilder();
        hashsTxtContent.append("Algorithm : SHA256\r\n");
        hashsTxtContent.append("Hash      : ").append(custMediaSha.toUpperCase()).append("\r\n");
        hashsTxtContent.append("Path      : C:\\Avilla-Work\\Case001\\CopyAll\\dev123\\sdcard\\Android\\media\\com.whatsapp\\WhatsApp\\\r\n");
        hashsTxtContent.append("            Media\\WhatsApp Images\\IMG-20261104-WA0001.jpg\r\n\r\n");
        hashsTxtContent.append("Algorithm : SHA256\r\n");
        hashsTxtContent.append("Hash      : ").append(cryptSha.toUpperCase()).append("\r\n");
        hashsTxtContent.append("Path      : C:\\Avilla-Work\\Case001\\CopyAll\\dev123\\sdcard\\Android\\media\\com.whatsapp\\WhatsApp\\\r\n");
        hashsTxtContent.append("            Databases\\msgstore.db.crypt14\r\n\r\n");

        Path hashsTxtPath = originDir.resolve("Hashs.txt");
        try (OutputStream out = Files.newOutputStream(hashsTxtPath)) {
            out.write(0xFF);
            out.write(0xFE);
            out.write(hashsTxtContent.toString().getBytes(StandardCharsets.UTF_16LE));
        }
        byte[] avillaBytes = "avilla-hmac-stub".getBytes(StandardCharsets.UTF_8);
        Path hashsAvillaPath = originDir.resolve("Hashs.avilla");
        Files.write(hashsAvillaPath, avillaBytes);

        // Synthetic report.pdf via IPED's PDFBox with Avilla custody labels
        String hashsTxtSha = sha256(Files.readAllBytes(hashsTxtPath));
        String hashsTxtMd5 = md5(Files.readAllBytes(hashsTxtPath));
        String avillaSha = sha256(avillaBytes);
        String avillaMd5 = md5(avillaBytes);
        Path pdfPath = originDir.resolve("report.pdf");
        writeSyntheticReportPdf(pdfPath, new String[] {
            "Collection code: TESTE001-LoJl5Bhn",
            "Start of extraction: 04-11-2026-09-13-22",
            "End of extraction: 04-11-2026-09-58-17",
            "HASH MD5 Hashs.txt:",
            hashsTxtMd5,
            "HASH Sha256 Hashs.txt:",
            hashsTxtSha,
            "HASH MD5 Hashs.avilla:",
            avillaMd5,
            "HASH Sha256 Hashs.avilla:",
            avillaSha
        });

        // Exported HTML inside parent IPED case + Lista de Arquivos.csv (SHA-256 + MD5 columns)
        Path parentIpedDir = tempRoot.resolve("IPED_parent");
        Path exportedHtmlDir = parentIpedDir.resolve("Exportados");
        Files.createDirectories(exportedHtmlDir);
        String htmlB64 = Base64.getEncoder().encodeToString(inlineThumbBytes);
        byte[] exportedHtmlBytes = ("<html><body><img src=\"data:image/jpeg;base64," + htmlB64 + "\"></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(exportedHtmlDir.resolve("chat1.html"), exportedHtmlBytes);
        String htmlMd5 = md5(exportedHtmlBytes);
        String htmlSha = sha256(exportedHtmlBytes);

        String csv = "\"Nome\";\"MD5\";\"SHA256\";\"Caminho\"\n"
                + "\"chat1.html\";\"" + htmlMd5 + "\";\"" + htmlSha + "\";\"Exportados/chat1.html\"\n"
                + "\"msgstore.db\";\"" + dbMd5 + "\";\"" + dbSha + "\";\"com.whatsapp/databases/msgstore.db\"\n"
                + "\"msgstore.db.crypt14\";\"" + cryptMd5 + "\";\"" + cryptSha + "\";\"Databases/msgstore.db.crypt14\"\n";
        Files.write(parentIpedDir.resolve("Lista de Arquivos.csv"), csv.getBytes(StandardCharsets.UTF_8));

        // Capture records: 1 active message in DB + 1 recovered/deleted message (-5)
        JSONArray records = new JSONArray()
                .put(new JSONObject().put("domId", "101").put("text", "10:00 Ola teste pericial")
                        .put("attachments", new JSONArray()
                                .put(new JSONObject().put("path", "attachments/" + custMediaSha + ".jpg").put("sha256", custMediaSha).put("size", custMediaBytes.length))
                                .put(new JSONObject().put("path", "attachments/" + inlineThumbSha + ".jpg").put("sha256", inlineThumbSha).put("size", inlineThumbBytes.length))
                                .put(new JSONObject().put("path", "attachments/" + orphanMediaSha + ".bin").put("sha256", orphanMediaSha).put("size", orphanMediaBytes.length))))
                .put(new JSONObject().put("domId", "-5").put("text", "Mensagem recuperada do freelist")
                        .put("attachments", new JSONArray()));

        Set<String> inlineShas = Collections.singleton(inlineThumbSha);
        Map<String, String> pathBySha = new HashMap<>();
        pathBySha.put(custMediaSha, "attachments/" + custMediaSha + ".jpg");
        pathBySha.put(inlineThumbSha, "attachments/" + inlineThumbSha + ".jpg");
        pathBySha.put(orphanMediaSha, "attachments/" + orphanMediaSha + ".bin");

        JSONObject sourceMetadata = new JSONObject().put("name", "chat1.html").put("size", exportedHtmlBytes.length)
                .put("type", "text/html").put("itemHash", htmlMd5).put("itemSha256", htmlSha).put("deleted", false);

        OriginCustodyBinder.OriginBindingResult res = OriginCustodyBinder.buildOriginBinding(
                originDir, parentIpedDir, sourceMetadata, records, inlineShas, pathBySha, "chat_teste-20261116");

        JSONObject full = res.fullBinding;
        check("TESTE001-LoJl5Bhn".equals(full.optString("collectionCode")), "collectionCode extracted from report.pdf: " + full.optString("collectionCode"));
        JSONArray custodyFiles = full.optJSONArray("custodyFiles");
        check("UTF-16LE".equalsIgnoreCase(custodyFiles.getJSONObject(0).optString("encoding")),
                "Hashs.txt UTF-16LE encoding must be detected");
        check(custodyFiles.getJSONObject(0).optBoolean("matchesReportPdf"), "Hashs.txt must match report.pdf");
        check(custodyFiles.getJSONObject(1).optBoolean("matchesReportPdf"), "Hashs.avilla must match report.pdf");
        check("extraido_pdfbox".equals(custodyFiles.getJSONObject(2).optString("parseStatus")), "report.pdf parsed via PDFBox");
        check(full.optInt("hashsTxtIndexedEntries") == 2, "2 SHA256 entries parsed from Hashs.txt");

        JSONObject sdb = full.optJSONObject("sourceDatabase");
        check(sdb.optBoolean("present"), "msgstore.db found");
        check(dbSha.equalsIgnoreCase(sdb.optString("sha256")), "msgstore.db sha256 matches");
        check(!sdb.optBoolean("listedInHashsTxt"), "post-tar msgstore.db must honestly report listedInHashsTxt=false");

        JSONObject iped = full.optJSONObject("ipedLineage");
        check("PAI_VINCULADO".equals(iped.optString("status")), "parent IPED CSV must link both HTML and msgstore.db via SHA-256: " + iped);
        check(iped.optInt("cryptBackupsMatchedInParentIpedCsv") == 1, "crypt14 SHA-256 matched in parent CSV");

        JSONObject attCounts = full.optJSONObject("attachmentsOrigin").optJSONObject("counts");
        check(attCounts.optInt("CUSTODIADO_AVILLA") == 1, "expected 1 CUSTODIADO_AVILLA");
        check(attCounts.optInt("EMBUTIDO_NO_HTML_IPED") == 1, "expected 1 EMBUTIDO_NO_HTML_IPED");
        check(attCounts.optInt("EM_ORIGEM_SEM_HASH_TXT") == 1, "expected 1 EM_ORIGEM_SEM_HASH_TXT");
        check(attCounts.optInt("NAO_LOCALIZADO") == 0, "expected 0 NAO_LOCALIZADO");

        JSONObject n3 = full.optJSONObject("n3SqliteAudit");
        check("CONFERE".equals(n3.optString("status")), "N3 SQLite check must succeed: " + n3);
        check(n3.optBoolean("prePostCopyOriginSha256Match"), "origin msgstore.db must be bit-identical after temp-copy N3");
        check(n3.optInt("matchedActive") == 1, "expected 1 active matched message");
        check(n3.optInt("recoveredOrNonTable") == 1, "expected 1 recovered/deleted message");
        check(n3.optInt("diverged") == 0, "expected 0 diverged messages");

        JSONObject gap = full.optJSONObject("custodyTimelineGap");
        check(gap.optBoolean("hasPostManifestGap"), "gap flag expected when msgstore.db is outside Hashs.txt");
        check("CUSTODIA_PARCIAL_COM_CONTINUIDADE_DOCUMENTADA".equals(gap.optString("status")), "gap status mismatch");
        check(dbSha.equalsIgnoreCase(sha256(Files.readAllBytes(dbFile))), "original msgstore.db unchanged on disk");
    }

    private static void runMd5OnlyRejectedAndMissingHashsTxtFailClosedTest(Path tempRoot) throws Exception {
        Path originDir = tempRoot.resolve("CaseNoHashs").resolve("CopyAll").resolve("dev789");
        Path tarDbDir = originDir.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases");
        Files.createDirectories(tarDbDir);
        Path dbFile = tarDbDir.resolve("msgstore.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, timestamp INTEGER, text_data TEXT)");
            st.execute("INSERT INTO message (_id, chat_row_id, timestamp, text_data) VALUES (301, 1, 1730720000000, 'Texto valido')");
        }
        String dbMd5 = md5(Files.readAllBytes(dbFile));

        byte[] inlineBytes = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4 };
        String inlineSha = sha256(inlineBytes);

        Path parentMd5Only = tempRoot.resolve("IPED_md5_only");
        Path expDir = parentMd5Only.resolve("Exportados");
        Files.createDirectories(expDir);
        byte[] htmlBytes = ("<html><img src=\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(inlineBytes) + "\"></html>")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(expDir.resolve("chat_md5.html"), htmlBytes);
        String htmlMd5 = md5(htmlBytes);

        String csvMd5Only = "\"Nome\";\"MD5\";\"Caminho\"\n"
                + "\"chat_md5.html\";\"" + htmlMd5 + "\";\"Exportados/chat_md5.html\"\n"
                + "\"msgstore.db\";\"" + dbMd5 + "\";\"databases/msgstore.db\"\n";
        Files.write(parentMd5Only.resolve("Lista de Arquivos.csv"), csvMd5Only.getBytes(StandardCharsets.UTF_8));

        JSONArray recs = new JSONArray().put(new JSONObject().put("domId", "301").put("text", "Texto valido")
                .put("attachments", new JSONArray().put(new JSONObject().put("path", "attachments/" + inlineSha + ".jpg")
                        .put("sha256", inlineSha).put("size", inlineBytes.length))));
        JSONObject md5OnlySource = new JSONObject().put("name", "chat_md5.html").put("size", htmlBytes.length)
                .put("type", "text/html").put("itemHash", htmlMd5).put("deleted", false);

        OriginCustodyBinder.OriginBindingResult res = OriginCustodyBinder.buildOriginBinding(
                originDir, parentMd5Only, md5OnlySource, recs, Collections.emptySet(),
                Collections.singletonMap(inlineSha, "attachments/" + inlineSha + ".jpg"), "cap_md5_only");
        JSONObject full = res.fullBinding;
        JSONObject iped = full.optJSONObject("ipedLineage");
        check("PAI_SOMENTE_MD5_INFORMATIVO_SEM_SHA256".equals(iped.optString("status")),
                "MD5-only CSV nao pode gerar PAI_VINCULADO: " + iped);
        check(!iped.optBoolean("msgstoreMatchedInParentIpedCsv"), "MD5-only nao pode certificar msgstore");
        check(!iped.optBoolean("htmlMatchedInParentIpedCsv"), "MD5-only nao pode certificar html");
        JSONObject counts = full.optJSONObject("attachmentsOrigin").optJSONObject("counts");
        check(counts.optInt("EMBUTIDO_NO_HTML_IPED") == 0, "MD5-only HTML nao pode certificar blob embutido");
        check("REFERENCIA_HASHS_TXT_AUSENTE".equals(full.optJSONObject("custodyTimelineGap").optString("status")),
                "ausencia de Hashs.txt deve marcar REFERENCIA_HASHS_TXT_AUSENTE: " + full);
        check("REFERENCIA_CUSTODIA_AUSENTE".equals(full.optString("status")),
                "ausencia de Hashs.txt nao pode gerar custodia verificada: " + full.optString("status"));
    }

    private static void writeSyntheticReportPdf(Path pdfPath, String[] lines) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10);
                cs.setLeading(14.0f);
                cs.newLineAtOffset(40, 740);
                for (String ln : lines) {
                    cs.showText(ln);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        return hex(d);
    }

    private static String md5(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(data);
        return hex(d);
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}
