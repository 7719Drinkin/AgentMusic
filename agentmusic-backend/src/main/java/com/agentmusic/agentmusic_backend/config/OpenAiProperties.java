package com.agentmusic.agentmusic_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        Chat chat,
        Api api
) {

    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        if (api != null && api.key() != null && !api.key().isBlank()) {
            return api.key();
        }
        return null;
    }

    public record Chat(String modelId) {
    }

    public record Api(String key) {
    }
}

