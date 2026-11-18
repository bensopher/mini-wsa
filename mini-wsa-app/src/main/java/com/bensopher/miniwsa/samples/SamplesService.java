package com.bensopher.miniwsa.samples;

import com.bensopher.miniwsa.common.FilterColumn;
import com.bensopher.miniwsa.common.QueryProperties;
import com.bensopher.miniwsa.common.SqlFilter;
import com.bensopher.miniwsa.common.TimeRange;
import com.bensopher.miniwsa.common.error.FieldViolation;
import com.bensopher.miniwsa.common.error.RequestValidationException;
import com.bensopher.miniwsa.event.Action;
import com.bensopher.miniwsa.event.Category;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class SamplesService {

    private final SamplesRepository repository;
    private final QueryProperties query;
    private final Clock clock;

    public SamplesService(SamplesRepository repository, QueryProperties query, Clock clock) {
        this.repository = repository;
        this.query = query;
        this.clock = clock;
    }

    /**
     * Validates every parameter and reports all problems together, before the repository takes a connection.
     * Out-of-range limits are rejected rather than silently clamped, so a client never gets fewer items than it
     * asked for without knowing why.
     */
    public SamplesPage samples(Long configId, String from, String to, Category category, Action action,
            Integer limit, Integer offset) {
        QueryProperties.Samples paging = query.samples();
        int pageLimit = limit != null ? limit : paging.defaultLimit();
        int pageOffset = offset != null ? offset : 0;

        List<FieldViolation> violations = new ArrayList<>();
        TimeRange range = null;
        try {
            range = TimeRange.resolve(from, to, clock.instant(), query.defaultRange(), query.maxRange());
        } catch (RequestValidationException e) {
            violations.addAll(e.violations());
        }
        if (pageLimit < 1 || pageLimit > paging.maxLimit()) {
            violations.add(new FieldViolation(null, "limit", "must be between 1 and " + paging.maxLimit(), limit));
        }
        if (pageOffset < 0 || pageOffset > paging.maxOffset()) {
            violations.add(new FieldViolation(null, "offset", "must be between 0 and " + paging.maxOffset()
                    + "; narrow the time range or add filters to reach older events", offset));
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException("Invalid query parameters", violations);
        }

        SqlFilter filter = SqlFilter.timeRange(range)
                .eq(FilterColumn.CONFIG_ID, configId)
                .eq(FilterColumn.CATEGORY, category)
                .eq(FilterColumn.ACTION, action);
        return repository.load(filter, pageLimit, pageOffset);
    }
}
