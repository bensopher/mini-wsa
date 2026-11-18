package com.bensopher.miniwsa.enrichment;

import com.bensopher.miniwsa.event.EnrichedEvent;
import com.bensopher.miniwsa.event.SecurityEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/** Adds the attack type and threat score to each event of a request. */
@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final AttackTypeClassifier classifier;
    private final ThreatScoreCalculator calculator;
    private final RepeatOffenderTracker tracker;
    private final Counter trackerUnavailable;
    /** Whether the last call reached the tracker; outages are logged when this flips, not on every request. */
    private final AtomicBoolean trackerAvailable = new AtomicBoolean(true);

    public EnrichmentService(AttackTypeClassifier classifier, ThreatScoreCalculator calculator,
            RepeatOffenderTracker tracker, MeterRegistry meterRegistry) {
        this.classifier = classifier;
        this.calculator = calculator;
        this.tracker = tracker;
        this.trackerUnavailable = meterRegistry.counter("miniwsa.enrichment.repeat_offender.unavailable");
    }

    /** Returns the enriched events in the same order as {@code events}. */
    public List<EnrichedEvent> enrich(List<SecurityEvent> events, Instant receivedAt) {
        // The tracker counts in event-time order (A5), so earlier events of this request count toward later
        // ones (A6) even when the request lists them out of order. The sort is stable: ties keep request order.
        List<Integer> timeOrder = IntStream.range(0, events.size()).boxed()
                .sorted(Comparator.comparing(i -> events.get(i).timestamp()))
                .toList();
        List<Integer> priorsInTimeOrder = priorEvents(timeOrder.stream().map(events::get).toList());

        int[] priors = new int[events.size()];
        for (int k = 0; k < timeOrder.size(); k++) {
            priors[timeOrder.get(k)] = priorsInTimeOrder.get(k);
        }
        return IntStream.range(0, events.size())
                .mapToObj(i -> {
                    SecurityEvent event = events.get(i);
                    return new EnrichedEvent(event, classifier.classify(event.rule().category()),
                            calculator.calculate(new ScoringContext(event, priors[i])), receivedAt);
                })
                .toList();
    }

    private List<Integer> priorEvents(List<SecurityEvent> eventsInTimeOrder) {
        try {
            List<Integer> priors = tracker.recordAndCount(eventsInTimeOrder);
            if (trackerAvailable.compareAndSet(false, true)) {
                log.info("Repeat-offender state is available again; the repeat bonus applies to new events");
            }
            return priors;
        } catch (DataAccessException e) {
            // Fail open: losing events is worse than scoring some of them without the repeat bonus for a while.
            // The cost is bounded by the Redis timeouts (500ms).
            trackerUnavailable.increment();
            // One WARN per outage, not per request: during an attack, a line per request would flood the logs
            // and bury everything else. The metric counts every affected request; alert on it.
            if (trackerAvailable.compareAndSet(true, false)) {
                log.warn("Repeat-offender state unavailable; scoring events without the repeat bonus until it "
                        + "recovers: {}", e.toString());
            } else {
                log.debug("Repeat-offender state still unavailable: {}", e.toString());
            }
            return Collections.nCopies(eventsInTimeOrder.size(), 0);
        }
    }
}
