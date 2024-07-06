package com.jh.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jh.dto.Result;
import com.jh.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.jh.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在返回
            return JSONUtil.toBean(json, type);
        }
        // 判断redis中存的是否为空值isNotBlank()为null、""、"\t\n"时都为false
        if (json != null) {
            return null;
        }
        // 4.不存在查询数据库，这里使用函数编程方式，调用用户指定方法
        R r = dbFallBack.apply(id);
        // 5.数据库不存在返回错误，并将空值保存到redis防止缓存穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在写入redis
        this.set(key, r, time, unit);
        // 7.返回信息
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.缓存不存在返回null
            return null;
        }
        // 4.命中判断逻辑过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // redisData.getData()并不是返回Object类型而是class cn.hutool.json.JSONObject，不能直接强转为Shop
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期返回信息
            return r;
        }
        // 6.过期缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁
        if (isLock) {
            // 6.3.1 成功开启线程执行重建
            // 需要再次判断是否逻辑过期，假设两线程，都判断过期，但一个线程在另一个线程获取锁之前完成了重建并释放了锁，那么另一个线程还会进行重建操作，因此重建过程需要再次判断释放过期
            // 1.查询商铺缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            // 2.判断是否存在
            if (StrUtil.isBlank(shopJson)) {
                // 3.缓存不存在返回null
                return null;
            }
            // 4.命中判断逻辑过期时间
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            // redisData.getData()并不是返回Object类型而是class cn.hutool.json.JSONObject，不能直接强转为Shop
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 5.未过期返回信息
                return r;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallBack.apply(id);
                    Thread.sleep(200);
                    // 写入redis
                    this.setWithLogicExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6.3.2 失败返回旧的店铺信息
        return r;
    }

    // 建立执行重建的线程池
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 自动拆箱可能会空指针
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
