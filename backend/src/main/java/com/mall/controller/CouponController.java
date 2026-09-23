package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.RequiresAdmin;
import com.mall.entity.Coupon;
import com.mall.service.CouponService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @RequiresAdmin
    @GetMapping("/list")
    public Result list() {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Coupon::getId);
        List<Coupon> coupons = couponService.list(wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", coupons);
        data.put("total", coupons.size());
        return Result.success(data);
    }

    @RequiresAdmin
    @PostMapping
    public Result create(@RequestBody Coupon coupon) {
        couponService.save(coupon);
        return Result.success();
    }

    @PostMapping("/{id}/receive")
    public Result receive(@PathVariable Long id) {
        return Result.success();
    }

    @GetMapping("/my")
    public Result myCoupons() {
        return Result.success(java.util.Collections.emptyList());
    }
}
