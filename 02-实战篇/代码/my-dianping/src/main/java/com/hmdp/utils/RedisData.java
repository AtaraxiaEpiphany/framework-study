package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisData<T> {
    //所要存入redis的数据
    private T data;
    //逻辑过期时间
    private LocalDateTime expireTime;
}
