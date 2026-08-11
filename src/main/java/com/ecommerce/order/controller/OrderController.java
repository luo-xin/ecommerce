package com.ecommerce.order.controller;

import com.ecommerce.common.Result;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/orders")
    public Result<CreateOrderResp> createOrder(@Valid @RequestBody CreateOrderReq req) {
        return Result.success(orderService.createOrder(req));
    }

    @GetMapping("/api/orders")
    public Result<?> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.listOrders(status, page, size));
    }

    @GetMapping("/api/orders/{orderId}")
    public Result<OrderDetailResp> getDetail(@PathVariable Long orderId) {
        return Result.success(orderService.getOrderDetail(orderId));
    }

    @PutMapping("/api/orders/{orderId}/cancel")
    public Result<?> cancel(@PathVariable Long orderId, @RequestBody(required = false) CancelOrderReq req) {
        orderService.cancelOrder(orderId, req);
        return Result.success();
    }

    @PutMapping("/api/orders/{orderId}/pay")
    public Result<?> pay(@PathVariable Long orderId) {
        orderService.payOrder(orderId);
        return Result.success();
    }

    @PutMapping("/api/admin/orders/{orderId}/ship")
    public Result<?> ship(@PathVariable Long orderId) {
        orderService.shipOrder(orderId);
        return Result.success();
    }

    @PutMapping("/api/orders/{orderId}/confirm")
    public Result<?> confirm(@PathVariable Long orderId) {
        orderService.confirmOrder(orderId);
        return Result.success();
    }

    @GetMapping("/api/orders/{orderId}/status-logs")
    public Result<?> statusLogs(@PathVariable Long orderId) {
        return Result.success(orderService.getStatusLogs(orderId));
    }

    @GetMapping("/api/admin/orders")
    public Result<?> adminList(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(orderService.adminListOrders(userId, status, page, size));
    }
}
