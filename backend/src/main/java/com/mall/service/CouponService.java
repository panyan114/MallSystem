package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.CouponDTO;
import com.mall.entity.Coupon;
import com.mall.vo.CouponVO;
import com.mall.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService extends IService<Coupon> {

    /** 店主创建优惠券，返回新券 id。 */
    Long createCoupon(CouponDTO couponDTO);

    /** 用户领券。已领过报 4004，领完报 4005。 */
    void receive(Long userId, Long couponId);

    /** 领券中心：当前可领取的券（含已领取的，前端据此渲染置灰按钮）。 */
    List<CouponVO> listAvailable(Long userId);

    /** 我的优惠券。status 为读时派生，可能返回 2=已过期。 */
    List<UserCouponVO> myCoupons(Long userId);

    /**
     * 下单时占券：校验 + 算价 + 把券置为已使用。调用方必须是已开启事务的
     * {@code OrderServiceImpl.createOrder}，本方法通过默认的 REQUIRED 传播加入它的事务——
     * 订单创建失败时占券会一并回滚。
     */
    CouponReservation reserve(Long userId, Long userCouponId, BigDecimal orderAmount);

    /**
     * 取消订单时退券，把券置回未使用。同样加入调用方的事务。
     * 券不是本单所占的（状态不为已使用）时静默跳过，不报错。
     */
    void restore(Long userId, Long userCouponId);

    /** 占券结果。{@code couponId} 是券模板 id（入订单用于统计），{@code userCouponId} 是回退指针。 */
    record CouponReservation(Long couponId, Long userCouponId, BigDecimal discountAmount) {
    }
}
