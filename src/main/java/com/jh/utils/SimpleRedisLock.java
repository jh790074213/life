package com.jh.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;


import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
    private String name;
    public static final String KEY_PREFIX = "lock:";
    // 对每个JVM生成一份UUID，在同一个JVM下线程id是唯一，但是跨JVM不一定是唯一，因为是JVM给线程生成的递增数字
    public static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private final StringRedisTemplate stringRedisTemplate;
    // 初始化Lua脚本
    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取锁,使用UUID和线程id作为锁唯一标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 自动拆箱可能会空指针，当success内部是null时
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock() {
        // 调用Lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());

    }
    // @Override
    // public void unlock() {
    //     String threadId = ID_PREFIX + Thread.currentThread().getId();
    //     String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //     if(threadId.equals(id)) {
    //         stringRedisTemplate.delete(KEY_PREFIX + name);
    //     }
    // }
}
