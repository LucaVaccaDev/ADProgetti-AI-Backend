package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.hub.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates the Edge before the WebSocket opens (header {@code X-Edge-Key}). This path is the only
 * one outside the JWT chain, by design: the Edge has a credential, not a user account.
 * Repeated failures from one address are limited.
 */
@Component
class EdgeHandshakeInterceptor implements HandshakeInterceptor {

    static final String HEADER = "X-Edge-Key";
    private static final int MAX_FAILURES_PER_MINUTE = 10;

    private final EdgeCredentialService credentials;
    private final AuditService audit;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    EdgeHandshakeInterceptor(EdgeCredentialService credentials, AuditService audit, Clock clock) {
        this.credentials = credentials;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String ip = request.getRemoteAddress() == null ? "unknown"
                : request.getRemoteAddress().getAddress().getHostAddress();
        if (tooManyFailures(ip)) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
        if (credentials.matches(request.getHeaders().getFirst(HEADER))) {
            return true;
        }
        recordFailure(ip);
        audit.record("EDGE_AUTH_FAILED", null, "edge", "edge", AuditService.FAILURE, null, ip);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {
        // nothing to do
    }

    private boolean tooManyFailures(String ip) {
        Deque<Instant> recent = failures.get(ip);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            Instant cutoff = clock.instant().minusSeconds(60);
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }
            return recent.size() >= MAX_FAILURES_PER_MINUTE;
        }
    }

    private void recordFailure(String ip) {
        Deque<Instant> recent = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addLast(clock.instant());
        }
    }
}
