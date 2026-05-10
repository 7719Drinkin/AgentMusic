package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.util.List;

public record RecommendationSelection(
        RecommendationSpec spec,
        List<TrackDto> tracks
) {
}
