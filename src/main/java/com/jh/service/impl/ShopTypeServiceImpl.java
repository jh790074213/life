package com.jh.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jh.dto.Result;
import com.jh.entity.ShopType;
import com.jh.mapper.ShopTypeMapper;
import com.jh.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.jh.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryType() {
        String typeKey = CACHE_SHOP_TYPE_KEY + "list";
        String typeJason = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY + "list");
        if(StrUtil.isNotBlank(typeJason)){
            return JSONUtil.toList(typeJason, ShopType.class);
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(typeKey,JSONUtil.toJsonStr(shopTypeList));
        return shopTypeList;
    }
}
