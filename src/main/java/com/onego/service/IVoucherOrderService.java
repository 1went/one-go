package com.onego.service;

import com.onego.entity.dto.Result;
import com.onego.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 异步秒杀<br/>
     *      先在redis里判断库存、是否重复等，成功则返回订单ID并将订单信息存入一个阻塞队列。<br/>
     *      再由一个异步线程从阻塞队列里获取订单信息，将其写入数据库
     */
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder order);
}
