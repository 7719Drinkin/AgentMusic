package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchQueryRefinerTests {

    private final SearchQueryRefiner refiner = new SearchQueryRefiner();

    @Test
    void shouldExtractCompactKeywordsFromNaturalLanguageRecommendationPrompt() {
        List<String> candidates = refiner.buildCandidates(
                "\u7ed9\u6211\u6765\u70b9\u8f7b\u677e\u7684\u7ca4\u8bed\u6b4c\uff0c\u968f\u673a\u64ad\u653e"
        );

        assertThat(candidates).startsWith("\u8f7b\u677e \u7ca4\u8bed");
    }

    @Test
    void shouldPrioritizeExplicitTrackTitleAndArtistWhenPresent() {
        List<String> candidates = refiner.buildCandidates(
                "\u7ed9\u6211\u518d\u63a8\u8350\u4e00\u6b21\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u5176\u4ed6\u4ed6\u81ea\u5df1\u521b\u4f5c\u7684\u6b4c\u66f2\uff0c\u4f46\u5148\u4e0d\u8981\u64ad\u653e"
        );

        assertThat(candidates).startsWith("track:\u6cb3 artist:\u5f20\u96e8\u751f");
        assertThat(candidates).contains("\u5f20\u96e8\u751f \u6cb3", "\u6cb3", "\u5f20\u96e8\u751f");
        assertThat(candidates).doesNotContain("\u96e8\u5929\u901a\u52e4");
    }

    @Test
    void shouldKeepLatestRequestTopicInsteadOfBackgroundMemoryWords() {
        List<String> candidates = refiner.buildCandidates(
                "\u6765\u70b9\u540e\u6447\u6eda\u4e50\uff0c\u4f46\u5148\u4e0d\u8981\u64ad\u653e"
        );

        assertThat(candidates).startsWith("\u540e\u6447\u6eda\u4e50");
        assertThat(candidates).doesNotContain("\u96e8\u5929\u901a\u52e4");
    }

    @Test
    void shouldPreferLatestMessageQueryOverEnglishPlannerRewriteWhenExplicitTitleExists() {
        String executionQuery = refiner.selectExecutionQuery(
                "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2",
                "He of Tom Chang"
        );

        assertThat(executionQuery).isEqualTo("track:\u6cb3 artist:\u5f20\u96e8\u751f");
    }

    @Test
    void shouldIncludeAlbumInStructuredCandidatesWhenAlbumIsExplicit() {
        SearchQueryRefiner.SearchQueryHints hints = refiner.analyze(
                "\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2"
        );

        assertThat(hints.explicitTitles()).containsExactly("\u53d1\u6655");
        assertThat(hints.artistTerms()).containsExactly("\u5f20\u96e8\u751f");
        assertThat(hints.albumTerms()).containsExactly("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(hints.structuredCandidates()).startsWith(
                "track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"
        );
    }

}
