package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.*;
import java.util.List;

public interface CartService {
    void addItem(AddCartItemReq req);
    void updateItem(Long productId, UpdateCartItemReq req);
    void deleteItem(Long productId);
    CartResp getCart();
    void clearCart();
    void removeItems(Long userId, List<Long> productIds);  // internal, called by OrderService
}
