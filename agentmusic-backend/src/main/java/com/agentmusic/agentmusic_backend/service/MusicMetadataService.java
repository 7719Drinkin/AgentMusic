package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.util.Optional;

public interface MusicMetadataService {

    Track saveTrack(Track track);

    Optional<Track> findTrack(String trackId);

    Optional<Track> findTrackOrFetch(String trackId);

    Artist saveArtist(Artist artist);

    Optional<Artist> findArtist(String artistId);

    Optional<Artist> findArtistOrFetch(String artistId);

    java.util.List<Track> searchTracks(String query, int limit);
}
