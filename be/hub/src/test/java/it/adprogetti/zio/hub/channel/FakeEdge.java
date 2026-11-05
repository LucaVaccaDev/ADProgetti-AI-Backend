package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.EdgeHello;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;

/** A stand-in for the Edge in tests: a real WebSocket client speaking the channel protocol. */
final class FakeEdge extends TextWebSocketHandler {

    private final BlockingQueue<ChannelEnvelope> received = new LinkedBlockingQueue<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile CloseStatus closeStatus;
    private WebSocketSession session;

    static FakeEdge connect(int port, String key) throws Exception {
        FakeEdge edge = new FakeEdge();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (key != null) {
            headers.add("X-Edge-Key", key);
        }
        edge.session = new StandardWebSocketClient()
                .execute(edge, headers, URI.create("ws://localhost:" + port + "/edge/ws"))
                .get(10, TimeUnit.SECONDS);
        return edge;
    }

    void hello() throws Exception {
        send(ChannelCodec.encode(ChannelEnvelope.Kind.HELLO,
                new EdgeHello(ContractVersion.CURRENT, "test-edge", System.currentTimeMillis())));
    }

    void send(ChannelEnvelope.Kind kind, Object message) throws Exception {
        send(ChannelCodec.encode(kind, message));
    }

    void send(String frame) throws Exception {
        session.sendMessage(new TextMessage(frame));
    }

    /** Next frame from the Hub, or null after the timeout. */
    ChannelEnvelope next(long millis) throws InterruptedException {
        return received.poll(millis, TimeUnit.MILLISECONDS);
    }

    boolean awaitClosed(long millis) throws InterruptedException {
        return closed.await(millis, TimeUnit.MILLISECONDS);
    }

    CloseStatus closeStatus() {
        return closeStatus;
    }

    void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
        received.add(ChannelCodec.decodeEnvelope(message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        closeStatus = status;
        closed.countDown();
    }
}
