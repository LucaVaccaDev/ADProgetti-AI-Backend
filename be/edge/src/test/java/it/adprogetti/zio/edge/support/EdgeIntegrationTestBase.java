package it.adprogetti.zio.edge.support;

import it.adprogetti.zio.edge.secrets.SecretNames;
import it.adprogetti.zio.edge.secrets.SecretStore;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL 17 with pgvector (never H2) and a fake Hub reachable at the Edge's own test port.
 * The Edge under test connects to it exactly as it would to the real Hub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
        "edge.hub.allow-insecure=true",
        "edge.hub.reconnect-initial=100ms",
        "edge.hub.reconnect-max=400ms",
        "edge.heartbeat-interval=500ms",
        "edge.secrets.store=test",
        "edge.ai.ollama-url=" })
@Import(EdgeIntegrationTestBase.HubConfig.class)
public abstract class EdgeIntegrationTestBase {

    public static final String EDGE_KEY = "test-edge-key-0123456789";
    static final int PORT = freePort();
    static final KeyPair HUB_KEYS = generateKeys();
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired protected FakeHub hub;

    @DynamicPropertySource
    static void dynamic(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("server.port", () -> PORT);
        registry.add("edge.hub.url", () -> "ws://localhost:" + PORT + "/edge/ws");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generateKeys() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    @EnableWebSocket
    static class HubConfig implements WebSocketConfigurer {

        private final FakeHub fakeHub = new FakeHub(EDGE_KEY, HUB_KEYS.getPrivate());

        @Bean
        FakeHub fakeHub() {
            return fakeHub;
        }

        @Bean
        SecretStore testSecretStore() {
            InMemorySecretStore store = new InMemorySecretStore();
            store.put(SecretNames.HUB_EDGE_KEY, EDGE_KEY);
            store.put(SecretNames.HUB_COMMAND_PUBLIC_KEY,
                    Base64.getEncoder().encodeToString(HUB_KEYS.getPublic().getEncoded()));
            return store;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(fakeHub, "/edge/ws").addInterceptors(fakeHub);
        }

        /** Only in tests, the fake Hub endpoint is open at HTTP level (the real Edge exposes nothing). */
        @Bean
        @Order(0)
        SecurityFilterChain fakeHubChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/edge/ws").csrf(c -> c.disable())
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }
}
