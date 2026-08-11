package com.ecommerce.user.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.user.dto.*;
import com.ecommerce.user.service.UserService;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;

    @Override
    public void register(GrpcRegisterReq req, StreamObserver<GrpcRegisterResp> responseObserver) {
        try {
            RegisterReq serviceReq = new RegisterReq();
            serviceReq.setPhone(req.getPhone());
            serviceReq.setPassword(req.getPassword());
            serviceReq.setConfirmPassword(req.getPassword());
            if (req.getUsername() != null && !req.getUsername().isBlank()) {
                serviceReq.setUsername(req.getUsername());
            }

            Object result = userService.register(serviceReq);
            long userId = 0L;
            if (result instanceof java.util.Map<?, ?> map && map.get("userId") instanceof Number n) {
                userId = n.longValue();
            } else if (result instanceof Number n) {
                userId = n.longValue();
            }
            responseObserver.onNext(GrpcRegisterResp.newBuilder().setUserId(userId).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void login(GrpcLoginReq req, StreamObserver<GrpcLoginResp> responseObserver) {
        try {
            LoginReq serviceReq = new LoginReq();
            serviceReq.setPhone(req.getPhone());
            serviceReq.setPassword(req.getPassword());

            LoginResp resp = userService.login(serviceReq);
            if (resp == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("登录失败").asRuntimeException());
                return;
            }
            responseObserver.onNext(GrpcLoginResp.newBuilder()
                    .setToken(resp.getToken() != null ? resp.getToken() : "")
                    .setUserId(resp.getUserId() != null ? resp.getUserId() : 0L)
                    .setRole(resp.getRole() != null ? resp.getRole() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProfile(Empty req, StreamObserver<GrpcUserInfo> responseObserver) {
        try {
            UserInfoResp info = userService.getMe();
            if (info == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException());
                return;
            }
            responseObserver.onNext(GrpcUserInfo.newBuilder()
                    .setId(info.getUserId() != null ? info.getUserId() : 0L)
                    .setPhone(info.getPhone() != null ? info.getPhone() : "")
                    .setUsername(info.getUsername() != null ? info.getUsername() : "")
                    .setRole(info.getRole() != null ? info.getRole() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listAddresses(Empty req, StreamObserver<GrpcAddressListResp> responseObserver) {
        try {
            List<AddressResp> addresses = userService.getAddresses();
            List<GrpcAddressResp> grpcAddresses = addresses.stream()
                    .map(a -> GrpcAddressResp.newBuilder()
                            .setId(a.getAddressId() != null ? a.getAddressId() : 0L)
                            .setReceiverName(a.getReceiverName() != null ? a.getReceiverName() : "")
                            .setReceiverPhone(a.getReceiverPhone() != null ? a.getReceiverPhone() : "")
                            .setProvince(a.getProvince() != null ? a.getProvince() : "")
                            .setCity(a.getCity() != null ? a.getCity() : "")
                            .setDistrict(a.getDistrict() != null ? a.getDistrict() : "")
                            .setDetail(a.getDetail() != null ? a.getDetail() : "")
                            .setIsDefault(Boolean.TRUE.equals(a.getIsDefault()))
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(GrpcAddressListResp.newBuilder()
                    .addAllAddresses(grpcAddresses).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void addAddress(GrpcAddressReq req, StreamObserver<GrpcAddressResp> responseObserver) {
        try {
            AddressReq serviceReq = new AddressReq();
            serviceReq.setReceiverName(req.getReceiverName());
            serviceReq.setReceiverPhone(req.getReceiverPhone());
            serviceReq.setProvince(req.getProvince());
            serviceReq.setCity(req.getCity());
            serviceReq.setDistrict(req.getDistrict());
            serviceReq.setDetail(req.getDetail());
            serviceReq.setIsDefault(req.getIsDefault());

            Long addressId = userService.addAddress(serviceReq);
            responseObserver.onNext(GrpcAddressResp.newBuilder()
                    .setId(addressId != null ? addressId : 0L)
                    .setReceiverName(req.getReceiverName())
                    .setReceiverPhone(req.getReceiverPhone())
                    .setProvince(req.getProvince())
                    .setCity(req.getCity())
                    .setDistrict(req.getDistrict())
                    .setDetail(req.getDetail())
                    .setIsDefault(req.getIsDefault())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteAddress(GrpcDeleteAddressReq req, StreamObserver<Empty> responseObserver) {
        try {
            userService.deleteAddress(req.getAddressId());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
