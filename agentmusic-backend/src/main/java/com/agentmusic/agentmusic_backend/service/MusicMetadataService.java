package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.util.Optional;

public interface MusicMetadataService {

    Track saveTrack(Track track);

    Optional<Track> findTrack(String trackId);

    Artist saveArtist(Artist artist);

    Optional<Artist> findArtist(String artistId);
}

