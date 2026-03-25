package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import java.util.Optional;

public interface MusicQueryApplicationService {

    Optional<TrackDto> getTrack(String trackId);

    Optional<ArtistDto> getArtist(String artistId);
}

