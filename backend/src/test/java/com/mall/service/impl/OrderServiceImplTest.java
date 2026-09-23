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
 * 下单与订单状态流转的集成测试，跑在真实 MySQL 上。
 *
 * <p>这里刻意不 mock Mapper：被验证的东西本身就在 SQL 里——
 * {@code UPDATE t_product SET stock = stock - n WHERE id = ? AND stock >= n} 影响 0 行
 * 才算超卖，这种保证只有真的打到 InnoDB 上才算数。
 */
@DisplayName("OrderServiceImpl 下单与状态流转")
class OrderServiceImplTest extends AbstractIntegrationTest {

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private JdbcTemplate jdbc;

    private long userFloor;
    private long productFloor;
    private long skuFloor;
    private long orderFloor;
    private long orderItemFloor;
    private long cartFloor;

    @BeforeEach
    void recordIdFloors() {
        userFloor = maxId("t_user");
        productFloor = maxId("t_product");
        skuFloor = maxId("t_sku");
        orderFloor = maxId("t_order");
        orderItemFloor = maxId("t_order_item");
        cartFloor = maxId("t_cart");
    }

    /**
     * 所有测试共用同一个数据库实例（容器是 shared 的），所以每个用例造的
     * 数据都必须按 id 水位线删干净，否则会污染后面的用例。
     */
    @AfterEach
    void deleteCreatedRows() {
        jdbc.update("DELETE FROM t_order_item WHERE id > ?", orderItemFloor);
        jdbc.update("DELETE FROM t_cart WHERE id > ?", cartFloor);
        jdbc.update("DELETE FROM t_order WHERE id > ?", orderFloor);
        jdbc.update("DELETE FROM t_sku WHERE id > ?", skuFloor);
        jdbc.update("DELETE FROM t_product WHERE id > ?", productFloor);
        jdbc.update("DELETE FROM t_user WHERE id > ?", userFloor);
    }

    // ---------------------------------------------------------------- 库存

    @Test
    @DisplayName("库存不足时下单失败，且不留下任何订单")
    void rejectsOrderWhenStockInsufficient() {
        long userId = createUser("buyer_stock");
        long productId = createProduct("库存紧张的商品", 2, "10.00");

        BusinessException error = catchThrowableOfType(
                () -> orderService.createOrder(userId, orderOf(productId, 3)),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode());
        assertThat(stockOf(productId)).isEqualTo(2);
        assertThat(orderCountOf(userId)).isZero();
    }

