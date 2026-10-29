package iped.viewers;

/**
 * Resultado da regra de elegibilidade dos itens Notas e Link no menu de contexto.
 *
 * Validacao leniente: exige apenas que exista marca e que a mensagem esteja depois
 * do inicio (ou antes do fim, quando so existe fim). A checagem estrita do
 * intervalo completo acontece no pre-flight do Start.
 */
class AnnotationEligibility {

    final boolean eligible;
    final String reasonKey;

    private AnnotationEligibility(boolean eligible, String reasonKey) {
        this.eligible = eligible;
        this.reasonKey = reasonKey;
    }

    static AnnotationEligibility ok() {
        return new AnnotationEligibility(true, ""); //$NON-NLS-1$
    }

    static AnnotationEligibility blocked(String reasonKey) {
        return new AnnotationEligibility(false, reasonKey);
    }

    /**
     * @param messageId    mensagem sob o cursor
     * @param startId      inicio marcado, ou null
     * @param endId        fim marcado, ou null
     * @param messageIndex indice da mensagem no DOM, ou -1
     * @param startIndex   indice do inicio no DOM, ou -1
     * @param endIndex     indice do fim no DOM, ou -1
     * @param isDate       true quando e um separador de data
     */
    static AnnotationEligibility evaluate(String messageId, String startId, String endId, int messageIndex,
            int startIndex, int endIndex, boolean isDate) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return blocked("MenuClass.ChatCaptureNotesDisabledNoMark"); //$NON-NLS-1$
        }
        if (isDate) {
            return blocked("MenuClass.ChatCaptureNotesDisabledDate"); //$NON-NLS-1$
        }
        boolean hasStart = startId != null && !startId.trim().isEmpty();
        boolean hasEnd = endId != null && !endId.trim().isEmpty();
        if (!hasStart && !hasEnd) {
            return blocked("MenuClass.ChatCaptureNotesDisabledNoMark"); //$NON-NLS-1$
        }
        if (messageIndex < 0) {
            return blocked("MenuClass.ChatCaptureNotesDisabledNoMark"); //$NON-NLS-1$
        }
        if (hasStart && startIndex >= 0 && messageIndex <= startIndex) {
            return blocked("MenuClass.ChatCaptureNotesDisabledAboveStart"); //$NON-NLS-1$
        }
        if (hasEnd && endIndex >= 0 && messageIndex >= endIndex) {
            return blocked("MenuClass.ChatCaptureNotesDisabledBelowEnd"); //$NON-NLS-1$
        }
        return ok();
    }
}