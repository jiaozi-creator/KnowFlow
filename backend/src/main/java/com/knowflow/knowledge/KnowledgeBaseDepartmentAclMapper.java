package com.knowflow.knowledge;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseDepartmentAclMapper {

    @Select("""
            select department_id
            from knowledge_base_department_acl
            where tenant_id = #{tenantId}
              and knowledge_base_id = #{knowledgeBaseId}
            order by id
            """)
    List<Long> listDepartmentIds(
            Long tenantId,
            Long knowledgeBaseId
    );

    @Insert("""
            insert into knowledge_base_department_acl (
                tenant_id,
                knowledge_base_id,
                department_id
            )
            values (
                #{tenantId},
                #{knowledgeBaseId},
                #{departmentId}
            )
            """)
    void insert(
            Long tenantId,
            Long knowledgeBaseId,
            Long departmentId
    );

    @Delete("""
            delete from knowledge_base_department_acl
            where tenant_id = #{tenantId}
              and knowledge_base_id = #{knowledgeBaseId}
            """)
    void deleteByKnowledgeBase(
            Long tenantId,
            Long knowledgeBaseId
    );
}