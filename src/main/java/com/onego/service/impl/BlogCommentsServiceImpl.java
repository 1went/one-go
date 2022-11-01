package com.onego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.onego.entity.BlogComments;
import com.onego.entity.User;
import com.onego.entity.dto.CommentVO;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserDTO;
import com.onego.mapper.BlogCommentsMapper;
import com.onego.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.service.IBlogService;
import com.onego.service.IUserService;
import com.onego.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    @Transactional
    public Result saveComment(BlogComments blogComments) {
        UserDTO curUser = UserHolder.getUser();
        blogComments.setUserId(curUser.getId());
        blogComments.setLiked(0);
        blogComments.setStatus(true);
        boolean result = this.save(blogComments);
        if (result) {
            result = blogService.update()
                    .setSql("comments = comments + 1")
                    .eq("id", blogComments.getBlogId())
                    .update();
        }
        return result ? Result.ok() : Result.fail("评论失败");
    }

    @Override
    public Result queryBlogComments(Long blogId) {
        List<BlogComments> blogCommentsList = this.query()
                .eq("blog_id", blogId).eq("status", true)
                .list();
        List<CommentVO> collect = blogCommentsList.stream().map(item -> {
            CommentVO commentVO = BeanUtil.copyProperties(item, CommentVO.class);
            Integer count = this.query().eq("parent_id", item.getId()).count();
            commentVO.setIComments(count == null ? 0 : count);
            User user = userService.query().eq("id", item.getUserId()).one();
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            commentVO.setUser(userDTO);
            return commentVO;
        }).collect(Collectors.toList());
        return Result.ok(collect);
    }
}
