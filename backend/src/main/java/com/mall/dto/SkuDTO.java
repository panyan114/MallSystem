package com.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuDTO {

    private String specKey;
    private String specDesc;
    private BigDecimal price;
    private Integer stock;
    private String image;
}
