package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.planner.PlanningContext;

public interface RecommendationSelectionService {

    RecommendationSelection buildSelection(PlanningContext planningContext);
}
