package com.onego.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封装一个对象，用于实现逻辑过期
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
