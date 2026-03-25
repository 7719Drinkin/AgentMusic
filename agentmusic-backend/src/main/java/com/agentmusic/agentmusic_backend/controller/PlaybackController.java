package com.agentmusic.agentmusic_backend.controller;

import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}

