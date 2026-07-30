package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.persistence.redis.RedisKeys;
import com.agentmusic.agentmusic_backend.domain.Playlist;
import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import com.agentmusic.agentmusic_backend.web.mapper.DomainDtoMapper;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistRepository;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistTrackRepository;
import com.agentmusic.agentmusic_backend.service.MusicMetadataService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaylistService implements PlaylistService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlaylistService.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final MusicMetadataService musicMetadataService;
    private final Clock clock;

    public DefaultPlaylistService(
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            MusicMetadataService musicMetadataService,
            Clock clock
    ) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.musicMetadataService = musicMetadataService;
        this.clock = clock;
    }

    @Override
    public PlaylistDto createRecommendedPlaylist(String userId, String name, List<TrackDto> tracks) {
        int version = playlistRepository.nextVersionForUser(userId);
        Playlist playlist = new Playlist(
                UUID.randomUUID().toString(),
                userId,
                name,
                version,
                LocalDateTime.now(clock)
        );
        playlistRepository.save(playlist);

        List<Track> savedTracks = tracks.stream()
                .map(this::toDomainTrack)
                .map(musicMetadataService::saveTrack)
                .toList();

        List<PlaylistTrack> playlistTracks = IntStream.range(0, savedTracks.size())
                .mapToObj(index -> new PlaylistTrack(
                        UUID.randomUUID().toString(),
                        playlist.id(),
                        savedTracks.get(index).trackId(),
                        index,
                        LocalDateTime.now(clock)
                ))
                .toList();
        playlistTrackRepository.replaceTracks(playlist.id(), playlistTracks);

        playlistRepository.deleteOldestExcess(userId, RedisKeys.RECENT_PLAYLIST_LIMIT);
        return DomainDtoMapper.toDto(playlist, playlistTracks, savedTracks);
    }

    @Override
    public List<PlaylistDto> getRecentPlaylists(String userId, int limit) {
        return playlistRepository.findRecentByUserId(userId, limit).stream()
                .map(this::toPlaylistDtoIfPlayable)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<PlaylistDto> getPlaylistById(String playlistId) {
        return playlistRepository.findById(playlistId)
                .flatMap(this::toPlaylistDtoIfPlayable);
    }

    private PlaylistDto toPlaylistDto(Playlist playlist) {
        List<PlaylistTrack> playlistTracks = playlistTrackRepository.findByPlaylistId(playlist.id());
        List<Track> tracks = playlistTracks.stream()
                .map(playlistTrack -> musicMetadataService.findTrack(playlistTrack.trackId()).orElseThrow())
                .toList();
        return DomainDtoMapper.toDto(playlist, playlistTracks, tracks);
    }

    private Optional<PlaylistDto> toPlaylistDtoIfPlayable(Playlist playlist) {
        List<PlaylistTrack> playlistTracks = playlistTrackRepository.findByPlaylistId(playlist.id());
        if (playlistTracks.isEmpty()) {
            return Optional.of(DomainDtoMapper.toDto(playlist, List.of(), List.of()));
        }

        boolean hasInvalidTrackId = playlistTracks.stream()
                .map(PlaylistTrack::trackId)
                .anyMatch(trackId -> !isLikelySpotifyTrackId(trackId));
        if (hasInvalidTrackId) {
            LOGGER.warn("Skipping non-playable playlist {} because at least one track id is not a valid Spotify id.", playlist.id());
            return Optional.empty();
        }

        List<Track> tracks = playlistTracks.stream()
                .map(playlistTrack -> musicMetadataService.findTrack(playlistTrack.trackId()).orElse(null))
                .toList();
        if (tracks.stream().anyMatch(track -> track == null)) {
            LOGGER.warn("Skipping playlist {} because at least one track metadata record is missing.", playlist.id());
            return Optional.empty();
        }

        return Optional.of(DomainDtoMapper.toDto(playlist, playlistTracks, tracks));
    }

    private boolean isLikelySpotifyTrackId(String trackId) {
        return trackId != null && trackId.matches("^[A-Za-z0-9]{22}$");
    }

    private Track toDomainTrack(TrackDto trackDto) {
        return new Track(
                trackDto.trackId(),
                trackDto.title(),
                trackDto.artistId(),
                trackDto.albumName(),
                trackDto.albumId(),
                trackDto.durationMs(),
                trackDto.previewUrl(),
                trackDto.albumImageUrl(),
                LocalDateTime.now(clock),
                LocalDateTime.now(clock)
        );
    }
}
