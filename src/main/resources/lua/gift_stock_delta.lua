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
if newStock < 0 then
    newStock = 0
end
redis.call('SET', stockKey, newStock)
return newStock
