package com.hmdp.controller.interceptors;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/*
    - prehandle() – called before the execution of the actual handler
    - postHandle() – called after the handler is executed
    - afterCompletion() – called after the complete request is finished and the view is generated
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
//    private RedisTemplate redisTemplate;
//
//    public LoginInterceptor(RedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //登录校验
//        HttpSession session = request.getSession();
        // 1. 获取session中的用户
//        UserDTO userDTO = (UserDTO) session.getAttribute("user");

        // 1.TODO 获取请求头中的token
////        String token = request.getHeader("authorization");
////        if (StrUtil.isBlank(token)) {
////            log.info("用户未登录!");
////            response.setStatus(401);
////            return false;
//        }
        // 2.TODO 基于token获取redis中user
//        Map entries = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        // 3.TODO 将hash 转化为  bean
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
//        System.out.println("=======================================");
//        System.out.println("userDTO in Interceptor ==> " + userDTO);
//        System.out.println("=======================================");
        // 3. 判断用户是否存在
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            // 3. 不存在
            log.info("用户未登录!");
            response.setStatus(401); // 未授权
            return false;
        }

        // 5. 存在
        // 6. 刷新token有效期
//        UserHolder.saveUser(userDTO);
//        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //在view已经渲染后移除保存的用户,防止内存泄漏
        UserDTO user = UserHolder.getUser();
        UserHolder.removeUser();
    }


}
