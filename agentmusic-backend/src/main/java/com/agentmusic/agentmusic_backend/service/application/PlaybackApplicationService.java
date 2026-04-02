package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import java.util.Optional;

public interface PlaybackApplicationService {

    Optional<PlaybackSessionDto> getActiveSession(String userId);

    PlaybackSessionDto updateSession(String userId, UpdatePlaybackSessionRequest request);

    PlaybackSessionDto playTrack(
            String userId,
            String trackId,
            String playlistId,
            Integer trackIndex,
            String deviceId,
            com.agentmusic.agentmusic_backend.domain.PlaybackMode playbackMode
    );

    PlaybackSessionDto pause(String userId, String deviceId);

    PlaybackSessionDto nextTrack(String userId, String deviceId);

    PlaybackSessionDto previousTrack(String userId, String deviceId);

    PlaybackSessionDto seek(String userId, Integer positionMs, String deviceId);

    PlaybackSessionDto changePlaybackMode(String userId, com.agentmusic.agentmusic_backend.domain.PlaybackMode playbackMode, String deviceId);

    Optional<PlaybackSessionDto> syncBridgeState(String userId);
}
