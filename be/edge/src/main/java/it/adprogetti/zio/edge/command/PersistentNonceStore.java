package it.adprogetti.zio.edge.command;

import it.adprogetti.zio.contracts.command.NonceStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Used nonces survive restarts. Called inside the intake transaction, so nonce and inbox row commit together. */
@Component
class PersistentNonceStore implements NonceStore {

    private final JdbcClient jdbc;
    private final Clock clock;

    PersistentNonceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean markSeen(String nonce, long expiresAtEpochMs) {
        if (nonce.length() > 100) {
            return false;
        }
        return jdbc.sql("""
                insert into nonce_seen (nonce, expires_at) values (:n, :e) on conflict (nonce) do nothing
                """).param("n", nonce).param("e", new Timestamp(expiresAtEpochMs)).update() == 1;
    }

    /** Expired nonces can be forgotten: an expired command is refused anyway. */
    @Scheduled(fixedDelay = 3_600_000)
    void cleanup() {
        jdbc.sql("delete from nonce_seen where expires_at < :cut")
                .param("cut", Timestamp.from(clock.instant().minus(Duration.ofHours(1)))).update();
    }
}
