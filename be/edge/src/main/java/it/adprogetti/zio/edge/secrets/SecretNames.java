package it.adprogetti.zio.edge.secrets;

/** Names of the secrets stored in the keychain (service {@code it.adprogetti.zio.edge}). */
public final class SecretNames {

    /** Credential the Edge presents to the Hub (header X-Edge-Key). */
    public static final String HUB_EDGE_KEY = "hub-edge-key";
    /** Ed25519 public key (Base64 X.509) used to verify Hub commands. */
    public static final String HUB_COMMAND_PUBLIC_KEY = "hub-command-public-key";

    private SecretNames() {
    }
}
