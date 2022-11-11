package com.onego.entity.dto;

import lombok.Data;

/**
 * @author yiwt
 */
@Data
public class UserUpdateDTO {
    /**
     * <p>用户更新目标</p>
     * <p> 0：{@link com.onego.entity.UserInfo} </p>
     * <p> 1：{@link com.onego.entity.User} </p>
     */
    private Byte status;
    private String type;
    private String content;
}
