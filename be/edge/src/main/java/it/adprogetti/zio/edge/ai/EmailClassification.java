package it.adprogetti.zio.edge.ai;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Expected AI answer when classifying an e-mail: a category from the closed list and an optional confidence. */
public record EmailClassification(EmailCategory category, Double confidence) {

    public EmailClassification {
        if (category == null) {
            throw new IllegalArgumentException("category required");
        }
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException("confidence out of range");
        }
    }

    /** JSON schema sent to the model so it can only answer in this shape. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"category":{"type":"string","enum":[%s]},"confidence":{"type":"number"}},
             "required":["category"],"additionalProperties":false}
            """.formatted(Arrays.stream(EmailCategory.values()).map(c -> "\"" + c.name() + "\"")
            .collect(Collectors.joining(",")));
}
