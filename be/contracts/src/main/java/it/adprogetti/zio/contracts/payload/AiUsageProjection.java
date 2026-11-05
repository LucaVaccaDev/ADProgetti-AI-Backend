package it.adprogetti.zio.contracts.payload;

import java.util.List;

/**
 * Projection {@code ai.usage}: metadata only (CLAUDE.md section 4). Never prompts, answers, file names
 * or e-mail content. One row per day, function, model and outcome.
 */
public record AiUsageProjection(List<Row> rows) {

    public AiUsageProjection {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public record Row(String day, AiFunction function, String model, String outcome, long count,
            long totalLatencyMs, long promptTokens, long completionTokens) {
    }
}
