package it.adprogetti.zio.hub.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+",
                message = "Ammessi solo lettere, numeri, punto, trattino e underscore.") String username,
        @NotBlank @Size(max = 150) String displayName,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128, message = "La password deve avere almeno 12 caratteri.") String password,
        @NotEmpty Set<@NotBlank String> roles) {
}
