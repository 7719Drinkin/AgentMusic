package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record Playlist(
        String id,
        String userId,
        String name,
        int version,
        LocalDateTime createdAt
) {
}

