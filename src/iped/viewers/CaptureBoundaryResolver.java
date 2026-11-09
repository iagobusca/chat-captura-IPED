package iped.viewers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Resolves capture boundaries against the ordered IPED 4.3.1 conversation rows. */
final class CaptureBoundaryResolver {

    private static final Pattern NUMERIC_MESSAGE_ID = Pattern.compile("[0-9]+"); //$NON-NLS-1$

    enum Role { START, END }

    static final class Row {
        final String messageId;
        final List<String> dateIds;

        Row(String messageId, List<String> dateIds) {
            this.messageId = messageId == null ? "" : messageId;
            this.dateIds = dateIds == null ? Collections.emptyList() : new ArrayList<>(dateIds);
        }
    }

    static final class Resolution {
        final String messageId;
        final String errorCode;
        final int rowIndex;

        private Resolution(String messageId, String errorCode, int rowIndex) {
            this.messageId = messageId;
            this.errorCode = errorCode;
            this.rowIndex = rowIndex;
        }

        static Resolution success(String id, int index) { return new Resolution(id, "", index); }
        static Resolution error(String code) { return new Resolution("", code, -1); }
        boolean isValid() { return errorCode.isEmpty(); }
    }

    private CaptureBoundaryResolver() { }

    static Resolution resolve(List<Row> rows, String requestedId, Role role) {
        if (rows == null || requestedId == null || requestedId.isBlank()) return Resolution.error("marker_missing");
        List<Integer> messageMatches = new ArrayList<>();
        List<Integer> dateMatches = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (requestedId.equals(row.messageId)) messageMatches.add(i);
            for (String dateId : row.dateIds) if (requestedId.equals(dateId)) dateMatches.add(i);
        }
        if (!messageMatches.isEmpty()) {
            if (messageMatches.size() != 1) return Resolution.error("marker_ambiguous");
            if (!NUMERIC_MESSAGE_ID.matcher(requestedId).matches()) return Resolution.error("marker_not_message");
            return Resolution.success(requestedId, messageMatches.get(0));
        }
        if (!dateMatches.isEmpty()) {
            if (dateMatches.size() != 1) return Resolution.error("marker_ambiguous");
            if (role == Role.END) return Resolution.error("end_date_not_supported");
            for (int i = dateMatches.get(0) + 1; i < rows.size(); i++) {
                String candidate = rows.get(i).messageId;
                if (candidate.isEmpty()) continue;
                int count = 0;
                for (Row row : rows) if (candidate.equals(row.messageId)) count++;
                if (count != 1) return Resolution.error("marker_ambiguous");
                return Resolution.success(candidate, i);
            }
            return Resolution.error("start_date_no_message");
        }
        return Resolution.error(NUMERIC_MESSAGE_ID.matcher(requestedId).matches() ? "marker_missing" : "marker_not_message");
    }

    static String validateRange(List<Row> rows, Resolution start, Resolution end) {
        if (start == null || !start.isValid()) return start == null ? "start_marker_missing" : start.errorCode;
        if (end == null || !end.isValid()) return end == null ? "end_marker_missing" : end.errorCode;
        if (start.rowIndex > end.rowIndex) return "invalid_order";
        return "";
    }
}
