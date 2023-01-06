package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PrintColor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
public class TestDBSpeed {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    /**
     * Generate tokens to login.
     */
    @Test
    void generateTokens() throws IOException {
        FileWriter fw = new FileWriter("D:/Workspaces/JMeter/tokens.csv", StandardCharsets.UTF_8, true);
        for (int i = 0; i < 60; i++) {
            try {
                User user = userService.lambdaQuery().eq(User::getId, i).one();
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                PrintColor.FG_BLUE.printWithColor("userDTO ==> " + userDTO);
                String token = UUID.randomUUID().toString(true);
                PrintColor.FG_BLUE.printWithColor("token ==> " + token);
                if (userDTO != null) {
                    fw.write(token + "\n");
                    Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                            CopyOptions.create()
                                    .setIgnoreNullValue(true)   // 将Long -> String
                                    .setFieldValueEditor((filedName, filedValue) -> filedValue.toString()));
                    try {
                        //7.3 存储
                        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap);
                    } finally {
                        //7.4 设置token有效期
                        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
                    }
                }
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        fw.close();
    }

    @Test
    void testRedisInDocker() {
        redisTemplate.opsForValue().set("test", "test", 30L, TimeUnit.MINUTES);
    }
}
