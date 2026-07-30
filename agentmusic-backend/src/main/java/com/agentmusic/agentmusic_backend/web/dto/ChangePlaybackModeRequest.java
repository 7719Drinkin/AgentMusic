package com.agentmusic.agentmusic_backend.web.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record ChangePlaybackModeRequest(
        PlaybackMode playbackMode,
        String deviceId
) {
}
