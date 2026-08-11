package com.ecommerce.order;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.mapper.*;
import com.ecommerce.order.service.impl.OrderServiceImpl;
import com.ecommerce.security.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper orderItemMapper;
    @Mock OrderStatusLogMapper statusLogMapper;
    @InjectMocks OrderServiceImpl orderService;

    @BeforeEach
    void setup() { UserContext.set(new UserContext(1L, "USER", "jti")); }
    @AfterEach
    void teardown() { UserContext.clear(); }

    @Test
    void payOrder_alreadyPaid_throws15008() {
        Order order = new Order();
        order.setId(1L); order.setUserId(1L); order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.payOrder(1L));
        assertEquals(ErrorCode.ORDER_STATUS_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void getOrderDetail_differentUser_throws15007() {
        Order order = new Order();
        order.setId(1L); order.setUserId(999L); order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrderDetail(1L));
        assertEquals(ErrorCode.ORDER_NO_PERMISSION.getCode(), ex.getCode());
    }

    @Test
    void cancelOrder_nonPendingStatus_throws15008() {
        Order order = new Order();
        order.setId(1L); order.setUserId(1L); order.setStatus("PAID");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L, null));
        assertEquals(ErrorCode.ORDER_STATUS_NOT_ALLOWED.getCode(), ex.getCode());
    }
}
