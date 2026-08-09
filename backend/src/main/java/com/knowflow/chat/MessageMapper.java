package com.knowflow.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
    @Select("select * from chat_message where conversation_id = #{conversationId} and tenant_id = #{tenantId} order by id")
    List<MessageEntity> listByConversation(Long conversationId, Long tenantId);
}
