package com.ecommerce.inventory.service;

import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.dto.*;

public interface InventoryService {
    // Public HTTP APIs
    Long initInventory(InitInventoryReq req);
    RestockResp restock(Long productId, RestockReq req);
    StockResp queryStock(Long productId);
    PageResult<InventoryLogItem> getLogs(Long productId, int page, int size);

    // Internal methods (called by ProductService / OrderService / RefundService)
    void initForProduct(Long productId);           // called on product creation
    void deduct(Long productId, int qty, String orderNo);   // atomic Redis DECRBY + MySQL
    void rollback(Long productId, int qty, String orderNo); // REQUIRES_NEW, called on cancel/refund
}
