package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record Artist(
        String artistId,
        String name,
        String bio,
        String imageUrl,
        Integer followers,
        LocalDateTime updatedAt
) {
}

