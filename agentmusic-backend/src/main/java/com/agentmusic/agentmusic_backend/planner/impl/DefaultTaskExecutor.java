package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.web.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import com.agentmusic.agentmusic_backend.service.RecommendationSelection;
import com.agentmusic.agentmusic_backend.service.RecommendationSelectionService;
import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultTaskExecutor implements TaskExecutor {

    private static final String[][] CJK_VARIANT_FOLDS = {
            {"發", "发"},
            {"暈", "晕"},
            {"戰", "战"},
            {"爭", "争"},
            {"兩", "两"},
            {"後", "后"},
            {"見", "见"},
            {"帶", "带"},
            {"魚", "鱼"},
            {"來", "来"},
            {"臺", "台"},
            {"樂", "乐"},
            {"專", "专"},
            {"氣", "气"},
            {"裡", "里"},
            {"長", "长"},
            {"創", "创"},
            {"愛", "爱"},
            {"夢", "梦"},
            {"會", "会"},
            {"聲", "声"},
            {"過", "过"}
    };

    private final MusicQueryApplicationService musicQueryApplicationService;
    private final PlaybackApplicationService playbackApplicationService;
    private final PlaylistApplicationService playlistApplicationService;
    private final SearchQueryRefiner searchQueryRefiner;
    private final RecommendationSelectionService recommendationSelectionService;

    public DefaultTaskExecutor(
            MusicQueryApplicationService musicQueryApplicationService,
            PlaybackApplicationService playbackApplicationService,
            PlaylistApplicationService playlistApplicationService,
            SearchQueryRefiner searchQueryRefiner,
            RecommendationSelectionService recommendationSelectionService
    ) {
        this.musicQueryApplicationService = musicQueryApplicationService;
        this.playbackApplicationService = playbackApplicationService;
        this.playlistApplicationService = playlistApplicationService;
        this.searchQueryRefiner = searchQueryRefiner;
        this.recommendationSelectionService = recommendationSelectionService;
    }

    @Override
    public PlannerExecutionResult execute(AgentPlan plan, PlanningContext planningContext) {
        String message = planningContext.request().message() == null ? "" : planningContext.request().message();
        String userId = planningContext.request().userId();

        return switch (plan.intent()) {
            case PLAYBACK_CONTROL -> new PlannerExecutionResult(plan, executePlaybackControl(userId, message));
            case TRACK_LOOKUP -> {
                String query = resolveQuery(plan, message, PlanStepType.LOOKUP_TRACK);
                yield new PlannerExecutionResult(plan, executeTrackLookup(query));
            }
            case RECOMMEND_PLAYLIST -> executeRecommendation(plan, planningContext, false);
            case PLAY_RECOMMENDATION -> executeRecommendation(plan, planningContext, true);
            case PLAYLIST_HISTORY_ACCESS -> new PlannerExecutionResult(plan, executePlaylistHistoryAccess(userId));
            case COMPOSITE_REQUEST -> {
                String query = resolveQuery(plan, message, PlanStepType.SEARCH_TRACKS, PlanStepType.UPDATE_PLAYBACK_STATE);
                yield new PlannerExecutionResult(plan, executeCompositeRequest(userId, query, message));
            }
            default -> new PlannerExecutionResult(
                    plan,
                    "Planner skeleton ready. Intent=" + plan.intent()
                            + ", steps=" + plan.steps().size()
                            + ". Spotify bridge-mode execution wiring is pending for this intent."
            );
        };
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

    private PlannerExecutionResult executeRecommendation(AgentPlan plan, PlanningContext planningContext, boolean autoPlay) {
        String userId = planningContext.request().userId();
        String message = planningContext.request().message() == null ? "" : planningContext.request().message();
        RecommendationSelection selection = recommendationSelectionService.buildSelection(planningContext);
        if (selection.tracks().isEmpty()) {
            return new PlannerExecutionResult(plan, "No suitable tracks were found for the current recommendation request.");
        }

        PlaylistDto playlist = playlistApplicationService.createPlaylist(
                userId,
                new CreatePlaylistRequest(buildPlaylistName(message), selection.tracks())
        );
        if (!autoPlay) {
            return new PlannerExecutionResult(
                    plan,
                    "Created recommendation playlist " + playlist.name()
                            + " with " + playlist.tracks().size() + " tracks."
            );
        }

        TrackDto entryTrack = selection.tracks().getFirst();
        PlaybackSessionDto session = playbackApplicationService.playTrack(
                userId,
                entryTrack.trackId(),
                playlist.id(),
                0,
                null,
                inferPlaybackMode(message)
        );
        return new PlannerExecutionResult(
                plan,
                "Created recommendation playlist " + playlist.name()
                        + " with " + playlist.tracks().size()
                        + " tracks and started playback from " + entryTrack.title()
                        + " in mode " + session.playbackMode() + "."
        );
    }

    private String executeCompositeRequest(String userId, String query, String message) {
        List<TrackDto> tracks = musicQueryApplicationService.searchTracks(query, 5);
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

    private String resolveQuery(AgentPlan plan, String fallback, PlanStepType... preferredStepTypes) {
        String plannerQuery = null;
        if (plan == null || plan.steps() == null || preferredStepTypes == null) {
            return searchQueryRefiner.selectExecutionQuery(fallback, null);
        }

        for (PlanStepType preferredStepType : preferredStepTypes) {
            for (PlanStep step : plan.steps()) {
                if (step == null || step.type() != preferredStepType) {
                    continue;
                }
                Map<String, Object> arguments = step.arguments();
                if (arguments == null) {
                    continue;
                }
                Object query = arguments.get("query");
                if (query instanceof String queryText && StringUtils.hasText(queryText)) {
                    plannerQuery = queryText.trim();
                    break;
                }
            }
            if (plannerQuery != null) {
                break;
            }
        }
        return searchQueryRefiner.selectExecutionQuery(fallback, plannerQuery);
    }

    private String buildPlaylistName(String message) {
        String condensed = message.replaceAll("\\s+", " ").trim();
        if (condensed.isBlank()) {
            return "Agent Recommendation Mix";
        }
        String prefix = condensed.length() > 32 ? condensed.substring(0, 32) : condensed;
        return "Agent Recommendation - " + prefix;
    }
}
