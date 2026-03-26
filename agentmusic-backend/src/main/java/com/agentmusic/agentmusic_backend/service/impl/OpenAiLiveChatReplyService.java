package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OpenAiLiveChatReplyService implements LiveChatReplyService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

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
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<String> generateReply(List<String> recentMessages, String userMessage) {
        if (!isEnabled() || !StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", agentChatProperties.systemPrompt()
        ));

        recentMessages.stream()
                .filter(StringUtils::hasText)
                .limit(6)
                .forEach(message -> messages.add(Map.of(
                        "role", "user",
                        "content", message
                )));

        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.chat().modelId());
        payload.put("messages", messages);
        payload.put("temperature", 0.7);

        try {
            Map<?, ?> response = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.apiKey())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(REQUEST_TIMEOUT);

            String content = extractMessageContent(response);
            return StringUtils.hasText(content) ? Optional.of(content.trim()) : Optional.empty();
        } catch (Exception exception) {
            return Optional.of("实时 LLM 回复调用失败，已回退到本地占位逻辑。");
        }
    }

    @Override
    public boolean isEnabled() {
        return agentChatProperties.liveLlmEnabled()
                && openAiProperties != null
                && StringUtils.hasText(openAiProperties.apiKey())
                && openAiProperties.chat() != null
                && StringUtils.hasText(openAiProperties.chat().modelId());
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
}
