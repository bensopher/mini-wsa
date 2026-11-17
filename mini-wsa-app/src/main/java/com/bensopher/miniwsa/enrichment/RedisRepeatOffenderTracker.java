package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.SecurityEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repeat-offender state in Redis: one sorted set per client IP, updated by an atomic Lua script. Redis rather
 * than an in-memory map so the counts are shared, and consistent, across every app instance.
 * <p>
 * A batch makes one script call per IP, all sent in a single pipeline: one network round trip per request.
 * Redis is written before Postgres. If the insert then fails, Redis remembers events that were never stored;
 * a client retry is still safe because recording is idempotent, at the cost of slightly inflated counts for
 * events that are never retried.
 */
@Component
public class RedisRepeatOffenderTracker implements RepeatOffenderTracker {

    static final String KEY_PREFIX = "miniwsa:ro:";

    private static final RedisScript<?> SCRIPT = RedisScript.of(new ClassPathResource("redis/repeat-offender.lua"));

    private final StringRedisTemplate redis;
    private final ScoringProperties.RepeatOffender config;

    public RedisRepeatOffenderTracker(StringRedisTemplate redis, ScoringProperties properties) {
        this.redis = redis;
        this.config = properties.repeatOffender();
    }

    @Override
    public List<Integer> recordAndCount(List<SecurityEvent> eventsSortedByTime) {
        // Positions of each IP's events, in time order. Each IP becomes one script call on one key.
        Map<String, List<Integer>> positionsByIp = new LinkedHashMap<>();
        for (int i = 0; i < eventsSortedByTime.size(); i++) {
            positionsByIp.computeIfAbsent(eventsSortedByTime.get(i).clientIp(), ip -> new ArrayList<>()).add(i);
        }
        List<byte[][]> calls = new ArrayList<>();
        positionsByIp.forEach((ip, positions) -> calls.add(scriptArguments(ip, positions, eventsSortedByTime)));

        List<Object> results = runScripts(calls);

        Integer[] counts = new Integer[eventsSortedByTime.size()];
        int call = 0;
        for (List<Integer> positions : positionsByIp.values()) {
            List<?> perEvent = (List<?>) results.get(call++);
            for (int j = 0; j < positions.size(); j++) {
                counts[positions.get(j)] = ((Number) perEvent.get(j)).intValue();
            }
        }
        return Arrays.asList(counts);
    }

    private byte[][] scriptArguments(String ip, List<Integer> positions, List<SecurityEvent> events) {
        byte[][] args = new byte[4 + positions.size() * 2][];
        args[0] = bytes(KEY_PREFIX + ip);
        args[1] = bytes(config.window().toMillis());
        args[2] = bytes(config.retention().toMillis());
        args[3] = bytes(config.keyTtl().toMillis());
        int a = 4;
        for (int position : positions) {
            SecurityEvent event = events.get(position);
            args[a++] = bytes(event.timestamp().toEpochMilli());
            args[a++] = bytes(member(event));
        }
        return args;
    }

    /**
     * The same (eventId, timestamp) identity Postgres deduplicates on, at the same microsecond precision, so a
     * retried event is recognized while two events sharing an eventId at different times stay distinct (A4).
     */
    static String member(SecurityEvent event) {
        return event.eventId() + ":" + ChronoUnit.MICROS.between(Instant.EPOCH, event.timestamp());
    }

    /**
     * Sends every call with EVALSHA in one pipeline. The script is loaded lazily: if Redis doesn't know it
     * (first use, or after a Redis restart), it is loaded and the pipeline sent once more, which is safe
     * because no call ran.
     */
    private List<Object> runScripts(List<byte[][]> calls) {
        try {
            return pipeline(calls);
        } catch (RuntimeException e) {
            if (!isNoScript(e)) {
                throw e;
            }
            redis.execute((RedisCallback<String>) connection ->
                    connection.scriptingCommands().scriptLoad(bytes(SCRIPT.getScriptAsString())));
            return pipeline(calls);
        }
    }

    private List<Object> pipeline(List<byte[][]> calls) {
        return redis.executePipelined((RedisCallback<Object>) connection -> {
            for (byte[][] keyAndArgs : calls) {
                connection.scriptingCommands().evalSha(SCRIPT.getSha1(), ReturnType.MULTI, 1, keyAndArgs);
            }
            return null;
        });
    }

    private static boolean isNoScript(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("NOSCRIPT")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] bytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }
}
