package com.agentmusic.agentmusic_backend.repository.memory;

import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import com.agentmusic.agentmusic_backend.repository.PlaylistTrackRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
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

