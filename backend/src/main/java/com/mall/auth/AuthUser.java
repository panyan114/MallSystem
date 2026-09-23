package com.mall.auth;

/**
 * 当前请求的登录身份。
 *
 * @param id        用户 ID
 * @param username  用户名
 * @param role      角色：0=消费者，1=店主
 * @param jti       token 唯一标识，用于登出后加入黑名单。旧版本签发的 token 没有该字段，可能为 null
 * @param expiresAt token 过期时间（epoch 秒），用于计算黑名单条目的存活时间
 */
public record AuthUser(Long id, String username, Integer role, String jti, Long expiresAt) {

    public static final String REQUEST_ATTRIBUTE = "authUser";

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}
