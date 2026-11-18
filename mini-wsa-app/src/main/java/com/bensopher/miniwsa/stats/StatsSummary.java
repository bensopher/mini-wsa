package com.bensopher.miniwsa.stats;

import com.bensopher.miniwsa.common.TimeRange;

import java.util.List;
import java.util.Map;

/**
 * {@code configId} is null when the summary covers every configuration, and is rendered as JSON null.
 * {@code byAction} always lists every action; {@code byCategory} only categories that have events, since an
 * average over zero events is undefined.
 */
public record StatsSummary(
        Long configId,
        TimeRange timeRange,
        long totalEvents,
        Map<String, CategoryStats> byCategory,
        Map<String, Long> byAction,
        List<AttackerStats> topAttackers,
        List<PathStats> topTargetedPaths) {

    /** {@code avgThreatScore} is null only if no event in the category has a score (rows from before scoring). */
    public record CategoryStats(long count, Double avgThreatScore) {
    }

    public record AttackerStats(String clientIp, long count, Double avgThreatScore) {
    }

    public record PathStats(String path, long count) {
    }
}
