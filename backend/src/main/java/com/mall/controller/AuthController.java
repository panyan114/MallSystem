package com.mall.controller;

import com.mall.auth.AuthUser;
import com.mall.auth.TokenBlacklistService;
import com.mall.dto.ChangePasswordDTO;
import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.dto.UpdateProfileDTO;
import com.mall.service.UserService;
import com.mall.vo.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(UserService userService, TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/register")
    public Result register(@Valid @RequestBody RegisterDTO registerDTO) {
        return Result.success(userService.register(registerDTO));
    }

    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(userService.login(loginDTO));
    }

    /**
     * 登出：把当前 token 加入黑名单，使其立即失效（而不是继续有效到自然过期）。
     */
    @PostMapping("/logout")
    public Result logout(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        tokenBlacklistService.revoke(authUser);
        return Result.success();
    }

    @GetMapping("/info")
    public Result info(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        return Result.success(userService.getProfile(authUser.id()));
    }

    @PutMapping("/info")
    public Result updateInfo(@Valid @RequestBody UpdateProfileDTO profileDTO,
                             @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        return Result.success(userService.updateProfile(authUser.id(), profileDTO));
    }

    @PutMapping("/password")
    public Result updatePassword(@Valid @RequestBody ChangePasswordDTO passwordDTO,
                                 @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        userService.changePassword(authUser.id(), passwordDTO);
        return Result.success();
    }
}
