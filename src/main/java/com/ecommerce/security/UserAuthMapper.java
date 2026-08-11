package com.ecommerce.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAuthMapper {
    @Select("SELECT password_version FROM `user` WHERE id = #{userId} AND status = 1")
    Integer selectPasswordVersion(@Param("userId") Long userId);
}
