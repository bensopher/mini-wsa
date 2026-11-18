package com.bensopher.miniwsa.generator;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "mini-wsa-generator", mixinStandardHelpOptions = true,
        description = "Generates realistic security events and sends them to the ingest API, or writes them to files.")
public class GeneratorCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--count", description = "Total events (default: ${DEFAULT-VALUE}).", defaultValue = "10000")
    int count;

    @Option(names = "--seed", description = "Random seed, for a reproducible run (default: random, printed at start).")
    Long seed;

    @Option(names = "--span", description = "Events are spread over this period, ending now, e.g. 30s, 60m, 2h, 1d "
            + "(default: ${DEFAULT-VALUE}).", defaultValue = "60m", converter = DurationConverter.class)
    Duration span;

    @Option(names = "--configs", split = ",", description = "Comma-separated configIds (default: ${DEFAULT-VALUE}).",
            defaultValue = "14227,14228,20001")
    List<Long> configIds;

    @Option(names = "--wave-ratio", description = "Fraction of events that belong to attack waves, 0 to 1 "
            + "(default: ${DEFAULT-VALUE}).", defaultValue = "0.4")
    double waveRatio;

    @Option(names = "--out", description = "Write event files to this directory instead of sending them.")
    Path out;

    @Option(names = "--format", description = "File format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            defaultValue = "array")
    EventFiles.Format format;

    @Option(names = "--batch-size", description = "Events per request or file (default: ${DEFAULT-VALUE}, at most "
            + "1000).", defaultValue = "500")
    int batchSize;

    @Option(names = "--target", description = "API base URL to POST to (default: ${DEFAULT-VALUE}).",
            defaultValue = "http://localhost:8080")
    URI target;

    @Option(names = "--concurrency", description = "Requests in flight at once (default: ${DEFAULT-VALUE}).",
            defaultValue = "4")
    int concurrency;

    @Option(names = "--wait-for-health", description = "Wait up to 90s for the API to be ready before sending.")
    boolean waitForHealth;

    // The default is not shown in --help, so a key taken from the environment is never printed.
    @Option(names = "--api-key", description = "Sent as X-API-Key (default: env MINIWSA_API_KEY, else dev-ingest-key).",
            defaultValue = "${env:MINIWSA_API_KEY:-dev-ingest-key}",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    String apiKey;

    private final Clock clock;
    private final IngestClient.Sleeper sleeper;

    public GeneratorCommand() {
        this(Clock.systemUTC(), Thread::sleep);
    }

    GeneratorCommand(Clock clock, IngestClient.Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new GeneratorCommand()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        validate();
        PrintWriter console = spec.commandLine().getOut();
        long runSeed = seed != null ? seed : new Random().nextLong();
        String runId = UUID.randomUUID().toString().substring(0, 8);
        console.printf("Run %s, seed %d (pass --seed %d to reproduce the same events)%n", runId, runSeed, runSeed);

        EventGenerator.Run run = new EventGenerator(runSeed, clock, span, configIds, waveRatio).generate(count, runId);
        printWaves(run.waves(), console);
        JsonMapper json = JsonMapper.builder().build();
        if (out != null) {
            List<Path> files = new EventFiles(json).write(batches(run.events()), out, format);
            console.printf("Wrote %d events to %d %s in %s%n", run.events().size(), files.size(),
                    files.size() == 1 ? "file" : "files", out.toAbsolutePath());
            return 0;
        }

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        boolean ready = !waitForHealth
                || new HealthCheck(http, target).waitUntilReady(Duration.ofSeconds(90), Duration.ofSeconds(1));
        if (!ready) {
            spec.commandLine().getErr().printf("%s did not become ready within 90s%n", target);
            return 1;
        }
        console.printf("Sending %d events to %s in batches of %d, %d at a time%n", run.events().size(), target,
                batchSize, concurrency);
        IngestClient client = new IngestClient(http, target, apiKey, json, sleeper, Duration.ofSeconds(1));
        SendSummary summary = new BatchSender(client, concurrency).send(batches(run.events()));
        summary.print(console);
        // Non-zero if any batch wasn't stored, so scripts and CI notice.
        return summary.failed() ? 1 : 0;
    }

    private void validate() {
        if (count < 1) {
            throw new ParameterException(spec.commandLine(), "--count must be at least 1");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new ParameterException(spec.commandLine(),
                    "--batch-size must be between 1 and 1000 (the API's limit)");
        }
        if (concurrency < 1) {
            throw new ParameterException(spec.commandLine(), "--concurrency must be at least 1");
        }
        if (waveRatio < 0 || waveRatio > 1) {
            throw new ParameterException(spec.commandLine(), "--wave-ratio must be between 0 and 1");
        }
        if (span.isZero() || span.isNegative()) {
            throw new ParameterException(spec.commandLine(), "--span must be positive");
        }
    }

    /** Where each attack happened, so its events can be found again through the stats and samples APIs. */
    private static void printWaves(List<AttackWaves.Wave> waves, PrintWriter console) {
        console.printf("%d attack waves:%n", waves.size());
        for (AttackWaves.Wave wave : waves) {
            String clients = wave.clients().size() == 1
                    ? wave.clients().getFirst()
                    : wave.clients().size() + " clients";
            console.printf("  %-19s %4d events  %s -> %s  %s on %s%n", wave.kind(), wave.events(), wave.start(),
                    wave.end(), clients, wave.path());
        }
    }

    private List<List<GeneratedEvent>> batches(List<GeneratedEvent> events) {
        List<List<GeneratedEvent>> batches = new ArrayList<>();
        for (int from = 0; from < events.size(); from += batchSize) {
            batches.add(events.subList(from, Math.min(from + batchSize, events.size())));
        }
        return batches;
    }

    /** Reads durations such as {@code 30s}, {@code 60m}, {@code 2h} or {@code 1d}. */
    static final class DurationConverter implements CommandLine.ITypeConverter<Duration> {

        private static final Pattern FORMAT = Pattern.compile("(\\d+)([smhd])");

        @Override
        public Duration convert(String value) {
            Matcher matcher = FORMAT.matcher(value.trim());
            if (!matcher.matches()) {
                throw new CommandLine.TypeConversionException(
                        "'" + value + "' is not a duration such as 30s, 60m, 2h or 1d");
            }
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            };
        }
    }
}
