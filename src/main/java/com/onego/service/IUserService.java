package com.onego.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.onego.entity.dto.LoginFormDTO;
import com.onego.entity.dto.Result;
import com.onego.entity.User;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码并存到redis
     */
    Result sendCode(String phone, Integer type);

    /**
     * 通过手机登录或者注册
     */
    Result login(LoginFormDTO loginForm);

    /**
     * 通过密码登录
     */
    Result loginByPwd(LoginFormDTO loginForm);

    /**
     * 用户签到
     */
    Result userSign();

    /**
     * 统计用户截止到今天的连续签到情况
     * @return 连续签到天数
     */
    Result signCount();

    Result logout(HttpServletRequest request);

    /**
     * 忘记密码
     */
    Result reset(LoginFormDTO loginForm);
}
