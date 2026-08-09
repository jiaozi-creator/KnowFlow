package com.knowflow.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("refresh_token")
public class RefreshTokenEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long organizationId;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
}
