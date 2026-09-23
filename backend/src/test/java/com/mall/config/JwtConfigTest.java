package com.mall.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密钥校验的测试。
 *
 * <p>这条规则是修复「JWT secret 硬编码进 application.yml」时加的：密钥随源码分发等于没有密钥，
 * 任何人都能离线签出一张 role=1 的 token。与其退化成一个人人皆知的默认值，不如启动失败。
 */
@DisplayName("JwtConfig 密钥校验")
class JwtConfigTest {

    @Test
    @DisplayName("未配置密钥时拒绝启动")
    void rejectsMissingSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret(null);
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET 未设置");
    }

    @Test
    @DisplayName("密钥只有空白字符时拒绝启动")
    void rejectsBlankSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("     ");
        assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("密钥短于 32 字符时拒绝启动")
    void rejectsShortSecret() {
        JwtConfig config = new JwtConfig();
        // 修复前 application.yml 里硬编码的就是这个值，17 字符，可被暴力枚举
        config.setSecret("mall2026secretkey");

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32");
    }

    @Test
    @DisplayName("恰好 32 字符的密钥通过校验（下边界）")
    void acceptsExactlyMinimumLength() {
        JwtConfig config = new JwtConfig();
        config.setSecret("a".repeat(JwtConfig.MIN_SECRET_LENGTH));

        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("通过校验后密钥被去除首尾空白")
    void trimsSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("  " + "a".repeat(48) + "  ");

        config.validate();

        assertThat(config.getSecret()).isEqualTo("a".repeat(48));
    }
}
