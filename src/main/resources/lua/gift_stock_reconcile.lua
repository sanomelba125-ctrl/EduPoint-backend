-- ===================================================================
-- 礼品库存对账原子修正 Lua 脚本 (P1-5)
--
-- 背景：库存对账以 MySQL 为准修正 Redis，但 Redis 的可用库存会被
--      秒杀预扣(DECRBY)与失败补偿(INCRBY)持续变动。若在「读取观测值」
--      与「写回修正值」之间发生新的预扣，直接 SET 会把该次预扣抹掉，
--      表现为「用户已抢到却显示没抢到」的少卖。
--
-- 本脚本用单次原子的「比较并修正(CAS)」消除该竞态：
-- 仅当 Key 的当前值仍等于调用方观测到的值时，才写入目标值。
--
-- KEYS[1]: gift:stock:{giftId}
-- ARGV[1]: expectStock  对账观测到的 Redis 值
-- ARGV[2]: targetStock  由 MySQL 库存与在途预扣计算出的目标可用库存
--
-- 返回值:
--   1 - 观测值未被并发修改，已修正为目标值
--   0 - 观测值已被并发修改（期间发生预扣/回补），本次跳过修正，交由下一轮对账
-- ===================================================================
local stockKey = KEYS[1]
local expectStock = ARGV[1]
local targetStock = ARGV[2]

local currentStock = redis.call('GET', stockKey)
if currentStock == false or currentStock == nil then
    return 0
end

if currentStock ~= expectStock then
    return 0
end

redis.call('SET', stockKey, targetStock)
return 1
