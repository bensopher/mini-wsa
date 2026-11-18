package com.bensopher.miniwsa.common;

/**
 * The columns a read query may filter on. {@link SqlFilter} takes column names only from here, never from
 * request input, so a filter can't inject SQL.
 */
public enum FilterColumn {
    CONFIG_ID("config_id");

    private final String column;

    FilterColumn(String column) {
        this.column = column;
    }

    String column() {
        return column;
    }
}
