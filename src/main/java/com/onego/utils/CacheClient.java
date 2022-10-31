package com.onego.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.onego.constants.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * 基于Redis的缓存工具类
 * @author yiwt
 */
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 通过构造器注入
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 通过id获取数据，通过缓存空值的方式防止缓存穿透的问题<br/>
     * 该方法更用于一般的数据缓存.
     *
     * @param cachePrefix  redis的缓存key前缀
     * @param id           需要获取的数据id
     * @param type         数据的类型
     * @param time         期望的过期时间
     * @param timeUnit     过期时间的单位
     * @param function     接收一个lambda，表示当缓存中不存在时，执行该方法从数据库中得到数据
     * @return             返回id对应的数据
     */
    public <R, I> R querySolvePassThrough(String cachePrefix, I id, Class<R> type, long time, TimeUnit timeUnit,
                                          Function<I, R> function) {
        String key = cachePrefix + id;
        // 从redis查
        String json = stringRedisTemplate.opsForValue().get(key);
        // 当查出来的数据为null或者是一个空串时，将跳过判断
        if (StrUtil.isNotBlank(json)) {
            // 存在直接返回
            return JSONUtil.toBean(json, type);
        }
        // 如果json是一个空串，说明此时取到的数据是之前缓存的空值，直接返回null
        if (json != null) {
            return null;
        }
        // 否则查数据库
        R r = function.apply(id);
        if (r == null) {
            // 缓存空值,过期2min。防止缓存穿透 todo 缓存空值的方式会多占用一些内存。可以提前对id进行合理的判断，或者将id设置复杂一点
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 写入缓存  //
        this.set(key, r, time, timeUnit);
        // 返回
        return r;
    }

    /**
     * 该方法实现了<span>逻辑过期</span>的方式防止缓存击穿，这种方式实现比较复杂，并且数据并不会真实过期，所以效率高，但是会出现一定的数据不一致。
     * 还实现了缓存空值防止缓存穿透。<br/>
     * 方法常用于热点数据的获取，需要先提前缓存热点数据，否则将返回空值
     *
     * @param cachePrefix  redis的缓存key前缀
     * @param id           需要获取的数据id
     * @param type         数据的类型
     * @param time         期望的过期时间
     * @param timeUnit     过期时间的单位
     * @param function     接收一个lambda，表示当缓存中不存在时，执行该方法从数据库中得到数据
     * @return             返回id对应的数据
     */
    @SuppressWarnings({"unused"})
    public <R, I> R queryDataWithLogicExpire(String cachePrefix, I id, Class<R> type, long time, TimeUnit timeUnit,
                                             Function<I, R> function) {
        String key = cachePrefix + id;
        // 从redis查
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 未命中，直接返回null
            return null;
        }
        // 如果命中，先判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (LocalDateTime.now().isBefore(expireTime)) {
            // 如果未过期，直接返回
            return r;
        }
        // 已过期，重建缓存
        // 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 获取锁是否成功
        if (tryLock(lockKey)) {
            // 如果成功，就开启一个独立线程去构建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 更新数据库
                    R apply = function.apply(id);
                    // 写入redis
                    this.setWithLoginExpire(key, apply, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    this.unLock(lockKey);
                }
            });
        }
        // 返回过期数据
        return r;
    }

    /**
     * 该方法实现了通过<span>加互斥锁</span>防止缓存击穿，这种方式实现简单，能保证数据的一致性，但是效率比较低。
     * 还实现了通过缓存空值防止缓存穿透。<br/>
     * 方法常用于热点数据的获取，需要先提前缓存热点数据，否则将返回空值
     *
     * @param cachePrefix  redis的缓存key前缀
     * @param id           需要获取的数据id
     * @param type         数据的类型
     * @param time         期望的过期时间
     * @param timeUnit     过期时间的单位
     * @param function     接收一个lambda，表示当缓存中不存在时，执行该方法从数据库中得到数据
     * @return             返回id对应的数据
     */
    @SuppressWarnings({"unused"})
    public <R, I> R queryDataWithMutex(String cachePrefix, I id, Class<R> type, long time, TimeUnit timeUnit,
                                       Function<I, R> function) {
        String key = cachePrefix + id;
        // 从redis查
        String json = stringRedisTemplate.opsForValue().get(key);
        // 当查出来的数据为null或者是一个空串时，将跳过判断
        if (StrUtil.isNotBlank(json)) {
            // 存在直接返回
            return JSONUtil.toBean(json, type);
        }
        // 如果json存在，说明此时取到的是空值
        if (json != null) {
            return null;
        }
        // 使用互斥锁防止缓存击穿
        R r;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            // 尝试获取互斥锁
            if (!this.tryLock(lockKey)) {
                // 失败就休眠重试
                Thread.sleep(50);
                return queryDataWithMutex(cachePrefix, id, type, time, timeUnit, function);
            }
            // 获取锁成功，doubleCheck 再次检查缓存是否存在
            json = stringRedisTemplate.opsForValue().get(key);
            // 如果存在直接返回
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            // 从数据库拿值
            r = function.apply(id);
            if (r == null) {
                // 缓存空值,过期2min。防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 写入缓存
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            this.unLock(lockKey);
        }
        // 返回
        return r;
    }

    /**
     * 一般的redis存值
     *
     * @param key       redis的key
     * @param value     值
     * @param time      过期时间
     * @param timeUnit  时间单位
     */
    private void set(String key, Object value, long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 基于逻辑过期的redis存值，在存值时并不设置过期时间，而是将过期时间作为redis的一个value存入<br/>
     * 该方法一般用于热点数据的情况，后续可通过手动的方式移除key
     *
     * @param key       redis的key
     * @param value     值
     * @param time      过期时间
     * @param timeUnit  时间单位
     */
    private void setWithLoginExpire(String key, Object value, long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 借助Redis的setnx命令实现锁的获取，保证多线程情况下只有一个线程能获取成功
     */
    private boolean tryLock(String key) {
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(absent);
    }

    /**
     * 释放锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
