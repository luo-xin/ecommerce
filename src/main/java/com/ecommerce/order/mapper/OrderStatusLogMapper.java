package com.ecommerce.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {
    @Select("SELECT * FROM order_status_log WHERE order_id = #{orderId} ORDER BY created_at ASC")
    List<OrderStatusLog> selectByOrderId(@Param("orderId") Long orderId);
}
