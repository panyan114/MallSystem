package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_sku")
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specKey;
    private String specDesc;
    private BigDecimal price;
    private Integer stock;
    private String image;
}
