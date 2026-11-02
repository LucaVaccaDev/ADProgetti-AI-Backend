package it.adprogetti.zio.contracts.command;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/** Signs commands with Ed25519. Used by the Hub only. */
public final class CommandSigner {

    private final PrivateKey privateKey;

    public CommandSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public SignedCommand sign(HubCommand command) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(CommandCodec.signingBytes(command));
            return new SignedCommand(command, Base64.getEncoder().encodeToString(signature.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign command", e);
        }
    }
}