    @Test
    @DisplayName("同一个商品拆成多行提交，会被合并后只扣一次库存")
    void mergesDuplicateLinesBeforeDeducting() {
        long userId = createUser("buyer_merge");
        long productId = createProduct("重复下单的商品", 10, "10.00");

        OrderDTO dto = new OrderDTO();
        dto.setReceiver("张三");
        dto.setPhone("13800000000");
        dto.setAddress("北京市朝阳区");
        // 同一个商品分成两行：2 + 3，应合并成一行 5 件，库存只扣 5
        dto.setItems(List.of(item(productId, 2), item(productId, 3)));

        Order order = orderService.createOrder(userId, dto);

        assertThat(orderItemCountOf(order.getId())).isEqualTo(1);
        assertThat(orderItemQuantityOf(order.getId())).isEqualTo(5);
        assertThat(stockOf(productId)).isEqualTo(5);
        assertThat(salesOf(productId)).isEqualTo(5);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("并发下单不会超卖：N 个线程抢 M 件库存，恰好 M 个成功")
    void doesNotOversellUnderConcurrency() throws Exception {
        int stock = 5;
        int threads = 12;
        long userId = createUser("buyer_concurrent");
        long productId = createProduct("秒杀商品", stock, "10.00");

        int succeeded = runConcurrently(threads,
                () -> orderService.createOrder(userId, orderOf(productId, 1)));

        assertThat(succeeded).as("成功下单数应恰好等于库存数").isEqualTo(stock);
        assertThat(stockOf(productId)).as("库存应扣到 0，不能为负").isZero();
        assertThat(salesOf(productId)).isEqualTo(stock);
        assertThat(orderCountOf(userId)).as("成功订单数应与扣减的库存数一致").isEqualTo(stock);
    }

    @Test
    @DisplayName("带 SKU 的商品下单时，商品库存和 SKU 库存同步扣减")
    void deductsBothProductAndSkuStock() {
        long userId = createUser("buyer_sku");
        long productId = createProduct("多规格商品", 20, "10.00");
        long skuId = createSku(productId, "红色", 8, "12.00");

        orderService.createOrder(userId, orderOf(productId, 3, skuId));

        assertThat(stockOf(productId)).isEqualTo(17);
        assertThat(skuStockOf(skuId)).isEqualTo(5);
    }

    // ------------------------------------------------------------ 取消订单

    @Test
    @DisplayName("并发取消同一订单：只有一次成功，库存只回滚一次")
    void cancelsExactlyOnceUnderConcurrency() throws Exception {
        long userId = createUser("buyer_cancel");
        long productId = createProduct("待取消的商品", 10, "10.00");

        Order order = orderService.createOrder(userId, orderOf(productId, 2));
        assertThat(stockOf(productId)).isEqualTo(8);

        // 8 个线程同时点「取消订单」——这正是修复前会把库存回滚 8 次的场景
        int succeeded = runConcurrently(8,
                () -> orderService.cancelOrder(order.getId(), userId, true));

        assertThat(succeeded).as("取消操作只能有一次成功").isEqualTo(1);
        assertThat(stockOf(productId)).as("库存只能回滚一次，回到 10 而不是更多").isEqualTo(10);
        assertThat(salesOf(productId)).as("销量不能变成负数").isZero();
        assertThat(statusOf(order.getId())).isEqualTo(4);
    }

    @Test
    @DisplayName("已支付的订单不能取消")
    void cannotCancelPaidOrder() {
        long userId = createUser("buyer_cancel_paid");
        long productId = createProduct("已付款商品", 10, "10.00");
        Order order = orderService.createOrder(userId, orderOf(productId, 1));

        orderService.payOrder(order.getId(), userId, true);

        BusinessException error = catchThrowableOfType(
                () -> orderService.cancelOrder(order.getId(), userId, true),
                BusinessException.class);
        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL.getCode());
        // 取消失败不应该动库存
        assertThat(stockOf(productId)).isEqualTo(9);
    }

    // ------------------------------------------------------------ 状态流转

    @Test
    @DisplayName("状态流转按时序推进，并写入对应时间戳")
    void advancesStatusAndStampsTimestamps() {
        long userId = createUser("buyer_flow");
        long productId = createProduct("正常商品", 10, "10.00");
        Order order = orderService.createOrder(userId, orderOf(productId, 1));

        assertThat(statusOf(order.getId())).isZero();
        assertThat(payTimeOf(order.getId())).isNull();

        orderService.payOrder(order.getId(), userId, true);
        assertThat(statusOf(order.getId())).isEqualTo(1);
        assertThat(payTimeOf(order.getId())).isNotNull();

        orderService.shipOrder(order.getId());
        assertThat(statusOf(order.getId())).isEqualTo(2);
        assertThat(shipTimeOf(order.getId())).isNotNull();

        orderService.confirmOrder(order.getId(), userId, true);
        assertThat(statusOf(order.getId())).isEqualTo(3);
        assertThat(confirmTimeOf(order.getId())).isNotNull();
    }

