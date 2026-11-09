import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PublicManifestComparisonHarness {
    public static void main(String[] args) throws Exception {
        String reference = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
        System.out.print(IntegrityVerifier.comparePublicMessageManifest(Paths.get(args[0]), "fixture", reference));
    }
}
