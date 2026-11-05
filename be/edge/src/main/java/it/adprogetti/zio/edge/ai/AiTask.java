package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;

/**
 * A request to the local AI. {@code instructions} are written by the Edge code; {@code untrustedContent} is
 * e-mail text, a PDF, a tender notice, a web page: data, never instructions (CLAUDE.md section 8).
 * {@code schemaJson} (optional) constrains the answer.
 */
public record AiTask(AiFunction function, String instructions, String untrustedContent, String schemaJson,
        int maxTokens, Priority priority, String requestedBy) {

    public AiTask {
        if (function == null || instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("function and instructions are required");
        }
        untrustedContent = untrustedContent == null ? "" : untrustedContent;
        maxTokens = maxTokens <= 0 ? 512 : maxTokens;
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
