package com.ecommerce.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.refund.entity.Refund;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {
    @Select("SELECT * FROM refund WHERE order_id = #{orderId} AND status = 'APPLYING' LIMIT 1")
    Refund selectApplyingByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM refund WHERE order_id = #{orderId} ORDER BY created_at DESC LIMIT 1")
    Refund selectLatestByOrderId(@Param("orderId") Long orderId);
}