    @Test
    @DisplayName("重复支付、跳步确认收货都会被条件更新挡下")
    void rejectsIllegalTransitions() {
        long userId = createUser("buyer_illegal");
        long productId = createProduct("非法流转商品", 10, "10.00");
        Order order = orderService.createOrder(userId, orderOf(productId, 1));
        orderService.payOrder(order.getId(), userId, true);

        // 已支付（状态 1）不能再支付一次
        assertThat(catchThrowableOfType(
                () -> orderService.payOrder(order.getId(), userId, true),
                BusinessException.class)).isNotNull();

        // 还没发货（状态 1，期望 2）不能直接确认收货
        assertThat(catchThrowableOfType(
                () -> orderService.confirmOrder(order.getId(), userId, true),
                BusinessException.class)).isNotNull();

        // 失败的流转不能篡改状态
        assertThat(statusOf(order.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("不能查看别人的订单")
    void cannotReadOthersOrder() {
        long owner = createUser("buyer_owner");
        long other = createUser("buyer_other");
        long productId = createProduct("他人订单商品", 10, "10.00");
        Order order = orderService.createOrder(owner, orderOf(productId, 1));

        BusinessException error = catchThrowableOfType(
                () -> orderService.getOrderDetail(order.getId(), other, true),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND.getCode());
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 让 {@code threads} 个线程尽量同时执行 {@code action}，返回其中没有抛
     * {@link BusinessException} 的次数（即业务上「成功」的次数）。
     *
     * <p>非 BusinessException 的异常会被直接抛出——像死锁、连接超时这类问题
     * 必须让测试失败，不能混进「失败的那一方」里被悄悄忽略。
     */
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

    private OrderDTO orderOf(long productId, int quantity) {
        return orderOf(productId, quantity, null);
    }

    private OrderDTO orderOf(long productId, int quantity, Long skuId) {
        OrderDTO dto = new OrderDTO();
        dto.setReceiver("张三");
        dto.setPhone("13800000000");
        dto.setAddress("北京市朝阳区某街道 1 号");
        dto.setItems(List.of(item(productId, quantity, skuId)));
        return dto;
    }

    private OrderItemDTO item(long productId, int quantity) {
        return item(productId, quantity, null);
    }

    private OrderItemDTO item(long productId, int quantity, Long skuId) {
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private long createUser(String username) {
        jdbc.update("INSERT INTO t_user (username, password, role, status) VALUES (?, 'x', 0, 1)", username);
        return requireLong("SELECT id FROM t_user WHERE username = ?", username);
    }

    private long createProduct(String name, int stock, String price) {
        jdbc.update("INSERT INTO t_product (category_id, name, price, stock, status, sales) "
                + "VALUES (1, ?, ?, ?, 1, 0)", name, new BigDecimal(price), stock);
        return requireLong("SELECT id FROM t_product WHERE name = ?", name);
    }

    private long createSku(long productId, String specDesc, int stock, String price) {
        jdbc.update("INSERT INTO t_sku (product_id, spec_desc, price, stock) VALUES (?, ?, ?, ?)",
                productId, specDesc, new BigDecimal(price), stock);
        return requireLong("SELECT id FROM t_sku WHERE product_id = ? AND spec_desc = ?", productId, specDesc);
    }

    private long maxId(String table) {
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return id == null ? 0L : id;
    }

    private long requireLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private int intOf(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int stockOf(long productId) {
        return intOf("SELECT stock FROM t_product WHERE id = ?", productId);
    }

    private int salesOf(long productId) {
        return intOf("SELECT sales FROM t_product WHERE id = ?", productId);
    }

    private int skuStockOf(long skuId) {
        return intOf("SELECT stock FROM t_sku WHERE id = ?", skuId);
    }

    private int statusOf(long orderId) {
        return intOf("SELECT status FROM t_order WHERE id = ?", orderId);
    }

    private int orderCountOf(long userId) {
        return intOf("SELECT COUNT(*) FROM t_order WHERE user_id = ?", userId);
    }

    private int orderItemCountOf(long orderId) {
        return intOf("SELECT COUNT(*) FROM t_order_item WHERE order_id = ?", orderId);
    }

    private int orderItemQuantityOf(long orderId) {
        return intOf("SELECT quantity FROM t_order_item WHERE order_id = ?", orderId);
    }

    private Object payTimeOf(long orderId) {
        return jdbc.queryForObject("SELECT pay_time FROM t_order WHERE id = ?", Object.class, orderId);
    }

    private Object shipTimeOf(long orderId) {
        return jdbc.queryForObject("SELECT ship_time FROM t_order WHERE id = ?", Object.class, orderId);
    }

    private Object confirmTimeOf(long orderId) {
        return jdbc.queryForObject("SELECT confirm_time FROM t_order WHERE id = ?", Object.class, orderId);
    }
}
