package it.adprogetti.zio.edge.docs;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.DocsSearchPayload;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.command.CommandHandler;
import org.springframework.stereotype.Component;

/**
 * {@code docs.search}: search in the document index. The Edge audits who searched and how long the query was, never
 * the query or the excerpts. The result is shown to the requester and erased by the Hub after a few minutes.
 */
@Component
class DocsSearchHandler implements CommandHandler {

    private final DocumentSearch search;
    private final EdgeAudit audit;

    DocsSearchHandler(DocumentSearch search, EdgeAudit audit) {
        this.search = search;
        this.audit = audit;
    }

    @Override
    public CommandType type() {
        return CommandType.DOCS_SEARCH;
    }

    @Override
    public String handle(HubCommand command, Object payload) {
        DocsSearchPayload request = (DocsSearchPayload) payload;
        var result = search.search(request.query(), request.limit(), command.requestedBy());
        if (!request.query().isEmpty()) {
            audit.record("DOCS_SEARCH", null, EdgeAudit.SUCCESS,
                    "by " + command.requestedBy() + ", query " + request.query().length() + " chars, "
                            + result.hits().size() + " hits");
        }
        return Payloads.write(result);
    }
}
