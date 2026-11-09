import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Regression for IPED 4.3.1 avatar PNGs mislabeled as image/jpg. */
public final class EmbeddedImageMimeCompatibilityTest {
    private EmbeddedImageMimeCompatibilityTest() { }

    public static void main(String[] args) throws Exception {
        Method validate = IntegrityVerifier.class.getDeclaredMethod("validateEmbeddedImage", String.class);
        validate.setAccessible(true);
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/7ZcAAAAASUVORK5CYII=";
        check(validate, "data:image/jpg;base64," + png, true, "legacy image/jpg with PNG bytes");
        check(validate, "data:image/png;base64," + png, true, "correct PNG MIME");
        check(validate, "data:image/jpeg;base64," + png, false, "JPEG MIME with PNG bytes");
        check(validate, "data:image/jpg;base64," + Base64.getEncoder().encodeToString("not an image".getBytes(StandardCharsets.US_ASCII)), false, "invalid raster bytes");
        check(validate, "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg/>".getBytes(StandardCharsets.US_ASCII)), false, "active image type");
        System.out.println("EmbeddedImageMimeCompatibilityTest: PASS");
    }

    private static void check(Method method, String uri, boolean expected, String label) throws Exception {
        boolean actual = (Boolean) method.invoke(null, uri);
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
