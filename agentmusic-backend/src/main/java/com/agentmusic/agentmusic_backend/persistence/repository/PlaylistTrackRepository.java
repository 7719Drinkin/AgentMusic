package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import java.util.List;

public interface PlaylistTrackRepository {

    void replaceTracks(String playlistId, List<PlaylistTrack> playlistTracks);

    List<PlaylistTrack> findByPlaylistId(String playlistId);

    void deleteByPlaylistId(String playlistId);
}

