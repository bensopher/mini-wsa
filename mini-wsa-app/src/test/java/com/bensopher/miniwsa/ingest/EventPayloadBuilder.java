package com.bensopher.miniwsa.ingest;

/** A valid event payload (the assignment's sample event) that tests change one field at a time. */
class EventPayloadBuilder {

    String eventId = "evt-00132";
    String timestamp = "2026-05-20T14:32:10Z";
    Long configId = 14227L;
    String policyId = "pol_web1";
    String clientIp = "203.0.113.42";
    String hostname = "www.example.com";
    String path = "/api/v1/login";
    String method = "POST";
    Integer statusCode = 403;
    String userAgent = "Mozilla/5.0";
    String ruleId = "950001";
    String ruleName = "SQL_INJECTION";
    String ruleMessage = "SQL Injection Attack Detected";
    String severity = "CRITICAL";
    String category = "INJECTION";
    boolean withRule = true;
    String action = "DENY";
    String country = "CN";
    String city = "Beijing";
    Long requestSize = 1024L;
    Long responseSize = 256L;

    static EventPayloadBuilder anEvent() {
        return new EventPayloadBuilder();
    }

    EventPayload build() {
        RulePayload rule = withRule ? new RulePayload(ruleId, ruleName, ruleMessage, severity, category) : null;
        return new EventPayload(eventId, timestamp, configId, policyId, clientIp, hostname, path, method,
                statusCode, userAgent, rule, action, new GeoPayload(country, city), requestSize, responseSize);
    }
}
