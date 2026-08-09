package com.knowflow.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("ingestion_task")
public class IngestionTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long documentId;
    private Long documentVersionId;
    private String status;
    private Integer progress;
    private Integer retryCount;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
