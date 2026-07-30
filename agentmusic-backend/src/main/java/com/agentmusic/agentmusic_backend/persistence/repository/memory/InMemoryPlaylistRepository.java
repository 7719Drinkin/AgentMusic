package com.agentmusic.agentmusic_backend.persistence.repository.memory;

import com.agentmusic.agentmusic_backend.domain.Playlist;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryPlaylistRepository implements PlaylistRepository {

    private final Map<String, Playlist> playlists = new ConcurrentHashMap<>();

    @Override
    public Playlist save(Playlist playlist) {
        playlists.put(playlist.id(), playlist);
        return playlist;
    }

    @Override
    public Optional<Playlist> findById(String playlistId) {
        return Optional.ofNullable(playlists.get(playlistId));
    }

    @Override
    public List<Playlist> findRecentByUserId(String userId, int limit) {
        return playlists.values().stream()
                .filter(playlist -> playlist.userId().equals(userId))
                .sorted(Comparator.comparingInt(Playlist::version).reversed()
                        .thenComparing(Playlist::createdAt, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Override
    public int nextVersionForUser(String userId) {
        return playlists.values().stream()
                .filter(playlist -> playlist.userId().equals(userId))
                .mapToInt(Playlist::version)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public void deleteOldestExcess(String userId, int keepLatest) {
        List<Playlist> toDelete = playlists.values().stream()
                .filter(playlist -> playlist.userId().equals(userId))
                .sorted(Comparator.comparingInt(Playlist::version).reversed()
                        .thenComparing(Playlist::createdAt, Comparator.reverseOrder()))
                .skip(keepLatest)
                .toList();
        toDelete.forEach(playlist -> playlists.remove(playlist.id()));
    }
}
