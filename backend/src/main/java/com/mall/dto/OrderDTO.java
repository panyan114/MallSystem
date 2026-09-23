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
    private Long couponId;
    private List<OrderItemDTO> items;
    private Boolean clearCart;
}
