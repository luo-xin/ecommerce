package com.ecommerce.cart;

import com.ecommerce.cart.dto.AddCartItemReq;
import com.ecommerce.cart.service.impl.CartServiceImpl;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductImageMapper;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.security.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock HashOperations<String, Object, Object> hashOps;
    @Mock ProductMapper productMapper;
    @Mock ProductImageMapper imageMapper;
    @InjectMocks CartServiceImpl cartService;

    @BeforeEach
    void setup() {
        UserContext.set(new UserContext(1L, "USER", "jti-test"));
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
    }

    @AfterEach
    void teardown() { UserContext.clear(); }

    @Test
    void addItem_productNotOnSale_throws14001() {
        Product p = new Product(); p.setStatus("OFF_SALE");
        when(productMapper.selectById(101L)).thenReturn(p);

        AddCartItemReq req = new AddCartItemReq();
        req.setProductId(101L); req.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> cartService.addItem(req));
        assertEquals(ErrorCode.PRODUCT_NOT_ON_SALE_CART.getCode(), ex.getCode());
    }

    @Test
    void addItem_exceedsLimit_throws14003() {
        Product p = new Product(); p.setStatus("ON_SALE"); p.setPrice(BigDecimal.ONE);
        when(productMapper.selectById(101L)).thenReturn(p);
        when(hashOps.get("cart:1", "101")).thenReturn("990");

        AddCartItemReq req = new AddCartItemReq();
        req.setProductId(101L); req.setQuantity(20);

        BusinessException ex = assertThrows(BusinessException.class, () -> cartService.addItem(req));
        assertEquals(ErrorCode.CART_ITEM_QUANTITY_LIMIT.getCode(), ex.getCode());
    }

    @Test
    void addItem_success_setsHashAndExpire() {
        Product p = new Product(); p.setStatus("ON_SALE"); p.setPrice(BigDecimal.ONE);
        when(productMapper.selectById(101L)).thenReturn(p);
        when(hashOps.get("cart:1", "101")).thenReturn(null);

        AddCartItemReq req = new AddCartItemReq();
        req.setProductId(101L); req.setQuantity(2);

        assertDoesNotThrow(() -> cartService.addItem(req));
        verify(hashOps).put("cart:1", "101", "2");
        verify(redisTemplate).expire(eq("cart:1"), eq(604800L), any());
    }

    @Test
    void removeItems_deletesCorrectFields() {
        lenient().when(hashOps.size("cart:1")).thenReturn(0L);

        cartService.removeItems(1L, List.of(101L, 102L));

        verify(hashOps).delete(eq("cart:1"), eq("101"), eq("102"));
    }
}
