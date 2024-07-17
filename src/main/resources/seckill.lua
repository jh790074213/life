--优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 优惠券库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:stock:' .. voucherId

-- 业务逻辑
-- 1.判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end
-- 2.判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已经购买过
    return 2
end
-- 3.扣减库存
redis.call('incrby', stockKey, -1)
-- 4.下单保存用户id
redis.call('sadd', orderKey, userId)
