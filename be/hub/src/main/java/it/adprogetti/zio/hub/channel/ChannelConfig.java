package it.adprogetti.zio.hub.channel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers {@code /edge/ws} and the periodic redelivery of unconfirmed commands. */
@Configuration
@EnableWebSocket
@EnableScheduling
@EnableConfigurationProperties(ChannelProperties.class)
class ChannelConfig implements WebSocketConfigurer {

    private final EdgeSocketHandler handler;
    private final EdgeHandshakeInterceptor interceptor;
    private final CommandService commands;

    ChannelConfig(EdgeSocketHandler handler, EdgeHandshakeInterceptor interceptor, CommandService commands,
            ChannelProperties props) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.commands = commands;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/edge/ws").addInterceptors(interceptor);
    }

    @Scheduled(fixedDelayString = "${hub.channel.maintenance-interval:15s}")
    void maintenance() {
        commands.expireOld();
        commands.deliverOpen(false);
    }
}
