package com.mall.service.impl;

import com.mall.entity.Coupon;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 优惠券算价规则的单元测试。
 *
 * <p>这里没有 Spring、没有容器——{@code CouponCalculator} 是纯函数，直接调比启动上下文快得多，
 * 更重要的是它能在没有 Docker 的机器上跑。集成测试全都标了
 * {@code disabledWithoutDocker = true}，会被静默跳过；算价恰恰是最需要逐分验证的部分。
 */
@DisplayName("CouponCalculator 优惠券算价")
class CouponCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 12, 0);

    // ---- 折扣券 ----

    @Test
    @DisplayName("9 折券打 77.90：应付 70.11，优惠 7.79")
    void discountAppliesRounding() {
        // 77.90 × 0.90 = 70.110 —— 必须按 HALF_UP 收敛到分，否则会算出 70.11 之外的值
        CouponCalculator.Discount result = CouponCalculator.compute(discountCoupon("0.90"), new BigDecimal("77.90"));

        assertThat(result.payableAmount()).isEqualByComparingTo("70.11");
        assertThat(result.discountAmount()).isEqualByComparingTo("7.79");
    }

    @Test
    @DisplayName("9 折券打 19.90：应付 17.91，优惠 1.99")
    void discountRoundsHalfUp() {
        // 19.90 × 0.90 = 17.910
        CouponCalculator.Discount result = CouponCalculator.compute(discountCoupon("0.90"), new BigDecimal("19.90"));

        assertThat(result.payableAmount()).isEqualByComparingTo("17.91");
        assertThat(result.discountAmount()).isEqualByComparingTo("1.99");
    }

    @Test
    @DisplayName("折扣算出来的优惠额与应付额之和恒等于订单总额")
    void discountPartsSumToTotal() {
        Coupon coupon = discountCoupon("0.88");
        for (String amount : new String[]{"0.01", "9.99", "33.33", "77.90", "1234.56"}) {
            BigDecimal total = new BigDecimal(amount);
            CouponCalculator.Discount result = CouponCalculator.compute(coupon, total);

            assertThat(result.discountAmount().add(result.payableAmount()))
                    .as("总额 %s 时 优惠+应付 必须精确等于总额", amount)
                    .isEqualByComparingTo(total);
        }
    }

    // ---- 满减券 ----

    @Test
    @DisplayName("满 50 减 10 打 77.90：应付 67.90")
    void fullReductionSubtracts() {
        CouponCalculator.Discount result = CouponCalculator.compute(fullReduction("50.00", "10.00"), new BigDecimal("77.90"));

        assertThat(result.payableAmount()).isEqualByComparingTo("67.90");
        assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("满减额超过订单金额时封顶到 0，不倒贴")
    void fullReductionIsCappedAtTotal() {
        CouponCalculator.Discount result = CouponCalculator.compute(fullReduction("50.00", "10.00"), new BigDecimal("3.00"));

        assertThat(result.payableAmount()).isEqualByComparingTo("0.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("3.00");
    }

    // ---- 金额门槛 ----

    @Test
    @DisplayName("订单金额恰好等于门槛时可用（边界值取等号）")
    void acceptsAmountExactlyAtThreshold() {
        Coupon coupon = fullReduction("50.00", "10.00");

        assertThatCode(() -> CouponCalculator.validateForUse(coupon, new BigDecimal("50.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("门槛比较走 compareTo：scale 不同但数值相等（50.0 vs 50.00）不能判为不等")
    void thresholdComparisonIgnoresScale() {
        // 这条是防回归：BigDecimal.equals 会把 50.0 和 50.00 判为不相等，
        // 一旦有人把 compareTo 改成 equals，恰好卡在门槛上的订单会被错误拒绝。
        Coupon coupon = fullReduction("50.0", "10.00");

        assertThatCode(() -> CouponCalculator.validateForUse(coupon, new BigDecimal("50.00"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("差一分钱没到门槛就不能用")
    void rejectsAmountBelowThreshold() {
        Coupon coupon = fullReduction("50.00", "10.00");

        BusinessException error = catchThrowableOfType(
                () -> CouponCalculator.validateForUse(coupon, new BigDecimal("49.99"), NOW),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
        assertThat(error.getMessage()).contains("50.00");
    }

    @Test
    @DisplayName("minAmount 为 null 视为无门槛")
    void nullThresholdMeansNoMinimum() {
        Coupon coupon = discountCoupon("0.90");
        coupon.setMinAmount(null);

        assertThatCode(() -> CouponCalculator.validateForUse(coupon, new BigDecimal("0.01"), NOW))
                .doesNotThrowAnyException();
    }

    // ---- 有效性 ----

    @Test
    @DisplayName("已过期的券报 COUPON_EXPIRED")
    void rejectsExpiredCoupon() {
        Coupon coupon = discountCoupon("0.90");
        coupon.setEndTime(NOW.minusSeconds(1));

        BusinessException error = catchThrowableOfType(
                () -> CouponCalculator.validateForUse(coupon, new BigDecimal("100.00"), NOW),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_EXPIRED.getCode());
    }

    @Test
    @DisplayName("还没到开始时间的券不能用")
    void rejectsNotYetStartedCoupon() {
        Coupon coupon = discountCoupon("0.90");
        coupon.setStartTime(NOW.plusSeconds(1));

        BusinessException error = catchThrowableOfType(
                () -> CouponCalculator.validateForUse(coupon, new BigDecimal("100.00"), NOW),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    @DisplayName("已停用的券不能用")
    void rejectsDisabledCoupon() {
        Coupon coupon = discountCoupon("0.90");
        coupon.setStatus(0);

        BusinessException error = catchThrowableOfType(
                () -> CouponCalculator.validateForUse(coupon, new BigDecimal("100.00"), NOW),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    @DisplayName("折扣比例 >= 1 的脏数据在使用时被拦下，不会算出天价订单")
    void rejectsDiscountRateAtOrAboveOne() {
        // 若 discountValue 被写成 10（想表达「打一折」），compute 会算出订单金额的十倍。
        // 创建接口会拦，但绕过接口写入的历史数据必须在使用时也拦得住。
        for (String bad : new String[]{"1", "1.00", "10", "0", "-0.5"}) {
            Coupon coupon = discountCoupon(bad);

            BusinessException error = catchThrowableOfType(
                    () -> CouponCalculator.validateForUse(coupon, new BigDecimal("100.00"), NOW),
                    BusinessException.class);

            assertThat(error).as("折扣比例 %s 应被拒绝", bad).isNotNull();
            assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
        }
    }

    @Test
    @DisplayName("时间窗两端都为 null 的券视为永久有效")
    void nullWindowMeansAlwaysValid() {
        Coupon coupon = discountCoupon("0.90");
        coupon.setStartTime(null);
        coupon.setEndTime(null);

        assertThatCode(() -> CouponCalculator.validateForReceive(coupon, NOW)).doesNotThrowAnyException();
    }

    // ---- 工具 ----

    private Coupon discountCoupon(String rate) {
        return coupon(CouponCalculator.TYPE_DISCOUNT, "0.00", rate);
    }

    private Coupon fullReduction(String minAmount, String off) {
        return coupon(CouponCalculator.TYPE_FULL_REDUCTION, minAmount, off);
    }

    private Coupon coupon(int type, String minAmount, String discountValue) {
        Coupon coupon = new Coupon();
        coupon.setType(type);
        coupon.setMinAmount(new BigDecimal(minAmount));
        coupon.setDiscountValue(new BigDecimal(discountValue));
        coupon.setStatus(1);
        coupon.setStartTime(NOW.minusDays(1));
        coupon.setEndTime(NOW.plusDays(1));
        return coupon;
    }
}
