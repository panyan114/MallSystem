package com.mall.dto;

import lombok.Data;

@Data
public class OrderItemDTO {

    private Long productId;
    private Long skuId;
    private Integer quantity;
}
