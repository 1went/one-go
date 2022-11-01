package com.onego.entity.dto;

import lombok.Data;

/**
 * @author yiwt
 */
@Data
public class CommentVO {
    private Long id;
    private UserDTO user;
    private Long blogId;
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    private Integer iComments;
    private Integer score;
}
