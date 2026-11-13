package iped.viewers;

/** Unit tests for S3 CSV formula injection neutralization. */
public final class CsvSanitizationTest {

    public static void main(String[] args) {
        assertEquals("\"\"", HtmlLinkViewer.sanitizeCsvCell(null), "Null cell");
        assertEquals("\"\"", HtmlLinkViewer.sanitizeCsvCell(""), "Empty cell");
        assertEquals("\"normal.jpg\"", HtmlLinkViewer.sanitizeCsvCell("normal.jpg"), "Normal filename");
        assertEquals("\"  normal.jpg\"", HtmlLinkViewer.sanitizeCsvCell("  normal.jpg"), "Leading spaces without formula");
        assertEquals("\"nome \"\"entre\"\" aspas\"", HtmlLinkViewer.sanitizeCsvCell("nome \"entre\" aspas"),
                "Double quote escaping");

        assertEquals("\"'=CMD|'/C calc'!A0\"", HtmlLinkViewer.sanitizeCsvCell("=CMD|'/C calc'!A0"), "Leading equals");
        assertEquals("\"'+SUM(1,2)\"", HtmlLinkViewer.sanitizeCsvCell("+SUM(1,2)"), "Leading plus");
        assertEquals("\"'-2+3\"", HtmlLinkViewer.sanitizeCsvCell("-2+3"), "Leading minus");
        assertEquals("\"'@SUM(A1:A2)\"", HtmlLinkViewer.sanitizeCsvCell("@SUM(A1:A2)"), "Leading at");
        assertEquals("\"'\t=1+1\"", HtmlLinkViewer.sanitizeCsvCell("\t=1+1"), "Leading tab");
        assertEquals("\"'\r=1+1\"", HtmlLinkViewer.sanitizeCsvCell("\r=1+1"), "Leading CR");
        assertEquals("\"'\n=1+1\"", HtmlLinkViewer.sanitizeCsvCell("\n=1+1"), "Leading LF");
        assertEquals("\"'  =1+1\"", HtmlLinkViewer.sanitizeCsvCell("  =1+1"), "Spaces before formula trigger");
        assertEquals("\"'   +cmd\"", HtmlLinkViewer.sanitizeCsvCell("   +cmd"), "Spaces before plus");
        assertEquals("\"'\tplain\"", HtmlLinkViewer.sanitizeCsvCell("\tplain"), "Leading tab before plain text");
        assertEquals("\"'\r\n=1\"", HtmlLinkViewer.sanitizeCsvCell("\r\n=1"), "CRLF handled safely");
        System.out.println("CsvSanitizationTest: PASS");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected + "> but got <" + actual + ">");
        }
    }
}
