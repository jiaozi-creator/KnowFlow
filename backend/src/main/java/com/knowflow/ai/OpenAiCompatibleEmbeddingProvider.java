package com.knowflow.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowflow.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private final AppProperties.Ai properties;
    private final RestClient client;

    public OpenAiCompatibleEmbeddingProvider(AppProperties.Ai properties) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey()).build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Response response = client.post().uri("/embeddings")
                .body(new Request(properties.embeddingModel(), texts, properties.embeddingDimensions()))
                .retrieve().body(Response.class);
        if (response == null || response.data() == null) throw new IllegalStateException("Embedding API 返回为空");
        return response.data().stream().sorted(Comparator.comparingInt(Item::index))
                .map(item -> toArray(item.embedding())).toList();
    }

    @Override
    public int dimensions() { return properties.embeddingDimensions(); }

    private float[] toArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i).floatValue();
        return result;
    }

    private record Request(String model, List<String> input, Integer dimensions) {}
    private record Item(Integer index, List<Double> embedding) {}
    private record Response(List<Item> data) {}
}
