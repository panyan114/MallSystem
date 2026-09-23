package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.AuthUser;
import com.mall.auth.RequiresAdmin;
import com.mall.dto.OrderDTO;
import com.mall.entity.Order;
import com.mall.service.OrderService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result create(@RequestBody OrderDTO orderDTO,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        return Result.success(orderService.createOrder(authUser.id(), orderDTO));
    }

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(defaultValue = "false") Boolean mine,
                       @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (!authUser.isAdmin() || Boolean.TRUE.equals(mine)) {
            wrapper.eq(Order::getUserId, authUser.id());
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getId);

        Page<Order> result = orderService.page(new Page<>(page, size), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "false") Boolean mine,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean onlyOwned = !authUser.isAdmin() || Boolean.TRUE.equals(mine);
        return Result.success(orderService.getOrderDetail(id, authUser.id(), onlyOwned));
    }

    @PutMapping("/{id}/cancel")
    public Result cancel(@PathVariable Long id,
                         @RequestParam(defaultValue = "false") Boolean mine,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean onlyOwned = !authUser.isAdmin() || Boolean.TRUE.equals(mine);
        orderService.cancelOrder(id, authUser.id(), onlyOwned);
        return Result.success();
    }

    @PostMapping("/{id}/pay")
    public Result pay(@PathVariable Long id,
                      @RequestParam(defaultValue = "true") Boolean mine,
                      @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean onlyOwned = !authUser.isAdmin() || Boolean.TRUE.equals(mine);
        orderService.payOrder(id, authUser.id(), onlyOwned);
        return Result.success();
    }

    @PutMapping("/{id}/confirm")
    public Result confirm(@PathVariable Long id,
                          @RequestParam(defaultValue = "true") Boolean mine,
                          @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean onlyOwned = !authUser.isAdmin() || Boolean.TRUE.equals(mine);
        orderService.confirmOrder(id, authUser.id(), onlyOwned);
        return Result.success();
    }

    @RequiresAdmin
    @PostMapping("/{id}/ship")
    public Result ship(@PathVariable Long id) {
        orderService.shipOrder(id);
        return Result.success();
    }

    @PostMapping("/{id}/refund")
    public Result refund(@PathVariable Long id) {
        return Result.success();
    }
}
