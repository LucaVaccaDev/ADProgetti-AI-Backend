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
        "edge.ai.freellmapi.daily-limit-per-user=3",
        "edge.docs.index-roots[0]=documenti:/docs-index",
        "edge.tenders.pause=0s",
        "edge.jobs.workers=3",
        "edge.jobs.poll-interval=50ms",
        "edge.jobs.lease=1s",
        "edge.jobs.schedules.test-run.enabled=true",
        "edge.jobs.schedules.test-run.type=TEST_SCHEDULED",
        "edge.jobs.schedules.test-run.zone=Europe/Rome",
        "edge.jobs.schedules.test-run.times=14:30,18:30" })
@Import({ EdgeIntegrationTestBase.HubConfig.class, TestJobHandlers.class })
public abstract class EdgeIntegrationTestBase {

    public static final String EDGE_KEY = "test-edge-key-0123456789";
    static final int PORT = freePort();
    static final KeyPair HUB_KEYS = generateKeys();
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    protected static final StubOllama OLLAMA;
    protected static final StubFreeLlmApi FREELLM;
    protected static final StubTed TED;

    /** A fake NAS on the local disk: four shares, one share that is not mounted, and a folder outside them. */
    protected static final java.nio.file.Path NAS;

    static {
        try {
            NAS = java.nio.file.Files.createTempDirectory("zio-nas-test").toRealPath();
            for (String dir : new String[] { "archivio", "gare", "documenti", "temporanea", "outside" }) {
                java.nio.file.Files.createDirectories(NAS.resolve(dir));
            }
            java.nio.file.Files.writeString(NAS.resolve("outside/secret.txt"), "top secret");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        POSTGRES.start();
        try {
            OLLAMA = new StubOllama();
            FREELLM = new StubFreeLlmApi();
            TED = new StubTed();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired protected FakeHub hub;

    @DynamicPropertySource
    static void dynamic(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("server.port", () -> PORT);
        registry.add("edge.ai.ollama-url", OLLAMA::url);
        registry.add("edge.ai.freellmapi.url", FREELLM::url);
        registry.add("edge.tenders.api-url", TED::url);
        for (String share : new String[] { "archivio", "gare", "documenti", "temporanea" }) {
            registry.add("edge.nas.shares." + share + ".mount", () -> NAS.resolve(share).toString());
        }
        registry.add("edge.nas.shares.fantasma.mount", () -> NAS.resolve("not-mounted").toString());
        registry.add("edge.nas.modes.index-read[0]", () -> "archivio:/");
        registry.add("edge.nas.modes.index-read[1]", () -> "documenti:/");
        registry.add("edge.nas.modes.index-read[2]", () -> "gare:/");
        registry.add("edge.nas.modes.index-read[3]", () -> "fantasma:/");
        registry.add("edge.nas.modes.protocol-write[0]", () -> "archivio:/Protocolli");
        registry.add("edge.nas.modes.tender-write[0]", () -> "gare:/");
        registry.add("edge.nas.min-free-bytes", () -> "1");
        registry.add("edge.nas.allow-unmounted-directory", () -> "true");
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
            store.put(SecretNames.FREELLMAPI_KEY, "freellmapi-test-key");
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
