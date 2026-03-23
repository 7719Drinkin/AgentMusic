package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record Track(
        String trackId,
        String title,
        String artistId,
        String albumName,
        String albumId,
        Integer durationMs,
        String previewUrl,
        String albumImageUrl,
        LocalDateTime updatedAt,
        LocalDateTime lastAccessedAt
) {
}

