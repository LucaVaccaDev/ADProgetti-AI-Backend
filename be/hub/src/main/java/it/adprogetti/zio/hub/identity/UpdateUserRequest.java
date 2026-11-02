package it.adprogetti.zio.hub.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Only these fields can be changed through the API; everything else is ignored by design. */
public record UpdateUserRequest(
        @Size(min = 1, max = 150) String displayName,
        @Email @Size(max = 254) String email,
        Boolean enabled,
        Set<String> roles) {
}
