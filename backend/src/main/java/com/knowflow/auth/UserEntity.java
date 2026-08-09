package com.knowflow.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("app_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
