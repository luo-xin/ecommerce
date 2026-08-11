package com.ecommerce.refund.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.refund.dto.*;
import com.ecommerce.refund.service.RefundService;
import com.ecommerce.security.UserContext;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class RefundGrpcService extends RefundServiceGrpc.RefundServiceImplBase {

    private final RefundService refundService;

    @Override
    public void applyRefund(GrpcApplyRefundReq req, StreamObserver<GrpcRefundResp> responseObserver) {
        try {
            ApplyRefundReq serviceReq = new ApplyRefundReq();
            serviceReq.setReason(req.getReason());

            ApplyRefundResp resp = refundService.applyRefund(req.getOrderId(), serviceReq);
            responseObserver.onNext(GrpcRefundResp.newBuilder()
                    .setRefundId(resp.getRefundId())
                    .setRefundNo(resp.getRefundNo() != null ? resp.getRefundNo() : "")
                    .setRefundAmount(resp.getRefundAmount() != null ? resp.getRefundAmount().toPlainString() : "0")
                    .setStatus(resp.getStatus() != null ? resp.getStatus() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getRefund(GrpcGetRefundReq req, StreamObserver<GrpcRefundDetailResp> responseObserver) {
        try {
            RefundDetailResp detail = refundService.getRefundDetail(req.getRefundId());
            responseObserver.onNext(GrpcRefundDetailResp.newBuilder()
                    .setRefundId(detail.getRefundId())
                    .setOrderId(detail.getOrderId())
                    .setRefundNo(detail.getRefundNo() != null ? detail.getRefundNo() : "")
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .setRefundAmount(detail.getRefundAmount() != null ? detail.getRefundAmount().toPlainString() : "0")
                    .setReason(detail.getReason() != null ? detail.getReason() : "")
                    .setCreatedAt(detail.getCreatedAt() != null ? detail.getCreatedAt().toString() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listRefunds(GrpcListRefundsReq req, StreamObserver<GrpcListRefundsResp> responseObserver) {
        try {
            String status = (req.getStatus() == null || req.getStatus().isBlank()) ? null : req.getStatus();
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            // Use current user's ID to scope the list (null = admin list all)
            Long currentUserId = UserContext.getUserId();
            PageResult<RefundListItem> result = refundService.adminListRefunds(status, currentUserId, req.getPage(), size);

            List<GrpcRefundDetailResp> grpcRefunds = result.getItems().stream()
                    .map(r -> GrpcRefundDetailResp.newBuilder()
                            .setRefundId(r.getRefundId())
                            .setOrderId(r.getOrderId())
                            .setRefundNo(r.getRefundNo() != null ? r.getRefundNo() : "")
                            .setStatus(r.getStatus() != null ? r.getStatus() : "")
                            .setRefundAmount(r.getRefundAmount() != null ? r.getRefundAmount().toPlainString() : "0")
                            .setReason(r.getReason() != null ? r.getReason() : "")
                            .setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcListRefundsResp.newBuilder()
                    .addAllRefunds(grpcRefunds)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
