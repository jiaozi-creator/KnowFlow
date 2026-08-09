package com.knowflow.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersionEntity> {
    @Select("select coalesce(max(version_no), 0) from document_version where document_id = #{documentId}")
    int maxVersionNo(Long documentId);
}
