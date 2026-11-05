package it.adprogetti.zio.hub.channel;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** The single live Edge connection. A new connection replaces (and closes) the previous one. */
@Component
class EdgeSessions {

    private final AtomicReference<WebSocketSession> current = new AtomicReference<>();

    void register(WebSocketSession session, int maxMessageBytes) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 10_000, maxMessageBytes);
        WebSocketSession old = current.getAndSet(safe);
        if (old != null) {
            try {
                old.close(CloseStatus.POLICY_VIOLATION.withReason("Replaced by a new connection"));
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    void unregister(WebSocketSession session) {
        WebSocketSession now = current.get();
        if (now != null && now.getId().equals(session.getId())) {
            current.compareAndSet(now, null);
        }
    }

    boolean isConnected() {
        WebSocketSession s = current.get();
        return s != null && s.isOpen();
    }

    /** Sends a frame; false if no Edge is connected or the send failed (the command stays in the outbox). */
    boolean send(String frame) {
        WebSocketSession s = current.get();
        if (s == null || !s.isOpen()) {
            return false;
        }
        try {
            s.sendMessage(new TextMessage(frame));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
