package com.knowflow.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("document_version")
public class DocumentVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long documentId;
    private Integer versionNo;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String status;
    private String errorMessage;

    /**
     * 最近一次成功索引使用的规则签名。
     */
    private String indexSignature;

    /**
     * 最近一次成功索引完成时间。
     */
    private OffsetDateTime indexedAt;

    private OffsetDateTime createdAt;
}