package com.knowflow.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CitationMapper extends BaseMapper<CitationEntity> {
    @Select("select * from citation where message_id = #{messageId} order by citation_index")
    List<CitationEntity> listByMessage(Long messageId);
}
