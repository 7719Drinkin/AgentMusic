package com.agentmusic.agentmusic_backend.integration.spotify;

public record SpotifyBridgeDevice(
        String id,
        String name,
        boolean active,
        boolean restricted,
        String type,
        Integer volumePercent
) {
}
