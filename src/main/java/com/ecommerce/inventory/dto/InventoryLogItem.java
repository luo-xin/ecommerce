package com.ecommerce.inventory.dto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class InventoryLogItem {
    private Long logId;
    private Long productId;
    private String changeType;
    private Integer changeQuantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String orderNo;
    private String remark;
    private LocalDateTime createdAt;
}
