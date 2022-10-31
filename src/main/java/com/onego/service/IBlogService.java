package com.onego.service;

import com.onego.entity.dto.Result;
import com.onego.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 博客详情
     * @param id 博客id
     */
    Result queryBlog(Long id);

    /**
     * 按点赞数量降序并分页返回博客，
     * @param current 当前页
     */
    Result queryHotBlog(Integer current);

    /**
     * 博客点赞
     * @param id 博客id
     */
    Result likeBlog(Long id);

    /**
     * 最先点赞博客的5个用户
     */
    Result blogLikes(Long id);

    /**
     * 发布博客，并且将博客推送给粉丝
     * @param blog 博客
     */
    Result saveBlog(Blog blog);

    /**
     * 获取我关注的人发表的博客，并且实现滚动分页
     * @param max    上一次查询的最小时间戳，即本次开始查询的最大值
     * @param offset 上次查询的结果相同最小时间戳的个数
     * @return       返回ScrollResult，包括数据、本次查询的最小时间戳、最小时间戳的个数
     */
    Result queryBlogOfFollow(Long max, Integer offset);

}
