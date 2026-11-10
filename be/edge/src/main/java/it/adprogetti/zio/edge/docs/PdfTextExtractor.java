package it.adprogetti.zio.edge.docs;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Text of a PDF, page by page. A PDF is untrusted data: PDFBox runs no JavaScript and opens no network connection,
 * and here the size, the page count, the amount of text and the time are all bounded. Scanned PDFs (no text layer)
 * are reported as NO_TEXT, never guessed. The SHA-256 is computed on the same read, so the file is read once.
 */
@Component
class PdfTextExtractor {

    record Page(int number, String text) {
    }

    record Extracted(String sha256, List<Page> pages, int totalPages, int textChars) {
    }

    /** Below this many characters in the whole document, or this many per page on average, it counts as scanned. */
    static final int MIN_TOTAL_CHARS = 80;
    static final int MIN_CHARS_PER_PAGE = 12;

    private final DocsProperties props;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "edge-pdf-extract");
        t.setDaemon(true);
        return t;
    });

    PdfTextExtractor(DocsProperties props) {
        this.props = props;
    }

    Extracted extract(InputStream source) throws ExtractionException {
        Future<Extracted> future = workers.submit(() -> doExtract(source));
        try {
            return future.get(props.extractTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExtractionException("TIMEOUT", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ExtractionException("INTERRUPTED", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ExtractionException x) {
                throw x;
            }
            throw new ExtractionException("PARSE_ERROR", e.getCause());
        }
    }

    private Extracted doExtract(InputStream source) throws ExtractionException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (DigestInputStream in = new DigestInputStream(source, digest);
                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            String sha = HexFormat.of().formatHex(digest.digest());
            if (document.isEncrypted()) {
                // Opened without a password: only the owner password is set (copy/print restrictions, readable by
                // anyone). A PDF that needs a user password never gets here (InvalidPasswordException below).
                document.setAllSecurityToBeRemoved(true);
            }
            int total = document.getNumberOfPages();
            if (total > props.maxPages()) {
                throw new ExtractionException("TOO_MANY_PAGES", null);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSuppressDuplicateOverlappingText(true);
            List<Page> pages = new ArrayList<>();
            int chars = 0;
            for (int p = 1; p <= total; p++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new ExtractionException("TIMEOUT", null);
                }
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = clean(stripper.getText(document));
                chars += text.length();
                if (chars > props.maxChars()) {
                    throw new ExtractionException("TOO_MUCH_TEXT", null);
                }
                if (!text.isBlank()) {
                    pages.add(new Page(p, text));
                }
            }
            return new Extracted(sha, pages, total, chars);
        } catch (InvalidPasswordException e) {
            throw new ExtractionException("ENCRYPTED", e);
        } catch (ExtractionException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ExtractionException("PARSE_ERROR", e);
        }
    }

    /** True when the text is too thin to be a real text layer (a scan, or only page numbers). */
    static boolean looksScanned(Extracted extracted) {
        return extracted.textChars() < MIN_TOTAL_CHARS
                || extracted.textChars() < (long) MIN_CHARS_PER_PAGE * extracted.totalPages();
    }

    /** Postgres text cannot hold NUL; joins words split by a hyphen at the end of a line; one blank per run. */
    static String clean(String raw) {
        String text = raw.replace('\u0000', ' ').replace("­", "").replaceAll("(\\p{L})-\\r?\\n(\\p{Ll})", "$1$2");
        text = text.replaceAll("[\\p{Cc}&&[^\\n]]", " ").replaceAll("[ \\t\\u00a0]+", " ")
                .replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }
}
