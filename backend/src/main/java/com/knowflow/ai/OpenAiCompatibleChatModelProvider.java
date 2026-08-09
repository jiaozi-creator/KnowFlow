package com.knowflow.ai;

import com.knowflow.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.List;

public class OpenAiCompatibleChatModelProvider implements ChatModelProvider {
    private final AppProperties.Ai properties;
    private final RestClient client;

    public OpenAiCompatibleChatModelProvider(AppProperties.Ai properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey()).build();
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        Request request = new Request(properties.chatModel(), List.of(
                new Message("system", systemPrompt), new Message("user", userPrompt)), 0.2, false);
        Response response = client.post().uri("/chat/completions").body(request).retrieve().body(Response.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Chat API 返回为空");
        }
        return response.choices().getFirst().message().content();
    }

    private record Message(String role, String content) {}
    private record Request(String model, List<Message> messages, double temperature, boolean stream) {}
    private record Choice(Message message) {}
    private record Response(List<Choice> choices) {}
}
