package it.adprogetti.zio.hub.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 12, max = 128, message = "La password deve avere almeno 12 caratteri.") String newPassword) {
}
