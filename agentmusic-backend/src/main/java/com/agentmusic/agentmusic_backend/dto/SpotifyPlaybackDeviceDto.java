package com.agentmusic.agentmusic_backend.dto;

public record SpotifyPlaybackDeviceDto(
        String id,
        String name,
        boolean active,
        boolean restricted,
        String type,
        Integer volumePercent
) {
}
