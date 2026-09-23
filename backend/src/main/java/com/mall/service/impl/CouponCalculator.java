package com.mall.service.impl;

import com.mall.entity.Coupon;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 优惠券的校验与算价规则。
 *
 * <p>刻意做成不依赖 Spring、不碰数据库的纯函数：{@code AbstractIntegrationTest} 标了
 * {@code disabledWithoutDocker = true}，在没有 Docker 的机器上所有集成测试会被整体跳过，
 * 而算价是本次改动里唯一真正需要逐分验证的逻辑。抽出来才能保证它在任何环境都跑得到。
 */
final class CouponCalculator {

    /** 满减：discountValue 是减免的金额。 */
    static final int TYPE_FULL_REDUCTION = 0;
    /** 折扣：discountValue 是实付比例，0.90 表示付九成。 */
    static final int TYPE_DISCOUNT = 1;

    private static final int STATUS_ENABLED = 1;

    private CouponCalculator() {
    }

    /** 算价结果。二者和订单总额恒满足 {@code discount + payable == total}。 */
    record Discount(BigDecimal discountAmount, BigDecimal payableAmount) {
    }

    /** 领券校验：券启用，且当前时间落在 [startTime, endTime] 内。 */
    static void validateForReceive(Coupon coupon, LocalDateTime now) {
        if (coupon.getStatus() == null || coupon.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(), "优惠券已停用");
        }
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(), "优惠券尚未开始发放");
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
    }

    /** 用券校验：在领券校验之上多一道金额门槛。 */
    static void validateForUse(Coupon coupon, BigDecimal orderAmount, LocalDateTime now) {
        validateForReceive(coupon, now);

        // 折扣券的 discountValue 是实付比例。若存量数据里写成了 10（想表达「打一折」），
        // compute 会算出订单金额的十倍。创建接口会拦，这里再用一次，防止绕过接口写入的脏数据被利用。
        if (isDiscount(coupon)) {
            BigDecimal rate = coupon.getDiscountValue();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(), "优惠券配置有误");
            }
        }

        BigDecimal threshold = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
        // 门槛比较必须用 compareTo：BigDecimal.equals 会把 50.0 和 50.00 判为不相等。
        if (orderAmount.compareTo(threshold) < 0) {
            throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(),
                    "订单金额未满 " + threshold.setScale(2, RoundingMode.HALF_UP) + " 元，无法使用该优惠券");
        }
    }

    /**
     * 算价：先算出「该付多少」，再用总额减出优惠额——不能反过来。
     *
     * <p>必须精确成立的不变式是 {@code totalAmount - discountAmount == realAmount}。
     * 若独立计算折扣再相减，遇到折扣券可能和单独取整的应付额差一分钱。
     */
    static Discount compute(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal total = orderAmount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal payable;
        if (isDiscount(coupon)) {
            payable = total.multiply(coupon.getDiscountValue()).setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal off = coupon.getDiscountValue() == null
                    ? BigDecimal.ZERO
                    : coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
            // 封顶：满减额大于订单金额时只减到 0，不倒贴
            payable = total.subtract(off.min(total));
        }

        payable = payable.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Discount(total.subtract(payable), payable);
    }

    private static boolean isDiscount(Coupon coupon) {
        return coupon.getType() != null && coupon.getType() == TYPE_DISCOUNT;
    }
}
