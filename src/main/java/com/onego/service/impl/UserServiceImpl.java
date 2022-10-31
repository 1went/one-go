package com.onego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.entity.dto.LoginFormDTO;
import com.onego.entity.dto.Result;
import com.onego.entity.dto.UserDTO;
import com.onego.entity.User;
import com.onego.mapper.UserMapper;
import com.onego.service.IUserService;
import com.onego.utils.PasswordEncoder;
import com.onego.utils.RegexUtils;
import com.onego.constants.SystemConstants;
import com.onego.utils.UserHolder;
import com.onego.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, Integer type) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到redis
        String key = type == null ? RedisConstants.LOGIN_CODE_KEY : RedisConstants.RESET_CODE_KEY;
        stringRedisTemplate.opsForValue().set(key + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送验证码  todo 实际业务通过第三方短信发送
        log.debug("验证码：{}，2分钟内有效", code);
        return Result.ok();
    }

    /**
     * 当手机号正确并且验证码通过时，从数据库里取出该用户数据。如果不存在，则先新增，再获取。
     * 并且将返回的用户进行脱敏 {@see com.hmdp.entity.dto.UserDTO}<br/>
     * 生成随机的token令牌，将用户UserDTO对象存入Redis，以Hash结构存储<br/>
     * 设置无操作过期时间30min。如果用户有操作，将在拦截器{@see com.hmdp.config.interceptor.LoginInterceptor}里重新设置30min过期时间<br/>
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        // 参数校验
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        Result r = checkParams(phone, code, RedisConstants.LOGIN_CODE_KEY, null);
        if (r != null) {
            return r;
        }
        // Select User
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // insert and return a new user if the user dose not exist
            user = createUserWithPhone(phone);
        }
        return genTokenAndReturn(user);
    }

    @Override
    public Result loginByPwd(LoginFormDTO loginForm) {
        // 参数校验
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();
        Result r = checkParams(phone, null, null, password);
        if (r != null) {
            return r;
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("手机号不存在");
        }
        String orgPwd = user.getPassword();
        if (!PasswordEncoder.matches(orgPwd, password)) {
            return Result.fail("密码不正确");
        }
        return genTokenAndReturn(user);
    }

    @Override
    public Result userSign() {
        Long id = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        // 每个月的签到分别统计
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = RedisConstants.USER_SIGN_KEY + id + date;
        // 当前是本月第几天
        int offset = now.getDayOfMonth() - 1;
        stringRedisTemplate.opsForValue().setBit(key, offset, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        // 每个月的签到分别统计
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = RedisConstants.USER_SIGN_KEY + id + date;
        int dayOfMonth = now.getDayOfMonth();
        // 拿到截止到今天的签到数据,返回的是一个十进制 --> BITFIELD key GET u[dayOfMonth] 0
        // 因为BITFIELD可以同时操作多个，因此返回的是一个集合，对于当前业务，集合中如果有就仅有一条数据
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0L)
        );
        // 无签到数据
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        // 如果用户一直无签到，直接返回
        Long signCount = result.get(0);
        if (signCount == null || signCount == 0) {
            return Result.ok(0);
        }
        // 连续签到是指，从今天开始往前统计，如果遇到未签就停止
        int count = 0;
        while ((signCount & 1) == 1) {
            count++;
            signCount >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().delete(key, "id", "nickName", "icon");
        return Result.ok();
    }

    @Override
    public Result reset(LoginFormDTO loginForm) {
        // 参数校验
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();
        Result r = checkParams(phone, loginForm.getCode(), RedisConstants.RESET_CODE_KEY, password);
        if (r != null) {
            return r;
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("手机号不存在");
        }
        // 重置密码
        user.setPassword(PasswordEncoder.encode(password));
        return this.updateById(user) ? Result.ok() : Result.fail("重置失败");
    }

    private Result checkParams(String phone, String code, String codeKey, String password) {
        if (StrUtil.isNotBlank(phone)) {
            if (RegexUtils.isPhoneInvalid(phone)) {
                return Result.fail("手机号格式错误");
            }
        }
        if (StrUtil.isNotBlank(code)) {
            String cacheCode = stringRedisTemplate.opsForValue().get(codeKey + phone);
            if (cacheCode == null || !cacheCode.equals(code)) {
                return Result.fail("验证码不正确");
            }
        }
        if (StrUtil.isNotBlank(password)) {
            if (RegexUtils.isPwdInvalid(password)) {
                return Result.fail("密码格式错误");
            }
        }
        return null;
    }


    // 根据电话注册新用户
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存用户
        save(user);
        return user;
    }

    // 生成token并保存用户信息，然后返回
    private Result genTokenAndReturn(User user) {
        // Generate token without underscore as login token
        String token = UUID.randomUUID().toString(true);
        // 用户脱敏
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Convert UserDTO to a Map
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)  // 忽略空值
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));  // 将value转为字符串
        // Use Redis hash to store the map
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        // Expiration time 30min
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // Return the login token to the front end
        return Result.ok(token);
    }
}
