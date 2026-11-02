package it.adprogetti.zio.hub.identity;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** What the API returns for a user. Never includes the password hash. */
public record UserDto(UUID id, String username, String displayName, String email, boolean enabled,
        Set<String> roles) {

    public static UserDto from(UserAccount user) {
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.isEnabled(), new TreeSet<>(user.getRoles()));
    }
}
