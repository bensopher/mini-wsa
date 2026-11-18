package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.IngestClient.BatchResult;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Sends batches with up to {@code concurrency} requests in flight, each on its own virtual thread. Batches are
 * started in chronological order, like a live feed; with several in flight they may finish slightly out of order,
 * which the API handles because the repeat-offender window is evaluated in event time.
 */
final class BatchSender {

    private final IngestClient client;
    private final int concurrency;

    BatchSender(IngestClient client, int concurrency) {
        this.client = client;
        this.concurrency = concurrency;
    }

    SendSummary send(List<List<GeneratedEvent>> batches) throws InterruptedException {
        Queue<BatchResult> results = new ConcurrentLinkedQueue<>();
        Semaphore inFlight = new Semaphore(concurrency);
        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<GeneratedEvent> batch : batches) {
                inFlight.acquire();
                executor.submit(() -> {
                    try {
                        results.add(client.send(batch));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inFlight.release();
                    }
                });
            }
        } // close() waits for every batch to finish
        return SendSummary.of(List.copyOf(results), Duration.ofNanos(System.nanoTime() - started));
    }
}
