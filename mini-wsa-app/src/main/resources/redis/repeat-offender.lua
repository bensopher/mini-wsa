-- Records one IP's events and counts, for each, the OTHER events of that IP in [ts - window, ts].
-- Runs atomically, so concurrent requests on any number of app instances see consistent counts.
-- Touches a single key, which keeps it compatible with Redis Cluster.
--
-- KEYS[1]   = miniwsa:ro:<canonical ip>    sorted set: member = eventId:epochMicros, score = epoch millis
-- ARGV[1]   = window ms, ARGV[2] = retention ms, ARGV[3] = key TTL ms
-- ARGV[4..] = ts1, member1, ts2, member2, ...   ascending by ts
-- Returns   one count per event, in argument order.
local window, retention, ttl = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
local results = {}
for i = 4, #ARGV, 2 do
  local ts, member = tonumber(ARGV[i]), ARGV[i + 1]
  local prior = redis.call('ZCOUNT', KEYS[1], ts - window, ts)
  if redis.call('ZSCORE', KEYS[1], member) then
    -- A retry of an event already recorded: its member embeds its timestamp, so it lies inside this
    -- window and was just counted. An event never counts itself (A4).
    prior = prior - 1
  end
  redis.call('ZADD', KEYS[1], ts, member)   -- re-adding an existing member changes nothing
  results[#results + 1] = prior
end
-- Prune relative to the newest EVENT time, not the wall clock, so replayed or historical data still works.
local newest = redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES')
if newest[2] then
  redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. (tonumber(newest[2]) - retention))
end
-- Idle IPs disappear, which bounds memory.
redis.call('PEXPIRE', KEYS[1], ttl)
return results
