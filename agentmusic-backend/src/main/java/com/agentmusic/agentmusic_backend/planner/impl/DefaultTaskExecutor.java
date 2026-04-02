package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DefaultTaskExecutor implements TaskExecutor {

    private final MusicQueryApplicationService musicQueryApplicationService;
    private final PlaybackApplicationService playbackApplicationService;
    private final PlaylistApplicationService playlistApplicationService;

    public DefaultTaskExecutor(
            MusicQueryApplicationService musicQueryApplicationService,
            PlaybackApplicationService playbackApplicationService,
            PlaylistApplicationService playlistApplicationService
    ) {
        this.musicQueryApplicationService = musicQueryApplicationService;
        this.playbackApplicationService = playbackApplicationService;
        this.playlistApplicationService = playlistApplicationService;
    }

    @Override
    public PlannerExecutionResult execute(AgentPlan plan, PlanningContext planningContext) {
        String message = planningContext.request().message() == null ? "" : planningContext.request().message();
        String userId = planningContext.request().userId();

        String reply = switch (plan.intent()) {
            case PLAYBACK_CONTROL -> executePlaybackControl(userId, message);
            case TRACK_LOOKUP -> executeTrackLookup(message);
            case RECOMMEND_PLAYLIST -> executeRecommendation(userId, message, false);
            case PLAY_RECOMMENDATION -> executeRecommendation(userId, message, true);
            case PLAYLIST_HISTORY_ACCESS -> executePlaylistHistoryAccess(userId);
            case COMPOSITE_REQUEST -> executeCompositeRequest(userId, message);
            default -> "Planner skeleton ready. Intent=" + plan.intent()
                    + ", steps=" + plan.steps().size()
                    + ". Spotify bridge-mode execution wiring is pending for this intent.";
        };
        return new PlannerExecutionResult(plan, reply);
    }

    private String executeTrackLookup(String message) {
        List<TrackDto> tracks = musicQueryApplicationService.searchTracks(message, 5);
        if (tracks.isEmpty()) {
            return "No matching track was found for the current request.";
        }

        TrackDto selected = tracks.getFirst();
        return "Found matching tracks. Top result is " + selected.title() + ".";
    }

    private String executePlaybackControl(String userId, String message) {
        if (containsPause(message)) {
            PlaybackSessionDto session = playbackApplicationService.pause(userId, null);
            return "Playback paused. Current local session device=" + session.deviceId() + ".";
        }

        PlaybackMode playbackMode = inferPlaybackMode(message);
        PlaybackSessionDto current = playbackApplicationService.syncBridgeState(userId).orElse(null);
        if (current != null && current.currentTrackId() != null) {
            PlaybackSessionDto session = playbackApplicationService.playTrack(
                    userId,
                    current.currentTrackId(),
                    current.currentPlaylistId(),
                    current.currentTrackIndex(),
                    current.deviceId(),
                    playbackMode
            );
            return "Playback updated using current track " + session.currentTrackId()
                    + " with mode " + session.playbackMode() + ".";
        }

        return "Playback control request received, but no track is currently available to resume.";
    }

    private String executeRecommendation(String userId, String message, boolean autoPlay) {
        RecommendationExecution recommendation = createRecommendationPlaylist(userId, message);
        if (recommendation == null) {
            return "No suitable tracks were found for the current recommendation request.";
        }

        if (!autoPlay) {
            return "Created recommendation playlist " + recommendation.playlist().name()
                    + " with " + recommendation.playlist().tracks().size() + " tracks.";
        }

        TrackDto entryTrack = recommendation.entryTrack();
        PlaybackSessionDto session = playbackApplicationService.playTrack(
                userId,
                entryTrack.trackId(),
                recommendation.playlist().id(),
                0,
                null,
                inferPlaybackMode(message)
        );
        return "Created recommendation playlist " + recommendation.playlist().name()
                + " with " + recommendation.playlist().tracks().size()
                + " tracks and started playback from " + entryTrack.title()
                + " in mode " + session.playbackMode() + ".";
    }

    private String executeCompositeRequest(String userId, String message) {
        List<TrackDto> tracks = musicQueryApplicationService.searchTracks(message, 5);
        if (tracks.isEmpty()) {
            return "No matching track was found for the current request.";
        }

        TrackDto selected = tracks.getFirst();
        PlaybackMode playbackMode = inferPlaybackMode(message);
        PlaybackSessionDto session = playbackApplicationService.playTrack(
                userId,
                selected.trackId(),
                null,
                null,
                null,
                playbackMode
        );
        return "Selected track " + selected.title()
                + " and started playback in mode " + session.playbackMode() + ".";
    }

    private String executePlaylistHistoryAccess(String userId) {
        List<PlaylistDto> playlists = playlistApplicationService.getRecentPlaylists(userId, 5);
        if (playlists.isEmpty()) {
            return "No historical recommendation playlist is available yet.";
        }

        PlaylistDto latest = playlists.getFirst();
        return "Latest recommendation playlist is " + latest.name()
                + " with " + latest.tracks().size() + " tracks.";
    }

    private PlaybackMode inferPlaybackMode(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("随机") || normalized.contains("shuffle")) {
            return PlaybackMode.SHUFFLE;
        }
        if (normalized.contains("单曲循环") || normalized.contains("single loop")) {
            return PlaybackMode.SINGLE_LOOP;
        }
        if (normalized.contains("列表循环") || normalized.contains("repeat all") || normalized.contains("repeat")) {
            return PlaybackMode.LIST_LOOP;
        }
        return PlaybackMode.SEQUENTIAL;
    }

    private boolean containsPause(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("暂停") || normalized.contains("pause");
    }

    private RecommendationExecution createRecommendationPlaylist(String userId, String message) {
        List<TrackDto> tracks = musicQueryApplicationService.searchTracks(message, 10);
        if (tracks.isEmpty()) {
            return null;
        }

        List<TrackDto> selectedTracks = tracks.stream()
                .limit(5)
                .toList();
        PlaylistDto playlist = playlistApplicationService.createPlaylist(
                userId,
                new CreatePlaylistRequest(buildPlaylistName(message), selectedTracks)
        );
        return new RecommendationExecution(playlist, selectedTracks.getFirst());
    }

    private String buildPlaylistName(String message) {
        String condensed = message.replaceAll("\\s+", " ").trim();
        if (condensed.isBlank()) {
            return "Agent Recommendation Mix";
        }
        String prefix = condensed.length() > 24 ? condensed.substring(0, 24) : condensed;
        return "Agent Recommendation - " + prefix;
    }

    private record RecommendationExecution(
            PlaylistDto playlist,
            TrackDto entryTrack
    ) {
    }
}
