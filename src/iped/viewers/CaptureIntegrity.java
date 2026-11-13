package iped.viewers;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Utilities for deterministic capture signatures and audit metadata. */
final class CaptureIntegrity {
    static final int SIGNATURE_VERSION = 1;
    static final String HASH_ALGORITHM = "SHA-256";
    static final String AUDIT_FILE = "reextracao-auditoria.jsonl";
    static final int MAX_INVENTORY_FILES = 25000;
    static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
    static final long MAX_HTML_CAPTURE_BYTES = 64L * 1024L * 1024L;
    static final int MAX_RELATIVE_PATH_CHARS = 4096;
    static final long MAX_AUDIT_BYTES = 16L * 1024L * 1024L;

    private CaptureIntegrity() {
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    static String sha256(InputStream input, long maximumBytes) throws IOException {
        if (input == null || maximumBytes < 0) throw new IOException("Invalid stream or byte limit for SHA-256");
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[16384];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > maximumBytes) throw new IOException("Stream exceeds the configured byte limit");
                digest.update(buffer, 0, count);
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    static JSONObject messageSignature(String id, String type, String originalText, String dateTime,
            int sequencePosition, Collection<String> mediaSha256, String auxiliaryTranscript) throws IOException {
        List<String> hashes = new ArrayList<>();
        if (mediaSha256 != null) {
            for (String hash : mediaSha256) {
                if (hash != null && !hash.trim().isEmpty()) {
                    hashes.add(hash.trim().toLowerCase());
                }
            }
        }
        hashes.sort(Comparator.naturalOrder());
        String normalizedType = value(type).toLowerCase();
        boolean mediaMessage = "image".equals(normalizedType) || "audio".equals(normalizedType)
                || "video".equals(normalizedType) || "file".equals(normalizedType);
        boolean complete = id != null && !id.trim().isEmpty()
                && (!mediaMessage || !hashes.isEmpty());

        StringBuilder canonical = new StringBuilder();
        appendField(canonical, Integer.toString(SIGNATURE_VERSION));
        appendField(canonical, id);
        appendField(canonical, normalizedType);
        appendField(canonical, originalText);
        appendField(canonical, dateTime);
        appendField(canonical, Integer.toString(sequencePosition));
        appendField(canonical, Integer.toString(hashes.size()));
        for (String hash : hashes) {
            appendField(canonical, hash);
        }

        JSONObject signature = new JSONObject();
        signature.put("signatureVersion", SIGNATURE_VERSION);
        signature.put("id", nullable(id));
        signature.put("type", normalizedType);
        signature.put("originalText", nullable(originalText));
        signature.put("dateTime", nullable(dateTime));
        signature.put("sequencePosition", sequencePosition);
        signature.put("mediaSha256", new JSONArray(hashes));
        signature.put("complete", complete);
        signature.put("identitySha256", sha256Text(canonical.toString()));
        if (auxiliaryTranscript != null && !auxiliaryTranscript.trim().isEmpty()) {
            signature.put("transcription", new JSONObject()
                    .put("kind", "auxiliary-ai-transcription")
                    .put("text", auxiliaryTranscript));
        }
        return signature;
    }

    static boolean isLinkOrReparse(Path p) throws IOException {
        if (Files.isSymbolicLink(p)) {
            return true;
        }
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther();
    }

    static List<Path> listRegularFilesSafe(Path rootPath, int maxFiles) throws IOException {
        Path root = rootPath.toAbsolutePath().normalize();
        Path realRoot = root.toRealPath();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root)) {
                    if (attrs.isSymbolicLink() || attrs.isOther() || isLinkOrReparse(dir)) {
                        throw new IOException("Symbolic links or junctions are not allowed in capture directories: " + dir);
                    }
                    if (!dir.toRealPath().startsWith(realRoot)) {
                        throw new IOException("Capture subdirectory resolves outside capture root: " + dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || attrs.isOther() || isLinkOrReparse(file)) {
                    throw new IOException("Symbolic links or junctions are not allowed in capture files: " + file);
                }
                if (attrs.isRegularFile()) {
                    if (!file.toRealPath().startsWith(realRoot)) {
                        throw new IOException("Capture file resolves outside capture root: " + file);
                    }
                    if (files.size() >= maxFiles) {
                        throw new IOException("Capture exceeds the inventory limit of " + maxFiles + " files");
                    }
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    static JSONArray artifactInventory(Path captureRoot) throws IOException {
        Path root = captureRoot.toAbsolutePath().normalize();
        JSONArray inventory = new JSONArray();
        List<Path> files = new ArrayList<>();
        for (Path path : listRegularFilesSafe(root, MAX_INVENTORY_FILES + 3)) {
            if (!path.equals(root)
                    && !AUDIT_FILE.equals(path.getFileName().toString())
                    && !"manifest.json".equals(path.getFileName().toString())
                    && !"integridade.json".equals(path.getFileName().toString())) {
                String relative = relative(root, path);
                if (relative.length() > MAX_RELATIVE_PATH_CHARS) {
                    throw new IOException("Capture contains a path longer than " + MAX_RELATIVE_PATH_CHARS + " characters");
                }
                if (files.size() >= MAX_INVENTORY_FILES) {
                    throw new IOException("Capture exceeds the inventory limit of " + MAX_INVENTORY_FILES + " files");
                }
                files.add(path);
            }
        }
        files.sort(Comparator.comparing(path -> relative(root, path)));
        for (Path file : files) {
            inventory.put(new JSONObject()
                    .put("path", relative(root, file))
                    .put("size", Files.size(file))
                    .put("sha256", sha256(file)));
        }
        return inventory;
    }

    static String readUtf8Limited(Path path) throws IOException {
        return readUtf8Limited(path, MAX_JSON_BYTES);
    }

    static String readUtf8Limited(Path path, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes > MAX_HTML_CAPTURE_BYTES) {
            throw new IllegalArgumentException("Invalid UTF-8 read limit: " + maxBytes);
        }
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new IOException("JSON metadata exceeds the limit of " + maxBytes + " bytes: " + path);
        }
        int limit = (int) maxBytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit || input.read() != -1) {
                throw new IOException("JSON metadata exceeds the limit of " + maxBytes + " bytes: " + path);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static String readJsonUtf8Limited(Path path) throws IOException {
        return readJsonUtf8Limited(path, MAX_JSON_BYTES);
    }

    static String readJsonUtf8Limited(Path path, long maxBytes) throws IOException {
        String json = readUtf8Limited(path, maxBytes);
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
                continue;
            }
            if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') {
                if (++depth > 64) throw new IOException("JSON metadata exceeds the maximum nesting depth of 64: " + path);
            } else if (ch == '}' || ch == ']') {
                if (--depth < 0) throw new IOException("Malformed JSON metadata: " + path);
            }
        }
        if (quoted || depth != 0) throw new IOException("Incomplete JSON metadata: " + path);
        return json;
    }

    static JSONObject integrityRecord(Path captureRoot, JSONObject previous, String action, String pluginVersion) throws IOException {
        JSONArray current = artifactInventory(captureRoot);
        Map<String, JSONObject> oldByPath = inventoryMap(previous == null ? null : previous.optJSONArray("artifacts"));
        Map<String, JSONObject> newByPath = inventoryMap(current);
        JSONArray changes = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : oldByPath.entrySet()) {
            JSONObject now = newByPath.get(entry.getKey());
            if (now == null) {
                changes.put(new JSONObject().put("path", entry.getKey()).put("action", "removed")
                        .put("previousSha256", entry.getValue().optString("sha256", "")));
            } else if (!entry.getValue().optString("sha256", "").equalsIgnoreCase(now.optString("sha256", ""))) {
                changes.put(new JSONObject().put("path", entry.getKey()).put("action", "changed")
                        .put("previousSha256", entry.getValue().optString("sha256", ""))
                        .put("sha256", now.optString("sha256", "")));
            }
        }
        for (Map.Entry<String, JSONObject> entry : newByPath.entrySet()) {
            if (!oldByPath.containsKey(entry.getKey())) {
                changes.put(new JSONObject().put("path", entry.getKey()).put("action", "added")
                        .put("sha256", entry.getValue().optString("sha256", "")));
            }
        }
        JSONArray events = previous == null ? new JSONArray() : new JSONArray(previous.optJSONArray("events") == null
                ? "[]" : previous.optJSONArray("events").toString());
        String previousHash = events.length() == 0 ? "" : events.optJSONObject(events.length() - 1).optString("recordHash", "");
        JSONObject event = new JSONObject().put("sequence", events.length() + 1)
                .put("timestampUtc", Instant.now().toString()).put("action", action)
                .put("contentRootHash", inventoryHash(current)).put("changes", changes)
                .put("previousRecordHash", previousHash)
                .put("recordHashFormat", "canonical-v1");
        event.put("recordHash", sha256Text(canonicalJson(event)));
        events.put(event);
        return new JSONObject().put("schemaVersion", 2).put("algorithm", HASH_ALGORITHM)
                .put("pluginVersion", pluginVersion)
                .put("contentRootHash", inventoryHash(current)).put("artifacts", current).put("events", events);
    }

    static String canonicalJson(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null"; //$NON-NLS-1$
        }
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            java.util.TreeSet<String> keys = new java.util.TreeSet<>(obj.keySet());
            StringBuilder out = new StringBuilder("{"); //$NON-NLS-1$
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(canonicalQuote(key)).append(':').append(canonicalJson(obj.opt(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            StringBuilder out = new StringBuilder("["); //$NON-NLS-1$
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(canonicalJson(arr.opt(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof String) {
            return canonicalQuote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return canonicalQuote(value.toString());
    }

    private static String canonicalQuote(String s) {
        if (s == null) {
            return "null"; //$NON-NLS-1$
        }
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\""); //$NON-NLS-1$
                    break;
                case '\\':
                    out.append("\\\\"); //$NON-NLS-1$
                    break;
                case '\b':
                    out.append("\\b"); //$NON-NLS-1$
                    break;
                case '\f':
                    out.append("\\f"); //$NON-NLS-1$
                    break;
                case '\n':
                    out.append("\\n"); //$NON-NLS-1$
                    break;
                case '\r':
                    out.append("\\r"); //$NON-NLS-1$
                    break;
                case '\t':
                    out.append("\\t"); //$NON-NLS-1$
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c)); //$NON-NLS-1$
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    private static Map<String, JSONObject> inventoryMap(JSONArray items) {
        Map<String, JSONObject> result = new java.util.TreeMap<>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) result.put(item.optString("path", ""), item);
        }
        return result;
    }

    private static String inventoryHash(JSONArray items) throws IOException {
        StringBuilder canonical = new StringBuilder();
        Map<String, JSONObject> ordered = inventoryMap(items);
        for (Map.Entry<String, JSONObject> entry : ordered.entrySet()) {
            JSONObject item = entry.getValue();
            canonical.append(entry.getKey()).append('|').append(item.optLong("size", 0)).append('|')
                    .append(item.optString("sha256", "")).append('\n');
        }
        return sha256Text(canonical.toString());
    }

    static JSONArray compareInventory(Path captureRoot, JSONArray expected) throws IOException {
        JSONArray results = new JSONArray();
        if (expected == null) {
            return results;
        }
        for (int i = 0; i < expected.length(); i++) {
            JSONObject entry = expected.optJSONObject(i);
            if (entry == null) {
                results.put(new JSONObject().put("status", "invalid_inventory_entry").put("index", i));
                continue;
            }
            String relativePath = entry.optString("path", "");
            Path file;
            try {
                file = resolveContained(captureRoot, relativePath);
            } catch (IOException e) {
                results.put(new JSONObject().put("path", relativePath).put("status", "unsafe_path"));
                continue;
            }
            String expectedHash = entry.optString("sha256", "");
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                results.put(new JSONObject().put("path", relativePath).put("expectedSha256", expectedHash)
                        .put("status", "missing"));
                continue;
            }
            String actualHash = sha256(file);
            results.put(new JSONObject().put("path", relativePath).put("expectedSha256", expectedHash)
                    .put("actualSha256", actualHash)
                    .put("status", expectedHash.equalsIgnoreCase(actualHash) ? "match" : "mismatch"));
        }
        return results;
    }

    static Path resolveContained(Path rootPath, String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IOException("Empty relative path");
        }
        if (relativePath.length() > MAX_RELATIVE_PATH_CHARS || relativePath.indexOf('\0') >= 0
                || relativePath.indexOf(':') >= 0) {
            throw new IOException("Invalid or excessively long relative path");
        }
        Path root = rootPath.toAbsolutePath().normalize();
        Path relative = root.getFileSystem().getPath(relativePath.replace('\\', '/'));
        if (relative.isAbsolute() || relative.getNameCount() == 0) {
            throw new IOException("Absolute paths are not allowed");
        }
        for (Path component : relative) {
            if ("..".equals(component.toString()) || ".".equals(component.toString())) {
                throw new IOException("Dot path components are not allowed");
            }
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Path escapes capture root");
        }
        Path current = root;
        for (Path part : root.relativize(resolved)) {
            current = current.resolve(part);
            if (isLinkOrReparse(current)) {
                throw new IOException("Symbolic links or junctions are not allowed in capture paths");
            }
        }
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            Path realRoot = root.toRealPath();
            Path realResolved = resolved.toRealPath();
            if (!realResolved.startsWith(realRoot) || realResolved.equals(realRoot)) {
                throw new IOException("Resolved path escapes capture root");
            }
        }
        return resolved;
    }

    static void appendAudit(Path captureRoot, JSONObject event) throws IOException {
        Path root = captureRoot.toAbsolutePath().normalize();
        createDirectoriesSafe(root, root);
        JSONObject record = new JSONObject(event == null ? new JSONObject().toMap() : event.toMap());
        record.put("recordedAtUtc", Instant.now().toString());
        record.put("auditSchemaVersion", 1);
        Path log = resolveContained(root, AUDIT_FILE);
        if (isLinkOrReparse(log)) {
            throw new IOException("Symbolic links or junctions are not allowed for audit log paths");
        }
        if (Files.exists(log, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Audit log path is not a regular file");
        }
        byte[] encoded = (record.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        long currentSize = Files.exists(log, LinkOption.NOFOLLOW_LINKS) ? Files.size(log) : 0L;
        if (currentSize + encoded.length > MAX_AUDIT_BYTES) {
            throw new IOException("Cumulative recapture audit exceeds the " + MAX_AUDIT_BYTES + " byte limit");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
                LinkOption.NOFOLLOW_LINKS)) {
            writer.write(new String(encoded, StandardCharsets.UTF_8));
        }
    }

    static void createDirectoriesSafe(Path root, Path directory) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedDir = directory.toAbsolutePath().normalize();
        if (!normalizedDir.startsWith(normalizedRoot)) {
            throw new IOException("Output directory escapes capture root");
        }
        ensureNoSymbolicLinkComponents(normalizedRoot, normalizedDir);
        if (!Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(normalizedRoot);
        }
        if (isLinkOrReparse(normalizedRoot) || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Symbolic links or junctions are not allowed as capture root");
        }
        if (!normalizedDir.equals(normalizedRoot)) {
            Path current = normalizedRoot;
            for (Path part : normalizedRoot.relativize(normalizedDir)) {
                current = current.resolve(part);
                if (isLinkOrReparse(current)) {
                    throw new IOException("Symbolic links or junctions are not allowed in capture output paths");
                }
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.createDirectory(current);
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // Concurrently created; verify below without following links.
                    }
                }
                if (isLinkOrReparse(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Symbolic links or junctions are not allowed in capture output paths");
                }
            }
        }
        Path realRoot = normalizedRoot.toRealPath();
        if (!normalizedDir.toRealPath().startsWith(realRoot)) {
            throw new IOException("Output path resolves outside capture root");
        }
    }

    static void writeAtomically(Path captureRoot, Path destination, String content) throws IOException {
        writeAtomically(captureRoot, destination, content, MAX_JSON_BYTES);
    }

    static void writeAtomically(Path captureRoot, Path destination, String content, long maxBytes) throws IOException {
        writeBytesAtomically(captureRoot, destination, content.getBytes(StandardCharsets.UTF_8), maxBytes);
    }

    static void writeBytesAtomically(Path captureRoot, Path destination, byte[] contentBytes, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes > MAX_HTML_CAPTURE_BYTES) {
            throw new IllegalArgumentException("Invalid atomic write limit: " + maxBytes);
        }
        Path root = captureRoot.toAbsolutePath().normalize();
        Path target = destination.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Output path escapes capture root");
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Output path has no parent directory");
        }
        createDirectoriesSafe(root, parent);
        if (isLinkOrReparse(target)) {
            throw new IOException("Symbolic links or junctions are not allowed as output targets");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output target is not a regular file");
        }
        if (contentBytes.length > maxBytes) {
            throw new IOException("JSON metadata exceeds the " + maxBytes + " byte limit; previous file was preserved");
        }
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                output.write(contentBytes);
            }
            if (isLinkOrReparse(target)) {
                throw new IOException("Symbolic links or junctions are not allowed as output targets");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void ensureNoSymbolicLinkComponents(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IOException("Output directory escapes capture root");
        }
        Path fsRoot = normalizedRoot.getRoot();
        if (fsRoot != null && !normalizedRoot.equals(fsRoot)) {
            Path current = fsRoot;
            for (Path part : fsRoot.relativize(normalizedRoot)) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    throw new IOException("Symbolic links are not allowed in capture output paths");
                }
            }
        }
        if (isLinkOrReparse(normalizedRoot)) {
            throw new IOException("Symbolic links or junctions are not allowed as capture root");
        }
        if (!normalizedTarget.equals(normalizedRoot)) {
            Path current = normalizedRoot;
            for (Path part : normalizedRoot.relativize(normalizedTarget)) {
                current = current.resolve(part);
                if (isLinkOrReparse(current)) {
                    throw new IOException("Symbolic links or junctions are not allowed in capture output paths");
                }
            }
        }
        if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS) && Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (!normalizedTarget.toRealPath().startsWith(normalizedRoot.toRealPath())) {
                throw new IOException("Output path resolves outside capture root");
            }
        }
    }

    static JSONObject frameSignature(int sequence, JSONArray messages) throws IOException {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, Integer.toString(SIGNATURE_VERSION));
        appendField(canonical, Integer.toString(sequence));
        boolean complete = messages != null && messages.length() > 0;
        int count = messages == null ? 0 : messages.length();
        appendField(canonical, Integer.toString(count));
        for (int i = 0; i < count; i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                complete = false;
                appendField(canonical, "");
                continue;
            }
            appendField(canonical, message.optString("identitySha256", ""));
            complete &= message.optBoolean("complete", false);
        }
        return new JSONObject()
                .put("signatureVersion", SIGNATURE_VERSION)
                .put("sequence", sequence)
                .put("complete", complete)
                .put("identitySha256", sha256Text(canonical.toString()));
    }
    static String sha256Text(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    static String sha256Bytes(byte[] value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return toHex(digest.digest(value == null ? new byte[0] : value));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static void appendField(StringBuilder canonical, String field) {
        String value = field == null ? "" : field;
        canonical.append(value.length()).append(':').append(value).append('\n');
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isSafeDirectory(Path path) {
        try {
            return path != null && !isLinkOrReparse(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isSafeRegularFile(Path path) {
        try {
            return path != null && !isLinkOrReparse(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}
