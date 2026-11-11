package it.adprogetti.zio.contracts.payload;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Payload of {@code references.summary}. {@code asOf} (yyyy-MM-dd) is the reference date, today when empty. */
public record ReferencesSummaryPayload(String asOf) {

    public ReferencesSummaryPayload {
        asOf = asOf == null || asOf.isBlank() ? null : asOf.strip();
        if (asOf != null) {
            try {
                LocalDate d = LocalDate.parse(asOf);
                if (d.getYear() < 2000 || d.getYear() > 2100) {
                    throw new IllegalArgumentException("Invalid date");
                }
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date", e);
            }
        }
    }
}
