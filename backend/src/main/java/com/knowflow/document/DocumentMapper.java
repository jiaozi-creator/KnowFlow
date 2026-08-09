package com.knowflow.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    @Select("select * from document where knowledge_base_id = #{knowledgeBaseId} and tenant_id = #{tenantId} order by updated_at desc")
    List<DocumentEntity> listByKnowledgeBase(Long knowledgeBaseId, Long tenantId);

    @Select("select * from document where id = #{id} and tenant_id = #{tenantId}")
    DocumentEntity findByIdAndTenant(Long id, Long tenantId);

    @Update("update document set current_version_id = null where id = #{id}")
    void clearCurrentVersion(Long id);
}
