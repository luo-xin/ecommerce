package com.ecommerce.product.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.product.entity.Category;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId} AND status != 'DELETED'")
    int countProductsByCategoryId(@Param("categoryId") Long categoryId);
}
