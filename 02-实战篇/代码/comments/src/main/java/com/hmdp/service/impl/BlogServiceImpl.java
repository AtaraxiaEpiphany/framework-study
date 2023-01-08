package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在!");
        }
        // 查询相关用户
        queryBlogUser(blog);
        // 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        //2.判断当前用户是否点赞
        String key = "blog:liked:" + blog.getId();
        Boolean isLiked = redisTemplate.opsForSet().isMember(key, userId.toString());
        //3.设置isLiked属性
        blog.setIsLike(BooleanUtil.isTrue(isLiked));
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
//        records.forEach(this::queryBlogUser);
        records.forEach(blog -> {
            this.isBlogLiked(blog);
            queryBlogUser(blog);
        });
        return Result.ok(records);

    }

    @Override
    public Result blogLike(Long id) {
        //1.获取用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否点赞
        String key = "blog:liked:" + id;
        Boolean isLiked = redisTemplate.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isLiked)) {
            //3.点赞
            //3.1 数据库点赞数+1
            boolean isUpdate = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isUpdate) {
                //3.2 保存用户到redis set
                redisTemplate.opsForSet().add(key, userId.toString());
            }
        } else {
            //4.取消点赞
            //4.1 数据库点赞数-1
            boolean isUpdate = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isUpdate) {
                //4.2 把用户从redis set中移除
                redisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
