package it.adprogetti.zio.hub.auth;

import it.adprogetti.zio.hub.identity.UserDto;

/** Result of login or refresh. The raw refresh token goes only into the HttpOnly cookie, never the body. */
public record AuthResult(String accessToken, long expiresInSeconds, UserDto user, String refreshToken) {
}
