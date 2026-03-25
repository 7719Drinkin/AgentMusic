package com.agentmusic.agentmusic_backend.controller;

import com.agentmusic.agentmusic_backend.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
public class MusicQueryController {

    private final MusicQueryApplicationService musicQueryApplicationService;

    public MusicQueryController(MusicQueryApplicationService musicQueryApplicationService) {
        this.musicQueryApplicationService = musicQueryApplicationService;
    }

    @GetMapping("/tracks/{trackId}")
    public Optional<TrackDto> getTrack(@PathVariable String trackId) {
        return musicQueryApplicationService.getTrack(trackId);
    }

    @GetMapping("/artists/{artistId}")
    public Optional<ArtistDto> getArtist(@PathVariable String artistId) {
        return musicQueryApplicationService.getArtist(artistId);
    }
}
