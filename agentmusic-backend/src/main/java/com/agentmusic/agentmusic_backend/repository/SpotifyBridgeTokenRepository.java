package com.agentmusic.agentmusic_backend.repository;

import com.agentmusic.agentmusic_backend.client.SpotifyToken;
import java.util.Optional;

public interface SpotifyBridgeTokenRepository {

    void save(SpotifyToken spotifyToken);

    Optional<SpotifyToken> findCurrent();
}

