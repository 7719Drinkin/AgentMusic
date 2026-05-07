package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class OpenAiLiveChatReplyService implements LiveChatReplyService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final String PRIORITY_POLICY = """
            Always answer the latest user message directly.
            Treat previous conversation as background context only.
            Do not continue an older recommendation topic unless the latest user message asks for it.
            If the latest user message explicitly asks to repeat or revisit a previously mentioned song, artist, or playlist, honor that explicit request.
            """;

    private final OpenAiProperties openAiProperties;
    private final AgentChatProperties agentChatProperties;
    private final WebClient webClient;

    public OpenAiLiveChatReplyService(
            OpenAiProperties openAiProperties,
            AgentChatProperties agentChatProperties
    ) {
        this.openAiProperties = openAiProperties;
        this.agentChatProperties = agentChatProperties;
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

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", PRIORITY_POLICY
        ));
        messages.add(Map.of(
                "role", "system",
                "content", agentChatProperties.systemPrompt()
        ));

        recentMessages.stream()
                .filter(message -> message != null && StringUtils.hasText(message.message()))
                .sorted(Comparator.comparing(ChatMessageDto::createdAt).reversed())
                .limit(Math.max(1, agentChatProperties.llmRecentMessageLimit()))
                .sorted(Comparator.comparing(ChatMessageDto::createdAt))
                .forEach(message -> messages.add(Map.of(
                        "role", toOpenAiRole(message),
                        "content", message.message().trim()
                )));

        messages.add(Map.of(
                "role", "user",
                "content", userMessage.trim()
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("messages", messages);
        payload.put("temperature", agentChatProperties.llmTemperature());

        try {
            Map<?, ?> response = executeChatCompletion(payload);
            String content = extractMessageContent(response);
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
