package com.ecommerce.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductImageMapper;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final long CART_TTL = 604800L;

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;

    private String cartKey() {
        return "cart:" + UserContext.getUserId();
    }

    private void resetTtl(String key) {
        Long size = redisTemplate.opsForHash().size(key);
        if (size != null && size > 0) {
            redisTemplate.expire(key, CART_TTL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void addItem(AddCartItemReq req) {
        // Validate product is ON_SALE
        Product p = productMapper.selectById(req.getProductId());
        if (p == null || !"ON_SALE".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE_CART);
        if (req.getQuantity() == null || req.getQuantity() < 1)
            throw new BusinessException(ErrorCode.CART_ITEM_QUANTITY_LIMIT);

        String key = cartKey();
        String field = String.valueOf(req.getProductId());
        String existing = (String) redisTemplate.opsForHash().get(key, field);
        int currentQty = existing != null ? Integer.parseInt(existing) : 0;
        int newQty = currentQty + req.getQuantity();

        if (newQty > 999) throw new BusinessException(ErrorCode.CART_ITEM_QUANTITY_LIMIT);

        redisTemplate.opsForHash().put(key, field, String.valueOf(newQty));
        redisTemplate.expire(key, CART_TTL, TimeUnit.SECONDS);
    }

    @Override
    public void updateItem(Long productId, UpdateCartItemReq req) {
        String key = cartKey();
        String field = String.valueOf(productId);

        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            redisTemplate.opsForHash().delete(key, field);
            resetTtl(key);
            return;
        }
        if (req.getQuantity() > 999) throw new BusinessException(ErrorCode.CART_ITEM_QUANTITY_LIMIT);

        redisTemplate.opsForHash().put(key, field, String.valueOf(req.getQuantity()));
        redisTemplate.expire(key, CART_TTL, TimeUnit.SECONDS);
    }

    @Override
    public void deleteItem(Long productId) {
        String key = cartKey();
        redisTemplate.opsForHash().delete(key, String.valueOf(productId));
        resetTtl(key);
    }

    @Override
    public CartResp getCart() {
        String key = cartKey();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return CartResp.builder().items(Collections.emptyList())
                    .totalAmount(BigDecimal.ZERO).build();
        }

        List<Long> productIds = entries.keySet().stream()
                .map(k -> Long.parseLong((String) k)).collect(Collectors.toList());
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().in(Product::getId, productIds));
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<CartItemResp> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            Long pid = Long.parseLong((String) e.getKey());
            int qty = Integer.parseInt((String) e.getValue());
            Product p = productMap.get(pid);
            if (p == null) continue;

            BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP);
            items.add(CartItemResp.builder()
                    .productId(pid).productName(p.getName()).price(p.getPrice())
                    .status(p.getStatus()).quantity(qty).subtotal(subtotal)
                    .mainImage(imageMapper.selectMainImageUrl(pid)).build());

            if ("ON_SALE".equals(p.getStatus())) total = total.add(subtotal);
        }

        resetTtl(key);
        return CartResp.builder().items(items).totalAmount(total.setScale(2, java.math.RoundingMode.HALF_UP)).build();
    }

    @Override
    public void clearCart() {
        redisTemplate.delete(cartKey());
    }

    @Override
    public void removeItems(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        String key = "cart:" + userId;
        Object[] fields = productIds.stream().map(String::valueOf).toArray();
        redisTemplate.opsForHash().delete(key, fields);
        resetTtl(key);
    }
}
