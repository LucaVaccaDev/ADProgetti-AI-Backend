package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.AiModelsResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import org.springframework.stereotype.Component;

@Component
class AiModelsListHandler implements CommandHandler {

    private final ModelCatalog catalog;
    private final AiProjector projector;

    AiModelsListHandler(ModelCatalog catalog, AiProjector projector) {
        this.catalog = catalog;
        this.projector = projector;
    }

    @Override
    public CommandType type() {
        return CommandType.AI_MODELS_LIST;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        try {
            AiModelsResult result = catalog.snapshot();
            projector.publishModels(result);
            return Payloads.write(result);
        } catch (AiUnavailableException e) {
            throw new CommandFailure("AI_UNAVAILABLE");
        }
    }
}
