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
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jh.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.jh.utils.RedisConstants.SHOP_GEO_KEY;

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
    // @Test
    // void testIdWorker() throws InterruptedException {
    //     CountDownLatch latch = new CountDownLatch(300);
    //     Runnable task = ()->{
    //         // 每个线程执行100次
    //         for (int i = 0; i < 100; i++) {
    //             long id = redisIdWorker.nextId("order");
    //             System.out.println("id = " + id);
    //         }
    //         latch.countDown();
    //     };
    //     long begin = System.currentTimeMillis();
    //     // 提交300次
    //     for (int i = 0; i < 300; i++) {
    //         es.submit(task);
    //     }
    //     latch.await();
    //     long end = System.currentTimeMillis();
    //     System.out.println("time = " + (end - begin));
    // }
    // @Test
    // void testSaveShop() throws InterruptedException {
    //     Shop shop = shopService.getById(1L);
    //     cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    // }
    // @Test
    // void userLogin(){
    //     // Long num = 13466803560L;
    //     // for (int i = 0; i < 1000; i++) {
    //     //     LoginFormDTO loginFormDTO = new LoginFormDTO();
    //     //     loginFormDTO.setPhone(num.toString());
    //     //     Result login = userService.login(loginFormDTO);
    //     //     num++;
    //     // }
    //     Set<String> keys = stringRedisTemplate.keys("login:token:*");
    //
    //     try (BufferedWriter writer = new BufferedWriter(new FileWriter("tokens.txt"))) {
    //         for (String line : keys) {
    //             int i = line.lastIndexOf(":");
    //             writer.write(line.substring(i+1));
    //             writer.newLine();  // 写入换行符
    //         }
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    // }
    @Test
    void loadShopData() {
        // 导入店铺位置信息到redis
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            // 使用locations是为了避免在每次循环中向redis添加信息，提升性能
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }


}
