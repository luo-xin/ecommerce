package com.ecommerce.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.refund.entity.RefundItem;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RefundItemMapper extends BaseMapper<RefundItem> {
    @Select("SELECT * FROM refund_item WHERE refund_id = #{refundId}")
    List<RefundItem> selectByRefundId(@Param("refundId") Long refundId);
}
