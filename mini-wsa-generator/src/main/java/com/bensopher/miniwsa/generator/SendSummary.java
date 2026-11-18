package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.IngestClient.BatchResult;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** What a sending run achieved, printed at the end and used for the performance numbers in the README. */
record SendSummary(int batches, int eventsStored, int duplicates, int retries, Map<String, Long> statuses,
        Duration elapsed, Duration p50, Duration p95) {

    static SendSummary of(List<BatchResult> results, Duration elapsed) {
        List<BatchResult> accepted = results.stream().filter(r -> r.status().equals("201")).toList();
        List<Duration> latencies = results.stream().map(BatchResult::latency).sorted().toList();
        return new SendSummary(
                results.size(),
                accepted.stream().mapToInt(r -> r.events() - r.duplicates()).sum(),
                accepted.stream().mapToInt(BatchResult::duplicates).sum(),
                results.stream().mapToInt(BatchResult::retries).sum(),
                results.stream().collect(
                        Collectors.groupingBy(BatchResult::status, TreeMap::new, Collectors.counting())),
                elapsed,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95));
    }

    /** True if any batch ended with something other than 201: those events were not stored. */
    boolean failed() {
        return statuses.keySet().stream().anyMatch(status -> !status.equals("201"));
    }

    /** Events accepted per second, duplicates included: the rate the API processed them at. */
    double eventsPerSecond() {
        double seconds = elapsed.toNanos() / 1e9;
        return seconds == 0 ? 0 : (eventsStored + duplicates) / seconds;
    }

    void print(PrintWriter out) {
        out.printf("Batches:        %d (status: %s)%n", batches, statuses);
        out.printf("Events stored:  %d (duplicates reported: %d)%n", eventsStored, duplicates);
        out.printf("Retries on 503: %d%n", retries);
        out.printf("Elapsed:        %.2f s, %.0f events/s%n", elapsed.toNanos() / 1e9, eventsPerSecond());
        out.printf("Batch latency:  p50 %d ms, p95 %d ms%n", p50.toMillis(), p95.toMillis());
    }

    /** Nearest-rank percentile. */
    private static Duration percentile(List<Duration> sorted, double p) {
        if (sorted.isEmpty()) {
            return Duration.ZERO;
        }
        int rank = (int) Math.ceil(p * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
