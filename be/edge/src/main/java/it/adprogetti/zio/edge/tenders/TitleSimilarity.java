package it.adprogetti.zio.edge.tenders;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether two titles from different sources describe the same tender. Sources rewrite titles, so this is a
 * heuristic: significant words in common against the shorter title. It is only used together with the same deadline day.
 */
final class TitleSimilarity {

    private static final double THRESHOLD = 0.6;
    private static final int MIN_WORDS = 3;

    private TitleSimilarity() {
    }

    static Set<String> words(String title) {
        String plain = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        for (String w : plain.split("[^a-z0-9]+")) {
            if (w.length() >= 4) {
                out.add(w);
            }
        }
        return out;
    }

    static boolean similar(String a, String b) {
        Set<String> x = words(a);
        Set<String> y = words(b);
        int smaller = Math.min(x.size(), y.size());
        if (smaller < MIN_WORDS) {
            return false;
        }
        long common = x.stream().filter(y::contains).count();
        return (double) common / smaller >= THRESHOLD;
    }
}
