package com.mall.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /** HS256 密钥长度下限。低于此长度可被暴力枚举，直接拒绝启动。 */
    static final int MIN_SECRET_LENGTH = 32;

    private String secret;
    private long expiration;

    /**
     * 密钥缺失或过短时快速失败，而不是退化成一个人人皆知的默认值。
     * <p>
     * 密钥硬编码进配置文件就等于随源码公开分发——任何人都能离线签发一个
     * role=1 的 token 直接获得店主权限。因此这里宁可启动失败。
     */
    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    JWT_SECRET 未设置，应用拒绝启动。
                    生成一个密钥：
                      openssl rand -base64 48
                    然后通过环境变量注入：
                      export JWT_SECRET="<生成的值>"     # Linux / macOS / Git Bash
                      set JWT_SECRET=<生成的值>           # Windows CMD
                    使用 Docker Compose 时，填写仓库根目录的 .env 文件。""");
        }
        secret = secret.trim();
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足：" + secret.length() + " 字符，至少需要 " + MIN_SECRET_LENGTH
                            + " 字符。请用 `openssl rand -base64 48` 重新生成。");
        }
    }
}
