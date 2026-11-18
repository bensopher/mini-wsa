package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.IngestClient.BatchResult;
import com.bensopher.miniwsa.generator.StubApi.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestClientTest {

    private final StubApi api = new StubApi();
    private final List<Duration> sleeps = new ArrayList<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<GeneratedEvent> batch =
            EventGeneratorTest.generator(1).generate(3, "run1").events();

    IngestClientTest() throws Exception {
    }

    @AfterEach
    void stop() {
        api.close();
    }

    @Test
    void postsTheBatchAsAJsonArrayWithTheApiKey() throws Exception {
        api.then(Reply.created(1));

        BatchResult result = client(api.uri()).send(batch);

        assertThat(result.status()).isEqualTo("201");
        assertThat(result.events()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.retries()).isZero();
        assertThat(json.readTree(api.bodies.peek()).size()).isEqualTo(3);
        assertThat(api.apiKeys).containsExactly("test-key");
    }

    @Test
    void a503IsRetriedWithExponentialBackoff() throws Exception {
        api.then(Reply.unavailable(null), Reply.unavailable(null), Reply.created(0));

        BatchResult result = client(api.uri()).send(batch);

        assertThat(result.status()).isEqualTo("201");
        assertThat(result.retries()).isEqualTo(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThat(api.bodies).hasSize(3);
    }

    @Test
    void theServersRetryAfterIsAMinimum() throws Exception {
        api.then(Reply.unavailable("5"), Reply.unavailable("1"), Reply.created(0));

        client(api.uri()).send(batch);

        // max(Retry-After, exponential): 5s instead of 1s, then 2s because the server's 1s is shorter.
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(2));
    }

    @Test
    void afterThreeRetriesThe503IsTheResult() throws Exception {
        api.then(Reply.unavailable(null), Reply.unavailable(null), Reply.unavailable(null), Reply.unavailable(null));

        BatchResult result = client(api.uri()).send(batch);

        assertThat(result.status()).isEqualTo("503");
        assertThat(result.retries()).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(api.bodies).hasSize(4);
    }

    @Test
    void otherErrorsAreNotRetried() throws Exception {
        api.then(new Reply(400, "{}", null));

        BatchResult result = client(api.uri()).send(batch);

        assertThat(result.status()).isEqualTo("400");
        assertThat(result.retries()).isZero();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void noResponseIsReportedAsAnIoError() throws Exception {
        URI closed = api.uri();
        api.close();

        assertThat(client(closed).send(batch).status()).isEqualTo("IO_ERROR");
    }

    private IngestClient client(URI target) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return new IngestClient(http, target, "test-key", json, sleeps::add, Duration.ofSeconds(1));
    }
}
