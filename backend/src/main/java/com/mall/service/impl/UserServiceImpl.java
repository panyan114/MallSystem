package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.auth.JwtTokenService;
import com.mall.dto.ChangePasswordDTO;
import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.entity.User;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.mapper.UserMapper;
import com.mall.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserServiceImpl(PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Map<String, Object> register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername().trim();
        String phone = registerDTO.getPhone().trim();
        if (count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException(ErrorCode.USER_EXIST.getCode(), "用户名已被使用");
        }
        if (count(new LambdaQueryWrapper<User>().eq(User::getPhone, phone)) > 0) {
            throw new BusinessException(ErrorCode.USER_EXIST.getCode(), "手机号已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole(0);
        user.setStatus(1);
        save(user);
        return createSession(user);
    }

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        String account = loginDTO.getUsername().trim();
        User user = getOne(new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper.eq(User::getUsername, account)
                        .or()
                        .eq(User::getPhone, account)), false);
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "账号已被禁用");
        }
        return createSession(user);
    }

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = requireUser(userId);
        return toUserView(user);
    }

    @Override
    public Map<String, Object> updateProfile(Long userId, UpdateProfileDTO profileDTO) {
        User user = requireUser(userId);
        String phone = profileDTO.getPhone() == null ? null : profileDTO.getPhone().trim();
        if (phone != null && !phone.equals(user.getPhone())) {
            Long count = count(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, phone)
                    .ne(User::getId, userId));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.USER_EXIST.getCode(), "手机号已被注册");
            }
            user.setPhone(phone);
        }
        if (profileDTO.getAvatar() != null) {
            user.setAvatar(profileDTO.getAvatar().trim());
        }
        updateById(user);
        return toUserView(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDTO passwordDTO) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(passwordDTO.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "原密码错误");
        }
        if (passwordEncoder.matches(passwordDTO.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(passwordDTO.getNewPassword()));
        updateById(user);
    }

    private User requireUser(Long userId) {
        User user = getById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private Map<String, Object> createSession(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtTokenService.createToken(user));
        result.put("user", toUserView(user));
        return result;
    }

    private Map<String, Object> toUserView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("phone", user.getPhone());
        view.put("avatar", user.getAvatar());
        view.put("role", user.getRole() == null ? 0 : user.getRole());
        return view;
    }
}
