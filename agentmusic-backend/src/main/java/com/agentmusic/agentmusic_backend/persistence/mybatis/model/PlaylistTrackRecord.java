package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record PlaylistTrackRecord(
        String playlistTrackId,
        String playlistId,
        String trackId,
        int position,
        LocalDateTime addedAt
) {
}
