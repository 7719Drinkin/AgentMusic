package com.agentmusic.agentmusic_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        Chat chat
) {

    public record Chat(String modelId) {
    }
}

