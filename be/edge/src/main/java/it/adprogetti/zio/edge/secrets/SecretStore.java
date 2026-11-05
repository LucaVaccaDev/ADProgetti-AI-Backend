package it.adprogetti.zio.edge.secrets;

import java.util.Optional;

/** Where the Edge reads its secrets from. The production implementation is the macOS keychain. */
public interface SecretStore {

    Optional<String> get(String name);
}
