package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.IngestClient.BatchResult;
import com.bensopher.miniwsa.generator.StubApi.Reply;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BatchSenderTest {

    @Test
    void sendsEveryBatchWithBoundedConcurrencyAndSummarizes() throws Exception {
        try (StubApi api = new StubApi()) {
            api.then(Reply.created(2), Reply.unavailable(null));
            List<List<GeneratedEvent>> batches = batches(12, 10);

            SendSummary summary = new BatchSender(client(api), 3).send(batches);

            assertThat(summary.batches()).isEqualTo(12);
            assertThat(summary.statuses()).isEqualTo(Map.of("201", 12L));
            assertThat(summary.duplicates()).isEqualTo(2);
            assertThat(summary.eventsStored()).isEqualTo(118);
            assertThat(summary.retries()).isEqualTo(1);
            assertThat(summary.failed()).isFalse();
            assertThat(api.maxInFlight.get()).isBetween(2, 3);
        }
    }

    @Test
    void anyBatchThatIsNotStoredMakesTheRunFail() throws Exception {
        try (StubApi api = new StubApi()) {
            api.then(new Reply(400, "{}", null));

            SendSummary summary = new BatchSender(client(api), 1).send(batches(3, 5));

            assertThat(summary.statuses()).isEqualTo(Map.of("201", 2L, "400", 1L));
            assertThat(summary.eventsStored()).isEqualTo(10);
            assertThat(summary.failed()).isTrue();
        }
    }

    @Test
    void percentilesUseNearestRank() {
        List<BatchResult> results = new ArrayList<>();
        for (int ms = 1; ms <= 20; ms++) {
            results.add(new BatchResult("201", 1, 0, 0, Duration.ofMillis(ms)));
        }

        SendSummary summary = SendSummary.of(results, Duration.ofSeconds(2));

        assertThat(summary.p50()).isEqualTo(Duration.ofMillis(10));
        assertThat(summary.p95()).isEqualTo(Duration.ofMillis(19));
        assertThat(summary.eventsPerSecond()).isEqualTo(10.0);
    }

    private static IngestClient client(StubApi api) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return new IngestClient(http, api.uri(), "k", JsonMapper.builder().build(), duration -> { },
                Duration.ofMillis(1));
    }

    private static List<List<GeneratedEvent>> batches(int count, int size) {
        List<GeneratedEvent> events = EventGeneratorTest.generator(1).generate(count * size, "run1").events();
        return IntStream.range(0, count).mapToObj(i -> events.subList(i * size, (i + 1) * size)).toList();
    }
}
