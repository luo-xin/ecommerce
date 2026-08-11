package com.ecommerce.refund;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.mapper.*;
import com.ecommerce.refund.entity.Refund;
import com.ecommerce.refund.mapper.*;
import com.ecommerce.refund.service.impl.RefundServiceImpl;
import com.ecommerce.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock RefundMapper refundMapper;
    @Mock RefundItemMapper refundItemMapper;
    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper orderItemMapper;
    @Mock OrderStatusLogMapper statusLogMapper;
    @Mock com.ecommerce.inventory.service.InventoryService inventoryService;
    @InjectMocks RefundServiceImpl refundService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.ecommerce.refund");
        TableInfoHelper.initTableInfo(assistant, Refund.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @BeforeEach
    void setup() { UserContext.set(new UserContext(1L, "ADMIN", "jti")); }
    @AfterEach
    void teardown() { UserContext.clear(); }

    @Test
    void approveRefund_alreadyProcessed_throwsCorrectError() {
        Refund refund = new Refund();
        refund.setId(1L); refund.setStatus("COMPLETED");
        when(refundMapper.selectById(1L)).thenReturn(refund);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refundService.approveRefund(1L));
        assertEquals(ErrorCode.REFUND_ALREADY_PROCESSED.getCode(), ex.getCode());
    }

    @Test
    void rejectRefund_notFound_throwsCorrectError() {
        when(refundMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refundService.rejectRefund(999L, null));
        assertEquals(ErrorCode.REFUND_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void applyRefund_orderNotPaid_throwsCorrectError() {
        Order order = new Order();
        order.setId(1L); order.setUserId(1L); order.setStatus("COMPLETED");
        order.setTotalAmount(BigDecimal.valueOf(100));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        com.ecommerce.refund.dto.ApplyRefundReq req = new com.ecommerce.refund.dto.ApplyRefundReq();
        req.setReason("质量问题");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refundService.applyRefund(1L, req));
        assertEquals(ErrorCode.REFUND_ORDER_STATUS_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void approveRefund_success_callsInventoryRollbackPerItem() {
        Refund refund = new Refund();
        refund.setId(1L); refund.setStatus("APPLYING");
        refund.setOrderId(10L); refund.setRefundNo("RF20260101000000001");
        when(refundMapper.selectById(1L)).thenReturn(refund);

        // approveRefund updates refund → COMPLETED
        when(refundMapper.update(any(), any())).thenReturn(1);
        // approveRefund updates order → REFUNDED (guard WHERE status=REFUNDING)
        when(orderMapper.update(any(), any())).thenReturn(1);

        // Return 2 refund items
        com.ecommerce.refund.entity.RefundItem item1 = new com.ecommerce.refund.entity.RefundItem();
        item1.setProductId(101L); item1.setQuantity(2);
        com.ecommerce.refund.entity.RefundItem item2 = new com.ecommerce.refund.entity.RefundItem();
        item2.setProductId(102L); item2.setQuantity(1);
        when(refundItemMapper.selectByRefundId(1L)).thenReturn(List.of(item1, item2));

        // Should not throw
        assertDoesNotThrow(() -> refundService.approveRefund(1L));

        // inventoryService.rollback called once per item
        verify(inventoryService).rollback(eq(101L), eq(2), eq("RF20260101000000001"));
        verify(inventoryService).rollback(eq(102L), eq(1), eq("RF20260101000000001"));
    }
}
