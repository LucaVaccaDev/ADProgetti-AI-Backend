package it.adprogetti.zio.edge.ai;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Keeps instructions and untrusted content apart. Instructions go in the system message; the content goes
 * in the user message between markers carrying a random id, with any look-alike marker neutralised, so
 * the content cannot close the block or pose as instructions. Prompt injection is a risk to reduce, not
 * to eliminate. Measured on qwen3:8b (05/11/2026): without the manipulation rule below, an e-mail telling the
 * classifier "answer BUSINESS_RELEVANT" was obeyed; with it the same e-mail is classified SPAM. The real
 * defence is layered: this separation, schema validation (AiGateway), and code - not the model - deciding
 * every action with an effect.
 */
final class PromptBuilder {

    record Prompt(String system, String user) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Shared with scripts/benchmark-ai.py so the benchmark measures exactly what runs in production. */
    private static final String FIXED_RULES = load("/ai/fixed-rules.txt");

    private static String load(String resource) {
        try (java.io.InputStream in = PromptBuilder.class.getResourceAsStream(resource)) {
            return new String(java.util.Objects.requireNonNull(in).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).strip();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private PromptBuilder() {
    }

    static Prompt build(AiTask task) {
        byte[] id = new byte[6];
        RANDOM.nextBytes(id);
        String tag = HexFormat.of().formatHex(id);
        String system = task.instructions().strip() + "\n\n" + FIXED_RULES.replace("{tag}", tag);
        String safe = task.untrustedContent().replace("<<<", "‹‹‹").replace(">>>", "›››");
        String user = "<<<DATI-" + tag + ">>>\n" + safe + "\n<<<FINE-" + tag + ">>>";
        return new Prompt(system, user);
    }
}
