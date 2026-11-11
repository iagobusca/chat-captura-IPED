package iped.viewers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public final class CaptureCensorshipTest {
    private static final String SHA_E = "e28257a60ffca0c5c99b4cf2eda215e9072dc1e343dab5c4937fa2286c525e48";
    private static final String SHA_T = "2c83f341e83bea245c5e0e933abaddc1fa2b0f1778dbb4c50481a2f5e364811a";
    private static final String SHA_X = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private CaptureCensorshipTest() {}

    public static void main(String[] args) throws Exception {
        marksWhatsAppAnchorByOriginalResourceHash();
        marksWhatsAppAnchorAndImgByThumbnailHash();
        marksStandaloneLinkedImage();
        returnsIdenticalFragmentWhenSetIsEmpty();
        failsClosedWhenCensoredHashHasNoTargetElement();
        doesNotDuplicateAttributeOnPreMarkedElement();
        doesNotTouchUnmatchedMessages();
        cssContainsExpectedBlurAndNoActiveRules();
        presentationDigestIsCanonicalAndOrderIndependent();
        manifestRecordIsOrderedAndExcludesFolderPath();
        System.out.println("CaptureCensorshipTest: PASS");
    }

    private static void marksWhatsAppAnchorByOriginalResourceHash() throws Exception {
        String html = "<div class=\"linha\"><a href=\"#preview-" + SHA_E
                + "\" class=\"capture-media-link\" title=\"foto.jpg\"><img class=\"thumb\" src=\"attachments/"
                + SHA_T + ".jpg\"></a></div>";
        String marked = CaptureCensorship.mark(html, Collections.singleton(SHA_E));
        check(marked.contains("class=\"capture-media-link capture-censored\" data-capture-censored=\"1\""),
                "anchor must receive capture-censored class and attribute");
        check(!marked.contains("<img data-capture-censored=\"1\""),
                "thumbnail img should not be marked directly when only resource hash matched");
    }

    private static void marksWhatsAppAnchorAndImgByThumbnailHash() throws Exception {
        String html = "<div class=\"linha\"><a href=\"#preview-" + SHA_E
                + "\" class=\"capture-media-link\" title=\"foto.jpg\"><img class=\"thumb\" src=\"attachments/"
                + SHA_T + ".jpg\"></a></div>";
        String marked = CaptureCensorship.mark(html, Collections.singleton(SHA_T));
        check(marked.contains("class=\"capture-media-link capture-censored\" data-capture-censored=\"1\""),
                "anchor must be marked when inner thumbnail matched");
        check(marked.contains("<img data-capture-censored=\"1\" class=\"thumb\""),
                "inner img must also receive data-capture-censored when thumbnail matched");
    }

    private static void marksStandaloneLinkedImage() throws Exception {
        String html = "<a class=\"capture-media-link\" href=\"#preview-" + SHA_T
                + "\"><img src=\"attachments/" + SHA_T + ".jpg\"></a>";
        String marked = CaptureCensorship.mark(html, Collections.singleton(SHA_T));
        check(marked.contains("class=\"capture-media-link capture-censored\" data-capture-censored=\"1\""),
                "standalone anchor must be marked");
        check(marked.contains("<img data-capture-censored=\"1\" src=\"attachments/" + SHA_T + ".jpg\">"),
                "standalone img must be marked");
    }

    private static void returnsIdenticalFragmentWhenSetIsEmpty() throws Exception {
        String html = "<div class=\"linha\"><img src=\"attachments/" + SHA_T + ".jpg\"></div>";
        equal(html, CaptureCensorship.mark(html, Collections.<String>emptySet()), "empty set must not alter fragment");
        equal(html, CaptureCensorship.mark(html, null), "null set must not alter fragment");
    }

    private static void failsClosedWhenCensoredHashHasNoTargetElement() {
        String html = "<div class=\"linha\"><img src=\"attachments/" + SHA_T + ".jpg\"></div>";
        boolean failed = false;
        try {
            CaptureCensorship.mark(html, Collections.singleton(SHA_X));
        } catch (IOException expected) {
            failed = true;
        }
        check(failed, "missing target element for censored hash must throw IOException");
    }

    private static void doesNotDuplicateAttributeOnPreMarkedElement() throws Exception {
        String html = "<img data-capture-censored=\"1\" src=\"attachments/" + SHA_T + ".jpg\">";
        String marked = CaptureCensorship.mark(html, Collections.singleton(SHA_T));
        int first = marked.indexOf("data-capture-censored");
        int last = marked.lastIndexOf("data-capture-censored");
        check(first >= 0 && first == last, "data-capture-censored attribute must appear exactly once");
    }

    private static void doesNotTouchUnmatchedMessages() throws Exception {
        String html = "<div class=\"linha\"><a href=\"#preview-" + SHA_E
                + "\" class=\"capture-media-link\" title=\"x\"><img src=\"attachments/" + SHA_T + ".jpg\"></a></div>";
        String marked = CaptureCensorship.mark(html, Collections.<String>emptySet());
        equal(html, marked, "unmatched message must remain byte-for-byte identical");
    }

    private static void cssContainsExpectedBlurAndNoActiveRules() {
        String css22 = CaptureCensorship.css(22);
        String css5 = CaptureCensorship.css(5);
        check(css22.contains("blur(22px) saturate(.25) brightness(.55)"), "css(22) must contain 22px blur");
        check(css5.contains("blur(5px) saturate(.25) brightness(.55)"), "css(5) must contain 5px blur");
        for (String forbidden : Arrays.asList("url(", "@import", "expression(", "behavior:", "</style", "javascript:")) {
            check(!css22.toLowerCase().contains(forbidden), "CSS must not contain forbidden token: " + forbidden);
        }
    }

    private static void presentationDigestIsCanonicalAndOrderIndependent() throws Exception {
        equal("", CaptureCensorship.presentationDigest(22, Collections.<String, Set<String>>emptyMap()),
                "empty map must produce empty digest");
        Map<String, Set<String>> first = new LinkedHashMap<String, Set<String>>();
        first.put(SHA_T, new HashSet<String>(Arrays.asList("k2", "k1")));
        first.put(SHA_E, new HashSet<String>(Arrays.asList("k3")));

        Map<String, Set<String>> second = new LinkedHashMap<String, Set<String>>();
        second.put(SHA_E, new HashSet<String>(Arrays.asList("k3")));
        second.put(SHA_T, new HashSet<String>(Arrays.asList("k1", "k2")));

        String digest1 = CaptureCensorship.presentationDigest(22, first);
        String digest2 = CaptureCensorship.presentationDigest(22, second);
        check(!digest1.isEmpty(), "non-empty map must produce digest");
        equal(digest1, digest2, "digest must be order-independent");

        String digestDifferentPx = CaptureCensorship.presentationDigest(15, first);
        check(!digest1.equals(digestDifferentPx), "different blur pixels must change presentationDigest");

        Map<String, Set<String>> third = new HashMap<String, Set<String>>(first);
        third.put(SHA_X, Collections.singleton("k4"));
        check(!digest1.equals(CaptureCensorship.presentationDigest(22, third)),
                "extra censored image must change presentationDigest");
    }

    private static void manifestRecordIsOrderedAndExcludesFolderPath() throws Exception {
        Map<String, Set<String>> map = new LinkedHashMap<String, Set<String>>();
        map.put(SHA_E, new HashSet<String>(Arrays.asList("keyB", "keyA")));
        map.put(SHA_T, new HashSet<String>(Collections.singletonList("keyC")));
        JSONObject record = CaptureCensorship.manifestRecord(75, 90, 22, 3, 2, map);
        equal(1, record.getInt("schemaVersion"), "schemaVersion");
        equal("css-blur", record.getString("mode"), "mode");
        equal(2, record.getInt("censoredImageCount"), "censoredImageCount");
        equal(2, record.getInt("censoredMessageCount"), "censoredMessageCount");
        JSONArray images = record.getJSONArray("images");
        equal(SHA_T, images.getJSONObject(0).getString("sha256"), "images must be sorted lexicographically by sha256");
        equal(SHA_E, images.getJSONObject(1).getString("sha256"), "second image sha256");
        equal("keyA", images.getJSONObject(1).getJSONArray("messageKeys").getString(0), "messageKeys must be sorted");
        String serialized = record.toString();
        check(!serialized.toLowerCase().contains("c:\\") && !serialized.contains("folderPath"),
                "manifest record must not leak local reference folder path");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
