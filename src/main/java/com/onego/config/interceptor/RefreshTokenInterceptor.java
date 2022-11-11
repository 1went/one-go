package com.onego.config.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.onego.entity.dto.UserDTO;
import com.onego.utils.UserHolder;
import com.onego.constants.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全局拦截器，拦截所有路径<br/>
 * 在这里刷新token的过期时间，然后放行给后面的登录拦截器{@see com.hmdp.config.interceptor.LoginInterceptor}
 * @author yiwt
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 统计UV
//        String ip = request.getRemoteHost();
//        LocalDate now = LocalDate.now();
//        String key = "UV:" + now.getYear() + ":" + now.getMonthValue();
//        stringRedisTemplate.opsForHyperLogLog().add(key, ip);

        // 前端发送请求时将token放在了请求头的authorization
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        request.setAttribute("token", token);
        // 从redis中获取用户所有信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return true;
        }
        // 将查到的hash转为一个对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 保存用户
        UserHolder.saveUser(userDTO);
        // 用户每次访问都要刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
