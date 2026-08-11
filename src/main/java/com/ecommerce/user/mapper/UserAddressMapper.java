package com.ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
    @Select("SELECT COUNT(*) FROM user_address WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_address WHERE user_id = #{userId} FOR UPDATE")
    int countByUserIdForUpdate(@Param("userId") Long userId);
}
