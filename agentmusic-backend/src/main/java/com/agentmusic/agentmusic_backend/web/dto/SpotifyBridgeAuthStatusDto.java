package com.agentmusic.agentmusic_backend.web.dto;

import java.time.Instant;
import java.util.Set;

public record SpotifyBridgeAuthStatusDto(
        boolean enabled,
        boolean authorized,
        String systemUserId,
        String redirectUri,
        Set<String> scopes,
        Instant expiresAt
) {
}

