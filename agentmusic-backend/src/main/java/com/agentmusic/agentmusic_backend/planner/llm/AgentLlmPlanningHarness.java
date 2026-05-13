package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentLlmPlanningHarness {

    private static final int MAX_SUMMARY_LENGTH = 140;
    private static final int MAX_REASONING_LENGTH = 180;
    private static final String QUERY_ARGUMENT = "query";
    private static final String LIMIT_ARGUMENT = "limit";
    private static final Pattern TITLE_PATTERN = Pattern.compile("《\\s*([^》]+?)\\s*》");
    private static final List<String> RECOMMENDATION_HINTS = List.of("推荐", "来点", "想听", "适合", "歌单", "mix");
    private static final List<String> NO_PLAY_HINTS = List.of(
            "不要播放",
            "先不要播放",
            "先别播",
            "稍后播放",
            "recommend only",
            "do not play",
            "don't play",
            "no autoplay",
            "without playback"
    );

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
        List<AgentLlmPlanningMessage> recentConversation = context.recentConversation() == null
                ? List.of()
                : context.recentConversation().stream()
                .filter(message -> message != null && StringUtils.hasText(message.message()))
                .sorted(java.util.Comparator.comparing(ChatMessageDto::createdAt))
                .limit(Math.max(1, agentChatProperties.llmRecentMessageLimit()))
                .map(message -> new AgentLlmPlanningMessage(
                        toPlanningRole(message),
                        message.message().trim()
                ))
                .toList();
        List<String> recentRecommendationSummaries = context.recentRecommendationSummaries() == null
                ? List.of()
                : context.recentRecommendationSummaries().stream()
                .filter(StringUtils::hasText)
                .limit(3)
                .map(String::trim)
                .toList();

        return new AgentLlmPlanningRequest(
                schemaVersion(),
                context.request().userId(),
                context.request().voiceInput(),
                safeTrim(context.request().message()),
                recentConversation,
                recentRecommendationSummaries,
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
                10. latestUserMessage is the primary source of truth. recentConversation and recentRecommendationSummaries are background memory only.
                11. Never let background memory override an explicit current request.
                12. If latestUserMessage explicitly asks to repeat, revisit, or keep a previously recommended track / artist / playlist, you must preserve that request in the query.
                13. If latestUserMessage asks for a different topic than history, do not continue the older topic.

                Intent selection rules:
                - If the user asks for recommended songs, a generated playlist, or music that fits a mood / scene / genre, default to PLAY_RECOMMENDATION.
                - Use RECOMMEND_PLAYLIST only when latestUserMessage explicitly says not to play, such as "不要播放", "先别播", "稍后播放", or "recommend only".
                - Use COMPOSITE_REQUEST only when the user wants to search explicit tracks first and then control playback in the same request.
                - Never use COMPOSITE_REQUEST for recommendation generation.

                Canonical argument rules:
                - READ_CHAT_CONTEXT arguments must be exactly {"limit": 20}
                - READ_PLAYLIST_HISTORY arguments must be exactly {"limit": 10}
                - READ_USER_PREFERENCES arguments must be exactly {}
                - READ_LOCAL_SESSION arguments must be exactly {}
                - PERSIST_CHAT_REPLY arguments must be exactly {}
                - Every query-driven step must use a non-empty "query" copied from latestUserMessage or a minimal equivalent query in the same language
                - Never copy literal topic words from examples or old history when latestUserMessage asks for something else
                - Query construction priority is: latestUserMessage first, then user preferences, then recent recommendation history as a tie-breaker to avoid accidental repetition

                Canonical PLAY_RECOMMENDATION example:
                {
                  "schemaVersion": "%s",
                  "intent": "PLAY_RECOMMENDATION",
                  "summary": "Build a recommendation playlist first, then start playback.",
                  "reasoning": "Recommendation requests default to immediate playback unless the user says not to play.",
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
        return parseAndValidate(rawJson, null);
    }

    public AgentLlmPlanningResult parseAndValidate(String rawJson, PlanningContext context) {
        AgentLlmPlanningResponse response;
        try {
            response = objectMapper.readValue(rawJson, AgentLlmPlanningResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("LLM planning response is not valid JSON for the harness contract.", exception);
        }

        validateResponse(response, context);

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

    private void validateResponse(AgentLlmPlanningResponse response, PlanningContext context) {
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

        validateIntentAgainstLatestMessage(response.intent(), context);

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
            validateStepArguments(actualStep, context);
        }
    }

    private void validateStepArguments(AgentLlmPlanningStep step, PlanningContext context) {
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
                    UPDATE_PLAYBACK_STATE -> validateQueryArguments(step.type(), arguments, context);
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

    private void validateQueryArguments(PlanStepType stepType, Map<String, Object> arguments, PlanningContext context) {
        if (!arguments.keySet().equals(Set.of(QUERY_ARGUMENT))) {
            throw new IllegalArgumentException(stepType.name() + " must only contain a query argument.");
        }

        Object query = arguments.get(QUERY_ARGUMENT);
        if (!(query instanceof String queryText) || !StringUtils.hasText(queryText)) {
            throw new IllegalArgumentException(stepType.name() + " requires a non-empty query argument.");
        }

        List<String> explicitTitles = extractExplicitTitles(context);
        if (!explicitTitles.isEmpty()) {
            String normalizedQuery = normalizeForMatching(queryText);
            for (String explicitTitle : explicitTitles) {
                if (!normalizedQuery.contains(normalizeForMatching(explicitTitle))) {
                    throw new IllegalArgumentException(
                            stepType.name() + " query must preserve explicit titles from latestUserMessage."
                    );
                }
            }
        }
    }

    private String formatStepTypes(List<PlanStepType> stepTypes) {
        return stepTypes.stream().map(Enum::name).collect(Collectors.joining(" -> "));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateIntentAgainstLatestMessage(AgentIntent intent, PlanningContext context) {
        String latestMessage = context == null || context.request() == null
                ? ""
                : safeTrim(context.request().message()).toLowerCase(java.util.Locale.ROOT);
        if (!StringUtils.hasText(latestMessage)) {
            return;
        }

        boolean recommendationRequest = containsAny(latestMessage, RECOMMENDATION_HINTS);
        boolean noPlayRequest = containsAny(latestMessage, NO_PLAY_HINTS);

        if (recommendationRequest && (intent == AgentIntent.TRACK_LOOKUP || intent == AgentIntent.ARTIST_LOOKUP)) {
            throw new IllegalArgumentException("Recommendation requests must not be classified as direct lookup intents.");
        }
        if (recommendationRequest && (intent == AgentIntent.UNKNOWN || intent == AgentIntent.CHAT_ONLY)) {
            throw new IllegalArgumentException("Recommendation requests must not be classified as UNKNOWN or CHAT_ONLY.");
        }
        if (recommendationRequest && !noPlayRequest && intent != AgentIntent.PLAY_RECOMMENDATION) {
            throw new IllegalArgumentException("Recommendation requests default to PLAY_RECOMMENDATION unless latestUserMessage explicitly says not to play.");
        }
        if (recommendationRequest && noPlayRequest && intent == AgentIntent.PLAY_RECOMMENDATION) {
            throw new IllegalArgumentException("Recommendation requests that explicitly say not to play must not use PLAY_RECOMMENDATION.");
        }
    }

    private List<String> extractExplicitTitles(PlanningContext context) {
        if (context == null || context.request() == null || !StringUtils.hasText(context.request().message())) {
            return List.of();
        }

        Matcher matcher = TITLE_PATTERN.matcher(context.request().message());
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            String title = safeTrim(matcher.group(1));
            if (StringUtils.hasText(title)) {
                titles.add(title);
            }
        }
        return List.copyOf(titles);
    }

    private String normalizeForMatching(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }

    private boolean containsAny(String value, List<String> fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String toPlanningRole(ChatMessageDto message) {
        return switch (message.role()) {
            case USER -> "user";
            case AGENT -> "assistant";
        };
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
