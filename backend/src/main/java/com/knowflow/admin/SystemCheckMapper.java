package com.knowflow.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemCheckMapper {

    @Select("""
            select count(*)
              from document_chunk dc
              left join document d on d.id = dc.document_id
             where dc.tenant_id = #{tenantId}
               and d.id is null
            """)
    Long orphanChunks(@Param("tenantId") Long tenantId);

    @Select("""
            select count(*)
              from document_chunk dc
              join document d on d.id = dc.document_id
             where dc.tenant_id = #{tenantId}
               and d.current_version_id is distinct from dc.document_version_id
            """)
    Long nonCurrentChunks(@Param("tenantId") Long tenantId);

    @Select("""
            select count(*)
              from document d
             where d.tenant_id = #{tenantId}
               and d.status = 'READY'
               and not exists (
                   select 1
                     from document_chunk dc
                    where dc.document_id = d.id
                      and dc.document_version_id = d.current_version_id
               )
            """)
    Long readyDocumentsWithoutChunks(@Param("tenantId") Long tenantId);

    @Select("""
            select count(*)
              from ingestion_task
             where tenant_id = #{tenantId}
               and status in ('PENDING','PARSING','CHUNKING','EMBEDDING')
            """)
    Long activeIngestionTasks(@Param("tenantId") Long tenantId);

    @Select("""
            select count(*)
              from document
             where tenant_id = #{tenantId}
               and status = 'FAILED'
            """)
    Long failedDocuments(@Param("tenantId") Long tenantId);

    @Select("""
            select count(*)
              from document
             where tenant_id = #{tenantId}
               and status = 'NEEDS_REINDEX'
            """)
    Long needsReindexDocuments(@Param("tenantId") Long tenantId);
}
