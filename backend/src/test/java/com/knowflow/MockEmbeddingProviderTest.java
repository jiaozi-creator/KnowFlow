package com.knowflow;

import com.knowflow.ai.MockEmbeddingProvider;
import com.knowflow.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockEmbeddingProviderTest {
    @Test
    void similarChineseTextsShareHigherCosineSimilarity() {
        var properties = new AppProperties.Ai("mock", "", "", "", "", 384);
        var provider = new MockEmbeddingProvider(properties);
        var vectors = provider.embed(List.of("报销超过5000元需要财务负责人审批", "5000元以上报销如何审批", "服务器部署操作手册"));
        assertEquals(384, vectors.getFirst().length);
        assertTrue(cosine(vectors.get(0), vectors.get(1)) > cosine(vectors.get(0), vectors.get(2)));
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            a += left[i] * left[i];
            b += right[i] * right[i];
        }
        return dot / Math.sqrt(a * b);
    }
}
