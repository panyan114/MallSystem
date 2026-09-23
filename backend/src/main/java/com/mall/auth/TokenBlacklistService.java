package com.mall.auth;

import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 登出黑名单。
 * <p>
 * 自研 JWT 是无状态的，签发后服务端无法单方面作废。这里用 Redis 记录「已登出但尚未过期」
 * 的 token 的 jti，拦截器每次请求校验一次，从而让登出立即生效。
 * <p>
 * <b>Redis 不可用时的行为（刻意不对称，按操作分别取舍）：</b>
 * <ul>
 *   <li>{@link #isRevoked} —— <b>失败开放</b>：放行并记录 ERROR。读路径在每次请求上，
 *       若失败关闭会导致 Redis 一挂整站不可用；对个人店铺而言，可用性优先。</li>
 *   <li>{@link #revoke} —— <b>失败响亮</b>：抛异常。登出是用户主动发起的安全动作，
 *       假装成功会让用户以为 token 已失效，实际仍可被使用到自然过期，这比报错更危险。
 *       前端即使收到失败也会清除本地会话，用户不会停留在「看起来已登录」的状态。</li>
 * </ul>
 * 若业务上无法接受「Redis 故障期间被吊销的 token 短暂复活」，把 isRevoked 的 catch 分支
 * 改为抛出 BusinessException(UNAUTHORIZED) 即可切换为失败关闭。
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "mall:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 把这张 token 拉黑，存活时间等于它剩余的有效期——过期即自动清除，黑名单不会无限增长。
     */
    public void revoke(AuthUser authUser) {
        if (authUser == null || authUser.jti() == null || authUser.expiresAt() == null) {
            // 旧版本签发的 token 没有 jti，无法精确吊销；它最多再存活到自然过期。
            return;
        }
        long ttlSeconds = authUser.expiresAt() - Instant.now().getEpochSecond();
        if (ttlSeconds <= 0) {
            // 已经过期了，本来就用不了，不必占用黑名单空间。
            return;
        }
        try {
            redisTemplate.opsForValue().set(keyOf(authUser.jti()), "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("写入 JWT 黑名单失败，jti={} 在自然过期前仍可继续使用", authUser.jti(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(),
                    "登出未完全生效：服务端会话存储不可用，请稍后重试");
        }
    }

    /**
     * @return true 表示该 token 已被吊销
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(keyOf(jti)));
        } catch (Exception e) {
            log.error("Redis 不可用，本次跳过 JWT 黑名单校验（失败开放），jti={}", jti, e);
            return false;
        }
    }

    private String keyOf(String jti) {
        return KEY_PREFIX + jti;
    }
}
