package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackClient;
import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackState;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultBridgePlaybackControlService implements BridgePlaybackControlService {

    private final SpotifyPlaybackClient spotifyPlaybackClient;
    private final SpotifyBridgeAuthService spotifyBridgeAuthService;
    private final PlaybackSessionService playbackSessionService;
    private final SpotifyBridgeProperties spotifyBridgeProperties;

    public DefaultBridgePlaybackControlService(
            SpotifyPlaybackClient spotifyPlaybackClient,
            SpotifyBridgeAuthService spotifyBridgeAuthService,
            PlaybackSessionService playbackSessionService,
            SpotifyBridgeProperties spotifyBridgeProperties
    ) {
        this.spotifyPlaybackClient = spotifyPlaybackClient;
        this.spotifyBridgeAuthService = spotifyBridgeAuthService;
        this.playbackSessionService = playbackSessionService;
        this.spotifyBridgeProperties = spotifyBridgeProperties;
    }

    @Override
    public PlaybackSessionDto playTrack(String userId, String trackId, PlaybackMode playbackMode, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.changePlaybackMode(accessToken, resolvedPlaybackMode, resolvedDeviceId);
                    spotifyPlaybackClient.playTrack(accessToken, trackId, resolvedDeviceId);
                    return playbackSessionService.saveSession(
                            userId,
                            null,
                            trackId,
                            0,
                            true,
                            resolvedPlaybackMode,
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.saveSession(
                        userId,
                        null,
                        trackId,
                        0,
                        true,
                        resolvedPlaybackMode,
                        resolvedDeviceId
                ));
    }

    @Override
    public PlaybackSessionDto pause(String userId, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.pause(accessToken, resolvedDeviceId);
                    PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? 0 : current.currentPositionMs(),
                            false,
                            current == null ? PlaybackMode.SEQUENTIAL : current.playbackMode(),
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.saveSession(
                        userId,
                        null,
                        null,
                        0,
                        false,
                        PlaybackMode.SEQUENTIAL,
                        resolvedDeviceId
                ));
    }

    @Override
    public PlaybackSessionDto nextTrack(String userId, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.nextTrack(accessToken, resolvedDeviceId);
                    return syncPlaybackState(userId);
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto previousTrack(String userId, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.previousTrack(accessToken, resolvedDeviceId);
                    return syncPlaybackState(userId);
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto seek(String userId, int positionMs, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.seek(accessToken, positionMs, resolvedDeviceId);
                    PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            positionMs,
                            current != null && current.isPlaying(),
                            current == null ? PlaybackMode.SEQUENTIAL : current.playbackMode(),
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto changePlaybackMode(String userId, PlaybackMode playbackMode, String deviceId) {
        String resolvedDeviceId = resolveDeviceId(deviceId);
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    spotifyPlaybackClient.changePlaybackMode(accessToken, resolvedPlaybackMode, resolvedDeviceId);
                    PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? 0 : current.currentPositionMs(),
                            current != null && current.isPlaying(),
                            resolvedPlaybackMode,
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto syncPlaybackState(String userId) {
        Optional<String> accessToken = spotifyBridgeAuthService.getValidAccessToken();
        if (accessToken.isEmpty()) {
            return playbackSessionService.getActiveSession(userId).orElse(null);
        }
        Optional<SpotifyPlaybackState> playbackState = spotifyPlaybackClient.getPlaybackState(accessToken.get());
        if (playbackState.isEmpty()) {
            return playbackSessionService.getActiveSession(userId).orElse(null);
        }
        SpotifyPlaybackState state = playbackState.get();
        return playbackSessionService.saveSession(
                userId,
                null,
                state.trackId(),
                state.progressMs(),
                state.isPlaying(),
                state.playbackMode(),
                resolveDeviceId(state.deviceId())
        );
    }

    private String resolveDeviceId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceId;
        }
        if (spotifyBridgeProperties.defaultDeviceId() != null && !spotifyBridgeProperties.defaultDeviceId().isBlank()) {
            return spotifyBridgeProperties.defaultDeviceId();
        }
        return null;
    }
}
