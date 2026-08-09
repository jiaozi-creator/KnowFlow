package com.knowflow.config;

import com.knowflow.ai.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {
    @Bean
    EmbeddingProvider embeddingProvider(AppProperties.Ai properties) {
        if ("openai-compatible".equalsIgnoreCase(properties.provider())) {
            return new OpenAiCompatibleEmbeddingProvider(properties);
        }
        return new MockEmbeddingProvider(properties);
    }

    @Bean
    ChatModelProvider chatModelProvider(AppProperties.Ai properties) {
        if ("openai-compatible".equalsIgnoreCase(properties.provider())) {
            return new OpenAiCompatibleChatModelProvider(properties);
        }
        return new MockChatModelProvider();
    }
}
