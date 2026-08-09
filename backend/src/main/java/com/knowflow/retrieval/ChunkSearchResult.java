package com.knowflow.retrieval;

public record ChunkSearchResult(Long chunkId, Long documentId, String documentName,
                                Integer pageNumber, String heading, String content, Double similarity) {}
