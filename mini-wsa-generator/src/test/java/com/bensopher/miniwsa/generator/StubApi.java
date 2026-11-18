package com.bensopher.miniwsa.generator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for the ingest API on a random local port. It answers with scripted responses in order, then with
 * {@code 201 {"duplicates": 0}}, and records what it received.
 */
final class StubApi implements AutoCloseable {

    record Reply(int status, String body, String retryAfter) {

        static Reply created(int duplicates) {
            return new Reply(201, "{\"duplicates\": " + duplicates + "}", null);
        }

        static Reply unavailable(String retryAfter) {
            return new Reply(503, "{}", retryAfter);
        }
    }

    final Queue<Reply> script = new ConcurrentLinkedQueue<>();
    final Queue<String> bodies = new ConcurrentLinkedQueue<>();
    final Queue<String> apiKeys = new ConcurrentLinkedQueue<>();
    final AtomicInteger inFlight = new AtomicInteger();
    final AtomicInteger maxInFlight = new AtomicInteger();
    private final HttpServer server;

    StubApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/events/ingest", this::ingest);
        server.createContext("/actuator/health/readiness", exchange -> reply(exchange, new Reply(200, "{}", null)));
        server.start();
    }

    StubApi then(Reply... replies) {
        script.addAll(List.of(replies));
        return this;
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void ingest(HttpExchange exchange) throws IOException {
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-API-Key")));
            Thread.sleep(20); // long enough for concurrent requests to overlap
            Reply reply = script.poll();
            reply(exchange, reply != null ? reply : Reply.created(0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static void reply(HttpExchange exchange, Reply reply) throws IOException {
        if (reply.retryAfter() != null) {
            exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
