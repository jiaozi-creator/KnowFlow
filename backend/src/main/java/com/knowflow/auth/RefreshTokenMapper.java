package com.knowflow.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {
    @Select("select * from refresh_token where token_hash = #{hash} and revoked_at is null and expires_at > now() limit 1")
    RefreshTokenEntity findActive(String hash);
}
