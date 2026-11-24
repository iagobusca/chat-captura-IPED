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
            corruptedIntegridadeJsonMarksInvalidRecordWithoutThrowing(root);
            legacyHtmlSlashEventAndBodyStyleAreRejected();
            cssEscapesCommentsAndSvgActiveOrQuotedUrlsAreRejected();
            canonicalRecordHashVerifiesRegardlessOfKeyOrderAndPreservesLegacy(root);
            htmlAttributeEntityDecodingInCspAndHrefWorks();
            htmlReportBlocksAndPublicSummaryHashesMatch(root);
            legacyCaptureExtraFileFailsVerificationAndCli(root);
            junctionAtRootIsRejectedByDiscoverAndCli(root);
            htmlReportHasCspNoInlineOnclickAndEscapesApostrophe(root);
            verifyHistoryRejectsEmptyDuplicateAndDecoupledLastEventRoot(root);
            htmlMessageV1RequiresCompleteArtifactsInventoryAndValidSchemaVersion(root);
            htmlMessageCaptureFailsClosedOnCaptureRootHashMismatchAndCliSuffix(root);
            writeReportsTreatsHtmlMessageAndReconciliationStatesAsHealthy(root);
            validateRenderedCaptureHtmlRejectsCommentsCdataAndProcessingInstructions();
            comparePublicMessageManifestRejectsCaseDuplicateKeysAndKeyDomIdMismatch(root);
            rejectLinksRejectsRootJunctionAndAtomicBatchStreamingMatchesBytes(root);
            atomicBatchNeverCreatesBakFiles(root);
            htmlReportReadOnlyOverwriteIdenticalSkipAndStaleBakCleanup(root);
            publicHistoryContentComparisonAcrossVersionsAndOriginBinding(root);
            alignedHeaderContractAcceptsSafeTableHeadingsAndRejectsUnsafeDataAttrs();
            unquotedQuoteHtmlBypassPayloadsAreRejected();
            openFileGuardRejectsJunctionAndMissingFiles(root);
            originAuditN3DivergenceAndFailClosedValidation(root);
            driverCarregaDoClasspath(root);
            nenhumaBuscaExternaDeDriver();
            jarMaliciosoNaCapturaIgnorado(root);
            buildPinSqliteJdbcHash();
            laudoFolderIgnoredAndOriginReportPdfCopyValidated(root);
            wrappedPlainLabelWrapsAndDisablesHtml();
            aboutDialogContainsCreatorRepoUrlAndSince2026();
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
        budget.invoke(null, Collections.singletonList(capture));
    }

    private static void batchFailurePreservesPreviousReports() throws Exception {
        Path root = Files.createTempDirectory("verifier-report-transaction");
        try {
            Path previous = root.resolve("previous.txt");
            Path newFile = root.resolve("new.txt");
            Files.write(previous, "old-report".getBytes(StandardCharsets.UTF_8));
            Files.createDirectory(newFile);
            Map<Path, String> reports = new LinkedHashMap<>();
            reports.put(previous, "replacement-report");
            reports.put(newFile, "unwritable-target-is-directory");
            Method batch = IntegrityVerifier.class.getDeclaredMethod("atomicBatch", Map.class);
            batch.setAccessible(true);
            try {
                batch.invoke(null, reports);
                throw new AssertionError("Invalid report batch was accepted");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException)) throw e;
            }
            String actual = new String(Files.readAllBytes(previous), StandardCharsets.UTF_8);
            if (!"old-report".equals(actual)) throw new AssertionError("Previous report was changed after batch rejection");
            if (!Files.isDirectory(newFile)) throw new AssertionError("Directory target was altered after batch rejection");
            Files.delete(newFile);
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

    private static void corruptedIntegridadeJsonMarksInvalidRecordWithoutThrowing(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);
        Field integrityHashField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("integrityFileHash");
        integrityHashField.setAccessible(true);

        Path stdDir = Files.createDirectories(root.resolve("corrupt-integrity-std"));
        byte[] imgBytes = "hello-artifact".getBytes(StandardCharsets.UTF_8);
        Files.write(stdDir.resolve("a.txt"), imgBytes);
        JSONArray art = new JSONArray().put(new JSONObject().put("path", "a.txt").put("size", imgBytes.length).put("sha256", sha256Bytes(imgBytes)));
        Files.write(stdDir.resolve("manifest.json"), new JSONObject().put("artifacts", art).toString().getBytes(StandardCharsets.UTF_8));
        Files.write(stdDir.resolve("integridade.json"), "{corrupted-json".getBytes(StandardCharsets.UTF_8));
        Object stdCap = newCapture(stdDir);
        verifyMethod.invoke(null, stdCap);
        String stdStatus = (String) statusField.get(stdCap);
        if (!"registro inválido".equals(stdStatus)) {
            throw new AssertionError("Expected 'registro inválido' for corrupt integridade.json, got: " + stdStatus);
        }
        String intHash = (String) integrityHashField.get(stdCap);
        if (intHash == null || intHash.length() != 64) {
            throw new AssertionError("Expected integrityFileHash to remain populated");
        }

        Path msgDir = Files.createDirectories(root.resolve("corrupt-integrity-msg"));
        createValidHtmlMessageCapture(msgDir, "Teste");
        Files.write(msgDir.resolve("integridade.json"), "[not-closed".getBytes(StandardCharsets.UTF_8));
        Object msgCap = newCapture(msgDir);
        verifyMethod.invoke(null, msgCap);
        String msgStatus = (String) statusField.get(msgCap);
        if (msgStatus == null || !msgStatus.startsWith("registro inválido")) {
            throw new AssertionError("Expected 'registro inválido...' for corrupt html-message integridade.json, got: " + msgStatus);
        }
    }

    private static void legacyHtmlSlashEventAndBodyStyleAreRejected() throws Exception {
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);
        String msgKey = repeat('a', 64);
        java.util.Set<String> keys = Collections.singleton(msgKey);
        String base = "<!doctype html><div class=\"linha\" data-capture-message-key=\"" + msgKey + "\">Ola</div>";
        String[] badLegacy = new String[] {
                base + "<img/onerror=alert(1)>",
                "<!doctype html><div class=\"linha\" data-capture-message-key=\"" + msgKey + "\" /onclick=\"alert(1)\">Ola</div>",
                base + "<a href=\"vbscript:msgbox(1)\">x</a>",
                base + "<template><script>alert(1)</script></template>",
                base + "<style>body{color:red}</style>"
        };
        for (String payload : badLegacy) {
            assertValidateHtmlRejected(validate, payload, keys, 0);
        }
    }

    private static void cssEscapesCommentsAndSvgActiveOrQuotedUrlsAreRejected() throws Exception {
        Method validateCss = IntegrityVerifier.class.getDeclaredMethod("validateCaptureCss", String.class);
        validateCss.setAccessible(true);
        String[] badCss = new String[] {
                "@\\69mport url('https://evil.example/a.css');",
                "/*comment*/@import 'https://evil.example/a.css';",
                "body{background:/*x*/expression/*y*/(alert(1))}",
                "body{background:\\75\\72\\6c(https://evil.example/a.png)}",
                "body{behavior:vbscript:alert(1)}"
        };
        for (String css : badCss) {
            boolean rejected = false;
            try {
                validateCss.invoke(null, css);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IOException) rejected = true;
                else throw e;
            }
            if (!rejected) throw new AssertionError("Disallowed CSS accepted: " + css);
        }

        String[] badSvgs = new String[] {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><style>@import 'https://evil.example/x.css';</style></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><animate attributeName=\"href\" to=\"javascript:alert(1)\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\" fill=\"url('https://evil.example/x')\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><use href=\"https://evil.example/x.svg#i\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect style=\"fill:url('https://evil.example/x')\"/></svg>"
        };
        for (String svg : badSvgs) {
            String css = "body{background:url(\"data:image/svg+xml;base64,"
                    + java.util.Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)) + "\")}";
            boolean rejected = false;
            try {
                validateCss.invoke(null, css);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IOException) rejected = true;
                else throw e;
            }
            if (!rejected) throw new AssertionError("Disallowed SVG accepted: " + svg);
        }

        String validSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><defs><linearGradient id=\"g\"/></defs><rect width=\"10\" height=\"10\" fill=\"url('#g')\"/></svg>";
        validateCss.invoke(null, "body{background:url(\"data:image/svg+xml;base64,"
                + java.util.Base64.getEncoder().encodeToString(validSvg.getBytes(StandardCharsets.UTF_8)) + "\")}");
    }

    private static void canonicalRecordHashVerifiesRegardlessOfKeyOrderAndPreservesLegacy(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Method canonicalJsonMethod = IntegrityVerifier.class.getDeclaredMethod("canonicalJson", Object.class);
        canonicalJsonMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);

        Path dirCanonical = Files.createDirectories(root.resolve("canonical-record-hash"));
        byte[] data = "sample-content".getBytes(StandardCharsets.UTF_8);
        Files.write(dirCanonical.resolve("a.txt"), data);
        String sha = sha256Bytes(data);
        JSONArray art = new JSONArray().put(new JSONObject().put("path", "a.txt").put("size", data.length).put("sha256", sha));
        String rootHash = sha256Utf8("a.txt|" + data.length + "|" + sha + "\n");

        JSONObject payload = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-13T17:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootHash)
                .put("recordHashFormat", "canonical-v1")
                .put("changes", new JSONArray().put(new JSONObject().put("action", "added").put("path", "a.txt").put("sha256", sha)));
        String canonicalHash = sha256Utf8((String) canonicalJsonMethod.invoke(null, payload));

        // Reorder keys manually when writing JSON to simulate arbitrary JSON parser/serializer ordering
        String reorderedEventJson = "{\"action\":\"capture_created\",\"recordHashFormat\":\"canonical-v1\",\"changes\":[{\"sha256\":\""
                + sha + "\",\"path\":\"a.txt\",\"action\":\"added\"}],\"contentRootHash\":\"" + rootHash
                + "\",\"previousRecordHash\":\"\",\"sequence\":1,\"timestampUtc\":\"2026-11-13T17:00:00Z\",\"recordHash\":\""
                + canonicalHash + "\"}";
        String integrityJson = "{\"schemaVersion\":2,\"contentRootHash\":\"" + rootHash + "\",\"artifacts\":" + art.toString()
                + ",\"events\":[" + reorderedEventJson + "]}";
        Files.write(dirCanonical.resolve("integridade.json"), integrityJson.getBytes(StandardCharsets.UTF_8));
        Files.write(dirCanonical.resolve("manifest.json"), new JSONObject().put("artifacts", art).toString().getBytes(StandardCharsets.UTF_8));

        Object capOk = newCapture(dirCanonical);
        verifyMethod.invoke(null, capOk);
        String okStatus = (String) statusField.get(capOk);
        if (!"consistente com registro local".equals(okStatus)) {
            throw new AssertionError("Canonical recordHash should verify regardless of key order, got: " + okStatus);
        }

        // Tampering canonical hash fails
        Files.write(dirCanonical.resolve("integridade.json"), integrityJson.replace(canonicalHash, repeat('f', 64)).getBytes(StandardCharsets.UTF_8));
        Object capBad = newCapture(dirCanonical);
        verifyMethod.invoke(null, capBad);
        if (!"hash de evento inválido".equals(statusField.get(capBad))) {
            throw new AssertionError("Tampered canonical recordHash should fail, got: " + statusField.get(capBad));
        }

        // Legacy capture without recordHashFormat still verifies
        Path dirLegacy = Files.createDirectories(root.resolve("legacy-record-hash"));
        Files.write(dirLegacy.resolve("a.txt"), data);
        JSONObject evLegacy = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-13T17:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootHash)
                .put("changes", new JSONArray().put(new JSONObject().put("action", "added").put("path", "a.txt").put("sha256", sha)));
        evLegacy.put("recordHash", sha256Utf8(evLegacy.toString()));
        JSONObject recLegacy = new JSONObject().put("schemaVersion", 2).put("contentRootHash", rootHash).put("artifacts", art).put("events", new JSONArray().put(evLegacy));
        Files.write(dirLegacy.resolve("integridade.json"), recLegacy.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(dirLegacy.resolve("manifest.json"), new JSONObject().put("artifacts", art).toString().getBytes(StandardCharsets.UTF_8));
        Object capLegacy = newCapture(dirLegacy);
        verifyMethod.invoke(null, capLegacy);
        if (!"consistente com registro local".equals(statusField.get(capLegacy))) {
            throw new AssertionError("Legacy recordHash without recordHashFormat should still verify, got: " + statusField.get(capLegacy));
        }
    }

    private static void htmlAttributeEntityDecodingInCspAndHrefWorks() throws Exception {
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);

        String msgKey = repeat('b', 64);
        java.util.Set<String> keys = Collections.singleton(msgKey);
        String escapedCsp = csp.replace("'", "&#39;");
        String okEscapedCsp = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + escapedCsp + "\"><title>WhatsApp</title><style>body{background:#eee}</style><style>.capture-modal{display:none}</style></head><body>"
                + "<div id=\"capture-topbar\">Conversa</div><div id=\"capture-conversation\"><div class=\"linha\" data-capture-message-key=\""
                + msgKey + "\">Ola</div></div><script>" + stopScript + "</script></body></html>";
        validate.invoke(null, okEscapedCsp, Collections.emptySet(), Collections.emptyMap(), keys, 2);

        String doubleEscapedCsp = okEscapedCsp.replace("&#39;", "&amp;#39;");
        assertValidateHtmlRejected(validate, doubleEscapedCsp, keys, 2);

        String entityObfuscatedHref = okEscapedCsp.replace(
                ">Ola</div>",
                "><a href=\"&#106;avascript:alert(1)\" class=\"capture-media-link\">link</a></div>");
        assertValidateHtmlRejected(validate, entityObfuscatedHref, keys, 2);
    }

    private static void legacyCaptureExtraFileFailsVerificationAndCli(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);

        Path suiteRoot = Files.createDirectories(root.resolve("legacy-extra-suite"));
        Path capDir = Files.createDirectories(suiteRoot.resolve("cap-legacy-extra"));
        byte[] data = "sample-legacy-content".getBytes(StandardCharsets.UTF_8);
        Files.write(capDir.resolve("a.txt"), data);
        String sha = sha256Bytes(data);
        JSONArray art = new JSONArray().put(new JSONObject().put("path", "a.txt").put("size", data.length).put("sha256", sha));
        String rootHash = sha256Utf8("a.txt|" + data.length + "|" + sha + "\n");
        JSONObject ev = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-13T17:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootHash)
                .put("changes", new JSONArray().put(new JSONObject().put("action", "added").put("path", "a.txt").put("sha256", sha)));
        ev.put("recordHash", sha256Utf8(ev.toString()));
        JSONObject rec = new JSONObject().put("schemaVersion", 2).put("contentRootHash", rootHash).put("artifacts", art).put("events", new JSONArray().put(ev));
        Files.write(capDir.resolve("integridade.json"), rec.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("manifest.json"), new JSONObject().put("artifacts", art).toString().getBytes(StandardCharsets.UTF_8));

        Object capClean = newCapture(capDir);
        verifyMethod.invoke(null, capClean);
        if (!"consistente com registro local".equals(statusField.get(capClean))) {
            throw new AssertionError("Expected clean legacy capture status, got: " + statusField.get(capClean));
        }

        Files.write(capDir.resolve("extra-nao-declarado.txt"), "extra".getBytes(StandardCharsets.UTF_8));
        Object capDirty = newCapture(capDir);
        verifyMethod.invoke(null, capDirty);
        String dirtyStatus = (String) statusField.get(capDirty);
        if (dirtyStatus == null || dirtyStatus.startsWith("consistente com registro local") || !dirtyStatus.contains("arquivos extras na captura: 1")) {
            throw new AssertionError("Expected extra file failure status on legacy capture, got: " + dirtyStatus);
        }

        boolean verified = false;
        try {
            IntegrityVerifier.main(new String[]{"--verify-only", suiteRoot.toString()});
            verified = true;
        } catch (IOException expected) {
            // expected fail-closed rejection
        }
        if (verified) {
            throw new AssertionError("--verify-only must fail when a legacy capture contains extra files");
        }
    }

    private static void junctionAtRootIsRejectedByDiscoverAndCli(Path root) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path targetRoot = Files.createDirectories(root.resolve("root-junction-target"));
        Path capDir = Files.createDirectories(targetRoot.resolve("cap-ok"));
        createValidHtmlMessageCapture(capDir, "Clean");
        Path junctionRoot = root.resolve("root-junction-link");
        createWindowsJunction(junctionRoot, targetRoot);
        try {
            boolean discovered = false;
            try {
                IntegrityVerifier.discover(junctionRoot);
                discovered = true;
            } catch (IOException expected) {
                // expected
            }
            if (discovered) {
                throw new AssertionError("discover() must reject an NTFS junction root folder");
            }
            boolean verified = false;
            try {
                IntegrityVerifier.main(new String[]{"--verify-only", junctionRoot.toString()});
                verified = true;
            } catch (IOException expected) {
                // expected
            }
            if (verified) {
                throw new AssertionError("--verify-only must reject an NTFS junction root folder");
            }
        } finally {
            Files.deleteIfExists(junctionRoot);
        }
    }

    private static void htmlReportHasCspNoInlineOnclickAndEscapesApostrophe(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Method writeMethod = IntegrityVerifier.class.getDeclaredMethod(
                "writeReports", Path.class, List.class, String.class, String.class, String.class);
        writeMethod.setAccessible(true);

        Path scanRoot = Files.createDirectories(root.resolve("html-csp-test"));
        String capName = "Cap'Test-20261113-190000";
        Path capDir = Files.createDirectories(scanRoot.resolve(capName));
        createValidHtmlMessageCapture(capDir, "Apostrophe test");

        List<IntegrityVerifier.Capture> captures = IntegrityVerifier.discover(scanRoot);
        if (captures.size() != 1) {
            throw new AssertionError("Expected 1 capture, got: " + captures.size());
        }
        verifyMethod.invoke(null, captures.get(0));
        writeMethod.invoke(null, scanRoot, captures, repeat('a', 64), "2026-11-13T19:00:00Z\"injected", "");

        String html = new String(Files.readAllBytes(scanRoot.resolve("integridade.html")), StandardCharsets.UTF_8);
        if (!html.contains("<meta http-equiv=\"Content-Security-Policy\"") || !html.contains("script-src 'sha256-")) {
            throw new AssertionError("Missing CSP meta header in integridade.html");
        }
        if (html.contains("onclick=")) {
            throw new AssertionError("Found disallowed inline onclick attribute in integridade.html");
        }
        if (!html.contains("Cap&#39;Test-20261113-190000") || html.contains("Cap'Test-20261113-190000")) {
            throw new AssertionError("Apostrophe in capture name was not safely escaped as &#39; in integridade.html");
        }

        String reportJsonRaw = new String(Files.readAllBytes(scanRoot.resolve("integridade/verificacao-integridade.json")), StandardCharsets.UTF_8);
        JSONObject reportJson = new JSONObject(reportJsonRaw);
        if (!"2026-11-13T19:00:00Z\"injected".equals(reportJson.optString("generatedAt"))) {
            throw new AssertionError("generatedAt in verificacao-integridade.json was not safely JSON-escaped");
        }
    }

    private static void htmlReportBlocksAndPublicSummaryHashesMatch(Path root) throws Exception {
        Path scanRoot = Files.createDirectories(root.resolve("html-report-blocks"));
        String[] names = { "João_São_Paulo-20261113-190000", "Maria_Cotia-20261113-190000" };
        for (int idx = 0; idx < names.length; idx++) {
            Path capDir = Files.createDirectories(scanRoot.resolve(names[idx]));
            byte[] payload = ("conteudo-" + idx).getBytes(StandardCharsets.UTF_8);
            Files.write(capDir.resolve("dados.txt"), payload);
            String fSha = sha256Utf8("conteudo-" + idx);
            JSONArray arts = new JSONArray().put(new JSONObject().put("path", "dados.txt").put("size", payload.length).put("sha256", fSha));
            Files.write(capDir.resolve("manifest.json"), new JSONObject().put("artifacts", arts).toString().getBytes(StandardCharsets.UTF_8));
            Files.write(capDir.resolve("integridade.json"), new JSONObject().put("artifacts", arts).toString().getBytes(StandardCharsets.UTF_8));
        }

        List<IntegrityVerifier.Capture> captures = IntegrityVerifier.discover(scanRoot);
        if (captures.size() != 2) throw new AssertionError("Expected 2 discovered captures");
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
        verifyMethod.setAccessible(true);
        for (IntegrityVerifier.Capture c : captures) verifyMethod.invoke(null, c);

        Method setHashMethod = IntegrityVerifier.class.getDeclaredMethod("setHash", List.class);
        setHashMethod.setAccessible(true);
        String rootHash = (String) setHashMethod.invoke(null, captures);
        Method historyMethod = IntegrityVerifier.class.getDeclaredMethod("historyReport", List.class, String.class);
        historyMethod.setAccessible(true);
        String fullHistory = (String) historyMethod.invoke(null, captures, rootHash);
        String pubHistory = IntegrityVerifier.publicHistoryReport(captures, rootHash);

        Method writeMethod = IntegrityVerifier.class.getDeclaredMethod(
                "writeReports", Path.class, List.class, String.class, String.class, String.class);
        writeMethod.setAccessible(true);
        writeMethod.invoke(null, scanRoot, captures, rootHash, "2026-11-13T19:00:00Z", fullHistory);

        String html = new String(Files.readAllBytes(scanRoot.resolve("integridade.html")), StandardCharsets.UTF_8);
        String expectedPubHash = sha256Utf8(pubHistory.replace("\r\n", "\n").replaceAll("\n+$", "") + "\n");
        if (!html.contains("Hash geral Público: <code id=\"publicSummaryHash\">" + expectedPubHash + "</code>")) {
            throw new AssertionError("Missing public summary hash in integridade.html");
        }
        for (IntegrityVerifier.Capture c : captures) {
            String fullBlock = IntegrityVerifier.captureHistoryBlock(c, false);
            String pubBlock = IntegrityVerifier.captureHistoryBlock(c, true);
            if (!fullHistory.contains("\n" + fullBlock + "\n")) {
                throw new AssertionError("captureHistoryBlock(full) must match historyReport block verbatim");
            }
            if (!pubHistory.contains("\n" + pubBlock + "\n")) {
                throw new AssertionError("captureHistoryBlock(public) must match publicHistoryReport block verbatim");
            }
            String fullSha = sha256Utf8(fullBlock);
            String pubSha = sha256Utf8(pubBlock);
            if (!html.contains("data-block-full=\"" + fullSha + "\" data-block-public=\"" + pubSha + "\"")) {
                throw new AssertionError("Missing block SHA-256 attributes for " + c.name);
            }
        }
    }

    private static void verifyHistoryRejectsEmptyDuplicateAndDecoupledLastEventRoot(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Method canonicalJsonMethod = IntegrityVerifier.class.getDeclaredMethod("canonicalJson", Object.class);
        canonicalJsonMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);

        Path capEmpty = Files.createDirectories(root.resolve("history-empty-artifacts"));
        JSONObject evEmpty = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-14T09:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", repeat('a', 64))
                .put("recordHashFormat", "canonical-v1");
        evEmpty.put("recordHash", sha256Utf8((String) canonicalJsonMethod.invoke(null, evEmpty)));
        JSONObject recEmpty = new JSONObject().put("schemaVersion", 2).put("contentRootHash", repeat('a', 64))
                .put("artifacts", new JSONArray()).put("events", new JSONArray().put(evEmpty));
        Files.write(capEmpty.resolve("integridade.json"), recEmpty.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(capEmpty.resolve("manifest.json"), new JSONObject().put("artifacts", new JSONArray()).toString().getBytes(StandardCharsets.UTF_8));
        Object emptyCap = newCapture(capEmpty);
        verifyMethod.invoke(null, emptyCap);
        String emptyStatus = (String) statusField.get(emptyCap);
        if (!"registro incompleto".equals(emptyStatus)) {
            throw new AssertionError("Empty artifacts array must fail verifyHistory, got: " + emptyStatus);
        }

        Path capDup = Files.createDirectories(root.resolve("history-dup-artifacts"));
        byte[] sample = "dup".getBytes(StandardCharsets.UTF_8);
        Files.write(capDup.resolve("a.txt"), sample);
        String sha = sha256Bytes(sample);
        JSONArray dupArt = new JSONArray()
                .put(new JSONObject().put("path", "a.txt").put("size", sample.length).put("sha256", sha))
                .put(new JSONObject().put("path", "A.TXT").put("size", sample.length).put("sha256", sha));
        Method verifyHistoryMethod = IntegrityVerifier.class.getDeclaredMethod(
                "verifyHistory", Class.forName("IntegrityVerifier$Capture"), JSONObject.class);
        verifyHistoryMethod.setAccessible(true);
        Object dupCap = newCapture(capDup);
        verifyHistoryMethod.invoke(null, dupCap, new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", repeat('b', 64))
                .put("artifacts", dupArt)
                .put("events", new JSONArray().put(evEmpty)));
        String dupStatus = (String) statusField.get(dupCap);
        if (!"inventário inválido".equals(dupStatus)) {
            throw new AssertionError("Duplicate case-folded path in artifacts must fail verifyHistory, got: " + dupStatus);
        }

        Path capDecoupled = Files.createDirectories(root.resolve("history-decoupled-last-event"));
        Files.write(capDecoupled.resolve("a.txt"), sample);
        String rootHash = sha256Utf8("a.txt|" + sample.length + "|" + sha + "\n");
        String wrongRoot = repeat('c', 64);
        JSONArray cleanArt = new JSONArray().put(new JSONObject().put("path", "a.txt").put("size", sample.length).put("sha256", sha));
        JSONObject ev1 = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-14T09:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", rootHash)
                .put("recordHashFormat", "canonical-v1");
        String h1 = sha256Utf8((String) canonicalJsonMethod.invoke(null, ev1));
        ev1.put("recordHash", h1);
        JSONObject ev2 = new JSONObject()
                .put("sequence", 2)
                .put("timestampUtc", "2026-11-14T09:01:00Z")
                .put("action", "reextracted")
                .put("previousRecordHash", h1)
                .put("contentRootHash", wrongRoot)
                .put("recordHashFormat", "canonical-v1");
        ev2.put("recordHash", sha256Utf8((String) canonicalJsonMethod.invoke(null, ev2)));
        JSONObject decoupledRec = new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", rootHash)
                .put("artifacts", cleanArt)
                .put("events", new JSONArray().put(ev1).put(ev2));
        Object decoupledCap = newCapture(capDecoupled);
        verifyHistoryMethod.invoke(null, decoupledCap, decoupledRec);
        String decoupledStatus = (String) statusField.get(decoupledCap);
        if (!"cadeia do histórico divergente do estado atual".equals(decoupledStatus)) {
            throw new AssertionError("Mismatched last event contentRootHash must be rejected, got: " + decoupledStatus);
        }
    }

    private static JSONObject buildLocalIntegrityRecordForFiles(Path capDir, String[] relPaths) throws Exception {
        Method canonicalJsonMethod = IntegrityVerifier.class.getDeclaredMethod("canonicalJson", Object.class);
        canonicalJsonMethod.setAccessible(true);
        java.util.List<String> sorted = new ArrayList<>(java.util.Arrays.asList(relPaths));
        java.util.Collections.sort(sorted);
        JSONArray artifacts = new JSONArray();
        StringBuilder rootLines = new StringBuilder();
        JSONArray changes = new JSONArray();
        for (String rel : sorted) {
            byte[] bytes = Files.readAllBytes(capDir.resolve(rel));
            String sha = sha256Bytes(bytes);
            artifacts.put(new JSONObject().put("path", rel).put("size", bytes.length).put("sha256", sha));
            rootLines.append(rel).append('|').append(bytes.length).append('|').append(sha).append('\n');
            changes.put(new JSONObject().put("action", "added").put("path", rel).put("sha256", sha));
        }
        String contentRoot = sha256Utf8(rootLines.toString());
        JSONObject ev = new JSONObject()
                .put("sequence", 1)
                .put("timestampUtc", "2026-11-14T09:00:00Z")
                .put("action", "capture_created")
                .put("previousRecordHash", "")
                .put("contentRootHash", contentRoot)
                .put("recordHashFormat", "canonical-v1")
                .put("changes", changes);
        ev.put("recordHash", sha256Utf8((String) canonicalJsonMethod.invoke(null, ev)));
        return new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", contentRoot)
                .put("artifacts", artifacts)
                .put("events", new JSONArray().put(ev));
    }

    private static void htmlMessageV1RequiresCompleteArtifactsInventoryAndValidSchemaVersion(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Field statusField = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusField.setAccessible(true);

        Path suiteRoot = Files.createDirectories(root.resolve("html-incomplete-inv-suite"));
        Path capDir = Files.createDirectories(suiteRoot.resolve("cap-subset-inv"));
        createValidHtmlMessageCapture(capDir, "mensagem valida");
        // Inventory lists only whatsapp.html, omitting messages.json
        JSONObject subsetIntegrity = buildLocalIntegrityRecordForFiles(capDir, new String[]{"whatsapp.html"});
        Files.write(capDir.resolve("integridade.json"), subsetIntegrity.toString().getBytes(StandardCharsets.UTF_8));

        Object cap = newCapture(capDir);
        verifyMethod.invoke(null, cap);
        String status = (String) statusField.get(cap);
        if (status == null || !status.contains("inventário incompleto na captura html-message-v1: messages.json")) {
            throw new AssertionError("Expected incomplete html-message-v1 inventory rejection, got: " + status);
        }
        boolean cliPassed = false;
        try {
            IntegrityVerifier.main(new String[]{"--verify-only", suiteRoot.toString()});
            cliPassed = true;
        } catch (IOException expected) {
            // expected fail-closed CLI rejection
        }
        if (cliPassed) {
            throw new AssertionError("--verify-only must fail when html-message-v1 integridade.json omits messages.json");
        }

        Path capV1 = Files.createDirectories(root.resolve("html-schema-v1-integrity"));
        createValidHtmlMessageCapture(capV1, "mensagem v1");
        JSONObject v1Integrity = buildLocalIntegrityRecordForFiles(capV1, new String[]{"whatsapp.html", "messages.json"});
        v1Integrity.put("schemaVersion", 1);
        Files.write(capV1.resolve("integridade.json"), v1Integrity.toString().getBytes(StandardCharsets.UTF_8));
        Object badSchemaCap = newCapture(capV1);
        boolean rejected = false;
        try {
            verifyMethod.invoke(null, badSchemaCap);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) rejected = true;
            else throw e;
        }
        if (!rejected) {
            throw new AssertionError("html-message-v1 must reject integridade.json with schemaVersion < 2");
        }
    }

    private static void htmlMessageCaptureFailsClosedOnCaptureRootHashMismatchAndCliSuffix(Path root) throws Exception {
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyMethod.setAccessible(true);
        Method cliCheck = IntegrityVerifier.class.getDeclaredMethod("isConsistentLocalHistoryStatus", String.class);
        cliCheck.setAccessible(true);

        if ((Boolean) cliCheck.invoke(null, "consistente com registro local; manifesto de mensagens requer referência pública")) {
            throw new AssertionError("CLI local status check must reject suffixed failure message");
        }
        if (!(Boolean) cliCheck.invoke(null, "consistente com registro local; consistente com registro local (manifesto de mensagens)")) {
            throw new AssertionError("CLI local status check must accept dual consistent html-message-v1 status");
        }

        Path suiteDir = Files.createDirectories(root.resolve("html-roothash-mismatch-suite"));
        Path capDir = Files.createDirectories(suiteDir.resolve("cap-mismatch"));
        createValidHtmlMessageCapture(capDir, "oi");
        JSONObject validIntegrity = buildLocalIntegrityRecordForFiles(capDir, new String[]{"whatsapp.html", "messages.json"});
        Files.write(capDir.resolve("integridade.json"), validIntegrity.toString().getBytes(StandardCharsets.UTF_8));

        String rawManifest = new String(Files.readAllBytes(capDir.resolve("manifest.json")), StandardCharsets.UTF_8);
        JSONObject tamperedManifest = new JSONObject(rawManifest).put("captureRootHash", repeat('f', 64));
        Files.write(capDir.resolve("manifest.json"), tamperedManifest.toString(2).getBytes(StandardCharsets.UTF_8));

        boolean rejected = false;
        try {
            verifyMethod.invoke(null, newCapture(capDir));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) rejected = true;
            else throw e;
        }
        if (!rejected) {
            throw new AssertionError("verifyMessageCapture must throw on captureRootHash mismatch");
        }
    }

    private static void writeReportsTreatsHtmlMessageAndReconciliationStatesAsHealthy(Path root) throws Exception {
        Path scanRoot = Files.createDirectories(root.resolve("html-report-healthy-states"));
        Path capDir = Files.createDirectories(scanRoot.resolve("cap-healthy"));
        createValidHtmlMessageCapture(capDir, "mensagem saudavel");
        JSONObject integrity = buildLocalIntegrityRecordForFiles(capDir, new String[]{"whatsapp.html", "messages.json"});
        Files.write(capDir.resolve("integridade.json"), integrity.toString().getBytes(StandardCharsets.UTF_8));

        List<IntegrityVerifier.Capture> captures = IntegrityVerifier.discover(scanRoot);
        Method verifyMethod = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
        verifyMethod.setAccessible(true);
        for (IntegrityVerifier.Capture c : captures) verifyMethod.invoke(null, c);
        Method setHashMethod = IntegrityVerifier.class.getDeclaredMethod("setHash", List.class);
        setHashMethod.setAccessible(true);
        String rootHash = (String) setHashMethod.invoke(null, captures);
        Method historyMethod = IntegrityVerifier.class.getDeclaredMethod("historyReport", List.class, String.class);
        historyMethod.setAccessible(true);
        String fullHistory = (String) historyMethod.invoke(null, captures, rootHash);
        Method writeMethod = IntegrityVerifier.class.getDeclaredMethod(
                "writeReports", Path.class, List.class, String.class, String.class, String.class);
        writeMethod.setAccessible(true);
        writeMethod.invoke(null, scanRoot, captures, rootHash, "2026-11-14T09:00:00Z", fullHistory);

        String html = new String(Files.readAllBytes(scanRoot.resolve("integridade.html")), StandardCharsets.UTF_8);
        if (!html.contains("class=\"ok\">mensagem estruturada</td>") || html.contains("class=\"bad\"")) {
            throw new AssertionError("Healthy html-message-v1 states must render with class=ok in integridade.html");
        }
    }

    private static void validateRenderedCaptureHtmlRejectsCommentsCdataAndProcessingInstructions() throws Exception {
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);

        String key = sha256Utf8("msg-comment-test");
        java.util.Set<String> keys = Collections.singleton(key);
        String[] badTokens = {"<!--comment-->", "-->", "<![CDATA[x]]>", "<?xml version=\"1.0\"?>"};
        for (String token : badTokens) {
            String modern = "<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                    + csp + "\"><style>body{}</style><style>.c{}</style></head><body>" + token
                    + "<div data-capture-message-key=\"" + key + "\">ok</div><script>" + stopScript + "</script></body></html>";
            assertValidateHtmlRejected(validate, modern, keys, 2);

            String legacy = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>" + token
                    + "<div data-capture-message-key=\"" + key + "\">ok</div></body></html>";
            assertValidateHtmlRejected(validate, legacy, keys, 0);
        }
    }

    private static void comparePublicMessageManifestRejectsCaseDuplicateKeysAndKeyDomIdMismatch(Path root) throws Exception {
        Path capDir = Files.createDirectories(root.resolve("public-manifest-key-check"));
        createValidHtmlMessageCapture(capDir, "ola");
        Path manifestPath = capDir.resolve("manifest.json");
        JSONObject baseManifest = new JSONObject(new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8));
        JSONObject row = baseManifest.getJSONArray("messageHashes").getJSONObject(0);
        String upperKey = row.getString("key").toUpperCase(java.util.Locale.ROOT);
        String msgHash = row.getString("messageHash");

        Method rootHashMethod = IntegrityVerifier.class.getDeclaredMethod(
                "htmlCaptureRootHash", JSONArray.class, String.class, String.class, String.class, JSONObject.class);
        rootHashMethod.setAccessible(true);

        // 1. Valid match across lowercase local vs uppercase reference key
        JSONArray upperRows = new JSONArray().put(new JSONObject().put("domId", "101").put("key", upperKey).put("messageHash", msgHash));
        JSONObject upperRef = new JSONObject(baseManifest.toString()).put("messageHashes", upperRows);
        upperRef.put("captureRootHash", rootHashMethod.invoke(null, upperRows, upperRef.getString("htmlSha256"), "", "", upperRef));
        String upperMatchReport = IntegrityVerifier.comparePublicMessageManifest(manifestPath, "cap", upperRef.toString());
        if (!upperMatchReport.contains("101|integra") || !upperMatchReport.contains("Integras: 1 | divergentes: 0")) {
            throw new AssertionError("Case-normalized key match should report 101|integra, got:\n" + upperMatchReport);
        }

        // 2. Duplicate case-variant key in publicManifestMap must be rejected
        Method publicMapMethod = IntegrityVerifier.class.getDeclaredMethod("publicMessageMap", JSONArray.class);
        publicMapMethod.setAccessible(true);
        boolean dupRejected = false;
        try {
            publicMapMethod.invoke(null, new JSONArray()
                    .put(new JSONObject().put("domId", "101").put("key", upperKey.toLowerCase(java.util.Locale.ROOT)).put("messageHash", msgHash))
                    .put(new JSONObject().put("domId", "102").put("key", upperKey).put("messageHash", msgHash)));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) dupRejected = true;
            else throw e;
        }
        if (!dupRejected) {
            throw new AssertionError("publicMessageMap must reject duplicate case-folded keys");
        }

        // 3. Matching domId + messageHash with mismatched key must be reported as chave_divergente
        String wrongKey = sha256Utf8("forged-key");
        JSONArray forgedRows = new JSONArray().put(new JSONObject().put("domId", "101").put("key", wrongKey).put("messageHash", msgHash));
        JSONObject forgedRef = new JSONObject(baseManifest.toString()).put("messageHashes", forgedRows);
        forgedRef.put("captureRootHash", rootHashMethod.invoke(null, forgedRows, forgedRef.getString("htmlSha256"), "", "", forgedRef));
        String keyMismatchReport = IntegrityVerifier.comparePublicMessageManifest(manifestPath, "cap", forgedRef.toString());
        if (!keyMismatchReport.contains("101|chave_divergente") || !keyMismatchReport.contains("Integras: 0 | divergentes: 1")) {
            throw new AssertionError("Key mismatch for same domId must not report integra, got:\n" + keyMismatchReport);
        }
    }

    private static void rejectLinksRejectsRootJunctionAndAtomicBatchStreamingMatchesBytes(Path root) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path outside = Files.createDirectories(root.resolve("outside-root-junction-target"));
        Path rootJunction = root.resolve("root-junction-for-atomic");
        createWindowsJunction(rootJunction, outside);
        try {
            Method atomicMethod = IntegrityVerifier.class.getDeclaredMethod("atomic", Path.class, Path.class, String.class);
            atomicMethod.setAccessible(true);
            boolean rootJunctionRejected = false;
            try {
                atomicMethod.invoke(null, rootJunction, rootJunction.resolve("integridade.html"), "<html></html>");
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IOException) {
                    rootJunctionRejected = true;
                } else {
                    throw e;
                }
            }
            if (!rootJunctionRejected || Files.exists(outside.resolve("integridade.html"))) {
                throw new AssertionError("atomic() / rejectLinks() must reject when normalizedRoot itself is an NTFS junction");
            }
        } finally {
            Files.deleteIfExists(rootJunction);
        }
    }

    private static void alignedHeaderContractAcceptsSafeTableHeadingsAndRejectsUnsafeDataAttrs() throws Exception {
        Field scanLimitField = IntegrityVerifier.class.getDeclaredField("MAX_SCAN_ENTRIES");
        scanLimitField.setAccessible(true);
        if (scanLimitField.getLong(null) != 100_000L) {
            throw new AssertionError("MAX_SCAN_ENTRIES must be 100000");
        }
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);

        String key = sha256Utf8("msg-header-contract-test");
        java.util.Set<String> keys = Collections.singleton(key);
        String safeHeader = "<div id=\"topbar\" class=\"hdr\" dir=\"ltr\" lang=\"pt-BR\" data-iped-hdr=\"ok\">"
                + "<h1>Chat</h1><h3>Sub</h3><table class=\"meta\" dir=\"ltr\"><tbody>"
                + "<tr><th colspan=\"2\" width=\"120\" height=\"32\" data-col=\"a\">Cab</th></tr>"
                + "<tr><td rowspan=\"1\" data-role=\"cell\"><em><u>Ok</u></em><hr></td></tr>"
                + "</tbody></table></div>";
        String modern = "<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + csp + "\"><style>body{}</style><style>.c{}</style></head><body>"
                + safeHeader + "<div data-capture-message-key=\"" + key + "\">ok</div><script>" + stopScript + "</script></body></html>";
        validate.invoke(null, modern, Collections.emptySet(), Collections.emptyMap(), keys, 2);

        String[] badFragments = {
            "<td width=\"120px\">bad</td>",
            "<div data-capture-link-token=\"x\">bad</div>",
            "<span data-capture-message-key=\"" + key + "\">bad</span>",
            "<img src=\"x.png\" data-extra=\"1\">",
            "<img src=\"data:image/svg+xml;base64,PHN2Zz4=\">"
        };
        for (String frag : badFragments) {
            String invalid = "<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                    + csp + "\"><style>body{}</style><style>.c{}</style></head><body>"
                    + frag + "<div data-capture-message-key=\"" + key + "\">ok</div><script>" + stopScript + "</script></body></html>";
            assertValidateHtmlRejected(validate, invalid, keys, 2);
        }
    }

    private static void unquotedQuoteHtmlBypassPayloadsAreRejected() throws Exception {
        Method validate = IntegrityVerifier.class.getDeclaredMethod(
                "validateRenderedCaptureHtml", String.class, java.util.Set.class, Map.class, java.util.Set.class, int.class);
        validate.setAccessible(true);
        Field cspField = IntegrityVerifier.class.getDeclaredField("CAPTURE_CSP");
        cspField.setAccessible(true);
        String csp = (String) cspField.get(null);
        Field scriptField = IntegrityVerifier.class.getDeclaredField("CAPTURE_MEDIA_STOP_SCRIPT");
        scriptField.setAccessible(true);
        String stopScript = (String) scriptField.get(null);

        String key = sha256Utf8("msg-f01-quote-bypass");
        java.util.Set<String> keys = Collections.singleton(key);
        String modernHead = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + csp + "\">";
        String modernTail = "<style>body{}</style><style>.c{}</style></head><body><div data-capture-message-key=\"" + key
                + "\">2 &lt; 3</div><script>" + stopScript + "</script></body></html>";
        validate.invoke(null, modernHead + modernTail, Collections.emptySet(), Collections.emptyMap(), keys, 2);

        String[] modernBypasses = {
            modernHead + "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.invalid\" a=b'>" + modernTail,
            modernHead + "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.invalid\" a=b\">" + modernTail,
            modernHead + "<meta '>" + modernTail,
            modernHead + modernTail.replace(">2 &lt; 3</div>", ">2 &lt; 3<iframe src=\"evil.html\" a=b'></iframe></div>"),
            modernHead + modernTail.replace(">2 &lt; 3</div>", ">2 &lt; 3<a href=\"https://evil.invalid\" a=b'>phish</a></div>"),
            modernHead + modernTail.replace(">2 &lt; 3</div>", ">2 &lt; 3<svg onload=\"alert(1)\" a=b'></div>"),
            modernHead + modernTail.replace(">2 &lt; 3</div>", ">2 &lt; 3<div title=\"a\" onClick=\"alert(1)\" a=b'>x</div></div>"),
            modernHead + "<!bad-decl>" + modernTail,
            modernHead + modernTail.replace("</div>", "</div extra=1>")
        };
        for (String payload : modernBypasses) {
            assertValidateHtmlRejected(validate, payload, keys, 2);
        }

        String legacyBase = "<!doctype html><div class=\"linha\" data-capture-message-key=\"" + key + "\">ok</div>";
        validate.invoke(null, legacyBase, Collections.emptySet(), Collections.emptyMap(), keys, 0);
        String[] legacyBypasses = {
            legacyBase + "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.invalid\" a=b'>",
            legacyBase + "<a href=\"https://evil.invalid\" a=b'>link</a>",
            legacyBase + "<div data-x=\"unclosed>"
        };
        for (String payload : legacyBypasses) {
            assertValidateHtmlRejected(validate, payload, keys, 0);
        }
    }

    private static void openFileGuardRejectsJunctionAndMissingFiles(Path root) throws Exception {
        Method guard = IntegrityVerifier.class.getDeclaredMethod("validateOpenableRegularFile", Path.class);
        guard.setAccessible(true);
        Path dir = Files.createDirectories(root.resolve("open-file-guard"));
        Path regular = Files.write(dir.resolve("historico-capturas.txt"), "ok".getBytes(StandardCharsets.UTF_8));
        guard.invoke(null, regular);

        for (Path invalid : new Path[]{null, dir, dir.resolve("missing.txt")}) {
            boolean rejected = false;
            try {
                guard.invoke(null, invalid);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IOException) rejected = true;
                else throw e;
            }
            if (!rejected) throw new AssertionError("validateOpenableRegularFile must reject: " + invalid);
        }
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            Path targetDir = Files.createDirectories(root.resolve("open-file-target"));
            Files.write(targetDir.resolve("historico-capturas.txt"), "outside".getBytes(StandardCharsets.UTF_8));
            Path junctionDir = root.resolve("open-file-junc");
            createWindowsJunction(junctionDir, targetDir);
            try {
                boolean rejected = false;
                try {
                    guard.invoke(null, junctionDir.resolve("historico-capturas.txt"));
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) rejected = true;
                    else throw e;
                }
                if (!rejected) throw new AssertionError("validateOpenableRegularFile must reject file inside NTFS junction parent");
            } finally {
                Files.deleteIfExists(junctionDir);
            }
        }
    }

    private static void atomicBatchNeverCreatesBakFiles(Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("no-bak-batch"));
        try {
            Path html = dir.resolve("integridade.html");
            Path sub = dir.resolve("integridade");
            Path txt = sub.resolve("verificacao-integridade.txt");
            Path json = sub.resolve("verificacao-integridade.json");
            Method batch = IntegrityVerifier.class.getDeclaredMethod("atomicBatch", Path.class, Map.class);
            batch.setAccessible(true);

            Map<Path, String> first = new LinkedHashMap<>();
            first.put(html, "<html>v1</html>");
            first.put(txt, "v1");
            first.put(json, "{\"v\":1}");
            batch.invoke(null, dir, first);

            Map<Path, String> second = new LinkedHashMap<>();
            second.put(html, "<html>v2</html>");
            second.put(txt, "v2");
            second.put(json, "{\"v\":2}");
            batch.invoke(null, dir, second);

            if (!"<html>v2</html>".equals(new String(Files.readAllBytes(html), StandardCharsets.UTF_8))) {
                throw new AssertionError("atomicBatch did not overwrite report cleanly");
            }
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                java.util.List<Path> leftovers = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".bak") || p.getFileName().toString().endsWith(".tmp"))
                        .collect(java.util.stream.Collectors.toList());
                if (!leftovers.isEmpty()) {
                    throw new AssertionError("atomicBatch must never create or leave .bak/.tmp files: " + leftovers);
                }
            }
        } finally {
            deleteTree(dir);
        }
    }

    @SuppressWarnings("unchecked")
    private static void publicHistoryContentComparisonAcrossVersionsAndOriginBinding(Path root) throws Exception {
        Path caseDir = Files.createDirectories(root.resolve("pub-history-compat"));
        try {
            Path capA = Files.createDirectories(caseDir.resolve("Chat_Alfa-20261114-100000"));
            Path capB = Files.createDirectories(caseDir.resolve("Chat_Beta-20261114-100001"));
            createValidHtmlMessageCapture(capA, "Mensagem Alfa 1");
            Files.write(capA.resolve("integridade.json"),
                    buildLocalIntegrityRecordForFiles(capA, new String[]{"whatsapp.html", "messages.json"}).toString(2).getBytes(StandardCharsets.UTF_8));
            createValidHtmlMessageCapture(capB, "Mensagem Beta 1");
            Files.write(capB.resolve("integridade.json"),
                    buildLocalIntegrityRecordForFiles(capB, new String[]{"whatsapp.html", "messages.json"}).toString(2).getBytes(StandardCharsets.UTF_8));

            Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
            verify.setAccessible(true);
            Method setHashMethod = IntegrityVerifier.class.getDeclaredMethod("setHash", java.util.List.class);
            setHashMethod.setAccessible(true);
            Method histFull = IntegrityVerifier.class.getDeclaredMethod("historyReport", java.util.List.class, String.class);
            histFull.setAccessible(true);
            Method canonicalJsonMethod = IntegrityVerifier.class.getDeclaredMethod("canonicalJson", Object.class);
            canonicalJsonMethod.setAccessible(true);
            Method cmpDetailed = IntegrityVerifier.class.getDeclaredMethod("comparePublicHistoryDetailed",
                    java.util.List.class, String.class, String.class, String.class, String.class);
            cmpDetailed.setAccessible(true);

            java.util.List<IntegrityVerifier.Capture> capsOld = IntegrityVerifier.discover(caseDir);
            for (IntegrityVerifier.Capture c : capsOld) verify.invoke(null, c);
            String oldSet = (String) setHashMethod.invoke(null, capsOld);
            String oldPub = IntegrityVerifier.publicHistoryReport(capsOld, oldSet);
            String oldPubBom = "\uFEFF\n" + oldPub;

            // Re-stamp capA integridade.json timestamp (same HASH_CONTEUDO, different EVENTO timestamp)
            JSONObject intA = new JSONObject(new String(Files.readAllBytes(capA.resolve("integridade.json")), StandardCharsets.UTF_8));
            JSONArray evA = intA.getJSONArray("events");
            JSONObject e1 = evA.getJSONObject(0);
            e1.put("timestampUtc", "2026-11-17T12:00:00Z");
            e1.remove("recordHash");
            e1.put("recordHash", sha256Utf8((String) canonicalJsonMethod.invoke(null, e1)));
            Files.write(capA.resolve("integridade.json"), intA.toString(2).getBytes(StandardCharsets.UTF_8));

            // Add origin-binding.json to capB while keeping identical message & attachment hashes
            JSONObject originJson = new JSONObject().put("originBindingVersion", 1).put("status", "VINCULADO_ORIGEM_AVILLA");
            byte[] originBytes = originJson.toString(2).getBytes(StandardCharsets.UTF_8);
            Files.write(capB.resolve("origin-binding.json"), originBytes);
            String originSha = sha256Utf8(new String(originBytes, StandardCharsets.UTF_8));
            JSONObject manB = new JSONObject(new String(Files.readAllBytes(capB.resolve("manifest.json")), StandardCharsets.UTF_8));
            manB.put("originBindingFile", "origin-binding.json")
                .put("originBindingSha256", originSha)
                .put("originBinding", new JSONObject().put("originBindingVersion", 1).put("status", "VINCULADO_ORIGEM_AVILLA"));
            Files.write(capB.resolve("manifest.json"), manB.toString(2).getBytes(StandardCharsets.UTF_8));
            JSONObject intB = new JSONObject(new String(Files.readAllBytes(capB.resolve("integridade.json")), StandardCharsets.UTF_8));
            JSONArray artsB = intB.getJSONArray("artifacts");
            artsB.put(new JSONObject().put("path", "origin-binding.json").put("size", originBytes.length).put("sha256", originSha));
            StringBuilder invB = new StringBuilder();
            java.util.List<JSONObject> sortedB = new ArrayList<>();
            for (int i = 0; i < artsB.length(); i++) sortedB.add(artsB.getJSONObject(i));
            sortedB.sort(java.util.Comparator.comparing(x -> x.getString("path")));
            for (JSONObject x : sortedB) invB.append(x.getString("path")).append('|').append(x.getLong("size")).append('|').append(x.getString("sha256")).append('\n');
            String newRootB = sha256Utf8(invB.toString());
            intB.put("contentRootHash", newRootB);
            JSONObject evB = intB.getJSONArray("events").getJSONObject(0);
            evB.put("timestampUtc", "2026-11-17T12:01:00Z");
            evB.put("contentRootHash", newRootB);
            evB.remove("recordHash");
            evB.put("recordHash", sha256Utf8((String) canonicalJsonMethod.invoke(null, evB)));
            Files.write(capB.resolve("integridade.json"), intB.toString(2).getBytes(StandardCharsets.UTF_8));

            java.util.List<IntegrityVerifier.Capture> capsNew = IntegrityVerifier.discover(caseDir);
            for (IntegrityVerifier.Capture c : capsNew) verify.invoke(null, c);
            String newSet = (String) setHashMethod.invoke(null, capsNew);
            String newFull = (String) histFull.invoke(null, capsNew, newSet);
            String newPub = IntegrityVerifier.publicHistoryReport(capsNew, newSet);

            // Legacy exact match still rejects (by design), whereas detailed cryptographic match validates!
            String legacyClassify = IntegrityVerifier.classifyPublicHistoryMatch(oldPub, newFull, newPub);
            if (!"DIVERGE".equals(legacyClassify)) {
                throw new AssertionError("Exact classifier must keep reporting DIVERGE when metadata changed: " + legacyClassify);
            }
            if (!"MATCH_PUBLIC".equals(IntegrityVerifier.classifyPublicHistoryMatch("\uFEFF\n" + newPub, newFull, newPub))) {
                throw new AssertionError("normalizePublicRecord must strip BOM and leading newlines cleanly");
            }
            Object res = cmpDetailed.invoke(null, capsNew, newSet, newFull, newPub, oldPubBom);
            Class<?> resCls = res.getClass();
            if (!((Boolean) resCls.getDeclaredMethod("isVerified").invoke(res))) {
                throw new AssertionError("Detailed comparison must validate equivalent message content across versions: "
                        + resCls.getDeclaredField("summaryLine").get(res));
            }
            if ((Integer) resCls.getDeclaredField("sameRootCount").get(res) != 1
                    || (Integer) resCls.getDeclaredField("contentMatchCount").get(res) != 1) {
                throw new AssertionError("Expected 1 SAME_CONTENT_ROOT + 1 CONTENT_MATCH");
            }

            // Tampering a message hash in the reference must still DIVERGE
            String tamperedPub = oldPub.replaceFirst("(?m)^(ARQUIVO\\|mensagem:[^|]+\\|[^|]+\\|)[0-9a-f]{64}\\|([^|]+\\|)[0-9a-f]{64}(\\|)", "$1" + repeat('f', 64) + "|$2" + repeat('f', 64) + "$3");
            Object badRes = cmpDetailed.invoke(null, capsNew, newSet, newFull, newPub, tamperedPub);
            if ((Boolean) resCls.getDeclaredMethod("isVerified").invoke(badRes)) {
                throw new AssertionError("Tampered message hash in reference must NOT validate");
            }

            // Missing local capture must DIVERGE
            Object partialRes = cmpDetailed.invoke(null, java.util.Collections.singletonList(capsNew.get(0)),
                    newSet, newFull, newPub, oldPub);
            if ((Boolean) resCls.getDeclaredMethod("isVerified").invoke(partialRes)
                    || (Integer) resCls.getDeclaredField("missingLocalCount").get(partialRes) != 1) {
                throw new AssertionError("Missing local capture must report DIVERGE with missingLocalCount=1");
            }
        } finally {
            deleteTree(caseDir);
        }
    }

    @SuppressWarnings("unchecked")
    private static void htmlReportReadOnlyOverwriteIdenticalSkipAndStaleBakCleanup(Path root) throws Exception {
        Path caseDir = Files.createDirectories(root.resolve("html-readonly-reuse"));
        Path html = caseDir.resolve("integridade.html");
        Path staleBak = caseDir.resolve(".integridade.html.205927726989895346.bak");
        try {
            Path cap1 = Files.createDirectories(caseDir.resolve("Chat_Ro-20261114-100000"));
            createValidHtmlMessageCapture(cap1, "Mensagem RO");
            Files.write(cap1.resolve("integridade.json"),
                    buildLocalIntegrityRecordForFiles(cap1, new String[]{"whatsapp.html", "messages.json"}).toString(2).getBytes(StandardCharsets.UTF_8));

            Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
            verify.setAccessible(true);
            Method setHash = IntegrityVerifier.class.getDeclaredMethod("setHash", List.class);
            setHash.setAccessible(true);
            Method hist = IntegrityVerifier.class.getDeclaredMethod("historyReport", List.class, String.class);
            hist.setAccessible(true);
            Method writeReports = IntegrityVerifier.class.getDeclaredMethod("writeReports",
                    Path.class, List.class, String.class, String.class, String.class);
            writeReports.setAccessible(true);

            List<IntegrityVerifier.Capture> caps = IntegrityVerifier.discover(caseDir);
            for (IntegrityVerifier.Capture c : caps) verify.invoke(null, c);
            String sh = (String) setHash.invoke(null, caps);
            String h = (String) hist.invoke(null, caps, sh);

            // Simulate an outdated read-only integridade.html plus a leftover .bak from older versions
            Files.write(html, "<html>outdated</html>".getBytes(StandardCharsets.UTF_8));
            Files.write(staleBak, "old-bak".getBytes(StandardCharsets.UTF_8));
            for (Path ro : new Path[] { html, staleBak }) {
                try {
                    Files.setAttribute(ro, "dos:readonly", Boolean.TRUE);
                } catch (UnsupportedOperationException ignored) {
                }
            }

            writeReports.invoke(null, caseDir, caps, sh, "2026-11-17T17:00:00Z", h);
            String firstWritten = new String(Files.readAllBytes(html), StandardCharsets.UTF_8);
            if (!firstWritten.contains("Chat_Ro-20261114-100000") || firstWritten.contains("outdated")) {
                throw new AssertionError("writeReports must overwrite outdated ReadOnly integridade.html");
            }
            if (Files.exists(staleBak)) {
                throw new AssertionError("writeReports must remove stale leftover .integridade.html.*.bak files");
            }

            // Second run with different timestamp only: identical verification result must skip rewriting integridade.html
            java.nio.file.attribute.FileTime mtime1 = Files.getLastModifiedTime(html);
            try {
                Files.setAttribute(html, "dos:readonly", Boolean.TRUE);
            } catch (UnsupportedOperationException ignored) {
            }
            Thread.sleep(35L);
            writeReports.invoke(null, caseDir, caps, sh, "2026-11-17T18:30:00Z", h);
            if (!mtime1.equals(Files.getLastModifiedTime(html))) {
                throw new AssertionError("Identical verification result must preserve existing integridade.html without overwriting");
            }

            // Third run with changed capture set: must update even when ReadOnly
            Path cap2 = Files.createDirectories(caseDir.resolve("Chat_Ro2-20261114-100001"));
            createValidHtmlMessageCapture(cap2, "Mensagem RO 2");
            Files.write(cap2.resolve("integridade.json"),
                    buildLocalIntegrityRecordForFiles(cap2, new String[]{"whatsapp.html", "messages.json"}).toString(2).getBytes(StandardCharsets.UTF_8));
            List<IntegrityVerifier.Capture> caps2 = IntegrityVerifier.discover(caseDir);
            for (IntegrityVerifier.Capture c : caps2) verify.invoke(null, c);
            String sh2 = (String) setHash.invoke(null, caps2);
            String h2 = (String) hist.invoke(null, caps2, sh2);
            writeReports.invoke(null, caseDir, caps2, sh2, "2026-11-17T19:00:00Z", h2);
            String updated = new String(Files.readAllBytes(html), StandardCharsets.UTF_8);
            if (!updated.contains("Chat_Ro2-20261114-100001")) {
                throw new AssertionError("Changed result must update ReadOnly integridade.html cleanly");
            }
            try (java.util.stream.Stream<Path> walk = Files.walk(caseDir)) {
                List<Path> leftovers = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".bak") || p.getFileName().toString().endsWith(".tmp"))
                        .collect(java.util.stream.Collectors.toList());
                if (!leftovers.isEmpty()) {
                    throw new AssertionError("Zero .bak/.tmp files may exist after writeReports: " + leftovers);
                }
            }
        } finally {
            for (Path ro : new Path[] { html, staleBak }) {
                try {
                    if (Files.exists(ro)) Files.setAttribute(ro, "dos:readonly", Boolean.FALSE);
                } catch (UnsupportedOperationException | IOException ignored) {
                }
            }
            deleteTree(caseDir);
        }
    }

    private static void originAuditN3DivergenceAndFailClosedValidation(Path root) throws Exception {
        // 1. N3 text matching must never accept substrings, empty DB wildcards, or arbitrary text after ISO timestamps
        if (IntegrityVerifier.matchesForensicMessageText("Ola", "Ola eu vou matar ele", "", "")) {
            throw new AssertionError("Substring match in N3 must fail");
        }
        if (IntegrityVerifier.matchesForensicMessageText("Ola eu vou matar ele", "Ola", "", "")) {
            throw new AssertionError("Reverse substring match in N3 must fail");
        }
        if (IntegrityVerifier.matchesForensicMessageText("", "Texto forjado", "", "")) {
            throw new AssertionError("Empty DB text must not act as wildcard");
        }
        if (IntegrityVerifier.matchesForensicMessageText("Msg original", "Msg original 2026-11-16 10:00:00 texto forjado", "", "")) {
            throw new AssertionError("Arbitrary suffix after ISO timestamp must not match");
        }
        if (!IntegrityVerifier.matchesForensicMessageText(
                "Mensagem editada",
                "Joao Mensagem editada 2026-11-16 10:00:00 -03:00 Editada em 2026-11-16 10:05:00 -03:00",
                "Joao",
                "")) {
            throw new AssertionError("Explicit Editada em ISO suffix with sender must match");
        }
        if (!IntegrityVerifier.matchesForensicMessageText("Caf\u00E9", "Cafe\u0301", "", "")) {
            throw new AssertionError("NFC canonical equivalence must match");
        }
        String sampleKey = repeat('b', 64);
        String sampleHtml = "<div class=\"captured-message-block\" data-message-key=\"" + sampleKey + "\">"
                + "<div class=\"linha\"><div class=\"buble\"><div class=\"incoming from\">"
                + "<span class=\"name\">Maria</span><br>"
                + "<div class=\"quoteFrom\"><div><span class=\"quoteUser\">Joao</span><br>"
                + "<span class=\"quoteMsg\">Citacao</span></div></div>"
                + "<img class=\"fwd\"> Encaminhada <br>"
                + "Texto <b>formatado</b> <img class=\"e1f923\">"
                + "<span class=\"time\">2026-11-16 10:00:00 -03:00</span>"
                + "</div></div></div></div>";
        Map<String, String> htmlMap = IntegrityVerifier.extractCapturedHtmlBlocksByKey(sampleHtml);
        String sampleChunk = htmlMap.get(sampleKey);
        String sampleCap = "Maria Joao Citacao Encaminhada Texto formatado 2026-11-16 10:00:00 -03:00";
        if (!IntegrityVerifier.matchesForensicMessageRecord(
                0, "Texto *formatado* \uD83E\uDD23", sampleCap, sampleCap, "2026-11-16 10:00:00 -03:00", sampleChunk, false, "text")) {
            throw new AssertionError("HTML bubble with quoteFrom, fwd, formatting and eHEX emoji must match SQLite text_data");
        }
        if (IntegrityVerifier.matchesForensicMessageRecord(
                0, "Texto diferente", sampleCap, sampleCap, "2026-11-16 10:00:00 -03:00", sampleChunk, false, "text")) {
            throw new AssertionError("Mismatched SQLite text_data must fail matchesForensicMessageRecord");
        }
        if (!IntegrityVerifier.matchesForensicMessageRecord(
                2, "", "Maria Audio (0:12) 2026-11-16 10:00:00 -03:00", "Maria", "2026-11-16 10:00:00 -03:00", null, false, "text")) {
            throw new AssertionError("Media message_type=2 with empty SQLite text_data must match");
        }
        if (IntegrityVerifier.matchesForensicMessageRecord(
                0, "", "Texto forjado em message_type=0", "Maria", "2026-11-16 10:00:00 -03:00", null, false, "text")) {
            throw new AssertionError("Plain text message_type=0 with empty SQLite text_data and non-empty capture must fail");
        }

        Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
        verify.setAccessible(true);
        Method runAudit = IntegrityVerifier.class.getDeclaredMethod("runOriginAudit",
                Path.class, List.class, Path.class, Path.class, Path.class, java.util.function.Consumer.class);
        runAudit.setAccessible(true);
        Class<?> oaResCls = Class.forName("IntegrityVerifier$OriginAuditResult");
        Field passedField = oaResCls.getDeclaredField("passed");
        passedField.setAccessible(true);
        Field statusField = oaResCls.getDeclaredField("overallStatus");
        statusField.setAccessible(true);
        Field reportTextField = oaResCls.getDeclaredField("reportText");
        reportTextField.setAccessible(true);
        Method htmlMethod = IntegrityVerifier.class.getDeclaredMethod(
                "buildOriginAuditHtml", oaResCls, Path.class, Path.class, Path.class, int.class);
        htmlMethod.setAccessible(true);

        // Setup valid Avilla origin folder
        Path origin = Files.createDirectories(root.resolve("OriginAuditFailClosed/CaseX/CopyAll/dev1"));
        String dummyFileSha = repeat('a', 64);
        String hashsTxtContent = "Algorithm : SHA256\r\nHash      : " + dummyFileSha + "\r\nPath      : C:\\CaseX\\dummy.txt\r\n\r\n";
        byte[] hashsTxtUtf16 = hashsTxtContent.getBytes(StandardCharsets.UTF_16LE);
        byte[] hashsTxtBytes = new byte[hashsTxtUtf16.length + 2];
        hashsTxtBytes[0] = (byte) 0xFF;
        hashsTxtBytes[1] = (byte) 0xFE;
        System.arraycopy(hashsTxtUtf16, 0, hashsTxtBytes, 2, hashsTxtUtf16.length);
        Path hashsTxtPath = origin.resolve("Hashs.txt");
        Files.write(hashsTxtPath, hashsTxtBytes);
        String hashsTxtSha = sha256Bytes(hashsTxtBytes);

        byte[] avillaBytes = "avilla-stub".getBytes(StandardCharsets.UTF_8);
        Files.write(origin.resolve("Hashs.avilla"), avillaBytes);
        String avillaSha = sha256Bytes(avillaBytes);
        byte[] pdfBytes = "%PDF-1.4 stub".getBytes(StandardCharsets.US_ASCII);
        Files.write(origin.resolve("report.pdf"), pdfBytes);
        String pdfSha = sha256Bytes(pdfBytes);

        JSONArray validCustodyFiles = new JSONArray()
                .put(new JSONObject().put("fileName", "Hashs.txt").put("sha256", hashsTxtSha))
                .put(new JSONObject().put("fileName", "Hashs.avilla").put("sha256", avillaSha))
                .put(new JSONObject().put("fileName", "report.pdf").put("sha256", pdfSha));

        // 2. N3 divergence reported by plugin must fail overall audit and style st-bad in HTML
        Path capDivRoot = Files.createDirectories(root.resolve("AuditN3Div"));
        Path capDiv = Files.createDirectories(capDivRoot.resolve("Chat_N3Div-20261116-120000"));
        createValidHtmlMessageCapture(capDiv, "Mensagem capturada");
        JSONObject n3DivBinding = new JSONObject()
                .put("originBindingVersion", 1)
                .put("status", "DIVERGENCIA_CUSTODIA_N3")
                .put("custodyFiles", validCustodyFiles)
                .put("n3SqliteAudit", new JSONObject()
                        .put("status", "COM_DIVERGENCIAS")
                        .put("matchedActive", 0)
                        .put("recoveredOrNonTable", 0)
                        .put("diverged", 1)
                        .put("prePostCopyOriginSha256Match", true));
        byte[] n3DivBytes = n3DivBinding.toString(2).getBytes(StandardCharsets.UTF_8);
        Files.write(capDiv.resolve("origin-binding.json"), n3DivBytes);
        JSONObject manDiv = new JSONObject(new String(Files.readAllBytes(capDiv.resolve("manifest.json")), StandardCharsets.UTF_8));
        manDiv.put("originBindingFile", "origin-binding.json")
              .put("originBindingSha256", sha256Bytes(n3DivBytes))
              .put("originBinding", new JSONObject().put("originBindingVersion", 1).put("status", "DIVERGENCIA_CUSTODIA_N3"));
        Files.write(capDiv.resolve("manifest.json"), manDiv.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capDiv.resolve("integridade.json"),
                buildLocalIntegrityRecordForFiles(capDiv, new String[]{"whatsapp.html", "messages.json", "origin-binding.json"})
                        .toString(2).getBytes(StandardCharsets.UTF_8));

        List<IntegrityVerifier.Capture> divCaps = IntegrityVerifier.discover(capDivRoot);
        for (IntegrityVerifier.Capture c : divCaps) verify.invoke(null, c);
        Object divRes = runAudit.invoke(null, capDivRoot, divCaps, origin, null, null, null);
        if ((Boolean) passedField.get(divRes)) {
            throw new AssertionError("runOriginAudit must fail when n3SqliteAudit reports COM_DIVERGENCIAS");
        }
        String divStatus = (String) statusField.get(divRes);
        String divRpt = (String) reportTextField.get(divRes);
        if (!divStatus.contains("divergenciasN3=") || !divRpt.contains("N3_MENSAGENS_PLUGIN|COM_DIVERGENCIAS")) {
            throw new AssertionError("Expected divergenciasN3 fail reason, got: " + divStatus + "\n" + divRpt);
        }
        String divHtml = (String) htmlMethod.invoke(null, divRes, origin, null, capDivRoot, 1);
        if (!divHtml.contains("banner-bad") || !divHtml.contains("class=\"st-bad\"><code>COM_DIVERGENCIAS")) {
            throw new AssertionError("Origin audit HTML must highlight COM_DIVERGENCIAS as st-bad");
        }

        // 3. Fail-closed when origin-binding lacks cryptographic anchors (SEM_REFERENCIA_CRIPTO) or omits Hashs.txt
        Path capNoAnchorRoot = Files.createDirectories(root.resolve("AuditNoAnchor"));
        Path capNoAnchor = Files.createDirectories(capNoAnchorRoot.resolve("Chat_NoAnchor-20261116-120001"));
        createValidHtmlMessageCapture(capNoAnchor, "Mensagem sem ancora");
        JSONObject noAnchorBinding = new JSONObject()
                .put("originBindingVersion", 1)
                .put("status", "REFERENCIA_CUSTODIA_AUSENTE")
                .put("custodyFiles", new JSONArray())
                .put("sourceDatabase", new JSONObject());
        byte[] noAnchorBytes = noAnchorBinding.toString(2).getBytes(StandardCharsets.UTF_8);
        Files.write(capNoAnchor.resolve("origin-binding.json"), noAnchorBytes);
        JSONObject manNoAnc = new JSONObject(new String(Files.readAllBytes(capNoAnchor.resolve("manifest.json")), StandardCharsets.UTF_8));
        manNoAnc.put("originBindingFile", "origin-binding.json")
                .put("originBindingSha256", sha256Bytes(noAnchorBytes))
                .put("originBinding", new JSONObject().put("originBindingVersion", 1).put("status", "REFERENCIA_CUSTODIA_AUSENTE"));
        Files.write(capNoAnchor.resolve("manifest.json"), manNoAnc.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capNoAnchor.resolve("integridade.json"),
                buildLocalIntegrityRecordForFiles(capNoAnchor, new String[]{"whatsapp.html", "messages.json", "origin-binding.json"})
                        .toString(2).getBytes(StandardCharsets.UTF_8));

        List<IntegrityVerifier.Capture> noAncCaps = IntegrityVerifier.discover(capNoAnchorRoot);
        for (IntegrityVerifier.Capture c : noAncCaps) verify.invoke(null, c);
        Object noAncRes = runAudit.invoke(null, capNoAnchorRoot, noAncCaps, origin, null, null, null);
        if ((Boolean) passedField.get(noAncRes) || !((String) reportTextField.get(noAncRes)).contains("SEM_REFERENCIA_CRIPTO")) {
            throw new AssertionError("Empty custody binding must fail closed as SEM_REFERENCIA_CRIPTO");
        }

        // Origin without Hashs.txt must also fail closed
        Path originNoHashs = Files.createDirectories(root.resolve("OriginWithoutHashsTxt"));
        Files.write(originNoHashs.resolve("Hashs.avilla"), avillaBytes);
        Object noHashsRes = runAudit.invoke(null, capNoAnchorRoot, noAncCaps, originNoHashs, null, null, null);
        if ((Boolean) passedField.get(noHashsRes) || !((String) statusField.get(noHashsRes)).contains("hashsTxtAusente=true")) {
            throw new AssertionError("Origin missing Hashs.txt must fail closed with hashsTxtAusente=true");
        }

        // 4. MD5-only HTML/CSV match (without itemSha256 / SHA256 column) must NOT certify EMBUTIDO_NO_HTML_IPED or MSGSTORE_MATCH
        byte[] inlinePng = new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };
        String inlineSha = sha256Bytes(inlinePng);
        Path md5Parent = Files.createDirectories(root.resolve("ParentMd5Only"));
        Path md5ExpDir = Files.createDirectories(md5Parent.resolve("Exportados"));
        byte[] expHtmlBytes = ("<html><img src=\"data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(inlinePng) + "\"></html>").getBytes(StandardCharsets.UTF_8);
        Files.write(md5ExpDir.resolve("exp.html"), expHtmlBytes);
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        byte[] md5Raw = md5Digest.digest(expHtmlBytes);
        StringBuilder md5Hex = new StringBuilder();
        for (byte b : md5Raw) md5Hex.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        String expHtmlMd5 = md5Hex.toString();
        String md5Csv = "\"Nome\";\"MD5\";\"Caminho\"\n\"exp.html\";\"" + expHtmlMd5 + "\";\"Exportados/exp.html\"\n";
        Files.write(md5Parent.resolve("Lista de Arquivos.csv"), md5Csv.getBytes(StandardCharsets.UTF_8));

        Path capMd5Root = Files.createDirectories(root.resolve("AuditMd5Only"));
        Path capMd5 = Files.createDirectories(capMd5Root.resolve("Chat_Md5Only-20261116-120002"));
        Files.createDirectories(capMd5.resolve("attachments"));
        String relAtt = "attachments/" + inlineSha + ".png";
        Files.write(capMd5.resolve(relAtt), inlinePng);
        createValidHtmlMessageCapture(capMd5, "Com anexo md5");
        String origHtml = new String(Files.readAllBytes(capMd5.resolve("whatsapp.html")), StandardCharsets.UTF_8);
        String modalHtml = origHtml.replace("</body>",
                "<section class=\"capture-modal\" id=\"preview-" + inlineSha + "\"><img src=\"" + relAtt
                        + "\"><a href=\"" + relAtt + "\" download=\"att.png\">Baixar</a>"
                        + "<a href=\"#\" class=\"capture-close\">Fechar</a></section></body>");
        Files.write(capMd5.resolve("whatsapp.html"), modalHtml.getBytes(StandardCharsets.UTF_8));
        JSONObject md5MsgsWrapper = new JSONObject(new String(Files.readAllBytes(capMd5.resolve("messages.json")), StandardCharsets.UTF_8));
        JSONArray md5Msgs = md5MsgsWrapper.getJSONArray("messages");
        JSONObject md5Msg = md5Msgs.getJSONObject(0);
        md5Msg.getJSONArray("attachments").put(new JSONObject()
                .put("path", relAtt).put("mime", "image/png").put("size", inlinePng.length).put("sha256", inlineSha));
        Method msgHashM = IntegrityVerifier.class.getDeclaredMethod("htmlMessageHash", JSONObject.class);
        msgHashM.setAccessible(true);
        String newMsgHash = (String) msgHashM.invoke(null, md5Msg);
        md5Msg.put("messageHash", newMsgHash);
        Files.write(capMd5.resolve("messages.json"), md5MsgsWrapper.toString(2).getBytes(StandardCharsets.UTF_8));

        JSONObject manMd5 = new JSONObject(new String(Files.readAllBytes(capMd5.resolve("manifest.json")), StandardCharsets.UTF_8));
        manMd5.getJSONObject("sourceMetadata").put("itemHash", expHtmlMd5);
        manMd5.getJSONArray("messageHashes").getJSONObject(0).put("messageHash", newMsgHash);
        String modalSha = sha256Utf8(modalHtml);
        manMd5.put("htmlSha256", modalSha);
        Method rootHashM = IntegrityVerifier.class.getDeclaredMethod(
                "htmlCaptureRootHash", JSONArray.class, String.class, String.class, String.class, JSONObject.class);
        rootHashM.setAccessible(true);
        manMd5.put("captureRootHash", (String) rootHashM.invoke(null, md5Msgs, modalSha, "", "", manMd5));
        Files.write(capMd5.resolve("manifest.json"), manMd5.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capMd5.resolve("integridade.json"),
                buildLocalIntegrityRecordForFiles(capMd5, new String[]{"whatsapp.html", "messages.json", relAtt})
                        .toString(2).getBytes(StandardCharsets.UTF_8));

        List<IntegrityVerifier.Capture> md5Caps = IntegrityVerifier.discover(capMd5Root);
        for (IntegrityVerifier.Capture c : md5Caps) verify.invoke(null, c);
        Object md5Res = runAudit.invoke(null, capMd5Root, md5Caps, origin, md5Parent, null, null);
        String md5Rpt = (String) reportTextField.get(md5Res);
        if ((Boolean) passedField.get(md5Res) || !md5Rpt.contains("NAO_LOCALIZADO=1") || md5Rpt.contains("EMBUTIDO_NO_HTML_IPED=1")) {
            throw new AssertionError("MD5-only CSV/HTML must NOT certify EMBUTIDO_NO_HTML_IPED:\n" + md5Rpt);
        }
    }

    private static void driverCarregaDoClasspath(Path root) throws Exception {
        Class<?> jdbcCls = Class.forName("org.sqlite.JDBC");
        if (jdbcCls == null) {
            throw new AssertionError("org.sqlite.JDBC must resolve directly from verifier classpath");
        }
        Method isAvail = IntegrityVerifier.class.getDeclaredMethod("isSqliteDriverAvailable");
        isAvail.setAccessible(true);
        if (!(Boolean) isAvail.invoke(null)) {
            throw new AssertionError("isSqliteDriverAvailable() must return true from embedded classpath driver");
        }
        Path verifierJar = java.nio.file.Paths.get("verificador-integridade/integridade-verifier.jar");
        if (!Files.isRegularFile(verifierJar)) {
            verifierJar = java.nio.file.Paths.get("integridade-verifier.jar");
        }
        if (Files.isRegularFile(verifierJar)) {
            try (java.util.jar.JarFile jf = new java.util.jar.JarFile(verifierJar.toFile())) {
                if (jf.getJarEntry("org/sqlite/JDBC.class") == null
                        || jf.getJarEntry("sqlite-jdbc.properties") == null
                        || jf.getJarEntry("META-INF/services/java.sql.Driver") == null) {
                    throw new AssertionError("integridade-verifier.jar must embed org/sqlite/JDBC.class, sqlite-jdbc.properties and META-INF/services/java.sql.Driver");
                }
            }
        }
        Path dbFile = root.resolve("classpath-n3-msgstore.db");
        Class<?> dmCls = Class.forName("java.sql.DriverManager");
        Method getConn = dmCls.getMethod("getConnection", String.class);
        AutoCloseable con = (AutoCloseable) getConn.invoke(null, "jdbc:sqlite:" + dbFile.toAbsolutePath());
        try {
            Method createSt = con.getClass().getMethod("createStatement");
            AutoCloseable st = (AutoCloseable) createSt.invoke(con);
            try {
                Method exec = st.getClass().getMethod("execute", String.class);
                exec.invoke(st, "CREATE TABLE message (_id INTEGER PRIMARY KEY, text_data TEXT, message_type INTEGER)");
                exec.invoke(st, "INSERT INTO message (_id, text_data, message_type) VALUES (101, 'Mensagem N3 do classpath', 0)");
            } finally {
                st.close();
            }
        } finally {
            con.close();
        }
        Path msgFile = root.resolve("classpath-n3-messages.json");
        JSONObject msgWrapper = new JSONObject().put("messages", new JSONArray()
                .put(new JSONObject().put("domId", "101").put("text", "Mensagem N3 do classpath")));
        Files.write(msgFile, msgWrapper.toString().getBytes(StandardCharsets.UTF_8));
        Method runIndN3 = IntegrityVerifier.class.getDeclaredMethod(
                "runIndependentN3VerificationIfPossible", Path.class, Path.class, boolean.class);
        runIndN3.setAccessible(true);
        Object outcome = runIndN3.invoke(null, dbFile, msgFile, Boolean.TRUE);
        Method toLine = outcome.getClass().getDeclaredMethod("toReportLine");
        toLine.setAccessible(true);
        String reportLine = (String) toLine.invoke(outcome);
        if (!"CONFERE|ATIVAS=1|RECUPERADAS=0|DIVERGENTES=0".equals(reportLine)) {
            throw new AssertionError("Expected embedded classpath N3 CONFERE, got: " + reportLine);
        }
    }

    private static void nenhumaBuscaExternaDeDriver() throws Exception {
        for (Method m : IntegrityVerifier.class.getDeclaredMethods()) {
            String name = m.getName();
            if ("resolveDynamicSqliteDriver".equals(name) || "openSqliteConnection".equals(name)) {
                throw new AssertionError("External driver loader method must not exist in IntegrityVerifier: " + name);
            }
            if ("isSqliteDriverAvailable".equals(name) && m.getParameterTypes().length != 0) {
                throw new AssertionError("isSqliteDriverAvailable must take 0 parameters (no searchRoots)");
            }
            if ("runIndependentN3VerificationIfPossible".equals(name) && m.getParameterTypes().length != 3) {
                throw new AssertionError("runIndependentN3VerificationIfPossible must take exactly 3 parameters (no searchRoots)");
            }
        }
        for (Field f : IntegrityVerifier.class.getDeclaredFields()) {
            if ("cachedSqliteDriverInstance".equals(f.getName())) {
                throw new AssertionError("cachedSqliteDriverInstance field must not exist in IntegrityVerifier");
            }
        }
        Path srcFile = java.nio.file.Paths.get("verificador-integridade/src/IntegrityVerifier.java");
        if (!Files.isRegularFile(srcFile)) {
            srcFile = java.nio.file.Paths.get("src/IntegrityVerifier.java");
        }
        if (Files.isRegularFile(srcFile)) {
            String src = new String(Files.readAllBytes(srcFile), StandardCharsets.UTF_8);
            if (src.contains("URLClassLoader")
                    || src.contains("resolveDynamicSqliteDriver")
                    || src.contains("candidateDirs")
                    || src.contains("sqlite-jdbc*.jar")) {
                throw new AssertionError("IntegrityVerifier.java must not contain URLClassLoader or external sqlite-jdbc search paths");
            }
        }
    }

    private static void jarMaliciosoNaCapturaIgnorado(Path root) throws Exception {
        Path origin = Files.createDirectories(root.resolve("EvilJarAudit/Origin"));
        Path capRoot = Files.createDirectories(root.resolve("EvilJarAudit/Captures"));
        Path capDir = Files.createDirectories(capRoot.resolve("Chat_EvilJar-20261118-120000"));
        createValidHtmlMessageCapture(capDir, "Mensagem limpa");

        Path tarDbDir = Files.createDirectories(origin.resolve("sdcard/Android/com.whatsapp.tar/data/user/0/com.whatsapp/databases"));
        Path dbFile = tarDbDir.resolve("msgstore.db");
        Class<?> dmCls = Class.forName("java.sql.DriverManager");
        Method getConn = dmCls.getMethod("getConnection", String.class);
        AutoCloseable con = (AutoCloseable) getConn.invoke(null, "jdbc:sqlite:" + dbFile.toAbsolutePath());
        try {
            Method createSt = con.getClass().getMethod("createStatement");
            AutoCloseable st = (AutoCloseable) createSt.invoke(con);
            try {
                Method exec = st.getClass().getMethod("execute", String.class);
                exec.invoke(st, "CREATE TABLE message (_id INTEGER PRIMARY KEY, text_data TEXT, message_type INTEGER)");
                exec.invoke(st, "INSERT INTO message (_id, text_data, message_type) VALUES (101, 'Mensagem limpa', 0)");
            } finally {
                st.close();
            }
        } finally {
            con.close();
        }
        String dbSha = sha256Bytes(Files.readAllBytes(dbFile));
        String hashsTxtContent = "Algorithm : SHA256\r\nHash      : " + dbSha.toUpperCase()
                + "\r\nPath      : C:\\Case\\msgstore.db\r\n\r\n";
        byte[] utf16 = hashsTxtContent.getBytes(StandardCharsets.UTF_16LE);
        byte[] hashsBytes = new byte[utf16.length + 2];
        hashsBytes[0] = (byte) 0xFF;
        hashsBytes[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, hashsBytes, 2, utf16.length);
        Files.write(origin.resolve("Hashs.txt"), hashsBytes);
        String hashsSha = sha256Bytes(hashsBytes);
        byte[] avillaBytes = "avilla-hmac".getBytes(StandardCharsets.UTF_8);
        Files.write(origin.resolve("Hashs.avilla"), avillaBytes);
        String avillaSha = sha256Bytes(avillaBytes);
        byte[] pdfBytes = "%PDF-1.4 stub".getBytes(StandardCharsets.US_ASCII);
        Files.write(origin.resolve("report.pdf"), pdfBytes);
        String pdfSha = sha256Bytes(pdfBytes);

        JSONObject binding = new JSONObject()
                .put("originBindingVersion", 1)
                .put("status", "CUSTODIA_VERIFICADA")
                .put("custodyFiles", new JSONArray()
                        .put(new JSONObject().put("name", "Hashs.txt").put("sha256", hashsSha))
                        .put(new JSONObject().put("name", "Hashs.avilla").put("sha256", avillaSha))
                        .put(new JSONObject().put("name", "report.pdf").put("sha256", pdfSha)))
                .put("sourceDatabase", new JSONObject().put("present", true).put("sha256", dbSha))
                .put("n3SqliteAudit", new JSONObject()
                        .put("status", "CONFERE")
                        .put("matchedActive", 1)
                        .put("recoveredOrNonTable", 0)
                        .put("diverged", 0)
                        .put("prePostCopyOriginSha256Match", true));
        byte[] bindBytes = binding.toString(2).getBytes(StandardCharsets.UTF_8);
        Files.write(capDir.resolve("origin-binding.json"), bindBytes);
        JSONObject man = new JSONObject(new String(Files.readAllBytes(capDir.resolve("manifest.json")), StandardCharsets.UTF_8));
        man.put("originBindingFile", "origin-binding.json")
           .put("originBindingSha256", sha256Bytes(bindBytes))
           .put("originBinding", new JSONObject().put("originBindingVersion", 1).put("status", "CUSTODIA_VERIFICADA"));
        Files.write(capDir.resolve("manifest.json"), man.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("integridade.json"),
                buildLocalIntegrityRecordForFiles(capDir, new String[]{"whatsapp.html", "messages.json", "origin-binding.json"})
                        .toString(2).getBytes(StandardCharsets.UTF_8));

        // Plant corrupt/malicious sqlite-jdbc-evil.jar in untrusted evidence directories
        byte[] evilBytes = "corrupted-or-malicious-jar-payload".getBytes(StandardCharsets.UTF_8);
        Files.write(capRoot.resolve("sqlite-jdbc-evil.jar"), evilBytes);
        Files.createDirectories(capRoot.resolve("lib"));
        Files.write(capRoot.resolve("lib").resolve("sqlite-jdbc-evil.jar"), evilBytes);
        Files.write(origin.resolve("sqlite-jdbc-evil.jar"), evilBytes);
        Files.createDirectories(origin.resolve("lib"));
        Files.write(origin.resolve("lib").resolve("sqlite-jdbc-evil.jar"), evilBytes);

        Method verify = IntegrityVerifier.class.getDeclaredMethod("verify", IntegrityVerifier.Capture.class);
        verify.setAccessible(true);
        Method runAudit = IntegrityVerifier.class.getDeclaredMethod("runOriginAudit",
                Path.class, List.class, Path.class, Path.class, Path.class, java.util.function.Consumer.class);
        runAudit.setAccessible(true);
        Class<?> oaResCls = Class.forName("IntegrityVerifier$OriginAuditResult");
        Field passedField = oaResCls.getDeclaredField("passed");
        passedField.setAccessible(true);
        Field reportTextField = oaResCls.getDeclaredField("reportText");
        reportTextField.setAccessible(true);

        List<IntegrityVerifier.Capture> caps = IntegrityVerifier.discover(capRoot);
        for (IntegrityVerifier.Capture c : caps) verify.invoke(null, c);
        Object res = runAudit.invoke(null, capRoot, caps, origin, null, null, null);
        String rpt = (String) reportTextField.get(res);
        if (!(Boolean) passedField.get(res) || !rpt.contains("N3_MENSAGENS_VERIFICADOR|CONFERE|ATIVAS=1|RECUPERADAS=0|DIVERGENTES=0")) {
            throw new AssertionError("Audit must ignore sqlite-jdbc-evil.jar in evidence folders and succeed via embedded classpath driver:\n" + rpt);
        }
    }

    private static void buildPinSqliteJdbcHash() throws Exception {
        Path buildScript = java.nio.file.Paths.get("build-reproducible.ps1");
        if (!Files.isRegularFile(buildScript)) {
            buildScript = java.nio.file.Paths.get("../build-reproducible.ps1");
        }
        if (!Files.isRegularFile(buildScript)) {
            throw new AssertionError("build-reproducible.ps1 not found");
        }
        String content = new String(Files.readAllBytes(buildScript), StandardCharsets.UTF_8);
        String expectedPinnedSha = "0CDAB410947E04B6743DF99CF1543267DDD107357D6F76948D145BE590FD497D";
        if (!content.contains(expectedPinnedSha)
                || !content.contains("SqliteJdbcOfficialSha256")
                || !content.contains("sqlite-jdbc-3.41.2.2.jar")) {
            throw new AssertionError("build-reproducible.ps1 must pin and verify SHA-256 of official sqlite-jdbc-3.41.2.2.jar");
        }
    }

    private static void laudoFolderIgnoredAndOriginReportPdfCopyValidated(Path root) throws Exception {
        Path caseDir = Files.createDirectories(root.resolve("laudo-ignore-case"));
        Path laudoDir = Files.createDirectories(caseDir.resolve("laudo"));
        Files.write(laudoDir.resolve("manifest.json"), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(laudoDir.resolve("texto.txt"), "doc".getBytes(StandardCharsets.UTF_8));
        List<IntegrityVerifier.Capture> discovered = IntegrityVerifier.discover(caseDir);
        if (!discovered.isEmpty()) {
            throw new AssertionError("discover() must ignore laudo/ folder even with manifest.json/texto.txt, got: " + discovered.size());
        }
        if (!IntegrityVerifier.isIgnoredCaptureDirectoryName("laudo")
                || !IntegrityVerifier.isIgnoredCaptureDirectoryName("LAUDO")) {
            throw new AssertionError("isIgnoredCaptureDirectoryName must ignore laudo case-insensitively");
        }
        laudoChainedLogAuditWithoutPdfBox(root);
        captureReportPackageFailClosed(root);
    }

    private static void laudoChainedLogAuditWithoutPdfBox(Path root) throws Exception {
        Path caseDir = Files.createDirectories(root.resolve("laudo-log-audit-case"));
        Path laudoDir = Files.createDirectories(caseDir.resolve("laudo"));
        String zero = repeat('0', 64);
        String p0 = "0|2026-11-23T12:00:00-03:00|2026-11-23T15:00:00Z|CRIADO|-|OK|Log.txt criado|prev=" + zero;
        String h0 = sha256Utf8(p0);
        String l0 = p0 + "|hash=" + h0;
        String p1 = "1|2026-11-23T12:01:00-03:00|2026-11-23T15:01:00Z|START|cap1|OK|msg=1|prev=" + h0;
        String h1 = sha256Utf8(p1);
        String l1 = p1 + "|hash=" + h1;
        String prefix01 = sha256Utf8(l0 + "\n" + l1 + "\n");

        Path logTxt = laudoDir.resolve("Log.txt");
        Path pdf = laudoDir.resolve("report-plugin.pdf");
        Files.write(logTxt, (l0 + "\n" + l1 + "\n").getBytes(StandardCharsets.UTF_8));
        String fakePdf = "%PDF-1.4\n/ChatCapturaLogAnchor (CHATCAPTURA_LOGANCHOR_V1;created=2026-11-23T15:00:00Z;lines=0..1;prefix="
                + prefix01 + ";head=" + h1 + ";status=CONFERE)\n%%EOF\n";
        Files.write(pdf, fakePdf.getBytes(StandardCharsets.ISO_8859_1));

        IntegrityVerifier.LaudoLogAuditResult okRes = IntegrityVerifier.auditLaudoChainedLog(caseDir);
        if (!okRes.applicable || !okRes.passed || !"CONFERE".equals(okRes.status)) {
            throw new AssertionError("Expected CONFERE, got " + okRes.status + " (" + okRes.detail + ")");
        }

        // Duplicate conflicting anchors in PDF must fail closed
        String dupPdf = fakePdf + "/ChatCapturaLogAnchor (CHATCAPTURA_LOGANCHOR_V1;created=2026-11-23T15:00:00Z;lines=0..0;prefix="
                + repeat('a', 64) + ";head=" + repeat('b', 64) + ";status=CONFERE)\n";
        Files.write(pdf, dupPdf.getBytes(StandardCharsets.ISO_8859_1));
        IntegrityVerifier.LaudoLogAuditResult dupRes = IntegrityVerifier.auditLaudoChainedLog(caseDir);
        if (dupRes.passed || !"FALHA_ANCORA_PDF_DUPLICADA".equals(dupRes.status)) {
            throw new AssertionError("Expected FALHA_ANCORA_PDF_DUPLICADA, got " + dupRes.status);
        }

        // V2 single-SHA-256 session Log.txt audit (without PDFBox)
        Path caseV2 = Files.createDirectories(root.resolve("laudo-log-v2-audit-case"));
        Path laudoV2 = Files.createDirectories(caseV2.resolve("laudo"));
        String v2Log = "=== LOG CRONOLOGICO - CHAT CAPTURA IPED ===\n"
                + "CRIADO_EM: 2026-11-23T14:00:00-03:00 (UTC: 2026-11-23T17:00:00Z)\n"
                + "PLUGIN: 2.8.14-beta\n\n"
                + "--- [SESSAO #1] 2026-11-23T14:01:00-03:00 | CAPTURA ADICIONADA (START) ---\n"
                + "  Pasta: cap1\n  Status: CONCLUIDO_OK\n  Detalhes: msg=1 OK\n  SHA-256 pasta: " + repeat('a', 64) + "\n";
        byte[] v2Bytes = v2Log.getBytes(StandardCharsets.UTF_8);
        String v2Sha = sha256BytesHex(v2Bytes);
        Files.write(laudoV2.resolve("Log.txt"), v2Bytes);
        String v2Pdf = "%PDF-1.4\n/ChatCapturaLogAnchor (CHATCAPTURA_LOGANCHOR_V2;created=2026-11-23T17:00:00Z;bytes="
                + v2Bytes.length + ";sessions=1;sha256=" + v2Sha + ";status=CONFERE)\n%%EOF\n";
        Files.write(laudoV2.resolve("report-plugin.pdf"), v2Pdf.getBytes(StandardCharsets.ISO_8859_1));
        IntegrityVerifier.LaudoLogAuditResult v2Ok = IntegrityVerifier.auditLaudoChainedLog(caseV2);
        if (!v2Ok.applicable || !v2Ok.passed || !"CONFERE".equals(v2Ok.status)) {
            throw new AssertionError("Expected V2 CONFERE, got " + v2Ok.status + " (" + v2Ok.detail + ")");
        }
        // 1-byte tamper in V2 Log.txt must fail closed
        Files.write(laudoV2.resolve("Log.txt"), v2Log.replace("msg=1 OK", "msg=9 OK").getBytes(StandardCharsets.UTF_8));
        IntegrityVerifier.LaudoLogAuditResult v2Bad = IntegrityVerifier.auditLaudoChainedLog(caseV2);
        if (v2Bad.passed || !"FALHA_LOG_ADULTERADO".equals(v2Bad.status)) {
            throw new AssertionError("Expected V2 FALHA_LOG_ADULTERADO, got " + v2Bad.status);
        }
    }

    private static void captureReportPackageFailClosed(Path root) throws Exception {
        if (!IntegrityVerifier.isAllowedCaptureReportPackagePath("Lista de Arquivos.csv")
                || !IntegrityVerifier.isAllowedCaptureReportPackagePath("chat-captura-IPED.log")
                || !IntegrityVerifier.isAllowedCaptureReportPackagePath("relatorio.htm")
                || !IntegrityVerifier.isAllowedCaptureReportPackagePath("relatorio/caseinformation.htm")
                || !IntegrityVerifier.isAllowedCaptureReportPackagePath("relatorio/res/estilo.css")) {
            throw new AssertionError("Valid report package paths must be allowed");
        }
        if (IntegrityVerifier.isAllowedCaptureReportPackagePath("relatorio/a/b/c.html")
                || IntegrityVerifier.isAllowedCaptureReportPackagePath("relatorio/evil.js")
                || IntegrityVerifier.isAllowedCaptureReportPackagePath("../relatorio.htm")
                || IntegrityVerifier.isAllowedCaptureReportPackagePath("extra.html")) {
            throw new AssertionError("Invalid or deep report package paths must be rejected");
        }

        Path capDir = Files.createDirectories(root.resolve("phase3-report-pkg-verify"));
        Path relDir = Files.createDirectories(capDir.resolve("relatorio"));
        Path resDir = Files.createDirectories(relDir.resolve("res"));
        String cleanHtml = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'self'\">"
                + "<link rel=\"stylesheet\" href=\"res/estilo.css\"><title>Info</title></head>"
                + "<body><a href=\"../Lista%20de%20Arquivos.csv\">CSV</a></body></html>";
        IntegrityVerifier.validateAuxiliaryReportHtml(capDir, "relatorio/caseinformation.htm", cleanHtml);
        IntegrityVerifier.validateAuxiliaryReportCss("relatorio/res/estilo.css", "body{color:#111;margin:0;}");

        // Active content, external URLs, path traversal, and CSS url() must fail closed
        String[] badHtmls = new String[] {
            "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>",
            "<!DOCTYPE html><html><body><a href=\"https://evil.example/x\">x</a></body></html>",
            "<!DOCTYPE html><html><body><img src=\"../attachments/a.png\" onerror=\"alert(1)\"></body></html>",
            "<!DOCTYPE html><html><body><a href=\"../../fora.txt\">escape</a></body></html>",
            "<!DOCTYPE html><html><body><!-- comentario -->ok</body></html>"
        };
        for (String bad : badHtmls) {
            boolean rejected = false;
            try {
                IntegrityVerifier.validateAuxiliaryReportHtml(capDir, "relatorio/caseinformation.htm", bad);
            } catch (IOException expected) {
                rejected = true;
            }
            if (!rejected) {
                throw new AssertionError("validateAuxiliaryReportHtml must reject: " + bad);
            }
        }
        boolean cssRejected = false;
        try {
            IntegrityVerifier.validateAuxiliaryReportCss("relatorio/res/estilo.css", "body{background:url(https://evil.test/x.png);}");
        } catch (IOException expected) {
            cssRejected = true;
        }
        if (!cssRejected) {
            throw new AssertionError("validateAuxiliaryReportCss must reject url(...)");
        }

        // Full verify() E2E on a sealed html-message-v1 capture with report package
        String convKey = repeat('1', 64);
        String srcFp = repeat('2', 64);
        String domId = "101";
        String natural = "2026-11-23 12:00:00\nAlice\nincoming\ntext";
        String msgKey = sha256Utf8(convKey + "\n" + domId + "\n" + natural + "\n0");
        JSONObject msg = new JSONObject()
                .put("domId", domId).put("order", 0).put("timestamp", "2026-11-23 12:00:00")
                .put("sender", "Alice").put("direction", "incoming").put("type", "text")
                .put("duplicateOrdinal", 0).put("text", "Ola").put("classes", "incoming")
                .put("attachments", new JSONArray()).put("key", msgKey);
        Method msgHashM = IntegrityVerifier.class.getDeclaredMethod("htmlMessageHash", JSONObject.class);
        msgHashM.setAccessible(true);
        String mHash = (String) msgHashM.invoke(null, msg);
        msg.put("messageHash", mHash);
        JSONArray msgs = new JSONArray().put(msg);

        String whatsappHtml = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'\">"
                + "<title>Chat</title></head><body><div class=\"chat\">"
                + "<div class=\"message incoming\" data-capture-message-key=\"" + msgKey + "\">Ola</div>"
                + "</div></body></html>";
        byte[] htmlBytes = whatsappHtml.getBytes(StandardCharsets.UTF_8);
        String htmlSha = sha256Utf8(whatsappHtml);
        Files.write(capDir.resolve("whatsapp.html"), htmlBytes);
        byte[] msgsBytes = new JSONObject().put("messages", msgs).toString(2).getBytes(StandardCharsets.UTF_8);
        Files.write(capDir.resolve("messages.json"), msgsBytes);

        JSONObject srcMeta = new JSONObject().put("name", "chat.html").put("size", 100).put("type", "text/html").put("itemHash", "abc").put("deleted", false);
        JSONObject manifest = new JSONObject()
                .put("schemaVersion", 1).put("captureFormat", "html-message-v1").put("presentationVersion", 0)
                .put("pluginVersion", "2.8.13-beta").put("ipedVersion", "4.3.1")
                .put("status", "completed").put("state", "complete")
                .put("createdAt", "2026-11-23T12:00:00Z")
                .put("sourceFingerprint", srcFp).put("conversationKey", convKey)
                .put("sourceMetadata", srcMeta)
                .put("anchors", new JSONObject().put("start", domId).put("end", domId))
                .put("messageCount", 1).put("messagesFile", "messages.json")
                .put("messageHashes", new JSONArray().put(new JSONObject().put("domId", domId).put("key", msgKey).put("messageHash", mHash)))
                .put("htmlSha256", htmlSha).put("annotationsSha256", "").put("reconciliationSha256", "");
        Method rootHashM = IntegrityVerifier.class.getDeclaredMethod("htmlCaptureRootHash",
                JSONArray.class, String.class, String.class, String.class, JSONObject.class);
        rootHashM.setAccessible(true);
        manifest.put("captureRootHash", (String) rootHashM.invoke(null, msgs, htmlSha, "", "", manifest));
        Files.write(capDir.resolve("manifest.json"), manifest.toString(2).getBytes(StandardCharsets.UTF_8));

        // Write report package files
        Files.write(capDir.resolve("Lista de Arquivos.csv"), "\"Nome\";\"SHA256\"\n\"whatsapp.html\";\"0\"\n".getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("chat-captura-IPED.log"), "CHAT_CAPTURA_IPED_LOG_V1\nmessageCount=1\n".getBytes(StandardCharsets.UTF_8));
        Files.write(capDir.resolve("relatorio.htm"), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>R</title></head><frameset cols=\"230,*\"><frame src=\"relatorio/caseinformation.htm\" name=\"ReportPage\"></frameset></html>".getBytes(StandardCharsets.UTF_8));
        Files.write(relDir.resolve("caseinformation.htm"), cleanHtml.getBytes(StandardCharsets.UTF_8));
        Files.write(resDir.resolve("estilo.css"), "body{color:#111;margin:0;}".getBytes(StandardCharsets.UTF_8));

        writeSealedIntegrityJson(capDir);

        Method verifyM = IntegrityVerifier.class.getDeclaredMethod("verify", Class.forName("IntegrityVerifier$Capture"));
        verifyM.setAccessible(true);
        Field statusF = Class.forName("IntegrityVerifier$Capture").getDeclaredField("historyStatus");
        statusF.setAccessible(true);

        Object cap1 = newCapture(capDir);
        verifyM.invoke(null, cap1);
        String st1 = (String) statusF.get(cap1);
        if (!"consistente com registro local; consistente com registro local (manifesto de mensagens)".equals(st1)) {
            throw new AssertionError("Sealed capture with report package must pass cleanly, got: " + st1);
        }

        // 1. Uninventoried extra file in relatorio/ must fail closed
        Path extraFile = relDir.resolve("extra.html");
        Files.write(extraFile, "<!DOCTYPE html><html><body>extra</body></html>".getBytes(StandardCharsets.UTF_8));
        Object cap2 = newCapture(capDir);
        verifyM.invoke(null, cap2);
        String st2 = (String) statusF.get(cap2);
        if (!st2.contains("arquivos extras na captura: 1")) {
            throw new AssertionError("Uninventoried relatorio/extra.html must be flagged as extra, got: " + st2);
        }
        Files.delete(extraFile);

        // 2. 1-byte tamper of chat-captura-IPED.log must fail
        Files.write(capDir.resolve("chat-captura-IPED.log"), "CHAT_CAPTURA_IPED_LOG_V1\nmessageCount=2\n".getBytes(StandardCharsets.UTF_8));
        Object cap3 = newCapture(capDir);
        verifyM.invoke(null, cap3);
        String st3 = (String) statusF.get(cap3);
        if (!st3.contains("arquivo divergente: chat-captura-IPED.log")) {
            throw new AssertionError("Tampered chat-captura-IPED.log must fail verification, got: " + st3);
        }
        Files.write(capDir.resolve("chat-captura-IPED.log"), "CHAT_CAPTURA_IPED_LOG_V1\nmessageCount=1\n".getBytes(StandardCharsets.UTF_8));

        // 3. Injecting <script> into relatorio/caseinformation.htm EVEN WITH recomputed integridade.json must throw IOException!
        Files.write(relDir.resolve("caseinformation.htm"), "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8));
        writeSealedIntegrityJson(capDir);
        boolean scriptBlocked = false;
        try {
            verifyM.invoke(null, newCapture(capDir));
        } catch (java.lang.reflect.InvocationTargetException ite) {
            if (ite.getCause() instanceof IOException) {
                scriptBlocked = true;
            }
        }
        if (!scriptBlocked) {
            throw new AssertionError("Injected <script> in relatorio/caseinformation.htm must fail closed even with recomputed integridade.json");
        }
    }

    private static void writeSealedIntegrityJson(Path capDir) throws Exception {
        List<Path> all = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(capDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                if (!"manifest.json".equals(name) && !"integridade.json".equals(name) && !"reextracao-auditoria.jsonl".equals(name)) {
                    all.add(p);
                }
            });
        }
        all.sort(java.util.Comparator.comparing(p -> capDir.relativize(p).toString().replace('\\', '/')));
        JSONArray arts = new JSONArray();
        StringBuilder inv = new StringBuilder();
        for (Path f : all) {
            String rel = capDir.relativize(f).toString().replace('\\', '/');
            byte[] b = Files.readAllBytes(f);
            String sha = sha256BytesHex(b);
            arts.put(new JSONObject().put("path", rel).put("size", b.length).put("sha256", sha));
            inv.append(rel).append('|').append(b.length).append('|').append(sha).append('\n');
        }
        String contentRoot = sha256Utf8(inv.toString());
        JSONObject ev = new JSONObject()
                .put("sequence", 1)
                .put("action", "capture_created")
                .put("timestamp", "2026-11-23T12:00:00Z")
                .put("pluginVersion", "2.8.13-beta")
                .put("previousRecordHash", "")
                .put("contentRootHash", contentRoot)
                .put("recordHashFormat", "canonical-v1");
        Method canonM = IntegrityVerifier.class.getDeclaredMethod("canonicalJson", Object.class);
        canonM.setAccessible(true);
        String evHash = sha256Utf8((String) canonM.invoke(null, ev));
        ev.put("recordHash", evHash);
        JSONObject integrity = new JSONObject()
                .put("schemaVersion", 2)
                .put("contentRootHash", contentRoot)
                .put("artifacts", arts)
                .put("events", new JSONArray().put(ev));
        Files.write(capDir.resolve("integridade.json"), integrity.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static void wrappedPlainLabelWrapsAndDisablesHtml() throws Exception {
        final String sample = "Log Cronológico (laudo/Log.txt): LOG_INTEGRO_OK — SHA-256 de laudo/Log.txt conferido com report-plugin.pdf (38 sessões registradas sem divergências).";
        final java.awt.Color okGreen = new java.awt.Color(0, 110, 50);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                javax.swing.JTextArea area = IntegrityVerifier.wrappedPlainLabel(sample, 46, okGreen);
                if (!Boolean.TRUE.equals(area.getClientProperty("html.disable"))) {
                    throw new AssertionError("wrappedPlainLabel must set html.disable=true");
                }
                if (area.isEditable() || !area.getLineWrap() || !area.getWrapStyleWord() || area.isOpaque()) {
                    throw new AssertionError("wrappedPlainLabel must be non-editable, transparent, and word-wrapped");
                }
                if (!okGreen.equals(area.getForeground())) {
                    throw new AssertionError("wrappedPlainLabel must preserve foreground color");
                }
                if (area.getMaximumSize().width != Integer.MAX_VALUE) {
                    throw new AssertionError("wrappedPlainLabel max width must expand horizontally");
                }
                area.setSize(600, Short.MAX_VALUE);
                int wideHeight = area.getPreferredSize().height;
                area.setSize(220, Short.MAX_VALUE);
                int narrowHeight = area.getPreferredSize().height;
                if (narrowHeight <= wideHeight) {
                    throw new AssertionError("wrappedPlainLabel must grow vertically when narrowed (wide=" + wideHeight + ", narrow=" + narrowHeight + ")");
                }
            }
        });
    }

    private static void aboutDialogContainsCreatorRepoUrlAndSince2026() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                javax.swing.JButton btn = IntegrityVerifier.createAboutButton(null);
                if (!"?".equals(btn.getText()) || !Boolean.TRUE.equals(btn.getClientProperty("html.disable"))) {
                    throw new AssertionError("About button must display '?' with html.disable=true");
                }
                javax.swing.JPanel panel = IntegrityVerifier.buildAboutDialogPanel(null);
                StringBuilder allText = new StringBuilder();
                collectSwingText(panel, allText);
                String text = allText.toString();
                if (!text.contains("iagobusca")) {
                    throw new AssertionError("About dialog must mention creator 'iagobusca': " + text);
                }
                if (!text.contains("https://github.com/iagobusca/chat-captura-IPED")) {
                    throw new AssertionError("About dialog must contain official GitHub repo URL: " + text);
                }
                if (!text.contains("2026")) {
                    throw new AssertionError("About dialog must mention created since 2026: " + text);
                }
            }
        });
    }

    private static void collectSwingText(java.awt.Component comp, StringBuilder out) {
        if (comp instanceof javax.swing.JLabel) {
            if (!Boolean.TRUE.equals(((javax.swing.JLabel) comp).getClientProperty("html.disable"))) {
                throw new AssertionError("About JLabel missing html.disable=true");
            }
            out.append(((javax.swing.JLabel) comp).getText()).append('\n');
        } else if (comp instanceof javax.swing.text.JTextComponent) {
            if (!Boolean.TRUE.equals(((javax.swing.text.JTextComponent) comp).getClientProperty("html.disable"))) {
                throw new AssertionError("About JTextComponent missing html.disable=true");
            }
            out.append(((javax.swing.text.JTextComponent) comp).getText()).append('\n');
        } else if (comp instanceof javax.swing.JButton) {
            out.append(((javax.swing.JButton) comp).getText()).append('\n');
        }
        if (comp instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) comp).getComponents()) {
                collectSwingText(child, out);
            }
        }
    }

    private static String sha256BytesHex(byte[] bytes) throws Exception {
        byte[] dig = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
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
