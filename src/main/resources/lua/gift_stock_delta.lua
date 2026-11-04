-- ===================================================================
-- 礼品库存增量调整 Lua 脚本 (F3 修复)
-- KEYS[1]: gift:stock:{giftId}
-- ARGV[1]: delta (增量，可正可负)
-- ARGV[2]: dbStock (数据库当前已提交库存，Key 不存在时作为初始值)
-- 返回值:
--   调整后的最新库存数值
-- ===================================================================
local stockKey = KEYS[1]
local delta = tonumber(ARGV[1]) or 0
local dbStock = tonumber(ARGV[2]) or 0

local currentStock = redis.call('GET', stockKey)
if currentStock == false or currentStock == nil then
    -- Key 不存在，说明尚未预热，直接以数据库最新库存初始化
    redis.call('SET', stockKey, dbStock)
    return dbStock
end

local currentVal = tonumber(currentStock)
if currentVal == nil then
    redis.call('SET', stockKey, dbStock)
    return dbStock
end

local newStock = currentVal + delta
-- 注意：当 delta < 0 导致 newStock < 0 时，绝不能强行钳制为 0（F3 修复）！
-- 否则并发在途单在 MySQL 扣减失败触发 compensateOnce 时，会在 0 基础上增加导致 Redis 库存虚增。
-- 保留真实负值差额，当在途失败单回补时恰好抵消归零，且在此期间阻止新的秒杀请求预扣。
redis.call('SET', stockKey, newStock)
return newStock
