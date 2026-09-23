package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal realAmount;
    /** 使用的优惠券ID（t_coupon.id），仅用于按券统计。 */
    private Long couponId;
    /** 使用的用户优惠券ID（t_user_coupon.id），取消订单时据此精确退回。 */
    private Long userCouponId;
    /** 优惠金额，未使用优惠券为 0.00。恒有 totalAmount - discountAmount == realAmount。 */
    private BigDecimal discountAmount;
    private Integer status;
    private String address;
    private String receiver;
    private String phone;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime confirmTime;
    @TableLogic
    private Integer deleted;
}
