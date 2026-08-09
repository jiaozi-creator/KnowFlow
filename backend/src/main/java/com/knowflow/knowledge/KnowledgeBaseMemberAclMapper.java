package com.knowflow.knowledge;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMemberAclMapper {

    @Select("""
            select organization_member_id
            from knowledge_base_member_acl
            where tenant_id = #{tenantId}
              and knowledge_base_id = #{knowledgeBaseId}
            order by id
            """)
    List<Long> listMemberIds(
            Long tenantId,
            Long knowledgeBaseId
    );

    @Insert("""
            insert into knowledge_base_member_acl (
                tenant_id,
                knowledge_base_id,
                organization_member_id
            )
            values (
                #{tenantId},
                #{knowledgeBaseId},
                #{organizationMemberId}
            )
            """)
    void insert(
            Long tenantId,
            Long knowledgeBaseId,
            Long organizationMemberId
    );

    @Delete("""
            delete from knowledge_base_member_acl
            where tenant_id = #{tenantId}
              and knowledge_base_id = #{knowledgeBaseId}
            """)
    void deleteByKnowledgeBase(
            Long tenantId,
            Long knowledgeBaseId
    );
}