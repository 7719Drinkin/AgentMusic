package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.web.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import com.agentmusic.agentmusic_backend.web.mapper.DomainDtoMapper;
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
    public List<ArtistDto> searchArtists(String query, int limit) {
        return musicMetadataService.searchArtists(query, limit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }

    @Override
    public List<TrackDto> getArtistTopTracks(String artistId, int limit) {
        return musicMetadataService.getArtistTopTracks(artistId, limit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }

    @Override
    public List<TrackDto> getArtistCatalogTracks(String artistId, int limit) {
        return musicMetadataService.getArtistCatalogTracks(artistId, limit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }

    @Override
    public List<TrackDto> searchTracks(String query, int limit) {
        return musicMetadataService.searchTracks(query, limit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }
}
