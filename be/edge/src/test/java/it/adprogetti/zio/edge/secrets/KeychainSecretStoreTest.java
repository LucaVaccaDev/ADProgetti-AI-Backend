package it.adprogetti.zio.edge.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against the real macOS keychain, with a throw-away service name so real secrets are never touched. */
class KeychainSecretStoreTest {

    private final String service = "it.adprogetti.zio.edge.test-" + UUID.randomUUID();
    private KeychainSecretStore store;

    @BeforeEach
    void setUp() {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"), "macOS only");
        store = new KeychainSecretStore(service);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            run("delete-generic-password", "-s", service, "-a", "test-secret");
        }
    }

    private static void run(String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("/usr/bin/security"));
        command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        p.waitFor(10, TimeUnit.SECONDS);
    }

    @Test
    void readsAStoredSecret() throws Exception {
        run("add-generic-password", "-s", service, "-a", "test-secret", "-w", "s3cr3t-value", "-U");
        assertThat(store.get("test-secret")).contains("s3cr3t-value");
    }

    @Test
    void aMissingSecretIsEmpty() {
        assertThat(store.get("not-there")).isEmpty();
    }

    @Test
    void namesThatCouldCarryOptionsOrPathsAreRefused() {
        for (String bad : List.of("-w", "a b", "../x", "UPPER", "", "a;b")) {
            assertThatThrownBy(() -> store.get(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
