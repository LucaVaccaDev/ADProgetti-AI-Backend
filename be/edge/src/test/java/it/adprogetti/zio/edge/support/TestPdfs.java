package it.adprogetti.zio.edge.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Makes small PDFs for tests (never real documents). Standard fonts: Italian accents work, no files needed. */
public final class TestPdfs {

    private TestPdfs() {
    }

    /** One page per argument, text wrapped on the page. */
    public static byte[] text(String... pages) {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.setLeading(14);
                    cs.newLineAtOffset(40, 760);
                    for (String line : wrap(text, 85)) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A page with nothing on it: like a scan without a text layer. */
    public static byte[] blank(int pages) {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Needs a password to open. */
    public static byte[] encrypted(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(40, 760);
                cs.showText(text);
                cs.endText();
            }
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Readable by anyone, but with the owner password set and copying of text forbidden (common on public tender files). */
    public static byte[] ownerRestricted(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(40, 760);
                cs.showText(text);
                cs.endText();
            }
            AccessPermission permissions = new AccessPermission();
            permissions.setCanExtractContent(false);
            permissions.setCanModify(false);
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", "", permissions);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() + word.length() + 1 > width && line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(line.length() == 0 ? "" : " ").append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
