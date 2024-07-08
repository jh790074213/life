package com.jh.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 起始时间戳，以秒为单位 2024-7-8-0:0:0
     */
    public static final long BEGIN_TIMESTAMP = 1720396800L;
    public static final int COUNT_BITS = 32;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成全局ID
     * @param keyPrefix 对应业务名称
     * @return 全局ID
     */
    public long nextId(String keyPrefix){
        // 1.生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.生成序列号,这里加上日期防止一个业务在一个key上数据量太大
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 这里不断增加一个字段的value
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3.拼接返回，左移后进行或运算拼接
        return timeStamp << COUNT_BITS | count;
    }

    // public static void main(String[] args) {
    //     LocalDateTime localDateTime = LocalDateTime.of(2024, 7, 8, 0, 0, 0);
    //     long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
    //     System.out.println(epochSecond);
    // }
}
