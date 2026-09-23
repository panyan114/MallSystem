package com.mall.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileDTO {

    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
    private String phone;

    private String avatar;
}
