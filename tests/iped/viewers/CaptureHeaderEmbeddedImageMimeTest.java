package iped.viewers;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Regression test for IPED 4.3.1 data:image/jpg headers containing PNG bytes. */
public final class CaptureHeaderEmbeddedImageMimeTest {
    private CaptureHeaderEmbeddedImageMimeTest() { }

    public static void main(String[] args) throws Exception {
        Method normalize = HtmlLinkViewer.class.getDeclaredMethod("validateCaptureHeaderHtml", String.class);
        normalize.setAccessible(true);
        Method verifierCheck = Class.forName("IntegrityVerifier").getDeclaredMethod("validateEmbeddedImage", String.class);
        verifierCheck.setAccessible(true);

        String pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/7ZcAAAAASUVORK5CYII=";
        String ipedUri = "data:image/jpg;base64," + pngBase64;
        String header = "<div id=\"capture-topbar\"><img src=\"" + ipedUri + "\"></div>";

        check((Boolean) verifierCheck.invoke(null, ipedUri), "verifier must retain compatibility with the known IPED 4.3.1 avatar encoding");
        String normalized = (String) normalize.invoke(null, header);
        String expectedUri = "data:image/png;base64," + pngBase64;
        check(normalized.contains("src=\"" + expectedUri + "\""), "capture header must declare the PNG MIME based on bytes");
        check((Boolean) verifierCheck.invoke(null, expectedUri), "verifier must accept the normalized embedded PNG");

        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img src=\"https://example.invalid/a.png\"></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img src=\"data:text/plain;base64," + pngBase64 + "\"></div>");
        System.out.println("CaptureHeaderEmbeddedImageMimeTest: PASS");
    }

    private static void assertHeaderRejected(Method normalize, String header) throws Exception {
        try {
            normalize.invoke(null, header);
            throw new AssertionError("unsafe or non-image header source was accepted");
        } catch (InvocationTargetException wrapper) {
            if (!(wrapper.getCause() instanceof IOException)) throw wrapper;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
