package com.knowflow.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("chat_message")
public class MessageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long conversationId;
    private String role;
    private String content;
    private String status;
    private OffsetDateTime createdAt;
}
