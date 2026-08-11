package com.ecommerce.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.entity.Product;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT COUNT(*) FROM orders o JOIN order_item oi ON o.id = oi.order_id " +
            "WHERE oi.product_id = #{productId} AND o.status IN " +
            "('PENDING_PAYMENT','PAID','SHIPPED','REFUNDING')")
    int countActiveOrders(@Param("productId") Long productId);
}
