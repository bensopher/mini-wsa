package com.bensopher.miniwsa.stats;

import com.bensopher.miniwsa.common.FilterColumn;
import com.bensopher.miniwsa.common.QueryProperties;
import com.bensopher.miniwsa.common.SqlFilter;
import com.bensopher.miniwsa.common.TimeRange;
import com.bensopher.miniwsa.event.Action;
import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatsService {

    private final StatsRepository repository;
    private final QueryProperties query;
    private final Clock clock;

    public StatsService(StatsRepository repository, QueryProperties query, Clock clock) {
        this.repository = repository;
        this.query = query;
        this.clock = clock;
    }

    /** Validates the parameters before the repository opens a transaction, so a bad request costs no connection. */
    @Timed(value = "miniwsa.stats.duration", description = "Time to build one stats summary")
    public StatsSummary summary(Long configId, String from, String to) {
        TimeRange range = TimeRange.resolve(from, to, clock.instant(), query.defaultRange(), query.maxRange());
        StatsRepository.Sections sections =
                repository.load(SqlFilter.timeRange(range).eq(FilterColumn.CONFIG_ID, configId));

        // Every action is listed, with 0 when absent, so clients don't have to handle missing keys.
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (Action action : Action.values()) {
            byAction.put(action.name(), sections.byAction().getOrDefault(action.name(), 0L));
        }
        // Every event has exactly one category, so the category counts add up to the total: no fifth query.
        long total = sections.byCategory().values().stream().mapToLong(StatsSummary.CategoryStats::count).sum();
        return new StatsSummary(configId, range, total, sections.byCategory(), byAction,
                sections.topAttackers(), sections.topTargetedPaths());
    }
}
