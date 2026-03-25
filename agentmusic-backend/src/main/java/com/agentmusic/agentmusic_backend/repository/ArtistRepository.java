package com.agentmusic.agentmusic_backend.repository;

import com.agentmusic.agentmusic_backend.domain.Artist;
import java.util.Optional;

public interface ArtistRepository {

    Artist save(Artist artist);

    Optional<Artist> findById(String artistId);
}

