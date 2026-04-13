package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.client.SpotifyBridgeDevice;
import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackClient;
import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackState;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.exception.SpotifyPlaybackUnavailableException;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultBridgePlaybackControlService implements BridgePlaybackControlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBridgePlaybackControlService.class);

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
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    Optional<SpotifyPlaybackState> playbackState = spotifyPlaybackClient.getPlaybackState(accessToken);
                    boolean shouldResume = playbackState
                            .filter(state -> !state.isPlaying())
                            .filter(state -> trackId.equals(state.trackId()))
                            .filter(state -> resolvedDeviceId == null || resolvedDeviceId.equals(state.deviceId()))
                            .isPresent();

                    ensureTargetDevice(accessToken, resolvedDeviceId);
                    if (shouldResume) {
                        spotifyPlaybackClient.transferPlayback(accessToken, resolvedDeviceId, true);
                    } else {
                        spotifyPlaybackClient.playTrack(accessToken, trackId, resolvedDeviceId);
                        applyPlaybackModeBestEffort(accessToken, resolvedPlaybackMode, resolvedDeviceId);
                    }
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            trackId,
                            current == null ? null : current.currentPlaylistId(),
                            current == null ? null : current.currentTrackIndex(),
                            shouldResume && current != null ? current.currentPositionMs() : 0,
                            true,
                            resolvedPlaybackMode,
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.saveSession(
                        userId,
                        current == null ? null : current.sessionId(),
                        trackId,
                        current == null ? null : current.currentPlaylistId(),
                        current == null ? null : current.currentTrackIndex(),
                        current == null ? 0 : current.currentPositionMs(),
                        true,
                        resolvedPlaybackMode,
                        resolveConfiguredDeviceId(deviceId)
                ));
    }

    @Override
    public PlaybackSessionDto pause(String userId, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    spotifyPlaybackClient.pause(accessToken, resolvedDeviceId);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? null : current.currentPlaylistId(),
                            current == null ? null : current.currentTrackIndex(),
                            current == null ? 0 : current.currentPositionMs(),
                            false,
                            current == null ? PlaybackMode.SEQUENTIAL : current.playbackMode(),
                            resolvedDeviceId
                    );
                })
                .orElseGet(() -> playbackSessionService.saveSession(
                        userId,
                        current == null ? null : current.sessionId(),
                        current == null ? null : current.currentTrackId(),
                        current == null ? null : current.currentPlaylistId(),
                        current == null ? null : current.currentTrackIndex(),
                        current == null ? 0 : current.currentPositionMs(),
                        false,
                        current == null ? PlaybackMode.SEQUENTIAL : current.playbackMode(),
                        current == null ? resolveConfiguredDeviceId(deviceId) : current.deviceId()
                ));
    }

    @Override
    public PlaybackSessionDto nextTrack(String userId, String deviceId) {
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    spotifyPlaybackClient.nextTrack(accessToken, resolvedDeviceId);
                    return syncPlaybackState(userId);
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto previousTrack(String userId, String deviceId) {
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    spotifyPlaybackClient.previousTrack(accessToken, resolvedDeviceId);
                    return syncPlaybackState(userId);
                })
                .orElseGet(() -> playbackSessionService.getActiveSession(userId).orElse(null));
    }

    @Override
    public PlaybackSessionDto seek(String userId, int positionMs, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    spotifyPlaybackClient.seek(accessToken, positionMs, resolvedDeviceId);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? null : current.currentPlaylistId(),
                            current == null ? null : current.currentTrackIndex(),
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
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    spotifyPlaybackClient.changePlaybackMode(accessToken, resolvedPlaybackMode, resolvedDeviceId);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? null : current.currentPlaylistId(),
                            current == null ? null : current.currentTrackIndex(),
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
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        Optional<String> accessToken = spotifyBridgeAuthService.getValidAccessToken();
        if (accessToken.isEmpty()) {
            return current;
        }
        Optional<SpotifyPlaybackState> playbackState = spotifyPlaybackClient.getPlaybackState(accessToken.get());
        if (playbackState.isEmpty()) {
            return current;
        }
        SpotifyPlaybackState state = playbackState.get();
        return playbackSessionService.saveSession(
                userId,
                current == null ? null : current.sessionId(),
                state.trackId(),
                current == null ? null : current.currentPlaylistId(),
                current == null ? null : current.currentTrackIndex(),
                state.progressMs(),
                state.isPlaying(),
                state.playbackMode(),
                resolveConfiguredDeviceId(state.deviceId())
        );
    }

    @Override
    public List<SpotifyBridgeDevice> getAvailableDevices(String userId) {
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(spotifyPlaybackClient::getAvailableDevices)
                .orElse(List.of());
    }

    @Override
    public PlaybackSessionDto transferPlayback(String userId, String deviceId, boolean play) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        return spotifyBridgeAuthService.getValidAccessToken()
                .map(accessToken -> {
                    String resolvedDeviceId = resolveTargetDeviceId(accessToken, userId, deviceId);
                    if (resolvedDeviceId == null) {
                        return current;
                    }
                    spotifyPlaybackClient.transferPlayback(accessToken, resolvedDeviceId, play);
                    return playbackSessionService.saveSession(
                            userId,
                            current == null ? null : current.sessionId(),
                            current == null ? null : current.currentTrackId(),
                            current == null ? null : current.currentPlaylistId(),
                            current == null ? null : current.currentTrackIndex(),
                            current == null ? 0 : current.currentPositionMs(),
                            play || (current != null && current.isPlaying()),
                            current == null ? PlaybackMode.SEQUENTIAL : current.playbackMode(),
                            resolvedDeviceId
                    );
                })
                .orElse(current);
    }

    private String resolveTargetDeviceId(String accessToken, String userId, String requestedDeviceId) {
        List<SpotifyBridgeDevice> devices = spotifyPlaybackClient.getAvailableDevices(accessToken).stream()
                .filter(device -> device.id() != null && !device.id().isBlank())
                .filter(device -> !device.restricted())
                .toList();
        if (devices.isEmpty()) {
            throw new SpotifyPlaybackUnavailableException(
                    "Spotify 未检测到可用设备。请保持同一 bridge 账号的 Spotify 客户端或 Web Player 在线。"
            );
        }

        String currentSessionDeviceId = playbackSessionService.getActiveSession(userId)
                .map(PlaybackSessionDto::deviceId)
                .filter(id -> !id.isBlank())
                .orElse(null);

        return findMatchingDeviceId(devices, requestedDeviceId)
                .or(() -> findMatchingDeviceId(devices, currentSessionDeviceId))
                .or(() -> findMatchingDeviceId(devices, spotifyBridgeProperties.defaultDeviceId()))
                .or(() -> devices.stream().filter(SpotifyBridgeDevice::active).map(SpotifyBridgeDevice::id).findFirst())
                .or(() -> devices.stream().map(SpotifyBridgeDevice::id).findFirst())
                .orElseThrow(() -> new SpotifyPlaybackUnavailableException(
                        "Spotify 未找到可控制的目标设备。"
                ));
    }

    private Optional<String> findMatchingDeviceId(List<SpotifyBridgeDevice> devices, String candidateDeviceId) {
        if (candidateDeviceId == null || candidateDeviceId.isBlank()) {
            return Optional.empty();
        }
        return devices.stream()
                .filter(device -> candidateDeviceId.equals(device.id()))
                .map(SpotifyBridgeDevice::id)
                .findFirst();
    }

    private void ensureTargetDevice(String accessToken, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        boolean targetDeviceIsActive = spotifyPlaybackClient.getAvailableDevices(accessToken).stream()
                .filter(device -> !device.restricted())
                .anyMatch(device -> deviceId.equals(device.id()) && device.active());
        if (targetDeviceIsActive) {
            return;
        }
        spotifyPlaybackClient.transferPlayback(accessToken, deviceId, false);
    }

    private void applyPlaybackModeBestEffort(String accessToken, PlaybackMode playbackMode, String deviceId) {
        if (playbackMode == null || playbackMode == PlaybackMode.SEQUENTIAL) {
            return;
        }
        try {
            spotifyPlaybackClient.changePlaybackMode(accessToken, playbackMode, deviceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Ignoring playback mode change failure during playTrack deviceId={} playbackMode={}",
                    deviceId,
                    playbackMode,
                    exception);
        }
    }

    private String resolveConfiguredDeviceId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceId;
        }
        if (spotifyBridgeProperties.defaultDeviceId() != null && !spotifyBridgeProperties.defaultDeviceId().isBlank()) {
            return spotifyBridgeProperties.defaultDeviceId();
        }
        return null;
    }
}
