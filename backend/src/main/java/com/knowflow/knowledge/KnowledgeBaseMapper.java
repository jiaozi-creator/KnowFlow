package com.knowflow.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper
        extends BaseMapper<KnowledgeBaseEntity> {

    /**
     * OWNER / ADMIN：
     * 查询整个企业所有知识库。
     */
    @Select("""
            select *
            from knowledge_base
            where tenant_id = #{tenantId}
            order by updated_at desc
            """)
    List<KnowledgeBaseEntity> listByTenant(
            Long tenantId
    );

    /**
     * 普通 MEMBER：
     * 只查询自己真正有权限访问的知识库。
     */
    @Select("""
            select kb.*
            from knowledge_base kb
            where kb.tenant_id = #{tenantId}

              and (

                    /*
                     * 企业全员可见
                     */
                    kb.visibility = 'TENANT'

                    /*
                     * 私有知识库：
                     * 只有创建者本人
                     */
                    or (
                        kb.visibility = 'PRIVATE'
                        and kb.created_by = #{userId}
                    )

                    /*
                     * 部门知识库
                     */
                    or (
                        kb.visibility = 'DEPARTMENT'

                        and exists (
                            select 1
                            from organization_member om

                            join knowledge_base_department_acl acl
                              on acl.department_id = om.department_id
                             and acl.knowledge_base_id = kb.id
                             and acl.tenant_id = kb.tenant_id

                            where om.organization_id = #{tenantId}
                              and om.user_id = #{userId}
                              and om.department_id is not null
                        )
                    )

                    /*
                     * 指定成员知识库
                     */
                    or (
                        kb.visibility = 'MEMBER'

                        and exists (
                            select 1
                            from organization_member om

                            join knowledge_base_member_acl acl
                              on acl.organization_member_id = om.id
                             and acl.knowledge_base_id = kb.id
                             and acl.tenant_id = kb.tenant_id

                            where om.organization_id = #{tenantId}
                              and om.user_id = #{userId}
                        )
                    )
              )

            order by kb.updated_at desc
            """)
    List<KnowledgeBaseEntity> listAccessible(
            Long tenantId,
            Long userId
    );

    /**
     * 企业内原始查询。
     */
    @Select("""
            select *
            from knowledge_base
            where id = #{id}
              and tenant_id = #{tenantId}
            """)
    KnowledgeBaseEntity findByIdAndTenant(
            Long id,
            Long tenantId
    );

    /**
     * 普通成员的知识库访问判断。
     */
    @Select("""
            select kb.*
            from knowledge_base kb
            where kb.id = #{id}
              and kb.tenant_id = #{tenantId}

              and (

                    kb.visibility = 'TENANT'

                    or (
                        kb.visibility = 'PRIVATE'
                        and kb.created_by = #{userId}
                    )

                    or (
                        kb.visibility = 'DEPARTMENT'

                        and exists (
                            select 1
                            from organization_member om

                            join knowledge_base_department_acl acl
                              on acl.department_id = om.department_id
                             and acl.knowledge_base_id = kb.id
                             and acl.tenant_id = kb.tenant_id

                            where om.organization_id = #{tenantId}
                              and om.user_id = #{userId}
                              and om.department_id is not null
                        )
                    )

                    or (
                        kb.visibility = 'MEMBER'

                        and exists (
                            select 1
                            from organization_member om

                            join knowledge_base_member_acl acl
                              on acl.organization_member_id = om.id
                             and acl.knowledge_base_id = kb.id
                             and acl.tenant_id = kb.tenant_id

                            where om.organization_id = #{tenantId}
                              and om.user_id = #{userId}
                        )
                    )
              )
            """)
    KnowledgeBaseEntity findAccessible(
            Long id,
            Long tenantId,
            Long userId
    );
}