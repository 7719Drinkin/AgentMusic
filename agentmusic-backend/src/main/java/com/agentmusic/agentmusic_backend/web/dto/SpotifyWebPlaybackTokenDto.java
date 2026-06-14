package com.agentmusic.agentmusic_backend.web.dto;

import java.time.Instant;
import java.util.Set;

public record SpotifyWebPlaybackTokenDto(
        String accessToken,
        Instant expiresAt,
        Set<String> scopes,
        Set<String> missingScopes
) {
}
