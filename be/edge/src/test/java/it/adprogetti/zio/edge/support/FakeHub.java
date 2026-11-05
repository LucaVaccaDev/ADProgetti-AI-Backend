package it.adprogetti.zio.edge.support;

import it.adprogetti.zio.contracts.command.CommandSigner;
import it.adprogetti.zio.contracts.command.HubCommand;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** A Hub stand-in for tests: a real WebSocket endpoint that checks the credential and signs commands. */
public final class FakeHub extends TextWebSocketHandler implements HandshakeInterceptor {

    private final String expectedKey;
    private final CommandSigner signer;
    private final List<ChannelEnvelope> frames = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger connections = new AtomicInteger();
    private volatile boolean rejectHandshakes;
    private volatile WebSocketSession session;

    public FakeHub(String expectedKey, PrivateKey signingKey) {
        this.expectedKey = expectedKey;
        this.signer = new CommandSigner(signingKey);
    }

    public SignedCommand sign(HubCommand command) {
        return signer.sign(command);
    }

    public int attempts() {
        return attempts.get();
    }

    public int connections() {
        return connections.get();
    }

    public void rejectHandshakes(boolean reject) {
        this.rejectHandshakes = reject;
    }

    public boolean isConnected() {
        WebSocketSession s = session;
        return s != null && s.isOpen();
    }

    public void clear() {
        frames.clear();
    }

    public void send(SignedCommand command) throws IOException {
        session.sendMessage(new TextMessage(ChannelCodec.encode(ChannelEnvelope.Kind.COMMAND, command)));
    }

    public void sendRaw(String frame) throws IOException {
        session.sendMessage(new TextMessage(frame));
    }

    public void drop() throws IOException {
        WebSocketSession s = session;
        if (s != null) {
            s.close(CloseStatus.SERVICE_RESTARTED);
        }
    }

    /** Removes and returns the first received frame of this kind that matches, waiting up to the timeout. */
    public ChannelEnvelope await(ChannelEnvelope.Kind kind, Predicate<ChannelEnvelope> filter, long millis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        do {
            for (ChannelEnvelope f : frames) {
                if (f.kind() == kind && filter.test(f)) {
                    frames.remove(f);
                    return f;
                }
            }
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    public ChannelEnvelope await(ChannelEnvelope.Kind kind, long millis) throws InterruptedException {
        return await(kind, f -> true, millis);
    }

    public int count(ChannelEnvelope.Kind kind) {
        return (int) frames.stream().filter(f -> f.kind() == kind).count();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        attempts.incrementAndGet();
        if (rejectHandshakes || !expectedKey.equals(request.getHeaders().getFirst("X-Edge-Key"))) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {
        // nothing
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession established) {
        session = established;
        connections.incrementAndGet();
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
        frames.add(ChannelCodec.decodeEnvelope(message.getPayload()));
    }
}
