package com.onego.service;

import com.onego.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserUpdateDTO;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserInfoService extends IService<UserInfo> {

    Result userInfoEdit(UserUpdateDTO userUpdateDTO);
}
