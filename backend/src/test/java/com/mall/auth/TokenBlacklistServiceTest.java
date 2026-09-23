package com.mall.auth;

import com.mall.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TokenBlacklistService 登出黑名单")
class TokenBlacklistServiceTest extends AbstractIntegrationTest {

    /** 必须与 TokenBlacklistService.KEY_PREFIX 一致。 */
    private static final String KEY_PREFIX = "mall:jwt:blacklist:";

    @Autowired
    private TokenBlacklistService blacklist;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("吊销后立即判定为已失效，未吊销的 token 不受影响")
    void detectsRevokedToken() {
        String revokedJti = newJti();
        String untouchedJti = newJti();

        assertThat(blacklist.isRevoked(revokedJti)).isFalse();

        blacklist.revoke(authUser(revokedJti, 3600));

        assertThat(blacklist.isRevoked(revokedJti)).isTrue();
        assertThat(blacklist.isRevoked(untouchedJti)).isFalse();
    }

    @Test
    @DisplayName("黑名单条目的存活时间不超过 token 剩余有效期")
    void expiresWithTheToken() {
        String jti = newJti();

        blacklist.revoke(authUser(jti, 120));

        Long ttl = redisTemplate.getExpire(KEY_PREFIX + jti, TimeUnit.SECONDS);
        // 记录永不过期（-1）或不存在的键（-2）都会让黑名单无限膨胀
        assertThat(ttl).isBetween(1L, 120L);
    }

    @Test
    @DisplayName("旧版本没有 jti 的 token 不会写入黑名单，也不会被误判为已吊销")
    void toleratesLegacyTokenWithoutJti() {
        assertThat(blacklist.isRevoked(null)).isFalse();
        assertThat(blacklist.isRevoked("   ")).isFalse();
        assertThatCode(() -> blacklist.revoke(new AuthUser(1L, "老用户", 0, null,
                Instant.now().getEpochSecond() + 3600))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("已经过期的 token 不必占用黑名单空间")
    void skipsAlreadyExpiredToken() {
        String jti = newJti();

        blacklist.revoke(authUser(jti, -60));

        assertThat(redisTemplate.hasKey(KEY_PREFIX + jti)).isFalse();
    }

    private String newJti() {
        return UUID.randomUUID().toString();
    }

    private AuthUser authUser(String jti, long secondsUntilExpiry) {
        return new AuthUser(1L, "测试用户", 0, jti,
                Instant.now().getEpochSecond() + secondsUntilExpiry);
    }
}
