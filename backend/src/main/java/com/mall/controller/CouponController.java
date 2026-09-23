package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.AuthUser;
import com.mall.auth.RequiresAdmin;
import com.mall.dto.CouponDTO;
import com.mall.entity.Coupon;
import com.mall.service.CouponService;
import com.mall.vo.CouponVO;
import com.mall.vo.Result;
import com.mall.vo.UserCouponVO;
import jakarta.validation.Valid;
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

    /** 后台的券列表（含停用和过期的）。消费者视角的可领券走 /available。 */
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
    public Result create(@Valid @RequestBody CouponDTO couponDTO) {
        return Result.success(couponService.createCoupon(couponDTO));
    }

    /**
     * 领券中心：当前可领取的券。
     *
     * <p>已领取的也会返回（带 {@code received=true}），前端据此渲染置灰的「已领取」按钮——
     * 过滤掉的话用户就看不到自己领过什么了。
     *
     * <p>需要登录而不放进公开白名单：它要用 userId 才能算出 received 标记。
     */
    @GetMapping("/available")
    public Result available(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        List<CouponVO> coupons = couponService.listAvailable(authUser.id());
        Map<String, Object> data = new HashMap<>();
        data.put("list", coupons);
        data.put("total", coupons.size());
        return Result.success(data);
    }

    @PostMapping("/{id}/receive")
    public Result receive(@PathVariable Long id,
                          @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        couponService.receive(authUser.id(), id);
        return Result.success();
    }

    /** 我的优惠券。返回 {list,total} 信封，与其他列表接口保持一致。 */
    @GetMapping("/my")
    public Result myCoupons(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        List<UserCouponVO> coupons = couponService.myCoupons(authUser.id());
        Map<String, Object> data = new HashMap<>();
        data.put("list", coupons);
        data.put("total", coupons.size());
        return Result.success(data);
    }
}
