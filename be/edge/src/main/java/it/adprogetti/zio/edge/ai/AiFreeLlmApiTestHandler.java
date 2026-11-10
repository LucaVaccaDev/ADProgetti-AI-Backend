package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.command.CommandType;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.payload.AiExternalTestResult;
import it.adprogetti.zio.contracts.payload.Payloads;
import org.springframework.stereotype.Component;

/**
 * {@code ai.freellmapi.test}: one fixed harmless sentence through FreeLLMAPI, with no local fallback, so the
 * administrator sees whether the external provider really works and which model served the request.
 */
@Component
class AiFreeLlmApiTestHandler implements it.adprogetti.zio.edge.command.CommandHandler {

    private final ExternalAiService external;

    AiFreeLlmApiTestHandler(ExternalAiService external) {
        this.external = external;
    }

    @Override
    public CommandType type() {
        return CommandType.AI_FREELLMAPI_TEST;
    }

    @Override
    public String handle(HubCommand command, Object payload) {
        ExternalAiService.Answer a = external.ask(ExternalAiService.Purpose.SELF_TEST, command.requestedBy(), null, null);
        return Payloads.write(new AiExternalTestResult(a.ok(), a.routedVia(), a.latencyMs(), a.ok() ? null : a.reason()));
    }
}
