package com.ecommerce.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Global
    NO_PERMISSION(10002, "无权限操作"),
    // User 11xxx
    INVALID_PHONE(11001, "手机号格式不正确"),
    PHONE_EXISTS(11002, "该手机号已注册"),
    INVALID_PASSWORD_FORMAT(11003, "密码须为8-20位字母数字组合"),
    PASSWORD_NOT_MATCH(11004, "两次输入的密码不一致"),
    LOGIN_FAILED(11005, "账号或密码错误"),
    NOT_LOGGED_IN(11006, "请先登录"),
    TOKEN_EXPIRED(11007, "登录已过期，请重新登录"),
    ADDRESS_LIMIT(11008, "收货地址最多保存5个"),
    ADDRESS_NOT_FOUND(11009, "地址不存在"),
    OLD_PASSWORD_WRONG(11010, "原密码不正确"),
    USER_NOT_FOUND(11011, "用户不存在"),
    // Product 12xxx
    CATEGORY_NAME_EXISTS(12001, "分类名称已存在"),
    PARENT_CATEGORY_NOT_FOUND(12002, "父分类不存在"),
    CATEGORY_NOT_FOUND(12003, "分类不存在"),
    CATEGORY_HAS_CHILDREN(12004, "该分类下有子分类，无法删除"),
    CATEGORY_HAS_PRODUCTS(12005, "该分类下有商品，无法删除"),
    NOT_SECOND_LEVEL_CATEGORY(12006, "必须指定二级分类"),
    PRODUCT_NOT_FOUND(12007, "商品不存在"),
    CANNOT_MODIFY_PRICE_ON_SALE(12008, "已上架商品不允许修改价格"),
    PRODUCT_STATUS_NOT_ALLOWED(12009, "商品当前状态不允许该操作"),
    MUST_OFF_SALE_FIRST(12010, "请先下架商品再删除"),
    PRODUCT_IMAGE_NOT_FOUND(12011, "商品图片不存在"),
    PRODUCT_HAS_ACTIVE_ORDER(12012, "商品存在未完成订单，无法删除"),
    CATEGORY_LEVEL_EXCEEDED(12013, "分类层级最多2级"),
    // Inventory 13xxx
    INVENTORY_NOT_FOUND(13001, "商品库存记录不存在"),
    INVALID_QUANTITY(13002, "操作数量必须大于0"),
    INSUFFICIENT_STOCK(13003, "库存不足"),
    INVENTORY_ALREADY_INITIALIZED(13004, "该商品库存已初始化"),
    // Cart 14xxx
    PRODUCT_NOT_ON_SALE_CART(14001, "商品不存在或已下架"),
    CART_EMPTY(14002, "购物车为空"),
    CART_ITEM_QUANTITY_LIMIT(14003, "单个商品数量不能超过999"),
    PRODUCT_NOT_IN_CART(14004, "指定商品不在购物车中"),
    // Order 15xxx
    ORDER_EMPTY_PRODUCTS(15001, "请至少选择一件商品"),
    ORDER_PRODUCT_UNAVAILABLE(15002, "商品[%s]已下架或不存在"),
    ORDER_PRODUCT_INSUFFICIENT_STOCK(15003, "商品[%s]库存不足"),
    ORDER_ADDRESS_NOT_FOUND(15004, "收货地址不存在"),
    ORDER_ADDRESS_NO_PERMISSION(15005, "无权限使用该收货地址"),
    ORDER_NOT_FOUND(15006, "订单不存在"),
    ORDER_NO_PERMISSION(15007, "无权限操作该订单"),
    ORDER_STATUS_NOT_ALLOWED(15008, "当前订单状态不允许此操作"),
    // Refund 16xxx
    REFUND_ORDER_STATUS_NOT_ALLOWED(16001, "当前订单状态不支持退款"),
    REFUND_ALREADY_APPLYING(16002, "该订单已有退款申请正在处理中"),
    REFUND_NOT_FOUND(16003, "退款单不存在"),
    REFUND_NO_PERMISSION(16004, "无权限操作该退款"),
    REFUND_ALREADY_PROCESSED(16005, "该退款单已处理，无法重复审核"),
    REFUND_PROCESS_FAILED(16006, "退款处理失败，请重试");

    private final int code;
    private final String message;
}
