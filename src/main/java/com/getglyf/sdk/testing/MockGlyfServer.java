package com.getglyf.sdk.testing;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * In-process fake GLYF API for unit tests — no network, no API key, no quota.
 *
 * <p>Lets you test your integration (and CI pipelines) without ever calling
 * {@code api.getglyf.com}. Queue responses, point the client at
 * {@link #baseUrl()}, then assert on {@link #requests()}.</p>
 *
 * <pre>{@code
 * try (MockGlyfServer server = MockGlyfServer.start()) {
 *     server.enqueue(200, MockGlyfServer.enrichmentJson("Netflix", "Streaming", 0.97));
 *
 *     GlyfClient glyf = GlyfClient.builder()
 *             .apiKey("test-key")
 *             .baseUrl(server.baseUrl())
 *             .build();
 *
 *     EnrichmentResult r = glyf.enrich(EnrichmentRequest.of("PRLV NETFLIX.COM", "FR"));
 *     assertEquals("Netflix", r.merchantName());
 * }
 * }</pre>
 */
public final class MockGlyfServer implements AutoCloseable {

    /** One HTTP exchange received by the mock, for assertions. */
    public record RecordedRequest(String method, String path, String apiKey, String body) {
    }

    private record QueuedResponse(int status, String body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Deque<QueuedResponse> queue = new ArrayDeque<>();
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

    private MockGlyfServer(HttpServer server) {
        this.server = server;
    }

    /** Starts the mock on a random local port. */
    public static MockGlyfServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MockGlyfServer mock = new MockGlyfServer(httpServer);
            httpServer.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                mock.requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Api-Key"),
                        body));

                QueuedResponse response;
                synchronized (mock.queue) {
                    response = mock.queue.poll();
                }
                if (response == null) {
                    response = new QueuedResponse(500, "{\"message\":\"MockGlyfServer: no response enqueued\"}", Map.of());
                }
                byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                response.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
                exchange.sendResponseHeaders(response.status(), payload.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            });
            httpServer.start();
            return mock;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start MockGlyfServer", e);
        }
    }

    /** Base URL to pass to {@code GlyfClient.builder().baseUrl(...)}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queues the next response (FIFO). */
    public MockGlyfServer enqueue(int status, String jsonBody) {
        return enqueue(status, jsonBody, Map.of());
    }

    /** Queues the next response with extra headers (e.g. {@code Retry-After}). */
    public MockGlyfServer enqueue(int status, String jsonBody, Map<String, String> headers) {
        synchronized (queue) {
            queue.add(new QueuedResponse(status, jsonBody, headers));
        }
        return this;
    }

    /** Every request received so far, in order. */
    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /** Convenience: a minimal valid enrichment response body. */
    public static String enrichmentJson(String merchantName, String category, double confidence) {
        return "{\"merchantName\":\"" + merchantName + "\","
                + "\"category\":\"" + category + "\","
                + "\"source\":\"DICTIONARY\","
                + "\"confidence\":" + confidence + "}";
    }

    /** Convenience: a standard API error body. */
    public static String errorJson(int status, String code, String message) {
        return "{\"status\":" + status + ",\"error\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
