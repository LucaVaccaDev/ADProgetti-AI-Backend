package it.adprogetti.zio.edge.ai;

import it.adprogetti.zio.contracts.payload.AiFunction;
import it.adprogetti.zio.contracts.payload.AiModelTestResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Quick functional test of a model for a function, with synthetic text. It checks that the model answers in
 * the expected structure, not that it is accurate: accuracy is measured by the benchmark on real data.
 */
@Component
public class ModelSelfTest {

    private static final String EMAIL_SAMPLE = "Oggetto: Richiesta di preventivo\n\nBuongiorno, vorremmo un preventivo "
            + "per la progettazione di una piccola ristrutturazione. Cordiali saluti, Mario Rossi.";
    private static final String INVOICE_SAMPLE = "Fattura n. 12/2026 del 3 marzo 2026. Totale da pagare: 1.500,00 euro.";

    private final AiGateway gateway;

    ModelSelfTest(AiGateway gateway) {
        this.gateway = gateway;
    }

    public AiModelTestResult run(String model, AiFunction function, String requestedBy) {
        return switch (function) {
            case CLASSIFICATION -> from(model, function, gateway.structured(new AiTask(function,
                    "Classifica l'e-mail in una delle categorie ammesse. Rispondi solo con il JSON richiesto.",
                    EMAIL_SAMPLE, EmailClassification.SCHEMA, 100, Priority.HIGH, requestedBy), model,
                    EmailClassification.class));
            case EXTRACTION -> from(model, function, gateway.structured(new AiTask(function,
                    "Estrai il numero della fattura e l'importo totale in euro. Rispondi solo con il JSON richiesto.",
                    INVOICE_SAMPLE, InvoiceExtraction.SCHEMA, 100, Priority.HIGH, requestedBy), model,
                    InvoiceExtraction.class));
            case ANALYSIS, DRAFTING -> from(model, function, gateway.text(new AiTask(function,
                    "Rispondi con la sola parola: ok.", "test", null, 20, Priority.HIGH, requestedBy), model));
            case EMBEDDING -> from(model, function, gateway.embed(List.of("prova"), model, Priority.HIGH, requestedBy));
            // No sample image is bundled yet: say so instead of reporting a pass that did not happen.
            case IMAGES -> new AiModelTestResult(model, function, false, 0, 0, "NOT_TESTABLE");
        };
    }

    private static AiModelTestResult from(String model, AiFunction function, AiOutcome<?> outcome) {
        return new AiModelTestResult(model, function, outcome.ok(), outcome.latencyMs(), outcome.tokensPerSecond(),
                outcome.ok() ? null : outcome.reason());
    }
}
