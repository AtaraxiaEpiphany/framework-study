package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdGenerator {
    public static final long BEGIN_TIMESTAMP = 1670425045;

    public static final int COUNT_BITS = 32;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String prefixKey) {
        // TODO 1.生成当前时间戳 (31位)
        LocalDateTime now = LocalDateTime.now();
        long now_timestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = now_timestamp - BEGIN_TIMESTAMP;
        // TODO 2.生成序列号(32位)
        //          2.1 生成当天时间格式 yy:mm:dd
        String today = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + prefixKey + ":" + today; //每天的序列号不同
        long count = redisTemplate.opsForValue().increment(key);
        // TODO 3.拼接返回
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        System.out.println("today ==> " + today);
    }

}

