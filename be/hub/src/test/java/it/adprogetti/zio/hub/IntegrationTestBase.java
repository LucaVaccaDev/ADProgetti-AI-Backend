package it.adprogetti.zio.hub;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL 17 through Testcontainers (never H2, CLAUDE.md section 7), one container shared by all
 * integration test classes of the run.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "hub.security.jwt-secret=test-only-secret-test-only-secret-1234567890",
        "hub.security.cookie-secure=false",
        "hub.channel.signing-key-file=target/test-keys/command-signing.key" })
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
