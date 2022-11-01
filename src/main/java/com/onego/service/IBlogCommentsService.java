package com.onego.service;

import com.onego.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.onego.entity.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    Result saveComment(BlogComments blogComments);

    Result queryBlogComments(Long blogId);
}
