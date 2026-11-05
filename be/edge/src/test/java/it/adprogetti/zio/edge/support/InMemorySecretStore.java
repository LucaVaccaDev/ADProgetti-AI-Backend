package it.adprogetti.zio.edge.support;

import it.adprogetti.zio.edge.secrets.SecretStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test secrets: never touches the real keychain. */
public final class InMemorySecretStore implements SecretStore {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    public void put(String name, String value) {
        values.put(name, value);
    }

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }
}
