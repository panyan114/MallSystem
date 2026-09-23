package com.mall.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权链路的端到端测试：走真实的拦截器 + 真实的 Redis 黑名单 + 真实的 MySQL。
 *
 * <p>注意本项目的 {@code GlobalExceptionHandler} 把业务异常统一转成 HTTP 200、
 * 错误码放在 body 的 {@code code} 里，所以断言一律打在 {@code $.code} 上，
 * HTTP 状态码除 200 外没有信息量。
 */
@DisplayName("鉴权链路")
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "123456";

    /** 一个不存在的分类 id。删它没有任何副作用，用来探测管理接口是否放行。 */
    private static final String ADMIN_PROBE = "/api/category/999999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private long userFloor;

    @BeforeEach
    void recordUserFloor() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM t_user", Long.class);
        userFloor = max == null ? 0L : max;
    }

    @AfterEach
    void deleteCreatedUsers() {
        jdbc.update("DELETE FROM t_user WHERE id > ?", userFloor);
    }

    // ------------------------------------------------------------ 登录

    @Test
    @DisplayName("建库脚本灌入的店主账号能用 README 承诺的密码登录")
    void seededOwnerCanLogIn() throws Exception {
        // 这条断言看着平淡，但它守住的是一个真实事故：init.sql 里曾存着一个 29 字符的
        // 残缺 bcrypt hash（合法值是 60 字符），bcrypt 永远匹配不上，店主永远登不进去，
        // 而报错只是笼统的「用户名或密码错误」。
        assertThat(login("店主", PASSWORD)).isNotBlank();
        assertThat(login("13800000001", PASSWORD)).as("也应该支持用手机号登录").isNotBlank();
    }

    @Test
    @DisplayName("密码错误时不签发 token")
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(loginRequest("店主", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ------------------------------------------------------- 登出与黑名单

    @Test
    @DisplayName("登出后原 token 立即失效，而不是继续有效到自然过期")
    void logoutRevokesTokenImmediately() throws Exception {
        String token = login("店主", PASSWORD);

        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登出只影响这一张 token，重新登录拿到的 token 照常可用")
    void logoutDoesNotAffectOtherTokens() throws Exception {
        String first = login("店主", PASSWORD);
        String second = login("店主", PASSWORD);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ------------------------------------------------------------ 拦截

    @Test
    @DisplayName("没有 token、token 格式错误都返回 401")
    void rejectsMissingOrMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/info"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/auth/info").header("Authorization", "Bearer 显然是假的"))
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/auth/info").header("Authorization", "Token 前缀不对"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("公开接口不需要 token")
    void publicEndpointsAreReachable() throws Exception {
        mockMvc.perform(get("/api/category/list"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/product/list"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("普通消费者访问管理接口被拒（403）")
    void nonAdminCannotReachAdminEndpoints() throws Exception {
        createUser("auth_normal_user", 0, 1);

        String token = login("auth_normal_user", PASSWORD);

        mockMvc.perform(delete(ADMIN_PROBE).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("店主可以访问管理接口")
    void adminCanReachAdminEndpoints() throws Exception {
        createUser("auth_admin_user", 1, 1);

        String token = login("auth_admin_user", PASSWORD);

        mockMvc.perform(delete(ADMIN_PROBE).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // --------------------------------------------------- 身份以数据库为准

    @Test
    @DisplayName("角色以数据库当前值为准，token 里的旧角色不作数")
    void roleIsReadFromDatabaseNotFromToken() throws Exception {
        long userId = createUser("auth_demoted_user", 1, 1);
        String token = login("auth_demoted_user", PASSWORD);

        // 此时库里是店主，管理接口放行
        mockMvc.perform(delete(ADMIN_PROBE).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200));

        // 把库里的角色降为消费者——token 仍然写着 role=1，且未过期
        jdbc.update("UPDATE t_user SET role = 0 WHERE id = ?", userId);

        // 如果拦截器偷懒直接信任 token 里的角色，这里就会放行一个已被撤权的用户
        mockMvc.perform(delete(ADMIN_PROBE).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("账号被禁用后，已签发的 token 立即失效")
    void disabledAccountIsRejectedEvenWithValidToken() throws Exception {
        long userId = createUser("auth_disabled_user", 0, 1);
        String token = login("auth_disabled_user", PASSWORD);

        jdbc.update("UPDATE t_user SET status = 0 WHERE id = ?", userId);

        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("账号被逻辑删除后，已签发的 token 立即失效")
    void logicallyDeletedAccountIsRejected() throws Exception {
        long userId = createUser("auth_deleted_user", 0, 1);
        String token = login("auth_deleted_user", PASSWORD);

        jdbc.update("UPDATE t_user SET deleted = 1 WHERE id = ?", userId);

        mockMvc.perform(get("/api/auth/info").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ---------------------------------------------------------------- 工具

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(loginRequest(username, password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(body).path("data").path("token").asText();
    }

    private RequestBuilder loginRequest(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long createUser(String username, int role, int status) {
        jdbc.update("INSERT INTO t_user (username, password, role, status) VALUES (?, ?, ?, ?)",
                username, passwordEncoder.encode(PASSWORD), role, status);
        Long id = jdbc.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
        return id == null ? 0L : id;
    }
}
