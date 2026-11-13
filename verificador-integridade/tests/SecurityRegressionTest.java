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
            System.out.println("SecurityRegressionTest: PASS");
        } finally {
            deleteTree(root);
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
                .put("pluginVersion", "2.6.1-beta")
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
