package com.ecommerce.inventory.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void getStock(GrpcGetStockReq req, StreamObserver<GrpcStockResp> responseObserver) {
        try {
            StockResp stock = inventoryService.queryStock(req.getProductId());
            responseObserver.onNext(GrpcStockResp.newBuilder()
                    .setProductId(stock.getProductId())
                    .setAvailable(stock.getAvailableStock() != null ? stock.getAvailableStock() : 0)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void restock(GrpcRestockReq req, StreamObserver<GrpcRestockResp> responseObserver) {
        try {
            RestockReq serviceReq = new RestockReq();
            serviceReq.setQuantity(req.getQuantity());
            serviceReq.setRemark((req.getRemark() == null || req.getRemark().isBlank()) ? null : req.getRemark());

            RestockResp resp = inventoryService.restock(req.getProductId(), serviceReq);
            responseObserver.onNext(GrpcRestockResp.newBuilder()
                    .setBeforeStock(resp.getBeforeStock() != null ? resp.getBeforeStock() : 0)
                    .setAfterStock(resp.getAfterStock() != null ? resp.getAfterStock() : 0)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getInventoryLogs(GrpcGetLogsReq req, StreamObserver<GrpcInventoryLogsResp> responseObserver) {
        try {
            int size = req.getSize() == 0 ? 10 : Math.min(req.getSize(), 100);
            PageResult<InventoryLogItem> result = inventoryService.getLogs(req.getProductId(), req.getPage(), size);

            List<GrpcInventoryLogItem> grpcLogs = result.getItems().stream()
                    .map(logItem -> GrpcInventoryLogItem.newBuilder()
                            .setId(logItem.getLogId() != null ? logItem.getLogId() : 0L)
                            .setChangeType(logItem.getChangeType() != null ? logItem.getChangeType() : "")
                            .setChangeQuantity(logItem.getChangeQuantity() != null ? logItem.getChangeQuantity() : 0)
                            .setCreatedAt(logItem.getCreatedAt() != null ? logItem.getCreatedAt().toString() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcInventoryLogsResp.newBuilder()
                    .addAllLogs(grpcLogs)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
