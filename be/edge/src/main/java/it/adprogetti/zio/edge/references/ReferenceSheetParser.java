package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.edge.references.XlsxReader.Cell;
import it.adprogetti.zio.edge.references.XlsxReader.Sheet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the studio's references sheet. The layout (verified on the real file, D-24): one column per service; the labels
 * "Località…", "Periodo…", "Oggetto…", "Committente…", "Ruolo e percentuale…", "CIG" in column A; then one row per work id
 * of DM 17/06/2016 (E.08, S.03, IA.01…) with the amount of the works in the service's column; a "TOTALE" row ends the
 * block. The label column is repeated after the last service, followed by a copy of the work ids, which is the only place
 * some ids appear. Nothing is guessed: what cannot be read is reported as an issue code and the service becomes
 * DA_VERIFICARE.
 */
final class ReferenceSheetParser {

    record Amount(String workId, String category, BigDecimal complexity, BigDecimal amount) {
    }

    record Service(String column, String place, String periodText, LocalDate periodFrom, LocalDate periodTo,
            String object, String client, String roleText, BigDecimal sharePercent, String cig, String approvalRef,
            List<Amount> amounts, Set<String> issues) {
    }

    record Parsed(List<Service> services, int totalMismatches) {
    }

    static class LayoutException extends RuntimeException {
        LayoutException(String code) {
            super(code);
        }
    }

