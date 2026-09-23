package com.mall.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.JwtConfig;
import com.mall.entity.User;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * JWT 签发与校验的测试。
 *
 * <p>这里没有 Spring——{@link JwtTokenService} 只依赖一个 {@link JwtConfig} 和 ObjectMapper，
 * 直接 new 出来测比启动整个上下文快得多。
 *
 * <p>需要构造「过期」「缺 jti」这类 token 时，测试自己按 HS256 规范签一份，而不是复用被测代码的
 * 签发逻辑——拿实现去验证实现，签名算法写错了也会一起错、一起通过。
 */
@DisplayName("JwtTokenService 签发与校验")
class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-characters-long";
    private static final String OTHER_SECRET = "another-secret-key-at-least-32-characters";

    private JwtTokenService service(String secret, long expirationSeconds) {
        JwtConfig config = new JwtConfig();
        config.setSecret(secret);
        config.setExpiration(expirationSeconds);
        return new JwtTokenService(config, new ObjectMapper());
    }

    private User user(long id, String username, int role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    @Test
    @DisplayName("签发的 token 能解析回原身份，并带上唯一的 jti")
    void roundTripsIdentity() {
        JwtTokenService service = service(SECRET, 3600);

        String token = service.createToken(user(7L, "店主", 1));
        AuthUser parsed = service.parseToken(token);

        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.username()).isEqualTo("店主");
        assertThat(parsed.role()).isEqualTo(1);
        assertThat(parsed.jti()).isNotBlank();
        assertThat(parsed.isAdmin()).isTrue();
        assertThat(parsed.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    @DisplayName("两次签发得到不同的 jti（登出才能精确吊销某一张 token）")
    void issuesUniqueJtiPerToken() {
        JwtTokenService service = service(SECRET, 3600);
        User user = user(7L, "店主", 1);

        AuthUser first = service.parseToken(service.createToken(user));
        AuthUser second = service.parseToken(service.createToken(user));

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    @DisplayName("用别的密钥签出来的 token 必须被拒绝")
    void rejectsTokenSignedWithAnotherSecret() {
        // 攻击者拿不到服务端密钥，就只能自己编一个——这正是密钥不能硬编码在源码里的原因
        String forged = service(OTHER_SECRET, 3600).createToken(user(1L, "攻击者", 1));

        BusinessException error = catchThrowableOfType(
                () -> service(SECRET, 3600).parseToken(forged), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("篡改 payload 把 role 改成 1 会被签名校验发现")
    void rejectsTamperedPayload() {
        JwtTokenService service = service(SECRET, 3600);
        String token = service.createToken(user(7L, "普通用户", 0));

        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // 把 role 从 0 改成 1，交给服务端做管理员——签名不变，拼接回去
        String tamperedPayload = payload.replace("\"role\":0", "\"role\":1");
        assertThat(tamperedPayload).as("替换必须真的生效，否则这条测试是假绿").isNotEqualTo(payload);

        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThat(catchThrowableOfType(
                () -> service.parseToken(tampered), BusinessException.class)).isNotNull();
    }

    @Test
    @DisplayName("过期的 token 被拒绝，并给出「已过期」而不是笼统的未登录")
    void rejectsExpiredToken() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", 7);
        payload.put("username", "用户");
        payload.put("role", 0);
        payload.put("jti", "expired-jti");
        payload.put("iat", now - 7200);
        payload.put("exp", now - 3600);

        String expired = signManually(payload, SECRET);

        BusinessException error = catchThrowableOfType(
                () -> service(SECRET, 3600).parseToken(expired), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("过期");
    }

    @Test
    @DisplayName("旧版本签发、没有 jti 的 token 仍能解析（jti 为 null）")
    void toleratesLegacyTokenWithoutJti() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", 7);
        payload.put("username", "老用户");
        payload.put("role", 0);
        payload.put("iat", now);
        payload.put("exp", now + 3600);

        AuthUser parsed = service(SECRET, 3600).parseToken(signManually(payload, SECRET));

        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.jti()).isNull();
    }

    @Test
    @DisplayName("格式不对的 token 一律拒绝，不抛底层异常")
    void rejectsMalformedToken() {
        JwtTokenService service = service(SECRET, 3600);

        for (String malformed : new String[]{"", "   ", "not-a-token", "a.b", "a.b.c.d", "a.b.c"}) {
            BusinessException error = catchThrowableOfType(
                    () -> service.parseToken(malformed), BusinessException.class);
            assertThat(error).as("输入 [%s] 应当被拒绝", malformed).isNotNull();
            assertThat(error.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }
    }

    /** 按 HS256 规范独立签一份 token，用于构造被测代码自己产不出来的输入。 */
    private String signManually(Map<String, Object> payload, String secret) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String content = encoder.encodeToString(mapper.writeValueAsBytes(header))
                    + "." + encoder.encodeToString(mapper.writeValueAsBytes(payload));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return content + "." + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("构造测试 token 失败", e);
        }
    }
}
