package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import java.util.Optional;

public interface PlaybackApplicationService {

    Optional<PlaybackSessionDto> getActiveSession(String userId);

    PlaybackSessionDto updateSession(String userId, UpdatePlaybackSessionRequest request);

    PlaybackSessionDto playTrack(String userId, String trackId, String deviceId, com.agentmusic.agentmusic_backend.domain.PlaybackMode playbackMode);

    PlaybackSessionDto pause(String userId, String deviceId);

    Optional<PlaybackSessionDto> syncBridgeState(String userId);
}
