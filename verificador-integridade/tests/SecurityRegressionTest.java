import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Regression checks for bounded parsing, path validation, and report preflight. */
public final class SecurityRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("verifier-security-regression");
        try {
            longPathDoesNotOverflowRegexStack(root);
            overLimitPathIsRejectedBeforeResolution(root);
            deeplyNestedJsonIsRejected();
            reportBudgetRejectsLargeOutputBeforeBuildingIt();
            batchFailurePreservesPreviousReports();
            extraFileInHtmlMessageCaptureIsFlagged(root);
            craftedHistoryReportInjectionIsRejectedAndEscaped(root);
            junctionInsideCaptureAndOutputAreRejected(root);
            disallowedHtmlAttributesAndQuoteTricksAreRejected(root);
            largeHtmlCaptureOver16MiBUpTo64MiBIsAccepted(root);
            auxiliaryCaptureFoldersAreIgnoredDuringDiscovery(root);
            System.out.println("SecurityRegressionTest: PASS");
        } finally {
            deleteTree(root);
        }
    }

    private static void auxiliaryCaptureFoldersAreIgnoredDuringDiscovery(Path root) throws Exception {
        String[] ignored = {
            "_backup_chat_capture_2026",
            "_backup_integridade_2026",
            "vinculado",
            ".iped-reextract-trash",
            ".__capture_staging_abcdef",
            "cap1.__old_2026",
            "cap1.__reextract_tmp_1",
            "cap1.__reextract_failed_1",
            "cap1.__migration_html_backup"
        };
        Path scanRoot = root.resolve("discover-aux-filter");
        Files.createDirectories(scanRoot);
        for (String name : ignored) {
            if (!IntegrityVerifier.isIgnoredCaptureDirectoryName(name)) {
                throw new AssertionError("Expected auxiliary directory to be ignored: " + name);
            }
            Path auxDir = scanRoot.resolve(name);
            Files.createDirectories(auxDir);
            Files.write(auxDir.resolve("manifest.json"), "{}".getBytes("UTF-8"));
        }
        String validName = "captura-chat-20261107-134436";
        if (IntegrityVerifier.isIgnoredCaptureDirectoryName(validName)) {
            throw new AssertionError("Valid capture folder must not be ignored");
        }
        Path validDir = scanRoot.resolve(validName);
        Files.createDirectories(validDir);
        Files.write(validDir.resolve("manifest.json"), "{}".getBytes("UTF-8"));

        java.util.List<IntegrityVerifier.Capture> discovered = IntegrityVerifier.discover(scanRoot);
        if (discovered.size() != 1 || !validName.equals(discovered.get(0).name)) {
            throw new AssertionError("discover() must only return real capture directories, found: " + discovered.size());
        }
    }

    private static void longPathDoesNotOverflowRegexStack(Path root) throws Exception {
        Path capture = Files.createDirectories(root.resolve("path-1536"));
        writeManifest(capture, repeat('a', 1536));
        Object object = newCapture(capture);
        Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", object.getClass());
        verify.setAccessible(true);
        verify.invoke(null, object);
        Field items = object.getClass().getDeclaredField("items");
        items.setAccessible(true);
        if (((List<?>) items.get(object)).isEmpty()) throw new AssertionError("Long path should be handled as a missing file");
    }

    private static void overLimitPathIsRejectedBeforeResolution(Path root) throws Exception {
        Path capture = Files.createDirectories(root.resolve("path-4097"));
        writeManifest(capture, repeat('b', 4097));
        Object object = newCapture(capture);
        Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", object.getClass());
        verify.setAccessible(true);
        try {
            verify.invoke(null, object);
            throw new AssertionError("Path over 4096 characters was accepted");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IOException)
                    || !e.getCause().getMessage().contains("Caminho excessivamente longo")) throw e;
        }
    }

    private static void deeplyNestedJsonIsRejected() throws Exception {
        Method parse = IntegrityVerifier.class.getDeclaredMethod("parseBoundedJson", String.class, Path.class);
        parse.setAccessible(true);
        String json = "{\"nested\":" + repeat('[', 65) + "0" + repeat(']', 65) + "}";
        try {
            parse.invoke(null, json, java.nio.file.Paths.get("synthetic.json"));
            throw new AssertionError("JSON nesting above the configured limit was accepted");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IOException)) throw e;
        }
    }

    private static void reportBudgetRejectsLargeOutputBeforeBuildingIt() throws Exception {
        Class<?> captureType = Class.forName("IntegrityVerifier$Capture");
        Constructor<?> captureConstructor = captureType.getDeclaredConstructor();
        captureConstructor.setAccessible(true);
        Object capture = captureConstructor.newInstance();
        Field name = captureType.getDeclaredField("name"); name.setAccessible(true); name.set(capture, "synthetic");
        Field items = captureType.getDeclaredField("items"); items.setAccessible(true);
        Class<?> itemType = Class.forName("IntegrityVerifier$Item");
        Constructor<?> itemConstructor = itemType.getDeclaredConstructor(); itemConstructor.setAccessible(true);
        Field path = itemType.getDeclaredField("path"); path.setAccessible(true);
        List<Object> rows = new ArrayList<>();
        String longPath = repeat('x', 4096);
        for (int i = 0; i < 1200; i++) { Object item = itemConstructor.newInstance(); path.set(item, longPath); rows.add(item); }
        items.set(capture, rows);
        Method budget = IntegrityVerifier.class.getDeclaredMethod("enforceReportBudget", List.class); budget.setAccessible(true);
        try {
            budget.invoke(null, Collections.singletonList(capture));
            throw new AssertionError("Oversized report estimate was accepted");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IOException)) throw e;
        }
    }

    private static void batchFailurePreservesPreviousReports() throws Exception {
        Path root = Files.createTempDirectory("verifier-report-transaction");
        try {
            Path previous = root.resolve("previous.txt");
            Path newFile = root.resolve("new.txt");
            Files.write(previous, "old-report".getBytes(StandardCharsets.UTF_8));
            Map<Path, String> reports = new LinkedHashMap<>();
            reports.put(previous, "replacement-report");
            reports.put(newFile, repeat('x', 16 * 1024 * 1024 + 1));
            Method batch = IntegrityVerifier.class.getDeclaredMethod("atomicBatch", Map.class);
            batch.setAccessible(true);
            try {
                batch.invoke(null, reports);
                throw new AssertionError("Oversized report batch was accepted");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException)) throw e;
            }
            String actual = new String(Files.readAllBytes(previous), StandardCharsets.UTF_8);
            if (!"old-report".equals(actual)) throw new AssertionError("Previous report was changed after batch rejection");
            if (Files.exists(newFile)) throw new AssertionError("Partial report was promoted after batch rejection");
            try (java.util.stream.Stream<Path> children = Files.list(root)) {
                if (children.count() != 1) throw new AssertionError("Temporary report files were not cleaned up");
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void extraFileInHtmlMessageCaptureIsFlagged(Path root) throws Exception {
        Path captureDir = root.resolve("captura-html-extra-20261112-120000");
        Files.createDirectories(captureDir);
        createValidHtmlMessageCapture(captureDir, "Ola mundo");

        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);
        Field hashField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("hash");
        hashField.setAccessible(true);
        Field itemsField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("items");
        itemsField.setAccessible(true);

        Object cleanCapture = newCapture(captureDir);
        verifyMethod.invoke(null, cleanCapture);
        String cleanStatus = (String) statusField.get(cleanCapture);
        String cleanHash = (String) hashField.get(cleanCapture);
        if (!cleanStatus.startsWith("consistente com registro local")) {
            throw new AssertionError("Clean html-message-v1 capture failed: " + cleanStatus);
        }

        // Legitimate audit log generated by recapture must remain accepted
        Files.write(captureDir.resolve("reextracao-auditoria.jsonl"), "{\"action\":\"test\"}\n".getBytes(StandardCharsets.UTF_8));
        Object auditedCapture = newCapture(captureDir);
        verifyMethod.invoke(null, auditedCapture);
        if (!((String) statusField.get(auditedCapture)).startsWith("consistente com registro local")) {
            throw new AssertionError("reextracao-auditoria.jsonl should not be flagged as extra");
        }

        // Inject extra payload file + unreferenced attachment
        Files.write(captureDir.resolve("payload.html"), "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(captureDir.resolve("attachments"));
        Files.write(captureDir.resolve("attachments").resolve(repeat('a', 64) + ".jpg"), new byte[] { 1, 2, 3 });

        Object dirtyCapture = newCapture(captureDir);
        verifyMethod.invoke(null, dirtyCapture);
        String dirtyStatus = (String) statusField.get(dirtyCapture);
        String dirtyHash = (String) hashField.get(dirtyCapture);
        if (!"arquivos extras na captura: 2".equals(dirtyStatus)) {
            throw new AssertionError("Expected extra files status, got: " + dirtyStatus);
        }
        if (!cleanHash.equals(dirtyHash)) {
            throw new AssertionError("Capture root hash must remain preserved when extra files are present");
        }
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) itemsField.get(dirtyCapture);
        Field pathF = Class.forName("IntegrityVerifier$Item").getDeclaredField("path");
        Field stateF = Class.forName("IntegrityVerifier$Item").getDeclaredField("state");
        pathF.setAccessible(true);
        stateF.setAccessible(true);
        int extraItems = 0;
        for (Object item : items) {
            if ("extra".equals(stateF.get(item))) {
                String p = (String) pathF.get(item);
                if ("payload.html".equals(p) || ("attachments/" + repeat('a', 64) + ".jpg").equals(p)) {
                    extraItems++;
                }
            }
        }
        if (extraItems != 2) {
            throw new AssertionError("Expected 2 extra items in inventory, got " + extraItems);
        }
    }

    private static void craftedHistoryReportInjectionIsRejectedAndEscaped(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Method reportMethod = IntegrityVerifier.class.getDeclaredMethod("historyReport", List.class, String.class);
        reportMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);

        // Case A (Craft): injected artifact path with newlines and forged STATUS/HASH_FINAL_HISTORICO lines
        Path dirA = Files.createDirectories(root.resolve("captura-craft-a-20261112-120001"));
        Files.write(dirA.resolve("a.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        String fileSha = sha256Utf8("hello");
        JSONArray artA = new JSONArray()
                .put(new JSONObject().put("path", "a.txt").put("size", 5).put("sha256", fileSha))
                .put(new JSONObject().put("path", "ghost.txt\nHASH_FINAL_HISTORICO|0000\nSTATUS|FORGED_OK")
                        .put("size", 1).put("sha256", repeat('a', 64)));
        List<JSONObject> sortedA = new ArrayList<>();
        for (int i = 0; i < artA.length(); i++) sortedA.add(artA.getJSONObject(i));
        sortedA.sort((x, y) -> x.getString("path").compareTo(y.getString("path")));
        StringBuilder sbA = new StringBuilder();
        for (JSONObject o : sortedA) sbA.append(o.getString("path")).append('|').append(o.getLong("size")).append('|').append(o.getString("sha256")).append('\n');
        String rootA = sha256Utf8(sbA.toString());
        JSONObject evA = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-12T12:00:01Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootA)
                .put("changes", new JSONArray().put(new JSONObject().put("action", "added").put("path", "a.txt").put("sha256", fileSha)));
        JSONObject payloadA = new JSONObject(evA.toString());
        evA.put("recordHash", sha256Utf8(payloadA.toString()));
        JSONObject recA = new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", rootA)
                .put("artifacts", artA)
                .put("events", new JSONArray().put(evA));
        Files.write(dirA.resolve("integridade.json"), recA.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(dirA.resolve("manifest.json"), new JSONObject().put("artifacts", artA).toString().getBytes(StandardCharsets.UTF_8));

        Object capA = newCapture(dirA);
        verifyMethod.invoke(null, capA);
        String statusA = (String) statusField.get(capA);
        if (!"inventário inválido".equals(statusA)) {
            throw new AssertionError("Crafted control chars in artifact path should fail verifyHistory, got: " + statusA);
        }
        String reportA = (String) reportMethod.invoke(null, Collections.singletonList(capA), repeat('1', 64));
        assertNoUnescapedLines(reportA, "FORGED_OK");

        // Case B (CraftClean): injected changes[].sha256 with newlines while event recordHash stays valid
        Path dirB = Files.createDirectories(root.resolve("captura-craft-b-20261112-120002"));
        Files.write(dirB.resolve("a.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        JSONArray artB = new JSONArray().put(new JSONObject().put("path", "a.txt").put("size", 5).put("sha256", fileSha));
        String rootB = sha256Utf8("a.txt|5|" + fileSha + "\n");
        JSONObject evB = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-12T12:00:02Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootB)
                .put("changes", new JSONArray().put(new JSONObject()
                        .put("action", "added")
                        .put("path", "a.txt")
                        .put("sha256", "deadbeef\nSTATUS|FORGED_OK\nHASH_FINAL_HISTORICO|0000")));
        JSONObject payloadB = new JSONObject(evB.toString());
        evB.put("recordHash", sha256Utf8(payloadB.toString()));
        JSONObject recB = new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", rootB)
                .put("artifacts", artB)
                .put("events", new JSONArray().put(evB));
        Files.write(dirB.resolve("integridade.json"), recB.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(dirB.resolve("manifest.json"), new JSONObject().put("artifacts", artB).toString().getBytes(StandardCharsets.UTF_8));

        Object capB = newCapture(dirB);
        verifyMethod.invoke(null, capB);
        String statusB = (String) statusField.get(capB);
        if (!"registro inválido".equals(statusB)) {
            throw new AssertionError("Crafted change sha256 should be rejected by verifyHistory, got: " + statusB);
        }
        String reportB = (String) reportMethod.invoke(null, Collections.singletonList(capB), repeat('2', 64));
        assertNoUnescapedLines(reportB, "FORGED_OK");
        assertNoUnescapedLines(reportB, "HASH_FINAL_HISTORICO|0000");
    }

    private static void assertNoUnescapedLines(String report, String forbidden) {
        String[] lines = report.split("\\r?\\n");
        for (String line : lines) {
            if (line.equals(forbidden) || line.startsWith("STATUS|" + forbidden) || line.equals("HASH_FINAL_HISTORICO|0000")) {
                throw new AssertionError("Injected line leaked unescaped into report: " + line);
            }
        }
    }

    private static void createValidHtmlMessageCapture(Path captureDir, String messageText) throws Exception {
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);

        String conversationKey = sha256Utf8("conv-test");
        String sourceFingerprint = sha256Utf8("src-test");
        String key = sha256Utf8(conversationKey + "\n101\n2026-11-12 10:00:00\nAlice\nincoming\ntext\n0");

        JSONObject msg = new JSONObject();
        msg.put("domId", "101");
        msg.put("order", 0);
        msg.put("timestamp", "2026-11-12 10:00:00");
        msg.put("sender", "Alice");
        msg.put("direction", "incoming");
        msg.put("type", "text");
        msg.put("duplicateOrdinal", 0);
        msg.put("text", messageText);
        msg.put("classes", "msg");
        msg.put("attachments", new JSONArray());
        msg.put("key", key);

        Method msgHashMethod = IntegrityVerifier.class.getDeclaredMethod("htmlMessageHash", JSONObject.class);
        msgHashMethod.setAccessible(true);
        String messageHash = (String) msgHashMethod.invoke(null, msg);
        msg.put("messageHash", messageHash);

        JSONArray messages = new JSONArray().put(msg);
        Files.write(captureDir.resolve("messages.json"),
                new JSONObject().put("messages", messages).toString().getBytes(StandardCharsets.UTF_8));

        String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + csp + "\"><title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style><style>.capture-modal{display:none}</style></head><body>"
                + "<div id=\"capture-topbar\">Conversa</div><div id=\"capture-conversation\"><div class=\"linha\" data-capture-message-key=\"" + key
                + "\">" + messageText + "</div></div><script>" + stopScript + "</script></body></html>";
        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        Files.write(captureDir.resolve("whatsapp.html"), htmlBytes);
        String htmlSha = sha256Bytes(htmlBytes);

        JSONObject sourceMeta = new JSONObject()
                .put("name", "msgstore.db")
                .put("size", 10L)
                .put("type", "application/octet-stream")
                .put("itemHash", "")
                .put("deleted", false);
        JSONObject anchors = new JSONObject().put("start", "101").put("end", "101");
        JSONArray publicHashes = new JSONArray().put(new JSONObject()
                .put("domId", "101").put("key", key).put("messageHash", messageHash));

        JSONObject manifest = new JSONObject()
                .put("schemaVersion", 1)
                .put("captureFormat", "html-message-v1")
                .put("presentationVersion", 2)
                .put("ipedVersion", "4.3.1")
                .put("pluginVersion", "2.6.2-beta")
                .put("status", "completed")
                .put("state", "complete")
                .put("messagesFile", "messages.json")
                .put("messageCount", 1)
                .put("sourceFingerprint", sourceFingerprint)
                .put("conversationKey", conversationKey)
                .put("sourceMetadata", sourceMeta)
                .put("anchors", anchors)
                .put("htmlSha256", htmlSha)
                .put("messageHashes", publicHashes);

        Method rootHashMethod = IntegrityVerifier.class.getDeclaredMethod(
                "htmlCaptureRootHash", JSONArray.class, String.class, String.class, String.class, JSONObject.class);
        rootHashMethod.setAccessible(true);
        String rootHash = (String) rootHashMethod.invoke(null, messages, htmlSha, "", "", manifest);
        manifest.put("captureRootHash", rootHash);

        Files.write(captureDir.resolve("manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static void junctionInsideCaptureAndOutputAreRejected(Path root) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path work = Files.createDirectories(root.resolve("junction-test"));
        Path outside = Files.createDirectories(work.resolve("outside-secret"));
        Files.write(outside.resolve("secret.txt"), "top-secret".getBytes(StandardCharsets.UTF_8));

        Path suiteRoot = Files.createDirectories(work.resolve("captures-root"));
        Path cap = Files.createDirectories(suiteRoot.resolve("cap-junction"));
        createValidHtmlMessageCapture(cap, "Normal");
        Path junction = cap.resolve("linkdir");
        createWindowsJunction(junction, outside);
        try {
            if (IntegrityVerifier.resolve(cap, "linkdir/secret.txt") != null) {
                throw new AssertionError("resolve() must return null for paths crossing an NTFS junction");
            }
            boolean walked = false;
            try {
                IntegrityVerifier.listCaptureFiles(cap);
                walked = true;
            } catch (IOException expected) {
                // expected
            }
            if (walked) {
                throw new AssertionError("listCaptureFiles() must fail when capture contains an NTFS junction");
            }
            boolean verified = false;
            try {
                IntegrityVerifier.main(new String[]{"--verify-only", suiteRoot.toString()});
                verified = true;
            } catch (IOException expected) {
                // expected
            }
            if (verified) {
                throw new AssertionError("--verify-only must reject a capture containing an NTFS junction");
            }
        } finally {
            Files.deleteIfExists(junction);
        }

        Path exportRoot = Files.createDirectories(work.resolve("export-root"));
        Path exportCap = Files.createDirectories(exportRoot.resolve("cap-ok"));
        createValidHtmlMessageCapture(exportCap, "Clean");
        Path outJunction = exportRoot.resolve("integridade");
        createWindowsJunction(outJunction, outside);
        try {
            boolean exported = false;
            try {
                IntegrityVerifier.main(new String[]{"--export-history", exportRoot.toString()});
                exported = true;
            } catch (IOException expected) {
                // expected
            }
            if (exported || Files.exists(outside.resolve("historico-capturas.txt"))) {
                throw new AssertionError("--export-history must reject an NTFS junction at output/integridade");
            }
            Method atomicMethod = IntegrityVerifier.class.getDeclaredMethod("atomic", Path.class, Path.class, String.class);
            atomicMethod.setAccessible(true);
            boolean atomicWrote = false;
            try {
                atomicMethod.invoke(null, exportRoot, outJunction.resolve("relatorio-p4.txt"), "payload");
                atomicWrote = true;
            } catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IOException)) {
                    throw expected;
                }
            }
            if (atomicWrote || Files.exists(outside.resolve("relatorio-p4.txt"))) {
                throw new AssertionError("atomic() must reject writing inside an NTFS junction");
            }
        } finally {
            Files.deleteIfExists(outJunction);
        }
    }

    private static void createWindowsJunction(Path link, Path target) throws Exception {
        Process p = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                link.toAbsolutePath().toString(), target.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        String output;
        try (java.io.InputStream in = p.getInputStream()) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            output = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("mklink /J failed (" + code + "): " + output);
        }
    }

    private static void disallowedHtmlAttributesAndQuoteTricksAreRejected(Path root) throws Exception {
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);

        String msgKey = repeat('a', 64);
        java.util.Set<String> keys = Collections.singleton(msgKey);
        String okBodyModern = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + csp + "\"><title>WhatsApp</title><style>body{background:#eee}</style><style>.capture-modal{display:none}</style></head><body>"
                + "<div id=\"capture-topbar\">Conversa</div><div id=\"capture-conversation\"><div class=\"linha\" data-capture-message-key=\""
                + msgKey + "\" title=\">\">Ola</div></div><script>" + stopScript + "</script></body></html>";
        validate.invoke(null, okBodyModern, Collections.emptySet(), Collections.emptyMap(), keys, 2);

        String okBodyLegacy = "<!doctype html><div class=\"linha\" data-capture-message-key=\"" + msgKey + "\" title=\">\">Ola</div>";
        validate.invoke(null, okBodyLegacy, Collections.emptySet(), Collections.emptyMap(), keys, 0);

        String[] modernPayloads = new String[] {
                okBodyModern.replace("title=\">\"", "srcset=\"https://evil.example/a.png\""),
                okBodyModern.replace("title=\">\"", "ping=\"https://evil.example/track\""),
                okBodyModern.replace("<body>", "<body background=\"https://evil.example/bg.png\">"),
                okBodyModern.replace("title=\">\"", "title=\">\" onclick=\"alert(1)\""),
                okBodyModern.replace("title=\">\"", "http-equiv=\"refresh\"")
        };
        String[] legacyPayloads = new String[] {
                okBodyLegacy.replace("title=\">\"", "srcset=\"https://evil.example/a.png\""),
                okBodyLegacy.replace("title=\">\"", "ping=\"https://evil.example/track\""),
                okBodyLegacy.replace("title=\">\"", "background=\"https://evil.example/bg.png\""),
                okBodyLegacy.replace("title=\">\"", "title=\">\" onclick=\"alert(1)\"")
        };

        for (String payload : modernPayloads) {
            assertValidateHtmlRejected(validate, payload, keys, 2);
        }
        for (String payload : legacyPayloads) {
            assertValidateHtmlRejected(validate, payload, keys, 0);
        }
    }

    private static void assertValidateHtmlRejected(Method validate, String html, java.util.Set<String> keys, int version) throws Exception {
        boolean rejected = false;
        try {
            validate.invoke(null, html, Collections.emptySet(), Collections.emptyMap(), keys, version);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                rejected = true;
            } else {
                throw e;
            }
        }
        if (!rejected) {
            throw new AssertionError("Disallowed HTML payload was accepted in presentationVersion=" + version + ": " + html);
        }
    }

    private static String sha256Utf8(String value) throws Exception {
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Bytes(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static void largeHtmlCaptureOver16MiBUpTo64MiBIsAccepted(Path root) throws Exception {
        Path work = Files.createDirectories(root.resolve("p3-large-html"));
        Path cap = Files.createDirectories(work.resolve("cap-large-valid"));

        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);

        String conversationKey = sha256Utf8("conv-large-p3");
        String sourceFingerprint = sha256Utf8("src-large-p3");
        String chunkText = repeat('A', 920_000);

        Method msgHashMethod = IntegrityVerifier.class.getDeclaredMethod("htmlMessageHash", JSONObject.class);
        msgHashMethod.setAccessible(true);

        JSONArray messages = new JSONArray();
        JSONArray publicHashes = new JSONArray();
        StringBuilder bodyRows = new StringBuilder(20_000_000);
        int count = 20;
        for (int i = 0; i < count; i++) {
            String domId = Integer.toString(100 + i);
            String ts = String.format("2026-11-12 10:00:%02d", i);
            String key = sha256Utf8(conversationKey + "\n" + domId + "\n" + ts + "\nAlice\nincoming\ntext\n0");

            JSONObject msg = new JSONObject();
            msg.put("domId", domId);
            msg.put("order", i);
            msg.put("timestamp", ts);
            msg.put("sender", "Alice");
            msg.put("direction", "incoming");
            msg.put("type", "text");
            msg.put("duplicateOrdinal", 0);
            msg.put("text", chunkText);
            msg.put("classes", "msg");
            msg.put("attachments", new JSONArray());
            msg.put("key", key);
            String messageHash = (String) msgHashMethod.invoke(null, msg);
            msg.put("messageHash", messageHash);
            messages.put(msg);
            publicHashes.put(new JSONObject().put("domId", domId).put("key", key).put("messageHash", messageHash));

            bodyRows.append("<div class=\"linha\" data-capture-message-key=\"").append(key)
                    .append("\">").append(chunkText).append("</div>");
        }

        byte[] messagesBytes = new JSONObject().put("messages", messages).toString().getBytes(StandardCharsets.UTF_8);
        if (messagesBytes.length <= 16 * 1024 * 1024) {
            throw new AssertionError("Teste invalido: messages.json deve exceder 16 MiB, obteve " + messagesBytes.length);
        }
        Files.write(cap.resolve("messages.json"), messagesBytes);

        StringBuilder htmlBuilder = new StringBuilder(messagesBytes.length + 4096);
        htmlBuilder.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"")
                .append(csp)
                .append("\"><title>WhatsApp</title><style>body{background:#eee}#capture-topbar{background:#47595a}#capture-conversation{padding-top:90px}</style><style>.capture-modal{display:none}</style></head><body>")
                .append("<div id=\"capture-topbar\">Conversa</div><div id=\"capture-conversation\">")
                .append(bodyRows)
                .append("</div><script>")
                .append(stopScript)
                .append("</script></body></html>");
        byte[] htmlBytes = htmlBuilder.toString().getBytes(StandardCharsets.UTF_8);
        if (htmlBytes.length <= 16 * 1024 * 1024) {
            throw new AssertionError("Teste invalido: whatsapp.html deve exceder 16 MiB, obteve " + htmlBytes.length);
        }
        Files.write(cap.resolve("whatsapp.html"), htmlBytes);
        String htmlSha = sha256Bytes(htmlBytes);

        JSONObject sourceMeta = new JSONObject()
                .put("name", "msgstore.db")
                .put("size", 10L)
                .put("type", "application/octet-stream")
                .put("itemHash", "")
                .put("deleted", false);
        JSONObject anchors = new JSONObject().put("start", "100").put("end", Integer.toString(100 + count - 1));
        JSONObject manifest = new JSONObject()
                .put("schemaVersion", 1)
                .put("captureFormat", "html-message-v1")
                .put("presentationVersion", 2)
                .put("ipedVersion", "4.3.1")
                .put("pluginVersion", "2.6.2-beta")
                .put("status", "completed")
                .put("state", "complete")
                .put("messagesFile", "messages.json")
                .put("messageCount", count)
                .put("sourceFingerprint", sourceFingerprint)
                .put("conversationKey", conversationKey)
                .put("sourceMetadata", sourceMeta)
                .put("anchors", anchors)
                .put("htmlSha256", htmlSha)
                .put("messageHashes", publicHashes);

        Method rootHashMethod = IntegrityVerifier.class.getDeclaredMethod(
                "htmlCaptureRootHash", JSONArray.class, String.class, String.class, String.class, JSONObject.class);
        rootHashMethod.setAccessible(true);
        String rootHash = (String) rootHashMethod.invoke(null, messages, htmlSha, "", "", manifest);
        manifest.put("captureRootHash", rootHash);
        Files.write(cap.resolve("manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));

        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Object captureObj = newCapture(cap);
        verifyMethod.invoke(null, captureObj);

        // Above 64 MiB HTML must still fail
        Path over64Html = cap.resolve("whatsapp.html");
        byte[] oldHtml = Files.readAllBytes(over64Html);
        try (java.io.OutputStream out = Files.newOutputStream(over64Html)) {
            byte[] pad = new byte[1024 * 1024];
            java.util.Arrays.fill(pad, (byte) ' ');
            for (int i = 0; i < 64; i++) {
                out.write(pad);
            }
            out.write((byte) ' ');
        }
        try {
            verifyMethod.invoke(null, newCapture(cap));
            throw new AssertionError("whatsapp.html > 64 MiB deveria ter falhado");
        } catch (InvocationTargetException expected) {
            Throwable cause = expected.getCause();
            if (!(cause instanceof IOException) || cause.getMessage() == null || !cause.getMessage().contains("excede o limite")) {
                throw new AssertionError("Erro inesperado para whatsapp.html > 64 MiB: " + cause);
            }
        } finally {
            Files.write(over64Html, oldHtml);
        }

        // Manifest > 16 MiB must still fail (MAX_METADATA_BYTES preserved)
        Path over16Manifest = cap.resolve("manifest.json");
        byte[] bigManifest = new byte[16 * 1024 * 1024 + 1024];
        java.util.Arrays.fill(bigManifest, (byte) ' ');
        bigManifest[0] = '{';
        bigManifest[bigManifest.length - 1] = '}';
        Files.write(over16Manifest, bigManifest);
        try {
            verifyMethod.invoke(null, newCapture(cap));
            throw new AssertionError("manifest.json > 16 MiB deveria ter falhado");
        } catch (InvocationTargetException expected) {
            Throwable cause = expected.getCause();
            if (!(cause instanceof IOException) || cause.getMessage() == null || !cause.getMessage().contains("excede o limite")) {
                throw new AssertionError("Erro inesperado para manifest.json > 16 MiB: " + cause);
            }
        }
    }

    private static Object newCapture(Path folder) throws Exception {
        Class<?> type = Class.forName("IntegrityVerifier$Capture");
        Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object capture = constructor.newInstance();
        Field dir = type.getDeclaredField("dir"); dir.setAccessible(true); dir.set(capture, folder);
        Field name = type.getDeclaredField("name"); name.setAccessible(true); name.set(capture, folder.getFileName().toString());
        return capture;
    }

    private static void writeManifest(Path capture, String path) throws Exception {
        String json = "{\"artifacts\":[{\"path\":\"" + path + "\",\"sha256\":\"" + repeat('0', 64) + "\",\"size\":0}]}";
        Files.write(capture.resolve("manifest.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    private static String repeat(char value, int count) { char[] chars = new char[count]; java.util.Arrays.fill(chars, value); return new String(chars); }
    private static void deleteTree(Path root) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception e) { throw new RuntimeException(e); } });
        }
    }
}
