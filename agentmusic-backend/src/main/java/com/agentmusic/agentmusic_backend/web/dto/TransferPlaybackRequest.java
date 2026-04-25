package com.agentmusic.agentmusic_backend.web.dto;

public record TransferPlaybackRequest(
        String deviceId,
        Boolean play
) {
}
