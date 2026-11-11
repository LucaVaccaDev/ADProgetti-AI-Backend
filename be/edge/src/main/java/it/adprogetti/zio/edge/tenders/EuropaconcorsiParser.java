package it.adprogetti.zio.edge.tenders;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Reads the result list of Europaconcorsi. Only the facts of each card are taken: id, entity, place, deadline, title,
 * the portal path and the link of the publishing portal. The descriptive summary is never read. The page is
 * untrusted text: everything is length-limited and links must be https.
 */
final class EuropaconcorsiParser {

    record Card(String id, String title, String buyer, String place, LocalDate deadline, String path, String originUrl) {
    }

    private static final String MARK = "<div class=\"competition\" data-competition-id=\"";
    private static final Pattern ID = Pattern.compile("^([0-9]{1,9})\"");
    private static final Pattern ORG = Pattern.compile("class=\"organization\">\\s*(.*?)\\s*</span>", Pattern.DOTALL);
    private static final Pattern PLACE = Pattern.compile("class=\"place\">\\s*(.*?)\\s*</span>", Pattern.DOTALL);
    private static final Pattern DEADLINE = Pattern.compile("Scadenza\\s+([0-9]{2})/([0-9]{2})/([0-9]{4})");
    private static final Pattern TITLE = Pattern.compile(
            "class=\"title\">\\s*<a href=\"(/bandi/[0-9]+-[a-z0-9-]{0,200})\">(.*?)</a>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("href=\"(https://[^\"\\s]{1,500})\"");
    private static final Pattern PAGE = Pattern.compile("[?&;]page=([0-9]{1,4})");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private EuropaconcorsiParser() {
    }

    static List<Card> parse(String html) {
        List<Card> out = new ArrayList<>();
        String[] blocks = html.split(java.util.regex.Pattern.quote(MARK));
        for (int i = 1; i < blocks.length; i++) {
            card(blocks[i]).ifPresent(out::add);
        }
        return out;
    }

    /** The highest page number linked from the pagination, or 1. */
    static int lastPage(String html) {
        int last = 1;
        Matcher m = PAGE.matcher(html);
        while (m.find()) {
            last = Math.max(last, Integer.parseInt(m.group(1)));
        }
        return last;
    }

    /** True when the page is the login form: the session is not valid. */
    static boolean isLoginForm(String html) {
        return html.contains("name=\"person[password]\"");
    }

    private static Optional<Card> card(String block) {
        Matcher id = ID.matcher(block);
        Matcher title = TITLE.matcher(block);
        if (!id.find() || !title.find()) {
            return Optional.empty();
        }
        String text = clean(title.group(2), 400);
        if (text == null) {
            return Optional.empty();
        }
        // Only the head of the card: later cards are not part of this block, but the description must not be read.
        int description = block.indexOf("class=\"description\"");
        String head = description > 0 ? block.substring(0, description) : block;
        return Optional.of(new Card(id.group(1), text, field(ORG, head, 300, " ·"), field(PLACE, head, 200, " ·"),
                deadline(head), title.group(1),
                origin(description > 0 ? block.substring(description) : block)));
    }

    private static String field(Pattern p, String s, int max, String trailing) {
        Matcher m = p.matcher(s);
        if (!m.find()) {
            return null;
        }
        String v = clean(m.group(1), max + 4);
        if (v == null) {
            return null;
        }
        while (v.endsWith("·") || v.endsWith(",")) {
            v = v.substring(0, v.length() - 1).trim();
        }
        return v.isEmpty() ? null : v;
    }

    private static LocalDate deadline(String head) {
        Matcher m = DEADLINE.matcher(head);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** The first https link that does not lead back to Europaconcorsi (its own files are attachments, not the notice). */
    private static String origin(String block) {
        Matcher m = LINK.matcher(block);
        while (m.find()) {
            String url = HtmlUtils.htmlUnescape(m.group(1));
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null && uri.getUserInfo() == null && !host.equals("europaconcorsi.com")
                        && !host.endsWith(".europaconcorsi.com") && url.length() <= 500) {
                    return url;
                }
            } catch (IllegalArgumentException e) {
                // a malformed link is simply not used
            }
        }
        return null;
    }

    private static String clean(String raw, int max) {
        String v = HtmlUtils.htmlUnescape(TAGS.matcher(raw).replaceAll(" ")).replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > max ? v.substring(0, max) : v;
    }
}
