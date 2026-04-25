package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.util.List;
import java.util.Optional;

public interface PlaylistService {

    PlaylistDto createRecommendedPlaylist(String userId, String name, List<TrackDto> tracks);

    List<PlaylistDto> getRecentPlaylists(String userId, int limit);

    Optional<PlaylistDto> getPlaylistById(String playlistId);
}
