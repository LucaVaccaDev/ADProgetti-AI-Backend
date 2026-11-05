package it.adprogetti.zio.edge.channel;

import it.adprogetti.zio.edge.secrets.SecretNames;
import it.adprogetti.zio.edge.secrets.SecretStore;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** What the Edge trusts about the Hub: its credential and the public key that signs commands. */
@Component
public class HubTrust {

    private static final Logger log = LoggerFactory.getLogger(HubTrust.class);

    private final SecretStore secrets;
    private volatile PublicKey commandPublicKey;

    HubTrust(SecretStore secrets) {
        this.secrets = secrets;
    }

    /** Re-reads the secrets (called before each connection attempt, so a rotation needs no restart). */
    public boolean refresh() {
        commandPublicKey = secrets.get(SecretNames.HUB_COMMAND_PUBLIC_KEY).map(HubTrust::parse).orElse(null);
        return edgeKey().isPresent() && commandPublicKey != null;
    }

    public Optional<String> edgeKey() {
        return secrets.get(SecretNames.HUB_EDGE_KEY);
    }

    public Optional<PublicKey> commandPublicKey() {
        return Optional.ofNullable(commandPublicKey);
    }

    private static PublicKey parse(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("The Hub public key in the keychain is not valid");
            return null;
        }
    }
}
