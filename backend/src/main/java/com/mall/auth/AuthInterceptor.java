package com.mall.auth;

import com.mall.entity.User;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtTokenService jwtTokenService,
                           TokenBlacklistService tokenBlacklistService,
                           UserMapper userMapper) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicRequest(request)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AuthUser authUser = jwtTokenService.parseToken(authorization.substring(7).trim());
        // 先查黑名单再查库：已登出的 token 直接拒绝，不必浪费一次数据库查询
        if (tokenBlacklistService.isRevoked(authUser.jti())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "登录已失效，请重新登录");
        }

        User user = userMapper.selectById(authUser.id());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "账号不存在或已禁用");
        }

        // 身份信息以库里的为准（角色可能被改动），jti 和过期时间取自 token 本身
        AuthUser currentUser = new AuthUser(user.getId(), user.getUsername(), user.getRole(),
                authUser.jti(), authUser.expiresAt());
        request.setAttribute(AuthUser.REQUEST_ATTRIBUTE, currentUser);

        if (requiresAdmin(handler) && !currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("/api/auth/login".equals(path) || "/api/auth/register".equals(path)) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/api/category/list".equals(path) || "/api/product/list".equals(path)) {
            return true;
        }
        return path.matches("^/api/product/\\d+$");
    }

    private boolean requiresAdmin(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return handlerMethod.hasMethodAnnotation(RequiresAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequiresAdmin.class);
    }
}
