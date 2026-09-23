package com.mall.exception;

public enum ErrorCode {

    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器错误"),
    USER_EXIST(1001, "用户已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OUT_OF_STOCK(2002, "库存不足"),
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_CANNOT_CANCEL(3002, "订单无法取消"),
    COUPON_EXPIRED(4001, "优惠券已过期"),
    COUPON_NOT_AVAILABLE(4002, "优惠券不可用"),
    COUPON_NOT_FOUND(4003, "优惠券不存在"),
    COUPON_ALREADY_RECEIVED(4004, "优惠券已领取"),
    COUPON_SOLD_OUT(4005, "优惠券已领完");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
