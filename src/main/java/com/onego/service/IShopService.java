package com.onego.service;

import com.onego.entity.dto.Result;
import com.onego.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    /**
     * 更新店铺信息时需要保证数据库和缓存的一致性<br/>
     * 采用的方案是：先更新数据库，再删缓存<br/>
     * 单体应用可以通过 @Transactional 保证两个操作的原子性
     * @param shop 商铺
     */
    Result updateShop(Shop shop);

    /**
     * 根据商铺类型查询商铺，支持通过传入经纬度并按商铺距离排序、分页返回
     * @param typeId  商铺类型
     * @param current 当前页
     * @param x       所在地区经度
     * @param y       所在地区的维度
     * @param sortBy 排序字段
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy);
}
