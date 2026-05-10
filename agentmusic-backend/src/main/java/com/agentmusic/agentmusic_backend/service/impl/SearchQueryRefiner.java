package com.agentmusic.agentmusic_backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchQueryRefiner {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}，。！？、；：“”‘’（）【】《》〈〉『』「」…]+");
    private static final Pattern TITLE_PATTERN = Pattern.compile("《\\s*([^》]+?)\\s*》");
    private static final Pattern ALBUM_PREFIX_PATTERN =
            Pattern.compile("(?:专辑|album)\\s*《\\s*([^》]+?)\\s*》", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALBUM_SUFFIX_PATTERN =
            Pattern.compile("《\\s*([^》]+?)\\s*》\\s*(?:专辑|album)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALBUM_INLINE_PATTERN =
            Pattern.compile("(?:专辑|album)\\s*([\\p{IsHan}A-Za-z0-9·'\\-\\s]{2,40}?)(?=\\s*(?:里|中的|中|内|收录|包含|以及|并|$))",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ALBUM_OWNER_PATTERN =
            Pattern.compile("([\\p{IsHan}A-Za-z0-9·'\\-\\s]{1,40})(?:的)?(?:专辑|album)\\s*《\\s*([^》]+?)\\s*》",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern POSSESSIVE_TITLE_PATTERN =
            Pattern.compile("([\\p{IsHan}A-Za-z0-9·'\\-\\s]{1,40})的《\\s*([^》]+?)\\s*》");
    private static final Pattern STRUCTURED_FIELD_PATTERN =
            Pattern.compile("(track|artist|album):([^:]+?)(?=\\s+(?:track|artist|album):|$)", Pattern.CASE_INSENSITIVE);

    private static final List<String> REQUEST_ANCHOR_FRAGMENTS = List.of(
            "推荐",
            "来点",
            "想听",
            "歌单",
            "歌曲",
            "专辑",
            "playlist",
            "songs",
            "tracks",
            "album"
    );

    private static final List<String> REFERENTIAL_FRAGMENTS = List.of(
            "类似",
            "同样",
            "这样",
            "那样",
            "刚才",
            "之前",
            "继续",
            "更多",
            "再来"
    );

    private static final List<String> GENERIC_CONTEXT_TERMS = List.of(
            "推荐",
            "歌曲",
            "歌",
            "歌单",
            "音乐",
            "风格",
            "专辑",
            "播放",
            "收录",
            "包含",
            "里面",
            "里的",
            "其他",
            "一些"
    );

    private static final List<Pattern> REMOVABLE_PATTERNS = List.of(
            Pattern.compile("给我(?:再)?推荐(?:一次)?"),
            Pattern.compile("给我(?:再)?来点"),
            Pattern.compile("再推荐一次"),
            Pattern.compile("再来一次"),
            Pattern.compile("来一(?:首|些|点)"),
            Pattern.compile("来点"),
            Pattern.compile("我想听"),
            Pattern.compile("适合"),
            Pattern.compile("随机播放"),
            Pattern.compile("并开始播放"),
            Pattern.compile("开始播放"),
            Pattern.compile("但先不要播放"),
            Pattern.compile("先不要播放"),
            Pattern.compile("不要播放"),
            Pattern.compile("先别播"),
            Pattern.compile("稍后播放"),
            Pattern.compile("以及(?:他|她|它)?(?:自己)?的?其他(?:歌曲|歌|作品)?"),
            Pattern.compile("其他(?:他|她|它)?(?:自己)?的?(?:歌曲|歌|作品)?"),
            Pattern.compile("以及其他"),
            Pattern.compile("他的"),
            Pattern.compile("她的"),
            Pattern.compile("自己的?(?:创作|写的|制作的)?(?:歌曲|歌)?"),
            Pattern.compile("歌曲?"),
            Pattern.compile("歌单"),
            Pattern.compile("音乐"),
            Pattern.compile("风格"),
            Pattern.compile("随机"),
            Pattern.compile("请"),
            Pattern.compile("帮我"),
            Pattern.compile("给我"),
            Pattern.compile("推荐"),
            Pattern.compile("一些"),
            Pattern.compile("的")
    );

    public List<String> buildCandidates(String rawQuery) {
        return analyze(rawQuery).candidates();
    }

    public boolean isStructuredSpotifyQuery(String rawQuery) {
        return StringUtils.hasText(rawQuery) && STRUCTURED_FIELD_PATTERN.matcher(rawQuery.trim()).find();
    }

    public String buildStructuredQuery(String track, String artist, String album) {
        List<String> fields = new ArrayList<>();
        if (StringUtils.hasText(track)) {
            fields.add("track:" + normalizePhrase(track));
        }
        if (StringUtils.hasText(artist)) {
            fields.add("artist:" + normalizePhrase(artist));
        }
        if (StringUtils.hasText(album)) {
            fields.add("album:" + normalizePhrase(album));
        }
        return String.join(" ", fields).trim();
    }

    public String selectExecutionQuery(String latestUserMessage, String plannerQuery) {
        SearchQueryHints latestHints = analyze(latestUserMessage);
        if (shouldPreferLatestMessage(latestUserMessage, latestHints)) {
            return firstPreferredCandidate(latestHints);
        }

        SearchQueryHints plannerHints = analyze(plannerQuery);
        if (!plannerHints.candidates().isEmpty()) {
            return firstPreferredCandidate(plannerHints);
        }
        if (!latestHints.candidates().isEmpty()) {
            return firstPreferredCandidate(latestHints);
        }
        if (plannerQuery != null && !plannerQuery.isBlank()) {
            return plannerQuery.trim();
        }
        if (latestUserMessage != null && !latestUserMessage.isBlank()) {
            return latestUserMessage.trim();
        }
        return "";
    }

    public SearchQueryHints analyze(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return new SearchQueryHints(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
        }
        if (isStructuredSpotifyQuery(rawQuery)) {
            return analyzeStructuredQuery(rawQuery);
        }

        String normalized = normalize(rawQuery);
        List<String> albumTerms = extractAlbumTerms(rawQuery);
        List<String> quotedTerms = extractQuotedTerms(rawQuery);
        List<String> explicitTitles = quotedTerms.stream()
                .filter(title -> albumTerms.stream().noneMatch(album -> album.equals(title)))
                .toList();
        String simplified = stripKnownTerms(simplify(normalized), explicitTitles, albumTerms);
        List<String> keywords = tokenize(simplified);
        List<String> artistTerms = extractArtistTerms(rawQuery, explicitTitles, albumTerms, keywords);
        List<String> contextKeywords = keywords.stream()
                .filter(keyword -> explicitTitles.stream().noneMatch(title -> title.equals(keyword)))
                .filter(keyword -> albumTerms.stream().noneMatch(album -> album.equals(keyword)))
                .filter(keyword -> artistTerms.stream().noneMatch(artist -> artist.equals(keyword)))
                .filter(keyword -> GENERIC_CONTEXT_TERMS.stream().noneMatch(generic -> generic.equals(keyword)))
                .toList();
        boolean wantsAdditionalTracks = normalized.contains("其他")
                || normalized.contains("别的")
                || normalized.contains("other")
                || normalized.contains("more");

        LinkedHashSet<String> structuredCandidates = new LinkedHashSet<>();
        structuredCandidates.addAll(buildStructuredCandidates(explicitTitles, artistTerms, albumTerms));

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        structuredCandidates.forEach(candidate -> addCandidate(candidates, candidate));

        for (String title : explicitTitles) {
            addCandidate(candidates, joinKeywords(artistTerms, title));
            addCandidate(candidates, joinKeywords(artistTerms, albumTerms, title));
        }
        addCandidate(candidates, simplified);
        explicitTitles.forEach(title -> addCandidate(candidates, title));
        artistTerms.forEach(artist -> addCandidate(candidates, artist));
        albumTerms.forEach(album -> addCandidate(candidates, album));
        addCandidate(candidates, joinKeywords(artistTerms));
        addCandidate(candidates, joinKeywords(albumTerms));
        addCandidate(candidates, joinKeywords(contextKeywords));
        addCandidate(candidates, joinKeywords(artistTerms, contextKeywords));

        if (candidates.isEmpty()) {
            addCandidate(candidates, normalized);
        }

        return new SearchQueryHints(
                List.copyOf(candidates),
                List.copyOf(structuredCandidates),
                explicitTitles,
                artistTerms,
                albumTerms,
                contextKeywords,
                wantsAdditionalTracks
        );
    }

    private SearchQueryHints analyzeStructuredQuery(String structuredQuery) {
        LinkedHashSet<String> tracks = new LinkedHashSet<>();
        LinkedHashSet<String> artists = new LinkedHashSet<>();
        LinkedHashSet<String> albums = new LinkedHashSet<>();
        Matcher matcher = STRUCTURED_FIELD_PATTERN.matcher(structuredQuery.trim());
        while (matcher.find()) {
            String field = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = normalizePhrase(matcher.group(2));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            switch (field) {
                case "track" -> tracks.add(value);
                case "artist" -> artists.add(value);
                case "album" -> albums.add(value);
                default -> {
                }
            }
        }

        return new SearchQueryHints(
                List.of(structuredQuery.trim()),
                List.of(structuredQuery.trim()),
                List.copyOf(tracks),
                List.copyOf(artists),
                List.copyOf(albums),
                List.of(),
                false
        );
    }

    private String firstPreferredCandidate(SearchQueryHints hints) {
        if (!hints.structuredCandidates().isEmpty()) {
            return hints.structuredCandidates().getFirst();
        }
        return hints.candidates().getFirst();
    }

    private String normalize(String rawQuery) {
        String lowered = rawQuery.toLowerCase(Locale.ROOT);
        String withoutPunctuation = PUNCTUATION.matcher(lowered).replaceAll(" ");
        return MULTI_SPACE.matcher(withoutPunctuation).replaceAll(" ").trim();
    }

    private String normalizePhrase(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return MULTI_SPACE.matcher(rawValue.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private String simplify(String normalizedQuery) {
        String simplified = normalizedQuery;
        for (Pattern removablePattern : REMOVABLE_PATTERNS) {
            simplified = removablePattern.matcher(simplified).replaceAll(" ");
        }
        return MULTI_SPACE.matcher(simplified).replaceAll(" ").trim();
    }

    private String stripKnownTerms(String simplifiedQuery, List<String> explicitTitles, List<String> albumTerms) {
        String stripped = simplifiedQuery;
        for (String title : explicitTitles) {
            stripped = stripped.replace(title, " ");
        }
        for (String album : albumTerms) {
            stripped = stripped.replace(album, " ");
        }
        return MULTI_SPACE.matcher(stripped).replaceAll(" ").trim();
    }

    private List<String> tokenize(String simplifiedQuery) {
        if (simplifiedQuery == null || simplifiedQuery.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : simplifiedQuery.split("\\s+")) {
            String cleaned = token.trim();
            if (cleaned.isBlank() || cleaned.length() <= 1) {
                continue;
            }
            tokens.add(cleaned);
        }
        return List.copyOf(tokens);
    }

    private List<String> extractQuotedTerms(String rawQuery) {
        Matcher matcher = TITLE_PATTERN.matcher(rawQuery);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            String value = normalizePhrase(matcher.group(1));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private List<String> extractAlbumTerms(String rawQuery) {
        LinkedHashSet<String> albums = new LinkedHashSet<>();
        collectCapturedValues(ALBUM_PREFIX_PATTERN, rawQuery, albums, 1);
        collectCapturedValues(ALBUM_SUFFIX_PATTERN, rawQuery, albums, 1);
        collectCapturedValues(ALBUM_INLINE_PATTERN, rawQuery, albums, 1);
        return List.copyOf(albums);
    }

    private void collectCapturedValues(
            Pattern pattern,
            String rawQuery,
            Set<String> values,
            int groupIndex
    ) {
        Matcher matcher = pattern.matcher(rawQuery);
        while (matcher.find()) {
            String value = normalizePhrase(matcher.group(groupIndex));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
    }

    private List<String> extractArtistTerms(
            String rawQuery,
            List<String> explicitTitles,
            List<String> albumTerms,
            List<String> keywords
    ) {
        LinkedHashSet<String> artists = new LinkedHashSet<>();
        Matcher albumOwnerMatcher = ALBUM_OWNER_PATTERN.matcher(rawQuery);
        while (albumOwnerMatcher.find()) {
            String matchedAlbum = normalizePhrase(albumOwnerMatcher.group(2));
            if (albumTerms.stream().noneMatch(album -> album.equals(matchedAlbum))) {
                continue;
            }
            String candidate = stripKnownTerms(
                    simplify(normalize(albumOwnerMatcher.group(1))),
                    explicitTitles,
                    albumTerms
            );
            addNormalizedPhrase(artists, candidate);
        }

        Matcher matcher = POSSESSIVE_TITLE_PATTERN.matcher(rawQuery);
        while (matcher.find()) {
            String matchedTitle = normalizePhrase(matcher.group(2));
            if (explicitTitles.stream().noneMatch(title -> title.equals(matchedTitle))) {
                continue;
            }
            String candidate = stripKnownTerms(
                    simplify(normalize(matcher.group(1))),
                    explicitTitles,
                    albumTerms
            );
            addNormalizedPhrase(artists, candidate);
        }

        if (artists.isEmpty() && (!explicitTitles.isEmpty() || !albumTerms.isEmpty())) {
            List<String> filtered = keywords.stream()
                    .filter(keyword -> explicitTitles.stream().noneMatch(title -> title.equals(keyword)))
                    .filter(keyword -> albumTerms.stream().noneMatch(album -> album.equals(keyword)))
                    .filter(keyword -> GENERIC_CONTEXT_TERMS.stream().noneMatch(generic -> generic.equals(keyword)))
                    .toList();
            if (!filtered.isEmpty()) {
                int upperBound = Math.min(filtered.size(), 3);
                addNormalizedPhrase(artists, joinKeywords(filtered.subList(0, upperBound)));
            }
        }

        return List.copyOf(artists);
    }

    private void addNormalizedPhrase(Set<String> phrases, String phrase) {
        String normalized = normalizePhrase(phrase);
        if (StringUtils.hasText(normalized)
                && GENERIC_CONTEXT_TERMS.stream().noneMatch(generic -> generic.equals(normalized))
                && !"里".equals(normalized)
                && !"中的".equals(normalized)
                && !"中".equals(normalized)
                && !"内".equals(normalized)) {
            phrases.add(normalized);
        }
    }

    private List<String> buildStructuredCandidates(
            List<String> explicitTitles,
            List<String> artistTerms,
            List<String> albumTerms
    ) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        for (String title : explicitTitles) {
            for (String artist : artistTerms) {
                for (String album : albumTerms) {
                    addCandidate(candidates, buildStructuredQuery(title, artist, album));
                }
                addCandidate(candidates, buildStructuredQuery(title, artist, null));
            }
            for (String album : albumTerms) {
                addCandidate(candidates, buildStructuredQuery(title, null, album));
            }
            addCandidate(candidates, buildStructuredQuery(title, null, null));
        }

        for (String artist : artistTerms) {
            for (String album : albumTerms) {
                addCandidate(candidates, buildStructuredQuery(null, artist, album));
            }
            addCandidate(candidates, buildStructuredQuery(null, artist, null));
        }

        albumTerms.forEach(album -> addCandidate(candidates, buildStructuredQuery(null, null, album)));
        return List.copyOf(candidates);
    }

    private String joinKeywords(List<String> keywords, String... suffixes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (keywords != null) {
            merged.addAll(keywords);
        }
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (suffix != null && !suffix.isBlank()) {
                    merged.add(normalizePhrase(suffix));
                }
            }
        }
        return String.join(" ", merged);
    }

    private String joinKeywords(List<String> first, List<String> second, String... suffixes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (StringUtils.hasText(suffix)) {
                    merged.add(normalizePhrase(suffix));
                }
            }
        }
        return String.join(" ", merged);
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = MULTI_SPACE.matcher(value.trim()).replaceAll(" ");
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }
    }

    private boolean shouldPreferLatestMessage(String latestUserMessage, SearchQueryHints latestHints) {
        if (latestHints.candidates().isEmpty()) {
            return false;
        }
        String normalizedLatest = normalize(latestUserMessage);
        return !latestHints.explicitTitles().isEmpty()
                || !latestHints.albumTerms().isEmpty()
                || latestHints.wantsAdditionalTracks()
                || !latestHints.structuredCandidates().isEmpty()
                || latestHints.artistTerms().size() >= 1
                || latestHints.contextKeywords().size() >= 2
                || (latestHints.contextKeywords().size() == 1
                && containsAny(normalizedLatest, REQUEST_ANCHOR_FRAGMENTS)
                && !containsAny(normalizedLatest, REFERENTIAL_FRAGMENTS));
    }

    private boolean containsAny(String value, List<String> fragments) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public record SearchQueryHints(
            List<String> candidates,
            List<String> structuredCandidates,
            List<String> explicitTitles,
            List<String> artistTerms,
            List<String> albumTerms,
            List<String> contextKeywords,
            boolean wantsAdditionalTracks
    ) {
    }
}
