package com.ecommerce.inventory.controller;

import com.ecommerce.common.Result;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // Admin: initialize stock (POST /api/admin/inventory)
    @PostMapping("/api/admin/inventory")
    public Result<?> initInventory(@RequestBody InitInventoryReq req) {
        return Result.success(Map.of("inventoryId", inventoryService.initInventory(req)));
    }

    // Admin: restock (PUT /api/admin/inventory/{productId}/restock)
    @PutMapping("/api/admin/inventory/{productId}/restock")
    public Result<RestockResp> restock(@PathVariable Long productId, @RequestBody RestockReq req) {
        return Result.success(inventoryService.restock(productId, req));
    }

    // Public: query stock (GET /api/products/{productId}/inventory)
    @GetMapping("/api/products/{productId}/inventory")
    public Result<StockResp> queryStock(@PathVariable Long productId) {
        return Result.success(inventoryService.queryStock(productId));
    }

    // Admin: query logs (GET /api/admin/inventory/{productId}/logs)
    @GetMapping("/api/admin/inventory/{productId}/logs")
    public Result<PageResult<InventoryLogItem>> getLogs(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(inventoryService.getLogs(productId, page, size));
    }
}
