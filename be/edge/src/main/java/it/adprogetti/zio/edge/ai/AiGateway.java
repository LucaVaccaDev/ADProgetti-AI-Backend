package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.InvalidPayloadException;
import it.adprogetti.zio.contracts.payload.Payloads;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The only door to the local AI (D-04). It chooses the model for the function, queues the request in the
 * right lane, validates the answer against the expected structure and records usage metadata.
 * An invalid answer is DA_VERIFICARE: never repaired, never guessed (CLAUDE.md section 8).
 */
@Service
public class AiGateway {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)^\\s*<think>.*?</think>\\s*");

    private final OllamaClient ollama;
    private final ModelAssignments assignments;
    private final AiQueue queue;
    private final AiUsageLog usage;

    AiGateway(OllamaClient ollama, ModelAssignments assignments, AiQueue queue, AiUsageLog usage) {
        this.ollama = ollama;
        this.assignments = assignments;
        this.queue = queue;
        this.usage = usage;
    }

    /** Structured answer using the model assigned to the task's function. */
    public <T> AiOutcome<T> structured(AiTask task, Class<T> type) {
        Optional<String> model = assignments.effective(task.function());
        if (model.isEmpty()) {
            return noModel(task);
        }
        return structured(task, model.get(), type);
    }

    /** Structured answer with an explicit model (used to test a model before assigning it). */
    public <T> AiOutcome<T> structured(AiTask task, String model, Class<T> type) {
        return execute(task, model, content -> {
            try {
                return Optional.of(Payloads.read(content, type));
            } catch (InvalidPayloadException e) {
                return Optional.empty();
            }
        });
    }

    /** Free text answer (drafts, summaries). The caller decides how to treat it; it is still data. */
    public AiOutcome<String> text(AiTask task) {
        Optional<String> model = assignments.effective(task.function());
        if (model.isEmpty()) {
            return noModel(task);
        }
        return text(task, model.get());
    }

    public AiOutcome<String> text(AiTask task, String model) {
        return execute(task, model, content -> content.isBlank() ? Optional.empty() : Optional.of(content));
    }

    /** Embeddings with the model assigned to EMBEDDING. */
    public AiOutcome<float[][]> embed(List<String> texts, Priority priority, String requestedBy) {
        Optional<String> model = assignments.effective(AiFunction.EMBEDDING);
        if (model.isEmpty()) {
            usage.record(AiFunction.EMBEDDING, "none", requestedBy, "UNAVAILABLE", "NOT_ASSIGNED", 0, 0, 0);
            return AiOutcome.unavailable("NOT_ASSIGNED", null, 0);
        }
        return embed(texts, model.get(), priority, requestedBy);
    }

    public AiOutcome<float[][]> embed(List<String> texts, String model, Priority priority, String requestedBy) {
        long start = System.nanoTime();
        try {
            OllamaClient.EmbedResponse r = queue.run(AiQueue.Lane.LIGHT, priority, () -> ollama.embed(model, texts));
            long ms = elapsed(start);
            usage.record(AiFunction.EMBEDDING, model, requestedBy, "OK", null, ms, r.promptTokens(), 0);
            boolean valid = r.vectors().length == texts.size() && r.vectors().length > 0 && r.vectors()[0].length > 0;
            return valid ? new AiOutcome<>(AiOutcome.Status.OK, r.vectors(), null, model, ms, r.promptTokens(), 0, 0)
                    : new AiOutcome<>(AiOutcome.Status.DA_VERIFICARE, null, "INVALID_OUTPUT", model, ms,
                            r.promptTokens(), 0, 0);
        } catch (AiUnavailableException | AiRequestException e) {
            return failed(AiFunction.EMBEDDING, model, requestedBy, code(e), elapsed(start));
        }
    }

    private <T> AiOutcome<T> execute(AiTask task, String model, java.util.function.Function<String, Optional<T>> parse) {
        long start = System.nanoTime();
        PromptBuilder.Prompt prompt = PromptBuilder.build(task);
        AiQueue.Lane lane = switch (task.function()) {
            case ANALYSIS, DRAFTING, IMAGES -> AiQueue.Lane.HEAVY;
            default -> AiQueue.Lane.LIGHT;
        };
        try {
            OllamaClient.ChatResponse r = queue.run(lane, task.priority(), () -> ollama.chat(model, prompt.system(),
                    prompt.user(), task.schemaJson(), task.maxTokens()));
            long ms = elapsed(start);
            String content = THINK_BLOCK.matcher(r.content()).replaceFirst("").strip();
            Optional<T> value = parse.apply(content);
            String outcome = value.isPresent() ? "OK" : "DA_VERIFICARE";
            usage.record(task.function(), model, task.requestedBy(), outcome, value.isPresent() ? null : "INVALID_OUTPUT",
                    ms, r.promptTokens(), r.completionTokens());
            return new AiOutcome<>(value.isPresent() ? AiOutcome.Status.OK : AiOutcome.Status.DA_VERIFICARE,
                    value.orElse(null), value.isPresent() ? null : "INVALID_OUTPUT", model, ms, r.promptTokens(),
                    r.completionTokens(), r.tokensPerSecond());
        } catch (AiUnavailableException | AiRequestException e) {
            return failed(task.function(), model, task.requestedBy(), code(e), elapsed(start));
        }
    }

    private <T> AiOutcome<T> noModel(AiTask task) {
        usage.record(task.function(), "none", task.requestedBy(), "UNAVAILABLE", "NOT_ASSIGNED", 0, 0, 0);
        return AiOutcome.unavailable("NOT_ASSIGNED", null, 0);
    }

    private <T> AiOutcome<T> failed(AiFunction function, String model, String by, String reason, long ms) {
        usage.record(function, model, by, "UNAVAILABLE", reason, ms, 0, 0);
        return AiOutcome.unavailable(reason, model, ms);
    }

    private static String code(RuntimeException e) {
        return e instanceof AiUnavailableException u ? u.code() : ((AiRequestException) e).code();
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
