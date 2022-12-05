package com.hmdp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.github.pagehelper.PageHelper;

import javax.annotation.Resource;


import java.util.*;
import java.util.concurrent.TimeUnit;
//import com.github.pagehelper.PageHelper;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopTypeService shopTypeService;

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

}
