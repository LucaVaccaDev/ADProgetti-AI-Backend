package it.adprogetti.zio.edge.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads secrets from the macOS keychain through {@code /usr/bin/security}, with fixed arguments and no
 * shell. The secret value is never logged and never passed on a command line by this class.
 * Store a secret with {@code scripts/edge-set-secret.sh <name>} (the value is typed at a prompt).
 */
@Component
@ConditionalOnProperty(name = "edge.secrets.store", havingValue = "keychain", matchIfMissing = true)
public class KeychainSecretStore implements SecretStore {

    public static final String SERVICE = "it.adprogetti.zio.edge";
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]{0,63}");
    private static final int NOT_FOUND = 44; // errSecItemNotFound
    private static final Logger log = LoggerFactory.getLogger(KeychainSecretStore.class);

    private final String service;

    public KeychainSecretStore() {
        this(SERVICE);
    }

    /** Alternative service name, used by tests so real secrets are never touched. */
    public KeychainSecretStore(String service) {
        this.service = service;
    }

    @Override
    public Optional<String> get(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid secret name");
        }
        try {
            Process process = new ProcessBuilder(List.of("/usr/bin/security", "find-generic-password",
                    "-s", service, "-a", name, "-w")).redirectErrorStream(false).start();
            process.getOutputStream().close();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Keychain read timed out for secret {}", name);
                return Optional.empty();
            }
            if (process.exitValue() == 0) {
                String value = new String(out, StandardCharsets.UTF_8).strip();
                return value.isEmpty() ? Optional.empty() : Optional.of(value);
            }
            if (process.exitValue() != NOT_FOUND) {
                log.warn("Keychain read failed for secret {} (exit {})", name, process.exitValue());
            }
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Keychain not available for secret {}", name);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
