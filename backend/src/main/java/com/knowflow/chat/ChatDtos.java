package com.knowflow.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record StreamRequest(Long conversationId, @NotBlank String question,
                                @NotEmpty List<Long> knowledgeBaseIds) {}
    public record CreateConversationRequest(String title) {}
    public record ConversationView(Long id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static ConversationView from(ConversationEntity e) {
            return new ConversationView(e.getId(), e.getTitle(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }
    public record CitationView(Long documentId, Long chunkId, Integer pageNumber,
                               Integer citationIndex, String excerpt, Double similarity) {}
    public record MessageView(Long id, String role, String content, String status,
                              OffsetDateTime createdAt, List<CitationView> citations) {}
}
