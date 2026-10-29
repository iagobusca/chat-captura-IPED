package iped.viewers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Conjunto de anotacoes que vai para a saida de uma captura.
 *
 * Este e o unico objeto que os writers consultam. Quando a referencia e nula,
 * nenhum bloco de anotacoes e gravado e a saida fica igual a da versao 1 do
 * plugin.
 */
class ChatAnnotationSet {

    final List<ChatAnnotation> items = new ArrayList<>();
    int ignoredOutOfRange = 0;
    int orphans = 0;
    String toggleState = "included"; //$NON-NLS-1$

    boolean isEmpty() {
        return items.isEmpty();
    }

    int notesCount() {
        int count = 0;
        for (ChatAnnotation item : items) {
            if (item.hasNote()) {
                count++;
            }
        }
        return count;
    }

    int annotatedMessagesCount() {
        return items.size();
    }

    int linksCount() {
        int count = 0;
        for (ChatAnnotation item : items) {
            if (item.hasLinks()) {
                count++;
            }
        }
        return count;
    }

    int contactsCount() {
        Set<String> unique = new LinkedHashSet<>();
        for (ChatAnnotation item : items) {
            for (LinkedChatRef link : item.links) {
                unique.add(link.key());
            }
        }
        return unique.size();
    }

    ChatAnnotationSet copy() {
        ChatAnnotationSet other = new ChatAnnotationSet();
        other.ignoredOutOfRange = ignoredOutOfRange;
        other.orphans = orphans;
        other.toggleState = toggleState;
        for (ChatAnnotation item : items) {
            ChatAnnotation clone = new ChatAnnotation(item.messageId);
            clone.noteText = item.noteText;
            clone.noteAuthor = item.noteAuthor;
            clone.noteCreatedAt = item.noteCreatedAt;
            clone.noteUpdatedAt = item.noteUpdatedAt;
            clone.range = item.range;
            for (LinkedChatRef link : item.links) {
                clone.links.add(link.copy());
            }
            other.items.add(clone);
        }
        return other;
    }
}