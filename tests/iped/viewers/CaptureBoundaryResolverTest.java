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

        List<CaptureBoundaryResolver.Row> callAndTimestampRows = Arrays.asList(
                new CaptureBoundaryResolver.Row("2546_call:1AAC54E6A374D0958B8A3E43671CD727",
                        Collections.singletonList("iped-capture-date-call-ts")),
                new CaptureBoundaryResolver.Row("203580",
                        Collections.singletonList("iped-capture-date-msg-ts")),
                new CaptureBoundaryResolver.Row("2253_call:AE186FDC5C5D933331913C6F34F65F1B",
                        Collections.emptyList()),
                new CaptureBoundaryResolver.Row("-1_0",
                        Collections.emptyList()));
        CaptureBoundaryResolver.Resolution callStart = CaptureBoundaryResolver.resolve(callAndTimestampRows,
                "2546_call:1AAC54E6A374D0958B8A3E43671CD727", CaptureBoundaryResolver.Role.START);
        CaptureBoundaryResolver.Resolution callEnd = CaptureBoundaryResolver.resolve(callAndTimestampRows,
                "2253_call:AE186FDC5C5D933331913C6F34F65F1B", CaptureBoundaryResolver.Role.END);
        require(callStart.isValid() && "2546_call:1AAC54E6A374D0958B8A3E43671CD727".equals(callStart.messageId),
                "voice/video call record ID must be accepted as start marker");
        require(callEnd.isValid() && "2253_call:AE186FDC5C5D933331913C6F34F65F1B".equals(callEnd.messageId),
                "voice/video call record ID must be accepted as end marker");
        require(CaptureBoundaryResolver.validateRange(callAndTimestampRows, callStart, callEnd).isEmpty(),
                "range bounded by call records must be valid");
        CaptureBoundaryResolver.Resolution tsStart = CaptureBoundaryResolver.resolve(callAndTimestampRows,
                "iped-capture-date-call-ts", CaptureBoundaryResolver.Role.START);
        CaptureBoundaryResolver.Resolution tsEnd = CaptureBoundaryResolver.resolve(callAndTimestampRows,
                "iped-capture-date-msg-ts", CaptureBoundaryResolver.Role.END);
        require(tsStart.isValid() && "2546_call:1AAC54E6A374D0958B8A3E43671CD727".equals(tsStart.messageId),
                "timestamp inside call row must resolve to its parent call row");
        require(tsEnd.isValid() && "203580".equals(tsEnd.messageId),
                "timestamp inside message row must resolve to its parent message row as end marker");
        CaptureBoundaryResolver.Resolution deletedEnd = CaptureBoundaryResolver.resolve(callAndTimestampRows,
                "-1_0", CaptureBoundaryResolver.Role.END);
        require(deletedEnd.isValid() && "-1_0".equals(deletedEnd.messageId),
                "deleted/carved IPED message ID must be accepted");

        System.out.println("CaptureBoundaryResolverTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
