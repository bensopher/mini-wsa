package com.bensopher.miniwsa.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The ingest API's field rules, restated independently of the app's code: every generated event must pass them, or
 * a seeding run would be rejected.
 */
final class IngestRules {

    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> CATEGORIES =
            Set.of("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT");
    private static final Set<String> ACTIONS = Set.of("DENY", "ALERT", "MONITOR");

    private IngestRules() {
    }

    static List<String> violations(GeneratedEvent e) {
        List<String> problems = new ArrayList<>();
        check(problems, e.eventId() != null && !e.eventId().isBlank() && e.eventId().length() <= 128, "eventId");
        check(problems, e.timestamp() != null, "timestamp");
        check(problems, e.configId() > 0, "configId");
        check(problems, e.clientIp() != null && isDocumentationAddress(e.clientIp()), "clientIp");
        check(problems, e.path() != null && e.path().startsWith("/") && e.path().length() <= 2048, "path");
        check(problems, e.method() != null && e.method().matches("[A-Z]{1,16}"), "method");
        check(problems, e.statusCode() == null || (e.statusCode() >= 100 && e.statusCode() <= 599), "statusCode");
        check(problems, e.userAgent() == null || e.userAgent().length() <= 1024, "userAgent");
        check(problems, e.rule() != null && e.rule().id() != null && !e.rule().id().isBlank(), "rule.id");
        check(problems, e.rule() != null && SEVERITIES.contains(e.rule().severity()), "rule.severity");
        check(problems, e.rule() != null && CATEGORIES.contains(e.rule().category()), "rule.category");
        check(problems, ACTIONS.contains(e.action()), "action");
        check(problems, e.geoLocation() == null || e.geoLocation().country().matches("[A-Z]{2}"), "geoLocation.country");
        check(problems, e.requestSize() == null || e.requestSize() >= 0, "requestSize");
        check(problems, e.responseSize() == null || e.responseSize() >= 0, "responseSize");
        return problems;
    }

    /** RFC 5737 (IPv4) and RFC 3849 (IPv6) documentation ranges only: never a real-world address. */
    static boolean isDocumentationAddress(String ip) {
        return ip.startsWith("192.0.2.") || ip.startsWith("198.51.100.") || ip.startsWith("203.0.113.")
                || ip.startsWith("2001:db8:");
    }

    private static void check(List<String> problems, boolean ok, String field) {
        if (!ok) {
            problems.add(field);
        }
    }
}
