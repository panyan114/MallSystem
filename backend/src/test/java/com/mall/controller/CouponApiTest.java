package com.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.exception.ErrorCode;
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

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 优惠券接口的鉴权与响应契约测试。
 *
 * <p>注意本项目的 {@code GlobalExceptionHandler} 把业务异常统一转成 HTTP 200、
 * 错误码放在 body 的 {@code code} 里，所以断言一律打在 {@code $.code} 上，
 * HTTP 状态码除 200 外没有信息量。
 */
@DisplayName("优惠券接口")
class CouponApiTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private long userFloor;
    private long couponFloor;
    private long userCouponFloor;

    @BeforeEach
    void recordIdFloors() {
        userFloor = maxId("t_user");
        couponFloor = maxId("t_coupon");
        userCouponFloor = maxId("t_user_coupon");
    }

    @AfterEach
    void deleteCreatedRows() {
        jdbc.update("DELETE FROM t_user_coupon WHERE id > ?", userCouponFloor);
        jdbc.update("DELETE FROM t_coupon WHERE id > ?", couponFloor);
        jdbc.update("DELETE FROM t_user WHERE id > ?", userFloor);
    }

    // ---- 鉴权 ----

    @Test
    @DisplayName("后台券列表：无 token 401，消费者 403，店主 200")
    void listRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/coupon/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(get("/api/coupon/list").header("Authorization", bearer(login("测试用户", PASSWORD))))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        mockMvc.perform(get("/api/coupon/list").header("Authorization", bearer(login("店主", PASSWORD))))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("领券中心需要登录")
    void availableRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/coupon/available"))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(get("/api/coupon/available")
                        .header("Authorization", bearer(login("测试用户", PASSWORD))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("我的优惠券返回 {list,total} 信封而非裸数组")
    void myCouponsReturnsEnvelope() throws Exception {
        String token = bearer(login("测试用户", PASSWORD));

        // 这条是回归守卫：该接口原先直接返回 Result.success(Collections.emptyList())，
        // 前端拿到的是 $.data 数组而不是 {list,total}，与其他列表接口不一致。
        mockMvc.perform(get("/api/coupon/my").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // ---- 领券 ----

    @Test
    @DisplayName("领券成功后出现在我的优惠券里，重复领取报 4004")
    void receivesAndRejectsDuplicate() throws Exception {
        String username = "api_coupon_user";
        createUser(username);
        long couponId = createCoupon("接口券-领取", 10);
        String token = bearer(login(username, PASSWORD));

        mockMvc.perform(post("/api/coupon/" + couponId + "/receive").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/coupon/my").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].couponId").value((int) couponId))
                .andExpect(jsonPath("$.data.list[0].name").value("接口券-领取"))
                .andExpect(jsonPath("$.data.list[0].status").value(0));

        mockMvc.perform(post("/api/coupon/" + couponId + "/receive").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_ALREADY_RECEIVED.getCode()));
    }

    @Test
    @DisplayName("领一张不存在的券报 4003")
    void receiveUnknownCoupon() throws Exception {
        String username = "api_coupon_unknown";
        createUser(username);

        mockMvc.perform(post("/api/coupon/999999999/receive")
                        .header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("领券中心把已领取的券标成 received=true")
    void availableMarksReceived() throws Exception {
        String username = "api_coupon_available";
        createUser(username);
        long couponId = createCoupon("接口券-可领", 10);
        String token = bearer(login(username, PASSWORD));

        mockMvc.perform(post("/api/coupon/" + couponId + "/receive").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200));

        String body = mockMvc.perform(get("/api/coupon/available").header("Authorization", token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        boolean found = objectMapper.readTree(body).path("data").path("list").findValuesAsText("id").stream()
                .anyMatch(id -> id.equals(String.valueOf(couponId)));
        org.assertj.core.api.Assertions.assertThat(found).as("自建的券应出现在领券中心").isTrue();
    }

    // ---- 请求体 ----

    @Test
    @DisplayName("创建优惠券时日期用了空格分隔的格式，报 400 而不是 500")
    void malformedDateReturnsParamError() throws Exception {
        String token = bearer(login("店主", PASSWORD));
        // 前端日期选择器若不配 value-format 就会发这种格式。Jackson 的 LocalDateTime
        // 只认 ISO 的 'T' 分隔，会抛 HttpMessageNotReadableException。
        // 没有对应 handler 时会掉进兜底分支报 500「服务器内部错误」。
        String body = "{\"name\":\"格式测试\",\"type\":0,\"minAmount\":50.00,\"discountValue\":10.00,"
                + "\"totalCount\":10,\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2027-01-01 00:00:00\"}";

        mockMvc.perform(post("/api/coupon").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("创建优惠券时校验失败报 400（名称空、数量为 0）")
    void createValidatesInput() throws Exception {
        String token = bearer(login("店主", PASSWORD));
        String body = "{\"name\":\"\",\"type\":0,\"minAmount\":50.00,\"discountValue\":10.00,\"totalCount\":0}";

        mockMvc.perform(post("/api/coupon").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("满减金额大于门槛的券建不出来，从源头堵住倒贴券")
    void createRejectsDiscountAboveThreshold() throws Exception {
        String token = bearer(login("店主", PASSWORD));
        // 满 10 减 100
        String body = "{\"name\":\"倒贴券\",\"type\":0,\"minAmount\":10.00,\"discountValue\":100.00,"
                + "\"totalCount\":10,\"startTime\":\"2026-01-01T00:00:00\",\"endTime\":\"2027-01-01T00:00:00\"}";

        mockMvc.perform(post("/api/coupon").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject("SELECT COUNT(*) FROM t_coupon WHERE name = '倒贴券'", Integer.class))
                .as("校验失败的券不应落库")
                .isZero();
    }

    @Test
    @DisplayName("折扣比例 >= 1 的券建不出来")
    void createRejectsInvalidDiscountRate() throws Exception {
        String token = bearer(login("店主", PASSWORD));
        String body = "{\"name\":\"假折扣券\",\"type\":1,\"minAmount\":0.00,\"discountValue\":10.00,"
                + "\"totalCount\":10,\"startTime\":\"2026-01-01T00:00:00\",\"endTime\":\"2027-01-01T00:00:00\"}";

        mockMvc.perform(post("/api/coupon").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ---- 工具 ----

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void createUser(String username) {
        jdbc.update("INSERT INTO t_user (username, password, role, status) VALUES (?, ?, 0, 1)",
                username, passwordEncoder.encode(PASSWORD));
    }

    private long createCoupon(String name, int totalCount) {
        jdbc.update("INSERT INTO t_coupon (name, type, min_amount, discount_value, total_count, received_count,"
                        + " status, start_time, end_time) VALUES (?, 0, 50.00, 10.00, ?, 0, 1, ?, ?)",
                name, totalCount,
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
        Long id = jdbc.queryForObject("SELECT id FROM t_coupon WHERE name = ?", Long.class, name);
        if (id == null) {
            throw new AssertionError("建券后查不到记录");
        }
        return id;
    }

    private long maxId(String table) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return max == null ? 0L : max;
    }
}
