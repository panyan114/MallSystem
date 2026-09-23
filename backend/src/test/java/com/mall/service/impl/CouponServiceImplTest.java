package com.mall.service.impl;

import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.support.AbstractIntegrationTest;
import com.mall.vo.CouponVO;
import com.mall.vo.UserCouponVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 领券链路的集成测试，跑在真实 MySQL 上。
 *
 * <p>这里的保证全落在 SQL 上——「{@code received_count < total_count} 作为 WHERE 条件」
 * 和 {@code uk_user_coupon} 唯一索引，打桩测不出来，所以坚持用真库。
 *
 * <p><b>绝对不要碰种子优惠券 1–3。</b>容器是全局共享的、数据真实提交，且回滚只能靠
 * id 水位线。在种子券上领一次会永久留下 {@code received_count = 1}，水位线清理重置不了它，
 * 同一 JVM 里后续所有测试类都会读到被污染的计数。券一律用 {@link #createCoupon} 自建。
 */
@DisplayName("CouponServiceImpl 领券与我的优惠券")
class CouponServiceImplTest extends AbstractIntegrationTest {

    @Autowired
    private CouponServiceImpl couponService;

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
        // 子表先删。此 schema 没有外键，顺序写错 MySQL 不会拦——所以更要写对。
        jdbc.update("DELETE FROM t_user_coupon WHERE id > ?", userCouponFloor);
        jdbc.update("DELETE FROM t_coupon WHERE id > ?", couponFloor);
        jdbc.update("DELETE FROM t_user WHERE id > ?", userFloor);
    }

    // ---- 领券 ----

    @Test
    @DisplayName("领券成功：计数加一、落一行、领取时间非空")
    void receivesCoupon() {
        long userId = createUser("receiver_ok");
        long couponId = createCoupon("测试券-正常", 0, "50.00", "10.00", 10);

        couponService.receive(userId, couponId);

        assertThat(receivedCountOf(couponId)).as("已领数量应加一").isEqualTo(1);
        assertThat(userCouponCountOf(userId)).as("应落一行用户券").isEqualTo(1);
        assertThat(receiveTimeOf(userId, couponId)).as("领取时间应被显式写入").isNotNull();
        assertThat(statusOfUserCoupon(userId, couponId)).as("新领的券是未使用").isZero();
    }

    @Test
    @DisplayName("重复领取被唯一索引拦下，且已领计数回滚，不留脏数据")
    void rejectsDuplicateReceiveAndRollsBackCounter() {
        long userId = createUser("receiver_dup");
        long couponId = createCoupon("测试券-重复", 0, "50.00", "10.00", 10);

        couponService.receive(userId, couponId);
        BusinessException error = catchThrowableOfType(
                () -> couponService.receive(userId, couponId), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_ALREADY_RECEIVED.getCode());

        // 这两条断言才是真正证明回滚生效的部分：
        // 第二次领券时先执行了 received_count + 1，撞唯一索引后才抛异常。
        // 若事务没回滚，计数会变成 2，凭空多出一个永远没人用的名额。
        assertThat(receivedCountOf(couponId)).as("计数必须回到 1，不能是 2").isEqualTo(1);
        assertThat(userCouponCountOf(userId)).as("只应有一行用户券").isEqualTo(1);
    }

    @Test
    @DisplayName("领完的券返回 COUPON_SOLD_OUT，且不落用户券")
    void rejectsSoldOutCoupon() {
        long userId = createUser("receiver_soldout");
        long couponId = createCoupon("测试券-仅一张", 0, "0.00", "5.00", 1);

        long first = createUser("receiver_soldout_a");
        couponService.receive(first, couponId);

        BusinessException error = catchThrowableOfType(
                () -> couponService.receive(userId, couponId), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_SOLD_OUT.getCode());
        assertThat(receivedCountOf(couponId)).as("失败不应改变计数").isEqualTo(1);
        assertThat(userCouponCountOf(userId)).as("失败者不应留下用户券").isZero();
    }

    @Test
    @DisplayName("已过期的券不能领")
    void rejectsExpiredCoupon() {
        long userId = createUser("receiver_expired");
        long couponId = createCoupon("测试券-过期", 0, "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), 1);

        BusinessException error = catchThrowableOfType(
                () -> couponService.receive(userId, couponId), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_EXPIRED.getCode());
        assertThat(receivedCountOf(couponId)).isZero();
    }

    @Test
    @DisplayName("已停用的券不能领")
    void rejectsDisabledCoupon() {
        long userId = createUser("receiver_disabled");
        long couponId = createCoupon("测试券-停用", 0, "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 0);

        BusinessException error = catchThrowableOfType(
                () -> couponService.receive(userId, couponId), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
        assertThat(receivedCountOf(couponId)).isZero();
    }

    @Test
    @DisplayName("不存在的券返回 COUPON_NOT_FOUND")
    void rejectsUnknownCoupon() {
        long userId = createUser("receiver_unknown");

        BusinessException error = catchThrowableOfType(
                () -> couponService.receive(userId, 999999999L), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("并发领券不会超发：3 张券被 12 个人抢，恰好 3 个人领到")
    void doesNotOverIssueUnderConcurrency() throws Exception {
        int totalCount = 3;
        int threads = 12;
        long couponId = createCoupon("测试券-并发", 0, "0.00", "5.00", totalCount);

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            userIds.add(createUser("receiver_concurrent_" + i));
        }

        // 每个线程用不同用户抢同一张券——这是在压 received_count < total_count 这个条件更新，
        // 而不是唯一索引（唯一索引挡的是同一个人重复领）。
        // 取号必须用 AtomicInteger：普通 int[] 自增在多线程下会撞号，
        // 两个线程拿到同一个用户就会有一个被唯一索引挡掉，成功数不足 3，测试随机失败。
        AtomicInteger next = new AtomicInteger();
        int succeeded = runConcurrently(threads, () -> {
            long userId = userIds.get(next.getAndIncrement());
            couponService.receive(userId, couponId);
        });

        assertThat(succeeded).as("成功领券数应恰好等于发放总量").isEqualTo(totalCount);
        assertThat(receivedCountOf(couponId)).as("已领计数应与成功数一致，不能超发").isEqualTo(totalCount);
        assertThat(userCouponCountOfAll()).as("落库的用户券行数应等于发放总量").isEqualTo(totalCount);
    }

    // ---- 领券中心 ----

    @Test
    @DisplayName("领券中心按用户标记 received，并在领取后翻转为 true")
    void listAvailableMarksReceived() {
        long userId = createUser("available_viewer");
        long couponId = createCoupon("测试券-可领中心", 0, "50.00", "10.00", 10);

        assertThat(findAvailable(couponId, userId).getReceived()).as("未领时为 false").isFalse();
        assertThat(findAvailable(couponId, userId).getRemainCount()).isEqualTo(10);

        couponService.receive(userId, couponId);

        assertThat(findAvailable(couponId, userId).getReceived()).as("领取后翻转为 true").isTrue();
        assertThat(findAvailable(couponId, userId).getRemainCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("领券中心不返回已停用和已过期的券")
    void listAvailableExcludesInvalidCoupons() {
        long userId = createUser("available_filter");
        long disabled = createCoupon("测试券-中心停用", 0, "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 0);
        long expired = createCoupon("测试券-中心过期", 0, "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), 1);

        List<Long> visibleIds = couponService.listAvailable(userId).stream().map(CouponVO::getId).toList();

        assertThat(visibleIds).doesNotContain(disabled, expired);
    }

    // ---- 我的优惠券 ----

    @Test
    @DisplayName("我的优惠券只返回自己的券")
    void myCouponsIsolatedByUser() {
        long mine = createUser("mycoupon_owner");
        long other = createUser("mycoupon_other");
        long couponId = createCoupon("测试券-隔离", 0, "0.00", "5.00", 10);

        couponService.receive(mine, couponId);
        couponService.receive(other, couponId);

        List<UserCouponVO> myList = couponService.myCoupons(mine);

        assertThat(myList).hasSize(1);
        assertThat(myList.get(0).getCouponId()).isEqualTo(couponId);
        assertThat(myList.get(0).getName()).as("应关联出券模板信息").isEqualTo("测试券-隔离");
        assertThat(myList.get(0).getStatus()).isZero();
    }

    @Test
    @DisplayName("券窗口已过时，我的优惠券把状态派生成已过期(2)，即使库里还是 0")
    void myCouponsDerivesExpiredStatus() {
        long userId = createUser("mycoupon_expired");
        // 直接插库绕过 receive（receive 会因窗口已过而拒绝），模拟「领了之后才过期的券」
        long couponId = createCoupon("测试券-先领后过期", 0, "0.00", "5.00", 10,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), 1);
        jdbc.update("INSERT INTO t_user_coupon (user_id, coupon_id, status, receive_time) VALUES (?, ?, 0, ?)",
                userId, couponId, Timestamp.valueOf(LocalDateTime.now().minusDays(20)));

        List<UserCouponVO> myList = couponService.myCoupons(userId);

        assertThat(myList).hasSize(1);
        assertThat(myList.get(0).getStatus()).as("读时派生的过期状态").isEqualTo(2);
        assertThat(statusOfUserCoupon(userId, couponId)).as("库里仍然是 0，派生不落库").isZero();
    }

    // ---- 工具 ----

    /** 所有线程在一道闸门后同时起跑；只把 BusinessException 当作业务失败。 */
    private int runConcurrently(int threads, ThrowingAction action) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        action.run();
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                }));
            }
            startGate.countDown();

            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(60, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private CouponVO findAvailable(long couponId, long userId) {
        return couponService.listAvailable(userId).stream()
                .filter(vo -> vo.getId() == couponId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("领券中心里找不到券 " + couponId));
    }

    private long createUser(String username) {
        jdbc.update("INSERT INTO t_user (username, password, role, status) VALUES (?, 'x', 0, 1)", username);
        return requireLong("SELECT id FROM t_user WHERE username = ?", username);
    }

    private long createCoupon(String name, int type, String minAmount, String discountValue, int totalCount) {
        return createCoupon(name, type, minAmount, discountValue, totalCount,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 1);
    }

    private long createCoupon(String name, int type, String minAmount, String discountValue, int totalCount,
                              LocalDateTime startTime, LocalDateTime endTime, int status) {
        jdbc.update("INSERT INTO t_coupon (name, type, min_amount, discount_value, total_count, received_count,"
                        + " status, start_time, end_time) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)",
                name, type, new BigDecimal(minAmount), new BigDecimal(discountValue), totalCount, status,
                Timestamp.valueOf(startTime), Timestamp.valueOf(endTime));
        return requireLong("SELECT id FROM t_coupon WHERE name = ?", name);
    }

    private long maxId(String table) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return max == null ? 0L : max;
    }

    private long requireLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        if (value == null) {
            throw new AssertionError("查询未返回结果：" + sql);
        }
        return value;
    }

    private int intOf(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int receivedCountOf(long couponId) {
        return intOf("SELECT received_count FROM t_coupon WHERE id = ?", couponId);
    }

    private int userCouponCountOf(long userId) {
        return intOf("SELECT COUNT(*) FROM t_user_coupon WHERE user_id = ?", userId);
    }

    private int userCouponCountOfAll() {
        return intOf("SELECT COUNT(*) FROM t_user_coupon WHERE id > ?", userCouponFloor);
    }

    private Integer statusOfUserCoupon(long userId, long couponId) {
        return jdbc.queryForObject("SELECT status FROM t_user_coupon WHERE user_id = ? AND coupon_id = ?",
                Integer.class, userId, couponId);
    }

    private Object receiveTimeOf(long userId, long couponId) {
        return jdbc.queryForObject("SELECT receive_time FROM t_user_coupon WHERE user_id = ? AND coupon_id = ?",
                Object.class, userId, couponId);
    }
}
