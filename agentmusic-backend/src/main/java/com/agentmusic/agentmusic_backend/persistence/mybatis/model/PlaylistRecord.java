package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record PlaylistRecord(
        String playlistId,
        String userId,
        String name,
        int version,
        LocalDateTime createdAt
) {
}
