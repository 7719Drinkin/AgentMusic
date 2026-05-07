package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class OpenAiCompatiblePlanningClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final OpenAiProperties openAiProperties;
    private final AgentChatProperties agentChatProperties;
    private final AgentLlmPlanningHarness planningHarness;
    private final WebClient webClient;

    public OpenAiCompatiblePlanningClient(
            OpenAiProperties openAiProperties,
            AgentChatProperties agentChatProperties,
            AgentLlmPlanningHarness planningHarness
    ) {
        this.openAiProperties = openAiProperties;
        this.agentChatProperties = agentChatProperties;
        this.planningHarness = planningHarness;
        this.webClient = WebClient.builder()
                .baseUrl(resolveBaseUrl(openAiProperties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AgentLlmPlanningResult generateValidatedPlan(PlanningContext context) {
        if (!isEnabled()) {
            throw new IllegalStateException("LLM planning client is not enabled.");
        }

        List<Map<String, String>> messages = createInitialMessages(context);
        String latestRawContent = "";
        IllegalArgumentException latestValidationException = null;
        int maxRepairAttempts = Math.max(0, agentChatProperties.llmPlanningMaxRepairAttempts());

        for (int attempt = 0; attempt <= maxRepairAttempts; attempt++) {
            Map<?, ?> response = executeChatCompletionWithFallback(messages);
            String content = extractMessageContent(response);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("LLM planning response content is empty.");
            }

            latestRawContent = content.trim();
            try {
                return planningHarness.parseAndValidate(latestRawContent);
            } catch (IllegalArgumentException exception) {
                latestValidationException = exception;
                if (attempt >= maxRepairAttempts) {
                    break;
                }
                messages = createRepairMessages(context, latestRawContent, exception.getMessage());
            }
        }

        throw new IllegalStateException(
                "LLM planning response failed harness validation. Raw content: " + abbreviate(latestRawContent),
                latestValidationException
        );
    }

    public boolean isEnabled() {
        return StringUtils.hasText(openAiProperties.resolvedApiKey())
                && StringUtils.hasText(openAiProperties.baseUrl())
                && openAiProperties.chat() != null
                && StringUtils.hasText(openAiProperties.chat().modelId());
    }

    private List<Map<String, String>> createInitialMessages(PlanningContext context) {
        return List.of(
                Map.of("role", "system", "content", planningHarness.buildSystemPrompt()),
                Map.of("role", "user", "content", planningHarness.buildUserPrompt(context))
        );
    }

    private List<Map<String, String>> createRepairMessages(
            PlanningContext context,
            String invalidRawContent,
            String validationError
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", planningHarness.buildSystemPrompt()));
        messages.add(Map.of("role", "user", "content", planningHarness.buildUserPrompt(context)));
        messages.add(Map.of("role", "assistant", "content", invalidRawContent));
        messages.add(Map.of(
                "role", "user",
                "content", """
                        The previous JSON failed harness validation.
                        Fix the JSON and return one corrected JSON object only.
                        Do not change the intent unless the validation error proves the intent template is wrong.
                        Validation error: %s
                        """.formatted(validationError)
        ));
        return List.copyOf(messages);
    }

    private Map<String, Object> createPayload(List<Map<String, String>> messages, boolean includeJsonResponseFormat) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("temperature", agentChatProperties.llmTemperature());
        payload.put("messages", messages);
        if (includeJsonResponseFormat) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        return payload;
    }

    private Map<?, ?> executeChatCompletionWithFallback(List<Map<String, String>> messages) {
        try {
            return executeChatCompletion(createPayload(messages, true));
        } catch (WebClientResponseException.BadRequest exception) {
            return executeChatCompletion(createPayload(messages, false));
        }
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
            } catch (WebClientResponseException.TooManyRequests exception) {
                if (attempt >= maxRetries) {
                    throw exception;
                }
                sleepBeforeRetry(retryBackoffMs, attempt + 1);
            }
        }

        throw new IllegalStateException("LLM planning request failed without a terminal result.");
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

    private String abbreviate(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 600) {
            return normalized;
        }
        return normalized.substring(0, 600) + "...";
    }

    private void sleepBeforeRetry(long retryBackoffMs, int multiplier) {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * multiplier);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM planning retry was interrupted.", exception);
        }
    }
}
