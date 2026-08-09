package com.knowflow.organization;

import com.knowflow.auth.OrganizationMemberEntity;
import com.knowflow.auth.UserEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemberMapper {

    /**
     * 查询当前企业全部成员。
     */
    @Select("""
            SELECT
                om.id AS membership_id,
                u.id AS user_id,
                u.email,
                u.display_name,
                u.status AS user_status,
                om.role,
                om.department_id,
                d.name AS department_name,
                om.created_at
            FROM organization_member om
            JOIN app_user u
              ON u.id = om.user_id
            LEFT JOIN department d
              ON d.id = om.department_id
             AND d.tenant_id = om.organization_id
            WHERE om.organization_id = #{tenantId}
            ORDER BY
                CASE om.role
                    WHEN 'OWNER' THEN 0
                    WHEN 'ADMIN' THEN 1
                    ELSE 2
                END,
                u.display_name ASC,
                om.id ASC
            """)
    @Results(
            id = "memberRecordMap",
            value = {
                    @Result(
                            column = "membership_id",
                            property = "membershipId"
                    ),
                    @Result(
                            column = "user_id",
                            property = "userId"
                    ),
                    @Result(
                            column = "email",
                            property = "email"
                    ),
                    @Result(
                            column = "display_name",
                            property = "displayName"
                    ),
                    @Result(
                            column = "user_status",
                            property = "userStatus"
                    ),
                    @Result(
                            column = "role",
                            property = "role"
                    ),
                    @Result(
                            column = "department_id",
                            property = "departmentId"
                    ),
                    @Result(
                            column = "department_name",
                            property = "departmentName"
                    ),
                    @Result(
                            column = "created_at",
                            property = "createdAt"
                    )
            }
    )
    List<MemberRecord> listByTenant(
            Long tenantId
    );

    /**
     * 查询指定成员。
     *
     * 同时强制 organization_id，
     * 防止跨租户访问。
     */
    @Select("""
            SELECT
                om.id AS membership_id,
                u.id AS user_id,
                u.email,
                u.display_name,
                u.status AS user_status,
                om.role,
                om.department_id,
                d.name AS department_name,
                om.created_at
            FROM organization_member om
            JOIN app_user u
              ON u.id = om.user_id
            LEFT JOIN department d
              ON d.id = om.department_id
             AND d.tenant_id = om.organization_id
            WHERE om.id = #{membershipId}
              AND om.organization_id = #{tenantId}
            """)
    @ResultMap("memberRecordMap")
    MemberRecord findMember(
            Long membershipId,
            Long tenantId
    );

    /**
     * 邮箱全局唯一。
     *
     * 当前 app_user.email 本身就是 UNIQUE。
     */
    @Select("""
            SELECT *
            FROM app_user
            WHERE lower(email) = lower(#{email})
            LIMIT 1
            """)
    UserEntity findUserByEmail(
            String email
    );

    /**
     * 创建 app_user。
     */
    @Insert("""
            INSERT INTO app_user (
                email,
                password_hash,
                display_name,
                status
            )
            VALUES (
                #{email},
                #{passwordHash},
                #{displayName},
                #{status}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    void insertUser(
            UserEntity user
    );

    /**
     * 创建企业成员关系。
     */
    @Insert("""
            INSERT INTO organization_member (
                organization_id,
                user_id,
                role,
                department_id
            )
            VALUES (
                #{organizationId},
                #{userId},
                #{role},
                #{departmentId}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "id"
    )
    void insertMembership(
            OrganizationMemberEntity entity
    );

    /**
     * 修改角色和部门。
     */
    @Update("""
            UPDATE organization_member
            SET
                role = #{role},
                department_id = #{departmentId}
            WHERE id = #{membershipId}
              AND organization_id = #{tenantId}
            """)
    int updateMembership(
            Long membershipId,
            Long tenantId,
            String role,
            Long departmentId
    );

    /**
     * 移除企业成员。
     *
     * 这里只删除 organization_member，
     * 不直接删除全局 app_user。
     */
    @Delete("""
            DELETE FROM organization_member
            WHERE id = #{membershipId}
              AND organization_id = #{tenantId}
            """)
    int deleteMembership(
            Long membershipId,
            Long tenantId
    );
}