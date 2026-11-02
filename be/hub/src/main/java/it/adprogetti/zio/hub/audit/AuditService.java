package it.adprogetti.zio.hub.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries in their own transaction, so they survive a rollback of the caller. */
@Service
public class AuditService {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    private final AuditLogRepository repository;
    private final Clock clock;

    AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID actorUserId, String actorUsername, String target, String outcome,
            String detail, String ipAddress) {
        repository.save(new AuditLog(clock.instant(), actorUserId, actorUsername, action, target, outcome,
                truncate(detail, 500), ipAddress));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
