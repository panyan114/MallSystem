package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.dto.OrderDTO;
import com.mall.dto.OrderItemDTO;
import com.mall.entity.Cart;
import com.mall.entity.Order;
import com.mall.entity.OrderItem;
import com.mall.entity.Product;
import com.mall.entity.Sku;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.mapper.CartMapper;
import com.mall.mapper.OrderItemMapper;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.SkuMapper;
import com.mall.service.CouponService;
import com.mall.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 订单状态流转：0 待付款 → 1 待发货 → 2 待收货 → 3 已完成；0 → 4 已取消。 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_UNSHIPPED = 1;
    private static final int STATUS_UNRECEIVED = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_CANCELLED = 4;

    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CartMapper cartMapper;
    private final CouponService couponService;

    public OrderServiceImpl(OrderItemMapper orderItemMapper,
                            ProductMapper productMapper,
                            SkuMapper skuMapper,
                            CartMapper cartMapper,
                            CouponService couponService) {
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.cartMapper = cartMapper;
        this.couponService = couponService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, OrderDTO orderDTO) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        validateOrder(orderDTO);

        Map<String, OrderLine> lines = mergeOrderItems(orderDTO.getItems());
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "订单商品不能为空");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderLine line : lines.values()) {
            Product product = productMapper.selectById(line.productId);
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "商品不存在或已下架");
            }

            Sku sku = resolveSku(product.getId(), line.skuId);
            if (line.skuId == null && hasSku(product.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请选择商品规格");
            }
            if (line.skuId != null && sku == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品规格不存在");
            }

            int availableStock = sku == null ? safeStock(product.getStock()) : safeStock(sku.getStock());
            if (line.quantity > availableStock) {
                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK.getCode(),
                        product.getName() + " 库存不足");
            }

            BigDecimal price = sku != null && sku.getPrice() != null
                    ? sku.getPrice()
                    : (product.getSalePrice() != null ? product.getSalePrice() : product.getPrice());
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(line.quantity));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setSkuId(line.skuId);
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(sku != null && hasText(sku.getImage()) ? sku.getImage() : firstImage(product.getImages()));
            orderItem.setSkuDesc(sku == null ? "默认规格" : sku.getSpecDesc());
            orderItem.setPrice(price);
            orderItem.setQuantity(line.quantity);
            orderItem.setTotalPrice(subtotal);
            orderItems.add(orderItem);
        }

        // 占券放在 save 之前：一是要拿到 totalAmount 才能判使用门槛，二是失败时在一条便宜的
        // UPDATE 上就返回了，不必先插订单再回滚（那会烧掉自增 id 并长时间持有唯一索引锁）。
        // reserve 走 REQUIRED 传播加入本方法的事务，后续任何一步失败都会把占券一并回滚。
        CouponService.CouponReservation reservation = orderDTO.getUserCouponId() == null
                ? null
                : couponService.reserve(userId, orderDTO.getUserCouponId(), totalAmount);
        // 不用券时也要显式给 0.00：MyBatis-Plus 会跳过 null 字段，留 null 会让接口返回
        // "discountAmount": null 而不是 0.00，前端得处理两种空值。
        BigDecimal discountAmount = reservation == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : reservation.discountAmount();

        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setCouponId(reservation == null ? null : reservation.couponId());
        order.setUserCouponId(reservation == null ? null : reservation.userCouponId());
        order.setDiscountAmount(discountAmount);
        // 不变式：totalAmount - discountAmount == realAmount，由 CouponCalculator 保证逐分精确
        order.setRealAmount(totalAmount.subtract(discountAmount));
        order.setStatus(STATUS_UNPAID);
        order.setAddress(orderDTO.getAddress().trim());
        order.setReceiver(orderDTO.getReceiver().trim());
        order.setPhone(orderDTO.getPhone().trim());
        order.setRemark(orderDTO.getRemark());
        order.setCreateTime(LocalDateTime.now());
        save(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
            decreaseStock(item.getProductId(), item.getSkuId(), item.getQuantity());
        }

        if (Boolean.TRUE.equals(orderDTO.getClearCart())) {
            removeOrderedCartItems(userId, orderItems);
        }
        return order;
    }

    @Override
    public Map<String, Object> getOrderDetail(Long orderId, Long userId, boolean onlyOwned) {
        Order order = requireOrder(orderId, userId, onlyOwned);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, Long userId, boolean onlyOwned) {
        Order order = requireOrder(orderId, userId, onlyOwned);

        // 先「抢占」状态，再回滚库存。
        // 把「判断 status==0」和「写入 status=4」合并成一条带 WHERE 条件的 UPDATE：
        // 并发重复取消时只有一方能影响到 1 行，另一方拿到 0 行直接失败，
        // 从而保证下面的库存回滚恰好执行一次。
        // （如果先查再判再写，两次请求会都读到 0、都通过检查，库存就被回滚两遍。）
        int updated = updateOrderStatus(orderId, STATUS_UNPAID, STATUS_CANCELLED, null);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        // 退回优惠券。用户此时一分钱没付（唯一起点是「待付款」），退券才是回到下单前的状态。
        // 与 restoreStock 依赖同一个保证：状态「抢占」成功者只有一个，所以回退体只跑一次。
        // 用 order.getUserId() 而非入参 userId —— 店主替消费者取消时，券是属于下单人的。
        couponService.restore(order.getUserId(), order.getUserCouponId());

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            restoreStock(item.getProductId(), item.getSkuId(), item.getQuantity());
        }
        return true;
    }

    @Override
    public boolean confirmOrder(Long orderId, Long userId, boolean onlyOwned) {
        requireOrder(orderId, userId, onlyOwned);
        LocalDateTime now = LocalDateTime.now();
        int updated = updateOrderStatus(orderId, STATUS_UNRECEIVED, STATUS_COMPLETED,
                wrapper -> wrapper.set(Order::getConfirmTime, now));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "当前订单不能确认收货");
        }
        return true;
    }

    @Override
    public boolean payOrder(Long orderId, Long userId, boolean onlyOwned) {
        requireOrder(orderId, userId, onlyOwned);
        LocalDateTime now = LocalDateTime.now();
        int updated = updateOrderStatus(orderId, STATUS_UNPAID, STATUS_UNSHIPPED,
                wrapper -> wrapper.set(Order::getPayTime, now));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "当前订单不能支付");
        }
        return true;
    }

    @Override
    public boolean shipOrder(Long orderId) {
        requireOrder(orderId, null, false);
        LocalDateTime now = LocalDateTime.now();
        int updated = updateOrderStatus(orderId, STATUS_UNSHIPPED, STATUS_UNRECEIVED,
                wrapper -> wrapper.set(Order::getShipTime, now));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "当前订单不能发货");
        }
        return true;
    }

    /**
     * 原子的状态流转：仅当订单当前正处于 {@code expectedStatus} 时才迁移到 {@code targetStatus}。
     *
     * @param extraSets 随状态一起写入的附加字段（如 payTime/shipTime/confirmTime），可为 null
     * @return 实际影响的行数，0 表示订单不在预期状态（并发冲突或非法流转）
     */
    private int updateOrderStatus(Long orderId, int expectedStatus, int targetStatus,
                                  Consumer<LambdaUpdateWrapper<Order>> extraSets) {
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, expectedStatus)
                .set(Order::getStatus, targetStatus);
        if (extraSets != null) {
            extraSets.accept(wrapper);
        }
        return baseMapper.update(null, wrapper);
    }

    private Order requireOrder(Long orderId, Long userId, boolean onlyOwned) {
        Order order = getById(orderId);
        if (order == null || (onlyOwned && !order.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void validateOrder(OrderDTO orderDTO) {
        if (orderDTO == null || orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "订单商品不能为空");
        }
        if (!hasText(orderDTO.getReceiver())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请填写收货人");
        }
        if (!hasText(orderDTO.getPhone())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请填写手机号");
        }
        if (!hasText(orderDTO.getAddress())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请填写收货地址");
        }
    }

    private Map<String, OrderLine> mergeOrderItems(List<OrderItemDTO> items) {
        Map<String, OrderLine> result = new LinkedHashMap<>();
        for (OrderItemDTO item : items) {
            if (item == null || item.getProductId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品ID不能为空");
            }
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品数量必须大于0");
            }
            String key = item.getProductId() + ":" + (item.getSkuId() == null ? "default" : item.getSkuId());
            OrderLine line = result.computeIfAbsent(key,
                    ignored -> new OrderLine(item.getProductId(), item.getSkuId()));
            line.quantity += quantity;
        }
        return result;
    }

    private Sku resolveSku(Long productId, Long skuId) {
        if (skuId == null) {
            return null;
        }
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            return null;
        }
        return sku;
    }

    private boolean hasSku(Long productId) {
        Long count = skuMapper.selectCount(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId));
        return count != null && count > 0;
    }

    private void decreaseStock(Long productId, Long skuId, Integer quantity) {
        int productUpdated = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .ge(Product::getStock, quantity)
                .setSql("stock = stock - " + quantity)
                .setSql("sales = sales + " + quantity));
        if (productUpdated == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        if (skuId != null) {
            int skuUpdated = skuMapper.update(null, new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, skuId)
                    .ge(Sku::getStock, quantity)
                    .setSql("stock = stock - " + quantity));
            if (skuUpdated == 0) {
                throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
            }
        }
    }

    private void restoreStock(Long productId, Long skuId, Integer quantity) {
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("stock = stock + " + quantity)
                .setSql("sales = GREATEST(sales - " + quantity + ", 0)"));
        if (skuId != null) {
            skuMapper.update(null, new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, skuId)
                    .setSql("stock = stock + " + quantity));
        }
    }

    private void removeOrderedCartItems(Long userId, List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, userId)
                    .eq(Cart::getProductId, item.getProductId());
            if (item.getSkuId() == null) {
                wrapper.isNull(Cart::getSkuId);
            } else {
                wrapper.eq(Cart::getSkuId, item.getSkuId());
            }
            cartMapper.delete(wrapper);
        }
    }

    private String nextOrderNo() {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return LocalDateTime.now().format(ORDER_NO_TIME) + suffix;
    }

    private int safeStock(Integer stock) {
        return stock == null ? 0 : Math.max(stock, 0);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstImage(String images) {
        if (!hasText(images)) {
            return "";
        }
        String trimmed = images.trim();
        if (trimmed.startsWith("[")) {
            int start = trimmed.indexOf('"');
            int end = trimmed.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end);
            }
        }
        return trimmed;
    }

    private static class OrderLine {
        private final Long productId;
        private final Long skuId;
        private int quantity;

        private OrderLine(Long productId, Long skuId) {
            this.productId = productId;
            this.skuId = skuId;
        }
    }
}
