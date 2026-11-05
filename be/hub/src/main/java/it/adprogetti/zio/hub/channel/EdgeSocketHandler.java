package it.adprogetti.zio.hub.channel;

import it.adprogetti.zio.contracts.message.ChannelCodec;
import it.adprogetti.zio.contracts.message.ChannelEnvelope;
import it.adprogetti.zio.contracts.message.CommandAck;
import it.adprogetti.zio.contracts.message.CommandResult;
import it.adprogetti.zio.contracts.message.EdgeHeartbeat;
import it.adprogetti.zio.contracts.message.EdgeHello;
import it.adprogetti.zio.contracts.message.ProjectionUpdate;
import it.adprogetti.zio.hub.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Frames from the Edge. Content is never logged; a malformed frame closes the connection. */
@Component
class EdgeSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EdgeSocketHandler.class);

    private final EdgeSessions sessions;
    private final EdgeStateService state;
    private final CommandService commands;
    private final ProjectionService projections;
    private final AuditService audit;
    private final ChannelProperties props;

    EdgeSocketHandler(EdgeSessions sessions, EdgeStateService state, CommandService commands,
            ProjectionService projections, AuditService audit, ChannelProperties props) {
        this.sessions = sessions;
        this.state = state;
        this.commands = commands;
        this.projections = projections;
        this.audit = audit;
        this.props = props;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(props.maxMessageBytes());
        session.setBinaryMessageSizeLimit(1024);
        sessions.register(session, props.maxMessageBytes());
        audit.record("EDGE_CONNECTED", null, "edge", "edge", AuditService.SUCCESS, null,
                session.getRemoteAddress() == null ? null : session.getRemoteAddress().getAddress().getHostAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            ChannelEnvelope envelope = ChannelCodec.decodeEnvelope(message.getPayload());
            switch (envelope.kind()) {
                case HELLO -> {
                    state.onHello(ChannelCodec.decodeBody(envelope, EdgeHello.class));
                    commands.deliverOpen(true);
                }
                case HEARTBEAT -> state.onHeartbeat(ChannelCodec.decodeBody(envelope, EdgeHeartbeat.class));
                case ACK -> commands.onAck(ChannelCodec.decodeBody(envelope, CommandAck.class));
                case RESULT -> commands.onResult(ChannelCodec.decodeBody(envelope, CommandResult.class));
                case PROJECTION -> {
                    if (!projections.apply(ChannelCodec.decodeBody(envelope, ProjectionUpdate.class))) {
                        log.warn("Projection refused or already applied");
                    }
                }
                case COMMAND -> session.close(CloseStatus.PROTOCOL_ERROR);
            }
        } catch (ChannelCodec.InvalidFrameException e) {
            log.warn("Invalid frame from the Edge, closing");
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.unregister(session);
    }
}
