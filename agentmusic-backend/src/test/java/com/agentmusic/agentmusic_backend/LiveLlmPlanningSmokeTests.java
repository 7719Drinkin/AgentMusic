package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningHarness;
import com.agentmusic.agentmusic_backend.planner.llm.OpenAiCompatiblePlanningClient;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

class LiveLlmPlanningSmokeTests {

    @Test
    void kimiCompatibleEndpointReturnsRecommendationPlanThatPassesHarness() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("agentmusic.liveLlmSmoke"));

        Properties properties = loadEffectiveProperties();
        OpenAiProperties openAiProperties = buildOpenAiProperties(properties);
        AgentChatProperties agentChatProperties = buildAgentChatProperties(properties);

        Assumptions.assumeTrue(StringUtils.hasText(openAiProperties.resolvedApiKey()));
        Assumptions.assumeTrue(StringUtils.hasText(openAiProperties.baseUrl()));
        Assumptions.assumeTrue(StringUtils.hasText(openAiProperties.chat().modelId()));

        AgentLlmPlanningHarness harness = new AgentLlmPlanningHarness(agentChatProperties, new ObjectMapper());
        OpenAiCompatiblePlanningClient planningClient = new OpenAiCompatiblePlanningClient(
                openAiProperties,
                agentChatProperties,
                harness
        );

        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", "来点适合雨天通勤的中文歌并直接播放", false),
                List.of("上一轮生成的是粤语歌单", "当前在通勤场景")
        );

        var result = planningClient.generateValidatedPlan(context);

        assertNotNull(result);
        assertNotNull(result.response());
        assertNotNull(result.plan());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    private Properties loadEffectiveProperties() throws IOException {
        Properties properties = new Properties();
        loadFromPath(properties, Path.of("src", "main", "resources", "application.properties"));
        loadFromPath(properties, Path.of("application-local.properties"));
        return properties;
    }

    private void loadFromPath(Properties properties, Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            Properties override = new Properties();
            override.load(inputStream);
            properties.putAll(override);
        }
    }

    private OpenAiProperties buildOpenAiProperties(Properties properties) {
        return new OpenAiProperties(
                resolveValue(properties.getProperty("openai.api.key")),
                resolveValue(properties.getProperty("openai.base-url")),
                new OpenAiProperties.Chat(resolveValue(properties.getProperty("openai.chat.model-id")))
        );
    }

    private AgentChatProperties buildAgentChatProperties(Properties properties) {
        return new AgentChatProperties(
                Boolean.parseBoolean(resolveValue(properties.getProperty("agent.chat.live-llm-enabled"))),
                resolveValue(properties.getProperty("agent.chat.system-prompt")),
                resolveValue(properties.getProperty("agent.chat.planning-harness-version")),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-recent-message-limit"))),
                Double.parseDouble(resolveValue(properties.getProperty("agent.chat.llm-temperature"))),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-planning-max-repair-attempts"))),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-http-max-retries"))),
                Long.parseLong(resolveValue(properties.getProperty("agent.chat.llm-http-retry-backoff-ms")))
        );
    }

    private String resolveValue(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("${") || !trimmed.endsWith("}")) {
            return trimmed;
        }

        String expressionBody = trimmed.substring(2, trimmed.length() - 1);
        int separatorIndex = expressionBody.indexOf(':');
        String environmentKey = separatorIndex >= 0 ? expressionBody.substring(0, separatorIndex) : expressionBody;
        String fallback = separatorIndex >= 0 ? expressionBody.substring(separatorIndex + 1) : "";
        String environmentValue = System.getenv(environmentKey);
        return StringUtils.hasText(environmentValue) ? environmentValue.trim() : fallback.trim();
    }
}
