package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.repository.ArtistRepository;
import com.agentmusic.agentmusic_backend.repository.TrackRepository;
import com.agentmusic.agentmusic_backend.service.MusicMetadataService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultMusicMetadataService implements MusicMetadataService {

    private final TrackRepository trackRepository;
    private final ArtistRepository artistRepository;
    private final Clock clock;

    public DefaultMusicMetadataService(TrackRepository trackRepository, ArtistRepository artistRepository, Clock clock) {
        this.trackRepository = trackRepository;
        this.artistRepository = artistRepository;
        this.clock = clock;
    }

    @Override
    public Track saveTrack(Track track) {
        LocalDateTime now = LocalDateTime.now(clock);
        Track normalized = new Track(
                track.trackId(),
                track.title(),
                track.artistId(),
                track.albumName(),
                track.albumId(),
                track.durationMs(),
                track.previewUrl(),
                track.albumImageUrl(),
                now,
                now
        );
        return trackRepository.save(normalized);
    }

    @Override
    public Optional<Track> findTrack(String trackId) {
        return trackRepository.findById(trackId)
                .map(track -> {
                    Track touched = new Track(
                            track.trackId(),
                            track.title(),
                            track.artistId(),
                            track.albumName(),
                            track.albumId(),
                            track.durationMs(),
                            track.previewUrl(),
                            track.albumImageUrl(),
                            track.updatedAt(),
                            LocalDateTime.now(clock)
                    );
                    trackRepository.save(touched);
                    return touched;
                });
    }

    @Override
    public Artist saveArtist(Artist artist) {
        Artist normalized = new Artist(
                artist.artistId(),
                artist.name(),
                artist.bio(),
                artist.imageUrl(),
                artist.followers(),
                LocalDateTime.now(clock)
        );
        return artistRepository.save(normalized);
    }

    @Override
    public Optional<Artist> findArtist(String artistId) {
        return artistRepository.findById(artistId);
    }
}

