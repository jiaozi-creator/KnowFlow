package com.knowflow.ai;

import com.knowflow.config.AppProperties;

import java.util.List;

public class MockEmbeddingProvider implements EmbeddingProvider {
    private final int dimensions;

    public MockEmbeddingProvider(AppProperties.Ai properties) { this.dimensions = properties.embeddingDimensions(); }

    @Override
    public List<float[]> embed(List<String> texts) { return texts.stream().map(this::vector).toList(); }

    @Override
    public int dimensions() { return dimensions; }

    private float[] vector(String text) {
        float[] result = new float[dimensions];
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", "");
        for (int n = 1; n <= 3; n++) {
            for (int i = 0; i + n <= normalized.length(); i++) {
                String token = normalized.substring(i, i + n);
                int hash = token.hashCode() * 31 + n;
                int index = Math.floorMod(hash, dimensions);
                float sign = ((hash >>> 1) & 1) == 0 ? 1f : -1f;
                result[index] += sign / n;
            }
        }
        normalize(result);
        return result;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += value * value;
        double norm = Math.sqrt(sum);
        if (norm == 0) return;
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
    }
}
