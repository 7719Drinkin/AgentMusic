package com.agentmusic.agentmusic_backend.mapper;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.ChatMessage;
import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import com.agentmusic.agentmusic_backend.domain.Playlist;
import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.domain.UserPreferences;
import com.agentmusic.agentmusic_backend.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.dto.UserPreferencesDto;
import java.util.List;

public final class DomainDtoMapper {

    private DomainDtoMapper() {
    }

    public static UserPreferencesDto toDto(UserPreferences preferences) {
        return new UserPreferencesDto(
                preferences.favoriteGenres(),
                preferences.favoriteArtists(),
                preferences.excludedGenres(),
                preferences.preferredLanguage(),
                preferences.moodPreference()
        );
    }

    public static TrackDto toDto(Track track) {
        return new TrackDto(
                track.trackId(),
                track.title(),
                track.artistId(),
                track.albumName(),
                track.albumId(),
                track.durationMs(),
                track.previewUrl(),
                track.albumImageUrl()
        );
    }

    public static ArtistDto toDto(Artist artist) {
        return new ArtistDto(
                artist.artistId(),
                artist.name(),
                artist.bio(),
                artist.imageUrl(),
                artist.followers()
        );
    }

    public static ChatMessageDto toDto(ChatMessage chatMessage) {
        return new ChatMessageDto(
                chatMessage.id(),
                chatMessage.role(),
                chatMessage.message(),
                chatMessage.metadata(),
                chatMessage.createdAt()
        );
    }

    public static PlaybackSessionDto toDto(PlaybackSession playbackSession) {
        return new PlaybackSessionDto(
                playbackSession.id(),
                playbackSession.currentTrackId(),
                playbackSession.currentPlaylistId(),
                playbackSession.currentTrackIndex(),
                playbackSession.currentPositionMs(),
                playbackSession.isPlaying(),
                playbackSession.playbackMode(),
                playbackSession.deviceId(),
                playbackSession.lastUpdated()
        );
    }

    public static PlaylistDto toDto(Playlist playlist, List<PlaylistTrack> playlistTracks, List<Track> tracks) {
        List<PlaylistTrackDto> trackDtos = playlistTracks.stream()
                .map(playlistTrack -> new PlaylistTrackDto(
                        playlistTrack.id(),
                        playlistTrack.playlistId(),
                        playlistTrack.position(),
                        toDto(findTrack(tracks, playlistTrack.trackId()))
                ))
                .toList();
        return new PlaylistDto(
                playlist.id(),
                playlist.name(),
                playlist.version(),
                playlist.createdAt(),
                trackDtos
        );
    }

    private static Track findTrack(List<Track> tracks, String trackId) {
        return tracks.stream()
                .filter(track -> track.trackId().equals(trackId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Track not found for playlist mapping: " + trackId));
    }
}
