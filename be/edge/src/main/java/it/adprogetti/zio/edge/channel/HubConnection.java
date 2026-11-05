package it.adprogetti.zio.edge.channel;

import it.adprogetti.zio.contracts.ContractVersion;
import it.adprogetti.zio.contracts.command.SignedCommand;
import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.EdgeHello;
import it.adprogetti.zio.edge.audit.EdgeAudit;
import it.adprogetti.zio.edge.command.CommandDispatcher;
import it.adprogetti.zio.edge.config.EdgeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The outbound channel to the Hub (D-02): the Edge dials, the Hub never dials the Edge. Reconnects by
 * itself with exponential backoff and jitter, and keeps working locally while disconnected.
 * The Hub certificate is checked; with {@code edge.hub.ca-file} only that CA is trusted (pinning).
 */
@Component
public class HubConnection implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(HubConnection.class);
    private static final Duration NOT_CONFIGURED_RETRY = Duration.ofSeconds(30);
    private static final Duration STABLE_AFTER = Duration.ofSeconds(10);
    private static final int MAX_FRAME_BYTES = 262_144;

    private final EdgeProperties props;
    private final HubTrust trust;
    private final OutboxService outbox;
    private final CommandDispatcher dispatcher;
    private final HeartbeatService heartbeat;
    private final EdgeAudit audit;
    private final Clock clock;

    private volatile boolean running;
    private volatile ConnectionState state = ConnectionState.NOT_CONFIGURED;
    private volatile Thread loop;
    private volatile WebSocketSession current;
    private volatile HubLink currentLink;

    HubConnection(EdgeProperties props, HubTrust trust, OutboxService outbox, CommandDispatcher dispatcher,
            HeartbeatService heartbeat, EdgeAudit audit, Clock clock) {
        this.props = props;
        this.trust = trust;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.heartbeat = heartbeat;
        this.audit = audit;
        this.clock = clock;
    }

    HubLink currentLink() {
        return currentLink;
    }

    public ConnectionState state() {
        return state;
    }

    @Override
    public void start() {
        EdgeProperties.Hub hub = props.hub();
        if (hub.configured()) {
            boolean secure = hub.url().startsWith("wss://");
            if (!secure && !(hub.allowInsecure() && hub.url().startsWith("ws://"))) {
                throw new IllegalStateException("edge.hub.url must be wss:// (ws:// only with edge.hub.allow-insecure)");
            }
        }
        running = true;
        heartbeat.bind(this);
        loop = new Thread(this::connectLoop, "edge-hub-channel");
        loop.setDaemon(true);
        loop.start();
    }

    @Override
    public void stop() {
        running = false;
        Thread t = loop;
        if (t != null) {
            t.interrupt();
        }
        closeQuietly(current);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void connectLoop() {
        Duration delay = props.hub().reconnectInitial();
        while (running) {
            try {
                if (!props.hub().configured() || !trust.refresh()) {
                    state = ConnectionState.NOT_CONFIGURED;
                    sleep(NOT_CONFIGURED_RETRY);
                    continue;
                }
                state = ConnectionState.CONNECTING;
                long started = clock.millis();
                boolean opened = connectOnce();
                boolean stable = opened && clock.millis() - started >= STABLE_AFTER.toMillis();
                delay = stable ? props.hub().reconnectInitial() : next(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Hub connection attempt failed: {}", e.getClass().getSimpleName());
                delay = next(delay);
            }
            state = ConnectionState.BACKOFF;
            try {
                sleep(withJitter(delay));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** @return true if the connection was opened (and has now closed) */
    private boolean connectOnce() throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-Edge-Key", trust.edgeKey().orElseThrow());
        CountDownLatch closed = new CountDownLatch(1);
        Session handler = new Session(closed);
        StandardWebSocketClient client = new StandardWebSocketClient();
        SSLContext ssl = pinnedContext();
        if (ssl != null) {
            client.setUserProperties(Map.of("org.apache.tomcat.websocket.SSL_CONTEXT", ssl));
        }
        WebSocketSession session;
        try {
            session = client.execute(handler, headers, URI.create(props.hub().url()))
                    .get(props.hub().connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            audit.record("HUB_CONNECT_FAILED", null, EdgeAudit.FAILURE, e.getCause() == null
                    ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
            return false;
        }
        current = session;
        closed.await();
        return true;
    }

    private SSLContext pinnedContext() throws Exception {
        if (props.hub().caFile() == null) {
            return null;
        }
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        try (InputStream in = Files.newInputStream(props.hub().caFile())) {
            int i = 0;
            for (Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                store.setCertificateEntry("hub-ca-" + i++, cert);
            }
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

    private final class Session extends TextWebSocketHandler implements HubLink {

        private final CountDownLatch closed;
        private volatile WebSocketSession socket;

        Session(CountDownLatch closed) {
            this.closed = closed;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            session.setTextMessageSizeLimit(MAX_FRAME_BYTES);
            session.setBinaryMessageSizeLimit(1024);
            socket = new ConcurrentWebSocketSessionDecorator(session, 10_000, MAX_FRAME_BYTES);
            state = ConnectionState.CONNECTED;
            currentLink = this;
            outbox.attach(this);
            send(ChannelCodec.encode(ChannelEnvelope.Kind.HELLO,
                    new EdgeHello(ContractVersion.CURRENT, props.version(), clock.millis())));
            heartbeat.sendNow();
            outbox.flush(true);
            audit.record("HUB_CONNECTED", null, EdgeAudit.SUCCESS, null);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            try {
                ChannelEnvelope envelope = ChannelCodec.decodeEnvelope(message.getPayload());
                if (envelope.kind() == ChannelEnvelope.Kind.COMMAND) {
                    dispatcher.onCommand(ChannelCodec.decodeBody(envelope, SignedCommand.class));
                } else {
                    log.warn("Unexpected {} frame from the Hub ignored", envelope.kind());
                }
            } catch (ChannelCodec.InvalidFrameException e) {
                log.warn("Invalid frame from the Hub, closing");
                session.close(CloseStatus.BAD_DATA);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            outbox.detach(this);
            currentLink = null;
            closed.countDown();
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("Hub channel transport error: {}", exception.getClass().getSimpleName());
        }

        @Override
        public boolean send(String frame) {
            WebSocketSession s = socket;
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

    private Duration next(Duration delay) {
        Duration doubled = delay.multipliedBy(2);
        return doubled.compareTo(props.hub().reconnectMax()) > 0 ? props.hub().reconnectMax() : doubled;
    }

    private static Duration withJitter(Duration base) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4; // +/- 20%
        return Duration.ofMillis(Math.max(1, (long) (base.toMillis() * factor)));
    }

    private static void sleep(Duration d) throws InterruptedException {
        Thread.sleep(d.toMillis());
    }

    private static void closeQuietly(WebSocketSession s) {
        if (s != null && s.isOpen()) {
            try {
                s.close(CloseStatus.GOING_AWAY);
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }
}
