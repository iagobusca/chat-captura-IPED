package iped.viewers;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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

    static JSONArray artifactInventory(Path captureRoot) throws IOException {
        Path root = captureRoot.toAbsolutePath().normalize();
        JSONArray inventory = new JSONArray();
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (!path.equals(root) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !AUDIT_FILE.equals(path.getFileName().toString())
                        && !"manifest.json".equals(path.getFileName().toString())
                        && !"integridade.json".equals(path.getFileName().toString())) {
                    if (files.size() >= MAX_INVENTORY_FILES) {
                        throw new IOException("Capture exceeds the inventory limit of " + MAX_INVENTORY_FILES + " files");
                    }
                    files.add(path);
                }
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
        long size = Files.size(path);
        if (size > MAX_JSON_BYTES) {
            throw new IOException("JSON metadata exceeds the limit of " + MAX_JSON_BYTES + " bytes: " + path);
        }
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((int) MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES || input.read() != -1) {
                throw new IOException("JSON metadata exceeds the limit of " + MAX_JSON_BYTES + " bytes: " + path);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
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
                .put("previousRecordHash", previousHash);
        event.put("recordHash", sha256Text(event.toString()));
        events.put(event);
        return new JSONObject().put("schemaVersion", 2).put("algorithm", HASH_ALGORITHM)
                .put("pluginVersion", pluginVersion)
                .put("contentRootHash", inventoryHash(current)).put("artifacts", current).put("events", events);
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
        Path root = rootPath.toAbsolutePath().normalize();
        Path relative = root.getFileSystem().getPath(relativePath.replace('\\', '/'));
        if (relative.isAbsolute()) {
            throw new IOException("Absolute paths are not allowed");
        }
        for (Path component : relative) {
            if ("..".equals(component.toString())) {
                throw new IOException("Parent path components are not allowed");
            }
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path escapes capture root");
        }
        Path current = root;
        for (Path part : root.relativize(resolved)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in capture paths");
            }
        }
        return resolved;
    }

    static void appendAudit(Path captureRoot, JSONObject event) throws IOException {
        Path root = captureRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        ensureNoSymbolicLinkComponents(root, root);
        JSONObject record = new JSONObject(event == null ? new JSONObject().toMap() : event.toMap());
        record.put("recordedAtUtc", Instant.now().toString());
        record.put("auditSchemaVersion", 1);
        Path log = root.resolve(AUDIT_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
                LinkOption.NOFOLLOW_LINKS)) {
            writer.write(record.toString());
            writer.newLine();
        }
    }

    static void writeAtomically(Path captureRoot, Path destination, String content) throws IOException {
        Path root = captureRoot.toAbsolutePath().normalize();
        Path target = destination.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Output path escapes capture root");
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Output path has no parent directory");
        }
        Files.createDirectories(parent);
        ensureNoSymbolicLinkComponents(root, parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
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
        Path current = normalizedRoot.getRoot();
        for (Path part : current.relativize(normalizedTarget)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in capture output paths");
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

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}
