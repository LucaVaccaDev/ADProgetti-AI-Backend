package it.adprogetti.zio.hub;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL 17 through Testcontainers (never H2, CLAUDE.md section 7). */
@SpringBootTest(properties = {
        "hub.security.jwt-secret=test-only-secret-test-only-secret-1234567890",
        "hub.security.cookie-secure=false" })
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
}
