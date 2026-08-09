package com.knowflow.admin;

public record SystemCheckView(
        Long orphanChunks,
        Long nonCurrentChunks,
        Long readyDocumentsWithoutChunks,
        Long activeIngestionTasks,
        Long failedDocuments,
        Long needsReindexDocuments
) {
}
