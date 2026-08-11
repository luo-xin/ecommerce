package com.ecommerce.refund.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_item")
public class RefundItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long refundId;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private LocalDateTime createdAt;
}
