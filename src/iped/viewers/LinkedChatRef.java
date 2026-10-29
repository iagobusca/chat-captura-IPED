package iped.viewers;

import iped.data.IItem;

/**
 * Referencia a um CHAT vinculado por {@link ChatAnnotation}.
 *
 * O que se vincula e a conversa inteira (por exemplo um chat de WhatsApp do caso),
 * nao uma pessoa. O {@code captureFolder} e resolvido na exportacao, porque a
 * captura do chat vinculado pode ser criada depois da anotacao.
 *
 * O {@code item} e apenas em memoria: quando o chat vem da lista do caso, o item ja
 * esta em maos e nao ha necessidade de consultar o indice de novo para abrir a
 * janela de captura. Nunca e gravado no {@code anotacoes.json}.
 */
class LinkedChatRef {

    String chatRefId = ""; //$NON-NLS-1$
    String title = ""; //$NON-NLS-1$
    String origin = ChatAnnotationStore.ORIGIN_CASE; //$NON-NLS-1$
    String sourceName = ""; //$NON-NLS-1$
    String chatKey = ""; //$NON-NLS-1$
    String captureFolder = ""; //$NON-NLS-1$
    String participants = ""; //$NON-NLS-1$
    transient IItem item;

    LinkedChatRef() {
    }

    LinkedChatRef(String chatRefId, String title) {
        this.chatRefId = safe(chatRefId);
        this.title = safe(title);
    }

    LinkedChatRef copy() {
        LinkedChatRef other = new LinkedChatRef(chatRefId, title);
        other.origin = origin;
        other.sourceName = sourceName;
        other.chatKey = chatKey;
        other.captureFolder = captureFolder;
        other.participants = participants;
        other.item = item;
        return other;
    }

    boolean hasItem() {
        return item != null;
    }

    /** true quando o chat veio do caso e pode ser aberto sem nova consulta. */
    boolean hasChat() {
        return item != null || (chatKey != null && !chatKey.trim().isEmpty());
    }

    String key() {
        return chatRefId + "|" + title; //$NON-NLS-1$
    }

    boolean sameChat(LinkedChatRef other) {
        return other != null && key().equals(other.key());
    }

    boolean hasCapture() {
        return captureFolder != null && !captureFolder.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim(); //$NON-NLS-1$
    }
}