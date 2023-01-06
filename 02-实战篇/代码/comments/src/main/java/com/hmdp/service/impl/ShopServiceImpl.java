package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;

    /**
     * Save hot data to redis.
     *
     * @param id            The id of shop.
     * @param expireSeconds The time of shop expire time.
     */
    public void saveShop2redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //封装逻辑过期时间及数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisTemplate.opsForValue().set(CACHE_SHOP_HOT_KEY + id, JSONUtil.toJsonStr(redisData));
        //未给数据设置过期时间
    }


    /**
     * 注意缓存击穿(热点key过期高并发请求数据库问题)
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //封装工具类实现缓存穿透
//        Shop shop = cacheClient.getWithPassThrough(CACHE_UTIL_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        // TODO 互斥锁解决缓存击穿(热点key)
        Shop shop = queryWithMutex(id);


        // TODO 逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicExpire(id);
        // 封装工具类实现逻辑击穿
//        Shop shop = cacheClient.getWithLogicExpire(id, CACHE_SHOP_HOT_KEY, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    //线程池用于重建缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * @param id
     * @return Return shop by logic expire time
     */
    private Shop queryWithLogicExpire(Long id) {
        String key = CACHE_SHOP_HOT_KEY + id;
        String shopDataJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopDataJson)) {
            //1. 缓存未命中,非热点key,直接返回
            return null;
        }
        //2.缓存命中,把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopDataJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        //3.判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //3.1 未过期,直接返回数据
            // now < expire time
            return shop;
        }
        //3.2 过期,需要缓存重建
        //3.2.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean gotLock = tryLock(lockKey);
        //3.2.2 获取锁成功
        if (gotLock) {
            //3.2.2.1  TODO 开启独立线程缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建
                    saveShop2redis(id, 20L);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //3.2.3 返回过期数据
        return shop;
    }


    /**
     * 互斥锁解决缓存击穿
     *
     * @return
     */
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从redis查询缓存
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        // 判断缓存是否命中
        if (!entries.isEmpty()) {
            //缓存命中
            if (entries.size() == 1 && entries.containsKey("") && entries.containsValue("")) {
                //缓存无效
                return null;
            }
            //返回数据
            return BeanUtil.fillBeanWithMap(entries, new Shop(), false);
        }
        // TODO 缓存未命中,实现缓存重建
        // 获取互斥锁
        // 判断是否获取锁成功
        Shop shop;
        try {
            if (!tryLock(LOCK_SHOP_KEY + id)) {
                // 失败休眠,并重新获取锁
                Thread.sleep(50);
                //重新获取锁
                return queryWithMutex(id);
            }
            // 获取🔒成功,查询数据库
            shop = getById(id);
            // 模拟重建延时
//            Thread.sleep(200);
            if (shop == null) {
                //数据库中没有数据
                //写入无效数据给redis并设置有效期
                try {
                    redisTemplate.opsForHash().put(key, "", "");
                } finally {
                    redisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
                }
                return null;
            }
            //数据中有数据,写入redis
            Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true)
                            .setFieldValueEditor((filedName, filedValue) ->
                                    filedValue != null ? filedValue.toString() : null
                            ));
            try {
                redisTemplate.opsForHash().putAll(key, shopMap);
            } finally {
                redisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(LOCK_SHOP_KEY + id);
        }
        return shop;
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


    /**
     * @param id
     * @return 查询商铺, 防止缓存穿透
     */
    public Shop queryWithPassThrough(Long id) {
        // TODO 考虑缓存穿透
        String key = CACHE_SHOP_KEY + id;
        // TODO 1. 从redis中获取商铺缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(key);
        // TODO 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            // TODO 3 存在
            // TODO 3.1判断是否为 ""=""
            if (shopMap.size() == 1 && shopMap.containsKey("") && shopMap.containsValue("")) {
                return null;
            }
            // TODO 3.2  返回entity
//            return Result.ok();
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // TODO 4. 不存在,根据id查数据库
        Shop shop = getById(id);
        if (shop == null) {
            // TODO 5. 未查到数据,返回错误
            // TODO 5.1 redis 写入 ""=""  防止缓存穿透
            try {
                redisTemplate.opsForHash().put(key, "", "");
            } finally {
                redisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
            }
//            return Result.fail("店铺不存在!");
            return null;
        }
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, filedValue) -> {
//                            System.out.println("===========================");
//                            System.out.println("filedName ==> " + filedName);
//                            System.out.println("filedValue ==> " + filedValue);
//                            System.out.println("===========================");
                            return filedValue != null ? filedValue.toString() : filedValue;
                        }));
        // TODO 6. 否则写入redis缓存
        try {
            redisTemplate.opsForHash().putAll(key, stringObjectMap);
        } finally {
            //设置过期时间
            redisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }
//        return Result.ok(shop);
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        // TODO 保证缓存与数据库数据一致性,需要注意线程安全问题
        // TODO 先更新数据库再删缓存或者反之
        // 方案一:
        // t1 -> 删除缓存            ->                 更新数据库
        // t2 ->         查询缓存  -> 写入缓存(数据不一致)
        // 方案二:
        // t1 -> 更新数据库          ->           删除缓存
        // t2 ->       查询缓存(旧的缓存数据) ->  写入缓存
        // TODO 如果先删在更新,由于更新操作慢,另一个线程更容易造成影响

        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空!");
        }

        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存

        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
