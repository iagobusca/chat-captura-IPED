package iped.viewers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONObject;

/** Creates a real plugin integrity chain around a fixture migration artifact. */
public final class MigrationIntegrityHistoryHarness {
    private MigrationIntegrityHistoryHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path capture = Paths.get(args[0]);
        JSONObject first = CaptureIntegrity.integrityRecord(capture, null, "capture_created", "2.5.5-beta");
        JSONObject migrated = CaptureIntegrity.integrityRecord(capture, first,
                "legacy_screenshot_capture_migrated_to_html_message_v1", "2.5.5-beta");
        CaptureIntegrity.writeAtomically(capture, capture.resolve("integridade.json"),
                migrated.toString(2) + System.lineSeparator());
        System.out.println("MigrationIntegrityHistoryHarness: PASS");
    }
}
