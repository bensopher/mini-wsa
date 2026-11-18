package com.bensopher.miniwsa.samples;

import java.util.List;

/** One page of matching events, newest first; {@code total} counts every match, not just this page. */
public record SamplesPage(long total, int limit, int offset, List<SampleEvent> items) {
}
