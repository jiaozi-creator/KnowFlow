package com.knowflow.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class MemberDtos {

    private MemberDtos() {
    }

    /**
     * 管理员新建企业成员。
     *
     * 当前 MVP 暂不接邮件邀请，
     * 直接创建初始账号和临时密码。
     */
    public record CreateRequest(

            @NotBlank
            @Email
            @Size(max = 190)
            String email,

            @NotBlank
            @Size(max = 80)
            String displayName,

            @NotBlank
            @Size(min = 8, max = 72)
            String temporaryPassword,

            Long departmentId,

            @Pattern(
                    regexp = "ADMIN|MEMBER",
                    message = "角色只能是 ADMIN 或 MEMBER"
            )
            String role
    ) {
    }

    /**
     * 编辑成员。
     *
     * departmentId = null 表示未分配部门。
     *
     * role 可以不传。
     */
    public record UpdateRequest(

            Long departmentId,

            @Pattern(
                    regexp = "ADMIN|MEMBER",
                    message = "角色只能是 ADMIN 或 MEMBER"
            )
            String role
    ) {
    }

    public record View(
            Long membershipId,
            Long userId,
            String email,
            String displayName,
            String status,
            String role,
            Long departmentId,
            String departmentName,
            OffsetDateTime createdAt
    ) {
    }
}