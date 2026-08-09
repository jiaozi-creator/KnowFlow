package com.knowflow.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {
    @Select("select * from conversation where tenant_id = #{tenantId} and user_id = #{userId} order by updated_at desc")
    List<ConversationEntity> listMine(Long tenantId, Long userId);

    @Select("select * from conversation where id = #{id} and tenant_id = #{tenantId} and user_id = #{userId}")
    ConversationEntity findMine(Long id, Long tenantId, Long userId);
}
