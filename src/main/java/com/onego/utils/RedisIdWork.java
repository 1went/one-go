package com.onego.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于Redis的自增长全局ID生成
 *
 * @author yiwt
 */
@Component
public class RedisIdWork {

    /**
     * 2022.10.14 00:00:00
     */
    private static final long BEGIN_TIMESTAMP = 1665705600L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPre) {
        // 全局ID是一个64位的数字，第一位是0，表示正数
        LocalDateTime now = LocalDateTime.now();
        // 时间戳，用31位表示，可以支持约69年
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 最后32位表示序列号
        // 每天用一个key记录
        String day = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPre + ":" + day);

        // 拼接并返回
        return timestamp << COUNT_BITS | count;
    }

}
