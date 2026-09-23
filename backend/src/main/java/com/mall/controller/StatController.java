package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.RequiresAdmin;
import com.mall.entity.Order;
import com.mall.entity.Product;
import com.mall.entity.User;
import com.mall.mapper.OrderMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.UserMapper;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stat")
@RequiresAdmin
public class StatController {

    private static final DateTimeFormatter TREND_DATE = DateTimeFormatter.ofPattern("MM-dd");

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    public StatController(OrderMapper orderMapper,
                          ProductMapper productMapper,
                          UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    @GetMapping("/dashboard")
    public Result dashboard() {
        List<Order> orders = orderMapper.selectList(null);
        BigDecimal totalSales = orders.stream()
                .filter(this::isPaidOrder)
                .map(Order::getRealAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal todaySales = sumSales(orders, today, today);
        BigDecimal monthSales = sumSales(orders, monthStart, today);
        long todayOrders = countOrders(orders, today, today);
        long monthOrders = countOrders(orders, monthStart, today);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalSales", totalSales);
        data.put("totalOrders", orders.size());
        data.put("totalUsers", safeCount(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1))));
        data.put("totalProducts", safeCount(productMapper.selectCount(null)));
        data.put("todaySales", todaySales);
        data.put("todayOrders", todayOrders);
        data.put("monthSales", monthSales);
        data.put("monthOrders", monthOrders);
        data.put("averageOrderAmount", averageOrderAmount(orders));
        data.put("salesTrend", buildSalesTrend(orders, today));
        data.put("orderStatus", buildOrderStatus(orders));
        return Result.success(data);
    }

    @GetMapping("/product-ranking")
    public Result productRanking() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .orderByDesc(Product::getSales)
                        .orderByDesc(Product::getId)
                        .last("LIMIT 5"));
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Product product : products) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", product.getId());
            item.put("name", product.getName());
            item.put("sales", product.getSales() == null ? 0 : product.getSales());
            item.put("stock", product.getStock() == null ? 0 : product.getStock());
            item.put("price", product.getSalePrice() == null ? product.getPrice() : product.getSalePrice());
            item.put("status", product.getStatus());
            ranking.add(item);
        }
        return Result.success(ranking);
    }

    @GetMapping("/order-stats")
    public Result orderStats() {
        List<Order> orders = orderMapper.selectList(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrders", orders.size());
        data.put("totalSales", orders.stream()
                .filter(this::isPaidOrder)
                .map(Order::getRealAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        data.put("orderStatus", buildOrderStatus(orders));
        return Result.success(data);
    }

    private List<Map<String, Object>> buildSalesTrend(List<Order> orders, LocalDate today) {
        Map<LocalDate, BigDecimal> dailyAmounts = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            dailyAmounts.put(today.minusDays(offset), BigDecimal.ZERO);
        }
        for (Order order : orders) {
            if (!isPaidOrder(order) || order.getCreateTime() == null || order.getRealAmount() == null) {
                continue;
            }
            LocalDate date = order.getCreateTime().toLocalDate();
            if (dailyAmounts.containsKey(date)) {
                dailyAmounts.computeIfPresent(date, (key, amount) -> amount.add(order.getRealAmount()));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : dailyAmounts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey().format(TREND_DATE));
            item.put("amount", entry.getValue());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildOrderStatus(List<Order> orders) {
        String[] labels = {"待付款", "待发货", "待收货", "已完成", "已取消"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int status = 0; status <= 4; status++) {
            int targetStatus = status;
            long count = orders.stream()
                    .filter(order -> order.getStatus() != null && order.getStatus() == targetStatus)
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("name", labels[status]);
            item.put("count", count);
            result.add(item);
        }
        return result;
    }

    private BigDecimal sumSales(List<Order> orders, LocalDate start, LocalDate end) {
        return orders.stream()
                .filter(this::isPaidOrder)
                .filter(order -> order.getCreateTime() != null)
                .filter(order -> {
                    LocalDate date = order.getCreateTime().toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .map(Order::getRealAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long countOrders(List<Order> orders, LocalDate start, LocalDate end) {
        return orders.stream()
                .filter(order -> order.getCreateTime() != null)
                .filter(order -> {
                    LocalDate date = order.getCreateTime().toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .count();
    }

    private BigDecimal averageOrderAmount(List<Order> orders) {
        List<Order> paidOrders = orders.stream().filter(this::isPaidOrder).toList();
        if (paidOrders.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = paidOrders.stream()
                .map(Order::getRealAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(paidOrders.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean isPaidOrder(Order order) {
        Integer status = order.getStatus();
        return status != null && status >= 1 && status <= 3;
    }

    private long safeCount(Long value) {
        return value == null ? 0 : value;
    }
}
