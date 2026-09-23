package com.mall.service.impl;

import com.mall.dto.OrderDTO;
import com.mall.dto.OrderItemDTO;
import com.mall.entity.Order;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.support.AbstractIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 下单用券 / 取消退券的集成测试。
 *
 * <p>单独成类而不是并进 {@code OrderServiceImplTest}：水位线清理涉及的表集不同
 * （这里还要管 {@code t_user_coupon} 和 {@code t_coupon}），混在一起容易漏删。
 *
 * <p><b>不要碰种子优惠券 1–3</b>——容器全局共享、数据真提交，在种子券上领一次会永久
 * 污染 {@code received_count}，而 id 水位线清理重置不了它。
 */
@DisplayName("下单用券与取消退券")
class OrderCouponIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private CouponServiceImpl couponService;

    @Autowired
    private JdbcTemplate jdbc;

    private long userFloor;
    private long productFloor;
    private long orderFloor;
    private long orderItemFloor;
    private long couponFloor;
    private long userCouponFloor;

    @BeforeEach
    void recordIdFloors() {
        userFloor = maxId("t_user");
        productFloor = maxId("t_product");
        orderFloor = maxId("t_order");
        orderItemFloor = maxId("t_order_item");
        couponFloor = maxId("t_coupon");
        userCouponFloor = maxId("t_user_coupon");
    }

    @AfterEach
    void deleteCreatedRows() {
        // 子先于父。此 schema 无外键，顺序错了 MySQL 不拦——所以更要写对。
        jdbc.update("DELETE FROM t_user_coupon WHERE id > ?", userCouponFloor);
        jdbc.update("DELETE FROM t_order_item WHERE id > ?", orderItemFloor);
        jdbc.update("DELETE FROM t_order WHERE id > ?", orderFloor);
        jdbc.update("DELETE FROM t_product WHERE id > ?", productFloor);
        jdbc.update("DELETE FROM t_coupon WHERE id > ?", couponFloor);
        jdbc.update("DELETE FROM t_user WHERE id > ?", userFloor);
    }

    // ---- 抵扣 ----

    @Test
    @DisplayName("用满减券下单：实付扣减、优惠额落库、券被核销")
    void appliesFullReductionCoupon() {
        long userId = createUser("order_coupon_ok");
        long productId = createProduct("用券商品-满减", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-满减", 0, "50.00", "10.00", 10));

        Order order = orderService.createOrder(userId, orderOf(productId, 1, userCouponId));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("77.90");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getRealAmount()).isEqualByComparingTo("67.90");
        assertThat(order.getTotalAmount().subtract(order.getDiscountAmount()))
                .as("不变式：总额 - 优惠 = 实付")
                .isEqualByComparingTo(order.getRealAmount());

        assertThat(order.getUserCouponId()).isEqualTo(userCouponId);
        assertThat(order.getCouponId()).as("券模板 id 也要落库，供按券统计").isNotNull();

        assertThat(userCouponStatus(userCouponId)).as("券应被核销").isEqualTo(1);
        assertThat(userCouponUseTime(userCouponId)).as("核销时间应被写入").isNotNull();
    }

    @Test
    @DisplayName("折扣券按实付比例算，逐分精确")
    void appliesDiscountCoupon() {
        long userId = createUser("order_coupon_discount");
        long productId = createProduct("用券商品-折扣", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-九折", 1, "0.00", "0.90", 10));

        Order order = orderService.createOrder(userId, orderOf(productId, 1, userCouponId));

        // 77.90 × 0.90 = 70.110 → 70.11；优惠 = 77.90 - 70.11 = 7.79
        assertThat(order.getRealAmount()).isEqualByComparingTo("70.11");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("7.79");
    }

    @Test
    @DisplayName("不用券的订单优惠额为 0.00，不是 null")
    void noCouponMeansZeroDiscount() {
        long userId = createUser("order_coupon_none");
        long productId = createProduct("用券商品-无券", 100, "77.90");

        Order order = orderService.createOrder(userId, orderOf(productId, 1, null));

        assertThat(order.getDiscountAmount()).as("显式 0.00，前端不必处理 null").isEqualByComparingTo("0.00");
        assertThat(order.getRealAmount()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(order.getCouponId()).isNull();
        assertThat(order.getUserCouponId()).isNull();
    }

    // ---- 拒绝路径 ----

    @Test
    @DisplayName("同一张券不能重复使用，且第二次不产生订单")
    void rejectsCouponReuse() {
        long userId = createUser("order_coupon_reuse");
        long productId = createProduct("用券商品-复用", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-复用", 0, "50.00", "10.00", 10));

        orderService.createOrder(userId, orderOf(productId, 1, userCouponId));
        long ordersBefore = orderCountOf(userId);

        BusinessException error = catchThrowableOfType(
                () -> orderService.createOrder(userId, orderOf(productId, 1, userCouponId)),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
        assertThat(orderCountOf(userId)).as("失败的第二次不应留下订单").isEqualTo(ordersBefore);
    }

    @Test
    @DisplayName("用别人的券报 COUPON_NOT_FOUND，不泄露券是否存在")
    void rejectsSomeoneElsesCoupon() {
        long owner = createUser("order_coupon_owner");
        long intruder = createUser("order_coupon_intruder");
        long productId = createProduct("用券商品-越权", 100, "77.90");
        long userCouponId = receiveCoupon(owner, createCoupon("用券券-越权", 0, "50.00", "10.00", 10));

        BusinessException error = catchThrowableOfType(
                () -> orderService.createOrder(intruder, orderOf(productId, 1, userCouponId)),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND.getCode());
        assertThat(userCouponStatus(userCouponId)).as("券不能被他人消耗掉").isZero();
        assertThat(stockOf(productId)).as("失败的下单不应扣库存").isEqualTo(100);
    }

    @Test
    @DisplayName("订单金额没达到门槛时不能用券")
    void rejectsBelowThreshold() {
        long userId = createUser("order_coupon_threshold");
        long productId = createProduct("用券商品-门槛", 100, "49.99");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-门槛50", 0, "50.00", "10.00", 10));

        BusinessException error = catchThrowableOfType(
                () -> orderService.createOrder(userId, orderOf(productId, 1, userCouponId)),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE.getCode());
        assertThat(orderCountOf(userId)).isZero();
        assertThat(stockOf(productId)).as("失败的下单不应扣库存").isEqualTo(100);
    }

    @Test
    @DisplayName("并发用同一张券下单：只有一单成功，库存只扣一件")
    void claimsCouponExactlyOnceUnderConcurrency() throws Exception {
        int threads = 8;
        long userId = createUser("order_coupon_concurrent");
        long productId = createProduct("用券商品-并发", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-并发", 0, "50.00", "10.00", 10));

        int succeeded = runConcurrently(threads, () -> orderService.createOrder(userId, orderOf(productId, 1, userCouponId)));

        assertThat(succeeded).as("一张券只能撑起一单").isEqualTo(1);
        assertThat(orderCountOf(userId)).as("只应产生一张订单").isEqualTo(1);
        assertThat(stockOf(productId)).as("库存只应扣 1，其余线程在占券那步就失败了").isEqualTo(99);
        assertThat(userCouponStatus(userCouponId)).isEqualTo(1);
    }

    // ---- 取消退券 ----

    @Test
    @DisplayName("取消订单把券退回未使用，且券可以再次下单使用")
    void cancelRestoresCoupon() {
        long userId = createUser("order_coupon_cancel");
        long productId = createProduct("用券商品-取消", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-取消", 0, "50.00", "10.00", 10));

        Order first = orderService.createOrder(userId, orderOf(productId, 1, userCouponId));
        assertThat(userCouponStatus(userCouponId)).isEqualTo(1);

        orderService.cancelOrder(first.getId(), userId, true);

        assertThat(userCouponStatus(userCouponId)).as("券应退回未使用").isZero();
        assertThat(userCouponUseTime(userCouponId)).as("核销时间应被清空").isNull();
        assertThat(stockOf(productId)).as("库存应退回").isEqualTo(100);

        // 退回的券必须能再次使用——否则「退券」只是把状态改回去给人看的
        Order second = orderService.createOrder(userId, orderOf(productId, 1, userCouponId));
        assertThat(second.getRealAmount()).isEqualByComparingTo("67.90");
        assertThat(userCouponStatus(userCouponId)).isEqualTo(1);
    }

    @Test
    @DisplayName("重复取消同一订单：只成功一次，券不会被反复回退")
    void cancelTwiceRestoresCouponOnce() {
        long userId = createUser("order_coupon_cancel_twice");
        long productId = createProduct("用券商品-重复取消", 100, "77.90");
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-重复取消", 0, "50.00", "10.00", 10));

        Order order = orderService.createOrder(userId, orderOf(productId, 1, userCouponId));
        orderService.cancelOrder(order.getId(), userId, true);

        BusinessException error = catchThrowableOfType(
                () -> orderService.cancelOrder(order.getId(), userId, true), BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL.getCode());
        assertThat(userCouponStatus(userCouponId)).as("券仍然是退回后的未使用态").isZero();
        assertThat(stockOf(productId)).as("库存只回滚一次，不能凭空多出来").isEqualTo(100);
    }

    @Test
    @DisplayName("不用券的订单取消时不会误动任何券")
    void cancelWithoutCouponIsHarmless() {
        long userId = createUser("order_coupon_cancel_none");
        long productId = createProduct("用券商品-无券取消", 100, "77.90");
        // 该用户手里有一张券，但本单没用
        long userCouponId = receiveCoupon(userId, createCoupon("用券券-未使用", 0, "50.00", "10.00", 10));

        Order order = orderService.createOrder(userId, orderOf(productId, 1, null));
        orderService.cancelOrder(order.getId(), userId, true);

        assertThat(userCouponStatus(userCouponId)).as("没用过的券应保持未使用").isZero();
        assertThat(stockOf(productId)).isEqualTo(100);
    }

    // ---- 工具 ----

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

    private OrderDTO orderOf(long productId, int quantity, Long userCouponId) {
        OrderDTO dto = new OrderDTO();
        dto.setReceiver("张三");
        dto.setPhone("13800000000");
        dto.setAddress("北京市朝阳区某街道 1 号");
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        dto.setUserCouponId(userCouponId);
        return dto;
    }

    /** 走真实领券路径拿到 t_user_coupon.id —— 结算页提交的就是这个值。 */
    private long receiveCoupon(long userId, long couponId) {
        couponService.receive(userId, couponId);
        Long id = jdbc.queryForObject("SELECT id FROM t_user_coupon WHERE user_id = ? AND coupon_id = ?",
                Long.class, userId, couponId);
        if (id == null) {
            throw new AssertionError("领券后查不到用户券记录");
        }
        return id;
    }

    private long createUser(String username) {
        jdbc.update("INSERT INTO t_user (username, password, role, status) VALUES (?, 'x', 0, 1)", username);
        return requireLong("SELECT id FROM t_user WHERE username = ?", username);
    }

    /** 不建 SKU 的商品：下单时 skuId 传 null 即可，价格走 t_product.price。 */
    private long createProduct(String name, int stock, String price) {
        jdbc.update("INSERT INTO t_product (category_id, name, price, stock, status, sales) VALUES (1, ?, ?, ?, 1, 0)",
                name, new BigDecimal(price), stock);
        return requireLong("SELECT id FROM t_product WHERE name = ?", name);
    }

    private long createCoupon(String name, int type, String minAmount, String discountValue, int totalCount) {
        jdbc.update("INSERT INTO t_coupon (name, type, min_amount, discount_value, total_count, received_count,"
                        + " status, start_time, end_time) VALUES (?, ?, ?, ?, ?, 0, 1, ?, ?)",
                name, type, new BigDecimal(minAmount), new BigDecimal(discountValue), totalCount,
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
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

    private int stockOf(long productId) {
        return intOf("SELECT stock FROM t_product WHERE id = ?", productId);
    }

    private int orderCountOf(long userId) {
        return intOf("SELECT COUNT(*) FROM t_order WHERE user_id = ?", userId);
    }

    private Integer userCouponStatus(long userCouponId) {
        return jdbc.queryForObject("SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, userCouponId);
    }

    private Object userCouponUseTime(long userCouponId) {
        return jdbc.queryForObject("SELECT use_time FROM t_user_coupon WHERE id = ?", Object.class, userCouponId);
    }
}
