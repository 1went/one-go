package com.onego.service;

import com.onego.entity.dto.Result;
import com.onego.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 or 取关
     * @param followUserId  操作的对象
     * @param isFollow      follow if this argument is true
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 是否关注某个用户
     * @param followUserId  操作的对象
     */
    Result isFollowOne(Long followUserId);

    /**
     * 共同关注
     * @param targetId 目标用户
     */
    Result commonsFollow(Long targetId);
}
