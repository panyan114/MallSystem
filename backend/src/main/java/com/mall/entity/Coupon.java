package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer type;
    private BigDecimal minAmount;
    private BigDecimal discountValue;
    private Integer totalCount;
    private Integer receivedCount;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
