package com.agentmusic.agentmusic_backend.integration.spotify;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.util.List;
import java.util.Optional;

public interface SpotifyCatalogClient {

    Optional<Track> getTrack(String trackId, String accessToken);

    Optional<Artist> getArtist(String artistId, String accessToken);

    List<Track> searchTracks(String query, String accessToken, int limit);
}

