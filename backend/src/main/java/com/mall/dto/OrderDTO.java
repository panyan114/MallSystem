package com.mall.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {

    private Long userId;
    private Long addressId;
    private String address;
    private String receiver;
    private String phone;
    private String remark;
    /**
     * 要使用的优惠券——注意是 {@code t_user_coupon.id}（用户手里那张券），不是 {@code t_coupon.id}。
     * 结算页从「我的优惠券」里选，拿到的就是用户券 id。两者语义不同，不要混用。
     */
    private Long userCouponId;
    private List<OrderItemDTO> items;
    private Boolean clearCart;
}
