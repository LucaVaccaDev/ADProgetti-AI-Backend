package it.adprogetti.zio.edge.tenders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Turns one TED search result into a {@link TenderRow}. TED fields are heterogeneous (a value, a list per lot, a map per
 * language), so every read is defensive: a notice that cannot be understood is skipped, never guessed. Titles and
 * names are untrusted text: they are only stored and shown as plain text (CLAUDE.md section 8).
 */
final class TedMapper {

    static final String[] FIELDS = { "publication-number", "notice-title", "buyer-name", "publication-date",
            "deadline-receipt-tender-date-lot", "deadline-receipt-tender-time-lot", "estimated-value-proc",
            "estimated-value-cur-proc", "estimated-value-lot", "estimated-value-cur-lot", "classification-cpv",
            "notice-type", "procedure-type", "place-of-performance-city-lot", "identifier-lot" };

    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,9}-[0-9]{4}");
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final String[] LANGS = { "ita", "eng" };
    private static final String SEPARATOR = " – ";

    private TedMapper() {
    }

    static Optional<TenderRow> map(JsonNode n) {
        String number = text(n.get("publication-number"));
        LocalDate published = date(text(n.get("publication-date")));
        String title = title(n.get("notice-title"));
        String noticeType = text(n.get("notice-type"));
        if (number == null || !NUMBER.matcher(number).matches() || published == null || title == null || noticeType == null) {
            return Optional.empty();
        }
        List<String> cpv = new ArrayList<>(new LinkedHashSet<>(texts(n.get("classification-cpv"))));
        if (cpv.size() > 6) {
            cpv = cpv.subList(0, 6);
        }
        Set<String> lots = new LinkedHashSet<>(texts(n.get("identifier-lot")));
        List<String> places = new ArrayList<>(new LinkedHashSet<>(texts(n.get("place-of-performance-city-lot"))));
        return Optional.of(new TenderRow(number, cut(title, 400), cut(localized(n.get("buyer-name")), 300), amount(n),
                currency(n), deadline(n), published, cut(text(n.get("procedure-type")), 40), cut(noticeType, 40), cpv,
                places.isEmpty() ? null : cut(places.get(0), 200), Math.max(1, lots.size())));
    }

    /** "Italia – Servizi di ingegneria – Titolo": TED prefixes country and CPV description; only the last part is the title. */
    static String title(JsonNode node) {
        String full = localized(node);
        if (full == null) {
            return null;
        }
        String[] parts = full.split(SEPARATOR);
        String last = parts.length >= 3 ? String.join(SEPARATOR, java.util.Arrays.copyOfRange(parts, 2, parts.length)) : full;
        String clean = last.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? null : clean;
    }

    private static BigDecimal amount(JsonNode n) {
        BigDecimal proc = number(n.get("estimated-value-proc"));
        if (proc != null) {
            return proc;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (String v : texts(n.get("estimated-value-lot"))) {
            BigDecimal value = parse(v);
            if (value != null) {
                sum = sum.add(value);
                any = true;
            }
        }
        return any ? sum : null;
    }

    private static String currency(JsonNode n) {
        String c = text(n.get("estimated-value-cur-proc"));
        if (c == null) {
            List<String> lots = texts(n.get("estimated-value-cur-lot"));
            c = lots.isEmpty() ? null : lots.get(0);
        }
        return c != null && c.matches("[A-Z]{3}") ? c : null;
    }

    /** The earliest receipt deadline among the lots; a missing time means the end of that day in Rome. */
    private static java.time.Instant deadline(JsonNode n) {
        List<String> dates = texts(n.get("deadline-receipt-tender-date-lot"));
        List<String> times = texts(n.get("deadline-receipt-tender-time-lot"));
        java.time.Instant best = null;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = date(dates.get(i));
            if (d == null) {
                continue;
            }
            java.time.Instant at = null;
            String time = i < times.size() ? times.get(i) : null;
            if (time != null) {
                try {
                    at = OffsetDateTime.parse(d + "T" + time).toInstant();
                } catch (DateTimeParseException e) {
                    at = null;
                }
            }
            if (at == null) {
                at = ZonedDateTime.of(d, LocalTime.of(23, 59), ROME).toInstant();
            }
            if (best == null || at.isBefore(best)) {
                best = at;
            }
        }
        return best;
    }

    private static LocalDate date(String text) {
        if (text == null || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** A map language -> text or list of texts: Italian first, then English, then whatever comes first. */
    private static String localized(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            return text(node);
        }
        for (String lang : LANGS) {
            String v = first(node.get(lang));
            if (v != null) {
                return v;
            }
        }
        Iterator<JsonNode> it = node.iterator();
        while (it.hasNext()) {
            String v = first(it.next());
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String first(JsonNode node) {
        List<String> all = texts(node);
        return all.isEmpty() ? null : all.get(0);
    }

    private static List<String> texts(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String t = text(child);
                if (t != null) {
                    out.add(t);
                }
            }
        } else {
            String t = text(node);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) {
            return null;
        }
        String t = node.asString().trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal number(JsonNode node) {
        return parse(text(node));
    }

    private static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(text);
            return v.signum() < 0 || v.precision() - v.scale() > 14 ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cut(String text, int max) {
        return text == null ? null : text.length() <= max ? text : text.substring(0, max);
    }
}
