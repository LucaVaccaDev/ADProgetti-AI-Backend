package it.adprogetti.zio.edge.ai;

/** Expected AI answer when extracting the main data of an invoice-like text. */
public record InvoiceExtraction(String numero, Double importoEuro) {

    public InvoiceExtraction {
        if (numero == null || numero.isBlank() || numero.length() > 40) {
            throw new IllegalArgumentException("numero required");
        }
        if (importoEuro == null || importoEuro < 0 || importoEuro > 1_000_000_000) {
            throw new IllegalArgumentException("importoEuro out of range");
        }
    }

    public static final String SCHEMA = """
            {"type":"object","properties":{"numero":{"type":"string"},"importoEuro":{"type":"number"}},
             "required":["numero","importoEuro"],"additionalProperties":false}
            """;
}