    private static final Pattern WORK_ID = Pattern.compile("^[A-Z]{1,2}[.,]\\d{2}$");
    private static final Pattern MONTH_YEAR = Pattern.compile("(\\d{1,2})\\s*[/.-]\\s*(\\d{4})");
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})(?:[.,]\\d+)?\\s*%");
    private static final int FIRST_SERVICE_COLUMN = 3; // D

    private ReferenceSheetParser() {
    }

    static Parsed parse(Sheet sheet) {
        Map<Integer, Map<Integer, Cell>> rows = sheet.rows();
        int placeRow = -1, periodRow = -1, objectRow = -1, clientRow = -1, roleRow = -1, cigRow = -1, approvalRow = -1;
        String placeLabel = null;
        for (var row : rows.entrySet()) {
            Cell a = row.getValue().get(0);
            if (a == null) {
                continue;
            }
            String label = a.asText().toLowerCase(Locale.ITALIAN);
            if (placeRow < 0 && label.startsWith("località")) {
                placeRow = row.getKey();
                placeLabel = a.asText();
            } else if (placeRow >= 0 && periodRow < 0 && label.startsWith("period")) {
                periodRow = row.getKey();
            } else if (placeRow >= 0 && objectRow < 0 && label.startsWith("oggetto")) {
                objectRow = row.getKey();
            } else if (placeRow >= 0 && clientRow < 0 && label.startsWith("committente")) {
                clientRow = row.getKey();
            } else if (placeRow >= 0 && roleRow < 0 && label.startsWith("ruolo")) {
                roleRow = row.getKey();
            } else if (placeRow >= 0 && cigRow < 0 && label.equals("cig")) {
                cigRow = row.getKey();
            } else if (placeRow >= 0 && approvalRow < 0 && label.startsWith("l. 143")) {
                approvalRow = row.getKey();
            }
        }
        if (placeRow < 0 || objectRow < 0 || cigRow < 0) {
            throw new LayoutException("LAYOUT_NOT_RECOGNISED");
        }
        // The first row after the header block where a work id sits in column B (or in the copy right of the totals).
        Map<Integer, Cell> placeCells = rows.get(placeRow);
        int totalColumn = -1;
        for (var c : placeCells.entrySet()) {
            if (c.getKey() >= FIRST_SERVICE_COLUMN && c.getValue().asText().equalsIgnoreCase(placeLabel)) {
                totalColumn = c.getKey();
                break;
            }
        }
        int lastColumn = totalColumn >= 0 ? totalColumn - 1 : rows.get(objectRow).keySet().stream()
                .mapToInt(Integer::intValue).max().orElse(FIRST_SERVICE_COLUMN);

        List<Service> services = new ArrayList<>();
        for (int col = FIRST_SERVICE_COLUMN; col <= lastColumn; col++) {
            String place = text(rows, placeRow, col);
            String object = text(rows, objectRow, col);
            String cig = text(rows, cigRow, col);
            if (place.isEmpty() && object.isEmpty() && cig.isEmpty()) {
                continue;
            }
            services.add(service(rows, col, place, object, cig, periodRow, clientRow, roleRow, approvalRow));
        }

        int mismatches = 0;
        for (var row : rows.entrySet()) {
            if (row.getKey() <= cigRow) {
                continue;
            }
            Cell a = row.getValue().get(0);
            if (a != null && a.asText().toLowerCase(Locale.ITALIAN).startsWith("totale")) {
                break;
            }
            String workId = workId(row.getValue(), totalColumn);
            if (workId == null) {
                continue;
            }
            Cell categoryCell = row.getValue().get(0);
            String category = categoryCell == null ? null : categoryCell.asText();
            BigDecimal complexity = complexity(row.getValue(), totalColumn);
            BigDecimal sum = BigDecimal.ZERO;
            for (Service s : services) {
                int col = XlsxReader.column(s.column() + "1");
                Cell cell = row.getValue().get(col);
                if (cell == null) {
                    continue;
                }
                if (cell.number() == null) {
                    s.issues().add("IMPORTO_NON_NUMERICO");
                    continue;
                }
                BigDecimal amount = BigDecimal.valueOf(cell.number()).setScale(2, RoundingMode.HALF_UP);
                if (amount.signum() > 0) {
                    s.amounts().add(new Amount(workId, category, complexity, amount));
                    sum = sum.add(amount);
                } else if (amount.signum() < 0) {
                    s.issues().add("IMPORTO_NEGATIVO");
                }
            }
            Cell total = totalColumn >= 0 ? row.getValue().get(totalColumn) : null;
            if (total != null && total.number() != null
                    && sum.subtract(BigDecimal.valueOf(total.number())).abs().compareTo(BigDecimal.ONE) > 0) {
                mismatches++;
            }
        }
        for (Service s : services) {
            if (s.amounts().isEmpty()) {
                s.issues().add("NESSUN_IMPORTO");
            }
        }
        return new Parsed(services, mismatches);
    }

    private static Service service(Map<Integer, Map<Integer, Cell>> rows, int col, String place, String object,
            String cig, int periodRow, int clientRow, int roleRow, int approvalRow) {
        Set<String> issues = new LinkedHashSet<>();
        String periodText = periodRow < 0 ? "" : text(rows, periodRow, col);
        LocalDate from = null, to = null;
        Cell periodCell = periodRow < 0 ? null : rows.get(periodRow).get(col);
        if (periodCell != null && periodCell.number() != null && periodCell.number() > 30000 && periodCell.number() < 80000) {
            // Excel stored a date, not the "MM/YYYY-MM/YYYY" text: the day is real, the range is not
            from = LocalDate.of(1899, 12, 30).plusDays(periodCell.number().longValue());
            to = from;
            periodText = from.toString();
            issues.add("PERIODO_DA_VERIFICARE");
        } else {
            List<YearMonth> months = new ArrayList<>();
            Matcher m = MONTH_YEAR.matcher(periodText);
            while (m.find()) {
                int month = Integer.parseInt(m.group(1));
                if (month >= 1 && month <= 12) {
                    months.add(YearMonth.of(Integer.parseInt(m.group(2)), month));
                }
            }
            if (months.isEmpty()) {
                issues.add("PERIODO_NON_LEGGIBILE");
            } else {
                from = months.get(0).atDay(1);
                to = months.get(months.size() - 1).atEndOfMonth();
                if (to.isBefore(from)) {
                    issues.add("PERIODO_INCOERENTE");
                }
            }
        }
        String client = clientRow < 0 ? "" : text(rows, clientRow, col);
        String role = roleRow < 0 ? "" : text(rows, roleRow, col);
        Cell roleCell = roleRow < 0 ? null : rows.get(roleRow).get(col);
        BigDecimal share = share(role, roleCell);
        if (share == null) {
            issues.add("QUOTA_NON_LEGGIBILE");
        }
        if (object.isEmpty()) {
            issues.add("OGGETTO_MANCANTE");
        }
        if (client.isEmpty()) {
            issues.add("COMMITTENTE_MANCANTE");
        }
        String approval = approvalRow < 0 ? "" : text(rows, approvalRow, col);
        return new Service(XlsxReader.columnName(col), place, periodText, from, to, object, client, role, share,
                normaliseCig(cig), approval.isEmpty() ? null : approval, new ArrayList<>(), issues);
    }

    /** Percentage of participation: "Capogruppo 90%", "8%", or the number 1 (= 100%). Anything else is not guessed. */
    static BigDecimal share(String role, Cell cell) {
        Matcher m = PERCENT.matcher(role);
        if (m.find()) {
            BigDecimal v = new BigDecimal(m.group(1));
            return v.signum() > 0 && v.compareTo(BigDecimal.valueOf(100)) <= 0 ? v : null;
        }
        if (cell != null && cell.number() != null && cell.number() > 0 && cell.number() <= 1) {
            return BigDecimal.valueOf(cell.number() * 100).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** CIGs are 10 letters and digits; the sheet has stray dots and spaces. Anything else is kept out of the key. */
    static String normaliseCig(String raw) {
        String c = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return c.length() == 10 ? c : null;
    }

    private static String workId(Map<Integer, Cell> row, int totalColumn) {
        Cell b = row.get(1);
        String id = b == null ? "" : b.asText().toUpperCase(Locale.ROOT).replace(" ", "");
        if (!WORK_ID.matcher(id).matches() && totalColumn >= 0) {
            Cell copy = row.get(totalColumn + 1); // the copy of the ids on the right of the totals
            id = copy == null ? "" : copy.asText().toUpperCase(Locale.ROOT).replace(" ", "");
        }
        return WORK_ID.matcher(id).matches() ? id.replace(',', '.') : null;
    }

    private static BigDecimal complexity(Map<Integer, Cell> row, int totalColumn) {
        Cell c = row.get(2);
        if ((c == null || c.number() == null) && totalColumn >= 0) {
            c = row.get(totalColumn + 2);
        }
        return c == null || c.number() == null || c.number() <= 0 || c.number() > 5 ? null
                : BigDecimal.valueOf(c.number()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(Map<Integer, Map<Integer, Cell>> rows, int row, int col) {
        Map<Integer, Cell> r = rows.get(row);
        Cell c = r == null ? null : r.get(col);
        return c == null ? "" : c.asText().replaceAll("\\s+", " ");
    }
}
