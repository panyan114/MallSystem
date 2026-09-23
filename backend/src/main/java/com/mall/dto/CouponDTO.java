package com.mall.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券的入参。
 *
 * <p>替代原先直接绑定 {@code Coupon} 实体的做法——那样客户端可以顺手把 {@code receivedCount}
 * 写成 1000，或者直接建一张 {@code status=0} 的废券。这里只暴露店主真正该控制的字段，
 * {@code receivedCount} 和 {@code status} 由服务端强制。
 *
 * <p>注意前端的日期选择器必须发 ISO 格式（{@code 2026-01-01T00:00:00}）。
 * 发 {@code "2026-01-01 00:00:00"}（空格分隔）对不上 Jackson 的默认 LocalDateTime 反序列化，
 * 会抛 HttpMessageNotReadableException。{@code GlobalExceptionHandler} 已把它转成 400 而不是 500。
 */
@Data
public class CouponDTO {

    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 50, message = "优惠券名称最长 50 字")
    private String name;

    /** 0=满减（discountValue 是减免金额），1=折扣（discountValue 是实付比例）。 */
    @NotNull(message = "请选择优惠券类型")
    private Integer type;

    /** 使用门槛，满减券必须给。 */
    @NotNull(message = "请填写使用门槛")
    @DecimalMin(value = "0.00", message = "使用门槛不能为负")
    private BigDecimal minAmount;

    @NotNull(message = "请填写优惠值")
    @DecimalMin(value = "0.01", message = "优惠值必须大于 0")
    private BigDecimal discountValue;

    @NotNull(message = "请填写发放数量")
    @Min(value = 1, message = "发放数量至少为 1")
    private Integer totalCount;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
