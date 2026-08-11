package com.ecommerce.cart.grpc;

import com.ecommerce.cart.dto.*;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.BusinessException;
import com.ecommerce.grpc.proto.*;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class CartGrpcService extends CartServiceGrpc.CartServiceImplBase {

    private final CartService cartService;

    @Override
    public void getCart(Empty req, StreamObserver<GrpcCartResp> responseObserver) {
        try {
            CartResp cart = cartService.getCart();
            List<GrpcCartItemResp> grpcItems = cart.getItems() != null
                    ? cart.getItems().stream()
                        .map(item -> GrpcCartItemResp.newBuilder()
                                .setProductId(item.getProductId())
                                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                                .setPrice(item.getPrice() != null ? item.getPrice().toPlainString() : "0")
                                .setQuantity(item.getQuantity() != null ? item.getQuantity() : 0)
                                .setMainImage(item.getMainImage() != null ? item.getMainImage() : "")
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            int totalCount = grpcItems.stream().mapToInt(GrpcCartItemResp::getQuantity).sum();

            responseObserver.onNext(GrpcCartResp.newBuilder()
                    .addAllItems(grpcItems)
                    .setTotalCount(totalCount)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void addItem(GrpcAddCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            AddCartItemReq serviceReq = new AddCartItemReq();
            serviceReq.setProductId(req.getProductId());
            serviceReq.setQuantity(req.getQuantity());
            cartService.addItem(serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void updateItem(GrpcUpdateCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            UpdateCartItemReq serviceReq = new UpdateCartItemReq();
            serviceReq.setQuantity(req.getQuantity());
            cartService.updateItem(req.getProductId(), serviceReq);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void removeItem(GrpcRemoveCartItemReq req, StreamObserver<Empty> responseObserver) {
        try {
            cartService.deleteItem(req.getProductId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void clearCart(Empty req, StreamObserver<Empty> responseObserver) {
        try {
            cartService.clearCart();
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
