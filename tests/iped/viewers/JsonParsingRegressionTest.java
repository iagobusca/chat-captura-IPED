package iped.viewers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonParsingRegressionTest {

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("json-parsing-regression-");
        try {
            testChatAnnotationStoreVersionBeforeChatsAndNestedKeys(tempRoot);
            testChatAnnotationStoreScalarsNullAndMalformed(tempRoot);
            testLoadRecaptureJobsLegacyAnchorOrderingAndOrgJson();
            testCaptureRootMetadataNestedFakeKeysAndBraces();
            testCoordinateAnchorsAndPreviousSensitiveMatch(tempRoot);
            testJsonControlAndScriptEscaping();
            System.out.println("JsonParsingRegressionTest: PASS");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void testChatAnnotationStoreVersionBeforeChatsAndNestedKeys(Path tempRoot) throws Exception {
        Path storeDir = tempRoot.resolve("valid-store");
        Files.createDirectories(storeDir);
        Path jsonPath = storeDir.resolve(ChatAnnotationStore.FILE_NAME);
        String json = "{\n"
                + "  \"version\": 2,\n"
                + "  \"updatedAt\": \"2026-11-13T10:00:00Z\",\n"
                + "  \"chats\": {\n"
                + "    \"chat-1\": {\n"
                + "      \"items\": [\n"
                + "        {\n"
                + "          \"messageId\": \"m-1\",\n"
                + "          \"note\": {\n"
                + "            \"text\": \"Spoofed \\\"chats\\\": {\\\"fake\\\": 1}, \\\"version\\\": 99, \\\"messageId\\\": \\\"spoof\\\" \\u00e3\\b\\f\",\n"
                + "            \"author\": \"analyst\",\n"
                + "            \"createdAt\": \"2026-11-13T09:00:00Z\",\n"
                + "            \"updatedAt\": \"2026-11-13T09:05:00Z\"\n"
                + "          },\n"
                + "          \"links\": [\n"
                + "            {\n"
                + "              \"title\": \"Linked \\\"title\\\": \\\"bad\\\"\",\n"
                + "              \"chatRefId\": \"ref-1\",\n"
                + "              \"origin\": \"saved\",\n"
                + "              \"sourceName\": \"db\",\n"
                + "              \"chatKey\": \"ck-1\",\n"
                + "              \"captureFolder\": \"cap-1\"\n"
                + "            }\n"
                + "          ]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        Files.writeString(jsonPath, json, StandardCharsets.UTF_8);

        assertEqual("2", ChatAnnotationStore.field(json, "version"), "numeric version field");
        assertNotNull(ChatAnnotationStore.extractObject(json, "chats"), "chats object must survive numeric version before it");

        ChatAnnotationStore store = new ChatAnnotationStore();
        store.bindOutputRoot(storeDir);
        ChatAnnotation item = store.get("chat-1", "m-1");
        assertNotNull(item, "chat-1 item m-1 must be loaded");
        assertEqual("m-1", item.messageId, "top-level item messageId must win over spoofed text");
        assertTrue(item.noteText.contains("\"chats\": {\"fake\": 1}"), "note text must preserve inner quotes");
        assertTrue(item.noteText.contains("\u00e3\b\f"), "unicode and \\b/\\f escapes must decode");
        assertEqual("analyst", item.noteAuthor, "note author");
        assertTrue(item.links.size() == 1, "expected 1 link");
        assertEqual("Linked \"title\": \"bad\"", item.links.get(0).title, "link title");
        assertEqual("cap-1", item.links.get(0).captureFolder, "link captureFolder");
    }

    private static void testChatAnnotationStoreScalarsNullAndMalformed(Path tempRoot) throws Exception {
        String scalarJson = "{ \"num\": 42, \"neg\": -3.5e1, \"ok\": true, \"no\": false, \"nil\": null, \"obj\": {\"ok\": false}, \"arr\": [1, 2] }";
        assertEqual("42", ChatAnnotationStore.field(scalarJson, "num"), "integer scalar");
        assertEqual("-3.5e1", ChatAnnotationStore.field(scalarJson, "neg"), "exponent scalar");
        assertEqual("true", ChatAnnotationStore.field(scalarJson, "ok"), "top-level boolean true");
        assertEqual("false", ChatAnnotationStore.field(scalarJson, "no"), "top-level boolean false");
        assertEqual("", ChatAnnotationStore.field(scalarJson, "nil"), "null literal must map to empty string");
        assertEqual("", ChatAnnotationStore.field(scalarJson, "obj"), "object must not coerce via field()");
        assertEqual("", ChatAnnotationStore.field(scalarJson, "arr"), "array must not coerce via field()");

        List<String> arr = ChatAnnotationStore.splitArrayElements("  [ 10, true, null, {\"a\":1}, \"hi\" ] ");
        assertTrue(arr.size() == 5, "array with numbers/booleans/null/object/string must parse 5 items");
        assertEqual("10", arr.get(0), "array element 0");
        assertEqual("{\"a\":1}", arr.get(3), "array element 3");

        String truncated = "{\"chats\":{\"c1\":{\"items\":[{\"messageId\":\"m1\"";
        assertNull(ChatAnnotationStore.extractObject(truncated, "chats"), "truncated JSON must not extract");
        assertTrue(ChatAnnotationStore.splitObjectEntries("{\"a\":1,}").isEmpty(), "trailing comma must be rejected");
        assertTrue(ChatAnnotationStore.splitObjectEntries("{\"a\": invalidToken}").isEmpty(), "unquoted junk token must be rejected");

        Path brokenDir = tempRoot.resolve("broken-store");
        Files.createDirectories(brokenDir);
        Files.writeString(brokenDir.resolve(ChatAnnotationStore.FILE_NAME), truncated, StandardCharsets.UTF_8);
        ChatAnnotationStore brokenStore = new ChatAnnotationStore();
        brokenStore.bindOutputRoot(brokenDir);
        assertNull(brokenStore.get("c1", "m1"), "malformed annotations file must load empty instead of misparsing");
    }

    private static void testLoadRecaptureJobsLegacyAnchorOrderingAndOrgJson() {
        HtmlLinkViewer.RecaptureJob job = new HtmlLinkViewer.RecaptureJob();
        String legacyManifest = "{\n"
                + "  \"version\": 1,\n"
                + "  \"status\": \"completed\",\n"
                + "  \"startMessageId\": 1001,\n"
                + "  \"endMessageId\": \"1009\",\n"
                + "  \"sourceMetadata\": {\n"
                + "    \"nome\": \"Grupo \\\"status\\\": \\\"hacked\\\" } { }\\n\",\n"
                + "    \"hash\": \"abc123\",\n"
                + "    \"caminho\": \"chats/grupo\"\n"
                + "  },\n"
                + "  \"startAnchor\": {\n"
                + "    \"type\": \"message\",\n"
                + "    \"id\": null,\n"
                + "    \"text\": \"msg com { chaves } e \\\"id\\\": \\\"errado\\\"\",\n"
                + "    \"date\": \"2026-10-01 10:00:00\",\n"
                + "    \"documentTop\": 12.5\n"
                + "  },\n"
                + "  \"endAnchor\": {\n"
                + "    \"type\": \"text\",\n"
                + "    \"text\": \"ultima\",\n"
                + "    \"date\": \"2026-10-01 10:05:00\"\n"
                + "  }\n"
                + "}";
        boolean ok = HtmlLinkViewer.populateRecaptureJobMetadata(job, legacyManifest, null);
        assertTrue(ok, "valid legacy manifest must parse");
        assertTrue(job.legacyCapture, "must be legacy capture");
        assertTrue(job.legacyAnnotations, "version 1 without html-message-v1 must be legacy annotations");
        assertEqual("completed", job.status, "top-level status must win over nested string");
        assertEqual("1001", job.startAnchor.id, "startAnchor.id must inherit startMessageId after both load");
        assertEqual("1009", job.endAnchor.id, "endAnchor.id must inherit endMessageId after both load");
        assertEqual("msg com { chaves } e \"id\": \"errado\"", job.startAnchor.text, "startAnchor text with braces and quotes");
        assertEqual("abc123", job.sourceHash, "sourceHash");

        HtmlLinkViewer.RecaptureJob nullStatusJob = new HtmlLinkViewer.RecaptureJob();
        String spoofJson = "{\"version\": 2, \"status\": null, \"startAnchor\": {\"id\": \"55\"}, \"endAnchor\": {\"id\": \"60\"}, "
                + "\"sourceMetadata\": {\"nome\": \"Fake \\\"status\\\": \\\"evil\\\"\"}}";
        assertTrue(HtmlLinkViewer.populateRecaptureJobMetadata(nullStatusJob, spoofJson, null), "v2 manifest must parse");
        assertEqual("", nullStatusJob.status, "JSON null top-level status must not read nested fake status or literal null");
        assertEqual("55", nullStatusJob.startMessageId, "v2 startMessageId from startAnchor");

        assertTrue(!HtmlLinkViewer.populateRecaptureJobMetadata(new HtmlLinkViewer.RecaptureJob(), "{ broken json \"status\": \"completed\"", null),
                "invalid manifest JSON must return false");
    }

    private static void testCaptureRootMetadataNestedFakeKeysAndBraces() {
        HtmlLinkViewer.CaptureRootEntry entry = new HtmlLinkViewer.CaptureRootEntry();
        entry.status = "unknown";
        String[] bounds = new String[] { "", "" };
        String manifest = "{\n"
                + "  \"captureFormat\": \"legacy-screenshot\",\n"
                + "  \"version\": 2,\n"
                + "  \"status\": \"completed\",\n"
                + "  \"startAnchor\": { \"text\": \"ini { }\", \"date\": \"2026-10-01 08:00:00\" },\n"
                + "  \"endAnchor\": { \"text\": \"fim { }\", \"date\": \"2026-10-01 09:00:00\" },\n"
                + "  \"annotations\": { \"counts\": { \"notes\": 3, \"annotatedMessages\": 4, \"contacts\": 2 } },\n"
                + "  \"sourceMetadata\": { \"nome\": \"Chat \\\"version\\\": 1 \\\"status\\\": \\\"failed\\\"\", \"size\": 4096 }\n"
                + "}";
        assertTrue(HtmlLinkViewer.populateCaptureRootManifestJson(entry, manifest, bounds), "manifest root entry must parse");
        assertTrue(!entry.legacyAnnotations, "top-level version 2 must override spoofed nested version 1");
        assertEqual("completed", entry.status, "top-level status");
        assertEqual("2026-10-01 08:00:00", bounds[0], "startAnchor date despite braces in text");
        assertEqual("2026-10-01 09:00:00", bounds[1], "endAnchor date despite braces in text");
        assertTrue(entry.annotationsEnabled && entry.notesCount == 3 && entry.linksCount == 4 && entry.contactsCount == 2,
                "annotation counts must parse cleanly");
        assertTrue(entry.sizeBytes == 4096L, "sizeBytes");

        HtmlLinkViewer.CaptureRootEntry invalidEntry = new HtmlLinkViewer.CaptureRootEntry();
        invalidEntry.status = "unknown";
        assertTrue(!HtmlLinkViewer.populateCaptureRootManifestJson(invalidEntry, "{ \"status\": \"completed\" ", bounds),
                "invalid root manifest must return false");
        assertEqual("invalid", invalidEntry.status, "invalid root manifest marks status invalid");
    }

    private static void testCoordinateAnchorsAndPreviousSensitiveMatch(Path tempRoot) throws Exception {
        String coords = "{\n"
                + "  \"frames\": [\n"
                + "    {\n"
                + "      \"file\": \"screenshots/frame1.png\",\n"
                + "      \"blocks\": [\n"
                + "        { \"id\": null, \"type\": \"image\", \"text\": \"Ignored {hash} 2026-10-01 10:00:00\", \"hash\": \"OTHER\", \"file\": \"../escape.png\" },\n"
                + "        { \"id\": \"m2\", \"type\": \"image\", \"text\": \"foto {ok} 2026-10-01 10:01:00\", \"hash\": \"deadbeef\", \"file\": \"screenshots/frame1.png\" }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        List<HtmlLinkViewer.CaptureAnchor> anchors = HtmlLinkViewer.readCoordinateAnchors(coords);
        assertTrue(anchors.size() == 2, "expected 2 blocks, got " + anchors.size());
        assertEqual("", anchors.get(0).id, "null id must become empty string");
        assertEqual("m2", anchors.get(1).id, "second block id");
        assertTrue(HtmlLinkViewer.readCoordinateAnchors("{\"frames\":[{\"blocks\":[{\"id\":\"x\"").isEmpty(),
                "invalid coordinates JSON must return empty instead of regex misparse");

        Files.createDirectories(tempRoot.resolve("screenshots"));
        Path safeFrame = tempRoot.resolve("screenshots/frame1.png");
        Files.write(safeFrame, new byte[] { 1, 2, 3 });
        Path outside = tempRoot.getParent().resolve("escape.png");
        Files.write(outside, new byte[] { 4, 5, 6 });

        List<File> matchedCandidates = new ArrayList<>();
        String traversalCoords = "{\n"
                + "  \"frames\": [\n"
                + "    {\n"
                + "      \"blocks\": [\n"
                + "        { \"id\": \"1\", \"text\": \"spoof \\\"hash\\\": \\\"DEADBEEF\\\", \\\"file\\\": \\\"screenshots/frame1.png\\\"\", \"hash\": \"NOPE\", \"file\": \"screenshots/frame1.png\" },\n"
                + "        { \"id\": \"2\", \"hash\": \"DEADBEEF\", \"file\": \"../escape.png\" }\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"blocks\": [\n"
                + "    { \"id\": \"3\", \"hash\": \"deadbeef\", \"file\": \"screenshots/frame1.png\" }\n"
                + "  ]\n"
                + "}";
        boolean matched = HtmlLinkViewer.matchesSensitiveImageInCoordinatesJson(tempRoot, traversalCoords, "deadbeef",
                file -> {
                    matchedCandidates.add(file);
                    return file.toPath().toAbsolutePath().normalize().equals(safeFrame.toAbsolutePath().normalize());
                });
        assertTrue(matched, "safe contained root/frame block match must succeed");
        assertTrue(matchedCandidates.size() == 1, "spoofed string hash and ../escape.png must never reach matcher");
        Files.deleteIfExists(outside);
    }

    private static void testJsonControlAndScriptEscaping() {
        assertEqual("null", HtmlLinkViewer.json(null), "json(null) must return literal null");
        String input = "\t\b\f\u0007</SCRIPT>\u2028\u2029";
        String encoded = HtmlLinkViewer.json(input);
        assertTrue(encoded.startsWith("\"") && encoded.endsWith("\""), "encoded JSON string must be quoted");
        assertTrue(!encoded.contains("</") && !encoded.contains("<"), "encoded JSON must escape '<' to prevent </script> breakout");
        assertTrue(encoded.contains("\\u003c/SCRIPT>") || encoded.contains("\\u003C/SCRIPT>"),
                "encoded JSON must escape </SCRIPT> with \\u003c");
        assertTrue(encoded.contains("\\t") && encoded.contains("\\b") && encoded.contains("\\f")
                && encoded.contains("\\u0007") && encoded.contains("\\u2028") && encoded.contains("\\u2029"),
                "encoded JSON must escape control chars and LS/PS separators");
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            assertTrue(c >= 0x20 && c != '\u2028' && c != '\u2029', "encoded JSON must not contain raw control chars or LS/PS");
        }
        Object decoded = new org.json.JSONTokener(encoded).nextValue();
        assertEqual(input, decoded == null ? null : decoded.toString(), "JSONTokener round-trip must recover exact input");
    }

    private static void assertEqual(String expected, String actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + ": expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertNull(Object val, String msg) {
        if (val != null) {
            throw new AssertionError(msg + ": expected null but got " + val);
        }
    }

    private static void assertNotNull(Object val, String msg) {
        if (val == null) {
            throw new AssertionError(msg + ": expected non-null");
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> s = Files.walk(root)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
