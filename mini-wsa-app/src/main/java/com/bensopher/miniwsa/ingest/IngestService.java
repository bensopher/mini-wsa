package com.bensopher.miniwsa.ingest;

import com.bensopher.miniwsa.common.error.PayloadTooLargeException;
import com.bensopher.miniwsa.common.error.RequestValidationException;
import com.bensopher.miniwsa.enrichment.EnrichmentService;
import com.bensopher.miniwsa.event.EnrichedEvent;
import com.bensopher.miniwsa.event.SecurityEvent;
import com.bensopher.miniwsa.ingest.IngestResponse.IngestedEvent;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ingestion is synchronous: a 201 means the events are durably stored. There is no in-memory queue that
 * could lose acknowledged events on a crash; the durable buffer in front of ingestion would be a log such
 * as Kafka.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final EventPayloadParser parser;
    private final EnrichmentService enrichment;
    private final JdbcEventWriteRepository repository;
    private final IngestProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Counter duplicates;
    private final DistributionSummary batchSize;
    private final DistributionSummary threatScore;

    public IngestService(EventPayloadParser parser, EnrichmentService enrichment, JdbcEventWriteRepository repository,
            IngestProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.parser = parser;
        this.enrichment = enrichment;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.duplicates = meterRegistry.counter("miniwsa.events.duplicates");
        this.batchSize = DistributionSummary.builder("miniwsa.ingest.batch.size").baseUnit("events")
                .register(meterRegistry);
        this.threatScore = DistributionSummary.builder("miniwsa.events.threat_score").register(meterRegistry);
    }

    /** @param body one event object, or an array of events */
    @Timed(value = "miniwsa.ingest.duration", description = "Time to validate, enrich and store one ingest request")
    public IngestResponse ingest(JsonNode body) {
        long started = System.nanoTime();
        boolean batch = body.isArray();
        List<JsonNode> elements = elements(body);
        // One receivedAt for the whole request. Millisecond precision is plenty and reads well.
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        List<SecurityEvent> events = parser.parse(elements, batch, receivedAt);

        // A4: the same (eventId, timestamp) twice in one request is stored once; the first occurrence wins.
        Set<EventKey> seen = new HashSet<>();
        List<SecurityEvent> unique = new ArrayList<>();
        for (SecurityEvent event : events) {
            if (seen.add(EventKey.of(event))) {
                unique.add(event);
            }
        }
        List<EnrichedEvent> enriched = enrichment.enrich(unique, receivedAt);
        Set<EventKey> stored = repository.insert(enriched);

        Map<EventKey, EnrichedEvent> enrichedByKey = new HashMap<>();
        enriched.forEach(e -> enrichedByKey.put(EventKey.of(e.event()), e));
        Set<EventKey> reported = new HashSet<>();
        List<IngestedEvent> results = new ArrayList<>(events.size());
        for (SecurityEvent event : events) {
            EventKey key = EventKey.of(event);
            EnrichedEvent result = enrichedByKey.get(key);
            // Stored by this request, and the first time this request mentions it: not a duplicate.
            boolean duplicate = !(stored.contains(key) && reported.add(key));
            results.add(new IngestedEvent(event.eventId(), result.attackType(), result.threatScore(), duplicate));
        }
        IngestResponse response = new IngestResponse(events.size(), stored.size(), events.size() - stored.size(),
                receivedAt, results);
        recordMetrics(response, enriched, stored);
        // Counts only: events are attacker-controlled and may hold sensitive data, so payloads are never logged.
        log.info("Ingested request: received={} stored={} duplicates={} durationMs={}", response.received(),
                response.stored(), response.duplicates(), (System.nanoTime() - started) / 1_000_000);
        return response;
    }

    /** Stored events are counted by category, with their scores; duplicates only as a count. */
    private void recordMetrics(IngestResponse response, List<EnrichedEvent> enriched, Set<EventKey> stored) {
        batchSize.record(response.received());
        duplicates.increment(response.duplicates());
        for (EnrichedEvent event : enriched) {
            if (stored.contains(EventKey.of(event.event()))) {
                meterRegistry.counter("miniwsa.events.ingested", "category", event.event().rule().category().name())
                        .increment();
                threatScore.record(event.threatScore());
            }
        }
    }

    private List<JsonNode> elements(JsonNode body) {
        if (body.isObject()) {
            return List.of(body);
        }
        if (!body.isArray() || body.isEmpty()) {
            throw new RequestValidationException(
                    "The request body must be an event object or a non-empty array of events", List.of());
        }
        if (body.size() > properties.maxBatchSize()) {
            throw new PayloadTooLargeException(
                    "A batch may contain at most " + properties.maxBatchSize() + " events; got " + body.size());
        }
        List<JsonNode> elements = new ArrayList<>(body.size());
        body.forEach(elements::add);
        return elements;
    }
}
