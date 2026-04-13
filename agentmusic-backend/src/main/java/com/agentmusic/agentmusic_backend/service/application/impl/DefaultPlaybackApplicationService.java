package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.dto.SpotifyPlaybackDeviceDto;
import com.agentmusic.agentmusic_backend.dto.TransferPlaybackRequest;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaybackApplicationService implements PlaybackApplicationService {

    private final PlaybackSessionService playbackSessionService;
    private final BridgePlaybackControlService bridgePlaybackControlService;
    private final PlaylistService playlistService;

    public DefaultPlaybackApplicationService(
            PlaybackSessionService playbackSessionService,
            BridgePlaybackControlService bridgePlaybackControlService,
            PlaylistService playlistService
    ) {
        this.playbackSessionService = playbackSessionService;
        this.bridgePlaybackControlService = bridgePlaybackControlService;
        this.playlistService = playlistService;
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
                request.currentPlaylistId(),
                request.currentTrackIndex(),
                request.currentPositionMs(),
                request.isPlaying(),
                request.playbackMode(),
                request.deviceId()
        );
    }

    @Override
    public PlaybackSessionDto playTrack(
            String userId,
            String trackId,
            String playlistId,
            Integer trackIndex,
            String deviceId,
            PlaybackMode playbackMode
    ) {
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        PlaybackSessionDto session = bridgePlaybackControlService.playTrack(userId, trackId, resolvedPlaybackMode, deviceId);
        return playbackSessionService.saveSession(
                userId,
                session.sessionId(),
                trackId,
                playlistId,
                trackIndex,
                session.currentPositionMs(),
                true,
                session.playbackMode(),
                session.deviceId()
        );
    }

    @Override
    public PlaybackSessionDto pause(String userId, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        PlaybackSessionDto session = bridgePlaybackControlService.pause(userId, deviceId);
        return playbackSessionService.saveSession(
                userId,
                session.sessionId(),
                current == null ? session.currentTrackId() : current.currentTrackId(),
                current == null ? session.currentPlaylistId() : current.currentPlaylistId(),
                current == null ? session.currentTrackIndex() : current.currentTrackIndex(),
                current == null ? session.currentPositionMs() : current.currentPositionMs(),
                false,
                current == null ? session.playbackMode() : current.playbackMode(),
                session.deviceId()
        );
    }

    @Override
    public PlaybackSessionDto nextTrack(String userId, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        if (current != null && current.currentPlaylistId() != null) {
            return moveWithinPlaylist(userId, 1, deviceId);
        }
        try {
            PlaybackSessionDto session = bridgePlaybackControlService.nextTrack(userId, deviceId);
            if (session != null) {
                return session;
            }
        } catch (RuntimeException ignored) {
        }
        return moveWithinPlaylist(userId, 1, deviceId);
    }

    @Override
    public PlaybackSessionDto previousTrack(String userId, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        if (current != null && current.currentPlaylistId() != null) {
            return moveWithinPlaylist(userId, -1, deviceId);
        }
        try {
            PlaybackSessionDto session = bridgePlaybackControlService.previousTrack(userId, deviceId);
            if (session != null) {
                return session;
            }
        } catch (RuntimeException ignored) {
        }
        return moveWithinPlaylist(userId, -1, deviceId);
    }

    @Override
    public PlaybackSessionDto seek(String userId, Integer positionMs, String deviceId) {
        int resolvedPositionMs = positionMs == null ? 0 : positionMs;
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        PlaybackSessionDto session = bridgePlaybackControlService.seek(userId, resolvedPositionMs, deviceId);
        return playbackSessionService.saveSession(
                userId,
                session.sessionId(),
                current == null ? session.currentTrackId() : current.currentTrackId(),
                current == null ? session.currentPlaylistId() : current.currentPlaylistId(),
                current == null ? session.currentTrackIndex() : current.currentTrackIndex(),
                resolvedPositionMs,
                current != null && current.isPlaying(),
                current == null ? session.playbackMode() : current.playbackMode(),
                session.deviceId()
        );
    }

    @Override
    public PlaybackSessionDto changePlaybackMode(String userId, PlaybackMode playbackMode, String deviceId) {
        PlaybackMode resolvedPlaybackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        PlaybackSessionDto session = bridgePlaybackControlService.changePlaybackMode(userId, resolvedPlaybackMode, deviceId);
        return playbackSessionService.saveSession(
                userId,
                session.sessionId(),
                current == null ? session.currentTrackId() : current.currentTrackId(),
                current == null ? session.currentPlaylistId() : current.currentPlaylistId(),
                current == null ? session.currentTrackIndex() : current.currentTrackIndex(),
                current == null ? session.currentPositionMs() : current.currentPositionMs(),
                current != null && current.isPlaying(),
                resolvedPlaybackMode,
                session.deviceId()
        );
    }

    @Override
    public Optional<PlaybackSessionDto> syncBridgeState(String userId) {
        try {
            return Optional.ofNullable(bridgePlaybackControlService.syncPlaybackState(userId));
        } catch (RuntimeException ignored) {
            return playbackSessionService.getActiveSession(userId);
        }
    }

    @Override
    public List<SpotifyPlaybackDeviceDto> getAvailableDevices(String userId) {
        return bridgePlaybackControlService.getAvailableDevices(userId).stream()
                .map(device -> new SpotifyPlaybackDeviceDto(
                        device.id(),
                        device.name(),
                        device.active(),
                        device.restricted(),
                        device.type(),
                        device.volumePercent()
                ))
                .toList();
    }

    @Override
    public PlaybackSessionDto transferPlayback(String userId, TransferPlaybackRequest request) {
        boolean play = request != null && Boolean.TRUE.equals(request.play());
        return bridgePlaybackControlService.transferPlayback(
                userId,
                request == null ? null : request.deviceId(),
                play
        );
    }

    private PlaybackSessionDto moveWithinPlaylist(String userId, int step, String deviceId) {
        PlaybackSessionDto current = playbackSessionService.getActiveSession(userId).orElse(null);
        if (current == null || current.currentPlaylistId() == null) {
            return current;
        }

        PlaylistDto playlist = playlistService.getPlaylistById(current.currentPlaylistId()).orElse(null);
        if (playlist == null || playlist.tracks().isEmpty()) {
            return current;
        }

        int currentIndex = resolveCurrentTrackIndex(current, playlist.tracks());
        int nextIndex = resolveWrappedIndex(currentIndex + step, playlist.tracks().size());
        PlaylistTrackDto nextTrack = playlist.tracks().get(nextIndex);

        return playTrack(
                userId,
                nextTrack.track().trackId(),
                playlist.id(),
                nextIndex,
                deviceId == null ? current.deviceId() : deviceId,
                current.playbackMode()
        );
    }

    private int resolveCurrentTrackIndex(PlaybackSessionDto current, List<PlaylistTrackDto> tracks) {
        if (current.currentTrackIndex() != null
                && current.currentTrackIndex() >= 0
                && current.currentTrackIndex() < tracks.size()) {
            return current.currentTrackIndex();
        }

        return tracks.stream()
                .filter(track -> track.track().trackId().equals(current.currentTrackId()))
                .map(PlaylistTrackDto::position)
                .findFirst()
                .orElse(0);
    }

    private int resolveWrappedIndex(int targetIndex, int size) {
        if (size <= 0) {
            return 0;
        }
        int mod = targetIndex % size;
        return mod < 0 ? mod + size : mod;
    }
}
