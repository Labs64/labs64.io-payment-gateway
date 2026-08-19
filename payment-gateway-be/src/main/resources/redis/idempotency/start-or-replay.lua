local redisTime = redis.call('TIME')
local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
local requestHash = redis.call('HGET', KEYS[1], 'requestHash')

if not requestHash then
    redis.call('HSET', KEYS[1],
        'requestHash', ARGV[1],
        'status', 'PROCESSING',
        'executionToken', ARGV[2],
        'startedAt', now)
    redis.call('PEXPIRE', KEYS[1], ARGV[3])
    return {'STARTED'}
end

if requestHash ~= ARGV[1] then
    return {'CONFLICT'}
end

local status = redis.call('HGET', KEYS[1], 'status')
if status == 'COMPLETED' then
    return {
        'COMPLETED',
        redis.call('HGET', KEYS[1], 'responseStatus') or '',
        redis.call('HGET', KEYS[1], 'responseHeaders') or '{}',
        redis.call('HGET', KEYS[1], 'responseBody') or 'null'
    }
end

local startedAt = tonumber(redis.call('HGET', KEYS[1], 'startedAt') or '0')
if now - startedAt >= tonumber(ARGV[4]) then
    redis.call('HSET', KEYS[1],
        'status', 'PROCESSING',
        'executionToken', ARGV[2],
        'startedAt', now)
    redis.call('HDEL', KEYS[1], 'responseStatus', 'responseHeaders', 'responseBody')
    redis.call('PEXPIRE', KEYS[1], ARGV[3])
    return {'STARTED'}
end

return {'PROCESSING'}
