package com.agentmusic.agentmusic_backend.web.dto;

import java.time.LocalDateTime;

public record PlaylistTrackDto(
        String id,
        String playlistId,
        int position,
        TrackDto track,
        LocalDateTime addedAt
) {
}
