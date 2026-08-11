package com.ecommerce.user.controller;

import com.ecommerce.common.Result;
import com.ecommerce.user.dto.*;
import com.ecommerce.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/users/register")
    public Result<?> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(userService.register(req));
    }

    @PostMapping("/users/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.success(userService.login(req));
    }

    @PostMapping("/users/logout")
    public Result<?> logout(HttpServletRequest request) {
        userService.logout(request.getHeader("Authorization"));
        return Result.success();
    }

    @PutMapping("/users/password")
    public Result<?> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(req);
        return Result.success();
    }

    @GetMapping("/users/me")
    public Result<UserInfoResp> getMe() {
        return Result.success(userService.getMe());
    }

    @GetMapping("/users/addresses")
    public Result<?> getAddresses() {
        return Result.success(userService.getAddresses());
    }

    @PostMapping("/users/addresses")
    public Result<?> addAddress(@Valid @RequestBody AddressReq req) {
        return Result.success(Map.of("addressId", userService.addAddress(req)));
    }

    @PutMapping("/users/addresses/{addressId}")
    public Result<?> updateAddress(@PathVariable Long addressId, @RequestBody AddressReq req) {
        userService.updateAddress(addressId, req);
        return Result.success();
    }

    @DeleteMapping("/users/addresses/{addressId}")
    public Result<?> deleteAddress(@PathVariable Long addressId) {
        userService.deleteAddress(addressId);
        return Result.success();
    }

    @PutMapping("/users/addresses/{addressId}/default")
    public Result<?> setDefault(@PathVariable Long addressId) {
        userService.setDefaultAddress(addressId);
        return Result.success();
    }
}
