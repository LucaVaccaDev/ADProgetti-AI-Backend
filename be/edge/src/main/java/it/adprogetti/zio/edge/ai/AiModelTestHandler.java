package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.AiModelTestPayload;
import it.adprogetti.zio.contracts.payload.AiModelTestResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import it.adprogetti.zio.edge.command.CommandFailure;
import it.adprogetti.zio.edge.command.CommandHandler;
import org.springframework.stereotype.Component;

@Component
class AiModelTestHandler implements CommandHandler {

    private final ModelCatalog catalog;
    private final ModelSelfTest selfTest;

    AiModelTestHandler(ModelCatalog catalog, ModelSelfTest selfTest) {
        this.catalog = catalog;
        this.selfTest = selfTest;
    }

    @Override
    public CommandType type() {
        return CommandType.AI_MODEL_TEST;
    }

    @Override
    public String handle(HubCommand command, Object payload) throws CommandFailure {
        AiModelTestPayload request = (AiModelTestPayload) payload;
        try {
            if (!catalog.isInstalled(request.model())) {
                return Payloads.write(new AiModelTestResult(request.model(), request.function(), false, 0, 0,
                        "MODEL_NOT_INSTALLED"));
            }
            return Payloads.write(selfTest.run(request.model(), request.function(), command.requestedBy()));
        } catch (AiUnavailableException e) {
            throw new CommandFailure("AI_UNAVAILABLE");
        }
    }
}
