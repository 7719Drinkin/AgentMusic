package com.agentmusic.agentmusic_backend.controller;

import com.agentmusic.agentmusic_backend.dto.ChangePlaybackModeRequest;
import com.agentmusic.agentmusic_backend.dto.PlayTrackRequest;
import com.agentmusic.agentmusic_backend.dto.PlaybackDeviceRequest;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.SeekPlaybackRequest;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController {

    private final PlaybackApplicationService playbackApplicationService;

    public PlaybackController(PlaybackApplicationService playbackApplicationService) {
        this.playbackApplicationService = playbackApplicationService;
    }

    @GetMapping("/{userId}/session")
    public Optional<PlaybackSessionDto> getActiveSession(@PathVariable String userId) {
        return playbackApplicationService.getActiveSession(userId);
    }

    @PutMapping("/{userId}/session")
    public PlaybackSessionDto updateSession(
            @PathVariable String userId,
            @RequestBody UpdatePlaybackSessionRequest request
    ) {
        return playbackApplicationService.updateSession(userId, request);
    }

    @PostMapping("/{userId}/play")
    public PlaybackSessionDto play(
            @PathVariable String userId,
            @RequestBody PlayTrackRequest request
    ) {
        return playbackApplicationService.playTrack(
                userId,
                request.trackId(),
                request.deviceId(),
                request.playbackMode()
        );
    }

    @PostMapping("/{userId}/pause")
    public PlaybackSessionDto pause(
            @PathVariable String userId,
            @RequestBody(required = false) PlaybackDeviceRequest request
    ) {
        return playbackApplicationService.pause(userId, request == null ? null : request.deviceId());
    }

    @PostMapping("/{userId}/next")
    public PlaybackSessionDto next(
            @PathVariable String userId,
            @RequestBody(required = false) PlaybackDeviceRequest request
    ) {
        return playbackApplicationService.nextTrack(userId, request == null ? null : request.deviceId());
    }

    @PostMapping("/{userId}/previous")
    public PlaybackSessionDto previous(
            @PathVariable String userId,
            @RequestBody(required = false) PlaybackDeviceRequest request
    ) {
        return playbackApplicationService.previousTrack(userId, request == null ? null : request.deviceId());
    }

    @PostMapping("/{userId}/seek")
    public PlaybackSessionDto seek(
            @PathVariable String userId,
            @RequestBody SeekPlaybackRequest request
    ) {
        return playbackApplicationService.seek(userId, request.positionMs(), request.deviceId());
    }

    @PostMapping("/{userId}/mode")
    public PlaybackSessionDto changeMode(
            @PathVariable String userId,
            @RequestBody ChangePlaybackModeRequest request
    ) {
        return playbackApplicationService.changePlaybackMode(
                userId,
                request.playbackMode(),
                request.deviceId()
        );
    }

    @PostMapping("/{userId}/sync")
    public Optional<PlaybackSessionDto> sync(@PathVariable String userId) {
        return playbackApplicationService.syncBridgeState(userId);
    }
}
