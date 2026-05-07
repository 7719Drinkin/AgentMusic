package com.agentmusic.agentmusic_backend.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryRefiner {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}，。！？、；：“”‘’（）【】《》]+");
    private static final Pattern TITLE_PATTERN = Pattern.compile("《\\s*([^》]+?)\\s*》");

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
            "这样的",
            "那样",
            "刚才",
            "之前",
            "继续",
            "更多",
            "再来"
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
            Pattern.compile("以及(?:他|她|它|自己)?的?其他[^\\s]{0,12}(?:创作|制作)"),
            Pattern.compile("以及(?:他|她|它|自己)?的?其他歌曲?"),
            Pattern.compile("以及其他(?:他|她|它|自己)?的?(?:创作|写的|制作的)?歌曲?"),
            Pattern.compile("以及(?:他|她|它|自己)?的?其他"),
            Pattern.compile("其他(?:他|她|它|自己)?的?(?:创作|写的|制作的)?歌曲?"),
            Pattern.compile("他的"),
            Pattern.compile("自己(?:创作|写的|制作的)歌曲?"),
            Pattern.compile("歌曲?"),
            Pattern.compile("歌单"),
            Pattern.compile("音乐"),
            Pattern.compile("风格"),
            Pattern.compile("随机"),
            Pattern.compile("请"),
            Pattern.compile("帮我"),
            Pattern.compile("给我"),
            Pattern.compile("推荐"),
            Pattern.compile("的")
    );

    public List<String> buildCandidates(String rawQuery) {
        return analyze(rawQuery).candidates();
    }

    public String selectExecutionQuery(String latestUserMessage, String plannerQuery) {
        SearchQueryHints latestHints = analyze(latestUserMessage);
        if (shouldPreferLatestMessage(latestUserMessage, latestHints)) {
            return latestHints.candidates().getFirst();
        }

        SearchQueryHints plannerHints = analyze(plannerQuery);
        if (!plannerHints.candidates().isEmpty()) {
            return plannerHints.candidates().getFirst();
        }
        if (!latestHints.candidates().isEmpty()) {
            return latestHints.candidates().getFirst();
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
            return new SearchQueryHints(List.of(), List.of(), List.of(), false);
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalize(rawQuery);
        List<String> titles = extractTitles(rawQuery);
        String simplified = simplify(normalized);
        boolean wantsAdditionalTracks = normalized.contains("其他")
                || normalized.contains("other")
                || normalized.contains("more");
        List<String> keywords = tokenize(simplified);
        List<String> contextKeywords = keywords.stream()
                .filter(keyword -> titles.stream().noneMatch(title -> title.equals(keyword)))
                .toList();

        for (String title : titles) {
            addCandidate(candidates, joinKeywords(contextKeywords, title));
        }

        addCandidate(candidates, simplified);

        for (String title : titles) {
            addCandidate(candidates, title);
        }

        addCandidate(candidates, joinKeywords(keywords));
        addCandidate(candidates, joinKeywords(contextKeywords));
        keywords.forEach(keyword -> addCandidate(candidates, keyword));

        if (candidates.isEmpty()) {
            addCandidate(candidates, normalized);
        }

        return new SearchQueryHints(
                List.copyOf(candidates),
                titles,
                contextKeywords,
                wantsAdditionalTracks
        );
    }

    private String normalize(String rawQuery) {
        String lowered = rawQuery.toLowerCase(Locale.ROOT);
        String withoutPunctuation = PUNCTUATION.matcher(lowered).replaceAll(" ");
        return MULTI_SPACE.matcher(withoutPunctuation).replaceAll(" ").trim();
    }

    private String simplify(String normalizedQuery) {
        String simplified = normalizedQuery;
        for (Pattern removablePattern : REMOVABLE_PATTERNS) {
            simplified = removablePattern.matcher(simplified).replaceAll(" ");
        }
        return MULTI_SPACE.matcher(simplified).replaceAll(" ").trim();
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

    private List<String> extractTitles(String rawQuery) {
        Matcher matcher = TITLE_PATTERN.matcher(rawQuery);
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        while (matcher.find()) {
            String title = normalize(matcher.group(1));
            if (!title.isBlank()) {
                titles.add(title);
            }
        }
        return List.copyOf(titles);
    }

    private String joinKeywords(List<String> keywords, String... suffixes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (keywords != null) {
            merged.addAll(keywords);
        }
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (suffix != null && !suffix.isBlank()) {
                    merged.add(suffix.trim());
                }
            }
        }
        return String.join(" ", merged);
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (value == null) {
            return;
        }
        String normalized = MULTI_SPACE.matcher(value).replaceAll(" ").trim();
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
                || latestHints.wantsAdditionalTracks()
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
            List<String> explicitTitles,
            List<String> contextKeywords,
            boolean wantsAdditionalTracks
    ) {
    }
}
