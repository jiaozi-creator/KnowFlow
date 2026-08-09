package com.knowflow.document;

import java.io.Serializable;

public record DocumentIngestionMessage(Long tenantId, Long knowledgeBaseId, Long documentId,
                                       Long versionId, Long taskId, String objectKey,
                                       String originalFilename, String contentType) implements Serializable {}
