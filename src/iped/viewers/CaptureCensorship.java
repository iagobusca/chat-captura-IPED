package iped.viewers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/** Censura visual (CSS) das capturas html-message-v1. Sem JavaScript; ver SPEC_CENSURA_IMAGENS_HTML_MESSAGE.md. */
final class CaptureCensorship {
    static final String ATTRIBUTE = "data-capture-censored"; //$NON-NLS-1$
    private static final String LINK_CLASS = "class=\"capture-media-link\""; //$NON-NLS-1$
    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>"); //$NON-NLS-1$
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b[^>]*>"); //$NON-NLS-1$
    private static final Pattern PREVIEW_HREF = Pattern.compile("(?i)\\bhref=\"#preview-([0-9a-f]{64})\""); //$NON-NLS-1$
    private static final Pattern ATTACHMENT_SRC = Pattern.compile("(?i)\\bsrc=\"attachments/([0-9a-f]{64})\\.[a-z0-9]{1,10}\""); //$NON-NLS-1$

    private CaptureCensorship() {}

    /**
     * Marca, no fragmento de UMA mensagem, as âncoras e imagens que referenciam algum SHA-256 censurado.
     * Âncora: class="capture-media-link capture-censored" + data-capture-censored="1".
     * Imagem: data-capture-censored="1" (cobre imagem fora de âncora e a miniatura dentro dela).
     * Fail-closed: se algum SHA-256 censurado não receber marca, lança IOException.
     */
    static String mark(String fragment, Set<String> censoredSha256) throws IOException {
        if (censoredSha256 == null || censoredSha256.isEmpty()) return fragment;
        Set<String> normalizedCensored = new HashSet<String>();
        for (String sha : censoredSha256) {
            if (sha != null && !sha.isEmpty()) normalizedCensored.add(sha.toLowerCase(Locale.ROOT));
        }
        if (normalizedCensored.isEmpty()) return fragment;

        Set<String> applied = new HashSet<String>();
        Matcher anchors = ANCHOR.matcher(fragment);
        StringBuffer withAnchors = new StringBuffer();
        while (anchors.find()) {
            String attrs = anchors.group(1), inner = anchors.group(2);
            Set<String> hits = referenced(attrs + " " + inner); //$NON-NLS-1$
            hits.retainAll(normalizedCensored);
            String replacement = anchors.group();
            if (!hits.isEmpty() && attrs.contains(LINK_CLASS)) {
                if (!attrs.contains(ATTRIBUTE)) {
                    replacement = "<a" + attrs.replace(LINK_CLASS, //$NON-NLS-1$
                            "class=\"capture-media-link capture-censored\" " + ATTRIBUTE + "=\"1\"") + ">" + inner + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                } else if (!attrs.contains("capture-censored")) { //$NON-NLS-1$
                    replacement = "<a" + attrs.replace(LINK_CLASS, //$NON-NLS-1$
                            "class=\"capture-media-link capture-censored\"") + ">" + inner + "</a>"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                applied.addAll(hits);
            }
            anchors.appendReplacement(withAnchors, Matcher.quoteReplacement(replacement));
        }
        anchors.appendTail(withAnchors);

        Matcher images = IMG.matcher(withAnchors.toString());
        StringBuffer result = new StringBuffer();
        while (images.find()) {
            String tag = images.group(), replacement = tag;
            Matcher src = ATTACHMENT_SRC.matcher(tag);
            if (src.find()) {
                String sha = src.group(1).toLowerCase(Locale.ROOT);
                if (normalizedCensored.contains(sha)) {
                    if (!tag.contains(ATTRIBUTE)) replacement = tag.replaceFirst("(?is)^<img\\b", "<img " + ATTRIBUTE + "=\"1\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    applied.add(sha);
                }
            }
            images.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        images.appendTail(result);
        if (!applied.containsAll(normalizedCensored)) {
            throw new IOException("Imagem censurada sem elemento HTML para receber a marcação; captura interrompida."); //$NON-NLS-1$
        }
        return result.toString();
    }

    private static Set<String> referenced(String markup) {
        Set<String> found = new HashSet<String>();
        Matcher href = PREVIEW_HREF.matcher(markup);
        while (href.find()) found.add(href.group(1).toLowerCase(Locale.ROOT));
        Matcher src = ATTACHMENT_SRC.matcher(markup);
        while (src.find()) found.add(src.group(1).toLowerCase(Locale.ROOT));
        return found;
    }

    /** Regras inseridas na 2ª folha <style>; mesmos filtros do visualizador ao vivo (censorVisualTarget). */
    static String css(int blurPixels) {
        int px = Math.max(1, Math.min(40, blurPixels));
        String filter = "blur(" + px + "px) saturate(.25) brightness(.55)"; //$NON-NLS-1$ //$NON-NLS-2$
        return ".capture-censored{position:relative;display:inline-block;overflow:hidden;border-radius:6px;vertical-align:top}" //$NON-NLS-1$
                + ".capture-censored img{display:block;transform:scale(1.06)}" //$NON-NLS-1$
                + ".capture-censored img,img[data-capture-censored]{-webkit-filter:" + filter + ";filter:" + filter + "}" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + ".capture-censored::after{content:\"Censurado - clique para ampliar\";position:absolute;left:0;right:0;bottom:0;z-index:1;" //$NON-NLS-1$
                + "box-sizing:border-box;padding:3px 6px;background:rgba(0,0,0,.68);color:#fff;font:600 11px/1.3 Arial,sans-serif;text-align:center;pointer-events:none}"; //$NON-NLS-1$
    }

    /** Resumo canônico da apresentação efetiva. Vazio quando nada foi censurado (equivale a captura sem censura). */
    static String presentationDigest(int blurPixels, Map<String, Set<String>> messageKeysBySha256) throws IOException {
        if (messageKeysBySha256 == null || messageKeysBySha256.isEmpty()) return ""; //$NON-NLS-1$
        StringBuilder canonical = new StringBuilder("censorship-presentation-v1\n").append(blurPixels).append('\n'); //$NON-NLS-1$
        for (Map.Entry<String, Set<String>> entry : new TreeMap<String, Set<String>>(messageKeysBySha256).entrySet()) {
            canonical.append(entry.getKey()).append(':');
            for (String key : new TreeSet<String>(entry.getValue())) canonical.append(key).append(',');
            canonical.append('\n');
        }
        return CaptureIntegrity.sha256Text(canonical.toString());
    }

    /** Bloco "censorship" do manifest.json. Não inclui o caminho da pasta de referência. */
    static JSONObject manifestRecord(int similarityPercent, int levelPercent, int blurPixels, int referenceImageCount,
            int censoredMessageCount, Map<String, Set<String>> messageKeysBySha256) throws IOException {
        JSONArray images = new JSONArray();
        if (messageKeysBySha256 != null) {
            for (Map.Entry<String, Set<String>> entry : new TreeMap<String, Set<String>>(messageKeysBySha256).entrySet()) {
                images.put(new JSONObject().put("sha256", entry.getKey()) //$NON-NLS-1$
                        .put("messageKeys", new JSONArray(new TreeSet<String>(entry.getValue())))); //$NON-NLS-1$
            }
        }
        return new JSONObject().put("schemaVersion", 1).put("mode", "css-blur").put("source", "reference-folder") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                .put("similarityPercent", similarityPercent).put("levelPercent", levelPercent).put("cssPixels", blurPixels) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("referenceImageCount", referenceImageCount) //$NON-NLS-1$
                .put("censoredImageCount", images.length()).put("censoredMessageCount", censoredMessageCount) //$NON-NLS-1$ //$NON-NLS-2$
                .put("images", images) //$NON-NLS-1$
                .put("presentationDigest", presentationDigest(blurPixels, messageKeysBySha256)) //$NON-NLS-1$
                .put("notice", "Supressão visual por CSS; os bytes originais permanecem em attachments/ e abrem no modal."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
