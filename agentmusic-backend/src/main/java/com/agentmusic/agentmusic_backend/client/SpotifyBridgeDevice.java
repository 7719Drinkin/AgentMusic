package com.agentmusic.agentmusic_backend.client;

public record SpotifyBridgeDevice(
        String id,
        String name,
        boolean active,
        String type,
        Integer volumePercent
) {
}

