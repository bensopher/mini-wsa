package com.bensopher.miniwsa.event;

/** Rule severity. The base score is the starting point of an event's threat score. */
public enum Severity {
    CRITICAL(40),
    HIGH(30),
    MEDIUM(20),
    LOW(10);

    private final int baseScore;

    Severity(int baseScore) {
        this.baseScore = baseScore;
    }

    public int baseScore() {
        return baseScore;
    }
}
