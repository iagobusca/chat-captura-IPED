package iped.viewers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tests selective HTML recapture verification/repair and path validation. */
public final class SelectiveRecaptureRepairTest {

    private static final String CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; frame-src 'self'; style-src 'unsafe-inline'; script-src 'sha256-Pq7vIvwsQpte7OdNyOYYE9cKIEPmIFR7E/G7kB1JaJI='; object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    private static final String SCRIPT = "(function(){function stop(){document.querySelectorAll('.capture-modal audio,.capture-modal video').forEach(function(m){try{m.pause();m.currentTime=0;}catch(e){}});}var returnX=0,returnY=0,restorePending=false;function isPreview(){return location.hash.indexOf('#preview-')===0;}document.addEventListener('click',function(e){var n=e.target;while(n&&n!==document&&!n.classList.contains('capture-media-link'))n=n.parentNode;if(n&&n.classList&&n.classList.contains('capture-media-link')&&(n.getAttribute('href')||'').indexOf('#preview-')===0){returnX=window.pageXOffset||0;returnY=window.pageYOffset||0;restorePending=true;}var c=e.target;while(c&&c!==document&&!c.classList.contains('capture-close')&&!c.classList.contains('capture-close-icon'))c=c.parentNode;if(c&&c.classList&& (c.classList.contains('capture-close')||c.classList.contains('capture-close-icon'))&&isPreview())stop();},true);window.addEventListener('hashchange',function(){if(!isPreview()){stop();if(restorePending){var x=returnX,y=returnY;restorePending=false;window.requestAnimationFrame(function(){window.scrollTo(x,y);});}}});})();";

    private SelectiveRecaptureRepairTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("selective-repair-test-");
        try {
            Class<?> verifier = Class.forName("IntegrityVerifier");
            Method mainMethod = verifier.getMethod("main", String[].class);

            testPathValidationAndQuotes(tmp);

            byte[] png1 = hexDecode("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082");
            byte[] png2 = hexDecode("89504e470d0a1a0a0000000d494844520000000200000002080600000072b60d240000000b49444154789c63600002000009000289f97e2a0000000049454e44ae426082");
            String sha1 = CaptureIntegrity.sha256Bytes(png1);
            String sha2 = CaptureIntegrity.sha256Bytes(png2);
            String rel1 = "attachments/" + sha1 + ".png";
            String rel2 = "attachments/" + sha2 + ".png";

            // Case 1: completely intact capture -> skipped, nothing modified
            Path root1 = tmp.resolve("case1_intact");
            Path cap1 = root1.resolve("chat-20261117-010000");
            Path cand1 = tmp.resolve("cand1");
            createSampleCapture(cap1, png1, png2, "Oi mundo", null, null, false);
            createSampleCapture(cand1, png1, png2, "Oi mundo", null, null, false);
            Map<Path, FileTime> mtimes1 = backdateCoreFiles(cap1, rel1, rel2, true);
            Map<Path, String> hashes1 = snapshotFileHashes(cap1, rel1, rel2, true);
            HtmlLinkViewer.SelectiveRepairOutcome out1 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap1, cand1, false);
            check(out1.handledInPlace && !out1.modified, "Intact capture must skip in place without modification");
            assertUntouched(mtimes1, hashes1, cap1.resolve("messages.json"), cap1.resolve("manifest.json"),
                    cap1.resolve("whatsapp.html"), cap1.resolve(rel1), cap1.resolve(rel2), cap1.resolve("integridade.json"));
            invokeVerifier(mainMethod, new String[] { "--verify-only", root1.toString() });

