local requestHash = redis.call('HGET', KEYS[1], 'requestHash')

if not requestHash then
    return 'MISSING'
end

if requestHash ~= ARGV[1] then
    return 'CONFLICT'
end

local executionToken = redis.call('HGET', KEYS[1], 'executionToken')
if executionToken ~= ARGV[2] then
    return 'STALE_OWNER'
end

redis.call('HSET', KEYS[1],
    'status', 'COMPLETED',
    'responseStatus', ARGV[3],
    'responseHeaders', ARGV[4],
    'responseBody', ARGV[5])
redis.call('PEXPIRE', KEYS[1], ARGV[6])

return 'COMPLETED'
