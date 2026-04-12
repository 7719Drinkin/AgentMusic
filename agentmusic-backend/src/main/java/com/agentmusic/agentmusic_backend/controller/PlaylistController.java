package com.agentmusic.agentmusic_backend.controller;

import com.agentmusic.agentmusic_backend.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistApplicationService playlistApplicationService;

    public PlaylistController(PlaylistApplicationService playlistApplicationService) {
        this.playlistApplicationService = playlistApplicationService;
    }

    @GetMapping("/{userId}")
    public List<PlaylistDto> getRecentPlaylists(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return playlistApplicationService.getRecentPlaylists(userId, limit);
    }

    @GetMapping("/{playlistId}/detail")
    public ResponseEntity<PlaylistDto> getPlaylistDetail(@PathVariable String playlistId) {
        return playlistApplicationService.getPlaylistDetail(playlistId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}")
    public PlaylistDto createPlaylist(
            @PathVariable String userId,
            @RequestBody CreatePlaylistRequest request
    ) {
        return playlistApplicationService.createPlaylist(userId, request);
    }
}
