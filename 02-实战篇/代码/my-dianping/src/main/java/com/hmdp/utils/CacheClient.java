package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate redisTemplate;


    /**
     * Set a Key:Value to redis with expire time.
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * set a Key:Value to redis with Logic Expire time.
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData<Object> redisData = new RedisData<>();
        redisData.setData(value);
        //设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * @param prefixKey  the prefix key of the redis's key.
     * @param id         a stuff id.
     * @param type       the return type.
     * @param dbFallback the function to query stuff.
     * @param time
     * @param unit
     * @param <R>        return type.
     * @param <ID>       the type of id.
     * @return
     */
    public <R, ID> R getWithPassThrough(String prefixKey, ID id, Class<R> type, Function<ID, R> dbFallback,
                                        Long time, TimeUnit unit) {
        String key = prefixKey + id;
        String json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            //不为空值
            return JSONUtil.toBean(json, type);
        }
        // json ==> "" ,"  " ,null
        if (json != null) {
            //json ==> "" or "  "
            return null;
        }
        //从数据库查询数据
        R r = dbFallback.apply(id);
        if (r == null) {
            //设置空值 防止缓存穿透
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, unit);
            return null;
        }
//        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        this.set(key, r, time, unit);
        return r;
    }

    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * @param id
     * @param prefixKey
     * @param type       return type.
     * @param dbFallback the function to query data from database.
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R getWithLogicExpire(ID id, String prefixKey, Class<R> type,
                                        Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = prefixKey + id;
        String json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            //缓存未命中->不是热点key
            return null;
        }
        // 缓存命中,转换成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        //判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //未过期
            return r;
        }
        // 开启新线程缓存重建
        // 尝试获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            //新建线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //查询
                try {
                    R ret = dbFallback.apply(id);
                    //缓存重建
                    this.setWithLogicExpire(key, ret, time, unit);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        return r;
    }


    /**
     * @param key
     * @return Return true when gets the lock to query database for data.
     */
    private boolean tryLock(String key) {
        Boolean try_get_lock = redisTemplate.opsForValue().setIfAbsent(key, "try get lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        Boolean.TRUE.equals(try_get_lock);
        if (try_get_lock) {
            System.out.println("============================");
            String name = Thread.currentThread().getName();
            long id = Thread.currentThread().getId();
            System.out.println(id + "->" + name + " got the lock...,key==>" + key);
            System.out.println("============================");
        }
        return BooleanUtil.isTrue(try_get_lock);
    }

    /**
     * @param key The key to delete in redis.
     *            Release the lock after query the database.
     */
    private void unLock(String key) {
        Boolean delete = redisTemplate.delete(key);
        if (delete) {
            System.out.println("============================");
            String name = Thread.currentThread().getName();
            long id = Thread.currentThread().getId();
            System.out.println(id + "->" + name + " is delete the lock...,key==>" + key);
            System.out.println("============================");
        }
    }


}
