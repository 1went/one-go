package com.onego.controller;


import com.onego.entity.dto.Result;
import com.onego.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注 or 取关
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 是否关注某个用户
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollowOne(followUserId);
    }

    /**
     * 共同关注
     */
    @GetMapping("/common/{id}")
    public Result commonsFollow(@PathVariable("id") Long targetId) {
        return followService.commonsFollow(targetId);
    }
}
