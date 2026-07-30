package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.service.RecommendationRequestMode;
import com.agentmusic.agentmusic_backend.service.RecommendationSelection;
import com.agentmusic.agentmusic_backend.service.RecommendationSelectionService;
import com.agentmusic.agentmusic_backend.service.RecommendationSpec;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.web.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class LlmBackedRecommendationSelectionService implements RecommendationSelectionService {

    private static final Logger log = LoggerFactory.getLogger(LlmBackedRecommendationSelectionService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_TRACK_COUNT = 12;
    private static final int DEFAULT_MIN_TRACK_COUNT = 10;
    private static final int DEFAULT_MAX_TRACK_COUNT = 15;
    private static final int MAX_EXPLICIT_TRACK_COUNT = 30;
    private static final int DEFAULT_SEARCH_LIMIT = 12;
    private static final int MAX_CANDIDATES = 40;
    private static final int MAX_THEME_CANDIDATES = 60;
    private static final int MAX_THEME_SEED_ARTISTS = 12;
    private static final Pattern DIGIT_TRACK_COUNT_PATTERN = Pattern.compile("(\\d{1,2})\\s*[\\u9996\\u6536]");
    private static final Pattern CHINESE_TRACK_COUNT_PATTERN = Pattern.compile("([\\u4e00\\u4e8c\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u4e24]{1,3})\\s*[\\u9996\\u6536]");
    private static final Pattern ARTIST_ONLY_COUNT_REQUEST_PATTERN = Pattern.compile(
            "(?:\\u63a8\\u8350|\\u6765\\u70b9|\\u7ed9\\u6211\\u63a8\\u8350)?\\s*(?:\\d{1,2}|[\\u4e00\\u4e8c\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u4e24]{1,3})\\s*[\\u9996\\u6536]\\s*([\\p{IsHan}A-Za-z0-9\\-\\s]{1,40}?)(?:\\u7684)?(?:\\u6b4c\\u66f2|\\u6b4c|\\u4f5c\\u54c1)?(?=\\s|$|[\\uff0c\\u3002,.!\\uff1f\\uff01])",
            Pattern.CASE_INSENSITIVE
    );
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
            {"過", "过"},
            {"張", "张"},
            {"學", "学"},
            {"劉", "刘"},
            {"華", "华"},
            {"葉", "叶"},
            {"麗", "丽"},
            {"蘭", "兰"},
            {"歲", "岁"},
            {"聽", "听"},
            {"為", "为"},
            {"傷", "伤"},
            {"最", "最"},
            {"深", "深"},
            {"紅", "红"},
            {"熱", "热"},
            {"情", "情"}
    };

    private final OpenAiProperties openAiProperties;
    private final AgentChatProperties agentChatProperties;
    private final MusicQueryApplicationService musicQueryApplicationService;
    private final SearchQueryRefiner searchQueryRefiner;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public LlmBackedRecommendationSelectionService(
            OpenAiProperties openAiProperties,
            AgentChatProperties agentChatProperties,
            MusicQueryApplicationService musicQueryApplicationService,
            SearchQueryRefiner searchQueryRefiner
    ) {
        this.openAiProperties = openAiProperties;
        this.agentChatProperties = agentChatProperties;
        this.musicQueryApplicationService = musicQueryApplicationService;
        this.searchQueryRefiner = searchQueryRefiner;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.webClient = WebClient.builder()
                .baseUrl(resolveBaseUrl(openAiProperties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public RecommendationSelection buildSelection(PlanningContext planningContext) {
        String message = planningContext.request().message() == null ? "" : planningContext.request().message();
        SearchQueryRefiner.SearchQueryHints fallbackHints = searchQueryRefiner.analyze(message);
        RecommendationSpec fallbackSpec = buildFallbackSpec(message, fallbackHints);
        RecommendationSpec spec = repairResolvedSpec(
                enforceExplicitTrackCount(resolveRecommendationSpec(planningContext, fallbackSpec), message),
                fallbackHints
        );
        ThemeAwareProfile themeProfile = deriveThemeAwareProfile(spec, message, fallbackHints);
        List<RecommendationCandidate> candidates = retrieveCandidates(spec, message, fallbackHints, themeProfile);
        if (candidates.isEmpty()) {
            return new RecommendationSelection(spec, List.of());
        }

        Map<String, Integer> llmPreferenceOrder = resolveLlmPreferenceOrder(planningContext, spec, candidates);
        List<RecommendationCandidate> orderedCandidates = buildOrderedCandidates(spec, candidates, llmPreferenceOrder, themeProfile);
        List<RecommendationCandidate> hardScopedCandidates = applyHardScopes(spec, orderedCandidates, themeProfile);
        List<TrackDto> selectedTracks = hardScopedCandidates.stream()
                .limit(spec.desiredTrackCount())
                .map(RecommendationCandidate::track)
                .toList();
        return new RecommendationSelection(spec, selectedTracks);
    }

    private RecommendationSpec enforceExplicitTrackCount(RecommendationSpec spec, String message) {
        Integer explicitTrackCount = extractExplicitTrackCount(message);
        if (explicitTrackCount == null || explicitTrackCount == spec.desiredTrackCount()) {
            return spec;
        }
        return new RecommendationSpec(
                spec.requestMode(),
                spec.artist(),
                spec.track(),
                spec.album(),
                spec.language(),
                spec.era(),
                spec.genre(),
                spec.mood(),
                spec.scene(),
                spec.seedArtists(),
                Math.max(1, Math.min(MAX_EXPLICIT_TRACK_COUNT, explicitTrackCount)),
                spec.wantAdditionalTracks(),
                spec.mustIncludeExplicitTrack(),
                spec.preferSameArtist(),
                spec.preferSameAlbum()
        );
    }

    private RecommendationSpec resolveRecommendationSpec(PlanningContext planningContext, RecommendationSpec fallbackSpec) {
        if (!isEnabled()) {
            return fallbackSpec;
        }

        try {
            RecommendationSpecResponse rawResponse = executeRecommendationSpecCall(planningContext);
            return mergeSpecWithFallback(rawResponse, fallbackSpec, planningContext.request().message());
        } catch (RuntimeException exception) {
            log.warn(
                    "Recommendation spec extraction fell back to deterministic parsing. userId={}, message={}",
                    planningContext.request().userId(),
                    abbreviate(planningContext.request().message()),
                    exception
            );
            return fallbackSpec;
        }
    }

    private RecommendationSpecResponse executeRecommendationSpecCall(PlanningContext planningContext) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", buildSpecSystemPrompt()),
                Map.of("role", "user", "content", buildSpecUserPrompt(planningContext))
        );
        Map<?, ?> response = executeChatCompletionWithJson(messages);
        String content = extractMessageContent(response);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Recommendation spec response content is empty.");
        }
        try {
            return objectMapper.readValue(content, RecommendationSpecResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Recommendation spec JSON parse failed.", exception);
        }
    }

    private Map<String, Integer> resolveLlmPreferenceOrder(
            PlanningContext planningContext,
            RecommendationSpec spec,
            List<RecommendationCandidate> candidates
    ) {
        if (!isEnabled() || candidates.isEmpty()) {
            return Map.of();
        }

        try {
            RecommendationRerankResponse rawResponse = executeRecommendationRerankCall(planningContext, spec, candidates);
            if (rawResponse == null || rawResponse.rankedTrackIds() == null || rawResponse.rankedTrackIds().isEmpty()) {
                return Map.of();
            }
            Set<String> candidateIds = candidates.stream()
                    .map(RecommendationCandidate::trackId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<String, Integer> preferenceOrder = new LinkedHashMap<>();
            int index = 0;
            for (String trackId : rawResponse.rankedTrackIds()) {
                if (!StringUtils.hasText(trackId) || !candidateIds.contains(trackId) || preferenceOrder.containsKey(trackId)) {
                    continue;
                }
                preferenceOrder.put(trackId, index++);
            }
            return preferenceOrder;
        } catch (RuntimeException exception) {
            log.warn(
                    "Recommendation rerank fell back to deterministic ranking. userId={}, message={}",
                    planningContext.request().userId(),
                    abbreviate(planningContext.request().message()),
                    exception
            );
            return Map.of();
        }
    }

    private RecommendationRerankResponse executeRecommendationRerankCall(
            PlanningContext planningContext,
            RecommendationSpec spec,
            List<RecommendationCandidate> candidates
    ) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", buildRerankSystemPrompt()),
                Map.of("role", "user", "content", buildRerankUserPrompt(planningContext, spec, candidates))
        );
        Map<?, ?> response = executeChatCompletionWithJson(messages);
        String content = extractMessageContent(response);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Recommendation rerank response content is empty.");
        }
        try {
            return objectMapper.readValue(content, RecommendationRerankResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Recommendation rerank JSON parse failed.", exception);
        }
    }

    private List<RecommendationCandidate> retrieveCandidates(
            RecommendationSpec spec,
            String message,
            SearchQueryRefiner.SearchQueryHints fallbackHints,
            ThemeAwareProfile themeProfile
    ) {
        boolean strictArtistOnlyMode = isStrictArtistOnlyMode(spec);
        if (strictArtistOnlyMode) {
            List<RecommendationCandidate> artistCatalogCandidates = retrieveArtistCatalogCandidates(spec, fallbackHints);
            if (!artistCatalogCandidates.isEmpty()) {
                return artistCatalogCandidates;
            }
        }
        if (isAlbumOnlyMode(spec)) {
            List<RecommendationCandidate> albumCandidates = retrieveAlbumScopedCandidates(spec, fallbackHints);
            if (!albumCandidates.isEmpty()) {
                return albumCandidates;
            }
        }
        if (isEntityConstrainedMode(spec)) {
            List<RecommendationCandidate> entityCandidates = retrieveEntityConstrainedCandidates(spec, fallbackHints);
            if (!entityCandidates.isEmpty()) {
                return entityCandidates;
            }
        }
        if (isThemeAwareMode(spec)) {
            List<RecommendationCandidate> themeCandidates = retrieveThemeAwareCandidates(spec, message, fallbackHints, themeProfile);
            if (!themeCandidates.isEmpty()) {
                return themeCandidates;
            }
        }

        LinkedHashSet<String> queries = new LinkedHashSet<>();

        addStructuredQueries(queries, spec);
        if (strictArtistOnlyMode) {
            addArtistOnlyFallbackQueries(queries, spec, fallbackHints);
        } else {
            queries.addAll(fallbackHints.structuredCandidates());
            queries.addAll(fallbackHints.candidates());
        }

        LinkedHashMap<String, RecommendationCandidate> aggregated = new LinkedHashMap<>();
        Map<String, String> artistNameCache = new LinkedHashMap<>();
        int searchLimit = resolveSearchLimit(spec, strictArtistOnlyMode);
        for (String query : queries) {
            List<TrackDto> results = musicQueryApplicationService.searchTracks(query, searchLimit);
            for (TrackDto track : results) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                resolveArtistName(track.artistId(), artistNameCache),
                                track.albumName(),
                                1,
                                track
                        )
                );
                if (aggregated.size() >= MAX_CANDIDATES) {
                    return List.copyOf(aggregated.values());
                }
            }
        }
        List<RecommendationCandidate> candidates = List.copyOf(aggregated.values());
        if (!strictArtistOnlyMode) {
            return candidates;
        }
        return filterStrictArtistCandidates(candidates, spec.artist());
    }

    private List<RecommendationCandidate> retrieveThemeAwareCandidates(
            RecommendationSpec spec,
            String message,
            SearchQueryRefiner.SearchQueryHints fallbackHints,
            ThemeAwareProfile themeProfile
    ) {
        LinkedHashSet<String> queries = buildThemeAwareQueries(message, fallbackHints, themeProfile);
        if (queries.isEmpty() && themeProfile.seedArtists().isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, RecommendationCandidate> aggregated = new LinkedHashMap<>();
        Map<String, Integer> retrievalHits = new LinkedHashMap<>();
        Map<String, String> artistNameCache = new LinkedHashMap<>();
        int perSeedArtistLimit = Math.max(3, Math.min(6, spec.desiredTrackCount() / Math.max(1, Math.min(6, themeProfile.seedArtists().size())) + 2));
        int perSeedSearchLimit = Math.max(6, Math.min(12, perSeedArtistLimit + 4));
        for (String seedArtist : themeProfile.seedArtists()) {
            Optional<ArtistDto> resolvedArtist = resolvePrimaryArtist(seedArtist, List.of());
            if (resolvedArtist.isEmpty()) {
                continue;
            }
            String candidateArtistName = preferredArtistName(resolvedArtist.get(), seedArtist);
            for (String seedQuery : buildSeedArtistThemeQueries(seedArtist, themeProfile)) {
                for (TrackDto track : musicQueryApplicationService.searchTracks(seedQuery, perSeedSearchLimit)) {
                    if (track == null || !StringUtils.hasText(track.trackId())) {
                        continue;
                    }
                    if (!resolvedArtist.get().artistId().equals(track.artistId())) {
                        continue;
                    }
                    retrievalHits.merge(track.trackId(), 3, Integer::sum);
                    aggregated.putIfAbsent(
                            track.trackId(),
                            new RecommendationCandidate(
                                    track.trackId(),
                                    track.title(),
                                    track.artistId(),
                                    candidateArtistName,
                                    track.albumName(),
                                    0,
                                    track
                            )
                    );
                }
            }
            for (TrackDto track : musicQueryApplicationService.getArtistCatalogTracks(resolvedArtist.get().artistId(), perSeedArtistLimit)) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                if (!resolvedArtist.get().artistId().equals(track.artistId())) {
                    continue;
                }
                retrievalHits.merge(track.trackId(), 1, Integer::sum);
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                candidateArtistName,
                                track.albumName(),
                                0,
                                track
                        )
                );
            }
        }

        int searchLimit = Math.min(MAX_EXPLICIT_TRACK_COUNT, Math.max(spec.desiredTrackCount() + 8, 18));
        for (String query : queries) {
            List<TrackDto> results = musicQueryApplicationService.searchTracks(query, searchLimit);
            for (TrackDto track : results) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                retrievalHits.merge(track.trackId(), 1, Integer::sum);
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                resolveArtistName(track.artistId(), artistNameCache),
                                track.albumName(),
                                0,
                                track
                        )
                );
            }
            if (aggregated.size() >= MAX_THEME_CANDIDATES) {
                break;
            }
        }
        return aggregated.values().stream()
                .map(candidate -> new RecommendationCandidate(
                        candidate.trackId(),
                        candidate.title(),
                        candidate.artistId(),
                        candidate.artistName(),
                        candidate.albumName(),
                        retrievalHits.getOrDefault(candidate.trackId(), 1),
                        candidate.track()
                ))
                .limit(MAX_THEME_CANDIDATES)
                .toList();
    }

    private List<RecommendationCandidate> retrieveArtistCatalogCandidates(
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints fallbackHints
    ) {
        Optional<ArtistDto> resolvedArtist = resolvePrimaryArtist(spec.artist(), fallbackHints.artistTerms());
        if (resolvedArtist.isEmpty()) {
            return List.of();
        }

        int catalogLimit = Math.min(MAX_CANDIDATES, Math.max(spec.desiredTrackCount() * 2, 20));
        List<TrackDto> catalogTracks = musicQueryApplicationService.getArtistCatalogTracks(
                resolvedArtist.get().artistId(),
                catalogLimit
        );
        if (catalogTracks.isEmpty()) {
            return List.of();
        }

        return catalogTracks.stream()
                .filter(track -> resolvedArtist.get().artistId().equals(track.artistId()))
                .map(track -> new RecommendationCandidate(
                        track.trackId(),
                        track.title(),
                        track.artistId(),
                        preferredArtistName(resolvedArtist.get(), spec.artist()),
                        track.albumName(),
                        1,
                        track
                ))
                .toList();
    }

    private List<RecommendationCandidate> retrieveAlbumScopedCandidates(
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints fallbackHints
    ) {
        LinkedHashMap<String, RecommendationCandidate> aggregated = new LinkedHashMap<>();
        Optional<ArtistDto> resolvedArtist = resolvePrimaryArtist(spec.artist(), fallbackHints.artistTerms());
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addStructuredQueries(queries, spec);
        queries.addAll(fallbackHints.structuredCandidates());
        queries.addAll(fallbackHints.candidates());

        int searchLimit = Math.max(DEFAULT_SEARCH_LIMIT, Math.min(MAX_EXPLICIT_TRACK_COUNT, spec.desiredTrackCount() + 6));
        for (String query : queries) {
            List<TrackDto> results = musicQueryApplicationService.searchTracks(query, searchLimit);
            for (TrackDto track : results) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                if (resolvedArtist.isPresent() && !resolvedArtist.get().artistId().equals(track.artistId())) {
                    continue;
                }
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                resolvedArtist.map(artist -> preferredArtistName(artist, spec.artist())).orElse(""),
                                track.albumName(),
                                1,
                                track
                        )
                );
            }
        }
        if (aggregated.isEmpty() && resolvedArtist.isPresent()) {
            int catalogLimit = Math.min(MAX_CANDIDATES, Math.max(spec.desiredTrackCount() * 2, 24));
            for (TrackDto track : musicQueryApplicationService.getArtistCatalogTracks(resolvedArtist.get().artistId(), catalogLimit)) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                if (!resolvedArtist.get().artistId().equals(track.artistId())) {
                    continue;
                }
                if (!albumMatches(track.albumName(), spec.album())) {
                    continue;
                }
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                preferredArtistName(resolvedArtist.get(), spec.artist()),
                                track.albumName(),
                                1,
                                track
                        )
                );
            }
        }
        return List.copyOf(aggregated.values());
    }

    private List<RecommendationCandidate> retrieveEntityConstrainedCandidates(
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints fallbackHints
    ) {
        Optional<ArtistDto> resolvedArtist = resolvePrimaryArtist(spec.artist(), fallbackHints.artistTerms());
        if (resolvedArtist.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, RecommendationCandidate> aggregated = new LinkedHashMap<>();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addStructuredQueries(queries, spec);
        queries.addAll(fallbackHints.structuredCandidates());
        queries.addAll(fallbackHints.candidates());

        int searchLimit = Math.max(DEFAULT_SEARCH_LIMIT, Math.min(MAX_EXPLICIT_TRACK_COUNT, spec.desiredTrackCount() + 6));
        for (String query : queries) {
            List<TrackDto> results = musicQueryApplicationService.searchTracks(query, searchLimit);
            for (TrackDto track : results) {
                if (track == null || !StringUtils.hasText(track.trackId())) {
                    continue;
                }
                if (!resolvedArtist.get().artistId().equals(track.artistId())) {
                    continue;
                }
                aggregated.putIfAbsent(
                        track.trackId(),
                        new RecommendationCandidate(
                                track.trackId(),
                                track.title(),
                                track.artistId(),
                                preferredArtistName(resolvedArtist.get(), spec.artist()),
                                track.albumName(),
                                1,
                                track
                        )
                );
            }
        }

        int catalogLimit = Math.min(MAX_CANDIDATES, Math.max(spec.desiredTrackCount() * 2, 24));
        List<TrackDto> catalogTracks = musicQueryApplicationService.getArtistCatalogTracks(
                resolvedArtist.get().artistId(),
                catalogLimit
        );
        for (TrackDto track : catalogTracks) {
            if (track == null || !StringUtils.hasText(track.trackId())) {
                continue;
            }
            if (!resolvedArtist.get().artistId().equals(track.artistId())) {
                continue;
            }
            aggregated.putIfAbsent(
                    track.trackId(),
                    new RecommendationCandidate(
                            track.trackId(),
                            track.title(),
                            track.artistId(),
                            preferredArtistName(resolvedArtist.get(), spec.artist()),
                            track.albumName(),
                            1,
                            track
                    )
            );
        }
        return List.copyOf(aggregated.values());
    }

    private int resolveSearchLimit(RecommendationSpec spec, boolean strictArtistOnlyMode) {
        int desiredTrackCount = Math.max(1, spec.desiredTrackCount());
        if (strictArtistOnlyMode) {
            return Math.min(MAX_CANDIDATES, desiredTrackCount + 10);
        }
        return Math.max(DEFAULT_SEARCH_LIMIT, Math.min(MAX_EXPLICIT_TRACK_COUNT, desiredTrackCount + 4));
    }

    private boolean isStrictArtistOnlyMode(RecommendationSpec spec) {
        return spec.requestMode() == RecommendationRequestMode.ARTIST_ONLY
                && StringUtils.hasText(spec.artist());
    }

    private boolean isAlbumOnlyMode(RecommendationSpec spec) {
        return spec.requestMode() == RecommendationRequestMode.ALBUM_ONLY
                && StringUtils.hasText(spec.album());
    }

    private boolean isEntityConstrainedMode(RecommendationSpec spec) {
        return spec.requestMode() == RecommendationRequestMode.ENTITY_CONSTRAINED
                && (StringUtils.hasText(spec.artist()) || StringUtils.hasText(spec.album()) || StringUtils.hasText(spec.track()));
    }

    private boolean isThemeAwareMode(RecommendationSpec spec) {
        return spec.requestMode() == RecommendationRequestMode.THEME_AWARE
                && !StringUtils.hasText(spec.artist())
                && !StringUtils.hasText(spec.track())
                && !StringUtils.hasText(spec.album());
    }

    private void addStructuredQueries(Set<String> queries, RecommendationSpec spec) {
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(spec.track(), spec.artist(), spec.album()));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(spec.track(), spec.artist(), null));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(spec.track(), null, spec.album()));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(spec.track(), null, null));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(null, spec.artist(), spec.album()));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(null, spec.artist(), null));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(null, null, spec.album()));
    }

    private void addArtistOnlyFallbackQueries(
            Set<String> queries,
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints fallbackHints
    ) {
        addQuery(queries, spec.artist());
        for (String artistTerm : fallbackHints.artistTerms()) {
            addQuery(queries, artistTerm);
        }
    }

    private ThemeAwareProfile deriveThemeAwareProfile(
            RecommendationSpec spec,
            String message,
            SearchQueryRefiner.SearchQueryHints fallbackHints
    ) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String language = choosePreferredValue(detectThemeLanguage(normalized), spec.language());
        String era = choosePreferredValue(detectThemeEra(normalized), spec.era());
        String genre = choosePreferredValue(detectThemeGenre(normalized, language), spec.genre());
        String mood = choosePreferredValue(detectThemeMood(normalized), spec.mood());
        String scene = choosePreferredValue(detectThemeScene(normalized), spec.scene());
        List<String> contextKeywords = fallbackHints == null ? List.of() : fallbackHints.contextKeywords();
        List<String> seedArtists = mergeThemeSeedArtists(
                spec.seedArtists(),
                defaultThemeSeedArtists(language, era, genre)
        );
        return new ThemeAwareProfile(language, era, genre, mood, scene, contextKeywords, seedArtists);
    }

    private List<String> mergeThemeSeedArtists(List<String> primarySeeds, List<String> fallbackSeeds) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (primarySeeds != null) {
            primarySeeds.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        if (fallbackSeeds != null) {
            fallbackSeeds.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return merged.stream()
                .limit(MAX_THEME_SEED_ARTISTS)
                .toList();
    }

    private LinkedHashSet<String> buildThemeAwareQueries(
            String message,
            SearchQueryRefiner.SearchQueryHints fallbackHints,
            ThemeAwareProfile themeProfile
    ) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addQuery(queries, message);
        fallbackHints.candidates().stream().limit(3).forEach(candidate -> addQuery(queries, candidate));

        String localizedLanguage = localizedLanguageToken(themeProfile.language());
        String englishLanguage = englishLanguageToken(themeProfile.language());
        String localizedEra = localizedEraToken(themeProfile.era());
        String englishEra = englishEraToken(themeProfile.era());
        String localizedGenre = localizedGenreToken(themeProfile.genre());
        String englishGenre = englishGenreToken(themeProfile.genre());
        String localizedMood = localizedMoodToken(themeProfile.mood());
        String englishMood = englishMoodToken(themeProfile.mood());
        String localizedScene = localizedSceneToken(themeProfile.scene());
        String englishScene = englishSceneToken(themeProfile.scene());

        addQuery(queries, joinThemeTokens(localizedEra, localizedLanguage, localizedGenre, localizedMood, localizedScene));
        addQuery(queries, joinThemeTokens(englishEra, englishLanguage, englishGenre, englishMood, englishScene));
        addQuery(queries, joinThemeTokens(localizedEra, localizedLanguage, localizedGenre, "\u7ecf\u5178"));
        addQuery(queries, joinThemeTokens(englishEra, englishLanguage, englishGenre, "hits"));
        addQuery(queries, joinThemeTokens(localizedScene, localizedMood, localizedLanguage, localizedGenre));
        addQuery(queries, joinThemeTokens(englishScene, englishMood, englishLanguage, englishGenre));

        if (StringUtils.hasText(englishLanguage) || StringUtils.hasText(englishGenre)) {
            addQuery(queries, joinThemeTokens(englishLanguage, englishGenre, englishEra, "classics"));
        }
        if (StringUtils.hasText(localizedLanguage) || StringUtils.hasText(localizedGenre)) {
            addQuery(queries, joinThemeTokens(localizedLanguage, localizedGenre, localizedEra, "\u7ecf\u5178"));
        }
        if (!themeProfile.contextKeywords().isEmpty()) {
            addQuery(queries, joinThemeTokens(String.join(" ", themeProfile.contextKeywords()), localizedLanguage, localizedEra));
        }
        return queries;
    }

    private LinkedHashSet<String> buildSeedArtistThemeQueries(
            String seedArtist,
            ThemeAwareProfile themeProfile
    ) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (!StringUtils.hasText(seedArtist)) {
            return queries;
        }

        String localizedLanguage = localizedLanguageToken(themeProfile.language());
        String englishLanguage = englishLanguageToken(themeProfile.language());
        String localizedEra = localizedEraToken(themeProfile.era());
        String englishEra = englishEraToken(themeProfile.era());
        String localizedGenre = localizedGenreToken(themeProfile.genre());
        String englishGenre = englishGenreToken(themeProfile.genre());
        String localizedMood = localizedMoodToken(themeProfile.mood());
        String localizedScene = localizedSceneToken(themeProfile.scene());

        addQuery(queries, joinThemeTokens(seedArtist, localizedLanguage, localizedGenre, localizedEra));
        addQuery(queries, joinThemeTokens(seedArtist, englishLanguage, englishGenre, englishEra));
        addQuery(queries, joinThemeTokens(seedArtist, localizedLanguage, localizedMood, localizedScene));
        addQuery(queries, searchQueryRefiner.buildStructuredQuery(null, seedArtist, null));
        return queries;
    }

    private String detectThemeLanguage(String normalizedMessage) {
        if (containsAny(normalizedMessage, "\u7ca4\u8bed", "\u7cb5\u8a9e", "cantonese", "cantopop")) {
            return "cantonese";
        }
        if (containsAny(normalizedMessage, "\u56fd\u8bed", "\u534e\u8bed", "\u4e2d\u6587", "mandarin", "mandopop")) {
            return "mandarin";
        }
        if (containsAny(normalizedMessage, "\u82f1\u6587", "english")) {
            return "english";
        }
        if (containsAny(normalizedMessage, "\u65e5\u8bed", "\u65e5\u8a9e", "japanese", "j-pop", "jpop")) {
            return "japanese";
        }
        if (containsAny(normalizedMessage, "\u97e9\u8bed", "\u97d3\u8a9e", "korean", "k-pop", "kpop")) {
            return "korean";
        }
        return null;
    }

    private String detectThemeEra(String normalizedMessage) {
        if (containsAny(normalizedMessage, "80\u5e74\u4ee3", "80s", "1980s")) {
            return "1980s";
        }
        if (containsAny(normalizedMessage, "90\u5e74\u4ee3", "90s", "1990s")) {
            return "1990s";
        }
        if (containsAny(normalizedMessage, "00\u5e74\u4ee3", "2000s")) {
            return "2000s";
        }
        if (containsAny(normalizedMessage, "10\u5e74\u4ee3", "2010s")) {
            return "2010s";
        }
        if (containsAny(normalizedMessage, "20\u5e74\u4ee3", "2020s")) {
            return "2020s";
        }
        return null;
    }

    private String detectThemeGenre(String normalizedMessage, String language) {
        if ("cantonese".equals(language)) {
            return "cantopop";
        }
        if ("mandarin".equals(language)) {
            return "mandopop";
        }
        if (containsAny(normalizedMessage, "\u6c11\u8c23", "folk")) {
            return "folk";
        }
        if (containsAny(normalizedMessage, "\u6447\u6eda", "rock")) {
            return "rock";
        }
        if (containsAny(normalizedMessage, "\u6d41\u884c", "pop")) {
            return "pop";
        }
        if (containsAny(normalizedMessage, "\u6292\u60c5", "ballad")) {
            return "ballad";
        }
        return null;
    }

    private String detectThemeMood(String normalizedMessage) {
        if (containsAny(normalizedMessage, "\u96e8\u5929", "rain", "rainy")) {
            return "rainy";
        }
        if (containsAny(normalizedMessage, "\u5b89\u9759", "\u8212\u7de9", "\u8212\u670d", "calm", "relax", "relaxing")) {
            return "calm";
        }
        if (containsAny(normalizedMessage, "\u708e\u70ed", "\u70ed\u8840", "energetic", "upbeat")) {
            return "energetic";
        }
        return null;
    }

    private String detectThemeScene(String normalizedMessage) {
        if (containsAny(normalizedMessage, "\u901a\u52e4", "commute", "commuting")) {
            return "commute";
        }
        if (containsAny(normalizedMessage, "\u6df1\u591c", "\u591c\u665a", "\u665a\u4e0a", "late night", "night")) {
            return "late-night";
        }
        if (containsAny(normalizedMessage, "\u96e8\u5929", "rain", "rainy")) {
            return "rainy";
        }
        return null;
    }

    private String localizedLanguageToken(String language) {
        return switch (safeLower(language)) {
            case "cantonese" -> "\u7ca4\u8bed";
            case "mandarin" -> "\u4e2d\u6587";
            case "english" -> "\u82f1\u6587";
            case "japanese" -> "\u65e5\u8bed";
            case "korean" -> "\u97e9\u8bed";
            default -> null;
        };
    }

    private String englishLanguageToken(String language) {
        return switch (safeLower(language)) {
            case "cantonese" -> "cantopop";
            case "mandarin" -> "mandopop";
            case "english" -> "english pop";
            case "japanese" -> "j-pop";
            case "korean" -> "k-pop";
            default -> null;
        };
    }

    private String localizedEraToken(String era) {
        return switch (safeLower(era)) {
            case "1980s" -> "80\u5e74\u4ee3";
            case "1990s" -> "90\u5e74\u4ee3";
            case "2000s" -> "2000\u5e74\u4ee3";
            case "2010s" -> "2010\u5e74\u4ee3";
            case "2020s" -> "2020\u5e74\u4ee3";
            default -> null;
        };
    }

    private String englishEraToken(String era) {
        return switch (safeLower(era)) {
            case "1980s" -> "80s";
            case "1990s" -> "90s";
            case "2000s" -> "2000s";
            case "2010s" -> "2010s";
            case "2020s" -> "2020s";
            default -> null;
        };
    }

    private String localizedGenreToken(String genre) {
        return switch (safeLower(genre)) {
            case "cantopop" -> "\u7ca4\u8bed\u6d41\u884c";
            case "mandopop" -> "\u534e\u8bed\u6d41\u884c";
            case "folk" -> "\u6c11\u8c23";
            case "rock" -> "\u6447\u6eda";
            case "ballad" -> "\u6292\u60c5";
            case "pop" -> "\u6d41\u884c";
            default -> null;
        };
    }

    private String englishGenreToken(String genre) {
        return switch (safeLower(genre)) {
            case "cantopop" -> "cantopop";
            case "mandopop" -> "mandopop";
            case "folk" -> "folk";
            case "rock" -> "rock";
            case "ballad" -> "ballad";
            case "pop" -> "pop";
            default -> null;
        };
    }

    private String localizedMoodToken(String mood) {
        return switch (safeLower(mood)) {
            case "rainy" -> "\u96e8\u5929";
            case "calm" -> "\u8212\u7de9";
            case "energetic" -> "\u70ed\u8840";
            default -> null;
        };
    }

    private String englishMoodToken(String mood) {
        return switch (safeLower(mood)) {
            case "rainy" -> "rainy";
            case "calm" -> "calm";
            case "energetic" -> "energetic";
            default -> null;
        };
    }

    private String localizedSceneToken(String scene) {
        return switch (safeLower(scene)) {
            case "commute" -> "\u901a\u52e4";
            case "late-night" -> "\u6df1\u591c";
            case "rainy" -> "\u96e8\u5929";
            default -> null;
        };
    }

    private String englishSceneToken(String scene) {
        return switch (safeLower(scene)) {
            case "commute" -> "commute";
            case "late-night" -> "late night";
            case "rainy" -> "rainy";
            default -> null;
        };
    }

    private String joinThemeTokens(String... tokens) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                merged.add(token.trim());
            }
        }
        return String.join(" ", merged);
    }

    private List<String> defaultThemeSeedArtists(String language, String era, String genre) {
        String normalizedLanguage = safeLower(language);
        String normalizedEra = safeLower(era);
        String normalizedGenre = safeLower(genre);
        if ("cantonese".equals(normalizedLanguage)
                || "cantopop".equals(normalizedGenre)) {
            if ("1990s".equals(normalizedEra) || !StringUtils.hasText(normalizedEra)) {
                return List.of(
                        "\u5f20\u5b66\u53cb",
                        "\u9648\u6167\u5a34",
                        "\u8c2d\u548f\u9e9f",
                        "\u5f20\u56fd\u8363",
                        "\u6797\u5b50\u7965",
                        "\u6797\u5fc6\u83b2",
                        "\u5218\u5fb7\u534e",
                        "\u9ece\u660e",
                        "\u90ed\u5bcc\u57ce",
                        "\u674e\u514b\u52e4",
                        "\u90d1\u79c0\u6587",
                        "\u8bb8\u51a0\u6770"
                );
            }
        }
        if ("mandarin".equals(normalizedLanguage) || "mandopop".equals(normalizedGenre)) {
            return List.of(
                    "\u5468\u6770\u4f26",
                    "\u5f20\u60e0\u59b9",
                    "\u738b\u529b\u5b8f",
                    "\u6797\u4fca\u6770",
                    "\u8521\u4f9d\u6797",
                    "\u9648\u5955\u8fc5"
            );
        }
        return List.of();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String normalizedMessage, String... needles) {
        for (String needle : needles) {
            if (normalizedMessage.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String extractDecadeChinese(String normalizedMessage) {
        if (containsAny(normalizedMessage, "80\u5e74\u4ee3", "80s", "1980s")) {
            return "80\u5e74\u4ee3";
        }
        if (containsAny(normalizedMessage, "90\u5e74\u4ee3", "90s", "1990s")) {
            return "90\u5e74\u4ee3";
        }
        if (containsAny(normalizedMessage, "00\u5e74\u4ee3", "2000s")) {
            return "2000\u5e74\u4ee3";
        }
        if (containsAny(normalizedMessage, "10\u5e74\u4ee3", "2010s")) {
            return "2010\u5e74\u4ee3";
        }
        if (containsAny(normalizedMessage, "20\u5e74\u4ee3", "2020s")) {
            return "2020\u5e74\u4ee3";
        }
        return null;
    }

    private String extractDecadeEnglish(String normalizedMessage) {
        if (containsAny(normalizedMessage, "80\u5e74\u4ee3", "80s", "1980s")) {
            return "80s";
        }
        if (containsAny(normalizedMessage, "90\u5e74\u4ee3", "90s", "1990s")) {
            return "90s";
        }
        if (containsAny(normalizedMessage, "00\u5e74\u4ee3", "2000s")) {
            return "2000s";
        }
        if (containsAny(normalizedMessage, "10\u5e74\u4ee3", "2010s")) {
            return "2010s";
        }
        if (containsAny(normalizedMessage, "20\u5e74\u4ee3", "2020s")) {
            return "2020s";
        }
        return null;
    }

    private Optional<ArtistDto> resolvePrimaryArtist(String primaryArtist, List<String> fallbackArtistTerms) {
        LinkedHashSet<String> artistQueries = new LinkedHashSet<>();
        addQuery(artistQueries, primaryArtist);
        if (fallbackArtistTerms != null) {
            fallbackArtistTerms.forEach(term -> addQuery(artistQueries, term));
        }
        if (artistQueries.isEmpty()) {
            return Optional.empty();
        }

        String expectedArtist = artistQueries.getFirst();
        for (String artistQuery : artistQueries) {
            List<ArtistDto> artists = musicQueryApplicationService.searchArtists(artistQuery, 5);
            if (artists == null || artists.isEmpty()) {
                continue;
            }
            ArtistDto bestMatch = artists.stream()
                    .max(Comparator.comparingInt(artist -> scoreArtistMatch(artist, expectedArtist)))
                    .orElse(artists.getFirst());
            if (bestMatch != null) {
                return Optional.of(bestMatch);
            }
        }
        return Optional.empty();
    }

    private int scoreArtistMatch(ArtistDto artist, String expectedArtist) {
        String normalizedArtistName = normalizeForMatching(artist.name());
        String normalizedExpected = normalizeForMatching(expectedArtist);
        if (normalizedArtistName.isBlank() || normalizedExpected.isBlank()) {
            return 0;
        }
        if (normalizedArtistName.equals(normalizedExpected)) {
            return 3;
        }
        if (normalizedArtistName.contains(normalizedExpected)) {
            return 2;
        }
        if (normalizedExpected.contains(normalizedArtistName)) {
            return 1;
        }
        return 0;
    }

    private List<RecommendationCandidate> filterStrictArtistCandidates(
            List<RecommendationCandidate> candidates,
            String expectedArtist
    ) {
        List<RecommendationCandidate> directMatches = candidates.stream()
                .filter(candidate -> artistMatches(candidate, expectedArtist))
                .toList();
        if (!directMatches.isEmpty()) {
            return directMatches;
        }

        String dominantArtistId = resolveDominantArtistId(candidates);
        if (!StringUtils.hasText(dominantArtistId)) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> dominantArtistId.equals(candidate.artistId()))
                .toList();
    }

    private String resolveDominantArtistId(List<RecommendationCandidate> candidates) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.artistId())) {
                continue;
            }
            counts.merge(candidate.artistId(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(entry -> -firstAppearanceIndex(candidates, entry.getKey())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private int firstAppearanceIndex(List<RecommendationCandidate> candidates, String artistId) {
        for (int index = 0; index < candidates.size(); index++) {
            if (artistId.equals(candidates.get(index).artistId())) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private void addQuery(Set<String> queries, String value) {
        if (StringUtils.hasText(value)) {
            queries.add(value.trim());
        }
    }

    private String resolveArtistName(String artistId, Map<String, String> artistNameCache) {
        if (!StringUtils.hasText(artistId)) {
            return "";
        }
        return artistNameCache.computeIfAbsent(
                artistId,
                key -> musicQueryApplicationService.getArtist(key)
                        .map(ArtistDto::name)
                        .filter(StringUtils::hasText)
                        .orElse("")
        );
    }

    private String preferredArtistName(ArtistDto resolvedArtist, String requestedArtist) {
        if (StringUtils.hasText(requestedArtist)) {
            return requestedArtist.trim();
        }
        return resolvedArtist == null || resolvedArtist.name() == null ? "" : resolvedArtist.name();
    }

    private RecommendationSpec repairResolvedSpec(
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints hints
    ) {
        String hintedTrack = hints.explicitTitles().isEmpty() ? null : hints.explicitTitles().getFirst();
        String hintedAlbum = hints.albumTerms().isEmpty() ? null : hints.albumTerms().getFirst();
        String artist = choosePreferredValue(spec.artist(), hints.artistTerms().isEmpty() ? null : hints.artistTerms().getFirst());
        String track = choosePreferredValue(hintedTrack, spec.track());
        String album = choosePreferredValue(hintedAlbum, spec.album());
        String language = spec.language();
        String era = spec.era();
        String genre = spec.genre();
        String mood = spec.mood();
        String scene = spec.scene();
        if (StringUtils.hasText(track) && StringUtils.hasText(album) && normalizeForMatching(track).equals(normalizeForMatching(album))) {
            track = null;
        }

        RecommendationRequestMode requestMode = spec.requestMode() == null
                ? RecommendationRequestMode.infer(artist, track, album, spec.wantAdditionalTracks())
                : spec.requestMode();
        boolean albumOnlyEvidence = StringUtils.hasText(album)
                && hints.explicitTitles().isEmpty()
                && !hints.wantsAdditionalTracks();
        boolean explicitTrackEvidence = StringUtils.hasText(track) && !hints.explicitTitles().isEmpty();
        boolean latestEntityEvidence = !hints.artistTerms().isEmpty()
                || !hints.explicitTitles().isEmpty()
                || !hints.albumTerms().isEmpty();
        boolean themeOnlyEvidence = !latestEntityEvidence
                && hasThemeKeywordEvidence(hints);
        if (explicitTrackEvidence) {
            requestMode = RecommendationRequestMode.ENTITY_CONSTRAINED;
        } else if (albumOnlyEvidence && !StringUtils.hasText(track)) {
            requestMode = RecommendationRequestMode.ALBUM_ONLY;
        } else if (requestMode == RecommendationRequestMode.ARTIST_ONLY && !hints.albumTerms().isEmpty()) {
            requestMode = RecommendationRequestMode.ENTITY_CONSTRAINED;
        } else if (themeOnlyEvidence) {
            requestMode = RecommendationRequestMode.THEME_AWARE;
        }

        boolean wantAdditionalTracks = spec.wantAdditionalTracks() || hints.wantsAdditionalTracks();
        boolean mustIncludeExplicitTrack = StringUtils.hasText(track)
                && (spec.mustIncludeExplicitTrack() || !hints.explicitTitles().isEmpty());
        boolean preferSameArtist = StringUtils.hasText(artist)
                && (spec.preferSameArtist() || !hints.artistTerms().isEmpty());
        boolean preferSameAlbum = StringUtils.hasText(album)
                && (spec.preferSameAlbum() || !hints.albumTerms().isEmpty());

        if (requestMode == RecommendationRequestMode.ARTIST_ONLY) {
            track = null;
            album = null;
            wantAdditionalTracks = true;
            mustIncludeExplicitTrack = false;
            preferSameArtist = StringUtils.hasText(artist);
            preferSameAlbum = false;
        } else if (requestMode == RecommendationRequestMode.ALBUM_ONLY) {
            track = null;
            wantAdditionalTracks = false;
            mustIncludeExplicitTrack = false;
            preferSameAlbum = StringUtils.hasText(album);
            preferSameArtist = StringUtils.hasText(artist);
        } else if (requestMode == RecommendationRequestMode.ENTITY_CONSTRAINED) {
            mustIncludeExplicitTrack = StringUtils.hasText(track);
            preferSameArtist = StringUtils.hasText(artist);
            preferSameAlbum = StringUtils.hasText(album);
        } else if (requestMode == RecommendationRequestMode.THEME_AWARE) {
            artist = null;
            track = null;
            album = null;
            wantAdditionalTracks = false;
            mustIncludeExplicitTrack = false;
            preferSameArtist = false;
            preferSameAlbum = false;
        }

        return new RecommendationSpec(
                requestMode,
                artist,
                track,
                album,
                language,
                era,
                genre,
                mood,
                scene,
                spec.seedArtists(),
                spec.desiredTrackCount(),
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
        );
    }

    private boolean hasThemeKeywordEvidence(SearchQueryRefiner.SearchQueryHints hints) {
        if (hints == null || hints.contextKeywords().isEmpty()) {
            return false;
        }
        String combined = String.join(" ", hints.contextKeywords()).toLowerCase(Locale.ROOT);
        return containsAny(
                combined,
                "\u7ca4\u8bed",
                "\u7cb5\u8a9e",
                "\u56fd\u8bed",
                "\u83ef\u8a9e",
                "\u534e\u8bed",
                "\u4e2d\u6587",
                "\u82f1\u6587",
                "\u65e5\u8bed",
                "\u65e5\u8a9e",
                "\u97e9\u8bed",
                "\u97d3\u8a9e",
                "cantonese",
                "cantopop",
                "mandarin",
                "mandopop",
                "80\u5e74\u4ee3",
                "90\u5e74\u4ee3",
                "00\u5e74\u4ee3",
                "80s",
                "90s",
                "2000s",
                "\u96e8\u5929",
                "\u901a\u52e4",
                "\u6df1\u591c",
                "\u6447\u6eda",
                "\u6c11\u8c23",
                "\u6292\u60c5",
                "rain",
                "commute",
                "night",
                "rock",
                "folk",
                "ballad"
        );
    }

    private RecommendationSpec buildFallbackSpec(String message, SearchQueryRefiner.SearchQueryHints hints) {
        Integer explicitTrackCount = extractExplicitTrackCount(message);
        int desiredTrackCount = resolveDesiredTrackCount(null, explicitTrackCount);
        String artist = hints.artistTerms().isEmpty() ? null : hints.artistTerms().getFirst();
        if (!StringUtils.hasText(artist)) {
            artist = extractArtistOnlyFallback(message);
        }
        String track = hints.explicitTitles().isEmpty() ? null : hints.explicitTitles().getFirst();
        String album = hints.albumTerms().isEmpty() ? null : hints.albumTerms().getFirst();
        ThemeAwareProfile themeProfile = deriveThemeAwareProfile(new RecommendationSpec(
                RecommendationRequestMode.THEME_AWARE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                desiredTrackCount,
                false,
                false,
                false,
                false
        ), message, hints);
        RecommendationRequestMode requestMode = RecommendationRequestMode.infer(artist, track, album, hints.wantsAdditionalTracks());

        return new RecommendationSpec(
                requestMode,
                artist,
                track,
                album,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.language() : null,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.era() : null,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.genre() : null,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.mood() : null,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.scene() : null,
                requestMode == RecommendationRequestMode.THEME_AWARE ? themeProfile.seedArtists() : List.of(),
                desiredTrackCount,
                hints.wantsAdditionalTracks(),
                StringUtils.hasText(track),
                StringUtils.hasText(artist),
                StringUtils.hasText(album)
        );
    }

    private String extractArtistOnlyFallback(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = ARTIST_ONLY_COUNT_REQUEST_PATTERN.matcher(message.trim());
        if (!matcher.find()) {
            return null;
        }
        String artist = matcher.group(1);
        return StringUtils.hasText(artist) ? artist.trim() : null;
    }
    private RecommendationSpec mergeSpecWithFallback(
            RecommendationSpecResponse response,
            RecommendationSpec fallbackSpec,
            String message
    ) {
        Integer explicitTrackCount = extractExplicitTrackCount(message);
        int desiredTrackCount = resolveDesiredTrackCount(response == null ? null : response.desiredTrackCount(), explicitTrackCount);
        RecommendationRequestMode requestMode = chooseRequestMode(
                response == null ? null : response.requestMode(),
                fallbackSpec.requestMode()
        );
        String artist = choosePreferredValue(response == null ? null : response.artist(), fallbackSpec.artist());
        String track = choosePreferredValue(response == null ? null : response.track(), fallbackSpec.track());
        String album = choosePreferredValue(response == null ? null : response.album(), fallbackSpec.album());
        String language = choosePreferredValue(response == null ? null : response.language(), fallbackSpec.language());
        String era = choosePreferredValue(response == null ? null : response.era(), fallbackSpec.era());
        String genre = choosePreferredValue(response == null ? null : response.genre(), fallbackSpec.genre());
        String mood = choosePreferredValue(response == null ? null : response.mood(), fallbackSpec.mood());
        String scene = choosePreferredValue(response == null ? null : response.scene(), fallbackSpec.scene());
        List<String> seedArtists = response == null || response.seedArtists() == null || response.seedArtists().isEmpty()
                ? fallbackSpec.seedArtists()
                : response.seedArtists();
        boolean mustIncludeExplicitTrack = StringUtils.hasText(track)
                && (response == null || response.mustIncludeExplicitTrack() == null
                ? fallbackSpec.mustIncludeExplicitTrack()
                : response.mustIncludeExplicitTrack());
        boolean preferSameArtist = StringUtils.hasText(artist)
                && (response == null || response.preferSameArtist() == null
                ? fallbackSpec.preferSameArtist()
                : response.preferSameArtist());
        boolean preferSameAlbum = StringUtils.hasText(album)
                && (response == null || response.preferSameAlbum() == null
                ? fallbackSpec.preferSameAlbum()
                : response.preferSameAlbum());
        boolean wantAdditionalTracks = response == null || response.wantAdditionalTracks() == null
                ? fallbackSpec.wantAdditionalTracks()
                : response.wantAdditionalTracks();

        return new RecommendationSpec(
                requestMode,
                artist,
                track,
                album,
                language,
                era,
                genre,
                mood,
                scene,
                seedArtists,
                desiredTrackCount,
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
        );
    }

    private RecommendationRequestMode chooseRequestMode(
            String externalRequestMode,
            RecommendationRequestMode fallbackRequestMode
    ) {
        RecommendationRequestMode requestMode = RecommendationRequestMode.fromExternalValue(externalRequestMode);
        return requestMode == null ? fallbackRequestMode : requestMode;
    }

    private String choosePreferredValue(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private int resolveDesiredTrackCount(Integer modelCount, Integer explicitTrackCount) {
        if (explicitTrackCount != null) {
            return Math.max(1, Math.min(MAX_EXPLICIT_TRACK_COUNT, explicitTrackCount));
        }
        if (modelCount != null && modelCount >= DEFAULT_MIN_TRACK_COUNT && modelCount <= DEFAULT_MAX_TRACK_COUNT) {
            return modelCount;
        }
        return DEFAULT_TRACK_COUNT;
    }

    private Integer extractExplicitTrackCount(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher digitMatcher = DIGIT_TRACK_COUNT_PATTERN.matcher(message);
        if (digitMatcher.find()) {
            return Integer.parseInt(digitMatcher.group(1));
        }
        Matcher chineseMatcher = CHINESE_TRACK_COUNT_PATTERN.matcher(message);
        if (chineseMatcher.find()) {
            return parseChineseNumberStable(chineseMatcher.group(1));
        }
        return null;
    }

    private Integer parseChineseNumberStable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value) {
            case "\u5341" -> 10;
            case "\u5341\u4e00" -> 11;
            case "\u5341\u4e8c" -> 12;
            case "\u5341\u4e09" -> 13;
            case "\u5341\u56db" -> 14;
            case "\u5341\u4e94" -> 15;
            case "\u5341\u516d" -> 16;
            case "\u5341\u4e03" -> 17;
            case "\u5341\u516b" -> 18;
            case "\u5341\u4e5d" -> 19;
            case "\u4e8c\u5341" -> 20;
            case "\u4e8c\u5341\u4e00" -> 21;
            case "\u4e8c\u5341\u4e8c" -> 22;
            case "\u4e8c\u5341\u4e09" -> 23;
            case "\u4e8c\u5341\u56db" -> 24;
            case "\u4e8c\u5341\u4e94" -> 25;
            case "\u4e8c\u5341\u516d" -> 26;
            case "\u4e8c\u5341\u4e03" -> 27;
            case "\u4e8c\u5341\u516b" -> 28;
            case "\u4e8c\u5341\u4e5d" -> 29;
            case "\u4e09\u5341" -> 30;
            case "\u4e00" -> 1;
            case "\u4e8c", "\u4e24" -> 2;
            case "\u4e09" -> 3;
            case "\u56db" -> 4;
            case "\u4e94" -> 5;
            case "\u516d" -> 6;
            case "\u4e03" -> 7;
            case "\u516b" -> 8;
            case "\u4e5d" -> 9;
            default -> null;
        };
    }

    private List<RecommendationCandidate> buildOrderedCandidates(
            RecommendationSpec spec,
            List<RecommendationCandidate> candidates,
            Map<String, Integer> llmPreferenceOrder,
            ThemeAwareProfile themeProfile
    ) {
        List<RecommendationCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingInt((RecommendationCandidate candidate) -> bucketFor(candidate, spec))
                .reversed()
                .thenComparing(Comparator.comparingInt((RecommendationCandidate candidate) -> themeQualityBucket(candidate, spec, themeProfile)).reversed())
                .thenComparingInt(candidate -> llmPreferenceOrder.getOrDefault(candidate.trackId(), Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingInt((RecommendationCandidate candidate) -> deterministicScore(candidate, spec, themeProfile)).reversed())
                .thenComparing(RecommendationCandidate::title, String.CASE_INSENSITIVE_ORDER));

        RecommendationCandidate explicitCandidate = findBestExplicitCandidate(spec, ranked);
        if (explicitCandidate == null) {
            return ranked;
        }

        List<RecommendationCandidate> reordered = new ArrayList<>();
        reordered.add(explicitCandidate);
        List<RecommendationCandidate> nonDuplicateVariants = ranked.stream()
                .filter(candidate -> !candidate.trackId().equals(explicitCandidate.trackId()))
                .filter(candidate -> !titleMatches(candidate.title(), explicitCandidate.title()))
                .filter(candidate -> !isSpuriousShortTitleExpansion(candidate, spec))
                .toList();
        List<RecommendationCandidate> shortTitleExpansions = ranked.stream()
                .filter(candidate -> !candidate.trackId().equals(explicitCandidate.trackId()))
                .filter(candidate -> !titleMatches(candidate.title(), explicitCandidate.title()))
                .filter(candidate -> isSpuriousShortTitleExpansion(candidate, spec))
                .toList();
        List<RecommendationCandidate> duplicateTitleVariants = ranked.stream()
                .filter(candidate -> !candidate.trackId().equals(explicitCandidate.trackId()))
                .filter(candidate -> titleMatches(candidate.title(), explicitCandidate.title()))
                .toList();
        reordered.addAll(nonDuplicateVariants);
        reordered.addAll(shortTitleExpansions);
        reordered.addAll(duplicateTitleVariants);
        return reordered;
    }

    private List<RecommendationCandidate> applyHardScopes(
            RecommendationSpec spec,
            List<RecommendationCandidate> orderedCandidates,
            ThemeAwareProfile themeProfile
    ) {
        if (isAlbumOnlyMode(spec)) {
            List<RecommendationCandidate> albumScoped = orderedCandidates.stream()
                    .filter(candidate -> albumMatches(candidate.albumName(), spec.album()))
                    .toList();
            if (!StringUtils.hasText(spec.artist())) {
                return albumScoped;
            }
            List<RecommendationCandidate> directArtistMatches = albumScoped.stream()
                    .filter(candidate -> artistMatches(candidate, spec.artist()))
                    .toList();
            if (!directArtistMatches.isEmpty()) {
                return directArtistMatches;
            }
            String dominantAlbumArtistId = resolveDominantArtistId(albumScoped);
            if (!StringUtils.hasText(dominantAlbumArtistId)) {
                return albumScoped;
            }
            return albumScoped.stream()
                    .filter(candidate -> dominantAlbumArtistId.equals(candidate.artistId()))
                    .toList();
        }
        if (isThemeAwareMode(spec)) {
            return applyThemeDiversity(orderedCandidates, spec.desiredTrackCount(), themeProfile);
        }
        return orderedCandidates;
    }

    private List<RecommendationCandidate> applyThemeDiversity(
            List<RecommendationCandidate> orderedCandidates,
            int desiredTrackCount,
            ThemeAwareProfile themeProfile
    ) {
        if (orderedCandidates.isEmpty()) {
            return orderedCandidates;
        }
        int maxPerArtist = desiredTrackCount >= 12 ? 2 : 1;
        Map<String, Integer> artistCounts = new LinkedHashMap<>();
        Set<String> selectedTitleKeys = new LinkedHashSet<>();
        List<RecommendationCandidate> selected = new ArrayList<>();
        List<RecommendationCandidate> artistOverflow = new ArrayList<>();
        List<RecommendationCandidate> qualityOverflow = new ArrayList<>();
        List<RecommendationCandidate> duplicateTitleOverflow = new ArrayList<>();

        for (RecommendationCandidate candidate : orderedCandidates) {
            String artistKey = StringUtils.hasText(candidate.artistId()) ? candidate.artistId() : candidate.artistName();
            String titleKey = normalizeForMatching(candidate.title());
            if (StringUtils.hasText(titleKey) && selectedTitleKeys.contains(titleKey)) {
                duplicateTitleOverflow.add(candidate);
                continue;
            }
            if (isLowConfidenceThemeCandidate(candidate, themeProfile)) {
                qualityOverflow.add(candidate);
                continue;
            }
            int currentCount = artistCounts.getOrDefault(artistKey, 0);
            if (StringUtils.hasText(artistKey) && currentCount >= maxPerArtist) {
                artistOverflow.add(candidate);
                continue;
            }
            selected.add(candidate);
            if (StringUtils.hasText(titleKey)) {
                selectedTitleKeys.add(titleKey);
            }
            if (StringUtils.hasText(artistKey)) {
                artistCounts.put(artistKey, currentCount + 1);
            }
        }

        if (selected.size() >= desiredTrackCount) {
            return selected;
        }

        for (RecommendationCandidate candidate : artistOverflow) {
            addThemeOverflowCandidate(candidate, selected, duplicateTitleOverflow, selectedTitleKeys);
            if (selected.size() >= desiredTrackCount) {
                break;
            }
        }

        if (selected.size() >= desiredTrackCount) {
            return selected;
        }

        if (!selected.isEmpty()) {
            return selected;
        }

        for (RecommendationCandidate candidate : qualityOverflow) {
            addThemeOverflowCandidate(candidate, selected, duplicateTitleOverflow, selectedTitleKeys);
            if (selected.size() >= desiredTrackCount) {
                break;
            }
        }

        if (selected.size() >= desiredTrackCount) {
            return selected;
        }

        for (RecommendationCandidate candidate : duplicateTitleOverflow) {
            selected.add(candidate);
            if (selected.size() >= orderedCandidates.size()) {
                break;
            }
        }
        return selected;
    }

    private void addThemeOverflowCandidate(
            RecommendationCandidate candidate,
            List<RecommendationCandidate> selected,
            List<RecommendationCandidate> duplicateTitleOverflow,
            Set<String> selectedTitleKeys
    ) {
        String titleKey = normalizeForMatching(candidate.title());
        if (StringUtils.hasText(titleKey) && selectedTitleKeys.contains(titleKey)) {
            duplicateTitleOverflow.add(candidate);
            return;
        }
        selected.add(candidate);
        if (StringUtils.hasText(titleKey)) {
            selectedTitleKeys.add(titleKey);
        }
    }

    private RecommendationCandidate findBestExplicitCandidate(
            RecommendationSpec spec,
            List<RecommendationCandidate> candidates
    ) {
        if (!spec.mustIncludeExplicitTrack() || !StringUtils.hasText(spec.track())) {
            return null;
        }

        return candidates.stream()
                .filter(candidate -> titleMatches(candidate.title(), spec.track()))
                .filter(candidate -> !StringUtils.hasText(spec.artist()) || artistMatches(candidate, spec.artist()))
                .filter(candidate -> !StringUtils.hasText(spec.album()) || albumMatches(candidate.albumName(), spec.album()))
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .filter(candidate -> titleMatches(candidate.title(), spec.track()))
                        .filter(candidate -> !StringUtils.hasText(spec.artist()) || artistMatches(candidate, spec.artist()))
                        .findFirst()
                        .orElseGet(() -> candidates.stream()
                                .filter(candidate -> titleMatches(candidate.title(), spec.track()))
                                .findFirst()
                                .orElse(null)));
    }

    private int bucketFor(RecommendationCandidate candidate, RecommendationSpec spec) {
        boolean sameTitle = StringUtils.hasText(spec.track()) && titleMatches(candidate.title(), spec.track());
        boolean sameArtist = StringUtils.hasText(spec.artist()) && artistMatches(candidate, spec.artist());
        boolean sameAlbum = StringUtils.hasText(spec.album()) && albumMatches(candidate.albumName(), spec.album());
        if (sameTitle && sameArtist && sameAlbum) {
            return 6;
        }
        if (sameTitle && sameArtist) {
            return 5;
        }
        if (sameTitle) {
            return 4;
        }
        if (spec.preferSameAlbum() && sameAlbum && (!StringUtils.hasText(spec.artist()) || sameArtist)) {
            return 3;
        }
        if (spec.preferSameArtist() && sameArtist) {
            return 2;
        }
        if (sameAlbum) {
            return 1;
        }
        return 0;
    }

    private int deterministicScore(
            RecommendationCandidate candidate,
            RecommendationSpec spec,
            ThemeAwareProfile themeProfile
    ) {
        int score = 0;
        if (StringUtils.hasText(spec.track()) && titleMatches(candidate.title(), spec.track())) {
            score += 500;
        } else if (isSpuriousShortTitleExpansion(candidate, spec)) {
            score -= 80;
        }
        if (StringUtils.hasText(spec.artist()) && artistMatches(candidate, spec.artist())) {
            score += 260;
        } else if (StringUtils.hasText(spec.artist())) {
            score -= 220;
        }
        if (StringUtils.hasText(spec.album()) && albumMatches(candidate.albumName(), spec.album())) {
            score += 180;
        } else if (StringUtils.hasText(spec.album())) {
            score -= 60;
        }
        if (spec.wantAdditionalTracks() && StringUtils.hasText(spec.artist()) && artistMatches(candidate, spec.artist())) {
            score += 40;
        }
        if (isThemeAwareMode(spec)) {
            score += scoreThemeAwareCandidate(candidate, themeProfile);
        }
        return score;
    }

    private int themeQualityBucket(
            RecommendationCandidate candidate,
            RecommendationSpec spec,
            ThemeAwareProfile themeProfile
    ) {
        if (!isThemeAwareMode(spec) || themeProfile == null) {
            return 0;
        }
        if (isGenericNonSongSegment(candidate.title()) || isLiveOrConcertVariant(candidate.title(), candidate.albumName())) {
            return -2;
        }
        if (isLowConfidenceThemeCandidate(candidate, themeProfile)) {
            return -1;
        }
        int bucket = 1;
        if (candidate.retrievalHits() >= 3) {
            bucket += 2;
        } else if (candidate.retrievalHits() >= 2) {
            bucket += 1;
        }
        if (hasThemeSurfaceEvidence(candidate, themeProfile)) {
            bucket += 1;
        }
        return bucket;
    }

    private int scoreThemeAwareCandidate(
            RecommendationCandidate candidate,
            ThemeAwareProfile themeProfile
    ) {
        int score = candidate.retrievalHits() * 80;
        String title = candidate.title() == null ? "" : candidate.title();
        String album = candidate.albumName() == null ? "" : candidate.albumName();
        String artist = candidate.artistName() == null ? "" : candidate.artistName();
        String normalizedTitle = normalizeForMatching(title);
        String normalizedAlbum = normalizeForMatching(album);
        String normalizedArtist = normalizeForMatching(artist);

        if (themeProfile != null) {
            for (String keyword : themeProfile.contextKeywords()) {
                String normalizedKeyword = normalizeForMatching(keyword);
                if (normalizedKeyword.isBlank()) {
                    continue;
                }
                if (normalizedTitle.contains(normalizedKeyword)) {
                    score += 28;
                } else if (normalizedAlbum.contains(normalizedKeyword)) {
                    score += 20;
                } else if (normalizedArtist.contains(normalizedKeyword)) {
                    score += 12;
                }
            }
            String language = safeLower(themeProfile.language());
            if ("cantonese".equals(language) || "mandarin".equals(language)) {
                if (containsCjk(title) || containsCjk(album) || containsCjk(artist)) {
                    score += 35;
                } else {
                    score -= 25;
                }
            } else if ("english".equals(language) && !(containsCjk(title) || containsCjk(album) || containsCjk(artist))) {
                score += 18;
            }
            if ("1990s".equalsIgnoreCase(themeProfile.era())) {
                if (title.contains("90") || album.contains("90") || album.toLowerCase(Locale.ROOT).contains("classic")) {
                    score += 14;
                }
            }
            if (isLiveOrConcertVariant(title, album)) {
                score -= 45;
            }
            if (hasThemeSurfaceEvidence(candidate, themeProfile)) {
                score += 45;
            }
            for (String seedArtist : themeProfile.seedArtists()) {
                String normalizedSeedArtist = normalizeForMatching(seedArtist);
                if (!normalizedSeedArtist.isBlank() && normalizedArtist.contains(normalizedSeedArtist)) {
                    score += 35;
                    break;
                }
            }
        }
        return score;
    }

    private boolean hasThemeSurfaceEvidence(
            RecommendationCandidate candidate,
            ThemeAwareProfile themeProfile
    ) {
        if (candidate == null || themeProfile == null) {
            return false;
        }
        String combined = normalizeForMatching(String.join(
                " ",
                safeString(candidate.title()),
                safeString(candidate.artistName()),
                safeString(candidate.albumName())
        ));
        if (combined.isBlank()) {
            return false;
        }
        String[] evidenceTerms = {
                localizedLanguageToken(themeProfile.language()),
                englishLanguageToken(themeProfile.language()),
                localizedEraToken(themeProfile.era()),
                englishEraToken(themeProfile.era()),
                localizedGenreToken(themeProfile.genre()),
                englishGenreToken(themeProfile.genre()),
                localizedMoodToken(themeProfile.mood()),
                localizedSceneToken(themeProfile.scene())
        };
        for (String evidenceTerm : evidenceTerms) {
            String normalizedEvidence = normalizeForMatching(evidenceTerm);
            if (!normalizedEvidence.isBlank() && combined.contains(normalizedEvidence)) {
                return true;
            }
        }
        return false;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private boolean containsCjk(String value) {
        return StringUtils.hasText(value) && value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private boolean isLiveOrConcertVariant(String title, String album) {
        String combined = safeLower((title == null ? "" : title) + " " + (album == null ? "" : album));
        return containsAny(combined, " live", "- live", "(live", "concert", "\u6f14\u5531\u4f1a", "\u6f14\u5531\u6703", "\u73fe\u5834", "\u73b0\u573a", "\u62c9\u95ca");
    }

    private boolean isLowConfidenceThemeCandidate(
            RecommendationCandidate candidate,
            ThemeAwareProfile themeProfile
    ) {
        if (candidate == null) {
            return true;
        }
        if (isGenericNonSongSegment(candidate.title())) {
            return true;
        }
        if (isLiveOrConcertVariant(candidate.title(), candidate.albumName())) {
            return true;
        }
        if (themeProfile == null) {
            return false;
        }
        String language = safeLower(themeProfile.language());
        if (!"cantonese".equals(language) && !"mandarin".equals(language)) {
            return false;
        }
        if (!containsCjk(candidate.title())
                && !containsCjk(candidate.albumName())
                && !containsCjk(candidate.artistName())) {
            return true;
        }
        return !isHighConfidenceThemeCandidate(candidate, themeProfile);
    }

    private boolean isHighConfidenceThemeCandidate(
            RecommendationCandidate candidate,
            ThemeAwareProfile themeProfile
    ) {
        if (candidate == null || themeProfile == null) {
            return false;
        }
        return hasThemeSurfaceEvidence(candidate, themeProfile)
                || matchesThemeSeedArtist(candidate, themeProfile);
    }

    private boolean matchesThemeSeedArtist(
            RecommendationCandidate candidate,
            ThemeAwareProfile themeProfile
    ) {
        if (candidate == null || themeProfile == null || themeProfile.seedArtists().isEmpty()) {
            return false;
        }
        String normalizedArtist = normalizeForMatching(candidate.artistName());
        if (normalizedArtist.isBlank()) {
            return false;
        }
        for (String seedArtist : themeProfile.seedArtists()) {
            String normalizedSeedArtist = normalizeForMatching(seedArtist);
            if (!normalizedSeedArtist.isBlank() && normalizedArtist.contains(normalizedSeedArtist)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGenericNonSongSegment(String title) {
        String normalized = normalizeForMatching(title);
        return normalized.equals("intro")
                || normalized.equals("outro")
                || normalized.equals("interlude")
                || normalized.equals("skit")
                || normalized.equals("80s")
                || normalized.equals("90s")
                || normalized.equals("00s")
                || normalized.equals("1980s")
                || normalized.equals("1990s")
                || normalized.equals("2000s")
                || normalized.equals("80\u5e74\u4ee3")
                || normalized.equals("90\u5e74\u4ee3")
                || normalized.equals("2000\u5e74\u4ee3");
    }

    private boolean titleMatches(String actualTitle, String expectedTitle) {
        String normalizedActual = normalizeForMatching(actualTitle);
        String normalizedExpected = normalizeForMatching(expectedTitle);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equals(normalizedExpected)) {
            return true;
        }
        if (isShortCjkTitle(normalizedExpected)) {
            return normalizeBaseTitle(actualTitle).equals(normalizedExpected);
        }
        if (normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual)) {
            return true;
        }
        int lcsLength = longestCommonSubsequenceLength(normalizedActual, normalizedExpected);
        int minLength = Math.min(normalizedActual.length(), normalizedExpected.length());
        return minLength >= 4 && lcsLength >= minLength - 1;
    }

    private boolean isShortCjkTitle(String normalizedTitle) {
        return normalizedTitle.codePointCount(0, normalizedTitle.length()) <= 2
                && containsCjk(normalizedTitle);
    }

    private boolean isSpuriousShortTitleExpansion(RecommendationCandidate candidate, RecommendationSpec spec) {
        if (candidate == null || spec == null || !StringUtils.hasText(spec.track())) {
            return false;
        }
        String normalizedExpected = normalizeForMatching(spec.track());
        if (!isShortCjkTitle(normalizedExpected)) {
            return false;
        }
        String normalizedActual = normalizeForMatching(candidate.title());
        return StringUtils.hasText(normalizedActual)
                && normalizedActual.contains(normalizedExpected)
                && !titleMatches(candidate.title(), spec.track());
    }

    private String normalizeBaseTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String base = value.replaceFirst("(?i)\\s*[-\\u2013\\u2014(（\\[]\\s*(live|remaster(?:ed)?|version|demo|伴奏|演唱会|演唱會|现场|現場|重制|重錄|重录).*$", "");
        return normalizeForMatching(base);
    }

    private boolean artistMatches(RecommendationCandidate candidate, String expectedArtist) {
        String normalizedExpected = normalizeForMatching(expectedArtist);
        String normalizedArtistName = normalizeForMatching(candidate.artistName());
        String normalizedArtistId = normalizeForMatching(candidate.artistId());
        if (normalizedExpected.isBlank()) {
            return false;
        }
        return normalizedArtistName.equals(normalizedExpected)
                || normalizedArtistName.contains(normalizedExpected)
                || normalizedArtistId.equals(normalizedExpected);
    }

    private boolean albumMatches(String actualAlbum, String expectedAlbum) {
        String normalizedActual = normalizeForMatching(actualAlbum);
        String normalizedExpected = normalizeForMatching(expectedAlbum);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equals(normalizedExpected) || normalizedActual.contains(normalizedExpected)) {
            return true;
        }
        if (normalizedActual.length() >= 4 && normalizedExpected.contains(normalizedActual)) {
            return true;
        }
        int lcsLength = longestCommonSubsequenceLength(normalizedActual, normalizedExpected);
        int minLength = Math.min(normalizedActual.length(), normalizedExpected.length());
        return minLength >= 6 && lcsLength >= minLength - 1;
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

    private int longestCommonSubsequenceLength(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)) {
                    dp[leftIndex][rightIndex] = dp[leftIndex - 1][rightIndex - 1] + 1;
                } else {
                    dp[leftIndex][rightIndex] = Math.max(dp[leftIndex - 1][rightIndex], dp[leftIndex][rightIndex - 1]);
                }
            }
        }
        return dp[left.length()][right.length()];
    }

    private boolean isEnabled() {
        return agentChatProperties.liveLlmEnabled()
                && StringUtils.hasText(openAiProperties.resolvedApiKey())
                && StringUtils.hasText(openAiProperties.baseUrl())
                && openAiProperties.chat() != null
                && StringUtils.hasText(openAiProperties.chat().modelId());
    }

    private String buildSpecSystemPrompt() {
        return """
                You are the recommendation extraction stage for AgentMusic.
                Read the latest user request and produce one JSON object only.

                Rules:
                1. Preserve explicit artist, track title, and album names in the same language/script as the user request.
                2. Do not translate Chinese song titles, artist names, or album names into English.
                3. Treat the latest user message as the source of truth.
                4. Use prior conversation only when the latest user message is clearly referential, such as "more", "again", "similar", or "continue".
                5. If the latest user message already contains an explicit artist, track, album, era, language, or quantity, do not borrow those entities from older context.
                6. Classify the request into one requestMode:
                   - ARTIST_ONLY: explicit artist, no explicit track, no album-only scope
                   - ENTITY_CONSTRAINED: explicit artist with explicit track and/or album, and expansion outside the album is allowed
                   - ALBUM_ONLY: the user wants songs from a named album only
                   - THEME_AWARE: no explicit artist/track/album, only theme, era, mood, language, or scene
                   - GENERAL: use only if none of the above fit
                7. Treat equivalent album-only phrasing as the same meaning, for example:
                   - songs from album X
                   - tracks in album X
                   - the full album X
                8. For ALBUM_ONLY, set track=null unless the user explicitly names a separate song title in addition to the album.
                9. For ALBUM_ONLY, set wantAdditionalTracks=false unless the user explicitly asks for songs outside that album.
                10. If the user explicitly names a track, set mustIncludeExplicitTrack=true.
                11. If the user explicitly names an album, set preferSameAlbum=true.
                12. If the user explicitly names an artist, set preferSameArtist=true.
                13. If the user asks for "other songs", set wantAdditionalTracks=true.
                14. If the user specifies a number of songs, copy that number exactly into desiredTrackCount.
                15. If the user does not specify a number, choose a desiredTrackCount between 10 and 15.
                16. For THEME_AWARE, fill theme fields when they are explicit:
                    - language: cantonese | mandarin | english | japanese | korean
                    - era: 1980s | 1990s | 2000s | 2010s | 2020s
                    - genre: cantopop | mandopop | pop | rock | folk | ballad
                    - mood: rainy | calm | energetic
                    - scene: commute | late-night | rainy
                17. For THEME_AWARE, include 4 to 8 seedArtists when helpful. Use artists strongly associated with the requested language, era, genre, mood, or scene.
                18. If a field is not clearly present in the latest user message, leave it null.

                Return JSON with exactly these fields:
                {
                  "requestMode": "ARTIST_ONLY"|"ENTITY_CONSTRAINED"|"ALBUM_ONLY"|"THEME_AWARE"|"GENERAL",
                  "artist": string|null,
                  "track": string|null,
                  "album": string|null,
                  "language": string|null,
                  "era": string|null,
                  "genre": string|null,
                  "mood": string|null,
                  "scene": string|null,
                  "seedArtists": [string, ...],
                  "desiredTrackCount": integer,
                  "wantAdditionalTracks": boolean,
                  "mustIncludeExplicitTrack": boolean,
                  "preferSameArtist": boolean,
                  "preferSameAlbum": boolean
                }
                """;
    }

    private String buildSpecUserPrompt(PlanningContext planningContext) {
        SearchQueryRefiner.SearchQueryHints latestHints = searchQueryRefiner.analyze(planningContext.request().message());
        boolean includeRecentContext = shouldIncludeRecentContext(planningContext.request().message(), latestHints);
        StringBuilder builder = new StringBuilder();
        builder.append("latestUserMessage:\n")
                .append(planningContext.request().message() == null ? "" : planningContext.request().message().trim())
                .append("\n\nrecentConversation:\n");

        if (!includeRecentContext) {
            builder.append("(ignored for standalone request)");
        } else if (planningContext.recentConversation() == null || planningContext.recentConversation().isEmpty()) {
            builder.append("(none)");
        } else {
            planningContext.recentConversation().stream()
                    .sorted(Comparator.comparing(ChatMessageDto::createdAt))
                    .forEach(message -> builder.append("- ")
                            .append(message.role().name())
                            .append(": ")
                            .append(message.message())
                            .append('\n'));
        }

        builder.append("\nrecentRecommendationSummaries:\n");
        if (!includeRecentContext) {
            builder.append("(ignored for standalone request)");
        } else if (planningContext.recentRecommendationSummaries() == null
                || planningContext.recentRecommendationSummaries().isEmpty()) {
            builder.append("(none)");
        } else {
            for (String summary : planningContext.recentRecommendationSummaries()) {
                builder.append("- ").append(summary).append('\n');
            }
        }
        return builder.toString();
    }

    private boolean shouldIncludeRecentContext(
            String latestUserMessage,
            SearchQueryRefiner.SearchQueryHints latestHints
    ) {
        if (!StringUtils.hasText(latestUserMessage)) {
            return false;
        }
        String normalized = latestUserMessage.toLowerCase(Locale.ROOT);
        if (normalized.contains("\u7ee7\u7eed")
                || normalized.contains("\u518d\u6765")
                || normalized.contains("\u66f4\u591a")
                || normalized.contains("\u7c7b\u4f3c")
                || normalized.contains("\u540c\u6837")
                || normalized.contains("\u521a\u624d")
                || normalized.contains("\u4e4b\u524d")
                || normalized.contains("\u4e0a\u6b21")
                || normalized.contains("again")
                || normalized.contains("more")
                || normalized.contains("similar")
                || normalized.contains("continue")) {
            return true;
        }
        return latestHints.artistTerms().isEmpty()
                && latestHints.explicitTitles().isEmpty()
                && latestHints.albumTerms().isEmpty()
                && latestHints.contextKeywords().size() <= 1;
    }

    private String buildRerankSystemPrompt() {
        return """
                You are the recommendation reranking stage for AgentMusic.
                You receive a structured recommendation spec and a list of candidate tracks.
                Return one JSON object only.

                Rules:
                1. Select and order trackIds only from the provided candidate list.
                2. If the spec includes an explicit track title and a matching candidate exists, rank that track first.
                3. If the spec includes an explicit artist, prefer that artist strongly.
                4. If the spec includes an explicit album, prefer tracks from that album before the artist's other tracks.
                5. If requestMode is ALBUM_ONLY, do not rank tracks outside the target album above album tracks.
                6. If requestMode is ARTIST_ONLY, prefer breadth across the target artist's catalog and avoid unrelated artists.
                7. If requestMode is THEME_AWARE, prefer tracks that best match the requested language, era, scene, mood, and genre while keeping artist diversity.
                8. For theme-aware Chinese requests such as Cantonese or Mandarin, avoid clearly unrelated Latin-only outliers when better Han-script candidates exist.
                9. Prefer candidates with higher retrievalHits when several tracks are otherwise similar.
                10. Keep the result aligned with the latest user message, not older context.

                Return JSON with exactly this shape:
                {
                  "rankedTrackIds": ["trackId-1", "trackId-2", "..."]
                }
                """;
    }

    private String buildRerankUserPrompt(
            PlanningContext planningContext,
            RecommendationSpec spec,
            List<RecommendationCandidate> candidates
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("latestUserMessage:\n")
                .append(planningContext.request().message() == null ? "" : planningContext.request().message().trim())
                .append("\n\nrecommendationSpec:\n");
        try {
            builder.append(objectMapper.writeValueAsString(spec));
        } catch (JsonProcessingException exception) {
            builder.append("{\"serialization\":\"failed\"}");
        }
        builder.append("\n\ncandidates:\n");
        SearchQueryRefiner.SearchQueryHints latestHints = searchQueryRefiner.analyze(planningContext.request().message());
        ThemeAwareProfile themeProfile = deriveThemeAwareProfile(spec, planningContext.request().message(), latestHints);
        for (RecommendationCandidate candidate : candidates) {
            builder.append("- {")
                    .append("\"trackId\":\"").append(candidate.trackId()).append("\",")
                    .append("\"title\":\"").append(escapeForPrompt(candidate.title())).append("\",")
                    .append("\"artistName\":\"").append(escapeForPrompt(candidate.artistName())).append("\",")
                    .append("\"albumName\":\"").append(escapeForPrompt(candidate.albumName())).append("\",")
                    .append("\"retrievalHits\":").append(candidate.retrievalHits()).append(",")
                    .append("\"evidence\":{")
                    .append("\"explicitTitleMatch\":").append(StringUtils.hasText(spec.track()) && titleMatches(candidate.title(), spec.track())).append(",")
                    .append("\"shortTitleExpansion\":").append(isSpuriousShortTitleExpansion(candidate, spec)).append(",")
                    .append("\"artistMatch\":").append(StringUtils.hasText(spec.artist()) && artistMatches(candidate, spec.artist())).append(",")
                    .append("\"albumMatch\":").append(StringUtils.hasText(spec.album()) && albumMatches(candidate.albumName(), spec.album())).append(",")
                    .append("\"themeQualityBucket\":").append(themeQualityBucket(candidate, spec, themeProfile)).append(",")
                    .append("\"lowConfidenceThemeCandidate\":").append(isLowConfidenceThemeCandidate(candidate, themeProfile))
                    .append("}")
                    .append("}\n");
        }
        return builder.toString();
    }

    private String escapeForPrompt(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"");
    }

    private Map<?, ?> executeChatCompletionWithJson(List<Map<String, String>> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("temperature", agentChatProperties.llmTemperature());
        payload.put("messages", messages);
        payload.put("response_format", Map.of("type", "json_object"));
        return executeChatCompletion(payload);
    }

    private Map<?, ?> executeChatCompletion(Map<String, Object> payload) {
        int maxRetries = Math.max(0, agentChatProperties.llmHttpMaxRetries());
        long retryBackoffMs = Math.max(0L, agentChatProperties.llmHttpRetryBackoffMs());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return webClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolvedApiKey())
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(REQUEST_TIMEOUT);
            } catch (WebClientResponseException exception) {
                if (!isRetryable(exception) || attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            } catch (WebClientRequestException exception) {
                if (attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            }
        }

        throw new IllegalStateException("Recommendation LLM request failed without a terminal result.");
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) {
            return "";
        }
        Object firstChoice = choiceList.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return "";
        }
        Object message = choiceMap.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return "";
        }
        Object content = messageMap.get("content");
        return content instanceof String contentText ? contentText : "";
    }

    private boolean isRetryable(WebClientResponseException exception) {
        return exception instanceof WebClientResponseException.TooManyRequests
                || exception.getStatusCode().is5xxServerError();
    }

    private void sleepBeforeRetry(long retryBackoffMs, int multiplier) {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * multiplier);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recommendation LLM retry was interrupted.", exception);
        }
    }

    private String resolveBaseUrl(OpenAiProperties properties) {
        if (properties != null && StringUtils.hasText(properties.baseUrl())) {
            return properties.baseUrl().trim();
        }
        return "https://api.openai.com/v1";
    }

    private String abbreviate(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private record RecommendationSpecResponse(
            String requestMode,
            String artist,
            String track,
            String album,
            String language,
            String era,
            String genre,
            String mood,
            String scene,
            List<String> seedArtists,
            Integer desiredTrackCount,
            Boolean wantAdditionalTracks,
            Boolean mustIncludeExplicitTrack,
            Boolean preferSameArtist,
            Boolean preferSameAlbum
    ) {
    }

    private record RecommendationRerankResponse(List<String> rankedTrackIds) {
    }

    private record RecommendationCandidate(
            String trackId,
            String title,
            String artistId,
            String artistName,
            String albumName,
            int retrievalHits,
            TrackDto track
    ) {
    }

    private record ThemeAwareProfile(
            String language,
            String era,
            String genre,
            String mood,
            String scene,
            List<String> contextKeywords,
            List<String> seedArtists
    ) {
    }
}
