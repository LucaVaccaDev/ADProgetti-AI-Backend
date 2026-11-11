package it.adprogetti.zio.edge.references;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Minimal reader of one sheet of an .xlsx, built on the JDK only (no new dependency). The file is untrusted data: it is
 * read in memory with a size cap, XML external entities and DTDs are off, and nothing is ever executed or evaluated
 * (formulas are not run: the value Excel stored is read).
 */
final class XlsxReader {

    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 60L * 1024 * 1024;

    /** A cell: text (shared or inline string) or a number. */
    record Cell(String text, Double number) {
        String asText() {
            if (text != null) {
                return text.strip();
            }
            if (number == null) {
                return "";
            }
            double n = number;
            return n == Math.rint(n) && Math.abs(n) < 1e15 ? Long.toString((long) n) : Double.toString(n);
        }

        boolean blank() {
            return asText().isEmpty();
        }
    }

    /** rows by 1-based row number, each a map of 0-based column index to cell, both in order. */
    record Sheet(String name, Map<Integer, Map<Integer, Cell>> rows) {
    }

    static class XlsxException extends RuntimeException {
        XlsxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Sheet read(InputStream in, String sheetName) {
        try {
            byte[] bytes = in.readNBytes((int) MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new XlsxException("TOO_LARGE", null);
            }
            Map<String, byte[]> parts = unzip(bytes);
            byte[] workbook = require(parts, "xl/workbook.xml");
            String relId = null;
            String exactName = null;
            for (Map<String, String> s : elements(workbook, "sheet")) {
                if (s.get("name") != null && s.get("name").strip().equalsIgnoreCase(sheetName.strip())) {
                    relId = s.get("id");
                    exactName = s.get("name");
                }
            }
            if (relId == null) {
                throw new XlsxException("SHEET_NOT_FOUND", null);
            }
            String target = null;
            for (Map<String, String> r : elements(require(parts, "xl/_rels/workbook.xml.rels"), "Relationship")) {
                if (relId.equals(r.get("Id"))) {
                    target = r.get("Target");
                }
            }
            if (target == null) {
                throw new XlsxException("BAD_FILE", null);
            }
            String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
            List<String> shared = parts.containsKey("xl/sharedStrings.xml")
                    ? sharedStrings(parts.get("xl/sharedStrings.xml")) : List.of();
            return new Sheet(exactName, cells(require(parts, path), shared));
        } catch (IOException | XMLStreamException e) {
            throw new XlsxException("BAD_FILE", e);
        }
    }

    private static byte[] require(Map<String, byte[]> parts, String name) {
        byte[] b = parts.get(name);
        if (b == null) {
            throw new XlsxException("BAD_FILE", null);
        }
        return b;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long total = 0;
            for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                if (e.isDirectory() || !(e.getName().startsWith("xl/") && e.getName().endsWith(".xml")
                        || e.getName().endsWith(".rels"))) {
                    continue;
                }
                byte[] data = zip.readNBytes((int) MAX_ENTRY_BYTES + 1);
                total += data.length;
                if (data.length > MAX_ENTRY_BYTES || total > 2 * MAX_ENTRY_BYTES) {
                    throw new XlsxException("TOO_LARGE", null);
                }
                parts.put(e.getName(), data);
            }
        }
        return parts;
    }

    private static XMLStreamReader xml(byte[] data) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return f.createXMLStreamReader(new ByteArrayInputStream(data));
    }

    /** Attributes of every element with this local name; the namespace of {@code r:id} is read as "id". */
    private static List<Map<String, String>> elements(byte[] data, String local) throws XMLStreamException {
        List<Map<String, String>> out = new ArrayList<>();
        XMLStreamReader r = xml(data);
        while (r.hasNext()) {
            if (r.next() == XMLStreamConstants.START_ELEMENT && local.equals(r.getLocalName())) {
                Map<String, String> attrs = new HashMap<>();
                for (int i = 0; i < r.getAttributeCount(); i++) {
                    attrs.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                }
                out.add(attrs);
            }
        }
        return out;
    }

    private static List<String> sharedStrings(byte[] data) throws XMLStreamException {
        List<String> out = new ArrayList<>();
        XMLStreamReader r = xml(data);
        StringBuilder current = null;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if ("si".equals(r.getLocalName())) {
                    current = new StringBuilder();
                } else if ("t".equals(r.getLocalName()) && current != null) {
                    current.append(r.getElementText());
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT && "si".equals(r.getLocalName()) && current != null) {
                out.add(current.toString());
                current = null;
            }
        }
        return out;
    }

    private static Map<Integer, Map<Integer, Cell>> cells(byte[] data, List<String> shared) throws XMLStreamException {
        Map<Integer, Map<Integer, Cell>> rows = new TreeMap<>();
        XMLStreamReader r = xml(data);
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT || !"c".equals(r.getLocalName())) {
                continue;
            }
            String ref = r.getAttributeValue(null, "r");
            String type = r.getAttributeValue(null, "t");
            String value = null;
            StringBuilder inline = new StringBuilder();
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if ("v".equals(r.getLocalName())) {
                        value = r.getElementText();
                    } else if ("t".equals(r.getLocalName())) {
                        inline.append(r.getElementText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && "c".equals(r.getLocalName())) {
                    break;
                }
            }
            if (ref == null) {
                continue;
            }
            Cell cell = null;
            if ("s".equals(type) && value != null) {
                int i = Integer.parseInt(value.strip());
                cell = i >= 0 && i < shared.size() ? new Cell(shared.get(i), null) : null;
            } else if ("inlineStr".equals(type)) {
                cell = new Cell(inline.toString(), null);
            } else if ("str".equals(type) || "e".equals(type) || "b".equals(type)) {
                cell = "str".equals(type) && value != null ? new Cell(value, null) : null; // errors and flags are not data
            } else if (value != null && !value.isBlank()) {
                try {
                    cell = new Cell(null, Double.parseDouble(value.strip()));
                } catch (NumberFormatException e) {
                    cell = null;
                }
            }
            if (cell != null && !cell.blank()) {
                int row = Integer.parseInt(ref.replaceAll("[^0-9]", ""));
                rows.computeIfAbsent(row, k -> new TreeMap<>()).put(column(ref), cell);
            }
        }
        return rows;
    }

    /** "AB12" is column 27 (0-based). */
    static int column(String ref) {
        int c = 0;
        for (char ch : ref.toCharArray()) {
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            c = c * 26 + (ch - 'A' + 1);
        }
        return c - 1;
    }

    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return sb.toString();
    }
}
