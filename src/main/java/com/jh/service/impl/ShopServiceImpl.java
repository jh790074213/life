package com.jh.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jh.dto.Result;
import com.jh.entity.Shop;
import com.jh.mapper.ShopMapper;
import com.jh.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.jh.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透 使用无效key存放redis
        // Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id){
        String key =CACHE_SHOP_KEY + id;
        // 1.查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断redis中存的是否为空值isNotBlank()为null、""、"\t\n"时都为false
        if(shopJson != null) {
            return null;
        }
        // 4.实现缓存重建
        // 4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 4.2判断是否获取成功
            while(!tryLock(lockKey)){
                Thread.sleep(50);
            }
            // 这里再次查询redis，是为了防止之前的线程已经写入了redis
            String shopJsonSecond = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJsonSecond)){
                // 存在返回
                return JSONUtil.toBean(shopJsonSecond, Shop.class);
            }
            // 4.4成功查询数据库，这里使用mybatis_plus方法
            shop = getById(id);
            // 模拟延迟
            Thread.sleep(200);
            // 5.数据库不存在返回错误，并将空值保存到redis防止缓存穿透
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放锁
            unlock(lockKey);
        }

        // 8.返回信息
        return shop;
    }
    public Shop queryWithPassThrough(Long id){
        String key =CACHE_SHOP_KEY + id;
        // 1.查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断redis中存的是否为空值isNotBlank()为null、""、"\t\n"时都为false
        if(shopJson != null){
            return null;
        }
        // 4.不存在查询数据库，这里使用mybatis_plus方法
        Shop shop = getById(id);
        // 5.数据库不存在返回错误，并将空值保存到redis防止缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回信息
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
    // 获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 自动拆箱可能会空指针
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
