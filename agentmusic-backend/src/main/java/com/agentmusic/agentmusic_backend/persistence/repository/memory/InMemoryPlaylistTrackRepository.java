package com.agentmusic.agentmusic_backend.persistence.repository.memory;

import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistTrackRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryPlaylistTrackRepository implements PlaylistTrackRepository {

    private final Map<String, List<PlaylistTrack>> playlistTracks = new ConcurrentHashMap<>();

    @Override
    public void replaceTracks(String playlistId, List<PlaylistTrack> playlistTracks) {
        this.playlistTracks.put(playlistId, List.copyOf(playlistTracks));
    }

    @Override
    public List<PlaylistTrack> findByPlaylistId(String playlistId) {
        return playlistTracks.getOrDefault(playlistId, List.of()).stream()
                .sorted(Comparator.comparingInt(PlaylistTrack::position))
                .toList();
    }

    @Override
    public void deleteByPlaylistId(String playlistId) {
        playlistTracks.remove(playlistId);
    }
}
