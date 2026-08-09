package com.knowflow.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("organization")
public class OrganizationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String slug;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
