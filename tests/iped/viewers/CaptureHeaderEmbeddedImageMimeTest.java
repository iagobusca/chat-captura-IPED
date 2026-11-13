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
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img src='https://example.invalid/a.png'></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img src=https://example.invalid/a.png></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img src=\"data:text/plain;base64," + pngBase64 + "\"></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><div title=\">\"><script>alert(1)</script></div></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><img title='a' onclick='alert(1)' src=\"" + ipedUri + "\"></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><svg onload=\"alert(1)\"></svg></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><!--comment--></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"><div id=\"inner\"></div></div>");
        assertHeaderRejected(normalize, "<div id=\"capture-topbar\"></div><div>extra</div>");

        String complexHeader = "<div id=\"capture-topbar\"><div><img src='" + ipedUri
                + "' width='72' height=\"72\" alt=\"Avatar &quot;A&quot;\"></div><div><span class=\"left\">Chat</span></div></div>";
        String normalizedComplex = (String) normalize.invoke(null, complexHeader);
        check(normalizedComplex.contains("src=\"" + expectedUri + "\""),
                "single-quoted image src must be normalized to double-quoted PNG URI");

        Method validateSep = HtmlLinkViewer.class.getDeclaredMethod("validateCaptureSeparatorHtml", String.class);
        validateSep.setAccessible(true);
        String validSep = "<div class=\"linha\"><div class=\"date\" data-iped-capture-date=\"true\">2026-09-01</div></div>";
        check(validSep.equals(validateSep.invoke(null, validSep)), "valid date separator must pass");
        check("".equals(validateSep.invoke(null, "")), "empty separator must pass");
        assertHeaderRejected(validateSep, "<div class=\"date\" onclick=\"alert(1)\">2026-09-01</div>");
        assertHeaderRejected(validateSep, "<div id=\"sep-id\">2026-09-01</div>");
        assertHeaderRejected(validateSep, "<div class=\"date\"><img src=\"data:image/png;base64," + pngBase64 + "\"></div>");
        assertHeaderRejected(validateSep, "<div class=\"date\"><a href=\"https://evil.invalid\">click</a></div>");
        assertHeaderRejected(validateSep, "<svg onload=\"alert(1)\"></svg>");
        assertHeaderRejected(validateSep, "<div title=\">\"><script>alert(1)</script></div>");

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
