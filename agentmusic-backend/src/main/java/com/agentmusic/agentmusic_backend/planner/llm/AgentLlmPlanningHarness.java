package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentLlmPlanningHarness {

    private static final int MAX_SUMMARY_LENGTH = 140;
    private static final int MAX_REASONING_LENGTH = 180;
    private static final String QUERY_ARGUMENT = "query";
    private static final String LIMIT_ARGUMENT = "limit";

    private static final Map<AgentIntent, List<PlanStepType>> STEP_TEMPLATES = createStepTemplates();

    private final AgentChatProperties agentChatProperties;
    private final ObjectMapper objectMapper;

    public AgentLlmPlanningHarness(
            AgentChatProperties agentChatProperties,
            ObjectMapper objectMapper
    ) {
        this.agentChatProperties = agentChatProperties;
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    }

    public AgentLlmPlanningRequest buildRequest(PlanningContext context) {
        List<String> recentMessages = context.recentMessages() == null
                ? List.of()
                : context.recentMessages().stream()
                .filter(StringUtils::hasText)
                .limit(Math.max(1, agentChatProperties.llmRecentMessageLimit()))
                .toList();

        return new AgentLlmPlanningRequest(
                schemaVersion(),
                context.request().userId(),
                context.request().voiceInput(),
                safeTrim(context.request().message()),
                recentMessages,
                Arrays.stream(AgentIntent.values()).map(Enum::name).toList(),
                Arrays.stream(PlanStepType.values()).map(Enum::name).toList()
        );
    }

    public String buildSystemPrompt() {
        String stepTemplates = STEP_TEMPLATES.entrySet().stream()
                .map(entry -> "- " + entry.getKey().name() + " -> " + formatStepTypes(entry.getValue()))
                .collect(Collectors.joining("\n"));

        return """
                You are the AgentMusic planning harness.
                Your only task is to classify the latest user request and emit a normalized execution plan.

                Return exactly one JSON object. Do not return markdown. Do not add prose outside the JSON object.

                Output contract:
                - schemaVersion: must equal "%s"
                - intent: must be one of the allowed intent enum values
                - summary: concise English summary, max %d characters
                - reasoning: concise classification rationale, max %d characters, no hidden chain-of-thought
                - confidence: integer 0-100
                - steps: ordered array of step objects

                Step object contract:
                - type: must be one of the allowed plan step enum values
                - arguments: JSON object only

                Hard constraints:
                1. Use only the intents and step types listed in the request payload.
                2. The step array must match the exact template for the selected intent.
                3. READ_CHAT_CONTEXT must always be the first step.
                4. PERSIST_CHAT_REPLY must always be the last step.
                5. Steps that require query must include a non-empty "query" argument in the user's original language.
                6. READ_CHAT_CONTEXT and READ_PLAYLIST_HISTORY must include only an integer "limit" argument.
                7. READ_USER_PREFERENCES, READ_LOCAL_SESSION, and PERSIST_CHAT_REPLY must use an empty arguments object.
                8. If the request is ambiguous, prefer CHAT_ONLY or UNKNOWN instead of inventing tool work.
                9. Never invent unsupported step types, unsupported arguments, or additional JSON fields.

                Intent selection rules:
                - If the user asks for recommended songs, a generated playlist, or music that fits a mood / scene / genre, use RECOMMEND_PLAYLIST or PLAY_RECOMMENDATION.
                - If that same recommendation request also asks to play now, play directly, or implies immediate playback, you must use PLAY_RECOMMENDATION.
                - Use COMPOSITE_REQUEST only when the user wants to search explicit tracks first and then control playback in the same request.
                - Never use COMPOSITE_REQUEST for recommendation generation.

                Canonical argument rules:
                - READ_CHAT_CONTEXT arguments must be exactly {"limit": 20}
                - READ_PLAYLIST_HISTORY arguments must be exactly {"limit": 10}
                - READ_USER_PREFERENCES arguments must be exactly {}
                - READ_LOCAL_SESSION arguments must be exactly {}
                - PERSIST_CHAT_REPLY arguments must be exactly {}
                - Every query-driven step must use a non-empty "query" copied from the latest user message or a minimal equivalent query in the same language

                Canonical PLAY_RECOMMENDATION example:
                {
                  "schemaVersion": "%s",
                  "intent": "PLAY_RECOMMENDATION",
                  "summary": "Build a recommendation playlist first, then start playback.",
                  "reasoning": "The user asked for recommendation plus immediate playback.",
                  "confidence": 90,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "<latestUserMessage>"}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "<latestUserMessage>"}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "<latestUserMessage>"}},
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "<latestUserMessage>"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }

                Self-check before you answer:
                - Confirm the chosen intent matches the request type.
                - Confirm the step count exactly matches the template.
                - Confirm READ_CHAT_CONTEXT includes limit 20.
                - Confirm every query step includes a non-empty query.
                - Confirm the output is valid JSON with no markdown.

                Allowed step templates:
                %s
                """.formatted(
                schemaVersion(),
                MAX_SUMMARY_LENGTH,
                MAX_REASONING_LENGTH,
                schemaVersion(),
                stepTemplates
        );
    }

    public String buildUserPrompt(PlanningContext context) {
        try {
            return objectMapper.writeValueAsString(buildRequest(context));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize LLM planning request.", exception);
        }
    }

    public AgentLlmPlanningResult parseAndValidate(String rawJson) {
        AgentLlmPlanningResponse response;
        try {
            response = objectMapper.readValue(rawJson, AgentLlmPlanningResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("LLM planning response is not valid JSON for the harness contract.", exception);
        }

        validateResponse(response);

        List<PlanStep> planSteps = new ArrayList<>();
        for (int index = 0; index < response.steps().size(); index++) {
            AgentLlmPlanningStep step = response.steps().get(index);
            planSteps.add(new PlanStep(index + 1, step.type(), step.arguments()));
        }

        return new AgentLlmPlanningResult(
                response,
                new AgentPlan(response.intent(), response.summary().trim(), List.copyOf(planSteps))
        );
    }

    public String schemaVersion() {
        return StringUtils.hasText(agentChatProperties.planningHarnessVersion())
                ? agentChatProperties.planningHarnessVersion().trim()
                : "agentmusic.plan.v1";
    }

    private void validateResponse(AgentLlmPlanningResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("LLM planning response is empty.");
        }
        if (!schemaVersion().equals(safeTrim(response.schemaVersion()))) {
            throw new IllegalArgumentException("LLM planning response schemaVersion does not match the active harness version.");
        }
        if (response.intent() == null) {
            throw new IllegalArgumentException("LLM planning response intent is missing.");
        }
        if (!StringUtils.hasText(response.summary()) || response.summary().trim().length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("LLM planning response summary is missing or exceeds the maximum length.");
        }
        if (!StringUtils.hasText(response.reasoning()) || response.reasoning().trim().length() > MAX_REASONING_LENGTH) {
            throw new IllegalArgumentException("LLM planning response reasoning is missing or exceeds the maximum length.");
        }
        if (response.confidence() < 0 || response.confidence() > 100) {
            throw new IllegalArgumentException("LLM planning response confidence must be between 0 and 100.");
        }
        if (response.steps() == null || response.steps().isEmpty()) {
            throw new IllegalArgumentException("LLM planning response steps are missing.");
        }

        List<PlanStepType> expectedSteps = STEP_TEMPLATES.get(response.intent());
        if (expectedSteps == null) {
            throw new IllegalArgumentException("LLM planning response uses an unsupported intent template.");
        }
        if (response.steps().size() != expectedSteps.size()) {
            throw new IllegalArgumentException("LLM planning response step count does not match the selected intent template.");
        }

        for (int index = 0; index < expectedSteps.size(); index++) {
            AgentLlmPlanningStep actualStep = response.steps().get(index);
            PlanStepType expectedStepType = expectedSteps.get(index);
            if (actualStep == null || actualStep.type() != expectedStepType) {
                throw new IllegalArgumentException("LLM planning response step sequence does not match the selected intent template.");
            }
            validateStepArguments(actualStep);
        }
    }

    private void validateStepArguments(AgentLlmPlanningStep step) {
        Map<String, Object> arguments = step.arguments() == null ? Map.of() : step.arguments();

        switch (step.type()) {
            case READ_CHAT_CONTEXT, READ_PLAYLIST_HISTORY -> validateLimitArguments(step.type(), arguments);
            case READ_USER_PREFERENCES, READ_LOCAL_SESSION, PERSIST_CHAT_REPLY -> {
                if (!arguments.isEmpty()) {
                    throw new IllegalArgumentException(step.type().name() + " must use an empty arguments object.");
                }
            }
            case LOOKUP_TRACK,
                    SEARCH_TRACKS,
                    LOOKUP_ARTIST,
                    GENERATE_RECOMMENDATION_CANDIDATES,
                    RANK_RECOMMENDATION_CANDIDATES,
                    CREATE_RECOMMENDATION_PLAYLIST,
                    UPDATE_PLAYBACK_STATE -> validateQueryArguments(step.type(), arguments);
        }
    }

    private void validateLimitArguments(PlanStepType stepType, Map<String, Object> arguments) {
        if (!arguments.keySet().equals(Set.of(LIMIT_ARGUMENT))) {
            throw new IllegalArgumentException(stepType.name() + " must only contain a numeric limit argument.");
        }

        Object limit = arguments.get(LIMIT_ARGUMENT);
        if (!(limit instanceof Number numberValue) || numberValue.intValue() <= 0) {
            throw new IllegalArgumentException(stepType.name() + " requires a positive numeric limit.");
        }
    }

    private void validateQueryArguments(PlanStepType stepType, Map<String, Object> arguments) {
        if (!arguments.keySet().equals(Set.of(QUERY_ARGUMENT))) {
            throw new IllegalArgumentException(stepType.name() + " must only contain a query argument.");
        }

        Object query = arguments.get(QUERY_ARGUMENT);
        if (!(query instanceof String queryText) || !StringUtils.hasText(queryText)) {
            throw new IllegalArgumentException(stepType.name() + " requires a non-empty query argument.");
        }
    }

    private String formatStepTypes(List<PlanStepType> stepTypes) {
        return stepTypes.stream().map(Enum::name).collect(Collectors.joining(" -> "));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<AgentIntent, List<PlanStepType>> createStepTemplates() {
        Map<AgentIntent, List<PlanStepType>> templates = new EnumMap<>(AgentIntent.class);
        templates.put(AgentIntent.CHAT_ONLY, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.TRACK_LOOKUP, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.LOOKUP_TRACK,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.ARTIST_LOOKUP, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.LOOKUP_ARTIST,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.RECOMMEND_PLAYLIST, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.READ_USER_PREFERENCES,
                PlanStepType.READ_PLAYLIST_HISTORY,
                PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES,
                PlanStepType.RANK_RECOMMENDATION_CANDIDATES,
                PlanStepType.CREATE_RECOMMENDATION_PLAYLIST,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.PLAY_RECOMMENDATION, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.READ_USER_PREFERENCES,
                PlanStepType.READ_PLAYLIST_HISTORY,
                PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES,
                PlanStepType.RANK_RECOMMENDATION_CANDIDATES,
                PlanStepType.CREATE_RECOMMENDATION_PLAYLIST,
                PlanStepType.UPDATE_PLAYBACK_STATE,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.PLAYLIST_HISTORY_ACCESS, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.READ_PLAYLIST_HISTORY,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.PLAYBACK_CONTROL, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.READ_LOCAL_SESSION,
                PlanStepType.UPDATE_PLAYBACK_STATE,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.COMPOSITE_REQUEST, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.SEARCH_TRACKS,
                PlanStepType.UPDATE_PLAYBACK_STATE,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        templates.put(AgentIntent.UNKNOWN, List.of(
                PlanStepType.READ_CHAT_CONTEXT,
                PlanStepType.PERSIST_CHAT_REPLY
        ));
        return Map.copyOf(templates);
    }
}
