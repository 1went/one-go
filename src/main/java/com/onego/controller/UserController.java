package com.onego.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.onego.entity.dto.LoginFormDTO;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserDTO;
import com.onego.entity.User;
import com.onego.entity.UserInfo;
import com.onego.entity.dto.UserUpdateDTO;
import com.onego.service.IUserInfoService;
import com.onego.service.IUserMessageService;
import com.onego.service.IUserService;
import com.onego.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IUserMessageService userMessageService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam(value = "type", required = false) Integer type,
                           @RequestParam("phone") String phone) {
        return userService.sendCode(phone, type);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        if (StrUtil.isBlank(loginForm.getPassword())) {
            return userService.login(loginForm);
        }
        return userService.loginByPwd(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        return userService.logout(request);
    }

    /**
     * 忘记密码
     * @return 无
     */
    @PostMapping("/reset")
    public Result reset(@RequestBody LoginFormDTO loginForm){
        return userService.reset(loginForm);
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 用户签到
     */
    @PostMapping("/sign")
    public Result userSign() {
        return userService.userSign();
    }

    /**
     * 用户签到统计
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    @PostMapping("/edit")
    public Result userEdit(@RequestBody UserUpdateDTO userUpdateDTO, HttpServletRequest request) {
        if (userUpdateDTO.getStatus() == 1) {
            return userService.userEdit(userUpdateDTO, request);
        }
        return userInfoService.userInfoEdit(userUpdateDTO);
    }

    @GetMapping("/mes")
    public Result myMessages(@RequestParam("status") Integer status) {
        return userMessageService.myMessages(status);
    }

    @PostMapping("/mes/read/{id}")
    public Result readMsg(@PathVariable("id") Integer id) {
        return userMessageService.readMsg(id);
    }

    @PostMapping("/mes/del/{id}")
    public Result delMsg(@PathVariable("id") Integer id) {
        boolean res = userMessageService.removeById(id);
        return res ? Result.ok() : Result.fail("删除失败");
    }
}
