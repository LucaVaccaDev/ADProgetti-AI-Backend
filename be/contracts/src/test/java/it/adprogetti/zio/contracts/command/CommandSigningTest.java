package it.adprogetti.zio.contracts.command;

import static org.assertj.core.api.Assertions.assertThat;

import it.adprogetti.zio.contracts.ContractVersion;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandSigningTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private KeyPair keys;
    private CommandSigner signer;
    private CommandVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signer = new CommandSigner(keys.getPrivate());
        verifier = new CommandVerifier(keys.getPublic(), new InMemoryNonceStore(), clock);
    }

    private HubCommand command(String nonce, long expiresAt, int version) {
        return new HubCommand(version, "cmd-1", "PING", "user-1", List.of("SYSTEM_READ"),
                NOW.toEpochMilli(), expiresAt, nonce, "{\"a\":1}");
    }

    private HubCommand valid(String nonce) {
        return command(nonce, NOW.plusSeconds(60).toEpochMilli(), ContractVersion.CURRENT);
    }

    @Test
    void validCommandIsAccepted() {
        assertThat(verifier.verify(signer.sign(valid("nonce-0000000001")))).isEqualTo(VerificationResult.OK);
    }

    @Test
    void tamperedCommandIsRejected() {
        SignedCommand signed = signer.sign(valid("nonce-0000000002"));
        HubCommand forged = new HubCommand(1, "cmd-1", "PING", "user-1", List.of("USERS_MANAGE"),
                NOW.toEpochMilli(), NOW.plusSeconds(60).toEpochMilli(), "nonce-0000000002", "{\"a\":1}");
        assertThat(verifier.verify(new SignedCommand(forged, signed.signature())))
                .isEqualTo(VerificationResult.BAD_SIGNATURE);
    }

    @Test
    void signatureFromAnotherKeyIsRejected() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        SignedCommand signed = new CommandSigner(other.getPrivate()).sign(valid("nonce-0000000003"));
        assertThat(verifier.verify(signed)).isEqualTo(VerificationResult.BAD_SIGNATURE);
    }

    @Test
    void expiredCommandIsRejected() {
        HubCommand expired = command("nonce-0000000004", NOW.minusSeconds(1).toEpochMilli(), 1);
        assertThat(verifier.verify(signer.sign(expired))).isEqualTo(VerificationResult.EXPIRED);
    }

    @Test
    void repeatedNonceIsRejected() {
        SignedCommand signed = signer.sign(valid("nonce-0000000005"));
        assertThat(verifier.verify(signed)).isEqualTo(VerificationResult.OK);
        assertThat(verifier.verify(signed)).isEqualTo(VerificationResult.REPLAYED);
    }

    @Test
    void unsupportedVersionIsRejected() {
        HubCommand future = command("nonce-0000000006", NOW.plusSeconds(60).toEpochMilli(), 99);
        assertThat(verifier.verify(signer.sign(future))).isEqualTo(VerificationResult.UNSUPPORTED_VERSION);
    }

    @Test
    void garbageSignatureIsRejected() {
        assertThat(verifier.verify(new SignedCommand(valid("nonce-0000000007"), "not base64 !!")))
                .isEqualTo(VerificationResult.BAD_SIGNATURE);
    }

    @Test
    void jsonRoundTripKeepsSignatureValid() {
        SignedCommand signed = signer.sign(valid("nonce-0000000008"));
        SignedCommand parsed = CommandCodec.fromJson(CommandCodec.toJson(signed));
        assertThat(parsed).isEqualTo(signed);
        assertThat(verifier.verify(parsed)).isEqualTo(VerificationResult.OK);
    }
}
