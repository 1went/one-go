package com.onego.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 消息
 */
@TableName(value ="tb_user_message")
@Data
@Accessors(chain = true)
public class UserMessage implements Serializable {
    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 消息发送者 0-系统用户
     */
    private Integer fromId;

    @TableField(exist = false)
    private String fromName;

    @TableField(exist = false)
    private String fromIcon;

    /**
     * 消息接收者
     */
    private Integer toId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息状态 1-已读 0-未读
     */
    private Boolean status;

    @TableField("is_deleted")
    @TableLogic
    private Boolean deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}