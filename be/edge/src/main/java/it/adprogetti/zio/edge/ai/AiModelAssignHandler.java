package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.AiModelAssignPayload;
import it.adprogetti.zio.contracts.payload.AiModelAssignResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import org.springframework.stereotype.Component;

/** Only a model that is really installed can be assigned. */
@Component
class AiModelAssignHandler implements CommandHandler {

    private final ModelCatalog catalog;
    private final ModelAssignments assignments;
    private final AiProjector projector;

    AiModelAssignHandler(ModelCatalog catalog, ModelAssignments assignments, AiProjector projector) {
        this.catalog = catalog;
        this.assignments = assignments;
        this.projector = projector;
    }

    @Override
    public CommandType type() {
        return CommandType.AI_MODEL_ASSIGN;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        AiModelAssignPayload request = (AiModelAssignPayload) payload;
        try {
            if (!catalog.isInstalled(request.model())) {
                throw new CommandFailure("MODEL_NOT_INSTALLED");
            }
        } catch (AiUnavailableException e) {
            throw new CommandFailure("AI_UNAVAILABLE");
        }
        AiModelAssignResult result = assignments.assign(request.function(), request.model(), command.requestedBy());
        try {
            projector.publishModels(catalog.snapshot());
        } catch (AiUnavailableException ignored) {
            // the assignment is saved; the projection is refreshed at the next cycle
        }
        return Payloads.write(result);
    }
}
