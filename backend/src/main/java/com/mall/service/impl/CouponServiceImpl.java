package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.dto.CouponDTO;
import com.mall.entity.Coupon;
import com.mall.entity.UserCoupon;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.mapper.CouponMapper;
import com.mall.mapper.UserCouponMapper;
import com.mall.service.CouponService;
import com.mall.vo.CouponVO;
import com.mall.vo.UserCouponVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    /** t_user_coupon.status 的取值。2 只由 {@link #toUserCouponVO} 在读取时派生出来。 */
    private static final int USER_COUPON_UNUSED = 0;
    private static final int USER_COUPON_USED = 1;
    private static final int USER_COUPON_EXPIRED = 2;

    private static final int COUPON_ENABLED = 1;

    private final UserCouponMapper userCouponMapper;

    public CouponServiceImpl(UserCouponMapper userCouponMapper) {
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCoupon(CouponDTO dto) {
        // 跨字段约束放在这里：Bean Validation 表达不了「按 type 走不同分支」的规则
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "结束时间必须晚于开始时间");
        }

        BigDecimal discountValue = dto.getDiscountValue();
        if (dto.getType() != null && dto.getType() == CouponCalculator.TYPE_DISCOUNT) {
            if (discountValue.compareTo(BigDecimal.ZERO) <= 0 || discountValue.compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                        "折扣比例必须在 0 与 1 之间（0.9 表示九折）");
            }
        } else if (discountValue.compareTo(dto.getMinAmount()) > 0) {
            // 从源头堵住「满 0 减 100」这类倒贴券
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "满减金额不能大于使用门槛");
        }

        Coupon coupon = new Coupon();
        coupon.setName(dto.getName());
        coupon.setType(dto.getType());
        coupon.setMinAmount(dto.getMinAmount());
        coupon.setDiscountValue(discountValue);
        coupon.setTotalCount(dto.getTotalCount());
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        // 服务端强制，不接受客户端传入——否则可以伪造「已领 1000 张」或直接建一张停用的券
        coupon.setReceivedCount(0);
        coupon.setStatus(COUPON_ENABLED);
        save(coupon);
        return coupon.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long userId, Long couponId) {
        Coupon coupon = getById(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        CouponCalculator.validateForReceive(coupon, LocalDateTime.now());

        // 先原子占额度：把「还有没有剩余」和「加一」合并成一条带 WHERE 的 UPDATE，
        // 并发下只有一方能影响到 1 行。和 OrderServiceImpl.decreaseStock 同一套路。
        // 必须用 apply 写列间比较——LambdaUpdateWrapper.lt() 只能和值比，不能和列比；
        // setSql 只写 SET 子句。resources/mapper/ 是空的，没有 XML 退路。
        int claimed = baseMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .eq(Coupon::getStatus, COUPON_ENABLED)
                .apply("received_count < total_count")
                .setSql("received_count = received_count + 1"));
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(USER_COUPON_UNUSED);
        // UserCoupon 上没有任何 @TableField(fill = ...)，FieldFillConfig 也认不得 receiveTime，
        // 所以必须显式赋值，不能指望自动填充。
        userCoupon.setReceiveTime(LocalDateTime.now());
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            // 撞上 uk_user_coupon：该用户已经领过这张券。
            //
            // 让事务回滚的不是「catch」这个动作，而是下面这个 BusinessException 最终抛出了代理边界——
            // 上面那句 received_count + 1 会随之撤销，不会留下脏计数。
            // 由此推出一个约束：今后任何 @Transactional 方法若 catch 掉它还想继续做事，
            // 事务已经是 rollback-only，提交时会抛 UnexpectedRollbackException。
            // 真要吞掉，得用 REQUIRES_NEW 另开一个事务。
            throw new BusinessException(ErrorCode.COUPON_ALREADY_RECEIVED);
        }
    }

    @Override
    public List<CouponVO> listAvailable(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = list(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, COUPON_ENABLED)
                .and(w -> w.isNull(Coupon::getStartTime).or().le(Coupon::getStartTime, now))
                .and(w -> w.isNull(Coupon::getEndTime).or().ge(Coupon::getEndTime, now))
                .orderByDesc(Coupon::getId));
        if (coupons.isEmpty()) {
            return List.of();
        }

        // 第二次查询批量取「我已领过的券 id」，避免逐张券查一次（N+1）
        List<Long> couponIds = coupons.stream().map(Coupon::getId).toList();
        Set<Long> receivedIds = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .in(UserCoupon::getCouponId, couponIds))
                .stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());

        return coupons.stream()
                .map(coupon -> toCouponVO(coupon, receivedIds.contains(coupon.getId())))
                .toList();
    }

    @Override
    public List<UserCouponVO> myCoupons(Long userId) {
        List<UserCoupon> mine = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId));
        if (mine.isEmpty()) {
            return List.of();
        }

        // 一次批量取券模板，避免 N+1
        List<Long> couponIds = mine.stream().map(UserCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> couponById = listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        return mine.stream()
                .map(userCoupon -> toUserCouponVO(userCoupon, couponById.get(userCoupon.getCouponId()), now))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponReservation reserve(Long userId, Long userCouponId, BigDecimal orderAmount) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        // 「券不存在」和「不是你的券」报同一个错误，避免泄露某个 id 是否存在
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (userCoupon.getStatus() == null || userCoupon.getStatus() != USER_COUPON_UNUSED) {
            throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(), "该优惠券已使用或已失效");
        }

        Coupon coupon = getById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        CouponCalculator.validateForUse(coupon, orderAmount, LocalDateTime.now());
        CouponCalculator.Discount discount = CouponCalculator.compute(coupon, orderAmount);

        // 原子占券：只有当前仍是「未使用」才占得到。0 行说明并发下已被同一用户的另一单用掉。
        int claimed = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, USER_COUPON_UNUSED)
                .set(UserCoupon::getStatus, USER_COUPON_USED)
                .set(UserCoupon::getUseTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.COUPON_NOT_AVAILABLE.getCode(), "该优惠券已被使用");
        }

        return new CouponReservation(coupon.getId(), userCouponId, discount.discountAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long userId, Long userCouponId) {
        if (userCouponId == null) {
            return;
        }
        // 刻意不对 0 行报错：调用方（cancelOrder）已经用条件更新「抢占」过订单状态，
        // 回退体保证只执行一次，这里的 0 行只意味着券不是本单所占的。restoreStock 同理。
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, USER_COUPON_USED)
                .set(UserCoupon::getStatus, USER_COUPON_UNUSED)
                .set(UserCoupon::getUseTime, null));
    }

    // ---- 装配 ----

    private CouponVO toCouponVO(Coupon coupon, boolean received) {
        CouponVO vo = new CouponVO();
        vo.setId(coupon.getId());
        vo.setName(coupon.getName());
        vo.setType(coupon.getType());
        vo.setMinAmount(coupon.getMinAmount());
        vo.setDiscountValue(coupon.getDiscountValue());
        vo.setTotalCount(coupon.getTotalCount());
        vo.setReceivedCount(coupon.getReceivedCount());
        vo.setStatus(coupon.getStatus());
        vo.setStartTime(coupon.getStartTime());
        vo.setEndTime(coupon.getEndTime());
        vo.setReceived(received);
        int total = coupon.getTotalCount() == null ? 0 : coupon.getTotalCount();
        int used = coupon.getReceivedCount() == null ? 0 : coupon.getReceivedCount();
        vo.setRemainCount(Math.max(total - used, 0));
        return vo;
    }

    private UserCouponVO toUserCouponVO(UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(userCoupon.getId());
        vo.setCouponId(userCoupon.getCouponId());
        vo.setReceiveTime(userCoupon.getReceiveTime());
        vo.setUseTime(userCoupon.getUseTime());
        vo.setStatus(deriveStatus(userCoupon, coupon, now));

        if (coupon != null) {
            vo.setName(coupon.getName());
            vo.setType(coupon.getType());
            vo.setMinAmount(coupon.getMinAmount());
            vo.setDiscountValue(coupon.getDiscountValue());
            vo.setStartTime(coupon.getStartTime());
            vo.setEndTime(coupon.getEndTime());
        }
        return vo;
    }

    /**
     * 派生出展示用的状态。
     *
     * <p>数据库里只会存 0（未使用）和 1（已使用），2（已过期）没有任何代码会写入，也没有定时任务
     * 去刷。这里在读取时现算，纯属展示层投影。能不能用由 {@code CouponCalculator.validateForUse} 独立判定，
     * 所以不存在「状态显示没过期但实际能用」的漏洞。
     */
    private int deriveStatus(UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        int status = userCoupon.getStatus() == null ? USER_COUPON_UNUSED : userCoupon.getStatus();
        if (status == USER_COUPON_UNUSED && coupon != null
                && coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            return USER_COUPON_EXPIRED;
        }
        return status;
    }
}
