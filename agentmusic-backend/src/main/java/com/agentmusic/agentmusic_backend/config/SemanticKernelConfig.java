package com.agentmusic.agentmusic_backend.config;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.KeyCredential;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, AgentChatProperties.class})
public class SemanticKernelConfig {

    @Bean
    public Kernel kernel(OpenAiProperties openAiProperties) {
        if (openAiProperties == null || !StringUtils.hasText(openAiProperties.apiKey())) {
            return Kernel.builder().build();
        }

        OpenAIAsyncClient client = new OpenAIClientBuilder()
                .credential(new KeyCredential(openAiProperties.apiKey()))
                .endpoint("https://api.openai.com")
                .buildAsyncClient();

        OpenAIChatCompletion chatCompletion = OpenAIChatCompletion.builder()
                .withModelId(openAiProperties.chat().modelId())
                .withOpenAIAsyncClient(client)
                .build();

        return Kernel.builder()
                .withAIService(OpenAIChatCompletion.class, chatCompletion)
                .build();
    }
}
