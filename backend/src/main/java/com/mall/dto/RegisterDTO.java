package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {

    @NotBlank(message = "请输入用户名")
    @Size(min = 2, max = 20, message = "用户名长度需为2到20个字符")
    private String username;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
    private String phone;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 30, message = "密码长度需为6到30个字符")
    private String password;
}
