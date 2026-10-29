package iped.viewers;

import java.util.ArrayList;
import java.util.List;

/**
 * Anotacao do analista sobre uma mensagem do chat: uma nota opcional e uma
 * lista opcional de CHATS vinculados.
 *
 * Nota e links sao independentes: remover a nota preserva os links e vice-versa.
 */
class ChatAnnotation {

    static final int RANGE_IN = 0;
    static final int RANGE_OUT = 1;
    static final int RANGE_ORPHAN = 2;

    final String messageId;
    String noteText = ""; //$NON-NLS-1$
    String noteAuthor = ""; //$NON-NLS-1$
    String noteCreatedAt = ""; //$NON-NLS-1$
    String noteUpdatedAt = ""; //$NON-NLS-1$
    final List<LinkedChatRef> links = new ArrayList<>();
    int range = RANGE_IN;

    ChatAnnotation(String messageId) {
        this.messageId = messageId == null ? "" : messageId; //$NON-NLS-1$
    }

    boolean hasNote() {
        return noteText != null && !noteText.trim().isEmpty();
    }

    boolean hasLinks() {
        return !links.isEmpty();
    }

    boolean isEmpty() {
        return !hasNote() && !hasLinks();
    }

    LinkedChatRef findLink(String chatKey) {
        if (chatKey == null) {
            return null;
        }
        for (LinkedChatRef link : links) {
            if (link.key().equals(chatKey)) {
                return link;
            }
        }
        return null;
    }

    boolean addLink(LinkedChatRef chat) {
        if (chat == null || chat.title.isEmpty()) {
            return false;
        }
        if (findLink(chat.key()) != null) {
            return false;
        }
        links.add(chat);
        return true;
    }

    boolean removeLink(String chatKey) {
        LinkedChatRef existing = findLink(chatKey);
        return existing != null && links.remove(existing);
    }

    void removeAllLinks() {
        links.clear();
    }
}