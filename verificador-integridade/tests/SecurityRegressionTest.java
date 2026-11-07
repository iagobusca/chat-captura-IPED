import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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
