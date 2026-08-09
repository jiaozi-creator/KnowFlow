package com.knowflow.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("citation")
public class CitationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long messageId;
    private Long documentId;
    private Long chunkId;
    private Integer pageNumber;
    private Integer citationIndex;
    private String excerpt;
    private Double similarity;
}
