package iped.viewers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ChatAnnotationStoreRoundTripTest {
    private ChatAnnotationStoreRoundTripTest() {
    }

    public static void main(String[] args) throws Exception {
        testNotesAndLinksRoundTrip();
        testFieldMetadataExtraction();
        testJunctionOutputIsRejected();
        testPersistContractAndMalformedUnicodeEscape();
        System.out.println("ChatAnnotationStoreRoundTripTest: PASS");
    }

    private static void testNotesAndLinksRoundTrip() throws Exception {
        String[] samples = new String[] {
                "C:\\Users\\perito\\notas.txt",
                "quoted \"text\" and \\\\backslash\\\\",
                "Linha 1\nLinha 2\tTab\rCR",
                "literal \\n and \\t and \\\"quote\\\"",
                "Unicode Jose \u00e9 \u2014 \\u0041 {a: [1, 2]} / ok"
        };

        Path tempDir = Files.createTempDirectory("chat-annotation-roundtrip");
        try {
            ChatAnnotationStore writer = new ChatAnnotationStore();
            writer.bindOutputRoot(tempDir);

            String[] chatKeys = new String[] {
                    "chat-hex-001",
                    "legacy\\chat\nkey\"quoted\""
            };

            for (int c = 0; c < chatKeys.length; c++) {
                String chatKey = chatKeys[c];
                for (int i = 0; i < samples.length; i++) {
                    String s = samples[i];
                    String msgId = "msg-" + c + "-" + i + "\\id\"x\"";
                    String author = "Perito \\ \"Silva\" #" + i;
                    writer.setNote(chatKey, msgId, s, author);

                    LinkedChatRef ref = new LinkedChatRef("ref\\" + i, "Titulo: " + s);
                    ref.origin = ChatAnnotationStore.ORIGIN_MANUAL;
                    ref.sourceName = "msgstore\\db\\" + i + ".db";
                    ref.chatKey = "linked\\" + i + "\\key";
                    ref.captureFolder = "C:\\Capturas\\caso_" + i + "\\sub";
                    writer.addLinks(chatKey, msgId, Collections.singletonList(ref));
                }
            }
            writer.persist();

            ChatAnnotationStore reader = new ChatAnnotationStore();
            reader.bindOutputRoot(tempDir);

            for (int c = 0; c < chatKeys.length; c++) {
                String chatKey = chatKeys[c];
                for (int i = 0; i < samples.length; i++) {
                    String s = samples[i];
                    String msgId = "msg-" + c + "-" + i + "\\id\"x\"";
                    String author = "Perito \\ \"Silva\" #" + i;
                    ChatAnnotation loaded = reader.get(chatKey, msgId);
                    check(loaded != null, "Missing loaded annotation for " + msgId);
                    equal(s, loaded.noteText, "noteText mismatch for sample " + i);
                    equal(author, loaded.noteAuthor, "noteAuthor mismatch for sample " + i);
                    equal(1, loaded.links.size(), "link count mismatch for sample " + i);
                    LinkedChatRef link = loaded.links.get(0);
                    equal("ref\\" + i, link.chatRefId, "chatRefId mismatch");
                    equal(("Titulo: " + s).trim(), link.title, "link.title mismatch");
                    equal("msgstore\\db\\" + i + ".db", link.sourceName, "link.sourceName mismatch");
                    equal("linked\\" + i + "\\key", link.chatKey, "link.chatKey mismatch");
                    equal("C:\\Capturas\\caso_" + i + "\\sub", link.captureFolder, "link.captureFolder mismatch");
                }
            }
        } finally {
            deleteTree(tempDir);
        }
    }

    private static void testFieldMetadataExtraction() {
        String[] values = new String[] {
                "C:\\Casos\\WhatsApp\\evidencia \"A\"\\chat.html",
                "Grupo \\nLiteral\\tTab\\\"Aspas\\\"",
                "Nome com barra / e contra-barra \\\\ no fim\\"
        };
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            String json = "{\"title\": \"" + ChatAnnotationStore.escape(value) + "\", "
                    + "\"sourceName\": \"" + ChatAnnotationStore.escape("src\\" + value) + "\", "
                    + "\"sourcePath\": \"" + ChatAnnotationStore.escape("D:\\Cases\\" + i + "\\" + value) + "\", "
                    + "\"captureFolder\": \"" + ChatAnnotationStore.escape("C:\\Out\\cap\\" + i) + "\"}";
            equal(value, ChatAnnotationStore.field(json, "title"), "field(title) mismatch");
            equal("src\\" + value, ChatAnnotationStore.field(json, "sourceName"), "field(sourceName) mismatch");
            equal("D:\\Cases\\" + i + "\\" + value, ChatAnnotationStore.field(json, "sourcePath"), "field(sourcePath) mismatch");
            equal("C:\\Out\\cap\\" + i, ChatAnnotationStore.field(json, "captureFolder"), "field(captureFolder) mismatch");
        }
    }

    private static void testJunctionOutputIsRejected() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return;
        }
        Path work = Files.createTempDirectory("chat-annotation-junction-");
        try {
            Path outside = Files.createDirectories(work.resolve("outside"));
            Files.writeString(outside.resolve(ChatAnnotationStore.FILE_NAME),
                    "{\"version\":2,\"chats\":{\"k\":{\"items\":[{\"id\":\"m1\",\"note\":{\"text\":\"evil\",\"author\":\"a\",\"updatedAt\":\"t\"}}]}}}");
            Path rootJunction = work.resolve("out-junction");
            Process p = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                    rootJunction.toAbsolutePath().toString(), outside.toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            if (p.waitFor() != 0) {
                throw new AssertionError("mklink /J failed");
            }
            try {
                ChatAnnotationStore reader = new ChatAnnotationStore();
                reader.bindOutputRoot(rootJunction);
                check(reader.get("k", "m1") == null, "load() must ignore store inside NTFS junction outputRoot");

                reader.setNote("k", "m2", "note", "perito");
                check(!reader.persist(), "persist() must return false on NTFS junction outputRoot");
                check(reader.isDirty(), "persist() must fail cleanly and stay dirty on NTFS junction outputRoot");
                boolean threw = false;
                try {
                    reader.persistOrThrow();
                } catch (IOException expected) {
                    threw = true;
                }
                check(threw, "persistOrThrow() must throw IOException on NTFS junction outputRoot");
                String content = Files.readString(outside.resolve(ChatAnnotationStore.FILE_NAME));
                check(!content.contains("m2"), "persist() must not overwrite store inside junction target");
            } finally {
                Files.deleteIfExists(rootJunction);
            }
        } finally {
            deleteTree(work);
        }
    }

    private static void testPersistContractAndMalformedUnicodeEscape() throws Exception {
        ChatAnnotationStore unbound = new ChatAnnotationStore();
        unbound.setNote("k1", "m1", "nota", "perito");
        check(!unbound.persist(), "persist() must return false when outputRoot is unbound");
        boolean threwUnbound = false;
        try {
            unbound.persistOrThrow();
        } catch (IOException expected) {
            threwUnbound = true;
        }
        check(threwUnbound, "persistOrThrow() must throw IOException when outputRoot is unbound");

        Path validRoot = Files.createTempDirectory("chat-annotation-persist-ok-");
        try {
            ChatAnnotationStore valid = new ChatAnnotationStore();
            valid.bindOutputRoot(validRoot);
            valid.setNote("k1", "m1", "nota ok", "perito");
            check(valid.persist(), "persist() must return true on valid write");
            check(!valid.isDirty(), "persist() must clear dirty flag after valid write");
        } finally {
            deleteTree(validRoot);
        }

        equal("u+041", ChatAnnotationStore.unescape("\\u+041"),
                "signed/non-hex \\u+041 must not decode to 'A'");
        equal("A", ChatAnnotationStore.unescape("\\u0041"),
                "valid 4-hex \\u0041 must still decode to A");
        equal("u12", ChatAnnotationStore.unescape("\\u12"),
                "truncated \\u escape must not throw or mis-decode");
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=[" + expected + "], actual=[" + actual + "])");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
