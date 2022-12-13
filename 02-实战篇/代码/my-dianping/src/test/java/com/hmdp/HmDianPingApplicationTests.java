package com.hmdp;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.util.UnsafeUtils;
import org.openjdk.jol.info.ClassLayout;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import sun.misc.Unsafe;

import javax.annotation.Resource;


import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;
//import com.github.pagehelper.PageHelper;
import static com.hmdp.utils.RedisConstants.*;


@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIdGenerator redisIDGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopTypeService shopTypeService;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedissonClient redissonClient;


    @Test
    void testRedisson() {
        System.out.println("redissonClient ==> " + redissonClient);
        //可重入锁
        RLock lock = redissonClient.getLock("anyLock");
        try {
            //尝试获取锁,param  等待时间(没拿到锁会重试),释放时间,单位
            boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (isLock) {
                try {
                    System.out.println("get lock...");
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * When the intern method is invoked, if the pool already contains a
     * string equal to this {@code String} object as determined by
     * the {@link #equals(Object)} method, then the string from the pool is
     * returned. Otherwise, this {@code String} object is added to the
     * pool and a reference to this {@code String} object is returned.
     */
    @Test
    void testIntern() {
        String t1 = "intern";
        String t2 = "intern";
        String t3 = new String("intern");
        System.out.println("t1 ==> " + t1);
        System.out.println("t2 ==> " + t2);
        System.out.println("====================");
        System.out.println(t1 == t2 && t1 == t3);
        System.out.println(t2 == t3.intern() && t2 == t3.intern());
        System.out.println("====================");
    }

    @Test
    void testJol() {
        Object obj = new Object();
        ClassLayout classLayout = ClassLayout.parseInstance(obj);
        System.out.println(classLayout.toPrintable());
        System.out.println(ClassLayout.parseInstance("test").toPrintable());
        System.out.println(ClassLayout.parseInstance("123456").toPrintable());
    }

    @Test
    public void testSave() {
        Long id = 2L;
        Long seconds = 30L;
        Long[] longs = LongStream.range(1, 6).boxed().toArray(Long[]::new);
        for (Long aLong : longs) {
            shopService.saveShop2redis(aLong, seconds);
        }
//        Shop shop = shopService.getById(id);
//        RedisData redisData = new RedisData(shop, LocalDateTime.now().plusSeconds(seconds));
//        System.out.println("========================");
//        System.out.println("redisData ==> " + redisData);
//        Map<String, Object> map = BeanUtil.beanToMap(redisData, new HashMap<>(), CopyOptions.create()
//                .setIgnoreNullValue(true)
//                .setFieldValueEditor((name, value) -> name.equals("expireTime") ? value : JSONUtil.toJsonStr(value)));
//        System.out.println(map);
////        RedisData<Shop> shopRedisData = BeanUtil.fillBeanWithMap(map, new RedisData<Shop>(), false);
//        System.out.println("========================");
    }

    @Test
    public void testRedis() {
        Integer id = 5;
        String key = CACHE_SHOP_KEY + id;
        HashMap<Object, Object> map = new HashMap<>();
        map.put(null, null);
        System.out.println("map ==> " + map);
        map.put(null, null);
        System.out.println("map ==> " + map);
        System.out.println("map.size() ==> " + map.size());
        Set<Map.Entry<Object, Object>> entries = map.entrySet();
        entries.forEach(entry -> {
            boolean ret = entry.getKey() == null && entry.getValue() == null;
            System.out.println("ret ==> " + ret);
        });
        try {
            redisTemplate.opsForHash().put("test", "", "");
        } finally {
            redisTemplate.expire("test", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }

        Map<Object, Object> test = redisTemplate.opsForHash().entries("test");
    }

    @Test
    public void testPageHelper() {
        Integer current = 1;
        Integer size = 4;
        //PageHelper
        PageHelper.startPage(current, size);
        List<ShopType> list = shopTypeService.lambdaQuery().orderByAsc(ShopType::getSort).list();
        System.out.println("==================");
        list.forEach(System.out::println);
        System.out.println("==================");
        PageInfo pageInfo = new PageInfo(list, 10);
        int[] navigatepageNums = pageInfo.getNavigatepageNums();
        System.out.println(Arrays.toString(navigatepageNums));

        //mybatis-plus
        Page<ShopType> page = shopTypeService.query().lt("id", 8).page(new Page<>(current, size, true));
        System.out.println("==================");
        page.getRecords().forEach(System.out::println);
        System.out.println("==================");

    }

    @Test
    public void testUtil() {
        String EMPTY = "";
        String NULL = null;
        String SPACE = " ";
        String s = " a";
        System.out.println("StrUtil.isBlank(EMPTY) ==> " + StrUtil.isNotBlank(EMPTY));
        System.out.println("StrUtil.isBlank(NULL) ==> " + StrUtil.isNotBlank(NULL));
        System.out.println("StrUtil.isBlank(SPACE) ==> " + StrUtil.isNotBlank(SPACE));
        System.out.println("StrUtil.isNotBlank(s) ==> " + StrUtil.isNotBlank(s));
        try {
            redisTemplate.opsForValue().set("test", "");
        } finally {
            redisTemplate.expire("test", 30L, TimeUnit.SECONDS);
        }
        String test = redisTemplate.opsForValue().get("test");
        if (StrUtil.isNotBlank(test)) {
        }
        System.out.println(test);
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);
    //拥有五百个线程的线程池

    @Test
    void testRedisIncrease() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        long start = System.currentTimeMillis();
        Runnable task = () -> {
            //每个线程获取100个id
            for (int i = 0; i < 100; i++) {
                long id = redisIDGenerator.nextId("test");
                System.out.println("id ==> " + id);
            }
            latch.countDown();
        };
        for (int i = 0; i < 300; i++) {
            //提交300个任务给线程池
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        long time = end - start;
        System.out.println("time ==> " + time);
    }

    @Test
    void testUnsafe() {
        Unsafe unsafe = UnsafeUtils.getUnsafe();
        System.out.println("unsafe ==> " + unsafe);
        System.out.println(unsafe.getAddress(1234515135L));
    }
}
