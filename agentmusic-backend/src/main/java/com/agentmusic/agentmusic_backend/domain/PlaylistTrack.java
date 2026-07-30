package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record PlaylistTrack(
        String id,
        String playlistId,
        String trackId,
        int position,
        LocalDateTime addedAt
) {
}

