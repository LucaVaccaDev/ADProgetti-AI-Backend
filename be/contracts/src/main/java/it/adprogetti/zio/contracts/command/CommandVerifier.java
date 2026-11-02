package it.adprogetti.zio.contracts.command;

import it.adprogetti.zio.contracts.ContractVersion;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;

/** Verifies version, signature, expiry and uniqueness of a command. Used by the Edge. */
public final class CommandVerifier {

    private final PublicKey publicKey;
    private final NonceStore nonceStore;
    private final Clock clock;

    public CommandVerifier(PublicKey publicKey, NonceStore nonceStore, Clock clock) {
        this.publicKey = publicKey;
        this.nonceStore = nonceStore;
        this.clock = clock;
    }

    public VerificationResult verify(SignedCommand signed) {
        if (signed == null || signed.command() == null || signed.signature() == null) {
            return VerificationResult.BAD_SIGNATURE;
        }
        HubCommand command = signed.command();
        if (command.schemaVersion() != ContractVersion.CURRENT) {
            return VerificationResult.UNSUPPORTED_VERSION;
        }
        if (!signatureValid(signed)) {
            return VerificationResult.BAD_SIGNATURE;
        }
        if (clock.millis() >= command.expiresAtEpochMs()) {
            return VerificationResult.EXPIRED;
        }
        if (command.nonce() == null || !nonceStore.markSeen(command.nonce(), command.expiresAtEpochMs())) {
            return VerificationResult.REPLAYED;
        }
        return VerificationResult.OK;
    }

    private boolean signatureValid(SignedCommand signed) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(CommandCodec.signingBytes(signed.command()));
            return signature.verify(Base64.getDecoder().decode(signed.signature()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
