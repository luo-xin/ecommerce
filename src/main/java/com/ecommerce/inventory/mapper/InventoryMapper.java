package com.ecommerce.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.inventory.entity.Inventory;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
    @Select("SELECT * FROM inventory WHERE product_id = #{productId}")
    Inventory selectByProductId(@Param("productId") Long productId);

    @Update("UPDATE inventory SET available_stock = available_stock + #{delta}, " +
            "total_stock = total_stock + #{delta} WHERE product_id = #{productId}")
    int addStock(@Param("productId") Long productId, @Param("delta") int delta);

    @Update("UPDATE inventory SET available_stock = available_stock - #{qty} " +
            "WHERE product_id = #{productId} AND available_stock >= #{qty}")
    int deductMysql(@Param("productId") Long productId, @Param("qty") int qty);

    @Update("UPDATE inventory SET available_stock = available_stock + #{qty} " +
            "WHERE product_id = #{productId}")
    int rollbackMysql(@Param("productId") Long productId, @Param("qty") int qty);
}
