package com.onego.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.entity.User;
import com.onego.entity.UserMessage;
import com.onego.entity.dto.Result;
import com.onego.service.IUserMessageService;
import com.onego.mapper.UserMessageMapper;
import com.onego.service.IUserService;
import com.onego.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.onego.constants.SystemConstants.MESSAGE_READ;

/**
 *
 */
@Service
public class UserMessageServiceImpl extends ServiceImpl<UserMessageMapper, UserMessage>
    implements IUserMessageService {

    @Resource
    private IUserService userService;

    @Override
    public Result readMsg(Integer id) {
        if (id == null || id <= 0) {
            return Result.fail("消息不正确");
        }
        UserMessage message = query().eq("id", id).one();
        if (message == null) {
            return Result.fail("消息不存在或者已被删除");
        }
        if (!message.getStatus()) {
            message.setStatus(MESSAGE_READ);
            if (updateById(message)) {
                return Result.ok();
            }
            return Result.fail("消息读取失败");
        }
        return Result.ok();
    }

    @Override
    public Result myMessages(Integer status) {
        List<UserMessage> userMessageList = query().eq("to_id", UserHolder.getUser().getId())
                .eq("status", status)
                .select("id, from_id, to_id, create_time")
//                .groupBy("from_id")
                .list();
        userMessageList.forEach(mes -> {
            User user = userService.getById(mes.getFromId());
            mes.setFromName(user.getNickName());
            mes.setFromIcon(user.getIcon());
        });
        return Result.ok(userMessageList);
    }
}




