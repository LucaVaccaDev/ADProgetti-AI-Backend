package it.adprogetti.zio.edge.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FreeLLMAPI, the free external provider (Allegato A). It listens on loopback only. {@code textProfile} is the
 * profile/group for TESTO ("auto" lets FreeLLMAPI pick); {@code imageProfile} is the explicit IMMAGINI group and
 * is empty until the administrator has created it (the photo functions then stay BLOCCATA_DA_DIPENDENZA).
 */
@ConfigurationProperties("edge.ai.freellmapi")
public record FreeLlmApiProperties(String url, String textProfile, String imageProfile, int dailyLimitPerUser,
        Duration requestTimeout, int maxQuestionLength) {

    public FreeLlmApiProperties {
        url = url == null ? "" : url.trim();
        textProfile = textProfile == null || textProfile.isBlank() ? "auto" : textProfile.trim();
        imageProfile = imageProfile == null ? "" : imageProfile.trim();
        dailyLimitPerUser = dailyLimitPerUser <= 0 ? 30 : dailyLimitPerUser;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
        maxQuestionLength = maxQuestionLength <= 0 ? 4000 : maxQuestionLength;
    }
}
