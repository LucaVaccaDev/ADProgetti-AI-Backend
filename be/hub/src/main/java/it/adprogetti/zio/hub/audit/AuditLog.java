package it.adprogetti.zio.hub.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(nullable = false)
    private String action;

    private String target;

    @Column(nullable = false)
    private String outcome;

    private String detail;

    @Column(name = "ip_address")
    private String ipAddress;

    protected AuditLog() {
    }

    AuditLog(Instant occurredAt, UUID actorUserId, String actorUsername, String action, String target,
            String outcome, String detail, String ipAddress) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.target = target;
        this.outcome = outcome;
        this.detail = detail;
        this.ipAddress = ipAddress;
    }

    public String getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getTarget() {
        return target;
    }

    public String getActorUsername() {
        return actorUsername;
    }
}
