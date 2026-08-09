package com.knowflow.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            insert into audit_log(
                tenant_id, user_id, action, resource_type, resource_id, metadata
            )
            values(
                #{tenantId}, #{userId}, #{action}, #{resourceType},
                #{resourceId}, cast(#{metadataJson} as jsonb)
            )
            """)
    void insertLog(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("metadataJson") String metadataJson
    );

    @Select("""
            select
                id,
                tenant_id as tenantId,
                user_id as userId,
                action,
                resource_type as resourceType,
                resource_id as resourceId,
                metadata::text as metadata,
                created_at as createdAt
            from audit_log
            where tenant_id = #{tenantId}
            order by created_at desc, id desc
            limit #{limit}
            """)
    List<AuditLogView> listRecent(
            @Param("tenantId") Long tenantId,
            @Param("limit") Integer limit
    );
}
