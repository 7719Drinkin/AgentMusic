package com.agentmusic.agentmusic_backend.web.dto;

public record SpotifyPlaybackDeviceDto(
        String id,
        String name,
        boolean active,
        boolean restricted,
        String type,
        Integer volumePercent
) {
}
