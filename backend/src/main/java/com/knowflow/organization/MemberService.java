package com.knowflow.organization;

import com.knowflow.auth.OrganizationMemberEntity;
import com.knowflow.auth.UserEntity;
import com.knowflow.common.BusinessException;
import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    private final DepartmentService departmentService;

    private final PasswordEncoder passwordEncoder;

    public MemberService(
            MemberMapper memberMapper,
            DepartmentService departmentService,
            PasswordEncoder passwordEncoder
    ) {

        this.memberMapper =
                memberMapper;

        this.departmentService =
                departmentService;

        this.passwordEncoder =
                passwordEncoder;
    }

    /**
     * ============================================================
     * 企业成员列表
     * ============================================================
     *
     * 只有 OWNER / ADMIN 可以查看。
     *
     * 普通 MEMBER 即使手工请求：
     *
     * GET /api/members
     *
     * 也会被拒绝。
     */
    public List<MemberDtos.View> list() {

        requireAdmin();

        UserPrincipal user =
                SecurityUtils.current();

        return memberMapper
                .listByTenant(
                        user.tenantId()
                )
                .stream()
                .map(
                        this::toView
                )
                .toList();
    }

    /**
     * ============================================================
     * 创建成员
     * ============================================================
     *
     * 当前版本：
     *
     * 管理员直接创建账号，
     * 设置初始密码。
     *
     * 后续可以升级为：
     *
     * 邮件邀请
     * +
     * 首次登录设置密码
     */
    @Transactional
    public MemberDtos.View create(
            MemberDtos.CreateRequest request
    ) {

        requireAdmin();

        UserPrincipal current =
                SecurityUtils.current();

        /*
         * 邮箱统一转成小写。
         */
        String email =
                request
                        .email()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        /*
         * ========================================================
         * 检查邮箱是否已经存在
         * ========================================================
         *
         * 当前 app_user.email 是全局 UNIQUE。
         *
         * 当前版本暂时不实现：
         *
         * 一个账号加入多个企业。
         */
        UserEntity existing =
                memberMapper.findUserByEmail(
                        email
                );

        if (
                existing != null
        ) {

            throw BusinessException.badRequest(
                    "该邮箱已存在。当前版本暂不支持将已有账号加入第二个企业"
            );
        }

        /*
         * ========================================================
         * 校验部门
         * ========================================================
         *
         * departmentService.require()
         *
         * 会校验：
         *
         * department.id
         * +
         * 当前 tenantId
         *
         * 因此不能把其他企业部门分配给当前企业成员。
         */
        if (
                request.departmentId()
                        != null
        ) {

            departmentService.require(
                    request.departmentId()
            );
        }

        /*
         * 新建成员只允许：
         *
         * ADMIN
         * MEMBER
         *
         * OWNER 不能通过这里创建。
         */
        String role =
                normalizeCreateRole(
                        request.role()
                );

        /*
         * ========================================================
         * 创建 app_user
         * ========================================================
         */
        UserEntity user =
                new UserEntity();

        user.setEmail(
                email
        );

        user.setDisplayName(
                request
                        .displayName()
                        .trim()
        );

        /*
         * BCrypt 加密密码。
         *
         * 数据库绝对不保存明文密码。
         */
        user.setPasswordHash(
                passwordEncoder.encode(
                        request.temporaryPassword()
                )
        );

        user.setStatus(
                "ACTIVE"
        );

        memberMapper.insertUser(
                user
        );

        /*
         * ========================================================
         * 创建 organization_member
         * ========================================================
         */
        OrganizationMemberEntity membership =
                new OrganizationMemberEntity();

        membership.setOrganizationId(
                current.tenantId()
        );

        membership.setUserId(
                user.getId()
        );

        membership.setRole(
                role
        );

        membership.setDepartmentId(
                request.departmentId()
        );

        memberMapper.insertMembership(
                membership
        );

        /*
         * 返回完整 JOIN 后的数据。
         */
        return toView(
                requireMember(
                        membership.getId(),
                        current.tenantId()
                )
        );
    }

    /**
     * ============================================================
     * 修改成员
     * ============================================================
     *
     * 可以修改：
     *
     * departmentId
     * role
     */
    @Transactional
    public MemberDtos.View update(
            Long membershipId,
            MemberDtos.UpdateRequest request
    ) {

        requireAdmin();

        UserPrincipal current =
                SecurityUtils.current();

        MemberRecord target =
                requireMember(
                        membershipId,
                        current.tenantId()
                );

        /*
         * ========================================================
         * 校验新部门
         * ========================================================
         */
        if (
                request.departmentId()
                        != null
        ) {

            departmentService.require(
                    request.departmentId()
            );
        }

        /*
         * 默认维持原角色。
         */
        String newRole =
                target.getRole();

        /*
         * ========================================================
         * OWNER 保护
         * ========================================================
         *
         * 企业所有者不能通过普通成员管理接口
         * 降级为 ADMIN / MEMBER。
         */
        if (
                "OWNER".equals(
                        target.getRole()
                )
        ) {

            if (
                    request.role()
                            != null
            ) {

                throw BusinessException.badRequest(
                        "企业所有者 OWNER 的角色不能在成员管理中修改"
                );
            }

        } else if (
                request.role()
                        != null
        ) {

            newRole =
                    normalizeManagedRole(
                            request.role()
                    );
        }

        /*
         * ========================================================
         * 更新 organization_member
         * ========================================================
         */
        int updated =
                memberMapper.updateMembership(
                        membershipId,
                        current.tenantId(),
                        newRole,
                        request.departmentId()
                );

        if (
                updated == 0
        ) {

            throw BusinessException.notFound(
                    "成员不存在"
            );
        }

        return toView(
                requireMember(
                        membershipId,
                        current.tenantId()
                )
        );
    }

    /**
     * ============================================================
     * 移除成员
     * ============================================================
     *
     * 注意：
     *
     * 删除的是 organization_member，
     * 不是直接 DELETE app_user。
     */
    @Transactional
    public void delete(
            Long membershipId
    ) {

        requireAdmin();

        UserPrincipal current =
                SecurityUtils.current();

        MemberRecord target =
                requireMember(
                        membershipId,
                        current.tenantId()
                );

        /*
         * OWNER 不能删除。
         */
        if (
                "OWNER".equals(
                        target.getRole()
                )
        ) {

            throw BusinessException.badRequest(
                    "不能移除企业所有者"
            );
        }

        /*
         * 当前登录用户不能删除自己。
         */
        if (
                target
                        .getUserId()
                        .equals(
                                current.userId()
                        )
        ) {

            throw BusinessException.badRequest(
                    "不能移除当前登录账号"
            );
        }

        int deleted =
                memberMapper.deleteMembership(
                        membershipId,
                        current.tenantId()
                );

        if (
                deleted == 0
        ) {

            throw BusinessException.notFound(
                    "成员不存在"
            );
        }
    }

    /**
     * ============================================================
     * 查询成员并验证 tenant
     * ============================================================
     */
    private MemberRecord requireMember(
            Long membershipId,
            Long tenantId
    ) {

        MemberRecord record =
                memberMapper.findMember(
                        membershipId,
                        tenantId
                );

        if (
                record == null
        ) {

            /*
             * 不向请求者暴露其他企业成员是否存在。
             */
            throw BusinessException.notFound(
                    "成员不存在"
            );
        }

        return record;
    }

    /**
     * ============================================================
     * 新建角色标准化
     * ============================================================
     */
    private String normalizeCreateRole(
            String role
    ) {

        if (
                role == null
                        || role.isBlank()
        ) {

            return "MEMBER";
        }

        return normalizeManagedRole(
                role
        );
    }

    /**
     * ============================================================
     * 可管理角色标准化
     * ============================================================
     *
     * OWNER 不允许通过普通成员管理接口创建。
     */
    private String normalizeManagedRole(
            String role
    ) {

        String value =
                role
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !"ADMIN".equals(
                        value
                )
                        &&
                !"MEMBER".equals(
                        value
                )
        ) {

            throw BusinessException.badRequest(
                    "角色只能是 ADMIN 或 MEMBER"
            );
        }

        return value;
    }

    /**
     * ============================================================
     * 管理员权限判断
     * ============================================================
     *
     * OWNER
     * ADMIN
     *
     * 可以管理企业成员。
     *
     * MEMBER：
     * 禁止。
     */
    private void requireAdmin() {

        if (
                !SecurityUtils.isTenantAdmin()
        ) {

            throw BusinessException.forbidden(
                    "只有企业管理员可以管理成员"
            );
        }
    }

    /**
     * ============================================================
     * MemberRecord -> DTO
     * ============================================================
     */
    private MemberDtos.View toView(
            MemberRecord record
    ) {

        return new MemberDtos.View(
                record.getMembershipId(),
                record.getUserId(),
                record.getEmail(),
                record.getDisplayName(),
                record.getUserStatus(),
                record.getRole(),
                record.getDepartmentId(),
                record.getDepartmentName(),
                record.getCreatedAt()
        );
    }
}