package iped.viewers;

import java.util.ArrayList;
import java.util.List;

/**
 * Classificacao das anotacoes do chat atual contra o intervalo marcado.
 *
 * Validacao estrita: a anotacao so vale se o indice da mensagem estiver entre o
 * inicio e o fim. O que fica fora e ignorado quando o Start e confirmado.
 */
class AnnotationPreflight {

/** Resumo por anotacao, usado no dialogo para o analista auditar. */
    static class Item {
        final ChatAnnotation annotation;
        final String messageId;
        final int messageIndex;
        final String range;
        final String summary;

        Item(ChatAnnotation annotation, int messageIndex, String range, String summary) {
            this.annotation = annotation;
            this.messageId = annotation.messageId;
            this.messageIndex = messageIndex;
            this.range = range;
            this.summary = summary;
        }
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> inRange = new ArrayList<>();
    private final List<Item> outOfRange = new ArrayList<>();
    private final List<Item> orphans = new ArrayList<>();
    private int notes;
    private int linkedMessages;
    private int contacts;

    void add(ChatAnnotation annotation, int messageIndex) {
        boolean hasNote = annotation.hasNote();
        boolean hasLinks = annotation.hasLinks();
        if (hasNote) {
            notes++;
        }
        if (hasLinks) {
            linkedMessages++;
            contacts += annotation.links.size();
        }
        if (hasNote || hasLinks) {
            Item item = new Item(annotation, messageIndex, toLabel(annotation.range), buildSummary(annotation));
            all.add(item);
            switch (annotation.range) {
                case ChatAnnotation.RANGE_OUT:
                    outOfRange.add(item);
                    break;
                case ChatAnnotation.RANGE_ORPHAN:
                    orphans.add(item);
                    break;
                default:
                    inRange.add(item);
                    break;
            }
        }
    }

    /**
     * @return true quando existe alguma anotacao, o que faz o pre-flight valer a pena.
     */
    boolean hasContent() {
        return !all.isEmpty();
    }

    int notesCount() {
        return notes;
    }

    int linkedMessagesCount() {
        return linkedMessages;
    }

    int contactsCount() {
        return contacts;
    }

    int inRangeCount() {
        return inRange.size();
    }

    int outOfRangeCount() {
        return outOfRange.size();
    }

    int orphanCount() {
        return orphans.size();
    }

    boolean hasWarning() {
        return !outOfRange.isEmpty();
    }

    List<Item> getAll() {
        return all;
    }

    List<Item> getOutOfRange() {
        return outOfRange;
    }

    List<Item> getOrphans() {
        return orphans;
    }

    /**
     * Constroi o conjunto que vai para a saida. As anotacoes fora do intervalo e as
     * orfas nao entram.
     */
    ChatAnnotationSet toAnnotationSet() {
        ChatAnnotationSet set = new ChatAnnotationSet();
        set.toggleState = "included"; //$NON-NLS-1$
        for (Item item : inRange) {
            set.items.add(item.annotation);
        }
        set.ignoredOutOfRange = outOfRange.size();
        set.orphans = orphans.size();
        return set;
    }

    private static String toLabel(int range) {
        switch (range) {
            case ChatAnnotation.RANGE_OUT:
                return "out"; //$NON-NLS-1$
            case ChatAnnotation.RANGE_ORPHAN:
                return "orphan"; //$NON-NLS-1$
            default:
                return "in"; //$NON-NLS-1$
        }
    }

    private static String buildSummary(ChatAnnotation annotation) {
        StringBuilder out = new StringBuilder();
        if (annotation.hasNote()) {
            String note = annotation.noteText.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$
            out.append("NOTA: "); //$NON-NLS-1$
            out.append(note.length() > 90 ? note.substring(0, 90) + "..." : note); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (annotation.hasLinks()) {
            if (out.length() > 0) {
                out.append(" | "); //$NON-NLS-1$
            }
out.append("CHAT: "); //$NON-NLS-1$
                for (int i = 0; i < annotation.links.size(); i++) {
                    if (i > 0) {
                        out.append(", "); //$NON-NLS-1$
                    }
                    out.append(annotation.links.get(i).title);
                }
        }
        return out.toString();
    }
}