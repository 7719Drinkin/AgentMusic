package com.agentmusic.agentmusic_backend.service;

import org.springframework.util.StringUtils;

public enum RecommendationRequestMode {
    ARTIST_ONLY,
    ENTITY_CONSTRAINED,
    ALBUM_ONLY,
    THEME_AWARE,
    GENERAL;

    public static RecommendationRequestMode fromExternalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return RecommendationRequestMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static RecommendationRequestMode infer(
            String artist,
            String track,
            String album,
            boolean wantAdditionalTracks
    ) {
        if (StringUtils.hasText(album) && !StringUtils.hasText(track) && !wantAdditionalTracks) {
            return ALBUM_ONLY;
        }
        if (StringUtils.hasText(artist) && !StringUtils.hasText(track) && !StringUtils.hasText(album)) {
            return ARTIST_ONLY;
        }
        if (StringUtils.hasText(artist) && (StringUtils.hasText(track) || StringUtils.hasText(album))) {
            return ENTITY_CONSTRAINED;
        }
        if (!StringUtils.hasText(artist) && !StringUtils.hasText(track) && !StringUtils.hasText(album)) {
            return THEME_AWARE;
        }
        return GENERAL;
    }
}
