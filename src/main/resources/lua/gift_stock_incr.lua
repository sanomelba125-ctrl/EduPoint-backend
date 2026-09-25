-- ===================================================================
-- 礼品库存反向回补/补偿 Lua 脚本 (P1-1 & P1-5)
-- KEYS[1]: gift:stock:{giftId}
-- ARGV[1]: quantity (回补数量)
-- 返回值:
--   回补后的最新库存数值
-- ===================================================================
local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

local currentStock = redis.call('GET', stockKey)
if currentStock == false or currentStock == nil then
    redis.call('SET', stockKey, quantity)
    return quantity
end

return redis.call('INCRBY', stockKey, quantity)
