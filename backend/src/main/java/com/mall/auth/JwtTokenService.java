package com.mall.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.JwtConfig;
import com.mall.entity.User;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JwtConfig jwtConfig;
    private final ObjectMapper objectMapper;

    public JwtTokenService(JwtConfig jwtConfig, ObjectMapper objectMapper) {
        this.jwtConfig = jwtConfig;
        this.objectMapper = objectMapper;
    }

    public String createToken(User user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", user.getRole() == null ? 0 : user.getRole());
        // jti 用于登出后把这张 token 单独拉黑，避免「登出了但 token 还能用到过期」
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now);
        payload.put("exp", now + getExpirationSeconds());

        try {
            String headerPart = encode(objectMapper.writeValueAsBytes(header));
            String payloadPart = encode(objectMapper.writeValueAsBytes(payload));
            String content = headerPart + "." + payloadPart;
            return content + "." + encode(sign(content));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "登录令牌生成失败");
        }
    }

    public AuthUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw unauthorized();
            }
            String content = parts[0] + "." + parts[1];
            byte[] expected = sign(content);
            byte[] actual = decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unauthorized();
            }

            Map<String, Object> payload = objectMapper.readValue(decode(parts[1]), MAP_TYPE);
            long expiration = numberValue(payload.get("exp"));
            if (expiration <= Instant.now().getEpochSecond()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
            }
            Object jti = payload.get("jti");
            return new AuthUser(
                    numberValue(payload.get("sub")),
                    String.valueOf(payload.get("username")),
                    (int) numberValue(payload.get("role")),
                    jti == null ? null : String.valueOf(jti),
                    expiration
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized();
        }
    }

    private long getExpirationSeconds() {
        return jwtConfig.getExpiration() > 0 ? jwtConfig.getExpiration() : 86400;
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
