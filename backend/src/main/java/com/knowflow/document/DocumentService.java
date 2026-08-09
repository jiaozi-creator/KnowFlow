package com.knowflow.document;

import com.knowflow.common.BusinessException;
import com.knowflow.config.RabbitConfig;
import com.knowflow.knowledge.KnowledgeBaseService;
import com.knowflow.security.SecurityUtils;
import com.knowflow.storage.MinioStorageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Set<String> ALLOWED =
            Set.of("pdf", "docx", "md", "markdown", "txt");

    private static final long MAX_FILE_SIZE =
            30L * 1024L * 1024L;

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper versionMapper;
    private final IngestionTaskMapper taskMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MinioStorageService storageService;
    private final RabbitTemplate rabbitTemplate;
    private final IndexSignatureService signatureService;
    private final IndexMaintenanceService maintenanceService;

    public DocumentService(
            DocumentMapper documentMapper,
            DocumentVersionMapper versionMapper,
            IngestionTaskMapper taskMapper,
            KnowledgeBaseService knowledgeBaseService,
            MinioStorageService storageService,
            RabbitTemplate rabbitTemplate,
            IndexSignatureService signatureService,
            IndexMaintenanceService maintenanceService
    ) {
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.storageService = storageService;
        this.rabbitTemplate = rabbitTemplate;
        this.signatureService = signatureService;
        this.maintenanceService = maintenanceService;
    }

    public List<DocumentDtos.View> list(Long knowledgeBaseId) {
        knowledgeBaseService.require(knowledgeBaseId);

        return documentMapper
                .listByKnowledgeBase(
                        knowledgeBaseId,
                        SecurityUtils.current().tenantId()
                )
                .stream()
                .map(DocumentDtos.View::from)
                .toList();
    }

    @Transactional
    public DocumentDtos.UploadResponse upload(
            Long knowledgeBaseId,
            MultipartFile file
    ) {
        knowledgeBaseService.requireManage(knowledgeBaseId);

        if (file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("单文件不能超过 30 MB");
        }

        String original =
                file.getOriginalFilename() == null
                        ? "file"
                        : file.getOriginalFilename();

        String safeFilename =
                original.replace('/', '_').replace('\\', '_');

        String contentType =
                file.getContentType() == null
                        ? "application/octet-stream"
                        : file.getContentType();

        String extension = extension(original);

        if (!ALLOWED.contains(extension)) {
            throw BusinessException.badRequest(
                    "仅支持 PDF、DOCX、Markdown、TXT"
            );
        }

        long tenantId = SecurityUtils.current().tenantId();

        String objectKey =
                tenantId
                        + "/"
                        + knowledgeBaseId
                        + "/"
                        + UUID.randomUUID()
                        + "/"
                        + safeFilename;

        try (InputStream input = file.getInputStream()) {
            storageService.put(
                    objectKey,
                    input,
                    file.getSize(),
                    contentType
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "文件上传失败: " + ex.getMessage(),
                    ex
            );
        }

        /*
         * 如果数据库事务最终回滚，清理已经写入 MinIO 的孤儿对象。
         */
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                storageService.delete(objectKey);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
        );

        DocumentEntity document = new DocumentEntity();
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName(original);
        document.setFileType(extension.toUpperCase(Locale.ROOT));
        document.setStatus("PROCESSING");
        document.setUploadedBy(SecurityUtils.current().userId());
        documentMapper.insert(document);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setTenantId(tenantId);
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setObjectKey(objectKey);
        version.setOriginalFilename(original);
        version.setContentType(contentType);
        version.setFileSize(file.getSize());
        version.setSha256(sha256(file));
        version.setStatus("UPLOADED");
        version.setIndexSignature(null);
        version.setIndexedAt(null);
        versionMapper.insert(version);

        document.setCurrentVersionId(version.getId());
        documentMapper.updateById(document);

        IngestionTaskEntity task = createTask(document, version);
        sendAfterCommit(document, version, task);

        return new DocumentDtos.UploadResponse(
                DocumentDtos.View.from(
                        documentMapper.selectById(document.getId())
                ),
                version.getId(),
                task.getId()
        );
    }

    public DocumentDtos.TaskView task(Long documentId) {
        DocumentEntity document = require(documentId);

        IngestionTaskEntity task =
                taskMapper.latestByVersion(
                        document.getCurrentVersionId()
                );

        if (task == null) {
            throw BusinessException.notFound("处理任务不存在");
        }

        return DocumentDtos.TaskView.from(task);
    }

    @Transactional
    public DocumentDtos.TaskView reindex(Long documentId) {
        DocumentEntity document = require(documentId);
        knowledgeBaseService.requireManage(document.getKnowledgeBaseId());

        return scheduleReindex(document);
    }

    public DocumentDtos.IndexStatus indexStatus(Long knowledgeBaseId) {
        knowledgeBaseService.requireManage(knowledgeBaseId);

        /*
         * 正常情况下启动时已经刷新。
         * 这里再次刷新是为了管理页主动检查时结果始终准确。
         */
        maintenanceService.refresh();

        List<DocumentEntity> documents =
                documentMapper.listByKnowledgeBase(
                        knowledgeBaseId,
                        SecurityUtils.current().tenantId()
                );

        int ready = 0;
        int needsReindex = 0;
        int processing = 0;
        int failed = 0;

        for (DocumentEntity document : documents) {
            switch (document.getStatus()) {
                case "READY" -> ready++;
                case "NEEDS_REINDEX" -> needsReindex++;
                case "PROCESSING" -> processing++;
                case "FAILED" -> failed++;
                default -> {
                }
            }
        }

        return new DocumentDtos.IndexStatus(
                signatureService.current(),
                documents.size(),
                ready,
                needsReindex,
                processing,
                failed,
                needsReindex + failed
        );
    }

    /**
     * 一键处理：
     * - NEEDS_REINDEX
     * - FAILED
     *
     * READY 不重复花费 Embedding API。
     * PROCESSING 不创建重复任务。
     */
    @Transactional
    public DocumentDtos.BatchReindexResponse repairIndexes(
            Long knowledgeBaseId
    ) {
        knowledgeBaseService.requireManage(knowledgeBaseId);
        maintenanceService.refresh();

        List<DocumentEntity> documents =
                documentMapper.listByKnowledgeBase(
                        knowledgeBaseId,
                        SecurityUtils.current().tenantId()
                );

        List<Long> documentIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();

        for (DocumentEntity document : documents) {
            if (
                    !"NEEDS_REINDEX".equals(document.getStatus())
                            && !"FAILED".equals(document.getStatus())
            ) {
                continue;
            }

            DocumentDtos.TaskView task =
                    scheduleReindex(document);

            documentIds.add(document.getId());
            taskIds.add(task.id());
        }

        return new DocumentDtos.BatchReindexResponse(
                signatureService.current(),
                documentIds.size(),
                documentIds,
                taskIds
        );
    }

    public DocumentDtos.Content content(Long documentId) {
        DocumentEntity document = require(documentId);

        DocumentVersionEntity version =
                versionMapper.selectById(
                        document.getCurrentVersionId()
                );

        if (version == null) {
            throw BusinessException.notFound("文档当前版本不存在");
        }

        try {
            return new DocumentDtos.Content(
                    version.getOriginalFilename(),
                    version.getContentType() == null
                            ? "application/octet-stream"
                            : version.getContentType(),
                    storageService.get(version.getObjectKey())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("读取文档失败", ex);
        }
    }

    @Transactional
    public void delete(Long documentId) {
        DocumentEntity document = require(documentId);
        knowledgeBaseService.requireManage(document.getKnowledgeBaseId());

        DocumentVersionEntity version =
                versionMapper.selectById(
                        document.getCurrentVersionId()
                );

        documentMapper.clearCurrentVersion(documentId);
        documentMapper.deleteById(documentId);

        if (version != null) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                storageService.delete(version.getObjectKey());
                            } catch (Exception ignored) {
                            }
                        }
                    }
            );
        }
    }

    private DocumentDtos.TaskView scheduleReindex(
            DocumentEntity document
    ) {
        DocumentVersionEntity version =
                versionMapper.selectById(
                        document.getCurrentVersionId()
                );

        if (version == null) {
            throw BusinessException.notFound("文档当前版本不存在");
        }

        /*
         * 防重复提交：
         * 已经存在活跃 task 时直接复用。
         */
        IngestionTaskEntity latest =
                taskMapper.latestByVersion(version.getId());

        if (latest != null && isActive(latest.getStatus())) {
            return DocumentDtos.TaskView.from(latest);
        }

        document.setStatus("PROCESSING");
        document.setUpdatedAt(OffsetDateTime.now());
        documentMapper.updateById(document);

        version.setStatus("UPLOADED");
        version.setErrorMessage(null);

        /*
         * 不清空上一次成功的 indexSignature/indexedAt。
         * 新索引成功时由 commit 原子覆盖；
         * 新索引失败时仍保留历史审计信息。
         */
        versionMapper.updateById(version);

        IngestionTaskEntity task = createTask(document, version);
        sendAfterCommit(document, version, task);

        return DocumentDtos.TaskView.from(task);
    }

    private boolean isActive(String status) {
        return "PENDING".equals(status)
                || "PARSING".equals(status)
                || "CHUNKING".equals(status)
                || "EMBEDDING".equals(status);
    }

    private IngestionTaskEntity createTask(
            DocumentEntity document,
            DocumentVersionEntity version
    ) {
        IngestionTaskEntity task = new IngestionTaskEntity();

        task.setTenantId(document.getTenantId());
        task.setDocumentId(document.getId());
        task.setDocumentVersionId(version.getId());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setRetryCount(0);

        taskMapper.insert(task);

        return task;
    }

    private void sendAfterCommit(
            DocumentEntity document,
            DocumentVersionEntity version,
            IngestionTaskEntity task
    ) {
        DocumentIngestionMessage message =
                new DocumentIngestionMessage(
                        document.getTenantId(),
                        document.getKnowledgeBaseId(),
                        document.getId(),
                        version.getId(),
                        task.getId(),
                        version.getObjectKey(),
                        version.getOriginalFilename(),
                        version.getContentType()
                );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        rabbitTemplate.convertAndSend(
                                RabbitConfig.INGESTION_EXCHANGE,
                                RabbitConfig.INGESTION_ROUTING_KEY,
                                message
                        );
                    }
                }
        );
    }

    private DocumentEntity require(Long id) {
        DocumentEntity document =
                documentMapper.findByIdAndTenant(
                        id,
                        SecurityUtils.current().tenantId()
                );

        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }

        knowledgeBaseService.require(document.getKnowledgeBaseId());

        return document;
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');

        return index < 0
                ? ""
                : filename
                .substring(index + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }

            return HexFormat
                    .of()
                    .formatHex(digest.digest());

        } catch (Exception ex) {
            return HexFormat
                    .of()
                    .formatHex(
                            originalBytes(file.getOriginalFilename())
                    );
        }
    }

    private byte[] originalBytes(String name) {
        return (
                name == null
                        ? UUID.randomUUID().toString()
                        : name
        ).getBytes(StandardCharsets.UTF_8);
    }
}