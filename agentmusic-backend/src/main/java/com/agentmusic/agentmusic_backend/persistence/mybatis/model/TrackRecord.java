package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record TrackRecord(
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
