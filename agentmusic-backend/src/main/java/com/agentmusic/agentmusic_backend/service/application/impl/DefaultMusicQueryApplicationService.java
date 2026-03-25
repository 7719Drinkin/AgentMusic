package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.mapper.DomainDtoMapper;
import com.agentmusic.agentmusic_backend.service.MusicMetadataService;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultMusicQueryApplicationService implements MusicQueryApplicationService {

    private final MusicMetadataService musicMetadataService;

    public DefaultMusicQueryApplicationService(MusicMetadataService musicMetadataService) {
        this.musicMetadataService = musicMetadataService;
    }

    @Override
    public Optional<TrackDto> getTrack(String trackId) {
        return musicMetadataService.findTrackOrFetch(trackId).map(DomainDtoMapper::toDto);
    }

    @Override
    public Optional<ArtistDto> getArtist(String artistId) {
        return musicMetadataService.findArtistOrFetch(artistId).map(DomainDtoMapper::toDto);
    }

    @Override
    public List<TrackDto> searchTracks(String query, int limit) {
        return musicMetadataService.searchTracks(query, limit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }
}
