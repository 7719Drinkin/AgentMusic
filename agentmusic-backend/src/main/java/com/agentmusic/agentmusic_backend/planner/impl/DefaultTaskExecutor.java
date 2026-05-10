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

    public DefaultTaskExecutor(
            MusicQueryApplicationService musicQueryApplicationService,
            PlaybackApplicationService playbackApplicationService,
            PlaylistApplicationService playlistApplicationService,
            SearchQueryRefiner searchQueryRefiner
    ) {
        this.musicQueryApplicationService = musicQueryApplicationService;
        this.playbackApplicationService = playbackApplicationService;
        this.playlistApplicationService = playlistApplicationService;
        this.searchQueryRefiner = searchQueryRefiner;
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
            case RECOMMEND_PLAYLIST -> executeRecommendation(plan, userId, message, false);
            case PLAY_RECOMMENDATION -> executeRecommendation(plan, userId, message, true);
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

    private PlannerExecutionResult executeRecommendation(AgentPlan plan, String userId, String message, boolean autoPlay) {
        String recommendationQuery = searchQueryRefiner.selectExecutionQuery(message, null);
        RecommendationExecution recommendation = createRecommendationPlaylist(userId, recommendationQuery, message);
        if (recommendation.candidateTracks().isEmpty()) {
            return new PlannerExecutionResult(plan, "No suitable tracks were found for the current recommendation request.");
        }

        if (!autoPlay) {
            return new PlannerExecutionResult(
                    plan,
                    "Created recommendation playlist " + recommendation.playlist().name()
                            + " with " + recommendation.playlist().tracks().size() + " tracks."
            );
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
        return new PlannerExecutionResult(
                plan,
                "Created recommendation playlist " + recommendation.playlist().name()
                        + " with " + recommendation.playlist().tracks().size()
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

    private RecommendationExecution createRecommendationPlaylist(String userId, String searchQuery, String originalMessage) {
        SearchQueryRefiner.SearchQueryHints hints = searchQueryRefiner.analyze(originalMessage);
        LinkedHashSet<String> candidateQueries = new LinkedHashSet<>();
        if (StringUtils.hasText(searchQuery)) {
            candidateQueries.add(searchQuery.trim());
        }
        candidateQueries.addAll(hints.structuredCandidates());
        candidateQueries.addAll(hints.candidates());

        Map<String, TrackDto> aggregatedTracks = new LinkedHashMap<>();
        for (String candidateQuery : candidateQueries) {
            List<TrackDto> candidateTracks = musicQueryApplicationService.searchTracks(candidateQuery, 20);
            for (TrackDto candidateTrack : candidateTracks) {
                aggregatedTracks.putIfAbsent(candidateTrack.trackId(), candidateTrack);
            }
        }

        if (aggregatedTracks.isEmpty()) {
            return new RecommendationExecution(
                    List.of(),
                    null,
                    null
            );
        }

        List<TrackDto> selectedTracks = selectRecommendationTracks(originalMessage, List.copyOf(aggregatedTracks.values()), 5);
        PlaylistDto playlist = playlistApplicationService.createPlaylist(
                userId,
                new CreatePlaylistRequest(buildPlaylistName(originalMessage), selectedTracks)
        );
        return new RecommendationExecution(
                List.copyOf(aggregatedTracks.values()),
                playlist,
                selectedTracks.getFirst()
        );
    }

    private List<TrackDto> selectRecommendationTracks(String message, List<TrackDto> tracks, int limit) {
        SearchQueryRefiner.SearchQueryHints hints = searchQueryRefiner.analyze(message);
        if (hints.explicitTitles().isEmpty() || !hints.wantsAdditionalTracks()) {
            return tracks.stream().limit(limit).toList();
        }

        String primaryTitle = hints.explicitTitles().getFirst();
        List<TrackDto> exactTitleMatches = tracks.stream()
                .filter(track -> titleMatches(track.title(), primaryTitle))
                .toList();
        if (exactTitleMatches.isEmpty()) {
            List<TrackDto> contextFallbackTracks = selectContextFallbackTracks(hints, limit);
            if (!contextFallbackTracks.isEmpty()) {
                return contextFallbackTracks;
            }
            return tracks.stream().limit(limit).toList();
        }

        String preferredAlbum = hints.albumTerms().isEmpty() ? null : hints.albumTerms().getFirst();
        List<TrackDto> prioritizedExactMatches = prioritizeByAlbum(exactTitleMatches, preferredAlbum);
        String primaryArtistId = prioritizedExactMatches.getFirst().artistId();
        List<TrackDto> selected = new ArrayList<>();
        LinkedHashSet<String> seenTrackIds = new LinkedHashSet<>();

        addIfAbsent(selected, seenTrackIds, prioritizedExactMatches.getFirst());

        if (StringUtils.hasText(preferredAlbum)) {
            tracks.stream()
                    .filter(track -> primaryArtistId != null && primaryArtistId.equals(track.artistId()))
                    .filter(track -> albumMatches(track.albumName(), preferredAlbum))
                    .filter(track -> !titleMatches(track.title(), primaryTitle))
                    .forEach(track -> addIfAbsent(selected, seenTrackIds, track));
        }

        tracks.stream()
                .filter(track -> primaryArtistId != null && primaryArtistId.equals(track.artistId()))
                .filter(track -> !titleMatches(track.title(), primaryTitle))
                .forEach(track -> addIfAbsent(selected, seenTrackIds, track));

        tracks.stream()
                .filter(track -> !titleMatches(track.title(), primaryTitle))
                .forEach(track -> addIfAbsent(selected, seenTrackIds, track));

        prioritizedExactMatches.stream()
                .skip(1)
                .forEach(track -> addIfAbsent(selected, seenTrackIds, track));

        tracks.forEach(track -> addIfAbsent(selected, seenTrackIds, track));

        return selected.stream().limit(limit).toList();
    }

    private List<TrackDto> selectContextFallbackTracks(SearchQueryRefiner.SearchQueryHints hints, int limit) {
        if (hints.artistTerms().isEmpty() && hints.contextKeywords().isEmpty() && hints.albumTerms().isEmpty()) {
            return List.of();
        }

        String contextQuery;
        if (!hints.artistTerms().isEmpty() && !hints.albumTerms().isEmpty()) {
            contextQuery = searchQueryRefiner.buildStructuredQuery(
                    null,
                    hints.artistTerms().getFirst(),
                    hints.albumTerms().getFirst()
            );
        } else if (!hints.artistTerms().isEmpty()) {
            contextQuery = searchQueryRefiner.buildStructuredQuery(null, hints.artistTerms().getFirst(), null);
        } else if (!hints.albumTerms().isEmpty()) {
            contextQuery = searchQueryRefiner.buildStructuredQuery(null, null, hints.albumTerms().getFirst());
        } else {
            contextQuery = joinKeywords(hints.contextKeywords());
        }
        if (!StringUtils.hasText(contextQuery)) {
            return List.of();
        }

        List<TrackDto> contextTracks = musicQueryApplicationService.searchTracks(contextQuery, 10);
        if (contextTracks.isEmpty()) {
            return List.of();
        }

        String dominantArtistId = resolveDominantArtistId(contextTracks);
        if (!StringUtils.hasText(dominantArtistId)) {
            return contextTracks.stream().limit(limit).toList();
        }

        List<TrackDto> selected = new ArrayList<>();
        LinkedHashSet<String> seenTrackIds = new LinkedHashSet<>();
        String preferredAlbum = hints.albumTerms().isEmpty() ? null : hints.albumTerms().getFirst();

        if (StringUtils.hasText(preferredAlbum)) {
            contextTracks.stream()
                    .filter(track -> dominantArtistId.equals(track.artistId()))
                    .filter(track -> albumMatches(track.albumName(), preferredAlbum))
                    .forEach(track -> addIfAbsent(selected, seenTrackIds, track));
        }

        contextTracks.stream()
                .filter(track -> dominantArtistId.equals(track.artistId()))
                .forEach(track -> addIfAbsent(selected, seenTrackIds, track));

        contextTracks.forEach(track -> addIfAbsent(selected, seenTrackIds, track));
        return selected.stream().limit(limit).toList();
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
        String prefix = condensed.length() > 24 ? condensed.substring(0, 24) : condensed;
        return "Agent Recommendation - " + prefix;
    }

    private boolean titleMatches(String actualTitle, String expectedTitle) {
        String normalizedActual = normalizeForMatching(actualTitle);
        String normalizedExpected = normalizeForMatching(expectedTitle);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        return normalizedActual.equals(normalizedExpected) || normalizedActual.contains(normalizedExpected);
    }

    private boolean albumMatches(String actualAlbum, String expectedAlbum) {
        String normalizedActual = normalizeForMatching(actualAlbum);
        String normalizedExpected = normalizeForMatching(expectedAlbum);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        return normalizedActual.equals(normalizedExpected) || normalizedActual.contains(normalizedExpected);
    }

    private List<TrackDto> prioritizeByAlbum(List<TrackDto> tracks, String preferredAlbum) {
        if (!StringUtils.hasText(preferredAlbum)) {
            return tracks;
        }
        List<TrackDto> prioritized = new ArrayList<>(tracks.size());
        tracks.stream()
                .filter(track -> albumMatches(track.albumName(), preferredAlbum))
                .forEach(prioritized::add);
        tracks.stream()
                .filter(track -> !albumMatches(track.albumName(), preferredAlbum))
                .forEach(prioritized::add);
        return prioritized;
    }

    private String normalizeForMatching(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String folded = value;
        for (String[] pair : CJK_VARIANT_FOLDS) {
            folded = folded.replace(pair[0], pair[1]);
        }
        return folded.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
    }

    private void addIfAbsent(List<TrackDto> selected, LinkedHashSet<String> seenTrackIds, TrackDto track) {
        if (track == null || !StringUtils.hasText(track.trackId())) {
            return;
        }
        if (seenTrackIds.add(track.trackId())) {
            selected.add(track);
        }
    }

    private String resolveDominantArtistId(List<TrackDto> tracks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TrackDto track : tracks) {
            if (track == null || !StringUtils.hasText(track.artistId())) {
                continue;
            }
            counts.merge(track.artistId(), 1, Integer::sum);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return String.join(" ", keywords);
    }

    private record RecommendationExecution(
            List<TrackDto> candidateTracks,
            PlaylistDto playlist,
            TrackDto entryTrack
    ) {
    }
}
