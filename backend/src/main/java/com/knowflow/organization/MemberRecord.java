package com.knowflow.organization;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 成员管理页面查询结果。
 *
 * 该对象并不直接对应一张表，
 * 而是 organization_member
 * + app_user
 * + department
 * 三张表 JOIN 后的结果。
 */
@Data
public class MemberRecord {

    private Long membershipId;

    private Long userId;

    private String email;

    private String displayName;

    private String userStatus;

    private String role;

    private Long departmentId;

    private String departmentName;

    private OffsetDateTime createdAt;
}