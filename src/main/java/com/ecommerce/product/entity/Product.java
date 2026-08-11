package com.ecommerce.product.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private BigDecimal price;
    private String status;   // ON_SALE / OFF_SALE / DELETED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
