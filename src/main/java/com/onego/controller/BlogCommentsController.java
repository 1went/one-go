package com.onego.controller;


import com.onego.entity.BlogComments;
import com.onego.entity.dto.Result;
import com.onego.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  发表评论前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {
    @Resource
    private IBlogCommentsService blogCommentsService;

    @PostMapping
    public Result saveComments(@RequestBody BlogComments blogComments) {
        return blogCommentsService.saveComment(blogComments);
    }

    @GetMapping("/{id}")
    public Result queryBlogComments(@PathVariable("id") Long blogId) {
        return blogCommentsService.queryBlogComments(blogId);
    }

}
