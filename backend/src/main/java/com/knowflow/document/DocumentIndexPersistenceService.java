package com.knowflow.document;

import com.knowflow.retrieval.DocumentChunkMapper;
import com.knowflow.retrieval.VectorUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class DocumentIndexPersistenceService {

    public record PreparedChunk(
            TextChunker.Chunk chunk,
            float[] vector,
            int tokenCount
    ) {
    }

    private final DocumentChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper versionMapper;
    private final IngestionTaskMapper taskMapper;
    private final IndexSignatureService signatureService;

    public DocumentIndexPersistenceService(
            DocumentChunkMapper chunkMapper,
            DocumentMapper documentMapper,
            DocumentVersionMapper versionMapper,
            IngestionTaskMapper taskMapper,
            IndexSignatureService signatureService
    ) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.signatureService = signatureService;
    }

    /**
     * 原子替换索引。
     *
     * delete old chunks + insert new chunks + READY 状态
     * 在同一个数据库事务中完成。
     */
    @Transactional
    public void commit(
            DocumentIngestionMessage message,
            List<PreparedChunk> prepared
    ) {
        if (prepared == null || prepared.isEmpty()) {
            throw new IllegalArgumentException("索引结果为空");
        }

        chunkMapper.deleteByVersion(message.versionId());

        for (PreparedChunk preparedChunk : prepared) {
            TextChunker.Chunk chunk = preparedChunk.chunk();

            chunkMapper.insert(
                    message.tenantId(),
                    message.knowledgeBaseId(),
                    message.documentId(),
                    message.versionId(),
                    chunk.index(),
                    chunk.pageNumber(),
                    chunk.heading(),
                    chunk.content(),
                    preparedChunk.tokenCount(),
                    VectorUtils.toPgVector(preparedChunk.vector())
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        DocumentVersionEntity version = versionMapper.selectById(message.versionId());
        if (version == null) {
            throw new IllegalStateException("文档版本不存在: " + message.versionId());
        }

        version.setStatus("READY");
        version.setErrorMessage(null);
        version.setIndexSignature(signatureService.current());
        version.setIndexedAt(now);
        versionMapper.updateById(version);

        DocumentEntity document = documentMapper.selectById(message.documentId());
        if (document == null) {
            throw new IllegalStateException("文档不存在: " + message.documentId());
        }

        /*
         * 只有当前版本仍是这个 version 时，才更新 document 状态。
         * 为未来真正的多版本上传预留并发安全。
         */
        if (message.versionId().equals(document.getCurrentVersionId())) {
            document.setStatus("READY");
            document.setUpdatedAt(now);
            documentMapper.updateById(document);
        }

        IngestionTaskEntity task = taskMapper.selectById(message.taskId());
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setErrorMessage(null);
            task.setUpdatedAt(now);
            if (task.getStartedAt() == null) {
                task.setStartedAt(now);
            }
            task.setFinishedAt(now);
            taskMapper.updateById(task);
        }
    }

    /**
     * 失败只更新状态。
     *
     * 不删除旧 chunks，不清空上一次成功的 indexSignature/indexedAt，
     * 方便排障和后续重试。
     */
    @Transactional
    public void fail(
            DocumentIngestionMessage message,
            Exception ex
    ) {
        String error = ex.getMessage() == null
                ? ex.getClass().getSimpleName()
                : ex.getMessage();

        if (error.length() > 1000) {
            error = error.substring(0, 1000);
        }

        OffsetDateTime now = OffsetDateTime.now();

        IngestionTaskEntity task = taskMapper.selectById(message.taskId());
        if (task != null) {
            task.setRetryCount(
                    (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1
            );
            task.setStatus("FAILED");
            task.setErrorMessage(error);
            task.setUpdatedAt(now);
            task.setFinishedAt(now);
            taskMapper.updateById(task);
        }

        DocumentVersionEntity version = versionMapper.selectById(message.versionId());
        if (version != null) {
            version.setStatus("FAILED");
            version.setErrorMessage(error);
            versionMapper.updateById(version);
        }

        DocumentEntity document = documentMapper.selectById(message.documentId());
        if (
                document != null
                        && message.versionId().equals(document.getCurrentVersionId())
        ) {
            document.setStatus("FAILED");
            document.setUpdatedAt(now);
            documentMapper.updateById(document);
        }
    }
}