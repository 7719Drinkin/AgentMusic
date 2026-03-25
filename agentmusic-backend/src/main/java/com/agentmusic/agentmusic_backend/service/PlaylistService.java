package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import java.util.List;

public interface PlaylistService {

    PlaylistDto createRecommendedPlaylist(String userId, String name, List<TrackDto> tracks);

    List<PlaylistDto> getRecentPlaylists(String userId, int limit);
}

