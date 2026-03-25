package com.agentmusic.agentmusic_backend.dto;

import java.util.List;

public record CreatePlaylistRequest(
        String name,
        List<TrackDto> tracks
) {
}

