package com.onego.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.onego.entity.dto.Result;
import com.onego.entity.ShopType;
import com.onego.service.IShopTypeService;
import com.onego.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/shop-type")
@Slf4j
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        // 因为shop的类型基本不变，使用string就行
        String typeStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE);
        List<ShopType> typeList;
        if (StrUtil.isBlank(typeStr)) {
            typeList = typeService
                    .query().orderByAsc("sort").list();
            // 把list转为string存储
            typeStr = JSONUtil.toJsonStr(typeList);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE, typeStr, 5L, TimeUnit.MINUTES);
        } else {
            // 做一个转换
            typeList = JSONUtil.toList(typeStr, ShopType.class);
        }
        return Result.ok(typeList);
    }
}
