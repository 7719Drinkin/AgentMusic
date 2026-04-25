package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.domain.Playlist;
import java.util.List;
import java.util.Optional;

public interface PlaylistRepository {

    Playlist save(Playlist playlist);

    Optional<Playlist> findById(String playlistId);

    List<Playlist> findRecentByUserId(String userId, int limit);

    int nextVersionForUser(String userId);

    void deleteOldestExcess(String userId, int keepLatest);
}

