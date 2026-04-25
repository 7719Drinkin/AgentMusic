package com.agentmusic.agentmusic_backend.web.dto;

public record SeekPlaybackRequest(
        Integer positionMs,
        String deviceId
) {
}
