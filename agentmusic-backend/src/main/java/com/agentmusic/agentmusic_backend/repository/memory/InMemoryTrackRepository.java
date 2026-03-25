package com.agentmusic.agentmusic_backend.repository.memory;

import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.repository.TrackRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTrackRepository implements TrackRepository {

    private final Map<String, Track> tracks = new ConcurrentHashMap<>();

    @Override
    public Track save(Track track) {
        tracks.put(track.trackId(), track);
        return track;
    }

    @Override
    public Optional<Track> findById(String trackId) {
        return Optional.ofNullable(tracks.get(trackId));
    }
}

