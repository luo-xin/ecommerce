package com.ecommerce.order.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public void createOrder(GrpcCreateOrderReq req, StreamObserver<GrpcCreateOrderResp> responseObserver) {
        try {
            CreateOrderReq serviceReq = new CreateOrderReq();
            serviceReq.setAddressId(req.getAddressId());
            serviceReq.setProductIds(req.getProductIdsList());

            CreateOrderResp resp = orderService.createOrder(serviceReq);
            responseObserver.onNext(GrpcCreateOrderResp.newBuilder()
                    .setOrderId(resp.getOrderId())
                    .setOrderNo(resp.getOrderNo() != null ? resp.getOrderNo() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listOrders(GrpcListOrdersReq req, StreamObserver<GrpcListOrdersResp> responseObserver) {
        try {
            String status = (req.getStatus() == null || req.getStatus().isBlank()) ? null : req.getStatus();
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            PageResult<OrderListItem> result = orderService.listOrders(status, req.getPage(), size);

            List<GrpcOrderSummary> summaries = result.getItems().stream()
                    .map(o -> GrpcOrderSummary.newBuilder()
                            .setId(o.getOrderId())
                            .setOrderNo(o.getOrderNo() != null ? o.getOrderNo() : "")
                            .setStatus(o.getStatus() != null ? o.getStatus() : "")
                            .setTotalAmount(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : "0")
                            .setFirstProductName(o.getFirstProductName() != null ? o.getFirstProductName() : "")
                            .setFirstProductMainImage(o.getFirstProductMainImage() != null ? o.getFirstProductMainImage() : "")
                            .setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcListOrdersResp.newBuilder()
                    .addAllOrders(summaries)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getOrder(GrpcGetOrderReq req, StreamObserver<GrpcOrderDetail> responseObserver) {
        try {
            OrderDetailResp detail = orderService.getOrderDetail(req.getOrderId());

            List<GrpcOrderItemDetail> grpcItems = detail.getItems() != null
                    ? detail.getItems().stream()
                        .map(item -> GrpcOrderItemDetail.newBuilder()
                                .setProductId(item.getProductId())
                                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                                .setPrice(item.getPrice() != null ? item.getPrice().toPlainString() : "0")
                                .setQuantity(item.getQuantity() != null ? item.getQuantity() : 0)
                                .setMainImage(item.getMainImage() != null ? item.getMainImage() : "")
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            String addressSnapshotJson = "";
            if (detail.getAddressSnapshot() != null) {
                try {
                    addressSnapshotJson = objectMapper.writeValueAsString(detail.getAddressSnapshot());
                } catch (JsonProcessingException ex) {
                    log.warn("序列化 AddressSnapshot 失败: {}", ex.getMessage());
                }
            }

            responseObserver.onNext(GrpcOrderDetail.newBuilder()
                    .setId(detail.getOrderId())
                    .setOrderNo(detail.getOrderNo() != null ? detail.getOrderNo() : "")
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .setTotalAmount(detail.getTotalAmount() != null ? detail.getTotalAmount().toPlainString() : "0")
                    .addAllItems(grpcItems)
                    .setAddressSnapshot(addressSnapshotJson)
                    .setCreatedAt(detail.getCreatedAt() != null ? detail.getCreatedAt().toString() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void payOrder(GrpcPayOrderReq req, StreamObserver<Empty> responseObserver) {
        try {
            orderService.payOrder(req.getOrderId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void cancelOrder(GrpcCancelOrderReq req, StreamObserver<Empty> responseObserver) {
        try {
            CancelOrderReq serviceReq = new CancelOrderReq();
            serviceReq.setCancelReason(req.getReason());
            orderService.cancelOrder(req.getOrderId(), serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
