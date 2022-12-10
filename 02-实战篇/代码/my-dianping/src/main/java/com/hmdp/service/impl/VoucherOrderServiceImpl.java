package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdGenerator redisIdGenerator;

    @Override
    public Result secKillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断秒杀是否开始结束
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime().isAfter(now) || voucher.getEndTime().isBefore(now)) {
            // 2.1 活动未开始 now < begin  活动已结束   now > end
            return Result.fail("活动未开放!");

        }
        // 3. 判断库存
        // TODO 需要加锁  此处采用乐观锁
        // TODO 版本号法
        //          每次修改值后version += 1
        //          gotVersion = getVersion
        //          set oldValue = newValue where version = gotVersion
        //          只有版本号一致,才修改值
        // TODO 由于库存只会减少,不存在aba问题
        //
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            System.out.println("\33[34m" + "userId ==> " + System.identityHashCode(userId) + "\33[m");
            System.out.println("\33[34m" + Thread.currentThread().getName() + " got lock==>" + userId + "\33[m");
            //解决锁释放但是事务还未提交的问题
            // transaction --> release lock

//            return createVoucherOrder(voucherId, voucher, userId);

            // NOTICE
            // 方法调用 this.createVoucherOrder(voucherId, voucher, userId);
            // this是serviceImpl对象,而不是代理对象
            // 事务是通过aop得到代理对象执行方法,因此会存在事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            System.out.println("=======");
            System.out.println("\33[34mproxy ==> " + proxy.getClass() + "\33[m");
            System.out.println("=======");
            //得到当前aop代理对象
            return proxy.createVoucherOrder(voucherId, voucher, userId);
        }

    }

    /**
     * 生成订单号
     * NOTICE: 不能synchronize(this),效率低,正确的做法应该是synchronize(userId)锁住用户
     * 注意事务失效的问题:事务方法被spring生成代理对象,如果在某个方法内调用该方法,
     * 会变成原生对象在调用
     *
     * @param voucherId
     * @param voucher
     * @return
     */

    @Override
    public Result createVoucherOrder(Long voucherId, SeckillVoucher voucher, Long userId) {
//        Long userId = UserHolder.getUser().getId();
        // NOTICE :
        // 1.toString()底层仍是new String,因此每次toString
        // 即使是同个用户,锁也不一样,需要调用intern()方法
        // 可以查看测试类
//        synchronized (userId.toString().intern()) {
        // 2.事务失效问题,由于锁在执行释放,此时事务尚未提交,其他线程仍有可能执行
        Long count = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count();
        System.out.println("\33[34m" + "count ==> " + count + "\33[m");
        if (count > 0) {
            return Result.fail("每个用户限购一次!");
        }
        // 4. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
//                .eq("stock", stock) // 只有stock没被修改时更新,但是失败率太高
                .gt("stock", 0) // 当stock > 0时
                .eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存不足!");
        }

        // 5. 生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdGenerator.nextId("order");
        // 5.1 订单id
        voucherOrder.setId(orderId);
        // 5.2 设置用户id
        voucherOrder.setUserId(userId);
        // 5.2 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 6. 返回订单id
        return Result.ok(orderId);
//        }
    }
}
/*
 * When the intern method is invoked, if the pool already contains a
 * string equal to this {@code String} object as determined by
 * the {@link #equals(Object)} method, then the string from the pool is
 * returned. Otherwise, this {@code String} object is added to the
 * pool and a reference to this {@code String} object is returned.
 */