-- 1.利用LUA脚本的原子性完成：判断库存是否充足、用户是否重复下单、扣减库存、将订单信息添加到stream里
-- 参数1：优惠券ID
local voucherId = ARGV[1]
-- 参数2：用户ID
local userId = ARGV[2]
-- 参数3：订单ID
local orderId = ARGV[3]

-- 2. 涉及到的key
-- 2.1 Redis中库存的key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 Redis中订单的key,value购买了该订单的所有用户
local orderKey = 'seckill:order:' .. voucherId

-- 3. 实际业务
-- 3.1 库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 不足，返回1
    return 1
end
-- 3.2 用户是否重复下单
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 重复下单，返回2
    return 2
end
-- 3.3 扣减库存
redis.call('incrby', stockKey, -1)
-- 3.4 保存用户到订单key
redis.call('sadd', orderKey, userId)
-- 3.5 有秒杀资格，将订单信息(用户、订单id、商品id)发送到stream.orders消息队列里
--  先在redis里创建消费者组 XGROUP CREATE stream.orders g1 0 MKSTREAM
--  发送消息的命令 XADD stream.orders * k1 v1 ...
redis.call('XADD', 'stream.orders', '*', 'userId', userId, 'id', orderId, 'voucherId', voucherId)
-- 3.6 成功返回0
return 0