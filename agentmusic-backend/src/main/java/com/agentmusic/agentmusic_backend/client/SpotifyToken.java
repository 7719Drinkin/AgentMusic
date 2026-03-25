package com.agentmusic.agentmusic_backend.client;

import java.time.Instant;
import java.util.Set;

public record SpotifyToken(
        String accessToken,
        String refreshToken,
        String tokenType,
        Set<String> scopes,
        Instant expiresAt
) {
}

