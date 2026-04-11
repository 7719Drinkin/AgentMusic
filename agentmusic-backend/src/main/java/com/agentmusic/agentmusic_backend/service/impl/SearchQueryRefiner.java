package com.agentmusic.agentmusic_backend.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryRefiner {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION = Pattern.compile("[,，。！？、；：/\\\\|]+");

    private static final List<String> REMOVABLE_PHRASES = List.of(
            "\u7ed9\u6211\u63a8\u8350",
            "\u7ed9\u6211\u6765\u70b9",
            "\u6765\u4e00\u9996",
            "\u6765\u4e00\u4e9b",
            "\u6765\u4e00\u70b9",
            "\u6765\u70b9",
            "\u6211\u60f3\u542c",
            "\u9002\u5408",
            "\u63a8\u8350",
            "\u968f\u673a\u64ad\u653e",
            "\u4e0d\u8981\u64ad\u653e",
            "\u5148\u4e0d\u8981\u64ad\u653e",
            "\u5148\u522b\u64ad",
            "\u64ad\u653e",
            "\u968f\u673a",
            "\u6b4c\u5355",
            "\u6b4c\u66f2",
            "\u97f3\u4e50",
            "\u98ce\u683c",
            "\u4e00\u9996",
            "\u4e00\u4e9b",
            "\u4e00\u70b9",
            "\u7684",
            "\u6b4c",
            "\u8bf7",
            "\u5e2e\u6211",
            "\u7ed9\u6211"
    );

    public List<String> buildCandidates(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalize(rawQuery);
        if (!normalized.isBlank()) {
            candidates.add(normalized);
        }

        String simplified = simplify(normalized);
        if (!simplified.isBlank()) {
            candidates.add(simplified);
        }

        List<String> keywords = tokenize(simplified);
        if (!keywords.isEmpty()) {
            String joined = String.join(" ", keywords);
            candidates.add(joined);
            candidates.addAll(keywords);
        }

        return List.copyOf(candidates);
    }

    private String normalize(String rawQuery) {
        String lowered = rawQuery.toLowerCase(Locale.ROOT);
        String withoutPunctuation = PUNCTUATION.matcher(lowered).replaceAll(" ");
        return MULTI_SPACE.matcher(withoutPunctuation).replaceAll(" ").trim();
    }

    private String simplify(String normalizedQuery) {
        String simplified = normalizedQuery;
        for (String removable : REMOVABLE_PHRASES) {
            simplified = simplified.replace(removable, " ");
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
}
