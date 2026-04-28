package com.agentmusic.agentmusic_backend.persistence.repository.memory;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.persistence.repository.ArtistRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryArtistRepository implements ArtistRepository {

    private final Map<String, Artist> artists = new ConcurrentHashMap<>();

    @Override
    public Artist save(Artist artist) {
        artists.put(artist.artistId(), artist);
        return artist;
    }

    @Override
    public Optional<Artist> findById(String artistId) {
        return Optional.ofNullable(artists.get(artistId));
    }
}
