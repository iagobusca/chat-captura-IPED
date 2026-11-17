package iped.viewers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
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
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/** End-to-end verification tests for origin-binding.json and IntegrityVerifier --audit-origin. */
public final class OriginAuditEndToEndTest {

    private static final String CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-Pq7vIvwsQpte7OdNyOYYE9cKIEPmIFR7E/G7kB1JaJI='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    private static final String SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();";

    private OriginAuditEndToEndTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("origin-audit-e2e-");
        try {
            Class<?> verifier = Class.forName("IntegrityVerifier");
            Method mainMethod = verifier.getMethod("main", String[].class);

            // 1. Setup origin dir (Avilla) + parent IPED + subcase Exportados HTML
            Path origin = tmp.resolve("Case001").resolve("CopyAll").resolve("dev99");
            Path mediaDir = origin.resolve("sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images");
            Path tarDbDir = origin.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases");
            Files.createDirectories(mediaDir);
            Files.createDirectories(tarDbDir);

            byte[] pngBytes = hexDecode("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082");
            String pngSha = CaptureIntegrity.sha256Text("");
            Path pngFile = mediaDir.resolve("img1.png");
            Files.write(pngFile, pngBytes);
            pngSha = CaptureIntegrity.sha256(pngFile);

            byte[] inlinePng = hexDecode("89504e470d0a1a0a0000000d494844520000000200000002080600000072b60d240000000b49444154789c63600002000009000289f97e2a0000000049454e44ae426082");
            MessageDigest mdSha = MessageDigest.getInstance("SHA-256");
            String inlineSha = hex(mdSha.digest(inlinePng));

            Path dbFile = tarDbDir.resolve("msgstore.db");
            Class.forName("org.sqlite.JDBC");
            try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
                 Statement st = con.createStatement()) {
                st.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, timestamp INTEGER, text_data TEXT)");
                st.execute("INSERT INTO message (_id, chat_row_id, timestamp, text_data) VALUES (101, 1, 1730720000000, 'Msg auditada')");
            }
            String dbMd5 = md5(Files.readAllBytes(dbFile));

            String hashsTxt = "Algorithm : SHA256\r\nHash      : " + pngSha.toUpperCase() + "\r\nPath      : C:\\Av\\Case001\\CopyAll\\dev99\\sdcard\\Android\\media\\com.whatsapp\\WhatsApp\\Media\\WhatsApp Images\\img1.png\r\n\r\n";
            try (OutputStream out = Files.newOutputStream(origin.resolve("Hashs.txt"))) {
                out.write(0xFF);
                out.write(0xFE);
                out.write(hashsTxt.getBytes(StandardCharsets.UTF_16LE));
            }
            Files.write(origin.resolve("Hashs.avilla"), "avilla-hmac".getBytes(StandardCharsets.UTF_8));
            Files.write(origin.resolve("report.pdf"), "%PDF-1.4 stub".getBytes(StandardCharsets.US_ASCII));

            Path parentIped = tmp.resolve("IPED_parent");
            Files.createDirectories(parentIped);
            Path subcase = tmp.resolve("Prova_sub");
            Path subExp = subcase.resolve("Exportados");
            Files.createDirectories(subExp);
            byte[] expHtmlBytes = ("<html><img src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(inlinePng) + "\"></html>").getBytes(StandardCharsets.UTF_8);
            Files.write(subExp.resolve("chat.html"), expHtmlBytes);
            String htmlMd5 = md5(expHtmlBytes);

            String csv = "\"Nome\";\"MD5\";\"Caminho\"\n\"chat.html\";\"" + htmlMd5 + "\";\"Exportados/chat.html\"\n\"msgstore.db\";\"" + dbMd5 + "\";\"databases/msgstore.db\"\n";
            Files.write(parentIped.resolve("Lista de Arquivos.csv"), csv.getBytes(StandardCharsets.UTF_8));

            // 2. Create two captures under capturesRoot: cap_bound (with origin-binding.json) & cap_legacy (without)
            Path capturesRoot = tmp.resolve("Capturas");
            Path capBound = capturesRoot.resolve("cap_bound-20261116-235900");
            Path capLegacy = capturesRoot.resolve("cap_legacy-20261116-235901");
            createSyntheticHtmlCapture(capBound, pngBytes, pngSha, inlinePng, inlineSha, htmlMd5, expHtmlBytes.length, origin, parentIped, true);
            createSyntheticHtmlCapture(capLegacy, pngBytes, pngSha, inlinePng, inlineSha, htmlMd5, expHtmlBytes.length, origin, parentIped, false);

            // Verify both pass --verify-only and --audit-origin
            invokeVerifier(mainMethod, new String[] { "--verify-only", capturesRoot.toString() });
            invokeVerifier(mainMethod, new String[] { "--audit-origin", capturesRoot.toString(),
                    "--origin", origin.toString(), "--parent-iped", parentIped.toString(), "--subcase", subcase.toString() });

            Path auditRpt = capturesRoot.resolve("integridade").resolve("auditoria-origem.txt");
            check(Files.isRegularFile(auditRpt), "auditoria-origem.txt must be written");
            String rptText = new String(Files.readAllBytes(auditRpt), StandardCharsets.UTF_8);
            check(rptText.contains("N2_ANEXOS|CUSTODIADO_AVILLA=1|EMBUTIDO_NO_HTML_IPED=1|EM_ORIGEM_SEM_HASH_TXT=0|NAO_LOCALIZADO=0"),
                    "audit report counts mismatch:\n" + rptText);
            check(rptText.contains("ORIGEM_MSGSTORE_INTACTA_POS_AUDITORIA|true"), "msgstore.db intact check missing");
            check(rptText.contains("RESUMO_AUDITORIA_ORIGEM|CONFERE"), "audit summary expected CONFERE");

            // 3. Antispoof: wrong origin dir must fail --audit-origin against bound capture
            Path wrongOrigin = tmp.resolve("WrongOrigin");
            Files.createDirectories(wrongOrigin);
            try (OutputStream out = Files.newOutputStream(wrongOrigin.resolve("Hashs.txt"))) {
                out.write("Algorithm : SHA256\n".getBytes(StandardCharsets.UTF_8));
            }
            boolean wrongRejected = false;
            try {
                invokeVerifier(mainMethod, new String[] { "--audit-origin", capturesRoot.toString(), "--origin", wrongOrigin.toString() });
            } catch (Exception expected) {
                wrongRejected = true;
            }
            check(wrongRejected, "wrong origin folder must be rejected for origin-bound capture");

            // 4. Tampering origin-binding.json must fail --verify-only
            Path obPath = capBound.resolve("origin-binding.json");
            String tamperedOb = new String(Files.readAllBytes(obPath), StandardCharsets.UTF_8).replace("CONFERE", "TAMPERED");
            Files.write(obPath, tamperedOb.getBytes(StandardCharsets.UTF_8));
            boolean tamperRejected = false;
            try {
                invokeVerifier(mainMethod, new String[] { "--verify-only", capturesRoot.toString() });
            } catch (Exception expected) {
                tamperRejected = true;
            }
            check(tamperRejected, "tampered origin-binding.json must fail --verify-only");

            // 5. Orphan origin-binding.json without manifesto declaration must fail --verify-only
            Files.write(capLegacy.resolve("origin-binding.json"), "{}".getBytes(StandardCharsets.UTF_8));
            boolean orphanRejected = false;
            try {
                invokeVerifier(mainMethod, new String[] { "--verify-only", capturesRoot.toString() });
            } catch (Exception expected) {
                orphanRejected = true;
            }
            check(orphanRejected, "orphan origin-binding.json in legacy capture must fail --verify-only");

            System.out.println("OriginAuditEndToEndTest: PASS");
        } finally {
            deleteTree(tmp);
        }
    }

    private static void createSyntheticHtmlCapture(
            Path capDir, byte[] pngBytes, String pngSha, byte[] inlinePng, String inlineSha,
            String srcHtmlMd5, long srcHtmlSize, Path originDir, Path parentIpedDir, boolean withOriginBinding) throws Exception {
        Files.createDirectories(capDir.resolve("attachments"));
        String relPng = "attachments/" + pngSha + ".png";
        String relInline = "attachments/" + inlineSha + ".png";
        Files.write(capDir.resolve(relPng), pngBytes);
        Files.write(capDir.resolve(relInline), inlinePng);

        String conversationKey = CaptureIntegrity.sha256Text("conv");
        String sourceFingerprint = hex(MessageDigest.getInstance("SHA-256").digest("source".getBytes(StandardCharsets.UTF_8)));
        JSONObject sourceMeta = new JSONObject().put("name", "chat.html").put("size", srcHtmlSize)
                .put("type", "text/html").put("itemHash", srcHtmlMd5).put("deleted", false);

        JSONObject att1 = new JSONObject().put("path", relPng).put("mime", "image/png").put("size", pngBytes.length).put("sha256", pngSha);
        JSONObject att2 = new JSONObject().put("path", relInline).put("mime", "image/png").put("size", inlinePng.length).put("sha256", inlineSha);
        String msgType = "image";
        String natural = "10:00\nAna\nincoming\n" + msgType;
        String key = CaptureIntegrity.sha256Text(conversationKey + "\n101\n" + natural + "\n0");

        JSONObject msg = new JSONObject().put("domId", "101").put("order", 0).put("timestamp", "10:00")
                .put("sender", "Ana").put("direction", "incoming").put("type", msgType)
                .put("duplicateOrdinal", 0).put("text", "Msg auditada").put("classes", "linha incoming")
                .put("attachments", new JSONArray().put(att1).put(att2))
                .put("key", key);
        msg.put("messageHash", messageHash(msg));
        JSONArray records = new JSONArray().put(msg);

        String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP + "\">"
                + "<title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style>"
                + "<style>.capture-modal{display:none}.capture-modal:target{display:flex}</style></head><body>"
                + "<div id=\"capture-topbar\">Conversa</div>"
                + "<div id=\"capture-conversation\"><div class=\"linha\" data-capture-message-key=\"" + key + "\">"
                + "<a href=\"#preview-" + pngSha + "\">Anexo 1</a><a href=\"#preview-" + inlineSha + "\">Anexo 2</a></div></div>"
                + "<section class=\"capture-modal\" id=\"preview-" + pngSha + "\"><img src=\"" + relPng + "\"><a href=\"" + relPng + "\" download=\"img1.png\">Baixar</a>"
                + "<a href=\"" + relPng + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir</a><a href=\"#\" class=\"capture-close\">Fechar</a></section>"
                + "<section class=\"capture-modal\" id=\"preview-" + inlineSha + "\"><img src=\"" + relInline + "\"><a href=\"" + relInline + "\" download=\"img2.png\">Baixar</a>"
                + "<a href=\"" + relInline + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir</a><a href=\"#\" class=\"capture-close\">Fechar</a></section>"
                + "<script>" + SCRIPT + "</script></body></html>";
        String htmlSha = CaptureIntegrity.sha256Text(html);

        JSONArray publicHashes = new JSONArray().put(new JSONObject().put("domId", "101").put("key", key).put("messageHash", msg.getString("messageHash")));
        String rootHash = htmlCaptureRootHash(records, htmlSha, conversationKey, sourceFingerprint, sourceMeta);

        JSONObject manifest = new JSONObject().put("schemaVersion", 1).put("captureFormat", "html-message-v1").put("presentationVersion", 2)
                .put("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION).put("ipedVersion", "4.3.1")
                .put("status", "completed").put("createdAt", "2026-11-16T23:59:00Z")
                .put("sourceFingerprint", sourceFingerprint).put("conversationKey", conversationKey)
                .put("sourceMetadata", sourceMeta)
                .put("anchors", new JSONObject().put("start", "101").put("end", "101"))
                .put("messageCount", 1).put("state", "complete").put("messagesFile", "messages.json")
                .put("messageHashes", publicHashes)
                .put("htmlSha256", htmlSha).put("annotationsSha256", "").put("reconciliationSha256", "")
                .put("captureRootHash", rootHash);

        if (withOriginBinding) {
            Map<String, String> pathBySha = new HashMap<>();
            pathBySha.put(pngSha, relPng);
            pathBySha.put(inlineSha, relInline);
            OriginCustodyBinder.OriginBindingResult ob = OriginCustodyBinder.buildOriginBinding(
                    originDir, parentIpedDir, sourceMeta, records, Collections.singleton(inlineSha),
                    pathBySha, capDir.getFileName().toString());
            Path obPath = capDir.resolve("origin-binding.json");
            Files.write(obPath, ob.fullBinding.toString(2).getBytes(StandardCharsets.UTF_8));
            manifest.put("originBinding", ob.manifestSummary)
                    .put("originBindingFile", "origin-binding.json")
                    .put("originBindingSha256", CaptureIntegrity.sha256(obPath));
        }

        Files.write(capDir.resolve("messages.json"), new JSONObject().put("messages", records).toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("whatsapp.html"), html.getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));

        JSONObject integrity = CaptureIntegrity.integrityRecord(capDir, null, "capture_created", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION);
        Files.write(capDir.resolve("integridade.json"), integrity.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static String messageHash(JSONObject record) throws Exception {
        StringBuilder sb = new StringBuilder("html-message-v1-message\n");
        for (String f : new String[] { "domId", "order", "timestamp", "sender", "direction", "type", "duplicateOrdinal", "text", "classes" }) {
            appendField(sb, String.valueOf(record.opt(f)));
        }
        JSONArray atts = record.getJSONArray("attachments");
        for (int i = 0; i < atts.length(); i++) {
            JSONObject a = atts.getJSONObject(i);
            for (String k : new String[] { "path", "mime", "size", "sha256" }) appendField(sb, String.valueOf(a.opt(k)));
        }
        return CaptureIntegrity.sha256Text(sb.toString());
    }

    private static String htmlCaptureRootHash(JSONArray msgs, String htmlSha, String conv, String fp, JSONObject src) throws Exception {
        StringBuilder sb = new StringBuilder("html-message-v1-root\n");
        appendField(sb, htmlSha);
        appendField(sb, "");
        appendField(sb, "");
        appendField(sb, conv);
        appendField(sb, fp);
        appendField(sb, src.optString("name", ""));
        appendField(sb, Long.toString(src.optLong("size", -1)));
        appendField(sb, src.optString("type", ""));
        appendField(sb, src.optString("itemHash", ""));
        appendField(sb, Boolean.toString(src.optBoolean("deleted", false)));
        appendField(sb, "4.3.1");
        appendField(sb, HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION);
        appendField(sb, "101");
        appendField(sb, "101");
        JSONObject m = msgs.getJSONObject(0);
        appendField(sb, m.getString("key"));
        appendField(sb, m.getString("messageHash"));
        return CaptureIntegrity.sha256Text(sb.toString());
    }

    private static void appendField(StringBuilder sb, String v) {
        sb.append(v.length()).append(':').append(v).append('\n');
    }

    private static void invokeVerifier(Method mainMethod, String[] args) throws Exception {
        try {
            mainMethod.invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw ite;
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static String md5(byte[] b) throws Exception {
        return hex(MessageDigest.getInstance("MD5").digest(b));
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }

    private static byte[] hexDecode(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return out;
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
