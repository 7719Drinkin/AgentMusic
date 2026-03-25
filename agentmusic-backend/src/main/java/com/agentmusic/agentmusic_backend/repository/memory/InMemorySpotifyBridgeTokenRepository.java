package com.agentmusic.agentmusic_backend.repository.memory;

import com.agentmusic.agentmusic_backend.client.SpotifyToken;
import com.agentmusic.agentmusic_backend.repository.SpotifyBridgeTokenRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySpotifyBridgeTokenRepository implements SpotifyBridgeTokenRepository {

    private final AtomicReference<SpotifyToken> currentToken = new AtomicReference<>();

    @Override
    public void save(SpotifyToken spotifyToken) {
        currentToken.set(spotifyToken);
    }

    @Override
    public Optional<SpotifyToken> findCurrent() {
        return Optional.ofNullable(currentToken.get());
    }
}

