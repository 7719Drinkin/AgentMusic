package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class OpenAiLiveChatReplyService implements LiveChatReplyService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(60);
    private static final List<String> REFERENTIAL_CONTEXT_HINTS = List.of(
            "继续",
            "上次",
            "刚才",
            "刚刚",
            "之前",
            "前面",
            "这首",
            "这个",
            "那首",
            "那个",
            "同样",
            "类似",
            "再说",
            "more",
            "again",
            "continue",
            "previous",
            "same",
            "similar",
            "that"
    );
    private static final String PRIORITY_POLICY = """
            Always answer the latest user message directly.
            Treat previous conversation as background context only.
            Do not continue an older recommendation topic unless the latest user message asks for it.
            If the latest user message explicitly asks to repeat or revisit a previously mentioned song, artist, or playlist, honor that explicit request.
            """;

    private final OpenAiProperties openAiProperties;
    private final AgentChatProperties agentChatProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiLiveChatReplyService(
            OpenAiProperties openAiProperties,
            AgentChatProperties agentChatProperties,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.agentChatProperties = agentChatProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(resolveBaseUrl(openAiProperties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<String> generateReply(List<ChatMessageDto> recentMessages, String userMessage) {
        if (!isEnabled() || !StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        Map<String, Object> payload = buildChatCompletionPayload(recentMessages, userMessage, false);

        try {
            Map<?, ?> response = executeChatCompletion(payload);
            String content = extractMessageContent(response);
            return StringUtils.hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generateStreamingReply(
            List<ChatMessageDto> recentMessages,
            String userMessage,
            Consumer<String> deltaConsumer
    ) {
        if (!isEnabled() || !StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        Map<String, Object> payload = buildChatCompletionPayload(recentMessages, userMessage, true);

        try {
            String content = executeStreamingChatCompletion(
                    payload,
                    deltaConsumer == null ? ignored -> { } : deltaConsumer
            );
            return StringUtils.hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generateStreamingNarration(
            AgentPlan plan,
            PlanningContext planningContext,
            String executionResult,
            Consumer<String> deltaConsumer
    ) {
        if (!isEnabled() || !StringUtils.hasText(executionResult)) {
            return Optional.empty();
        }

        Map<String, Object> payload = buildNarrationPayload(plan, planningContext, executionResult, true);

        try {
            String content = executeStreamingChatCompletion(
                    payload,
                    deltaConsumer == null ? ignored -> { } : deltaConsumer
            );
            return StringUtils.hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isEnabled() {
        return agentChatProperties.liveLlmEnabled()
                && openAiProperties != null
                && StringUtils.hasText(openAiProperties.resolvedApiKey())
                && StringUtils.hasText(openAiProperties.baseUrl())
                && openAiProperties.chat() != null
                && StringUtils.hasText(openAiProperties.chat().modelId());
    }

    @Override
    public AgentRuntimeStatusDto getRuntimeStatus() {
        return new AgentRuntimeStatusDto(
                agentChatProperties.liveLlmEnabled(),
                openAiProperties != null && StringUtils.hasText(openAiProperties.resolvedApiKey()),
                openAiProperties != null && openAiProperties.chat() != null
                        ? openAiProperties.chat().modelId()
                        : null,
                isEnabled()
        );
    }

    private Map<String, Object> buildChatCompletionPayload(
            List<ChatMessageDto> recentMessages,
            String userMessage,
            boolean stream
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", PRIORITY_POLICY
        ));
        messages.add(Map.of(
                "role", "system",
                "content", agentChatProperties.systemPrompt()
        ));

        if (shouldIncludeRecentMessages(userMessage)) {
            recentMessages.stream()
                    .filter(message -> message != null && StringUtils.hasText(message.message()))
                    .sorted(Comparator.comparing(ChatMessageDto::createdAt).reversed())
                    .limit(Math.max(1, agentChatProperties.llmRecentMessageLimit()))
                    .sorted(Comparator.comparing(ChatMessageDto::createdAt))
                    .forEach(message -> messages.add(Map.of(
                            "role", toOpenAiRole(message),
                            "content", message.message().trim()
                    )));
        } else {
            messages.add(Map.of(
                    "role", "system",
                    "content", "No prior conversation is included because the latest user message is standalone."
            ));
        }

        messages.add(Map.of(
                "role", "system",
                "content", "Answer only the next latest user message. Do not borrow topics, songs, artists, or intent from older context unless the latest message explicitly asks to continue or revisit them."
        ));

        messages.add(Map.of(
                "role", "user",
                "content", userMessage.trim()
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("messages", messages);
        payload.put("temperature", agentChatProperties.llmTemperature());
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private Map<String, Object> buildNarrationPayload(
            AgentPlan plan,
            PlanningContext planningContext,
            String executionResult,
            boolean stream
    ) {
        String latestUserMessage = planningContext == null || planningContext.request() == null
                ? ""
                : planningContext.request().message();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", """
                        You are AgentMusic's final response narrator.
                        Convert backend tool execution results into a concise user-facing answer.
                        The execution result is the source of truth for app actions, Spotify matches, playlists, tracks, and playback state.
                        For artist introduction or explanation requests, you may add brief general music knowledge, but do not fabricate exact stats, search results, playlists, tracks, albums, or playback actions not present in the execution result.
                        If the execution result says nothing was found, unavailable, pending, or failed, say that transparently and suggest one practical next query.
                        Do not expose internal implementation phrases such as intent, steps, planner, skeleton, bridge-mode wiring, metadata, JSON, or source names.
                        Use Chinese when the latest user message is Chinese; otherwise use the user's language.
                        """
        ));

        messages.add(Map.of(
                "role", "user",
                "content", buildNarrationUserContent(plan, latestUserMessage, executionResult)
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("messages", messages);
        payload.put("temperature", agentChatProperties.llmTemperature());
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private String buildNarrationUserContent(AgentPlan plan, String latestUserMessage, String executionResult) {
        StringBuilder builder = new StringBuilder();
        builder.append("latestUserMessage:\n")
                .append(StringUtils.hasText(latestUserMessage) ? latestUserMessage.trim() : "(empty)")
                .append("\n\nexecutionResult:\n")
                .append(executionResult.trim());

        if (plan != null) {
            builder.append("\n\nplanIntent:\n")
                    .append(plan.intent() == null ? "UNKNOWN" : plan.intent().name());
            if (StringUtils.hasText(plan.summary())) {
                builder.append("\n\nplanSummary:\n")
                        .append(plan.summary().trim());
            }
            String stepSummary = summarizePlanSteps(plan);
            if (StringUtils.hasText(stepSummary)) {
                builder.append("\n\nexecutedStepSummary:\n")
                        .append(stepSummary);
            }
        }

        return builder.toString();
    }

    private String summarizePlanSteps(AgentPlan plan) {
        if (plan.steps() == null || plan.steps().isEmpty()) {
            return "";
        }

        return plan.steps().stream()
                .filter(step -> step != null && step.type() != null)
                .map(this::summarizePlanStep)
                .reduce((left, right) -> left + " -> " + right)
                .orElse("");
    }

    private String summarizePlanStep(PlanStep step) {
        return step.arguments() == null || step.arguments().isEmpty()
                ? step.type().name()
                : step.type().name() + step.arguments();
    }

    private boolean shouldIncludeRecentMessages(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }

        String normalized = userMessage.toLowerCase(Locale.ROOT);
        return REFERENTIAL_CONTEXT_HINTS.stream().anyMatch(normalized::contains);
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }

        Object choices = response.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) {
            return "";
        }

        Object firstChoice = choiceList.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return "";
        }

        Object message = choiceMap.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return "";
        }

        Object content = messageMap.get("content");
        return content instanceof String contentText ? contentText : "";
    }

    private String resolveBaseUrl(OpenAiProperties properties) {
        if (properties != null && StringUtils.hasText(properties.baseUrl())) {
            return properties.baseUrl().trim();
        }
        return "https://api.openai.com/v1";
    }

    private String toOpenAiRole(ChatMessageDto message) {
        return switch (message.role()) {
            case USER -> "user";
            case AGENT -> "assistant";
        };
    }

    private Map<?, ?> executeChatCompletion(Map<String, Object> payload) {
        int maxRetries = Math.max(0, agentChatProperties.llmHttpMaxRetries());
        long retryBackoffMs = Math.max(0L, agentChatProperties.llmHttpRetryBackoffMs());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return webClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolvedApiKey())
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(REQUEST_TIMEOUT);
            } catch (WebClientResponseException exception) {
                if (!isRetryable(exception) || attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            } catch (WebClientRequestException exception) {
                if (attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            }
        }

        throw new IllegalStateException("Live chat reply request failed without a terminal result.");
    }

    private String executeStreamingChatCompletion(Map<String, Object> payload, Consumer<String> deltaConsumer) {
        int maxRetries = Math.max(0, agentChatProperties.llmHttpMaxRetries());
        long retryBackoffMs = Math.max(0L, agentChatProperties.llmHttpRetryBackoffMs());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            StringBuilder reply = new StringBuilder();
            try {
                streamChatCompletion(payload, reply, deltaConsumer);
                return reply.toString();
            } catch (WebClientResponseException exception) {
                if (reply.length() > 0 || !isRetryable(exception) || attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            } catch (WebClientRequestException exception) {
                if (reply.length() > 0 || attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            }
        }

        throw new IllegalStateException("Streaming live chat reply request failed without a terminal result.");
    }

    private void streamChatCompletion(
            Map<String, Object> payload,
            StringBuilder reply,
            Consumer<String> deltaConsumer
    ) {
        webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolvedApiKey())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .timeout(STREAM_IDLE_TIMEOUT)
                .toIterable()
                .forEach(event -> consumeStreamingPayload(event.data(), reply, deltaConsumer));
    }

    private void consumeStreamingPayload(
            String payload,
            StringBuilder reply,
            Consumer<String> deltaConsumer
    ) {
        if (!StringUtils.hasText(payload)) {
            return;
        }

        String trimmedPayload = payload.trim();
        if ("[DONE]".equals(trimmedPayload)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(trimmedPayload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray()) {
                return;
            }

            choices.forEach(choice -> {
                String delta = extractStreamingDelta(choice);
                if (!delta.isEmpty()) {
                    reply.append(delta);
                    deltaConsumer.accept(delta);
                }
            });
        } catch (JsonProcessingException ignored) {
            // Ignore malformed stream keepalive chunks from OpenAI-compatible providers.
        }
    }

    private String extractStreamingDelta(JsonNode choice) {
        JsonNode content = choice.path("delta").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return "";
        }
        return content.asText("");
    }

    private boolean isRetryable(WebClientResponseException exception) {
        return exception instanceof WebClientResponseException.TooManyRequests
                || exception.getStatusCode().is5xxServerError();
    }

    private void sleepBeforeRetry(long retryBackoffMs, int multiplier) {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * multiplier);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Live chat reply retry was interrupted.", exception);
        }
    }
}
