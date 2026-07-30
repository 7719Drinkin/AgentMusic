package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.web.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import java.util.List;
import java.util.Optional;

public interface PlaylistApplicationService {

    List<PlaylistDto> getRecentPlaylists(String userId, int limit);

    PlaylistDto createPlaylist(String userId, CreatePlaylistRequest request);

    Optional<PlaylistDto> getPlaylistDetail(String playlistId);
}
