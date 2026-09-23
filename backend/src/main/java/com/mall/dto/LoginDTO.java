package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "请输入用户名或手机号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
