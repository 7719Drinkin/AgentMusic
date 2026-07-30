package com.agentmusic.agentmusic_backend.planner;

public interface TaskPlanner {

    TaskPlanningResult createPlan(PlanningContext planningContext);
}
