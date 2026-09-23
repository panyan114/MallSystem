package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.ChangePasswordDTO;
import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {

    Map<String, Object> register(RegisterDTO registerDTO);

    Map<String, Object> login(LoginDTO loginDTO);

    Map<String, Object> getProfile(Long userId);

    Map<String, Object> updateProfile(Long userId, UpdateProfileDTO profileDTO);

    void changePassword(Long userId, ChangePasswordDTO passwordDTO);
}
