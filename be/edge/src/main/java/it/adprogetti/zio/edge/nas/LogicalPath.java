package it.adprogetti.zio.edge.nas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An application path such as {@code archivio:/Commesse/2026/Rossi}: a logical share name and clean segments. It
 * is built only by {@link #parse}, which refuses everything that could mean "somewhere else": {@code ..}, dots,
 * absolute or Windows paths, drive letters, control characters, trailing spaces or dots, and look-alike Unicode
 * characters that normalise to a slash or a dot.
 */
record LogicalPath(String share, List<String> segments) {

    private static final Pattern FORM = Pattern.compile("([a-z][a-z0-9_-]{1,30}):/(.*)", Pattern.DOTALL);
    private static final int MAX_TOTAL = 1024;
    private static final int MAX_SEGMENT = 255;

    LogicalPath {
        segments = List.copyOf(segments);
    }

    static LogicalPath parse(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TOTAL) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        Matcher m = FORM.matcher(text);
        if (!m.matches()) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        String rest = m.group(2);
        List<String> segments = new ArrayList<>();
        if (!rest.isEmpty()) {
            String[] parts = rest.split("/", -1);
            int n = parts.length;
            if (parts[n - 1].isEmpty()) {
                n--; // one trailing slash is fine
            }
            for (int i = 0; i < n; i++) {
                segments.add(requireClean(parts[i]));
            }
        }
        return new LogicalPath(m.group(1), segments);
    }

    LogicalPath child(String name) {
        List<String> next = new ArrayList<>(segments);
        next.add(requireClean(name));
        return new LogicalPath(share, next);
    }

    String text() {
        return share + ":/" + String.join("/", segments);
    }

    boolean startsWith(List<String> prefix) {
        return segments.size() >= prefix.size() && segments.subList(0, prefix.size()).equals(prefix);
    }

    static String requireClean(String segment) {
        if (segment == null || segment.isEmpty() || segment.length() > MAX_SEGMENT || !isClean(segment)
                || !isClean(Normalizer.normalize(segment, Normalizer.Form.NFKC))) {
            throw new NasException(NasException.Code.INVALID_PATH);
        }
        return segment;
    }

    private static boolean isClean(String s) {
        if (s.equals(".") || s.equals("..") || s.endsWith(" ") || s.endsWith(".") || s.startsWith(".zio-")) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '/' || c == '\\' || c == ':' || c == '\u0000' || c == '∕'
                    || c == '⁄' || c == '／' || c == '＼' || c == '．') {
                return false;
            }
        }
        return true;
    }
}
