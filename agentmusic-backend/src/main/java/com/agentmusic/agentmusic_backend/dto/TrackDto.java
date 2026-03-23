package com.agentmusic.agentmusic_backend.dto;

public record TrackDto(
        String trackId,
        String title,
        String artistId,
        String albumName,
        String albumId,
        Integer durationMs,
        String previewUrl,
        String albumImageUrl
) {
}

