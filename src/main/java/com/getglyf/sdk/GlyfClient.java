package com.getglyf.sdk;

import com.getglyf.sdk.internal.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Official Java client for the GLYF bank-transaction enrichment API.
 *
 * <pre>{@code
 * GlyfClient glyf = GlyfClient.builder()
 *         .apiKey(System.getenv("GLYF_API_KEY"))
 *         .build();
 *
 * EnrichmentResult r = glyf.enrich(
 *         EnrichmentRequest.of("CARTE 14/02 NETFLIX.COM CB*0000", "FR"));
 *
 * r.merchantName();   // "Netflix"
 * r.category();       // "Streaming"
 * r.confidence();     // 0.97
 * }</pre>
 *
 * <p>Get a free API key (500 enrichments/day, no credit card) at
 * <a href="https://getglyf.com/access">getglyf.com/access</a>.</p>
 *
 * <p>Thread-safe; build one instance and share it.</p>
 */
public final class GlyfClient {

    /** SDK version, sent as User-Agent. */
    public static final String VERSION = "0.1.0";

    private static final String DEFAULT_BASE_URL = "https://api.getglyf.com";
    private static final int MAX_BATCH_SIZE = 1000;

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final int batchSize;

    private GlyfClient(Builder b) {
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey;
        this.requestTimeout = b.requestTimeout;
        this.maxRetries = b.maxRetries;
        this.batchSize = b.batchSize;
        this.http = HttpClient.newBuilder()
                .connectTimeout(b.connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Enriches a single transaction label.
     *
     * @throws GlyfApiException     when the API answers with a non-2xx status
     * @throws GlyfNetworkException when no response could be obtained after retries
     */
    public EnrichmentResult enrich(EnrichmentRequest request) {
        Objects.requireNonNull(request, "request");
        Object response = send("/api/v1/enrich", Json.write(request.toJsonMap()));
        if (!(response instanceof Map)) {
            throw new GlyfApiException(200, "UNEXPECTED_RESPONSE", "Expected a JSON object response", null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) response;
        return EnrichmentResult.fromJson(map);
    }

    /**
     * Enriches a list of labels. Lists larger than the API batch limit (1000)
     * are split automatically; results come back in input order.
     *
     * @throws GlyfApiException     when the API answers with a non-2xx status
     * @throws GlyfNetworkException when no response could be obtained after retries
     */
    public List<EnrichmentResult> enrichBatch(List<EnrichmentRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        List<EnrichmentResult> results = new ArrayList<>(requests.size());
        for (int from = 0; from < requests.size(); from += batchSize) {
            List<EnrichmentRequest> chunk = requests.subList(from, Math.min(from + batchSize, requests.size()));
            List<Map<String, Object>> body = chunk.stream().map(EnrichmentRequest::toJsonMap).toList();
            Object response = send("/api/v1/enrich/batch", Json.write(body));
            if (!(response instanceof List<?> list)) {
                throw new GlyfApiException(200, "UNEXPECTED_RESPONSE", "Expected a JSON array response", null);
            }
            for (Object item : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                results.add(EnrichmentResult.fromJson(map));
            }
        }
        return results;
    }

    // ── Transport ───────────────────────────────────────────────────────

    private Object send(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("X-Api-Key", apiKey)
                .header("User-Agent", "glyf-sdk-java/" + VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        IOException lastNetworkError = null;
        GlyfApiException lastRetryableApiError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                backoff(attempt, lastRetryableApiError);
            }
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return Json.parse(response.body());
                }
                GlyfApiException apiError = toApiException(status, response);
                if (isRetryable(status) && attempt < maxRetries) {
                    lastRetryableApiError = apiError;
                    continue;
                }
                throw apiError;
            } catch (IOException e) {
                lastNetworkError = e;
                lastRetryableApiError = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GlyfNetworkException("Request interrupted", e);
            }
        }
        throw new GlyfNetworkException(
                "No response from " + baseUrl + path + " after " + (maxRetries + 1) + " attempt(s)",
                lastNetworkError);
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static GlyfApiException toApiException(int status, HttpResponse<String> response) {
        String code = null;
        String message = null;
        Integer retryAfter = null;
        try {
            if (Json.parse(response.body()) instanceof Map<?, ?> error) {
                Object c = error.get("error");
                Object m = error.get("message");
                Object r = error.get("retryAfterSeconds");
                code = c == null ? null : String.valueOf(c);
                message = m == null ? null : String.valueOf(m);
                retryAfter = r instanceof Number n ? n.intValue() : null;
            }
        } catch (RuntimeException ignored) {
            // Non-JSON error body (proxy page, HTML…) — fall through to defaults.
        }
        if (retryAfter == null) {
            retryAfter = response.headers().firstValue("Retry-After")
                    .map(GlyfClient::parseRetryAfter)
                    .orElse(null);
        }
        if (message == null) {
            message = "GLYF API error (HTTP " + status + ")";
        }
        return new GlyfApiException(status, code, message, retryAfter);
    }

    private static Integer parseRetryAfter(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void backoff(int attempt, GlyfApiException retryableError) {
        long millis;
        if (retryableError != null && retryableError.retryAfterSeconds() != null) {
            millis = retryableError.retryAfterSeconds() * 1000L;
        } else {
            millis = Math.min(250L << (attempt - 1), 4_000L);
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GlyfNetworkException("Interrupted while backing off", e);
        }
    }

    // ── Builder ─────────────────────────────────────────────────────────

    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private int maxRetries = 3;
        private int batchSize = MAX_BATCH_SIZE;

        /** Your GLYF API key — get one free at <a href="https://getglyf.com/access">getglyf.com/access</a>. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Override the API origin (tests, future regions). Default: {@code https://api.getglyf.com}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** Retries on 429/502/503/504 and network errors. Default: 3. Set 0 to disable. */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /** Chunk size for {@code enrichBatch}. Default and maximum: 1000. */
        public Builder batchSize(int batchSize) {
            if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
            }
            this.batchSize = batchSize;
            return this;
        }

        public GlyfClient build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "apiKey is required — get a free key at https://getglyf.com/access");
            }
            return new GlyfClient(this);
        }
    }
}
