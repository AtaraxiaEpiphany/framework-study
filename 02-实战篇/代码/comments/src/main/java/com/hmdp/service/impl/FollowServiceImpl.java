package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录!");
        }
        //2. 判断关注还是取关
        Long userId = user.getId();
        String key = "follows:" + userId;
        if (isFollow) {
            //2.1 关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // sadd userId follow_user_id
                redisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //2.2 取关, 删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isRemove = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
            );
            if (isRemove) {
                redisTemplate.opsForSet().remove(key, followUserId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录!");
        }
        Long userId = user.getId();
        /*
        查询是否关注
        select count(*) from tb_follow where user_id = ? and follow_id = ?
         */
        Long count = lambdaQuery().eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollows(Long id) {
        // 1. 获取当前用户id
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录!");
        }
        // 2. 获取key
        Long userId = user.getId();
        String currentUser = "follows:" + userId;
        String otherUser = "follows:" + id;
        // 3.求交集
        Set<String> intersect = redisTemplate.opsForSet().intersect(currentUser, otherUser);
        // 4. 解析交集
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(collect)
                .stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }


}
