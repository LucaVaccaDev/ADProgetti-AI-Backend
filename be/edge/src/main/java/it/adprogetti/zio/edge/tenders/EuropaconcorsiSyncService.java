package it.adprogetti.zio.edge.tenders;

import it.adprogetti.zio.edge.jobs.JobCancelledException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Collects the Italian tenders listed on Europaconcorsi for the studio's account: the selected categories, page by
 * page, with a pause between requests. Facts only (see {@link EuropaconcorsiParser}). Pure code, no AI.
 */
@Service
public class EuropaconcorsiSyncService {

    public record Result(String status, int fetched, int added, int skipped, int pages) {
    }

    private static final Logger log = LoggerFactory.getLogger(EuropaconcorsiSyncService.class);
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final Duration KEEP_CLOSED = Duration.ofDays(60);

    private final EuropaconcorsiProperties props;
    private final EuropaconcorsiClient client;
    private final TenderStore store;
    private final TendersProjector projector;
    private final Clock clock;

    EuropaconcorsiSyncService(EuropaconcorsiProperties props, EuropaconcorsiClient client, TenderStore store,
            TendersProjector projector, Clock clock) {
        this.props = props;
        this.client = client;
        this.store = store;
        this.projector = projector;
        this.clock = clock;
    }

    /** @param checkpoint called between pages: it throws when the job was cancelled */
    public Result run(Runnable checkpoint) throws TedUnavailableException {
        if (!props.enabled()) {
            return new Result("DISABLED", 0, 0, 0, 0);
        }
        store.startSync(TenderStore.SOURCE_EC);
        java.time.Instant started = clock.instant();
        int fetched = 0;
        int added = 0;
        int skipped = 0;
        int pages = 0;
        try {
            var session = client.login();
            for (Map.Entry<String, String> category : props.categories().entrySet()) {
                int last = 1;
                for (int page = 1; page <= Math.min(last, props.maxPagesPerCategory()); page++) {
                    checkpoint.run();
                    pause();
                    String html = session.listPage(category.getKey(), page);
                    pages++;
                    last = EuropaconcorsiParser.lastPage(html);
                    var cards = EuropaconcorsiParser.parse(html);
                    for (var card : cards) {
                        fetched++;
                        TenderRow row = toRow(card, category.getKey(), category.getValue());
                        if (row == null || !matchesKeywords(row) || !isEngineering(category.getKey(), row.title())) {
                            skipped++;
                        } else if (store.upsert(row)) {
                            added++;
                        }
                    }
                    if (cards.isEmpty()) {
                        break;
                    }
                }
            }
            // A complete run is the truth about what the portal lists today: rows it no longer lists (or the filters no
            // longer accept) are dropped instead of lingering until their deadline.
            store.deleteUnseen(TenderStore.SOURCE_EC, started);
            store.purgeClosedBefore(clock.instant().minus(KEEP_CLOSED));
            store.finishSync(TenderStore.SOURCE_EC, "OK", fetched, added, null);
            return new Result("OK", fetched, added, skipped, pages);
        } catch (TedUnavailableException e) {
            store.finishSync(TenderStore.SOURCE_EC, "FAILED", fetched, added, e.code());
            throw e;
        } catch (RuntimeException e) {
            store.finishSync(TenderStore.SOURCE_EC, "FAILED", fetched, added,
                    e instanceof JobCancelledException ? "CANCELLED" : "EXECUTION_ERROR");
            throw e;
        } finally {
            projector.publishSoon();
            log.info("Europaconcorsi sync: {} read, {} new", fetched, added);
        }
    }

    private TenderRow toRow(EuropaconcorsiParser.Card card, String categoryId, String label) {
        if (card.title() == null) {
            return null;
        }
        var deadline = card.deadline() == null ? null : card.deadline().atTime(LocalTime.of(23, 59)).atZone(ROME).toInstant();
        String url = card.originUrl() != null ? card.originUrl() : client.detailUrl(card.path());
        return new TenderRow("EC-" + card.id(), card.title(), card.buyer(), null, null, deadline,
                LocalDate.now(clock.withZone(ROME)), label, "ec-" + categoryId, List.of(), card.place(), 1,
                TenderStore.SOURCE_EC, url);
    }

    /** Categories without a CPV code keep only technical-service titles (see {@link EuropaconcorsiProperties}). */
    boolean isEngineering(String categoryId, String title) {
        if (!props.keywordCategories().contains(categoryId)) {
            return true;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return props.engineeringKeywords().stream().anyMatch(k -> lower.contains(k.toLowerCase(Locale.ROOT)));
    }

    private boolean matchesKeywords(TenderRow row) {
        List<String> keywords = props.titleKeywords();
        if (keywords.isEmpty()) {
            return true;
        }
        String title = row.title().toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(k -> title.contains(k.toLowerCase(Locale.ROOT)));
    }

    private void pause() {
        try {
            Thread.sleep(props.pause().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobCancelledException();
        }
    }
}
