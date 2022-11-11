package com.onego.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.onego.entity.User;
import com.onego.entity.UserInfo;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserDTO;
import com.onego.entity.dto.UserUpdateDTO;
import com.onego.mapper.UserInfoMapper;
import com.onego.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Override
    public Result userInfoEdit(UserUpdateDTO userUpdateDTO) {
        UserDTO user = UserHolder.getUser();
        UpdateWrapper<UserInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set(userUpdateDTO.getType(), userUpdateDTO.getContent()).eq("id", user.getId());
        return update(updateWrapper) ? Result.ok() : Result.fail("修改失败");
    }
}
