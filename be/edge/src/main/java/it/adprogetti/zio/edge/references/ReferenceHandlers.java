package it.adprogetti.zio.edge.references;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.contracts.payload.ReferencesDecidePayload;
import it.adprogetti.zio.contracts.payload.ReferencesListPayload;
import it.adprogetti.zio.contracts.payload.ReferencesResult;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The two commands on the references archive. The Hub cannot widen what they do: the rules are here. */
final class ReferenceHandlers {

    private ReferenceHandlers() {
    }

    @Component
    static class ListHandler implements CommandHandler {
        private final ReferenceStore store;

        ListHandler(ReferenceStore store) {
            this.store = store;
        }

        @Override
        public CommandType type() {
            return CommandType.REFERENCES_LIST;
        }

        @Override
        public String handle(HubCommand command, Object payload) {
            ReferencesListPayload p = (ReferencesListPayload) payload;
            return Payloads.write(new ReferencesResult(store.page(p.status(), p.offset(), p.limit()), store.counts(),
                    store.count(p.status())));
        }
    }

    @Component
    static class DecideHandler implements CommandHandler {
        private final ReferenceStore store;
        private final EdgeAudit audit;

        DecideHandler(ReferenceStore store, EdgeAudit audit) {
            this.store = store;
            this.audit = audit;
        }

        @Override
        public CommandType type() {
            return CommandType.REFERENCES_DECIDE;
        }

        /**
         * A reference whose data could not be read (DA_VERIFICARE) cannot be approved: the data must be fixed in the
         * Excel and imported again. Discarding it is allowed. Reopening goes back to DA_APPROVARE.
         */
        @Override
        @Transactional
        public String handle(HubCommand command, Object payload) throws CommandFailure {
            ReferencesDecidePayload p = (ReferencesDecidePayload) payload;
            String current = store.status(p.id()).orElseThrow(() -> new CommandFailure("NOT_FOUND"));
            if ("APPROVATA".equals(p.decision()) && !"DA_APPROVARE".equals(current) && !"APPROVATA".equals(current)) {
                audit.record("REFERENCE_DECISION", "reference " + p.id(), EdgeAudit.FAILURE,
                        "by " + command.requestedBy() + ": cannot approve from " + current);
                throw new CommandFailure("NOT_APPROVABLE");
            }
            if ("DA_APPROVARE".equals(p.decision()) && "DA_VERIFICARE".equals(current)) {
                throw new CommandFailure("NOT_APPROVABLE"); // unreadable data is fixed in the Excel, not by reopening
            }
            store.setStatus(p.id(), p.decision());
            audit.record("REFERENCE_DECISION", "reference " + p.id(), EdgeAudit.SUCCESS,
                    "by " + command.requestedBy() + ": " + current + " -> " + p.decision());
            var item = store.one(p.id()).orElseThrow(() -> new CommandFailure("NOT_FOUND"));
            return Payloads.write(new ReferencesResult(List.of(item), store.counts(), 1));
        }
    }
}
