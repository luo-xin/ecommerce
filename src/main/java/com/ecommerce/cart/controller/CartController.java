package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public Result<?> addItem(@Valid @RequestBody AddCartItemReq req) {
        cartService.addItem(req);
        return Result.success();
    }

    @PutMapping("/items/{productId}")
    public Result<?> updateItem(@PathVariable Long productId, @RequestBody UpdateCartItemReq req) {
        cartService.updateItem(productId, req);
        return Result.success();
    }

    @DeleteMapping("/items/{productId}")
    public Result<?> deleteItem(@PathVariable Long productId) {
        cartService.deleteItem(productId);
        return Result.success();
    }

    @GetMapping
    public Result<CartResp> getCart() {
        return Result.success(cartService.getCart());
    }

    @DeleteMapping
    public Result<?> clearCart() {
        cartService.clearCart();
        return Result.success();
    }
}