            // Case 2: missing attachment -> restores ONLY missing attachment
            Path root2 = tmp.resolve("case2_missing_att");
            Path cap2 = root2.resolve("chat-20261117-010001");
            createSampleCapture(cap2, png1, png2, "Oi mundo", null, null, false);
            Files.delete(cap2.resolve(rel2));
            Map<Path, FileTime> mtimes2 = backdateCoreFiles(cap2, rel1, null, false);
            Map<Path, String> hashes2 = snapshotFileHashes(cap2, rel1, null, false);
            HtmlLinkViewer.SelectiveRepairOutcome out2 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap2, cand1, false);
            check(out2.handledInPlace && out2.modified, "Missing attachment must trigger selective repair");
            check(out2.restoredPaths.contains(rel2), "Missing attachment must be in restoredPaths");
            check(!out2.restoredPaths.contains("messages.json") && !out2.restoredPaths.contains("manifest.json")
                    && !out2.restoredPaths.contains("whatsapp.html"), "Core files must not be modified on attachment repair");
            assertUntouched(mtimes2, hashes2, cap2.resolve("messages.json"), cap2.resolve("manifest.json"),
                    cap2.resolve("whatsapp.html"), cap2.resolve(rel1));
            check(CaptureIntegrity.sha256(cap2.resolve(rel2)).equalsIgnoreCase(sha2), "Restored attachment hash mismatch");
            invokeVerifier(mainMethod, new String[] { "--verify-only", root2.toString() });

            // Case 3: altered attachment + extra file inside attachments and root -> removes extra & restores altered only
            Path root3 = tmp.resolve("case3_corrupt_and_extra");
            Path cap3 = root3.resolve("chat-20261117-010002");
            createSampleCapture(cap3, png1, png2, "Oi mundo", null, null, false);
            Files.write(cap3.resolve(rel1), "corrupt".getBytes(StandardCharsets.UTF_8));
            Path extraAtt = cap3.resolve("attachments/extra_rogue.png");
            Path extraRoot = cap3.resolve("rogue_note.txt");
            Files.write(extraAtt, "rogue".getBytes(StandardCharsets.UTF_8));
            Files.write(extraRoot, "rogue".getBytes(StandardCharsets.UTF_8));
            Map<Path, FileTime> mtimes3 = backdateCoreFiles(cap3, null, rel2, false);
            Map<Path, String> hashes3 = snapshotFileHashes(cap3, null, rel2, false);
            HtmlLinkViewer.SelectiveRepairOutcome out3 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap3, cand1, false);
            check(out3.handledInPlace && out3.modified, "Altered + extra capture must trigger selective repair");
            check(out3.removedPaths.contains("attachments/extra_rogue.png") && out3.removedPaths.contains("rogue_note.txt"),
                    "Extra files must be removed");
            check(!Files.exists(extraAtt) && !Files.exists(extraRoot), "Extra files must be deleted on disk");
            check(out3.restoredPaths.contains(rel1), "Altered attachment must be restored");
            assertUntouched(mtimes3, hashes3, cap3.resolve("messages.json"), cap3.resolve("manifest.json"),
                    cap3.resolve("whatsapp.html"), cap3.resolve(rel2));
            check(CaptureIntegrity.sha256(cap3.resolve(rel1)).equalsIgnoreCase(sha1), "Repaired attachment hash mismatch");
            invokeVerifier(mainMethod, new String[] { "--verify-only", root3.toString() });

            // Case 4: altered whatsapp.html -> restores ONLY whatsapp.html, keeps messages/manifest/attachments untouched
            Path root4 = tmp.resolve("case4_tampered_html");
            Path cap4 = root4.resolve("chat-20261117-010003");
            createSampleCapture(cap4, png1, png2, "Oi mundo", null, null, false);
            Files.write(cap4.resolve("whatsapp.html"), "<html>tampered</html>".getBytes(StandardCharsets.UTF_8));
            Map<Path, FileTime> mtimes4 = backdateFiles(cap4.resolve("messages.json"), cap4.resolve("manifest.json"),
                    cap4.resolve(rel1), cap4.resolve(rel2));
            Map<Path, String> hashes4 = snapshotHashes(cap4.resolve("messages.json"), cap4.resolve("manifest.json"),
                    cap4.resolve(rel1), cap4.resolve(rel2));
            HtmlLinkViewer.SelectiveRepairOutcome out4 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap4, cand1, false);
            check(out4.handledInPlace && out4.modified && out4.restoredPaths.contains("whatsapp.html"),
                    "Tampered HTML must be restored");
            check(!out4.restoredPaths.contains("messages.json") && !out4.restoredPaths.contains("manifest.json"),
                    "messages.json & manifest.json must stay untouched when only HTML differed");
            assertUntouched(mtimes4, hashes4, cap4.resolve("messages.json"), cap4.resolve("manifest.json"),
                    cap4.resolve(rel1), cap4.resolve(rel2));
            invokeVerifier(mainMethod, new String[] { "--verify-only", root4.toString() });

            // Case 5A: deleted messages.json -> restores ONLY messages.json
            Path root5a = tmp.resolve("case5a_missing_messages");
            Path cap5a = root5a.resolve("chat-20261117-010004");
            createSampleCapture(cap5a, png1, png2, "Oi mundo", null, null, false);
            check(HtmlLinkViewer.isReadableHtmlMessagesJson(cap5a), "Valid messages.json must be readable");
            Files.delete(cap5a.resolve("messages.json"));
            check(!HtmlLinkViewer.isReadableHtmlMessagesJson(cap5a), "Deleted messages.json must not fail check");
            Map<Path, FileTime> mtimes5a = backdateFiles(cap5a.resolve("manifest.json"), cap5a.resolve("whatsapp.html"),
                    cap5a.resolve(rel1), cap5a.resolve(rel2));
            Map<Path, String> hashes5a = snapshotHashes(cap5a.resolve("manifest.json"), cap5a.resolve("whatsapp.html"),
                    cap5a.resolve(rel1), cap5a.resolve(rel2));
            HtmlLinkViewer.SelectiveRepairOutcome out5a = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap5a, cand1, false);
            check(out5a.handledInPlace && out5a.modified && out5a.restoredPaths.contains("messages.json"),
                    "Deleted messages.json must be selectively restored");
            check(!out5a.restoredPaths.contains("manifest.json") && !out5a.restoredPaths.contains("whatsapp.html"),
                    "manifest.json & whatsapp.html must remain untouched when only messages.json was missing");
            assertUntouched(mtimes5a, hashes5a, cap5a.resolve("manifest.json"), cap5a.resolve("whatsapp.html"),
                    cap5a.resolve(rel1), cap5a.resolve(rel2));
            invokeVerifier(mainMethod, new String[] { "--verify-only", root5a.toString() });

            // Case 5B: tampered manifest.json -> restores ONLY manifest.json
            Path root5b = tmp.resolve("case5b_tampered_manifest");
            Path cap5b = root5b.resolve("chat-20261117-010005");
            createSampleCapture(cap5b, png1, png2, "Oi mundo", null, null, false);
            JSONObject badManifest = new JSONObject(CaptureIntegrity.readJsonUtf8Limited(cap5b.resolve("manifest.json")));
            badManifest.put("messageCount", 99);
            Files.write(cap5b.resolve("manifest.json"), badManifest.toString(2).getBytes(StandardCharsets.UTF_8));
            Map<Path, FileTime> mtimes5b = backdateFiles(cap5b.resolve("messages.json"), cap5b.resolve("whatsapp.html"),
                    cap5b.resolve(rel1), cap5b.resolve(rel2));
            Map<Path, String> hashes5b = snapshotHashes(cap5b.resolve("messages.json"), cap5b.resolve("whatsapp.html"),
                    cap5b.resolve(rel1), cap5b.resolve(rel2));
            HtmlLinkViewer.SelectiveRepairOutcome out5b = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap5b, cand1, false);
            check(out5b.handledInPlace && out5b.modified && out5b.restoredPaths.contains("manifest.json"),
                    "Tampered manifest.json must be selectively restored");
            check(!out5b.restoredPaths.contains("messages.json") && !out5b.restoredPaths.contains("whatsapp.html"),
                    "messages.json & whatsapp.html must remain untouched when only manifest.json was tampered");
            assertUntouched(mtimes5b, hashes5b, cap5b.resolve("messages.json"), cap5b.resolve("whatsapp.html"),
                    cap5b.resolve(rel1), cap5b.resolve(rel2));
            invokeVerifier(mainMethod, new String[] { "--verify-only", root5b.toString() });

            // Case 6: Origin Avilla + parent IPED added on recapture -> adds origin-binding.json + updates manifest origin fields only
            OriginFixture originFx = createOriginFixture(tmp, png1, png2);
            Path root6 = tmp.resolve("case6_origin_added");
            Path cap6 = root6.resolve("chat-20261117-010006");
            Path candOrigin1 = tmp.resolve("cand_origin_1");
            Path candOrigin2 = tmp.resolve("cand_origin_2");
            createSampleCapture(cap6, png1, png2, "Oi mundo", null, null, false);
            createSampleCapture(candOrigin1, png1, png2, "Oi mundo", originFx.originDir, originFx.parentIpedDir, true);
            Thread.sleep(25L);
            createSampleCapture(candOrigin2, png1, png2, "Oi mundo", originFx.originDir, originFx.parentIpedDir, true);

            Map<Path, FileTime> mtimes6 = backdateFiles(cap6.resolve("messages.json"), cap6.resolve("whatsapp.html"),
                    cap6.resolve(rel1), cap6.resolve(rel2));
            Map<Path, String> hashes6 = snapshotHashes(cap6.resolve("messages.json"), cap6.resolve("whatsapp.html"),
                    cap6.resolve(rel1), cap6.resolve(rel2));
            HtmlLinkViewer.SelectiveRepairOutcome out6 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap6, candOrigin1, true);
            check(out6.handledInPlace && out6.modified
                    && out6.restoredPaths.contains("origin-binding.json") && out6.restoredPaths.contains("manifest.json"),
                    "Adding Origem Avilla on recapture must write origin-binding.json and update manifest.json");
            check(!out6.restoredPaths.contains("messages.json") && !out6.restoredPaths.contains("whatsapp.html"),
                    "Adding Origem Avilla must leave messages.json, whatsapp.html, attachments untouched");
            assertUntouched(mtimes6, hashes6, cap6.resolve("messages.json"), cap6.resolve("whatsapp.html"),
                    cap6.resolve(rel1), cap6.resolve(rel2));
            invokeVerifier(mainMethod, new String[] { "--verify-only", root6.toString() });

            // Immediate re-run with same origin -> semantically equivalent -> skipped without touching origin-binding or core files!
            Map<Path, FileTime> mtimes6b = backdateFiles(cap6.resolve("origin-binding.json"), cap6.resolve("manifest.json"));
            Map<Path, String> hashes6b = snapshotHashes(cap6.resolve("origin-binding.json"), cap6.resolve("manifest.json"));
            HtmlLinkViewer.SelectiveRepairOutcome out6b = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap6, candOrigin2, true);
            check(out6b.handledInPlace && !out6b.modified,
                    "Semantically equivalent origin binding on second recapture must skip modification");
            assertUntouched(mtimes6b, hashes6b, cap6.resolve("origin-binding.json"), cap6.resolve("manifest.json"));

            // Case 7: genuine message text change -> not handled in place (falls back to full differential recapture)
            Path candChanged = tmp.resolve("cand_changed");
            createSampleCapture(candChanged, png1, png2, "Oi mundo editado", null, null, false);
            HtmlLinkViewer.SelectiveRepairOutcome out7 = HtmlLinkViewer.trySelectiveHtmlCaptureRepair(cap1, candChanged, false);
            check(!out7.handledInPlace, "Changed message content must fall back to full recapture");

            System.out.println("SelectiveRecaptureRepairTest: PASS");
        } finally {
            deleteTree(tmp);
        }
    }

    private static void testPathValidationAndQuotes(Path tmp) throws Exception {
        Path realDir = Files.createDirectories(tmp.resolve("valid_blur_dir"));
        check(ChatCapturePanel.stripSurroundingQuotes("  \"" + realDir + "\"  ").equals(realDir.toString()),
                "stripSurroundingQuotes failed");
        check(ChatCapturePanel.stripSurroundingQuotes("\"\"").isEmpty(), "Empty quotes must strip to empty string");

        Path validated = HtmlLinkViewer.toValidatedDirectoryPath(new File("\"" + realDir + "\""), "Pasta imagens blur", true);
        check(validated != null && validated.equals(realDir.toAbsolutePath().normalize()), "Quoted valid dir failed");

        String concatenated = "F:\\Download\\Avilla-Acquisitions\\chat_casoF:\\Download\\Avilla-Acquisitions\\imagem_ignorada";
        expectIoException(() -> HtmlLinkViewer.toValidatedDirectoryPath(new File(concatenated), "Pasta imagens blur", true),
                "concatenated invalid path");
        expectIoException(() -> HtmlLinkViewer.toValidatedDirectoryPath(new File(tmp.resolve("missing_dir").toString()), "Origem Avilla", true),
                "missing directory");

        ChatCapturePanel panel = new ChatCapturePanel(null);
        check(panel != null, "Panel with null viewer must construct cleanly");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void expectIoException(ThrowingRunnable action, String desc) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            check(expected.getMessage() != null && !expected.getMessage().isBlank(), "Expected clear message for " + desc);
            return;
        }
        throw new AssertionError("Expected IOException for " + desc);
    }

    private static final class OriginFixture {
        final Path originDir;
        final Path parentIpedDir;
        OriginFixture(Path originDir, Path parentIpedDir) {
            this.originDir = originDir;
            this.parentIpedDir = parentIpedDir;
        }
    }

    private static OriginFixture createOriginFixture(Path tmp, byte[] png1, byte[] png2) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("OriginCase/CopyAll/dev1"));
        Path mediaDir = Files.createDirectories(origin.resolve("sdcard/WhatsApp/Media"));
        Path dbDir = Files.createDirectories(origin.resolve("sdcard/msgstore/databases"));
        Path f1 = mediaDir.resolve("a.png");
        Path f2 = mediaDir.resolve("b.png");
        Files.write(f1, png1);
        Files.write(f2, png2);
        String s1 = CaptureIntegrity.sha256(f1).toUpperCase();
        String s2 = CaptureIntegrity.sha256(f2).toUpperCase();

        Path dbFile = dbDir.resolve("msgstore.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement st = con.createStatement()) {
            st.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, timestamp INTEGER, text_data TEXT)");
            st.execute("INSERT INTO message (_id, chat_row_id, timestamp, text_data) VALUES (101, 1, 1730720000000, 'Oi mundo')");
        }
        String hashsTxt = "Algorithm : SHA256\r\nHash      : " + s1 + "\r\nPath      : " + f1 + "\r\n\r\n"
                + "Algorithm : SHA256\r\nHash      : " + s2 + "\r\nPath      : " + f2 + "\r\n\r\n";
        try (OutputStream out = Files.newOutputStream(origin.resolve("Hashs.txt"))) {
            out.write(0xFF);
            out.write(0xFE);
            out.write(hashsTxt.getBytes(StandardCharsets.UTF_16LE));
        }
        Files.write(origin.resolve("Hashs.avilla"), "avilla".getBytes(StandardCharsets.UTF_8));
        Files.write(origin.resolve("report.pdf"), "%PDF-1.4 stub".getBytes(StandardCharsets.US_ASCII));

        Path parentIped = Files.createDirectories(tmp.resolve("ParentIPED"));
        String dbMd5 = md5(Files.readAllBytes(dbFile));
        String csv = "\"Nome\";\"MD5\";\"Caminho\"\n\"msgstore.db\";\"" + dbMd5 + "\";\"databases/msgstore.db\"\n";
        Files.write(parentIped.resolve("Lista de Arquivos.csv"), csv.getBytes(StandardCharsets.UTF_8));
        return new OriginFixture(origin, parentIped);
    }

    private static void createSampleCapture(Path capDir, byte[] png1, byte[] png2, String msgText,
            Path originDir, Path parentIpedDir, boolean withOrigin) throws Exception {
        Files.createDirectories(capDir.resolve("attachments"));
        String sha1 = CaptureIntegrity.sha256Bytes(png1);
        String sha2 = CaptureIntegrity.sha256Bytes(png2);
        String rel1 = "attachments/" + sha1 + ".png";
        String rel2 = "attachments/" + sha2 + ".png";
        Files.write(capDir.resolve(rel1), png1);
        Files.write(capDir.resolve(rel2), png2);

        String conversationKey = CaptureIntegrity.sha256Text("conv-selective");
        String sourceFingerprint = CaptureIntegrity.sha256Text("source-selective");
        JSONObject sourceMeta = new JSONObject().put("name", "chat.html").put("size", 1024L)
                .put("type", "text/html").put("itemHash", "0123456789abcdef0123456789abcdef").put("deleted", false);

        JSONObject att1 = new JSONObject().put("path", rel1).put("mime", "image/png").put("size", png1.length).put("sha256", sha1);
        JSONObject att2 = new JSONObject().put("path", rel2).put("mime", "image/png").put("size", png2.length).put("sha256", sha2);
        String natural = "10:00\nAna\nincoming\nimage";
        String key = CaptureIntegrity.sha256Text(conversationKey + "\n101\n" + natural + "\n0");
        JSONObject msg = new JSONObject().put("domId", "101").put("order", 0).put("timestamp", "10:00")
                .put("sender", "Ana").put("direction", "incoming").put("type", "image")
                .put("duplicateOrdinal", 0).put("text", msgText).put("classes", "linha incoming")
                .put("attachments", new JSONArray().put(att1).put(att2))
                .put("key", key);
        msg.put("messageHash", HtmlLinkViewer.computeHtmlMessageHash(msg));
        JSONArray records = new JSONArray().put(msg);

        String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP + "\">"
                + "<title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style>"
                + "<style>.capture-modal{display:none}.capture-modal:target{display:flex}</style></head><body>"
                + "<div id=\"capture-topbar\">Conversa</div>"
                + "<div id=\"capture-conversation\"><div class=\"linha\" data-capture-message-key=\"" + key + "\">"
                + "<a href=\"#preview-" + sha1 + "\">Anexo 1</a><a href=\"#preview-" + sha2 + "\">Anexo 2</a></div></div>"
                + "<section class=\"capture-modal\" id=\"preview-" + sha1 + "\"><img src=\"" + rel1 + "\"><a href=\"" + rel1 + "\" download=\"a.png\">Baixar</a>"
                + "<a href=\"" + rel1 + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir</a><a href=\"#\" class=\"capture-close\">Fechar</a></section>"
                + "<section class=\"capture-modal\" id=\"preview-" + sha2 + "\"><img src=\"" + rel2 + "\"><a href=\"" + rel2 + "\" download=\"b.png\">Baixar</a>"
                + "<a href=\"" + rel2 + "\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir</a><a href=\"#\" class=\"capture-close\">Fechar</a></section>"
                + "<script>" + SCRIPT + "</script></body></html>";
        String htmlSha = CaptureIntegrity.sha256Text(html);

        JSONArray publicHashes = new JSONArray().put(new JSONObject()
                .put("domId", "101").put("key", key).put("messageHash", msg.getString("messageHash")));
        String rootHash = HtmlLinkViewer.computeHtmlCaptureRootHash(records, htmlSha, "", "",
                conversationKey, sourceFingerprint, sourceMeta, "4.3.1", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION, "101", "101");

        JSONObject manifest = new JSONObject().put("schemaVersion", 1).put("captureFormat", "html-message-v1")
                .put("presentationVersion", 2).put("pluginVersion", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION)
                .put("ipedVersion", "4.3.1").put("status", "completed").put("createdAt", "2026-11-17T01:00:00Z")
                .put("sourceFingerprint", sourceFingerprint).put("conversationKey", conversationKey)
                .put("sourceMetadata", sourceMeta)
                .put("anchors", new JSONObject().put("start", "101").put("end", "101"))
                .put("messageCount", 1).put("state", "complete").put("messagesFile", "messages.json")
                .put("messageHashes", publicHashes).put("htmlSha256", htmlSha)
                .put("annotationsSha256", "").put("reconciliationSha256", "").put("captureRootHash", rootHash);

        if (withOrigin) {
            Map<String, String> bySha = new HashMap<>();
            bySha.put(sha1, rel1);
            bySha.put(sha2, rel2);
            OriginCustodyBinder.OriginBindingResult ob = OriginCustodyBinder.buildOriginBinding(
                    originDir, parentIpedDir, sourceMeta, records, Collections.emptySet(), bySha, capDir.getFileName().toString());
            Path obPath = capDir.resolve(OriginCustodyBinder.ORIGIN_BINDING_FILE);
            Files.write(obPath, ob.fullBinding.toString(2).getBytes(StandardCharsets.UTF_8));
            manifest.put("originBinding", ob.manifestSummary)
                    .put("originBindingFile", OriginCustodyBinder.ORIGIN_BINDING_FILE)
                    .put("originBindingSha256", CaptureIntegrity.sha256(obPath));
        }

        Files.write(capDir.resolve("messages.json"), new JSONObject().put("messages", records).toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("whatsapp.html"), html.getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        JSONObject integrity = CaptureIntegrity.integrityRecord(capDir, null, "capture_created", HtmlLinkViewer.CHAT_CAPTURE_PLUGIN_VERSION);
        Files.write(capDir.resolve("integridade.json"), integrity.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<Path, FileTime> backdateCoreFiles(Path cap, String rel1, String rel2, boolean includeIntegrity) throws IOException {
        java.util.List<Path> list = new java.util.ArrayList<>();
        list.add(cap.resolve("messages.json"));
        list.add(cap.resolve("manifest.json"));
        list.add(cap.resolve("whatsapp.html"));
        if (rel1 != null) list.add(cap.resolve(rel1));
        if (rel2 != null) list.add(cap.resolve(rel2));
        if (includeIntegrity) list.add(cap.resolve("integridade.json"));
        return backdateFiles(list.toArray(new Path[0]));
    }

    private static Map<Path, FileTime> backdateFiles(Path... paths) throws IOException {
        FileTime back = FileTime.fromMillis(System.currentTimeMillis() - 60_000L);
        Map<Path, FileTime> out = new LinkedHashMap<>();
        for (Path p : paths) {
            Files.setLastModifiedTime(p, back);
            out.put(p, Files.getLastModifiedTime(p));
        }
        return out;
    }

    private static Map<Path, String> snapshotFileHashes(Path cap, String rel1, String rel2, boolean includeIntegrity) throws IOException {
        java.util.List<Path> list = new java.util.ArrayList<>();
        list.add(cap.resolve("messages.json"));
        list.add(cap.resolve("manifest.json"));
        list.add(cap.resolve("whatsapp.html"));
        if (rel1 != null) list.add(cap.resolve(rel1));
        if (rel2 != null) list.add(cap.resolve(rel2));
        if (includeIntegrity) list.add(cap.resolve("integridade.json"));
        return snapshotHashes(list.toArray(new Path[0]));
    }

    private static Map<Path, String> snapshotHashes(Path... paths) throws IOException {
        Map<Path, String> out = new LinkedHashMap<>();
        for (Path p : paths) {
            out.put(p, CaptureIntegrity.sha256(p));
        }
        return out;
    }

    private static void assertUntouched(Map<Path, FileTime> expectedTimes, Map<Path, String> expectedHashes, Path... paths)
            throws IOException {
        for (Path p : paths) {
            FileTime nowTime = Files.getLastModifiedTime(p);
            check(nowTime.equals(expectedTimes.get(p)), "File mtime unexpectedly modified: " + p);
            String nowHash = CaptureIntegrity.sha256(p);
            check(nowHash.equals(expectedHashes.get(p)), "File hash unexpectedly modified: " + p);
        }
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
        byte[] d = MessageDigest.getInstance("MD5").digest(b);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte x : d) sb.append(Character.forDigit((x >>> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
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
