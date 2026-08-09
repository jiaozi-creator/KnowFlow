package com.knowflow.document;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IngestionTaskMapper extends BaseMapper<IngestionTaskEntity> {
    @Select("select * from ingestion_task where document_version_id = #{versionId} order by id desc limit 1")
    IngestionTaskEntity latestByVersion(Long versionId);
}
