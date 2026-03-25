package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;

public interface BridgePlaybackControlService {

    PlaybackSessionDto playTrack(String userId, String trackId, PlaybackMode playbackMode, String deviceId);

    PlaybackSessionDto pause(String userId, String deviceId);

    PlaybackSessionDto syncPlaybackState(String userId);
}

