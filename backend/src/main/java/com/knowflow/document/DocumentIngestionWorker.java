package com.knowflow.document;

import com.knowflow.ai.EmbeddingProvider;
import com.knowflow.config.RabbitConfig;
import com.knowflow.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "knowflow.worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DocumentIngestionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIngestionWorker.class);

    private final MinioStorageService storage;
    private final DocumentParserService parser;
    private final TextChunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final IngestionTaskMapper taskMapper;
    private final DocumentIndexPersistenceService persistenceService;

    public DocumentIngestionWorker(
            MinioStorageService storage,
            DocumentParserService parser,
            TextChunker chunker,
            EmbeddingProvider embeddingProvider,
            IngestionTaskMapper taskMapper,
            DocumentIndexPersistenceService persistenceService
    ) {
        this.storage = storage;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.taskMapper = taskMapper;
        this.persistenceService = persistenceService;
    }

    @RabbitListener(queues = RabbitConfig.INGESTION_QUEUE)
    public void consume(DocumentIngestionMessage message) {
        IngestionTaskEntity task = taskMapper.selectById(message.taskId());

        if (task == null) {
            log.warn("忽略不存在的 ingestion task: {}", message.taskId());
            return;
        }

        if (
                "COMPLETED".equals(task.getStatus())
                        || "FAILED".equals(task.getStatus())
        ) {
            return;
        }

        try {
            updateTask(task, "PARSING", 10, null, true);

            byte[] bytes;
            try (InputStream input = storage.get(message.objectKey())) {
                bytes = input.readAllBytes();
            }

            ParsedDocument parsed =
                    parser.parse(bytes, message.originalFilename());

            updateTask(task, "CHUNKING", 35, null, false);

            List<TextChunker.Chunk> chunks =
                    chunker.chunk(parsed);

            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "未提取到有效文本，扫描版 PDF 请在后续版本接入 OCR"
                );
            }

            /*
             * 先把所有新向量准备完整。
             *
             * 只有所有 Embedding 都成功之后，
             * persistenceService.commit() 才会原子替换旧 chunks。
             */
            List<DocumentIndexPersistenceService.PreparedChunk> prepared =
                    new ArrayList<>(chunks.size());

            int total = chunks.size();
            int batchSize = 10;

            for (int offset = 0; offset < total; offset += batchSize) {
                int end = Math.min(total, offset + batchSize);

                List<TextChunker.Chunk> batch =
                        chunks.subList(offset, end);

                List<float[]> vectors =
                        embeddingProvider.embed(
                                batch.stream()
                                        .map(TextChunker.Chunk::content)
                                        .toList()
                        );

                if (vectors == null || vectors.size() != batch.size()) {
                    throw new IllegalStateException(
                            "Embedding 返回数量与输入 chunk 数不一致"
                    );
                }

                for (int i = 0; i < batch.size(); i++) {
                    TextChunker.Chunk chunk = batch.get(i);

                    prepared.add(
                            new DocumentIndexPersistenceService.PreparedChunk(
                                    chunk,
                                    vectors.get(i),
                                    estimateTokens(chunk.content())
                            )
                    );
                }

                int progress =
                        40 + (int) Math.round(55.0 * end / total);

                updateTask(
                        task,
                        "EMBEDDING",
                        Math.min(progress, 95),
                        null,
                        false
                );
            }

            persistenceService.commit(message, prepared);

        } catch (Exception ex) {
            /*
             * 不再向 RabbitMQ 抛出异常，避免默认 requeue 造成
             * Embedding API 被无限重复调用。
             *
             * FAILED 文档由管理员从 UI 主动重试。
             */
            try {
                persistenceService.fail(message, ex);
            } catch (Exception persistenceError) {
                log.error(
                        "记录文档索引失败状态时再次失败，taskId={}",
                        message.taskId(),
                        persistenceError
                );
            }

            log.error(
                    "文档索引失败，documentId={}, versionId={}, taskId={}",
                    message.documentId(),
                    message.versionId(),
                    message.taskId(),
                    ex
            );
        }
    }

    private void updateTask(
            IngestionTaskEntity task,
            String status,
            Integer progress,
            String error,
            boolean start
    ) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMessage(error);
        task.setUpdatedAt(OffsetDateTime.now());

        if (start && task.getStartedAt() == null) {
            task.setStartedAt(OffsetDateTime.now());
        }

        taskMapper.updateById(task);
    }

    private int estimateTokens(String content) {
        return Math.max(1, content.length() / 3);
    }
}