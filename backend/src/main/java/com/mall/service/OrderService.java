package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.OrderDTO;
import com.mall.entity.Order;

import java.util.Map;

public interface OrderService extends IService<Order> {

    Order createOrder(Long userId, OrderDTO orderDTO);

    Map<String, Object> getOrderDetail(Long orderId, Long userId, boolean onlyOwned);

    boolean cancelOrder(Long orderId, Long userId, boolean onlyOwned);

    boolean payOrder(Long orderId, Long userId, boolean onlyOwned);

    boolean confirmOrder(Long orderId, Long userId, boolean onlyOwned);

    boolean shipOrder(Long orderId);
}
