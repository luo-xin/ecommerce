package com.ecommerce.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.security.UserContext;
import com.ecommerce.user.dto.*;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.entity.UserAddress;
import com.ecommerce.user.mapper.UserAddressMapper;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern PWD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$");

    private final UserMapper userMapper;
    private final UserAddressMapper addressMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> register(RegisterReq req) {
        if (!PHONE_PATTERN.matcher(req.getPhone()).matches())
            throw new BusinessException(ErrorCode.INVALID_PHONE);
        if (!PWD_PATTERN.matcher(req.getPassword()).matches())
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        if (!req.getPassword().equals(req.getConfirmPassword()))
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        if (userMapper.selectByPhone(req.getPhone()) != null)
            throw new BusinessException(ErrorCode.PHONE_EXISTS);

        User user = new User();
        user.setPhone(req.getPhone());
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setPasswordVersion(1);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        // Mask phone: 138****5678
        String maskedPhone = req.getPhone().substring(0, 3) + "****" + req.getPhone().substring(7);
        return Map.of("userId", user.getId(), "username",
                user.getUsername() != null ? user.getUsername() : "", "phone", maskedPhone);
    }

    @Override
    public LoginResp login(LoginReq req) {
        User user = userMapper.selectByPhone(req.getPhone());
        if (user == null || user.getStatus() != 1 || !passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new BusinessException(ErrorCode.LOGIN_FAILED);

        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getPasswordVersion());
        return LoginResp.builder()
                .token(token).userId(user.getId())
                .username(user.getUsername()).role(user.getRole())
                .build();
    }

    @Override
    public void logout(String rawToken) {
        // Prefer JTI from UserContext (already validated by JwtAuthFilter)
        UserContext ctx = UserContext.get();
        if (ctx != null && ctx.getJti() != null) {
            String token = rawToken != null && rawToken.startsWith("Bearer ") ? rawToken.substring(7) : rawToken;
            if (token == null) return;
            try {
                Claims claims = jwtUtil.parseToken(token);
                long ttl = jwtUtil.getRemainingSeconds(claims);
                if (ttl > 0) {
                    redisTemplate.opsForValue().set(
                            "token:blacklist:" + ctx.getJti(), "1", ttl, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) { }
        }
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        if (!PWD_PATTERN.matcher(req.getNewPassword()).matches())
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        if (!req.getNewPassword().equals(req.getConfirmPassword()))
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);

        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword()))
            throw new BusinessException(ErrorCode.OLD_PASSWORD_WRONG);

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(req.getNewPassword()))
                .set(User::getPasswordVersion, user.getPasswordVersion() + 1));
    }

    @Override
    public UserInfoResp getMe() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        String masked = user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7);
        return UserInfoResp.builder()
                .userId(user.getId()).phone(masked).username(user.getUsername())
                .role(user.getRole()).createdAt(user.getCreatedAt()).build();
    }

    @Override
    public List<AddressResp> getAddresses() {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, UserContext.getUserId())
                .orderByDesc(UserAddress::getIsDefault)
                .orderByAsc(UserAddress::getCreatedAt))
                .stream().map(this::toAddressResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long addAddress(AddressReq req) {
        Long userId = UserContext.getUserId();
        if (addressMapper.countByUserIdForUpdate(userId) >= 5)
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT);

        // If new address is default, clear existing defaults
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId).set(UserAddress::getIsDefault, false));
        }
        UserAddress addr = buildAddress(req, userId);
        addressMapper.insert(addr);
        return addr.getId();
    }

    @Override
    @Transactional
    public void updateAddress(Long addressId, AddressReq req) {
        UserAddress addr = getOwnedAddress(addressId);
        Long userId = UserContext.getUserId();
        if (req.getReceiverName() != null) addr.setReceiverName(req.getReceiverName());
        if (req.getReceiverPhone() != null) addr.setReceiverPhone(req.getReceiverPhone());
        if (req.getProvince() != null) addr.setProvince(req.getProvince());
        if (req.getCity() != null) addr.setCity(req.getCity());
        if (req.getDistrict() != null) addr.setDistrict(req.getDistrict());
        if (req.getDetail() != null) addr.setDetail(req.getDetail());
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId).set(UserAddress::getIsDefault, false));
            addr.setIsDefault(true);
        }
        addressMapper.updateById(addr);
    }

    @Override
    @Transactional
    public void deleteAddress(Long addressId) {
        getOwnedAddress(addressId);
        addressMapper.deleteById(addressId);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long addressId) {
        Long userId = UserContext.getUserId();
        getOwnedAddress(addressId);
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId).set(UserAddress::getIsDefault, false));
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId).set(UserAddress::getIsDefault, true));
    }

    private UserAddress getOwnedAddress(Long addressId) {
        UserAddress addr = addressMapper.selectById(addressId);
        if (addr == null) throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        if (!addr.getUserId().equals(UserContext.getUserId()))
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        return addr;
    }

    private UserAddress buildAddress(AddressReq req, Long userId) {
        UserAddress a = new UserAddress();
        a.setUserId(userId);
        a.setReceiverName(req.getReceiverName());
        a.setReceiverPhone(req.getReceiverPhone());
        a.setProvince(req.getProvince());
        a.setCity(req.getCity());
        a.setDistrict(req.getDistrict());
        a.setDetail(req.getDetail());
        a.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        return a;
    }

    private AddressResp toAddressResp(UserAddress a) {
        return AddressResp.builder()
                .addressId(a.getId()).receiverName(a.getReceiverName())
                .receiverPhone(a.getReceiverPhone()).province(a.getProvince())
                .city(a.getCity()).district(a.getDistrict()).detail(a.getDetail())
                .isDefault(a.getIsDefault()).build();
    }
}
