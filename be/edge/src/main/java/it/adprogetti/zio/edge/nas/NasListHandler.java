package it.adprogetti.zio.edge.nas;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.NasListPayload;
import it.adprogetti.zio.contracts.payload.NasListResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code nas.list}: read-only listing of NAS folders for the administration page. The mode is fixed to
 * INDEX_READ here, whatever the Hub asks: the Hub cannot widen it. All path handling stays in {@link NasReader}
 * (and so in {@code NasPathResolver}); this class only paginates and maps errors to short codes. An empty path
 * lists the roots allowed for INDEX_READ. Names and metadata only, never contents.
 */
@Component
class NasListHandler implements CommandHandler {

    private final NasReader reader;
    private final NasProperties props;
    private final EdgeAudit audit;

    NasListHandler(NasReader reader, NasProperties props, EdgeAudit audit) {
        this.reader = reader;
        this.props = props;
        this.audit = audit;
    }

    @Override
    public CommandType type() {
        return CommandType.NAS_LIST;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        NasListPayload request = (NasListPayload) payload;
        try {
            List<NasListResult.Entry> all = request.path().isBlank() ? roots() : folder(request.path());
            int from = Math.min(request.offset(), all.size());
            int to = Math.min(from + request.limit(), all.size());
            return Payloads.write(new NasListResult(request.path(), all.subList(from, to), all.size(), to < all.size()));
        } catch (NasException e) {
            audit.record("NAS_LIST", request.path(), EdgeAudit.FAILURE, e.code().name());
            throw new CommandFailure(e.code().name());
        }
    }

    private List<NasListResult.Entry> roots() {
        return props.modes().getOrDefault(NasMode.INDEX_READ, List.of()).stream().sorted()
                .map(root -> new NasListResult.Entry(root, true, 0, "")).toList();
    }

    private List<NasListResult.Entry> folder(String path) {
        return reader.list(NasMode.INDEX_READ, path).stream()
                .map(e -> new NasListResult.Entry(e.name(), e.directory(), e.size(), e.modifiedAt().toString()))
                .toList();
    }
}
