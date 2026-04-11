package com.agentmusic.agentmusic_backend;

import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryRefinerTests {

    private final SearchQueryRefiner refiner = new SearchQueryRefiner();

    @Test
    void shouldExtractSearchKeywordsFromNaturalLanguageRecommendationPrompt() {
        List<String> candidates = refiner.buildCandidates("给我来点轻松的粤语歌，随机播放");

        assertThat(candidates).contains("轻松 粤语");
        assertThat(candidates).contains("轻松");
        assertThat(candidates).contains("粤语");
    }
}
