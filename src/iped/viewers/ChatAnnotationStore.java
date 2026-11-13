package iped.viewers;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado das anotacoes do analista, por chat, e persistencia em
 * {@code <outputRoot>/anotacoes.json}.
 *
 * Recurso opcional: se o arquivo nao existir, nada e criado por leitura e nada
 * quebra. A gravacao e atomica (arquivo temporario + move).
 */
class ChatAnnotationStore {

    private static final long MAX_STORE_BYTES = 8L * 1024L * 1024L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatAnnotationStore.class);

    static final String FILE_NAME = "anotacoes.json"; //$NON-NLS-1$
    static final int SCHEMA_VERSION = 2;

    static final String ORIGIN_CASE = "case"; //$NON-NLS-1$
    static final String ORIGIN_MANUAL = "manual"; //$NON-NLS-1$

    private final Map<String, List<ChatAnnotation>> byChatKey = new LinkedHashMap<>();
    private Path outputRoot;
    private Path storeFile;
    private long loadedStamp = -1;

    /**
     * Anotacoes ficam so na RAM ate a captura. Quando ha mudanca ainda nao
     * gravada, o arquivo em disco e mais velho e nunca pode sobrescrever o que
     * o analista fez na sessao atual.
     */
    private boolean dirty;

    boolean isDirty() {
        return dirty;
    }

    /**
     * Aponta o armazenamento para a raiz de saida e carrega o que houver.
     *
     * As anotacoes existentes no destino sao apenas carregadas e exibidas; nada
     * e gravado aqui. Alteracoes da sessao ficam na RAM e so vao para o disco
     * quando o usuario executa a captura ({@link #persist()}).
     */
    void bindOutputRoot(Path outputRoot) {
        Path normalizedRoot = outputRoot == null ? null : outputRoot.toAbsolutePath().normalize();
        Path newFile = normalizedRoot == null ? null : normalizedRoot.resolve(FILE_NAME);
        if (newFile == null) {
            return;
        }
        this.outputRoot = normalizedRoot;
        if (!newFile.equals(storeFile)) {
            byChatKey.clear();
            dirty = false;
            storeFile = newFile;
            loadedStamp = lastModifiedOf(newFile);
            load();
            return;
        }
        if (dirty) {
            // o que esta na RAM e mais novo que o arquivo: nao recarrega.
            return;
        }
        long stamp = lastModifiedOf(newFile);
        if (stamp != loadedStamp) {
            // mudou por fora (por exemplo depois de um Re-Extrair)
            loadedStamp = stamp;
            load();
        }
    }

    private static long lastModifiedOf(Path path) {
        try {
            if (path == null || CaptureIntegrity.isLinkOrReparse(path)
                    || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return -1L;
            }
            return Files.getLastModifiedTime(path, java.nio.file.LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (Exception e) {
            return -1L;
        }
    }

    Path getStoreFile() {
        return storeFile;
    }

    boolean isBound() {
        return storeFile != null;
    }

    // ---------------------------------------------------------------- leitura

    private void load() {
        if (storeFile == null) {
            return;
        }
        try {
            if ((outputRoot != null && CaptureIntegrity.isLinkOrReparse(outputRoot))
                    || CaptureIntegrity.isLinkOrReparse(storeFile)
                    || !Files.isRegularFile(storeFile, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to inspect {}, keeping annotations in memory only", storeFile, e); //$NON-NLS-1$
            return;
        }
        String content;
        try {
            content = CaptureIntegrity.readUtf8Limited(storeFile, MAX_STORE_BYTES);
        } catch (IOException e) {
            LOGGER.warn("Unable to read {}, keeping annotations in memory only", storeFile, e); //$NON-NLS-1$
            return;
        }
        String chats = extractObject(content, "chats"); //$NON-NLS-1$
        if (chats == null || chats.isEmpty()) {
            return;
        }
        for (String[] entry : splitObjectEntries(chats)) {
            String chatKey = entry[0];
            List<ChatAnnotation> items = parseItems(entry[1]);
            if (!items.isEmpty()) {
                byChatKey.put(chatKey, items);
            }
        }
        // arquivo legado pode ter outra chave; se sobrou algo, usa o unico objeto
        if (!byChatKey.isEmpty()) {
            return;
        }
        for (String[] entry : splitObjectEntries(content)) {
            List<ChatAnnotation> items = parseItems(entry[1]);
            if (!items.isEmpty()) {
                byChatKey.put(entry[0], items);
            }
        }
    }

    private List<ChatAnnotation> parseItems(String chatJson) {
        List<ChatAnnotation> items = new ArrayList<>();
        String array = extractArray(chatJson, "items"); //$NON-NLS-1$
        if (array == null) {
            return items;
        }
        for (String raw : splitArrayElements(array)) {
            String messageId = field(raw, "messageId"); //$NON-NLS-1$
            if (messageId.isEmpty()) {
                continue;
            }
            ChatAnnotation item = new ChatAnnotation(messageId);
            String note = extractObject(raw, "note"); //$NON-NLS-1$
            if (note != null) {
                item.noteText = field(note, "text"); //$NON-NLS-1$
                item.noteAuthor = field(note, "author"); //$NON-NLS-1$
                item.noteCreatedAt = field(note, "createdAt"); //$NON-NLS-1$
                item.noteUpdatedAt = field(note, "updatedAt"); //$NON-NLS-1$
            }
            String links = extractArray(raw, "links"); //$NON-NLS-1$
            if (links != null) {
                for (String rawLink : splitArrayElements(links)) {
                    // displayName e contactId sao os nomes antigos do schema; mantidos
                    // para leitura de arquivos gravados por versoes anteriores.
                    String title = field(rawLink, "title"); //$NON-NLS-1$
                    if (title.isEmpty()) {
                        title = field(rawLink, "displayName"); //$NON-NLS-1$
                    }
                    String refId = field(rawLink, "chatRefId"); //$NON-NLS-1$
                    if (refId.isEmpty()) {
                        refId = field(rawLink, "contactId"); //$NON-NLS-1$
                    }
                    if (refId.isEmpty()) {
                        refId = chatRefIdOf(title, field(rawLink, "sourceName")); //$NON-NLS-1$
                    }
                    LinkedChatRef link = new LinkedChatRef(refId, title);
                    if (link.title.isEmpty()) {
                        continue;
                    }
                    link.origin = field(rawLink, "origin"); //$NON-NLS-1$
                    if (link.origin.isEmpty()) {
                        link.origin = ORIGIN_CASE;
                    }
                    link.sourceName = field(rawLink, "sourceName"); //$NON-NLS-1$
                    link.chatKey = field(rawLink, "chatKey"); //$NON-NLS-1$
                    link.captureFolder = field(rawLink, "captureFolder"); //$NON-NLS-1$
                    item.links.add(link);
                }
            }
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    // ---------------------------------------------------------------- estado

    List<ChatAnnotation> itemsFor(String chatKey) {
        List<ChatAnnotation> items = byChatKey.get(chatKey);
        return items == null ? new ArrayList<>() : items;
    }

    ChatAnnotation get(String chatKey, String messageId) {
        if (chatKey == null || messageId == null) {
            return null;
        }
        for (ChatAnnotation item : itemsFor(chatKey)) {
            if (item.messageId.equals(messageId)) {
                return item;
            }
        }
        return null;
    }

    void setNote(String chatKey, String messageId, String text, String author) {
        List<ChatAnnotation> items = itemsFor(chatKey);
        ChatAnnotation item = null;
        for (ChatAnnotation candidate : items) {
            if (candidate.messageId.equals(messageId)) {
                item = candidate;
                break;
            }
        }
        if (item == null) {
            item = new ChatAnnotation(messageId);
            items.add(item);
            byChatKey.put(chatKey, items);
        }
        String now = Instant.now().toString();
        if (item.noteCreatedAt.isEmpty()) {
            item.noteCreatedAt = now;
        }
        item.noteText = text;
        item.noteAuthor = author;
        item.noteUpdatedAt = now;
        dropIfEmpty(chatKey, items, item);
        markDirty();
    }

    void removeNote(String chatKey, String messageId) {
        ChatAnnotation item = get(chatKey, messageId);
        if (item == null) {
            return;
        }
        item.noteText = ""; //$NON-NLS-1$
        item.noteUpdatedAt = Instant.now().toString();
        dropIfEmpty(chatKey, itemsFor(chatKey), item);
        markDirty();
    }

    void addLinks(String chatKey, String messageId, List<LinkedChatRef> chats) {
        if (chats == null || chats.isEmpty()) {
            return;
        }
        List<ChatAnnotation> items = itemsFor(chatKey);
        ChatAnnotation item = null;
        for (ChatAnnotation candidate : items) {
            if (candidate.messageId.equals(messageId)) {
                item = candidate;
                break;
            }
        }
        if (item == null) {
            item = new ChatAnnotation(messageId);
            items.add(item);
            byChatKey.put(chatKey, items);
        }
        for (LinkedChatRef chat : chats) {
            item.addLink(chat);
        }
        markDirty();
    }

    void removeLinks(String chatKey, String messageId, List<String> chatKeys) {
        if (chatKeys == null || chatKeys.isEmpty()) {
            return;
        }
        ChatAnnotation item = get(chatKey, messageId);
        if (item == null) {
            return;
        }
        for (String key : chatKeys) {
            item.removeLink(key);
        }
        dropIfEmpty(chatKey, itemsFor(chatKey), item);
        markDirty();
    }

    void removeAllLinks(String chatKey, String messageId) {
        ChatAnnotation item = get(chatKey, messageId);
        if (item == null) {
            return;
        }
        item.removeAllLinks();
        dropIfEmpty(chatKey, itemsFor(chatKey), item);
        markDirty();
    }

    private void dropIfEmpty(String chatKey, List<ChatAnnotation> items, ChatAnnotation item) {
        if (!item.isEmpty()) {
            return;
        }
        items.remove(item);
        if (items.isEmpty()) {
            byChatKey.remove(chatKey);
        }
    }

    void clearChat(String chatKey) {
        byChatKey.remove(chatKey);
        markDirty();
    }

    // ------------------------------------------------------------ gravacao

    private void markDirty() {
        dirty = true;
    }

    /**
     * Grava as anotacoes em disco. Chamado apenas na captura, porque e ela que
     * consolida o trabalho da sessao no resultado final.
     */
    void persist() {
        if (storeFile == null) {
            return;
        }
        try {
            Path root = outputRoot != null ? outputRoot : storeFile.getParent();
            if (root == null) {
                return;
            }
            java.io.StringWriter buffer = new java.io.StringWriter(4096);
            try (PrintWriter out = new PrintWriter(buffer)) {
                out.println("{"); //$NON-NLS-1$
                out.println("  \"version\": " + SCHEMA_VERSION + ","); //$NON-NLS-1$
                out.println("  \"updatedAt\": \"" + Instant.now() + "\","); //$NON-NLS-1$
                out.println("  \"chats\": {"); //$NON-NLS-1$
                int chatIndex = 0;
                for (Map.Entry<String, List<ChatAnnotation>> entry : byChatKey.entrySet()) {
                    if (chatIndex++ > 0) {
                        out.println(","); //$NON-NLS-1$
                    }
                    out.print("    \"" + escape(entry.getKey()) + "\": "); //$NON-NLS-1$ //$NON-NLS-2$
                    out.print("{\"items\": "); //$NON-NLS-1$
                    writeItems(out, entry.getValue());
                    out.print("}"); //$NON-NLS-1$
                }
                out.println(); //$NON-NLS-1$
                out.println("  }"); //$NON-NLS-1$
                out.println("}"); //$NON-NLS-1$
            }
            CaptureIntegrity.writeAtomically(root, storeFile, buffer.toString(), MAX_STORE_BYTES);
            dirty = false;
            loadedStamp = lastModifiedOf(storeFile);
        } catch (Exception e) {
            LOGGER.warn("Unable to write {}", storeFile, e); //$NON-NLS-1$
        }
    }

    private void writeItems(PrintWriter out, List<ChatAnnotation> items) {
        out.print("["); //$NON-NLS-1$
        for (int i = 0; i < items.size(); i++) {
            ChatAnnotation item = items.get(i);
            if (i > 0) {
                out.print(","); //$NON-NLS-1$
            }
            out.print("{\"messageId\": \"" + escape(item.messageId) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
            if (item.hasNote()) {
                out.print(", \"note\": {\"text\": \"" + escape(item.noteText) + "\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                out.print(", \"author\": \"" + escape(item.noteAuthor) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.print(", \"createdAt\": \"" + escape(item.noteCreatedAt) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
                out.print(", \"updatedAt\": \"" + escape(item.noteUpdatedAt) + "\"}"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (item.hasLinks()) {
                out.print(", \"links\": ["); //$NON-NLS-1$
                for (int j = 0; j < item.links.size(); j++) {
                    if (j > 0) {
                        out.print(","); //$NON-NLS-1$
                    }
                    writeLink(out, item.links.get(j));
                }
                out.print("]"); //$NON-NLS-1$
            }
            out.print("}"); //$NON-NLS-1$
        }
        out.print("]"); //$NON-NLS-1$
    }

    private void writeLink(PrintWriter out, LinkedChatRef link) {
        out.print("{\"chatRefId\": \"" + escape(link.chatRefId) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(", \"title\": \"" + escape(link.title) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(", \"origin\": \"" + escape(link.origin) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(", \"sourceName\": \"" + escape(link.sourceName) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(", \"chatKey\": \"" + escape(link.chatKey) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
        out.print(", \"captureFolder\": \"" + escape(link.captureFolder) + "\"}"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    // ------------------------------------------------------------- utilidades

    static String chatKeyOf(String sourceHash, String sourceName, String sourcePath) {
        String base = sourceHash;
        if (base == null || base.trim().isEmpty()) {
            base = sourceName;
        }
        if (base == null || base.trim().isEmpty()) {
            base = sourcePath;
        }
        if (base == null) {
            return ""; //$NON-NLS-1$
        }
        if (!sourceHashIsUsable(sourceHash)) {
            base = base + "|" + normalizeKey(sourceName) + "|" + normalizeKey(sourcePath); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return sha1Hex(base);
    }

    private static boolean sourceHashIsUsable(String sourceHash) {
        return sourceHash != null && sourceHash.trim().length() >= 8;
    }

    /** Id estavel de um chat vinculado, derivado do titulo e do nome do item. */
    static String chatRefIdOf(String title, String sourceName) {
        String base = normalizeKey(title) + "|" + normalizeKey(sourceName); //$NON-NLS-1$
        if (normalizeKey(title).isEmpty() && normalizeKey(sourceName).isEmpty()) {
            return ""; //$NON-NLS-1$
        }
        return "ch-" + sha1Hex(base).substring(0, 8); //$NON-NLS-1$
    }

    static String normalizeKey(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); //$NON-NLS-1$
        return normalized.replaceAll("\\s+", " ").trim().toLowerCase(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1"); //$NON-NLS-1$
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(String.format("%02x", b)); //$NON-NLS-1$
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    static String escape(String value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\""); //$NON-NLS-1$
                    break;
                case '\\':
                    out.append("\\\\"); //$NON-NLS-1$
                    break;
                case '\r':
                    out.append("\\r"); //$NON-NLS-1$
                    break;
                case '\n':
                    out.append("\\n"); //$NON-NLS-1$
                    break;
                case '\t':
                    out.append("\\t"); //$NON-NLS-1$
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c)); //$NON-NLS-1$
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value == null ? "" : value; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append(next);
                        }
                    } else {
                        out.append(next);
                    }
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }

    /**
     * Extrai o conteudo bruto de um objeto JSON de primeiro nivel por chave.
     * Suficiente para o formato gravado por esta classe.
     */
    static String extractObject(String json, String key) {
        if (json == null) {
            return null; //$NON-NLS-1$
        }
        String needle = "\"" + key + "\""; //$NON-NLS-1$
        int at = json.indexOf(needle);
        if (at < 0) {
            return null; //$NON-NLS-1$
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return null; //$NON-NLS-1$
        }
        int start = skipSpace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '{') {
            return null; //$NON-NLS-1$
        }
        int end = matchBracket(json, start, '{', '}');
        return end < 0 ? null : json.substring(start, end + 1);
    }

    static String extractArray(String json, String key) {
        if (json == null) {
            return null; //$NON-NLS-1$
        }
        String needle = "\"" + key + "\""; //$NON-NLS-1$
        int at = json.indexOf(needle);
        if (at < 0) {
            return null; //$NON-NLS-1$
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return null; //$NON-NLS-1$
        }
        int start = skipSpace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '[') {
            return null; //$NON-NLS-1$
        }
        int end = matchBracket(json, start, '[', ']');
        return end < 0 ? null : json.substring(start, end + 1);
    }

    static String field(String json, String key) {
        if (json == null) {
            return ""; //$NON-NLS-1$
        }
        String needle = "\"" + key + "\""; //$NON-NLS-1$
        int at = json.indexOf(needle);
        if (at < 0) {
            return ""; //$NON-NLS-1$
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return ""; //$NON-NLS-1$
        }
        int start = skipSpace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '"') {
            return ""; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                out.append(c);
                i++;
                if (i < json.length()) {
                    out.append(json.charAt(i));
                }
                continue;
            }
            if (c == '"') {
                return unescape(out.toString());
            }
            out.append(c);
        }
        return unescape(out.toString());
    }

    /** Divide {@code {"k":{...},"k2":{...}}} em pares chave/conteudo. */
    static List<String[]> splitObjectEntries(String objectJson) {
        List<String[]> entries = new ArrayList<>();
        if (objectJson == null || objectJson.length() < 2) {
            return entries;
        }
        int i = 1;
        while (i < objectJson.length()) {
            while (i < objectJson.length()
                    && (objectJson.charAt(i) == ',' || Character.isWhitespace(objectJson.charAt(i)))) {
                i++;
            }
            if (i >= objectJson.length() || objectJson.charAt(i) == '}') {
                break;
            }
            int keyEnd = matchQuote(objectJson, i);
            if (keyEnd < 0) {
                break;
            }
            String key = unescape(objectJson.substring(i + 1, keyEnd - 1));
            int colon = objectJson.indexOf(':', keyEnd);
            if (colon < 0) {
                break;
            }
            int valueStart = skipSpace(objectJson, colon + 1);
            if (valueStart >= objectJson.length()) {
                break;
            }
            char open = objectJson.charAt(valueStart);
            int valueEnd = open == '{' ? matchBracket(objectJson, valueStart, '{', '}')
                    : open == '[' ? matchBracket(objectJson, valueStart, '[', ']') : matchQuote(objectJson, valueStart);
            if (valueEnd < 0) {
                break;
            }
            entries.add(new String[] { key, objectJson.substring(valueStart, valueEnd + 1) });
            i = valueEnd + 1;
        }
        return entries;
    }

    static List<String> splitArrayElements(String arrayJson) {
        List<String> elements = new ArrayList<>();
        if (arrayJson == null || arrayJson.length() < 2) {
            return elements;
        }
        int i = 1;
        while (i < arrayJson.length()) {
            while (i < arrayJson.length() && (arrayJson.charAt(i) == ',' || Character.isWhitespace(arrayJson.charAt(i)))) {
                i++;
            }
            if (i >= arrayJson.length() || arrayJson.charAt(i) == ']') {
                break;
            }
            char open = arrayJson.charAt(i);
            int end = open == '{' ? matchBracket(arrayJson, i, '{', '}') : open == '[' ? matchBracket(arrayJson, i, '[', ']')
                    : matchQuote(arrayJson, i);
            if (end < 0) {
                break;
            }
            elements.add(arrayJson.substring(i, end + 1));
            i = end + 1;
        }
        return elements;
    }

    private static int skipSpace(String json, int from) {
        int i = from;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int matchQuote(String json, int start) {
        if (start >= json.length() || json.charAt(start) != '"') {
            return -1; //$NON-NLS-1$
        }
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '"') {
                return i + 1; //$NON-NLS-1$
            }
        }
        return -1; //$NON-NLS-1$
    }

    private static int matchBracket(String json, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i; //$NON-NLS-1$
                }
            }
        }
        return -1; //$NON-NLS-1$
    }
}
