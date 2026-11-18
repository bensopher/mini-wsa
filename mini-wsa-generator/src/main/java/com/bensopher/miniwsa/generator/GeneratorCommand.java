package com.bensopher.miniwsa.generator;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
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
        description = "Generates realistic security events and writes them to files.")
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

    @Option(names = "--out", required = true, description = "Directory to write event files to.")
    Path out;

    @Option(names = "--format", description = "File format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            defaultValue = "array")
    EventFiles.Format format;

    @Option(names = "--batch-size", description = "Events per file (default: ${DEFAULT-VALUE}, at most 1000).",
            defaultValue = "500")
    int batchSize;

    private final Clock clock;

    public GeneratorCommand() {
        this(Clock.systemUTC());
    }

    GeneratorCommand(Clock clock) {
        this.clock = clock;
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

        List<GeneratedEvent> events = new EventGenerator(runSeed, clock, span, configIds).generate(count, runId);
        List<Path> files = new EventFiles(JsonMapper.builder().build()).write(batches(events), out, format);
        console.printf("Wrote %d events to %d files in %s%n", events.size(), files.size(), out.toAbsolutePath());
        return 0;
    }

    private void validate() {
        if (count < 1) {
            throw new ParameterException(spec.commandLine(), "--count must be at least 1");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new ParameterException(spec.commandLine(), "--batch-size must be between 1 and 1000 (the API's limit)");
        }
        if (span.isZero() || span.isNegative()) {
            throw new ParameterException(spec.commandLine(), "--span must be positive");
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
