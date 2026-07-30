package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.web.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaylistApplicationService implements PlaylistApplicationService {

    private final PlaylistService playlistService;

    public DefaultPlaylistApplicationService(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public List<PlaylistDto> getRecentPlaylists(String userId, int limit) {
        return playlistService.getRecentPlaylists(userId, limit);
    }

    @Override
    public PlaylistDto createPlaylist(String userId, CreatePlaylistRequest request) {
        return playlistService.createRecommendedPlaylist(userId, request.name(), request.tracks());
    }

    @Override
    public Optional<PlaylistDto> getPlaylistDetail(String playlistId) {
        return playlistService.getPlaylistById(playlistId);
    }
}
