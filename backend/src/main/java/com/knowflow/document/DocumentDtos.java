package com.knowflow.document;

import java.time.OffsetDateTime;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record View(
            Long id,
            Long knowledgeBaseId,
            String name,
            String fileType,
            String status,
            Long currentVersionId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static View from(DocumentEntity e) {
            return new View(
                    e.getId(),
                    e.getKnowledgeBaseId(),
                    e.getName(),
                    e.getFileType(),
                    e.getStatus(),
                    e.getCurrentVersionId(),
                    e.getCreatedAt(),
                    e.getUpdatedAt()
            );
        }
    }

    public record UploadResponse(
            View document,
            Long versionId,
            Long taskId
    ) {
    }

    public record TaskView(
            Long id,
            String status,
            Integer progress,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        static TaskView from(IngestionTaskEntity e) {
            return new TaskView(
                    e.getId(),
                    e.getStatus(),
                    e.getProgress(),
                    e.getErrorMessage(),
                    e.getStartedAt(),
                    e.getFinishedAt()
            );
        }
    }

    public record IndexStatus(
            String currentSignature,
            Integer total,
            Integer ready,
            Integer needsReindex,
            Integer processing,
            Integer failed,
            Integer repairable
    ) {
    }

    public record BatchReindexResponse(
            String currentSignature,
            Integer queuedCount,
            List<Long> documentIds,
            List<Long> taskIds
    ) {
    }

    public record Content(
            String filename,
            String contentType,
            java.io.InputStream inputStream
    ) {
    }
}