package it.adprogetti.zio.edge.references;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a small .xlsx with the same layout as the studio's references sheet, with invented data (never real
 * clients). Strings go through sharedStrings.xml like Excel does.
 */
final class TestReferencesXlsx {

    private final List<String> shared = new ArrayList<>();
    private final StringBuilder rows = new StringBuilder();
    private final String sheetName;

    TestReferencesXlsx(String sheetName) {
        this.sheetName = sheetName;
    }

    /** cells: "A=text" for strings or "D=123.4" for numbers (a leading '#' on the value forces a number). */
    TestReferencesXlsx row(int number, String... cells) {
        rows.append("<row r=\"").append(number).append("\">");
        for (String c : cells) {
            String col = c.substring(0, c.indexOf('='));
            String v = c.substring(c.indexOf('=') + 1);
            if (v.startsWith("#")) {
                rows.append("<c r=\"").append(col).append(number).append("\"><v>").append(v.substring(1)).append("</v></c>");
            } else {
                shared.add(v);
                rows.append("<c r=\"").append(col).append(number).append("\" t=\"s\"><v>").append(shared.size() - 1)
                        .append("</v></c>");
            }
        }
        rows.append("</row>");
        return this;
    }

    byte[] build() {
        StringBuilder ss = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : shared) {
            ss.append("<si><t>").append(s.replace("&", "&amp;").replace("<", "&lt;")).append("</t></si>");
        }
        ss.append("</sst>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                    + "<sheet name=\"5 ANNI\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"" + sheetName
                    + "\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Target=\"worksheets/sheet2.xml\"/></Relationships>");
            put(zip, "xl/sharedStrings.xml", ss.toString());
            put(zip, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>");
            put(zip, "xl/worksheets/sheet2.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                    + rows + "</sheetData></worksheet>");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * Three invented services in columns D, E, F; label repeated in G (the totals column) with the copy of the ids in H.
     * D: complete, CIG, role "Capogruppo 90%". E: no CIG, share given as the number 1, period as an Excel date.
     * F: unreadable period and a text in an amount cell.
     */
    static TestReferencesXlsx sample() {
        return new TestReferencesXlsx("10 ANNI")
                .row(1, "A=Località esecuzione servizio", "D=Comune Alfa (XX)", "E=Comune Beta (YY)", "F=Comune Gamma (ZZ)",
                        "G=Località esecuzione servizio")
                .row(2, "A=Periodo esecuzione servizio", "D=09/2020-12/2020", "E=#45536", "F=autunno 2021")
                .row(3, "A=Oggetto del servizio", "D=Progetto esecutivo scuola", "E=Verifica del progetto", "F=Collaudo ponte")
                .row(4, "A=Committente", "D=Comune Alfa", "E=Provincia Beta", "F=Comune Gamma")
                .row(5, "A=Ruolo e percentuale di partecipazione", "D=Capogruppo 90%", "E=#1", "F=100%")
                .row(6, "A=CIG", "D=83974903F8.", "F=ZA61F7E6EF", "G=CIG")
                .row(7, "A=L. 143/49", "B=DM 2016", "C=G", "D=Determina n. 12 del 3/4/2021")
                .row(8, "A=I/d", "B=E.08", "C=#0.95", "D=#1000000.5", "E=#200000", "G=#1200000.5", "H=E.08", "I=#0.95")
                .row(9, "D=#500000", "G=#500000", "H=E.04", "I=#1.2") // id only in the copy on the right
                .row(10, "A=I/g", "B=S,03", "C=#0.95", "E=#300000", "F=circa 100000", "G=#999")
                .row(11, "A=TOTALE", "D=#1500000.5");
    }
}
