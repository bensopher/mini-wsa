package com.bensopher.miniwsa.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Picks items with probability proportional to their weights. */
final class Weighted<T> {

    private final List<T> items = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private double total;

    Weighted<T> add(double weight, T item) {
        total += weight;
        items.add(item);
        cumulative.add(total);
        return this;
    }

    T pick(Random random) {
        double point = random.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            if (point < cumulative.get(i)) {
                return items.get(i);
            }
        }
        return items.getLast();
    }

    /** Equal weights. */
    @SafeVarargs
    static <T> Weighted<T> of(T... items) {
        Weighted<T> weighted = new Weighted<>();
        Arrays.stream(items).forEach(item -> weighted.add(1, item));
        return weighted;
    }

    /**
     * Zipf-like weights (1, 1/2, 1/3, ...): a few items are picked very often and most rarely, the way a few
     * clients produce most of the traffic.
     */
    static <T> Weighted<T> zipf(List<T> items) {
        Weighted<T> weighted = new Weighted<>();
        for (int i = 0; i < items.size(); i++) {
            weighted.add(1.0 / (i + 1), items.get(i));
        }
        return weighted;
    }
}
