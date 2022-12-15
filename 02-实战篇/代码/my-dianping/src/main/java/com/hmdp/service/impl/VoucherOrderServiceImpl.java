package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.PrintColor;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdGenerator redisIdGenerator;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private RedissonClient redissonClient;


    /**
     * 秒杀脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    /**
     * 创建阻塞队列
     */
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTORS = Executors.newFixedThreadPool(10);

    private Proxy proxy;

    /**
     * 线程池任务
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取订单.
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单.
                    handleCreateVoucherOrder(voucherOrder);
                } catch (Exception e) {
//                    throw new RuntimeException(e);
                    log.error("处理订单异常", e);
                }
            }
        }

        /**
         * 创建订单.
         * 此处省略了事务!
         *
         * @param voucherOrder
         */
        private void handleCreateVoucherOrder(VoucherOrder voucherOrder) {
            //1. 获取用户id,由于不是main线程,不能通过ThreadLocal获取用户
            Long userId = voucherOrder.getUserId();
            //2. 尝试获取锁
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            //3. 判断🔒
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.error("禁止重复下单!");
                return;
            }
            // TODO 需要用事务,可以用之前的方法做改造,此处省略...
            try {
                // 4. 扣减库存
                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock-1")
//                .eq("stock", stock) // 只有stock没被修改时更新,但是失败率太高
                        .gt("stock", 0) // 当stock > 0时
                        .eq("voucher_id", voucherOrder.getVoucherId()).update();
                if (!success) {
                    log.error("库存不足!");
                    return;
                }
                PrintColor.FG_BLUE.printWithColor(">>>>>>>>>>>>>>>>>>>>>>>>");
                PrintColor.FG_BLUE.printWithColor("create voucher order...");
                PrintColor.FG_BLUE.printWithColor("voucher order ==> " + voucherOrder);
                PrintColor.FG_BLUE.printWithColor("<<<<<<<<<<<<<<<<<<<<<<<<");
                save(voucherOrder);
            } finally {
                lock.unlock();
            }
            //5.
        }
    }

    /**
     * 在类初始化后就开始执行下单处理方法(handler)
     */
    @PostConstruct
    private void init() {
        // 执行下单任务.
        SECKILL_ORDER_EXECUTORS.submit(new VoucherOrderHandler());
    }

    static {
        //TODO 初始化脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //读取脚本文件
        ClassPathResource resource = new ClassPathResource("seckill.lua");
        SECKILL_SCRIPT.setLocation(resource);
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优化
     *
     * @param voucherId
     * @return
     */
    public Result secKillVoucherWithOptimize(Long voucherId) {
        // 在生成秒杀券前,同时将其写入缓存
        // 因此可以将判断流程交给redis
        // 最后异步在数据库下单.
        // 1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long ret = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果
        int r = ret.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足!" : "禁止重复下单!");
        }
        // 3.有下单资格,生成下单信息
        // TODO  生成下单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdGenerator.nextId("order");
        //  订单信息
        // 3.1 订单id
        voucherOrder.setId(orderId);
        // 3.2 设置用户id
        voucherOrder.setUserId(userId);
        // 3.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // TODO 4.放入阻塞队列
        orderTasks.add(voucherOrder);

        return Result.ok(orderId);
    }

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

        /*
        synchronized不是分布式🔒
         */
        // TODO 创建🔒对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        // TODO 尝试获取🔒
//        boolean isLock = lock.tryLock(30);
        if (!isLock) {
            //获取锁失败
            return Result.fail("不允许重复下单!");
        }

//        synchronized (userId.toString().intern()) {
        /*
        使用分布式锁
         */
        try {
            PrintColor.FG_BLUE.printWithColor("userId ==> " + System.identityHashCode(userId.toString().intern()));
            PrintColor.FG_BLUE.printWithColor(Thread.currentThread().getName() + " got lock==>" + userId);
            //解决锁释放但是事务还未提交的问题
            // transaction --> release lock

//            return createVoucherOrder(voucherId, voucher, userId);

            // NOTICE
            // 方法调用 this.createVoucherOrder(voucherId, voucher, userId);
            // this是serviceImpl对象,而不是代理对象
            // 事务是通过aop得到代理对象执行方法,因此会存在事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            System.out.println("=======");
            PrintColor.FG_BLUE.printWithColor("proxy ==> " + proxy.getClass());
            System.out.println("=======");
            //得到当前aop代理对象
            return proxy.createVoucherOrder(voucherId, voucher, userId);
        } finally {
            //TODO 确保锁的释放
            lock.unlock();
        }
//        }


    }

    /**
     * 生成订单号
     * NOTICE: 不能synchronize(this),效率低,正确的做法应该是synchronize(userId)锁住用户
     * 注意事务失效的问题:事务方法被spring生成代理对象,如果在某个方法内调用该方法,
     * 会变成原生对象this在调用
     * 集群模式锁会失效,因为每个集群都有各自的JVM 锁监视器,因此每个集群都能获得一把锁,即使用户id一致
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
        PrintColor.FG_BLUE.printWithColor("count ==> " + count);
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