package com.bensopher.miniwsa.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorCommandTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void writesBatchesAsPostableJsonArrays(@TempDir Path dir) throws Exception {
        int exit = run("--count", "1200", "--batch-size", "500", "--seed", "1", "--out", dir.toString());

        assertThat(exit).isZero();
        assertThat(files(dir)).containsExactly("events-00001.json", "events-00002.json", "events-00003.json");
        JsonNode last = json.readTree(Files.readString(dir.resolve("events-00003.json")));
        assertThat(last.isArray()).isTrue();
        assertThat(last.size()).isEqualTo(200);
        assertThat(out.toString()).contains("seed 1 (pass --seed 1", "Wrote 1200 events to 3 files");
    }

    @Test
    void writesOneEventPerLineAsNdjson(@TempDir Path dir) throws Exception {
        run("--count", "10", "--format", "ndjson", "--out", dir.toString());

        List<String> lines = Files.readAllLines(dir.resolve("events-00001.ndjson"));
        assertThat(lines).hasSize(10).allSatisfy(line -> assertThat(json.readTree(line).isObject()).isTrue());
    }

    @Test
    void rejectsBatchesTheApiWouldRefuse(@TempDir Path dir) {
        int exit = run("--batch-size", "1001", "--out", dir.toString());

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).contains("--batch-size must be between 1 and 1000");
    }

    @Test
    void sendsToTheApiAndPrintsASummary() throws Exception {
        try (StubApi api = new StubApi()) {
            int exit = run("--count", "250", "--batch-size", "100", "--target", api.uri().toString(),
                    "--wait-for-health", "--api-key", "my-key");

            assertThat(exit).isZero();
            assertThat(api.bodies).hasSize(3);
            assertThat(api.apiKeys).containsOnly("my-key");
            assertThat(out.toString()).contains("Sending 250 events", "Batches:        3 (status: {201=3})",
                    "Events stored:  250", "events/s", "p95");
        }
    }

    @Test
    void exitsNonZeroWhenABatchIsNotStored() throws Exception {
        try (StubApi api = new StubApi()) {
            api.then(new StubApi.Reply(400, "{}", null));

            assertThat(run("--count", "20", "--batch-size", "10", "--target", api.uri().toString())).isEqualTo(1);
            assertThat(out.toString()).contains("{201=1, 400=1}");
        }
    }

    @Test
    void readsShortDurations() {
        var converter = new GeneratorCommand.DurationConverter();

        assertThat(converter.convert("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(converter.convert("60m")).isEqualTo(Duration.ofMinutes(60));
        assertThat(converter.convert("2h")).isEqualTo(Duration.ofHours(2));
        assertThat(converter.convert("1d")).isEqualTo(Duration.ofDays(1));
        assertThatThrownBy(() -> converter.convert("PT1H")).isInstanceOf(CommandLine.TypeConversionException.class);
    }

    private int run(String... args) {
        CommandLine command = new CommandLine(new GeneratorCommand(EventGeneratorTest.CLOCK, duration -> { }));
        command.setOut(new PrintWriter(out, true));
        command.setErr(new PrintWriter(err, true));
        return command.execute(args);
    }

    private static List<String> files(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }
}
