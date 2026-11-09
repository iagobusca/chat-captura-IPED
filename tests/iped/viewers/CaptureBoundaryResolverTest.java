package iped.viewers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CaptureBoundaryResolverTest {
    private CaptureBoundaryResolverTest() { }

    public static void main(String[] args) {
        List<CaptureBoundaryResolver.Row> rows = Arrays.asList(
                new CaptureBoundaryResolver.Row("", Collections.singletonList("iped-capture-date-a")),
                new CaptureBoundaryResolver.Row("100", Collections.emptyList()),
                new CaptureBoundaryResolver.Row("101", Collections.emptyList()),
                new CaptureBoundaryResolver.Row("", Collections.singletonList("iped-capture-date-b")),
                new CaptureBoundaryResolver.Row("102", Collections.emptyList()),
                new CaptureBoundaryResolver.Row("", Collections.singletonList("iped-capture-date-last")));

        CaptureBoundaryResolver.Resolution dateStart = CaptureBoundaryResolver.resolve(rows,
                "iped-capture-date-a", CaptureBoundaryResolver.Role.START);
        CaptureBoundaryResolver.Resolution end = CaptureBoundaryResolver.resolve(rows, "101", CaptureBoundaryResolver.Role.END);
        require(dateStart.isValid() && "100".equals(dateStart.messageId), "date start must resolve to next message");
        require(CaptureBoundaryResolver.validateRange(rows, dateStart, end).isEmpty(), "message range must be valid");
        require("end_date_not_supported".equals(CaptureBoundaryResolver.resolve(rows,
                "iped-capture-date-b", CaptureBoundaryResolver.Role.END).errorCode), "date end must be rejected");
        require("start_date_no_message".equals(CaptureBoundaryResolver.resolve(rows,
                "iped-capture-date-last", CaptureBoundaryResolver.Role.START).errorCode), "date without next message must fail");
        require("invalid_order".equals(CaptureBoundaryResolver.validateRange(rows,
                CaptureBoundaryResolver.resolve(rows, "102", CaptureBoundaryResolver.Role.START),
                CaptureBoundaryResolver.resolve(rows, "100", CaptureBoundaryResolver.Role.END))), "reversed range must fail");

        List<CaptureBoundaryResolver.Row> duplicates = Arrays.asList(
                new CaptureBoundaryResolver.Row("100", Collections.emptyList()),
                new CaptureBoundaryResolver.Row("100", Collections.emptyList()));
        require("marker_ambiguous".equals(CaptureBoundaryResolver.resolve(duplicates,
                "100", CaptureBoundaryResolver.Role.START).errorCode), "duplicate message ID must fail as ambiguous");
        require("marker_not_message".equals(CaptureBoundaryResolver.resolve(rows,
                "topbar", CaptureBoundaryResolver.Role.START).errorCode), "non-message ID must be rejected");
        require("marker_missing".equals(CaptureBoundaryResolver.resolve(rows,
                "999", CaptureBoundaryResolver.Role.END).errorCode), "missing numeric message marker must fail closed");
        System.out.println("CaptureBoundaryResolverTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
