package com.agentmusic.agentmusic_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlaylistDto(
        String id,
        String name,
        int version,
        LocalDateTime createdAt,
        List<PlaylistTrackDto> tracks
) {
}

