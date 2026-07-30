package com.agentmusic.agentmusic_backend.web.dto;

import java.util.List;

public record CreatePlaylistRequest(
        String name,
        List<TrackDto> tracks
) {
}

