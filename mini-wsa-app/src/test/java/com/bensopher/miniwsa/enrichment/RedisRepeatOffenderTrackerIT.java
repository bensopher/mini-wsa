package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.AbstractIntegrationTest;
import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import com.bensopher.miniwsa.event.Rule;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.event.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Repeat-offender semantics against a real Redis: window in event time (A5), counting (A6), idempotency (A4). */
class RedisRepeatOffenderTrackerIT extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");
    private static final String IP = "203.0.113.42";
    private static final String OTHER_IP = "198.51.100.7";

    @Autowired
    RedisRepeatOffenderTracker tracker;

    @Test
    void sixEventsFromOneIpCountZeroToFivePriorEvents() {
        List<SecurityEvent> events = IntStream.range(0, 6).mapToObj(i -> event("e" + i, IP, minutes(i))).toList();

        assertThat(tracker.recordAndCount(events)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void earlierRequestsCountToo() {
        tracker.recordAndCount(List.of(event("e1", IP, minutes(0)), event("e2", IP, minutes(1))));

        assertThat(tracker.recordAndCount(List.of(event("e3", IP, minutes(2))))).containsExactly(2);
    }

    @Test
    void resultIsAlignedWithTheInputAcrossInterleavedIps() {
        List<SecurityEvent> events = List.of(
                event("a1", IP, minutes(0)), event("b1", OTHER_IP, minutes(1)), event("a2", IP, minutes(2)),
                event("b2", OTHER_IP, minutes(3)), event("a3", IP, minutes(4)));

        assertThat(tracker.recordAndCount(events)).containsExactly(0, 0, 1, 1, 2);
    }

    @Test
    void differentIpsAreIsolated() {
        tracker.recordAndCount(IntStream.range(0, 6).mapToObj(i -> event("a" + i, IP, minutes(i))).toList());

        assertThat(tracker.recordAndCount(List.of(event("b1", OTHER_IP, minutes(6))))).containsExactly(0);
    }

    @Test
    void outOfOrderArrivalIsCountedByEventTime() {
        assertThat(tracker.recordAndCount(List.of(event("e1", IP, minutes(0)), event("e2", IP, minutes(8)))))
                .containsExactly(0, 1);

        // Arrives last but happened in between: only e1 is before it in [t-10m, t]; e2 happened later.
        assertThat(tracker.recordAndCount(List.of(event("late", IP, minutes(4))))).containsExactly(1);

        // A later event sees all three.
        assertThat(tracker.recordAndCount(List.of(event("e3", IP, minutes(9))))).containsExactly(3);
    }

    @Test
    void retriedEventIsNeitherDoubleCountedNorCountsItself() {
        SecurityEvent event = event("e1", IP, minutes(0));
        assertThat(tracker.recordAndCount(List.of(event))).containsExactly(0);

        assertThat(tracker.recordAndCount(List.of(event))).as("retry").containsExactly(0);
        assertThat(tracker.recordAndCount(List.of(event("e2", IP, minutes(1))))).as("next event").containsExactly(1);
        assertThat(zcard(IP)).isEqualTo(2);
    }

    @Test
    void sameEventIdAtTwoTimestampsCountsAsTwoEvents() {
        assertThat(tracker.recordAndCount(List.of(event("e1", IP, minutes(0)), event("e1", IP, minutes(1)))))
                .containsExactly(0, 1);
        assertThat(tracker.recordAndCount(List.of(event("e2", IP, minutes(2))))).containsExactly(2);
    }

    @Test
    void windowIsTenMinutesInclusive() {
        assertThat(tracker.recordAndCount(List.of(event("e1", IP, minutes(0)), event("e2", IP, minutes(10)))))
                .as("exactly 10 minutes apart").containsExactly(0, 1);
        assertThat(tracker.recordAndCount(List.of(event("f1", OTHER_IP, minutes(0)), event("f2", OTHER_IP, minutes(11)))))
                .as("11 minutes apart").containsExactly(0, 0);
    }

    @Test
    void keyGetsAnExpiry() {
        tracker.recordAndCount(List.of(event("e1", IP, minutes(0))));

        Long ttl = redis.getExpire(RedisRepeatOffenderTracker.KEY_PREFIX + IP, TimeUnit.MILLISECONDS);
        assertThat(ttl).isBetween(Duration.ofHours(2).minusMinutes(1).toMillis(), Duration.ofHours(2).toMillis());
    }

    @Test
    void entriesOlderThanRetentionArePrunedRelativeToTheNewestEvent() {
        // Event times are in May 2026 while the wall clock is later: pruning must follow event time.
        tracker.recordAndCount(List.of(event("old", IP, minutes(0)), event("recent", IP, minutes(30))));
        assertThat(zcard(IP)).isEqualTo(2);

        tracker.recordAndCount(List.of(event("new", IP, minutes(61))));

        assertThat(zcard(IP)).as("'old' is more than 1h before the newest event").isEqualTo(2);
    }

    @Test
    void recoversWhenRedisForgetsTheScript() {
        tracker.recordAndCount(List.of(event("e1", IP, minutes(0))));
        redis.execute((RedisCallback<Void>) connection -> {
            connection.scriptingCommands().scriptFlush();
            return null;
        });

        assertThat(tracker.recordAndCount(List.of(event("e2", IP, minutes(1))))).containsExactly(1);
    }

    @Test
    void ipv6AddressesWorkAsKeys() {
        List<SecurityEvent> events = List.of(event("e1", "2001:db8::1", minutes(0)), event("e2", "2001:db8::1", minutes(1)));

        assertThat(tracker.recordAndCount(events)).containsExactly(0, 1);
    }

    private long zcard(String ip) {
        return redis.opsForZSet().zCard(RedisRepeatOffenderTracker.KEY_PREFIX + ip);
    }

    private static Instant minutes(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private static SecurityEvent event(String eventId, String clientIp, Instant timestamp) {
        return new SecurityEvent(eventId, timestamp, 14227L, null, clientIp, null, "/", "GET", null, null,
                new Rule("1", null, null, Severity.LOW, Category.BOT), Action.MONITOR, null, null, null);
    }
}
