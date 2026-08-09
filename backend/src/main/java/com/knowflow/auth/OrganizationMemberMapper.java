package com.knowflow.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrganizationMemberMapper
        extends BaseMapper<OrganizationMemberEntity> {

    /**
     * 原有登录逻辑使用。
     */
    @Select("""
            select *
            from organization_member
            where user_id = #{userId}
            order by id
            limit 1
            """)
    OrganizationMemberEntity firstByUserId(
            Long userId
    );

    /**
     * 根据企业 + 用户查成员关系。
     */
    @Select("""
            select *
            from organization_member
            where organization_id = #{organizationId}
              and user_id = #{userId}
            limit 1
            """)
    OrganizationMemberEntity findByOrganizationAndUser(
            Long organizationId,
            Long userId
    );

    /**
     * 根据成员关系 ID 查询，
     * 同时限制企业，防止跨租户访问。
     */
    @Select("""
            select *
            from organization_member
            where id = #{id}
              and organization_id = #{organizationId}
            """)
    OrganizationMemberEntity findByIdAndOrganization(
            Long id,
            Long organizationId
    );
}