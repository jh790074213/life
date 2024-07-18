package com.jh.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {// 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://114.116.225.208:6379").setPassword("@jh987654321");// 创建RedissonClient对象
        return Redisson.create(config);
    }
}
