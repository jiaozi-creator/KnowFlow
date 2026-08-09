package com.knowflow.organization;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("department")
public class DepartmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long parentId;

    private String name;

    private Integer sortOrder;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}