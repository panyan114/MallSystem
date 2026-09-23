package com.mall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductDTO {

    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String images;
    private BigDecimal price;
    private BigDecimal salePrice;
    private Integer stock;
    private Integer status;
    private Integer sort;
    private List<SkuDTO> skus;
}
