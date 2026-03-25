package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.mapper.DomainDtoMapper;
import com.agentmusic.agentmusic_backend.service.MusicMetadataService;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
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
        return musicMetadataService.findTrack(trackId).map(DomainDtoMapper::toDto);
    }

    @Override
    public Optional<ArtistDto> getArtist(String artistId) {
        return musicMetadataService.findArtist(artistId).map(DomainDtoMapper::toDto);
    }
}

