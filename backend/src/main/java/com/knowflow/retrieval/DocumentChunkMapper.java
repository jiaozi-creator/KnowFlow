package com.knowflow.retrieval;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    @Insert("""
            insert into document_chunk
            (
                tenant_id,
                knowledge_base_id,
                document_id,
                document_version_id,
                chunk_index,
                page_number,
                heading,
                content,
                token_count,
                embedding
            )
            values
            (
                #{tenantId},
                #{knowledgeBaseId},
                #{documentId},
                #{versionId},
                #{chunkIndex},
                #{pageNumber},
                #{heading},
                #{content},
                #{tokenCount},
                cast(#{embedding} as vector)
            )
            """)
    void insert(
            Long tenantId,
            Long knowledgeBaseId,
            Long documentId,
            Long versionId,
            Integer chunkIndex,
            Integer pageNumber,
            String heading,
            String content,
            Integer tokenCount,
            String embedding
    );

    @Delete("""
            delete from document_chunk
            where document_version_id = #{versionId}
            """)
    void deleteByVersion(
            Long versionId
    );

    /**
     * ============================================================
     * 带 ACL 的 RAG 向量检索
     * ============================================================
     */
    @Select("""
            select
                dc.id as chunk_id,
                dc.document_id,
                d.name as document_name,
                dc.page_number,
                dc.heading,
                dc.content,

                1 - (
                    dc.embedding
                    <=>
                    cast(#{embedding} as vector)
                ) as similarity

            from document_chunk dc

            join document d
              on d.id = dc.document_id

            join knowledge_base kb
              on kb.id = dc.knowledge_base_id
             and kb.tenant_id = dc.tenant_id

            where dc.tenant_id = #{tenantId}
              and exists (
                  select 1
                    from document d_active
                    join document_version dv_active
                      on dv_active.id = dc.document_version_id
                   where d_active.id = dc.document_id
                     and d_active.tenant_id = dc.tenant_id
                     and d_active.status = 'READY'
                     and d_active.current_version_id = dc.document_version_id
                     and dv_active.status = 'READY'
              )

              and dc.knowledge_base_id =
                  any(
                      cast(
                          #{knowledgeBaseIds}
                          as bigint[]
                      )
                  )

              and d.status = 'READY'

              and d.current_version_id =
                  dc.document_version_id

              /*
               * ====================================================
               * 权限过滤
               * ====================================================
               */
              and (

                    /*
                     * OWNER / ADMIN
                     */
                    #{role} in (
                        'OWNER',
                        'ADMIN'
                    )

                    /*
                     * TENANT
                     */
                    or kb.visibility =
                        'TENANT'

                    /*
                     * PRIVATE
                     */
                    or (
                        kb.visibility =
                            'PRIVATE'

                        and kb.created_by =
                            #{userId}
                    )

                    /*
                     * DEPARTMENT
                     */
                    or (
                        kb.visibility =
                            'DEPARTMENT'

                        and exists (
                            select 1

                            from organization_member om

                            join knowledge_base_department_acl acl
                              on acl.department_id =
                                 om.department_id

                             and acl.knowledge_base_id =
                                 kb.id

                             and acl.tenant_id =
                                 kb.tenant_id

                            where om.organization_id =
                                  #{tenantId}

                              and om.user_id =
                                  #{userId}

                              and om.department_id
                                  is not null
                        )
                    )

                    /*
                     * MEMBER
                     */
                    or (
                        kb.visibility =
                            'MEMBER'

                        and exists (
                            select 1

                            from organization_member om

                            join knowledge_base_member_acl acl
                              on acl.organization_member_id =
                                 om.id

                             and acl.knowledge_base_id =
                                 kb.id

                             and acl.tenant_id =
                                 kb.tenant_id

                            where om.organization_id =
                                  #{tenantId}

                              and om.user_id =
                                  #{userId}
                        )
                    )
              )

            order by
                dc.embedding
                <=>
                cast(
                    #{embedding}
                    as vector
                )

            limit #{topK}
            """)
    List<ChunkSearchResult> search(
            Long tenantId,
            Long userId,
            String role,
            String knowledgeBaseIds,
            String embedding,
            Integer topK
    );
}