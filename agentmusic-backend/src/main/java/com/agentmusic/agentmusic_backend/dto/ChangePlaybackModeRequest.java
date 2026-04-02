package com.agentmusic.agentmusic_backend.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record ChangePlaybackModeRequest(
        PlaybackMode playbackMode,
        String deviceId
) {
}
