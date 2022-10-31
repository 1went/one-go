-- 比较当前锁是不是自己的
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁
    redis.call('del', KEYS[1])
end