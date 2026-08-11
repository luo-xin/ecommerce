package com.ecommerce.order.service;

import com.ecommerce.common.dto.PageResult;
import com.ecommerce.order.dto.*;
import java.util.List;

public interface OrderService {
    CreateOrderResp createOrder(CreateOrderReq req);
    PageResult<OrderListItem> listOrders(String status, int page, int size);
    OrderDetailResp getOrderDetail(Long orderId);
    void cancelOrder(Long orderId, CancelOrderReq req);
    void payOrder(Long orderId);
    void shipOrder(Long orderId);
    void confirmOrder(Long orderId);
    List<StatusLogResp> getStatusLogs(Long orderId);
    PageResult<OrderListItem> adminListOrders(Long userId, String status, int page, int size);

    // Internal: called by RefundService to update order status
    void updateOrderStatus(Long orderId, String fromStatus, String toStatus, String operator, String remark);
}
