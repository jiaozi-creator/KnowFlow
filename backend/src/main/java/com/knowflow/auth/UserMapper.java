package com.knowflow.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("select * from app_user where lower(email) = lower(#{email}) limit 1")
    UserEntity findByEmail(String email);
}
