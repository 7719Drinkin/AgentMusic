package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyToken;
import java.util.Optional;

public interface SpotifyBridgeTokenRepository {

    void save(SpotifyToken spotifyToken);

    Optional<SpotifyToken> findCurrent();
}

