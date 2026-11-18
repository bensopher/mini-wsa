package com.bensopher.miniwsa.generator;

import com.bensopher.miniwsa.generator.GeneratedEvent.Geo;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** The vocabulary of generated events: rules, paths, user agents, places and addresses. */
final class Catalog {

    private Catalog() {
    }

    record RuleInfo(String id, String name, String message) {
    }

    static final Map<String, RuleInfo> RULES = Map.of(
            "INJECTION", new RuleInfo("950001", "SQL_INJECTION", "SQL Injection Attack Detected"),
            "XSS", new RuleInfo("941100", "XSS_ATTACK", "Cross-Site Scripting Attack Detected"),
            "PROTOCOL_VIOLATION", new RuleInfo("920100", "PROTOCOL_VIOLATION", "Invalid HTTP Request Line"),
            "DATA_LEAKAGE", new RuleInfo("951100", "DATA_LEAKAGE", "Sensitive Data in Response"),
            "BOT", new RuleInfo("990012", "BOT_DETECTED", "Automated Client Detected"),
            "DOS", new RuleInfo("981000", "DOS_PATTERN", "Denial of Service Pattern Detected"),
            "RATE_LIMIT", new RuleInfo("999001", "RATE_LIMIT_EXCEEDED", "Request Rate Limit Exceeded"));

    /** Background traffic is mostly bots and malformed requests, as on a real edge. */
    static final Weighted<String> BACKGROUND_CATEGORIES = new Weighted<String>()
            .add(30, "BOT").add(25, "PROTOCOL_VIOLATION").add(12, "INJECTION").add(10, "XSS")
            .add(10, "RATE_LIMIT").add(8, "DOS").add(5, "DATA_LEAKAGE");

    /** Severity depends on the kind of attack. */
    static final Map<String, Weighted<String>> SEVERITY_BY_CATEGORY = Map.of(
            "INJECTION", new Weighted<String>().add(60, "CRITICAL").add(35, "HIGH").add(5, "MEDIUM"),
            "XSS", new Weighted<String>().add(20, "CRITICAL").add(50, "HIGH").add(30, "MEDIUM"),
            "PROTOCOL_VIOLATION", new Weighted<String>().add(10, "HIGH").add(50, "MEDIUM").add(40, "LOW"),
            "DATA_LEAKAGE", new Weighted<String>().add(50, "CRITICAL").add(50, "HIGH"),
            "BOT", new Weighted<String>().add(10, "HIGH").add(50, "MEDIUM").add(40, "LOW"),
            "DOS", new Weighted<String>().add(30, "CRITICAL").add(60, "HIGH").add(10, "MEDIUM"),
            "RATE_LIMIT", new Weighted<String>().add(30, "MEDIUM").add(70, "LOW"));

    /** The action follows the severity: critical events are mostly blocked, low ones mostly just logged. */
    static final Map<String, Weighted<String>> ACTION_BY_SEVERITY = Map.of(
            "CRITICAL", new Weighted<String>().add(90, "DENY").add(10, "ALERT"),
            "HIGH", new Weighted<String>().add(60, "DENY").add(30, "ALERT").add(10, "MONITOR"),
            "MEDIUM", new Weighted<String>().add(20, "DENY").add(50, "ALERT").add(30, "MONITOR"),
            "LOW", new Weighted<String>().add(5, "DENY").add(15, "ALERT").add(80, "MONITOR"));

    static final Weighted<String> HOSTNAMES = new Weighted<String>()
            .add(50, "www.example.com").add(30, "api.example.com").add(20, "shop.example.org");

    static final Weighted<String> PATHS = new Weighted<String>()
            .add(20, "/").add(15, "/search?q=shoes").add(15, "/api/v1/users").add(10, "/api/v1/login")
            .add(5, "/admin").add(10, "/wp-login.php").add(10, "/checkout").add(15, "/static/app.js");

    /** Paths that carry the payload of the attack. */
    static final Map<String, Weighted<String>> PATHS_BY_CATEGORY = Map.of(
            "INJECTION", Weighted.of("/search?q=1%27+OR+%271%27%3D%271", "/api/v1/users?id=1+UNION+SELECT+password",
                    "/api/v1/login"),
            "XSS", Weighted.of("/search?q=%3Cscript%3Ealert(1)%3C%2Fscript%3E", "/comments?text=%3Cimg+onerror%3D1%3E"),
            "DATA_LEAKAGE", Weighted.of("/api/v1/users", "/api/v1/export", "/.env"));

    static final Weighted<String> BROWSERS = Weighted.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 Version/18.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0");

    static final String SQLMAP = "sqlmap/1.8.11#stable (https://sqlmap.org)";
    static final String CURL = "curl/8.11.0";
    static final String PYTHON_REQUESTS = "python-requests/2.32.3";
    /** Claims to be Googlebot; real crawlers are verified by reverse DNS, which these addresses would fail. */
    static final String FAKE_GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    static String userAgent(String category, Random random) {
        return switch (category) {
            case "INJECTION" -> new Weighted<String>().add(50, SQLMAP).add(20, CURL).add(30, BROWSERS.pick(random))
                    .pick(random);
            case "BOT" -> new Weighted<String>().add(50, FAKE_GOOGLEBOT).add(30, PYTHON_REQUESTS).add(20, CURL)
                    .pick(random);
            default -> new Weighted<String>().add(80, BROWSERS.pick(random)).add(10, CURL).add(10, PYTHON_REQUESTS)
                    .pick(random);
        };
    }

    static final Weighted<Geo> PLACES = new Weighted<Geo>()
            .add(20, new Geo("US", "Ashburn")).add(10, new Geo("US", "San Jose")).add(15, new Geo("CN", "Beijing"))
            .add(5, new Geo("CN", "Shanghai")).add(10, new Geo("RU", "Moscow")).add(8, new Geo("DE", "Frankfurt"))
            .add(8, new Geo("NL", "Amsterdam")).add(8, new Geo("BR", "Sao Paulo")).add(8, new Geo("IN", "Mumbai"))
            .add(8, new Geo("IL", "Tel Aviv"));

    /**
     * Where an address appears to come from. Derived from the address itself, so one attacker keeps the same
     * country across all its events; about 1 in 20 addresses has no location, as when GeoIP lookup fails.
     */
    static Geo placeOf(String ip) {
        Random byAddress = new Random(ip.hashCode());
        return byAddress.nextInt(20) == 0 ? null : PLACES.pick(byAddress);
    }

    private static final List<String> IPV4_DOCUMENTATION_PREFIXES = List.of("192.0.2.", "198.51.100.", "203.0.113.");

    /**
     * A random address from the ranges reserved for documentation (RFC 5737 for IPv4, RFC 3849 for IPv6), so the
     * generator never produces a real-world address. About 1 in 20 is IPv6.
     */
    static String documentationIp(Random random) {
        if (random.nextInt(20) == 0) {
            return "2001:db8::" + Integer.toHexString(1 + random.nextInt(0xfffe));
        }
        return IPV4_DOCUMENTATION_PREFIXES.get(random.nextInt(3)) + (1 + random.nextInt(254));
    }
}
