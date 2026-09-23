package com.mall.dto;

import lombok.Data;

@Data
public class OrderQueryDTO {

    private Integer status;
    private String orderNo;
    private Integer page;
    private Integer size;
}
