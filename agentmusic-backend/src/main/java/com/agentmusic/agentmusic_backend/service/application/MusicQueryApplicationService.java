package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.web.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.util.List;
import java.util.Optional;

public interface MusicQueryApplicationService {

    Optional<TrackDto> getTrack(String trackId);

    Optional<ArtistDto> getArtist(String artistId);

    List<ArtistDto> searchArtists(String query, int limit);

    List<TrackDto> getArtistTopTracks(String artistId, int limit);

    List<TrackDto> getArtistCatalogTracks(String artistId, int limit);

    List<TrackDto> searchTracks(String query, int limit);
}
