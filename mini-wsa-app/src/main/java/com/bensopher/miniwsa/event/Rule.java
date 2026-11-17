package com.bensopher.miniwsa.event;

public record Rule(String id, String name, String message, Severity severity, Category category) {
}
