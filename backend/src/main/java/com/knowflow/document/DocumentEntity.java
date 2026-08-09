package com.knowflow.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("document")
public class DocumentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private String status;
    private Long currentVersionId;
    private Long uploadedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
