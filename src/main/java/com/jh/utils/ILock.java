package com.jh.utils;

public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec 过期时间
     * @return 是否获取锁成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
