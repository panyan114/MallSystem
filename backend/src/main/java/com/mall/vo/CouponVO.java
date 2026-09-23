package com.mall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面向消费者的可领取优惠券视图。
 *
 * <p>比 {@code Coupon} 实体多两个字段：当前用户是否已领取（前端据此把按钮置灰成「已领取」），
 * 以及剩余数量。
 */
@Data
public class CouponVO {

    private Long id;
    private String name;
    /** 0=满减，1=折扣。 */
    private Integer type;
    private BigDecimal minAmount;
    /** 满减时为减免金额，折扣时为实付比例（0.9 表示九折）。 */
    private BigDecimal discountValue;
    private Integer totalCount;
    private Integer receivedCount;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 当前用户是否已领取过这张券。 */
    private Boolean received;
    /** 剩余可领数量。 */
    private Integer remainCount;
}
