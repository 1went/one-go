package com.onego.utils;

/**
 * @author yiwt
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeSeconds  设置锁的过期时间
     * @return             true表示获取锁成功，false表示获取失败
     */
    boolean tryLock(long timeSeconds);

    /**
     * 释放锁
     */
    void unlock();
}
