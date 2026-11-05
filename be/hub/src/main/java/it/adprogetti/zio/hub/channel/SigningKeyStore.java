package it.adprogetti.zio.hub.channel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Ed25519 key pair that signs Hub commands. Stored in a file with 600 permissions: line 1 is the Base64
 * PKCS#8 private key, line 2 the Base64 X.509 public key. Created on first start if missing.
 */
@Component
public class SigningKeyStore {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    SigningKeyStore(ChannelProperties properties) {
        try {
            Path file = properties.signingKeyFile();
            if (file == null) {
                throw new IllegalStateException("hub.channel.signing-key-file is required");
            }
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            if (Files.exists(file)) {
                requireOwnerOnly(file);
                List<String> lines = Files.readAllLines(file);
                if (lines.size() < 2) {
                    throw new IllegalStateException("Invalid command signing key file");
                }
                this.privateKey = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(lines.get(0).trim())));
                this.publicKey = factory.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(lines.get(1).trim())));
            } else {
                KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(file, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n"
                        + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n", StandardCharsets.UTF_8);
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot access the command signing key", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid command signing key", e);
        }
    }

    private static void requireOwnerOnly(Path file) throws IOException {
        var perms = Files.getPosixFilePermissions(file);
        boolean open = perms.stream().anyMatch(p -> p == PosixFilePermission.GROUP_READ
                || p == PosixFilePermission.GROUP_WRITE || p == PosixFilePermission.GROUP_EXECUTE
                || p == PosixFilePermission.OTHERS_READ || p == PosixFilePermission.OTHERS_WRITE
                || p == PosixFilePermission.OTHERS_EXECUTE);
        if (open) {
            throw new IllegalStateException("The signing key file must have 600 permissions");
        }
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    /** Base64 X.509 public key, given to the Edge when pairing. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
