package com.ecommerce.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.entity.ProductImage;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {
    @Select("SELECT url FROM product_image WHERE product_id = #{productId} AND type = 'MAIN' LIMIT 1")
    String selectMainImageUrl(@Param("productId") Long productId);
}
