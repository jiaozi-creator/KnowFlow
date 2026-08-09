package com.knowflow.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IngestionRecoveryMapper {

    @Update("""
            update document_version dv
               set status = 'FAILED',
                   error_message = coalesce(
                       dv.error_message,
                       '索引任务因服务中断超时，已自动标记失败，可重新索引'
                   )
             where exists (
                 select 1
                   from ingestion_task t
                  where t.document_version_id = dv.id
                    and t.status in ('PENDING','PARSING','CHUNKING','EMBEDDING')
                    and t.updated_at <
                        now() - (#{timeoutMinutes} * interval '1 minute')
             )
            """)
    int failStaleVersions(@Param("timeoutMinutes") Integer timeoutMinutes);

    @Update("""
            update document d
               set status = 'FAILED',
                   updated_at = now()
             where d.status = 'PROCESSING'
               and exists (
                   select 1
                     from ingestion_task t
                    where t.document_id = d.id
                      and t.document_version_id = d.current_version_id
                      and t.status in ('PENDING','PARSING','CHUNKING','EMBEDDING')
                      and t.updated_at <
                          now() - (#{timeoutMinutes} * interval '1 minute')
               )
            """)
    int failStaleDocuments(@Param("timeoutMinutes") Integer timeoutMinutes);

    @Update("""
            update ingestion_task
               set status = 'FAILED',
                   error_message = coalesce(
                       error_message,
                       '索引任务因服务中断超时，已自动标记失败，可重新索引'
                   ),
                   finished_at = coalesce(finished_at, now()),
                   updated_at = now(),
                   retry_count = retry_count + 1
             where status in ('PENDING','PARSING','CHUNKING','EMBEDDING')
               and updated_at <
                   now() - (#{timeoutMinutes} * interval '1 minute')
            """)
    int failStaleTasks(@Param("timeoutMinutes") Integer timeoutMinutes);
}
