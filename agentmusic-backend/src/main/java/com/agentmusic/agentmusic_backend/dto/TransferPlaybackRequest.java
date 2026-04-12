package com.agentmusic.agentmusic_backend.dto;

public record TransferPlaybackRequest(
        String deviceId,
        Boolean play
) {
}
