package com.hmdp.utils.redisLock;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.ILock;
import com.hmdp.utils.PrintColor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    /**
     * 业务名称
     */
    private String name;
    /**
     * 在释放锁之前提前加载lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    private static final String PREFIX_KEY = "lock:";
    /**
     * 每个实例对象都有不同的uuid
     */
    private static final String PREFIX_ID = UUID.fastUUID().toString(true) + "-";
    private StringRedisTemplate redisTemplate;

    static {
        //TODO 初始化脚本
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //读取脚本文件
        ClassPathResource resource = new ClassPathResource("unlock.lua");
        UNLOCK_SCRIPT.setLocation(resource);
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeSec) {
        // TODO  获取线程标识,多个jvm之间可能存在同样的线程id
        String threadId = PREFIX_ID + Thread.currentThread().getId();
        Boolean ret = redisTemplate.opsForValue().setIfAbsent(PREFIX_KEY + name, threadId, timeSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ret);
    }

    /**
     * 基于lua脚本确保释放锁的原子性
     * redis.call()
     * KEYS
     * ARGV
     */
    @Override
    public void unLock() {
        String key = PREFIX_KEY + name;
        Long deleteNums = redisTemplate.execute(
                //lua脚本
                UNLOCK_SCRIPT,
                //KEYS -> list<keys>
                Collections.singletonList(key),
                //ARGV -> args...
                PREFIX_ID + Thread.currentThread().getId()
        );
        PrintColor.FG_BLUE.printWithColor("deleteNums ==> " + deleteNums);
    }

//    @Override
//    public void unLock() {
//        /*
//        存在误删情况,若某个线程执行业务逻辑时间大于过期时间,
//        在该线程还没执行完,另一个线程获取到了锁,两个线程并行执行,
//        当第一个线程执行完,会删除第二个线程的锁
//         */
//        // TODO 获取当前线程标识
//        String currentInfo = PREFIX_ID + Thread.currentThread().getId();
//        // TODO 获取redis中线程标识
//        String threadInfo = redisTemplate.opsForValue().get(PREFIX_KEY + name);
//        PrintColor.FG_BLUE.printWithColor("threadInfo ==> " + threadInfo);
//        if (currentInfo.equals(threadInfo)) {
//            /*
//            只解决了解决误删锁的问题,
//            如果在进入*判断之后*被阻塞,锁过期了,仍然会删除别的锁
//            因此使用lua脚本保证原子性
//             */
//            redisTemplate.delete(PREFIX_KEY + name);
//        }
//  }
}
