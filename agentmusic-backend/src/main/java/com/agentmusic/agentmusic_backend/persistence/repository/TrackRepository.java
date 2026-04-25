package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.domain.Track;
import java.util.Optional;

public interface TrackRepository {

    Track save(Track track);

    Optional<Track> findById(String trackId);
}

