package iped.viewers;

/**
 * Resultado da pesquisa por texto dentro de um chat.
 *
 * Guarda apenas o necessario para localizar a mensagem no DOM e mostrar um
 * trecho na lista: o texto completo continua no WebView.
 */
class ChatSearchHit {

    /** id do elemento da mensagem no HTML do chat. */
    final String messageId;

    /** message, date, image, audio, video ou file. */
    final String type;

    /** Trecho curto do texto em volta do termo encontrado. */
    final String snippet;

    ChatSearchHit(String messageId, String type, String snippet) {
        this.messageId = messageId;
        this.type = type;
        this.snippet = snippet;
    }

    static String typeLabel(String type) {
        if (type == null) {
            return ""; //$NON-NLS-1$
        }
        switch (type) {
        case "date": //$NON-NLS-1$
            return "data"; //$NON-NLS-1$
        case "image": //$NON-NLS-1$
            return "imagem"; //$NON-NLS-1$
        case "audio": //$NON-NLS-1$
            return "audio"; //$NON-NLS-1$
        case "video": //$NON-NLS-1$
            return "video"; //$NON-NLS-1$
        case "file": //$NON-NLS-1$
            return "arquivo"; //$NON-NLS-1$
        default:
            return type;
        }
    }

    /** Texto exibido na lista de resultados. */
    String display() {
        String label = typeLabel(type);
        return label.isEmpty() ? snippet : "[" + label + "] " + snippet; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public String toString() {
        return display();
    }
}