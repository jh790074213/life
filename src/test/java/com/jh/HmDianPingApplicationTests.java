package com.jh;

import cn.hutool.json.JSONUtil;
import com.jh.entity.Shop;
import com.jh.service.impl.ShopServiceImpl;
import com.jh.utils.CacheClient;
import com.jh.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.jh.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);

    }


}
