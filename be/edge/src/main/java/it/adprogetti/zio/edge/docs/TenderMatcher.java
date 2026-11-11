package it.adprogetti.zio.edge.docs;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Finds which tender a loose document belongs to, so the user can drop files in one folder without naming it. Two
 * clues, both checked by code and never by the AI: a known tender number in the file name, or the words of a tender's
 * title found in the first pages of the document. The match needs a clear winner: when two tenders look alike, or none
 * does, the document is NOT assigned and the user is told (the file is never guessed onto a tender).
 */
@Component
class TenderMatcher {

    static final double MIN_SCORE = 0.7;
    static final double MARGIN = 0.2;
    private static final int PAGES_READ = 4;
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z0-9])(EC-\\d{4,10}|\\d{5,7}-\\d{4})(?![A-Za-z0-9])");

    private record Candidate(String notice, Set<String> words) {
    }

    private final JdbcClient jdbc;

    TenderMatcher(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A tender number written in the file name, if it is one we know. */
    Optional<String> fromFileName(String fileName) {
        Matcher m = NUMBER.matcher(fileName);
        while (m.find()) {
            String n = m.group(1);
            if (jdbc.sql("select count(*) from tender where notice_number = :n").param("n", n).query(Integer.class).single() > 0) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    /** The tender whose title words are found in the first pages, if exactly one clearly stands out. */
    Optional<String> fromContent(List<PdfTextExtractor.Page> pages) {
        Set<String> doc = new HashSet<>();
        pages.stream().limit(PAGES_READ).forEach(p -> doc.addAll(words(p.text())));
        if (doc.isEmpty()) {
            return Optional.empty();
        }
        String best = null;
        double bestScore = 0;
        double second = 0;
        for (Candidate c : candidates()) {
            long hit = c.words().stream().filter(doc::contains).count();
            double score = c.words().isEmpty() ? 0 : (double) hit / c.words().size();
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = c.notice();
            } else if (score > second) {
                second = score;
            }
        }
        return best != null && bestScore >= MIN_SCORE && bestScore - second >= MARGIN ? Optional.of(best) : Optional.empty();
    }

    private List<Candidate> candidates() {
        return jdbc.sql("select notice_number, title from tender where title is not null")
                .query((rs, n) -> new Candidate(rs.getString(1), words(rs.getString(2)))).list().stream()
                .filter(c -> c.words().size() >= 3).toList();
    }

    /** Significant words: letters only, at least 5 characters, accents removed, lower case. */
    static Set<String> words(String text) {
        String t = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        for (String w : t.split("[^a-z]+")) {
            if (w.length() >= 5) {
                out.add(w);
            }
        }
        return out;
    }
}
