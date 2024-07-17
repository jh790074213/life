package com.jh;

import cn.hutool.json.JSONUtil;
import com.jh.dto.LoginFormDTO;
import com.jh.dto.Result;
import com.jh.entity.Shop;
import com.jh.service.impl.ShopServiceImpl;
import com.jh.service.impl.UserServiceImpl;
import com.jh.utils.CacheClient;
import com.jh.utils.RedisData;
import com.jh.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.jh.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private UserServiceImpl userService;
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    // 定义500个线程的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
            // 每个线程执行100次
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        // 提交300次
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);

    }
    @Test
    void userLogin(){
        // Long num = 13466803560L;
        // for (int i = 0; i < 1000; i++) {
        //     LoginFormDTO loginFormDTO = new LoginFormDTO();
        //     loginFormDTO.setPhone(num.toString());
        //     Result login = userService.login(loginFormDTO);
        //     num++;
        // }
        Set<String> keys = stringRedisTemplate.keys("login:token:*");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("tokens.txt"))) {
            for (String line : keys) {
                int i = line.lastIndexOf(":");
                writer.write(line.substring(i+1));
                writer.newLine();  // 写入换行符
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
