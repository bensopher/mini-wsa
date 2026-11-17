package com.bensopher.miniwsa.event;

/** What the edge did with the request. Blocking actions add to the threat score. */
public enum Action {
    DENY(20),
    ALERT(10),
    MONITOR(0);

    private final int scoreBonus;

    Action(int scoreBonus) {
        this.scoreBonus = scoreBonus;
    }

    public int scoreBonus() {
        return scoreBonus;
    }
}
