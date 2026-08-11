package com.ecommerce.product.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_image")
public class ProductImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String url;
    private String type;   // MAIN / DETAIL
    private Integer sort;
    private LocalDateTime createdAt;
}
