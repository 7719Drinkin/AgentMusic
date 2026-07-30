package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record ArtistRecord(
        String artistId,
        String name,
        String bio,
        String imageUrl,
        Integer followers,
        LocalDateTime updatedAt
) {
}
