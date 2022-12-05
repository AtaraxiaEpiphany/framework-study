package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConverter;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> typeList() {
        List<String> cacheTypes = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE, 0, -1);
        if (cacheTypes.isEmpty()) {
            // 1. 缓存没有数据,通过数据库查找
            List<ShopType> lists = lambdaQuery().orderByAsc(ShopType::getSort).list();
            // 2. 将ShopType -> String
            List<String> listOfString = lists.stream().map(list -> JSONUtil.toJsonStr(list)).collect(Collectors.toList());
            // 3. 存入redis
            try {
                stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE, listOfString);
            } finally {
                stringRedisTemplate.expire(CACHE_SHOP_TYPE, CACHE_SHOP_TYPE_TTL, TimeUnit.HOURS);
            }
            // . 将数据返回
            return lists;
        }
        // . 缓存有数据
        return cacheTypes.stream().map((typeJsonString) -> JSONUtil.toBean(typeJsonString, ShopType.class)).collect(Collectors.toList());
    }
}
