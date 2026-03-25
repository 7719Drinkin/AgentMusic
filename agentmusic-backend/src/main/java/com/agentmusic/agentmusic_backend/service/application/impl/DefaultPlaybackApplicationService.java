package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaybackApplicationService implements PlaybackApplicationService {

    private final PlaybackSessionService playbackSessionService;
    private final BridgePlaybackControlService bridgePlaybackControlService;

    public DefaultPlaybackApplicationService(
            PlaybackSessionService playbackSessionService,
            BridgePlaybackControlService bridgePlaybackControlService
    ) {
        this.playbackSessionService = playbackSessionService;
        this.bridgePlaybackControlService = bridgePlaybackControlService;
    }

    @Override
    public Optional<PlaybackSessionDto> getActiveSession(String userId) {
        return playbackSessionService.getActiveSession(userId);
    }

    @Override
    public PlaybackSessionDto updateSession(String userId, UpdatePlaybackSessionRequest request) {
        return playbackSessionService.saveSession(
                userId,
                request.sessionId(),
                request.currentTrackId(),
                request.currentPositionMs(),
                request.isPlaying(),
                request.playbackMode(),
                request.deviceId()
        );
    }

    @Override
    public PlaybackSessionDto playTrack(String userId, String trackId, String deviceId, PlaybackMode playbackMode) {
        return bridgePlaybackControlService.playTrack(userId, trackId, playbackMode, deviceId);
    }

    @Override
    public PlaybackSessionDto pause(String userId, String deviceId) {
        return bridgePlaybackControlService.pause(userId, deviceId);
    }

    @Override
    public Optional<PlaybackSessionDto> syncBridgeState(String userId) {
        return Optional.ofNullable(bridgePlaybackControlService.syncPlaybackState(userId));
    }
}
