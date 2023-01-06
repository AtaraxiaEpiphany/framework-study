package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //invalid phone
            // 2. 不符合返回错误信息
            log.debug("手机号{}格式错误!", phone);
            return Result.fail("手机号格式错误!");
        }
        // 3. 符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到session
//        session.setAttribute("code", code);
//         4. TODO  保存验证码到redis,并设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5. 发送验证码
        log.debug("验证码{}发送成功!", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误!");
        }
        // 2. 校验验证码
//        String cacheCode = (String) session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3. 不一致报错
            return Result.fail("验证码错误或已过期!");
        }
        // 4. 一致,根据手机号查用户
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 不存在,创建新用户并保存session
            user = createUserWithPhone(phone);
        }
        // 7. 存在, 保存用户信息到session --> 简化user信息   userDto
//        System.out.println("=======================");
//        System.out.println("user ==> " + user);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

//        System.out.println("userDTO ==> " + userDTO);
//        System.out.println("=======================");
//        session.setAttribute("user", userDTO);
//         7.TODO 存在, 保存用户信息到redis

        //7.1 生成随机token,作为登录令牌返回
        String token = UUID.randomUUID().toString(true);
        //7.2 讲userDto转成hash存储
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)   // 将Long -> String
                        .setFieldValueEditor((filedName, filedValue) -> filedValue.toString()));
        try {
            //7.3 存储
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap);
        } finally {
            //7.4 设置token有效期
            stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        }
        //8. 返回token,前端请求头自带 authorization : token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        boolean save = save(user);
        return user;
    }
}
