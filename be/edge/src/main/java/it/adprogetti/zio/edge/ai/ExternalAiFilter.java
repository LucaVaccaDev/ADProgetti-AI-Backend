package it.adprogetti.zio.edge.ai;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Allegato A.2: code, not the AI, decides whether a text may leave the studio. It blocks e-mail addresses, tax
 * codes, VAT numbers, NAS paths and any listed job or client name. It is conservative and not infallible; a
 * blocked text is answered by the local AI instead. The result names only the category, never the matched text.
 */
final class ExternalAiFilter {

    record Verdict(boolean allowed, String reason) {
        static final Verdict OK = new Verdict(true, null);

        static Verdict blocked(String reason) {
            return new Verdict(false, reason);
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+");
    private static final Pattern TAX_CODE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z](?![\\p{L}\\p{N}])");
    private static final Pattern VAT = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(IT)?\\s?[0-9]{11}(?![\\p{L}\\p{N}])");
    private static final Pattern NAS_PATH = Pattern.compile(
            "(?i)(smb|file|afp)://|\\\\\\\\[^\\s\\\\]+\\\\|/Volumes/|(?<![\\p{L}\\p{N}])(archivio|gare|documenti|temporanea):[/\\\\]|(?<![\\p{L}\\p{N}])[a-z]:\\\\");

    private ExternalAiFilter() {
    }

    static Verdict check(String text, List<String> sensitiveTerms, int maxLength) {
        if (text == null || text.isBlank()) {
            return Verdict.blocked("EMPTY");
        }
        if (text.length() > maxLength) {
            return Verdict.blocked("TOO_LONG");
        }
        if (EMAIL.matcher(text).find()) {
            return Verdict.blocked("EMAIL");
        }
        if (TAX_CODE.matcher(text).find()) {
            return Verdict.blocked("TAX_CODE");
        }
        if (VAT.matcher(text).find()) {
            return Verdict.blocked("VAT_NUMBER");
        }
        if (NAS_PATH.matcher(text).find()) {
            return Verdict.blocked("NAS_PATH");
        }
        String haystack = " " + normalize(text) + " ";
        for (String term : sensitiveTerms) {
            String needle = normalize(term);
            if (needle.length() >= 3 && haystack.contains(" " + needle + " ")) {
                return Verdict.blocked("SENSITIVE_NAME");
            }
        }
        return Verdict.OK;
    }

    /** Lower case, no accents, every run of non-alphanumerics becomes one space. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
