package com.onego.service;

import com.onego.entity.UserMessage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.onego.entity.dto.Result;

/**
 *
 */
public interface IUserMessageService extends IService<UserMessage> {

    Result readMsg(Integer id);

    /**
     * 返回和当前用户有过聊天记录的用户
     * @param status 消息状态
     */
    Result myMessages(Integer status);
}
