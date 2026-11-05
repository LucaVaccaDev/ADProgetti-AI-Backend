package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.channel.EdgeCredentialService;
import it.adprogetti.zio.hub.channel.SigningKeyStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pairing of the Edge: credential (shown once) and the Hub's public key for command signatures. */
@RestController
@RequestMapping("/api/integrations/edge")
@PreAuthorize("hasAuthority('INTEGRATIONS_MANAGE')")
class EdgeAdminController {

    private final EdgeCredentialService credentials;
    private final SigningKeyStore keys;

    EdgeAdminController(EdgeCredentialService credentials, SigningKeyStore keys) {
        this.credentials = credentials;
        this.keys = keys;
    }

    record CredentialResponse(String credential) {
    }

    record PublicKeyResponse(String algorithm, String publicKey) {
    }

    @PostMapping("/credential")
    ResponseEntity<CredentialResponse> rotate(Authentication auth, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(auth, jwt, request);
        String clear = credentials.rotate(user.id(), user.username(), user.ip());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CredentialResponse(clear));
    }

    @GetMapping("/public-key")
    PublicKeyResponse publicKey() {
        return new PublicKeyResponse("Ed25519", keys.publicKeyBase64());
    }
}
