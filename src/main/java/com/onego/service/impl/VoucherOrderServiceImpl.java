package com.onego.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.onego.entity.dto.Result;
import com.onego.entity.VoucherOrder;
import com.onego.mapper.VoucherOrderMapper;
import com.onego.service.ISeckillVoucherService;
import com.onego.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.onego.utils.RedisIdWork;
import com.onego.utils.UserHolder;
import com.onego.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 优惠券订单服务实现类
 */
@Slf4j
//@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWork redisIdWork;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private IVoucherOrderService proxy;

    // 加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 线程池
    private static final ExecutorService ORDER_SECKILL_EXECUTOR = Executors.newSingleThreadExecutor();

    // 阻塞队列，用于存放订单信息
    /*
    private final BlockingQueue<VoucherOrder> orderBlockingQueue = new ArrayBlockingQueue<>(1024 * 1024);
    */

    // 类初始化后就去开启异步线程
    @PostConstruct
    private void init() {
        ORDER_SECKILL_EXECUTOR.submit(new VoucherTaskHandler());
    }

    private class VoucherTaskHandler implements Runnable {

        private static final String QUEUE_NAME = "stream.orders";
        private static final String GROUP_NAME = "g1";

        @Override
        public void run() {
            while (true) {
                try {
                    // 从redis消息队列获取消息 -> XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            // 指定消费者组和消费者（不存在会创建）
                            Consumer.from(GROUP_NAME, "c1"),
                            // 每次读取一条消息，如果2s内没有读到消息，则返回
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            // 指定监听的消息队列，并且每次读取最新的未消费的消息
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    // 判断消息
                    if (list == null) {
                        // 如果读取失败，继续下一次循环
                        continue;
                    }
                    // 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder order = getInMapRecord(record);
                    // 成功则创建订单
                    handleVoucherOrder(order);
                    // 确认消息 -> SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    // 出现异常则消息未被确认，而未被确认的消息将会进入pending list，
                    // 因此捕获到异常后，需要从pending list里面读取消息
                    handlePendingList();
                    log.error("订单异常", e);
                }
            }
        }

        @SuppressWarnings({"unchecked"})
        private void handlePendingList() {
            while (true) {
                try {
                    // 从pending list消息队列获取消息 -> XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            // 指定消费者组和消费者（不存在会创建）
                            Consumer.from(GROUP_NAME, "c1"),
                            // 每次读取一条消息，如果2s内没有读到消息，则返回
                            StreamReadOptions.empty().count(1),
                            // 指定监听的消息队列，pending list里从头开始读
                            StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                    );
                    // 判断消息
                    if (list == null || list.isEmpty()) {
                        // 如果读取失败，说明pending list没有，结束循环
                        break;
                    }
                    // 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder order = getInMapRecord(record);
                    // 成功则创建订单
                    handleVoucherOrder(order);
                    // 同样需要确认消息 -> SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("pending list异常", e);
                }
            }
        }

        private VoucherOrder getInMapRecord(MapRecord<String, Object, Object> record) {
            Map<Object, Object> value = record.getValue();
            return BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
        }

        // 往数据库里修改订单相关信息
        private void handleVoucherOrder(VoucherOrder order) {
            // 获取锁对象 ，严格来讲，经过前面的步骤后，这里出现并发问题的可能性已经很小了
            RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + order.getUserId());
            /*
             * boolean tryLock(long waitTime, long leaseTime, TimeUnit unit)
             *    waitTime：超过这个时间仍未获取锁，则返回。默认值-1，表示不等待
             *    leaseTime：锁的过期时间。默认30s，如果该值设为-1，将开启看门狗机制，每隔10s自动续期到30s。默认-1
             *    unit：时间单位
             */
            if (!lock.tryLock()) {
                return;
            }
            try {
                proxy.createVoucherOrder(order);
            } finally {
                lock.unlock();
            }
        }

        // 从阻塞队列里拿消息
        /*
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列中的订单信息，如果队列为空，将会阻塞
                    VoucherOrder order = orderBlockingQueue.take();
                    handleVoucherOrder(order);
                } catch (InterruptedException e) {
                    log.error("订单异常", e);
                }
            }
        }
        */
    }

    // 基于Redis的Stream消息队列实现异步秒杀
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 执行LUA脚本
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWork.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString());
        // 判断是否有购买资格
        long l = result.longValue();
        if (l != 0) {
            // 没有购买资格
            return Result.fail(l == 1 ? "库存不足" : "请勿重复下单");
        }
        // 下单信息已经在redis里保存了

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单ID
        return Result.ok(orderId);
    }

    // 基于Java阻塞队列实现的异步秒杀
    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 执行LUA脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        // 判断是否有购买资格
        long l = result.longValue();
        if (l != 0) {
            // 没有购买资格
            return Result.fail(l == 1 ? "库存不足" : "请勿重复下单");
        }
        // 如果有，把下单信息保存到阻塞队列中，后续会有异步线程监听这个队列，从而完成数据库操作
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWork.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderBlockingQueue.add(voucherOrder);

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单ID
        return Result.ok(orderId);
    }
    */

    /**
     * 将订单相关信息写入数据库
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
        // 一人一单
        Long userId = order.getUserId();

        int count = query().eq("user_id", userId).eq("voucher_id", order.getVoucherId()).count();
        if (count > 0) {
            log.error("已经购买一次");
            return;
        }
        // 减少库存，基于CAS实现的乐观锁防止超卖
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId()).gt("stock", 0)
                .update();
        if (!result) {
            log.error("扣减失败");
            return;
        }
        // 创建订单
        this.save(order);
    }
}
