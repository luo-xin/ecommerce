package com.ecommerce.user.service;

import com.ecommerce.user.dto.*;
import java.util.List;

public interface UserService {
    Object register(RegisterReq req);
    LoginResp login(LoginReq req);
    void logout(String token);
    void changePassword(ChangePasswordReq req);
    UserInfoResp getMe();
    List<AddressResp> getAddresses();
    Long addAddress(AddressReq req);
    void updateAddress(Long addressId, AddressReq req);
    void deleteAddress(Long addressId);
    void setDefaultAddress(Long addressId);
}
