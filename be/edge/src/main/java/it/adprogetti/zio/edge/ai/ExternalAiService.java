package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * The only way to FreeLLMAPI (Allegato A, D-04): free external AI for general technical questions, never for
 * studio data. Code decides, in this order: purpose allowed -> filter (A.2) -> daily limit per user -> call ->
 * local fallback. A text the filter blocks is NEVER sent: the caller is told to use the local AI. When the
 * provider is unavailable or the user's quota is used up, the question goes to the local AI instead, and the
 * answer says so. Photo retouching and public tender documents (B.4) will be added with their modules.
 */
@Service
public class ExternalAiService {

    /** Allowed uses. SELF_TEST sends one fixed sentence, never user text. */
    public enum Purpose { GENERAL_SEARCH, SELF_TEST, TENDER_DOCUMENT }

    public enum Status { OK, BLOCKED, UNAVAILABLE }

    public enum Source { EXTERNAL, LOCAL }

    /** Label the interface shows next to an external answer (Allegato A.2). */
    public static final String SOURCE_LABEL = "Fonte: servizi AI esterni gratuiti";

    /** @param reason short code: filter category, QUOTA, or why the external service could not answer */
    public record Answer(Status status, Source source, String text, String routedVia, String reason, long latencyMs) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private static final String INSTRUCTIONS = "Sei un assistente tecnico per uno studio di ingegneria e architettura. "
            + "Rispondi in italiano, in modo chiaro, conciso e prudente, a domande di cultura tecnica generale, "
            + "normativa pubblica, prodotti e materiali. Non hai accesso ai documenti, alle commesse né alle email "
            + "dello studio: se la domanda li riguarda, dillo e non inventare.";
    private static final String SELF_TEST_TEXT = "Rispondi soltanto con la parola OK.";

    private final FreeLlmApiClient client;
    private final FreeLlmApiProperties props;
    private final SensitiveTermsSource sensitive;
    private final AiUsageLog usage;
    private final AiGateway local;

    ExternalAiService(FreeLlmApiClient client, FreeLlmApiProperties props, SensitiveTermsSource sensitive,
            AiUsageLog usage, AiGateway local) {
        this.client = client;
        this.props = props;
        this.sensitive = sensitive;
        this.usage = usage;
        this.local = local;
    }

    public Answer ask(Purpose purpose, String requestedBy, String conversationId, String question) {
        if (purpose == Purpose.SELF_TEST) {
            return callExternal(purpose, requestedBy, conversationId, SELF_TEST_TEXT);
        }
        ExternalAiFilter.Verdict verdict = ExternalAiFilter.check(question, sensitive.terms(), props.maxQuestionLength());
        if (!verdict.allowed()) {
            usage.recordExternal(purpose.name(), null, requestedBy, "BLOCKED", verdict.reason(), 0, 0, 0);
            return new Answer(Status.BLOCKED, null, null, null, verdict.reason(), 0);
        }
        if (usage.externalSearchesToday(requestedBy) >= props.dailyLimitPerUser()) {
            usage.recordExternal(purpose.name(), null, requestedBy, "QUOTA", "QUOTA", 0, 0, 0);
            return fallbackToLocal(requestedBy, question, "QUOTA");
        }
        Answer external = callExternal(purpose, requestedBy, conversationId, question);
        return external.ok() ? external : fallbackToLocal(requestedBy, question, external.reason());
    }

    /**
     * Reads a PUBLIC tender document (capitolato B.4/E.2; the committente confirmed it on 11/11/2026). The text of a
     * public tender is not studio data, so the A.2 filter (which would block the RUP's e-mail or VAT number) is not
     * applied; what protects the studio is that the caller sends only the document text, never studio data. No
     * local fallback and no per-user limit: the caller handles an unavailable answer. The call is logged by purpose.
     */
    public Answer readPublicDocument(String requestedBy, String instructions, String documentText, int maxTokens) {
        long start = System.nanoTime();
        PromptBuilder.Prompt prompt = PromptBuilder.build(new AiTask(AiFunction.ANALYSIS, instructions, documentText, null,
                maxTokens, Priority.NORMAL, requestedBy));
        try {
            FreeLlmApiClient.Answer r = client.chat(props.textProfile(), prompt.system(), prompt.user(), maxTokens,
                    userTag(requestedBy, "tender-doc"));
            long ms = elapsed(start);
            usage.recordExternal(Purpose.TENDER_DOCUMENT.name(), r.routedVia(), requestedBy, "OK", null, ms,
                    r.promptTokens(), r.completionTokens());
            return new Answer(Status.OK, Source.EXTERNAL, r.content().strip(), r.routedVia(), null, ms);
        } catch (AiUnavailableException | AiRequestException e) {
            long ms = elapsed(start);
            String code = e instanceof AiUnavailableException u ? u.code() : ((AiRequestException) e).code();
            usage.recordExternal(Purpose.TENDER_DOCUMENT.name(), null, requestedBy, "UNAVAILABLE", code, ms, 0, 0);
            return new Answer(Status.UNAVAILABLE, null, null, null, code, ms);
        }
    }

    private Answer callExternal(Purpose purpose, String requestedBy, String conversationId, String text) {
        long start = System.nanoTime();
        PromptBuilder.Prompt prompt = PromptBuilder.build(
                new AiTask(AiFunction.ANALYSIS, INSTRUCTIONS, text, null, 800, Priority.NORMAL, requestedBy));
        try {
            FreeLlmApiClient.Answer r = client.chat(props.textProfile(), prompt.system(), prompt.user(), 800,
                    userTag(requestedBy, conversationId));
            long ms = elapsed(start);
            usage.recordExternal(purpose.name(), r.routedVia(), requestedBy, "OK", null, ms, r.promptTokens(),
                    r.completionTokens());
            return new Answer(Status.OK, Source.EXTERNAL, r.content().strip(), r.routedVia(), null, ms);
        } catch (AiUnavailableException | AiRequestException e) {
            long ms = elapsed(start);
            String code = e instanceof AiUnavailableException u ? u.code() : ((AiRequestException) e).code();
            usage.recordExternal(purpose.name(), null, requestedBy, "UNAVAILABLE", code, ms, 0, 0);
            return new Answer(Status.UNAVAILABLE, null, null, null, code, ms);
        }
    }

    private Answer fallbackToLocal(String requestedBy, String question, String why) {
        AiOutcome<String> r = local.text(new AiTask(AiFunction.ANALYSIS, INSTRUCTIONS, question, null, 800,
                Priority.NORMAL, requestedBy));
        return r.ok()
                ? new Answer(Status.OK, Source.LOCAL, r.value().strip(), r.model(), why, r.latencyMs())
                : new Answer(Status.UNAVAILABLE, null, null, null, why, r.latencyMs());
    }

    /** Opaque id per user and conversation: never the user id itself. */
    static String userTag(String requestedBy, String conversationId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((requestedBy + "|" + (conversationId == null ? "" : conversationId))
                            .getBytes(StandardCharsets.UTF_8));
            return "u-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
