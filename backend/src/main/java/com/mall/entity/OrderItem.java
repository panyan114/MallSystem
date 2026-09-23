package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String productImage;
    private String skuDesc;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalPrice;
}
