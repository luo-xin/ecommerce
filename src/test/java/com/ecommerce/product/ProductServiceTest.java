package com.ecommerce.product;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.product.dto.CreateProductReq;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.mapper.*;
import com.ecommerce.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductMapper productMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock ProductImageMapper imageMapper;
    @Mock InventoryService inventoryService;
    @InjectMocks ProductServiceImpl productService;

    @Test
    void createProduct_withFirstLevelCategory_throws12006() {
        Category cat = new Category();
        cat.setId(1L); cat.setParentId(0L);
        when(categoryMapper.selectById(1L)).thenReturn(cat);

        CreateProductReq req = new CreateProductReq();
        req.setCategoryId(1L); req.setName("Test"); req.setPrice(BigDecimal.ONE);

        BusinessException ex = assertThrows(BusinessException.class, () -> productService.createProduct(req));
        assertEquals(ErrorCode.NOT_SECOND_LEVEL_CATEGORY.getCode(), ex.getCode());
        verify(inventoryService, never()).initForProduct(any());
    }

    @Test
    void createProduct_success_callsInventoryInit() {
        Category cat = new Category();
        cat.setId(3L); cat.setParentId(1L);
        when(categoryMapper.selectById(3L)).thenReturn(cat);
        when(productMapper.insert(any(com.ecommerce.product.entity.Product.class))).thenAnswer(inv -> {
            ((com.ecommerce.product.entity.Product) inv.getArgument(0)).setId(100L);
            return 1;
        });

        CreateProductReq req = new CreateProductReq();
        req.setCategoryId(3L); req.setName("iPhone"); req.setPrice(BigDecimal.valueOf(5999));

        Long id = productService.createProduct(req);
        assertEquals(100L, id);
        verify(inventoryService).initForProduct(100L);
    }
}
