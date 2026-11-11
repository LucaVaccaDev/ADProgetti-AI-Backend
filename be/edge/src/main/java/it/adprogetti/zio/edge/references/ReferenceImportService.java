package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.nas.NasMode;
import it.adprogetti.zio.edge.nas.NasReader;
import it.adprogetti.zio.edge.references.ReferenceSheetParser.Parsed;
import it.adprogetti.zio.edge.references.ReferenceSheetParser.Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports the studio's references from the Excel. Idempotent: the same file twice changes nothing. A reference an
 * operator already approved or discarded keeps that decision unless the sheet now says something different, in which
 * case it goes back to DA_VERIFICARE. Nothing is read from AI: the sheet is parsed by code.
 */
@org.springframework.stereotype.Service
public class ReferenceImportService {

    /** Counts only (no names, no amounts): this is what the job result and the audit keep. */
    public record Result(String status, String errorCode, int services, int created, int updated, int unchanged,
            int toVerify, int changedAfterApproval, int amounts, int totalMismatches) {
        static Result failed(String code) {
            return new Result("FAILED", code, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private final ReferenceProperties props;
    private final NasReader nas;
    private final ReferenceStore store;
    private final EdgeAudit audit;

    ReferenceImportService(ReferenceProperties props, NasReader nas, ReferenceStore store, EdgeAudit audit) {
        this.props = props;
        this.nas = nas;
        this.store = store;
        this.audit = audit;
    }

    /** Reads the file configured in {@code edge.references.file}. */
    public Result run(String requestedBy) {
        if (props.file() == null || props.file().isBlank()) {
            return fail("NOT_CONFIGURED", requestedBy);
        }
        try (InputStream in = nas.open(NasMode.INDEX_READ, props.file())) {
            return importSheet(XlsxReader.read(in, props.sheet()), props.file(), requestedBy);
        } catch (it.adprogetti.zio.edge.nas.NasException e) {
            return fail("NAS_" + e.code(), requestedBy);
        } catch (XlsxReader.XlsxException e) {
            return fail(e.getMessage(), requestedBy);
        } catch (IOException e) {
            return fail("UNAVAILABLE", requestedBy);
        }
    }

    @Transactional
    Result importSheet(XlsxReader.Sheet sheet, String file, String requestedBy) {
        Parsed parsed;
        try {
            parsed = ReferenceSheetParser.parse(sheet);
        } catch (ReferenceSheetParser.LayoutException e) {
            return fail(e.getMessage(), requestedBy);
        }
        int created = 0, updated = 0, unchanged = 0, toVerify = 0, changedAfterApproval = 0, amounts = 0;
        Set<String> keys = new HashSet<>();
        for (Service s : parsed.services()) {
            String key = key(s);
            if (!keys.add(key)) { // the same CIG in two columns: never merge two services silently
                s.issues().add("CHIAVE_DUPLICATA");
                key = key + "#" + s.column();
                keys.add(key);
            }
            String hash = ReferenceStore.hash(s);
            String status = s.issues().isEmpty() ? "DA_APPROVARE" : "DA_VERIFICARE";
            amounts += s.amounts().size();
            var existing = store.find(key);
            if (existing.isEmpty()) {
                long id = store.insert(key, s, status, hash, file, sheet.name());
                store.replaceAmounts(id, s.amounts());
                created++;
            } else if (existing.get().contentHash().equals(hash)) {
                unchanged++;
            } else {
                String keep = existing.get().status();
                boolean decided = "APPROVATA".equals(keep) || "SCARTATA".equals(keep);
                if (decided) {
                    status = "DA_VERIFICARE";
                    s.issues().add("MODIFICATA_DOPO_APPROVAZIONE");
                    changedAfterApproval++;
                }
                store.update(existing.get().id(), s, status, hash, file, sheet.name());
                store.replaceAmounts(existing.get().id(), s.amounts());
                updated++;
            }
            if ("DA_VERIFICARE".equals(status)) {
                toVerify++;
            }
        }
        Result result = new Result("OK", null, parsed.services().size(), created, updated, unchanged, toVerify,
                changedAfterApproval, amounts, parsed.totalMismatches());
        audit.record("REFERENCES_IMPORT", "sheet " + sheet.name(), EdgeAudit.SUCCESS, "by " + requestedBy + ": "
                + result.services() + " services, " + created + " new, " + updated + " changed, " + toVerify + " to verify");
        return result;
    }

    private Result fail(String code, String requestedBy) {
        audit.record("REFERENCES_IMPORT", "references", EdgeAudit.FAILURE, "by " + requestedBy + ": " + code);
        return Result.failed(code);
    }

    private static String key(Service s) {
        if (s.cig() != null) {
            return s.cig();
        }
        String basis = s.place() + "|" + s.periodText() + "|" + s.object();
        return "H-" + ReferenceStore.sha(basis).substring(0, 24);
    }
}
