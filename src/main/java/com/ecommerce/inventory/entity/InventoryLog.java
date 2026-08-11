package com.ecommerce.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("inventory_log")
public class InventoryLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String changeType;   // RESTOCK / DEDUCT / ROLLBACK
    private Integer changeQuantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String orderNo;
    private String remark;
    private LocalDateTime createdAt;
}
