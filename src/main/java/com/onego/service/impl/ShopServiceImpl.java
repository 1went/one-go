package com.onego.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.onego.entity.dto.Result;
import com.onego.entity.Shop;
import com.onego.mapper.ShopMapper;
import com.onego.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.utils.CacheClient;
import com.onego.constants.RedisConstants;
import com.onego.constants.SystemConstants;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;  // 封装的缓存工具类

    @Override
    public Result queryById(Long id) {
        // 做缓存穿透
        Shop shop = cacheClient.querySolvePassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS, this::getById);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能未空");
        }
        this.updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String scoreBy) {
        if (x == null || y == null) {
            // 不需要根据坐标查询
            Page<Shop> page = this.query()
                    .eq("type_id", typeId)
                    .orderByDesc(StrUtil.isNotBlank(scoreBy), scoreBy)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        /* 根据所在坐标查询周围的店铺 */
        // 计算分页参数
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // Redis6.0之前使用radius查询 --> GEORADIUS key 圆心经度 圆心维度 半径 WITHDIST，返回的是从 0 ~ end 的数据
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(
                    key,
                    new Circle(new Point(x, y), new Distance(5.0, RedisGeoCommands.DistanceUnit.KILOMETERS)),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
                );
        /*
            Redis6.0及以后，使用search。GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() //
                    .search(
                            key,
                            GeoReference.fromCoordinate(x, y),
                            new Distance(5000),
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                    );
        */
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        // 如果集合里的数据不足start，就不用截取
        if (content.size() <= start) {
            return Result.ok(Collections.emptyList());
        }
        // 店铺ID集合
        List<Long> ids = new ArrayList<>(content.size());
        // key:店铺 value:和距离
        Map<Long, Distance> distanceMap = new HashMap<>(content.size());
        // 截取从 start ~ end 的数据
        content.stream().skip(start).forEach(result -> {
            // 拿到店铺id
            String shopIdStr = result.getContent().getName();
            Long shopId = Long.parseLong(shopIdStr);
            ids.add(shopId);
            // 拿到距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 根据id批量查询，注意保证有序
        String str = StrUtil.join(",", ids);
        List<Shop> shopList = this.query().in("id", ids).last("ORDER BY FIELD(id," + str + ")").list();
        // 存储shop的距离
        List<Shop> finalShopList = shopList.stream().peek(shop -> shop.setDistance(distanceMap.get(shop.getId()).getValue())).collect(Collectors.toList());
        return Result.ok(finalShopList);
    }
}
