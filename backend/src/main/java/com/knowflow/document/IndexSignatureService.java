package com.knowflow.document;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class IndexSignatureService {

    private final Environment environment;

    public IndexSignatureService(Environment environment) {
        this.environment = environment;
    }

    public String current() {
        String parserVersion = value(
                "KNOWFLOW_INDEX_PARSER_VERSION",
                "knowflow.index.parser-version",
                "parser-v2"
        );

        String chunkerVersion = value(
                "KNOWFLOW_INDEX_CHUNKER_VERSION",
                "knowflow.index.chunker-version",
                "chunker-v1"
        );

        String embeddingModel = value(
                "AI_EMBEDDING_MODEL",
                "knowflow.ai.embedding-model",
                "text-embedding-v4"
        );

        String embeddingDimensions = value(
                "AI_EMBEDDING_DIMENSIONS",
                "knowflow.ai.embedding-dimensions",
                "1024"
        );

        return clean(parserVersion)
                + "|"
                + clean(chunkerVersion)
                + "|"
                + clean(embeddingModel)
                + "|"
                + clean(embeddingDimensions);
    }

    public boolean isCurrent(String signature) {
        return signature != null && current().equals(signature);
    }

    private String value(String envName, String propertyName, String defaultValue) {
        String value = System.getenv(envName);

        if (value == null || value.isBlank()) {
            value = environment.getProperty(propertyName);
        }

        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private String clean(String value) {
        return value.replace('|', '_').trim();
    }
}