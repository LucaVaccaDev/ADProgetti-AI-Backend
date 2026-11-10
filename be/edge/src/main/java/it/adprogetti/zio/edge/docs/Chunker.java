package it.adprogetti.zio.edge.docs;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the text of a document into passages of about {@code size} characters that overlap by {@code overlap},
 * breaking at a paragraph, sentence or word boundary, and remembers which pages each passage covers.
 */
final class Chunker {

    record Chunk(int ordinal, int pageFrom, int pageTo, String content) {
    }

    private static final int MIN_CHUNK = 30;

    private Chunker() {
    }

    static List<Chunk> split(List<PdfTextExtractor.Page> pages, int size, int overlap) {
        StringBuilder all = new StringBuilder();
        List<int[]> starts = new ArrayList<>(); // {offset, page}
        for (PdfTextExtractor.Page page : pages) {
            starts.add(new int[] { all.length(), page.number() });
            all.append(page.text()).append("\n\n");
        }
        String text = all.toString();
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                end = boundary(text, start + size / 2, end);
            }
            String content = text.substring(start, end).strip();
            if (content.length() >= MIN_CHUNK) {
                chunks.add(new Chunk(chunks.size(), pageAt(starts, start), pageAt(starts, Math.max(start, end - 1)),
                        content));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
            while (start < text.length() && !Character.isWhitespace(text.charAt(start - 1)) && start < end) {
                start++; // do not start in the middle of a word
            }
        }
        return chunks;
    }

    private static int boundary(String text, int from, int end) {
        for (String mark : new String[] { "\n\n", ". ", ".\n", "\n", "; ", ", ", " " }) {
            int at = text.lastIndexOf(mark, end - 1);
            if (at >= from) {
                return at + mark.length();
            }
        }
        return end;
    }

    private static int pageAt(List<int[]> starts, int offset) {
        int page = starts.get(0)[1];
        for (int[] s : starts) {
            if (s[0] <= offset) {
                page = s[1];
            } else {
                break;
            }
        }
        return page;
    }
}
