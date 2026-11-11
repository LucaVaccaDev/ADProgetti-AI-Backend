package it.adprogetti.zio.edge.tenders;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches the tenders published on TED in the last days that match the configured filters and stores them. Pure code,
 * no AI: the filter is a query (country, CPV prefixes, notice types, publication date) plus optional title keywords.
 * A notice that cannot be understood is counted and skipped. Nothing about the studio goes into the query.
 */
@Service
public class TenderSyncService {

    public record Result(String status, int fetched, int added, int skipped, int pages) {
    }

    private static final Logger log = LoggerFactory.getLogger(TenderSyncService.class);
    private static final DateTimeFormatter TED_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final TedProperties props;
    private final TedClient client;
    private final TenderStore store;
    private final TendersProjector projector;
    private final Clock clock;

    TenderSyncService(TedProperties props, TedClient client, TenderStore store, TendersProjector projector, Clock clock) {
        this.props = props;
        this.client = client;
        this.store = store;
        this.projector = projector;
        this.clock = clock;
    }

    boolean enabled() {
        return props.enabled();
    }

    String query() {
        LocalDate since = LocalDate.now(clock.withZone(ZoneId.of("Europe/Rome"))).minusDays(props.lookbackDays());
        String cpv = props.cpvPrefixes().stream().map(p -> "classification-cpv=" + p + "*").collect(Collectors.joining(" OR "));
        String types = String.join(" ", props.noticeTypes());
        return "buyer-country=" + props.country() + " AND (" + cpv + ") AND notice-type IN (" + types
                + ") AND publication-date>=" + since.format(TED_DATE);
    }

    /** @param checkpoint called between pages: it throws when the job was cancelled */
    public Result run(Runnable checkpoint) throws TedUnavailableException {
        if (!props.enabled()) {
            return new Result("DISABLED", 0, 0, 0, 0);
        }
        store.startSync(TenderStore.SOURCE_TED);
        int fetched = 0;
        int added = 0;
        int skipped = 0;
        int page = 1;
        try {
            while (page <= props.maxPages()) {
                checkpoint.run();
                TedClient.Page result = client.search(query(), page, props.pageSize());
                for (var notice : result.notices()) {
                    fetched++;
                    var row = TedMapper.map(notice).filter(this::matchesKeywords);
                    if (row.isEmpty()) {
                        skipped++;
                    } else if (store.upsert(row.get())) {
                        added++;
                    }
                }
                if (result.notices().size() < props.pageSize()) {
                    break;
                }
                page++;
                pause();
            }
            store.purgeExpired();
            store.finishSync(TenderStore.SOURCE_TED, "OK", fetched, added, null);
            return new Result("OK", fetched, added, skipped, Math.min(page, props.maxPages()));
        } catch (TedUnavailableException e) {
            store.finishSync(TenderStore.SOURCE_TED, "FAILED", fetched, added, e.code());
            throw e;
        } catch (RuntimeException e) {
            store.finishSync(TenderStore.SOURCE_TED, "FAILED", fetched, added, e instanceof it.adprogetti.zio.edge.jobs.JobCancelledException
                    ? "CANCELLED" : "EXECUTION_ERROR");
            throw e;
        } finally {
            projector.publishSoon();
            log.info("Tender sync: {} fetched, {} new", fetched, added);
        }
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
            throw new it.adprogetti.zio.edge.jobs.JobCancelledException();
        }
    }
}
