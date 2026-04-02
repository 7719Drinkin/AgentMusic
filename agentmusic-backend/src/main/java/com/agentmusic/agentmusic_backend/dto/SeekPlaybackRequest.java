package com.agentmusic.agentmusic_backend.dto;

public record SeekPlaybackRequest(
        Integer positionMs,
        String deviceId
) {
}
