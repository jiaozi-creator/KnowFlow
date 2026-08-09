package com.knowflow.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("organization_member")
public class OrganizationMemberEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long organizationId;

    private Long userId;

    private String role;

    private Long departmentId;

    private OffsetDateTime createdAt;
}