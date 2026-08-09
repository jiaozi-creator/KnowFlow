package com.knowflow.ai;

import java.util.List;

public interface EmbeddingProvider {
    List<float[]> embed(List<String> texts);
    int dimensions();
}
