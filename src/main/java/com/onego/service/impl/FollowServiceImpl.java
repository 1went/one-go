package com.onego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserDTO;
import com.onego.entity.Follow;
import com.onego.mapper.FollowMapper;
import com.onego.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.service.IUserService;
import com.onego.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if (isFollow) {
            // 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean result = this.save(follow);
            if (result) {
                // 放入redis  -> key:用户  value:关注的人
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关
            QueryWrapper<Follow> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId).eq("follow_user_id", followUserId);
            boolean result = this.remove(wrapper);
            if (result) {
                // 移除用户关注
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowOne(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = this.query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonsFollow(Long targetId) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + targetId;
        // 利用Redis的Set集合的交集功能实现
        Set<String> commonsFollow = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (commonsFollow == null || commonsFollow.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析ID
        List<Long> ids = commonsFollow.stream().map(Long::parseLong).collect(Collectors.toList());
        List<UserDTO> list = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(list);
    }
}
