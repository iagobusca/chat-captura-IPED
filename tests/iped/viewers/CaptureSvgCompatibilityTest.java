package iped.viewers;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Runtime regression for the Xerces 2.12.2 parser bundled with IPED 4.3.1. */
public final class CaptureSvgCompatibilityTest {
    private CaptureSvgCompatibilityTest() { }

    public static void main(String[] args) throws Exception {
        Class<?> viewer = Class.forName("iped.viewers.HtmlLinkViewer");
        Method sanitize = viewer.getDeclaredMethod("sanitizeCaptureThemeCss", String.class);
        sanitize.setAccessible(true);

        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0\"/></svg>";
        String data = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        String sanitized = (String) sanitize.invoke(null, "#topbar{background-image:url('" + data + "')}");
        if (!sanitized.contains("#capture-topbar") || !sanitized.contains("data:image/svg+xml;base64,")) {
            throw new AssertionError("SVG válido não foi preservado no CSS sanitizado.");
        }

        assertRejected(sanitize, "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///C:/Windows/win.ini'>]><svg xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>");
        assertRejected(sanitize, "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>");
        System.out.println("PASS: SVG válido aceito pelo Xerces do IPED; DTD e evento rejeitados.");
    }

    private static void assertRejected(Method sanitize, String svg) throws Exception {
        String data = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        try {
            sanitize.invoke(null, "body{background-image:url('" + data + "')}");
            throw new AssertionError("SVG ativo ou com DTD foi aceito.");
        } catch (InvocationTargetException wrapper) {
            if (!(wrapper.getCause() instanceof IOException)) throw wrapper;
        }
    }
}
