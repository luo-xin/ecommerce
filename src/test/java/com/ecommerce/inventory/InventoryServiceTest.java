package com.ecommerce.inventory;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.mapper.InventoryLogMapper;
import com.ecommerce.inventory.mapper.InventoryMapper;
import com.ecommerce.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ecommerce.inventory.entity.InventoryLog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryMapper inventoryMapper;
    @Mock InventoryLogMapper logMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks InventoryServiceImpl inventoryService;

    @Test
    void deduct_insufficientStock_throwsException() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.decrement("inventory:available:1", 10L)).thenReturn(-5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.deduct(1L, 10, "ORDER123"));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK.getCode(), ex.getCode());
        verify(valueOps).increment("inventory:available:1", 10L); // rollback called
    }

    @Test
    void deduct_success_updatesRedisAndMysql() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.decrement("inventory:available:1", 5L)).thenReturn(5L);
        when(inventoryMapper.deductMysql(1L, 5)).thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.deduct(1L, 5, "ORDER123"));
        verify(inventoryMapper).deductMysql(1L, 5);
        verify(valueOps).decrement("inventory:available:1", 5L);
        verify(logMapper).insert(any(InventoryLog.class));
    }

    @Test
    void rollback_updatesRedisAndMysql() {
        Inventory inv = new Inventory();
        inv.setAvailableStock(10);
        when(inventoryMapper.selectByProductId(1L)).thenReturn(inv);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(inventoryMapper.rollbackMysql(1L, 5)).thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.rollback(1L, 5, "ORDER123"));
        verify(valueOps).increment("inventory:available:1", 5L);
        verify(logMapper).insert(any(InventoryLog.class));
    }
}
