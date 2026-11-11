package it.adprogetti.zio.edge.tenders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A tender as stored: what the Edge keeps of a public notice. {@code url} is null for TED (built from the number). */
record TenderRow(String number, String title, String buyer, BigDecimal amount, String currency, Instant deadline,
        LocalDate published, String procedure, String noticeType, List<String> cpv, String place, int lots, String source, String url) {
}
