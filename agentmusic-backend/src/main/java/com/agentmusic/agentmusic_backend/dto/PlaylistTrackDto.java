package com.agentmusic.agentmusic_backend.dto;

public record PlaylistTrackDto(
        String id,
        String playlistId,
        int position,
        TrackDto track
) {
}

