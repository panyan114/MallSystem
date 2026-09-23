package com.mall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「我的优惠券」列表项——用户持有的券 + 券本身的信息。
 *
 * <p>{@link #id} 是 {@code t_user_coupon.id}，不是 {@code t_coupon.id}。结算页选券后发给后端的
 * 就是它（{@code OrderDTO.userCouponId}）。两个 id 语义不同，混用会导致用错券。
 */
@Data
public class UserCouponVO {

    /** t_user_coupon.id —— 结算页下单时提交的就是这个值。 */
    private Long id;
    /** t_coupon.id —— 券模板本身。 */
    private Long couponId;

    private String name;
    private Integer type;
    private BigDecimal minAmount;
    private BigDecimal discountValue;

    /**
     * 0=未使用，1=已使用，2=已过期。
     *
     * <p>注意 2 是**读时派生**的：数据库里只会有 0 和 1，没有任何代码会写入 2，也没有定时任务
     * 去刷过期的券。这里在装配 VO 时比较券的 endTime 现算出来，纯属展示层投影，
     * 供前端分 tab 用。真正能不能用由 {@code CouponCalculator.validateForUse} 独立判定。
     */
    private Integer status;

    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    /** 券模板的起止时间，供前端展示「有效期至 …」。 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
