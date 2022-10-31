package com.onego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.ScrollResult;
import com.onego.entity.dto.UserDTO;
import com.onego.entity.Blog;
import com.onego.entity.Follow;
import com.onego.entity.User;
import com.onego.mapper.BlogMapper;
import com.onego.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.service.IFollowService;
import com.onego.service.IUserService;
import com.onego.constants.RedisConstants;
import com.onego.constants.SystemConstants;
import com.onego.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlog(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 组装blog的相关信息
        this.assembleBlog(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 组装blog的相关信息
        records.forEach(this::assembleBlog);
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        String userId = UserHolder.getUser().getId().toString();
        // 是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score == null) {
            // 未点赞则数据库值 + 1
            boolean result = this.update().setSql("liked = liked + 1").eq("id", id).update();
            if (result) {
                // 保存用户到redis集合  zadd key 用户id 点赞时间
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
        } else {
            // 已点赞则数据库值 - 1
            boolean result = this.update().setSql("liked = liked - 1").eq("id", id).update();
            if (result) {
                // 将用户从点赞集合里移除
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result blogLikes(Long id) {
        // 获取最先点赞的5个用户id
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::parseLong).collect(Collectors.toList());
        // 数据库默认按照id升序排序的，之前查好的数据又变得无序，因此不符合业务要求
        // 不过，因为之前返回的用户id是按点赞先后排序的，因此可以指定排序规则为传入的用户id
        String idStr = StrUtil.join(",", ids);
        List<User> userList = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 封装成UserDTO返回
        List<UserDTO> userDTOList = userList.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean result = this.save(blog);
        if (!result) {
            return Result.fail("发布失败");
        }
        // 当前用户的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 将消息发送给粉丝
        for (Follow follow : follows) {
            // 粉丝id
            Long fansId = follow.getUserId();
            // 推送
            String key = RedisConstants.FEED_KEY + fansId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        // 从redis里读数据，实现滚动分页 -->  ZREVRANGEBYSCORE key max 0 WITHSCORES LIMIT offset 3
        // 按分数降序排序
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;  // 记录最小时间戳（就是typedTuples里的最后一个元素）
        int os = 1;  // 最小时间戳的个数
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取ID
            ids.add(Long.parseLong(tuple.getValue()));
            // 时间戳
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 根据id返回blog信息，注意，不要直接查，否则返回的数据又变成无序了
        String str = StrUtil.join(",", ids);
        List<Blog> blogList = this.query().in("id", ids).last("ORDER BY FIELD(id," + str +  ")").list();
        // 注意给每个blog组装其他信息
        for (Blog blog : blogList) {
            this.assembleBlog(blog);
        }
        // 封装最后结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    /**
     * 组装博客用户信息，当前用户是否点赞
     * @param blog  博客
     */
    private void assembleBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        this.isBlogLike(blog);
    }

    /**
     * 当前用户是否给博客点赞
     * @param blog  博客
     */
    private void isBlogLike(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录
            return;
        }
        String userId = user.getId().toString();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        blog.setIsLike(stringRedisTemplate.opsForZSet().score(key, userId) != null);
    }
}
