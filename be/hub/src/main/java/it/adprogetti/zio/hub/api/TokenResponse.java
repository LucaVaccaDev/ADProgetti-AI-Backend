package it.adprogetti.zio.hub.api;

import it.adprogetti.zio.hub.auth.AuthResult;
import it.adprogetti.zio.hub.identity.UserDto;

record TokenResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {

    static TokenResponse from(AuthResult result) {
        return new TokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds(), result.user());
    }
}
