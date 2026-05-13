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
        List<RecommendationCandidate> candidates = retrieveCandidates(spec, message);
        if (candidates.isEmpty()) {
            return new RecommendationSelection(spec, List.of());
        }

        Map<String, Integer> llmPreferenceOrder = resolveLlmPreferenceOrder(planningContext, spec, candidates);
        List<RecommendationCandidate> orderedCandidates = buildOrderedCandidates(spec, candidates, llmPreferenceOrder);
        List<RecommendationCandidate> hardScopedCandidates = applyHardScopes(spec, orderedCandidates);
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
                spec.artist(),
                spec.track(),
                spec.album(),
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

    private List<RecommendationCandidate> retrieveCandidates(RecommendationSpec spec, String message) {
        SearchQueryRefiner.SearchQueryHints fallbackHints = searchQueryRefiner.analyze(message);
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
                        resolvedArtist.get().name(),
                        track.albumName(),
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
                                resolvedArtist.map(ArtistDto::name).orElse(""),
                                track.albumName(),
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
                                resolvedArtist.get().name(),
                                track.albumName(),
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
                                resolvedArtist.get().name(),
                                track.albumName(),
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
                            resolvedArtist.get().name(),
                            track.albumName(),
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
            if (artists.isEmpty()) {
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

    private RecommendationSpec repairResolvedSpec(
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints hints
    ) {
        String artist = choosePreferredValue(spec.artist(), hints.artistTerms().isEmpty() ? null : hints.artistTerms().getFirst());
        String track = choosePreferredValue(spec.track(), hints.explicitTitles().isEmpty() ? null : hints.explicitTitles().getFirst());
        String album = choosePreferredValue(spec.album(), hints.albumTerms().isEmpty() ? null : hints.albumTerms().getFirst());
        if (StringUtils.hasText(track) && StringUtils.hasText(album) && normalizeForMatching(track).equals(normalizeForMatching(album))) {
            track = null;
        }

        RecommendationRequestMode requestMode = spec.requestMode() == null
                ? RecommendationRequestMode.infer(artist, track, album, spec.wantAdditionalTracks())
                : spec.requestMode();
        boolean albumOnlyEvidence = StringUtils.hasText(album)
                && hints.explicitTitles().isEmpty()
                && !hints.wantsAdditionalTracks();
        if (albumOnlyEvidence && !StringUtils.hasText(track)) {
            requestMode = RecommendationRequestMode.ALBUM_ONLY;
        }

        boolean wantAdditionalTracks = spec.wantAdditionalTracks();
        boolean mustIncludeExplicitTrack = spec.mustIncludeExplicitTrack() && StringUtils.hasText(track);
        boolean preferSameArtist = spec.preferSameArtist() && StringUtils.hasText(artist);
        boolean preferSameAlbum = spec.preferSameAlbum() && StringUtils.hasText(album);

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
        }

        return new RecommendationSpec(
                requestMode,
                artist,
                track,
                album,
                spec.desiredTrackCount(),
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
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
        RecommendationRequestMode requestMode = RecommendationRequestMode.infer(artist, track, album, hints.wantsAdditionalTracks());

        return new RecommendationSpec(
                requestMode,
                artist,
                track,
                album,
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
            Map<String, Integer> llmPreferenceOrder
    ) {
        List<RecommendationCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingInt((RecommendationCandidate candidate) -> bucketFor(candidate, spec))
                .reversed()
                .thenComparingInt(candidate -> llmPreferenceOrder.getOrDefault(candidate.trackId(), Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingInt((RecommendationCandidate candidate) -> deterministicScore(candidate, spec)).reversed())
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
                .toList();
        List<RecommendationCandidate> duplicateTitleVariants = ranked.stream()
                .filter(candidate -> !candidate.trackId().equals(explicitCandidate.trackId()))
                .filter(candidate -> titleMatches(candidate.title(), explicitCandidate.title()))
                .toList();
        reordered.addAll(nonDuplicateVariants);
        reordered.addAll(duplicateTitleVariants);
        return reordered;
    }

    private List<RecommendationCandidate> applyHardScopes(
            RecommendationSpec spec,
            List<RecommendationCandidate> orderedCandidates
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
        return orderedCandidates;
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

    private int deterministicScore(RecommendationCandidate candidate, RecommendationSpec spec) {
        int score = 0;
        if (StringUtils.hasText(spec.track()) && titleMatches(candidate.title(), spec.track())) {
            score += 500;
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
        return score;
    }

    private boolean titleMatches(String actualTitle, String expectedTitle) {
        String normalizedActual = normalizeForMatching(actualTitle);
        String normalizedExpected = normalizeForMatching(expectedTitle);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equals(normalizedExpected)
                || normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual)) {
            return true;
        }
        int lcsLength = longestCommonSubsequenceLength(normalizedActual, normalizedExpected);
        int minLength = Math.min(normalizedActual.length(), normalizedExpected.length());
        return minLength >= 4 && lcsLength >= minLength - 1;
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
        return normalizedActual.equals(normalizedExpected) || normalizedActual.contains(normalizedExpected);
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

                Return JSON with exactly these fields:
                {
                  "requestMode": "ARTIST_ONLY"|"ENTITY_CONSTRAINED"|"ALBUM_ONLY"|"THEME_AWARE"|"GENERAL",
                  "artist": string|null,
                  "track": string|null,
                  "album": string|null,
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
                7. Keep the result aligned with the latest user message, not older context.

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
        for (RecommendationCandidate candidate : candidates) {
            builder.append("- {")
                    .append("\"trackId\":\"").append(candidate.trackId()).append("\",")
                    .append("\"title\":\"").append(escapeForPrompt(candidate.title())).append("\",")
                    .append("\"artistName\":\"").append(escapeForPrompt(candidate.artistName())).append("\",")
                    .append("\"albumName\":\"").append(escapeForPrompt(candidate.albumName())).append("\"")
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
            TrackDto track
    ) {
    }
}
