package com.getglyf.sdk;

import com.getglyf.sdk.testing.MockGlyfServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyfClientTest {

    private MockGlyfServer server;

    @BeforeEach
    void setUp() {
        server = MockGlyfServer.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private GlyfClient client() {
        return client(b -> {
        });
    }

    private GlyfClient client(java.util.function.Consumer<GlyfClient.Builder> customizer) {
        GlyfClient.Builder builder = GlyfClient.builder()
                .apiKey("glyf_test_key")
                .baseUrl(server.baseUrl())
                .requestTimeout(Duration.ofSeconds(2));
        customizer.accept(builder);
        return builder.build();
    }

    // ── Nominal ─────────────────────────────────────────────────────────

    @Test
    void enrichMapsTheFullPublicContract() {
        server.enqueue(200, """
                {"merchantName":"Carrefour Market","operatorName":"Label'Vie",
                 "logoUrl":"https://api.getglyf.com/logos/carrefour.fr.png",
                 "category":"Grande distribution","refinedCategory":null,
                 "originalLabel":"PAIEMENT TPE CARREFOUR MARKET MA RABAT",
                 "cleanLabel":"Carrefour Market","complement":"Rabat",
                 "businessId":"002345678000091","businessIdType":"ICE",
                 "legalName":"LABEL VIE SA","address":"KM 3,5 ROUTE COTIERE","city":"Rabat",
                 "detectedCity":"Rabat","postalCode":"10000","country":"MA","nafCode":null,
                 "source":"DICTIONARY","confidence":0.96,"registryPending":false,
                 "display":{"merchantName":"Carrefour Market","overrideApplied":false}}""");

        EnrichmentResult r = client().enrich(
                EnrichmentRequest.of("PAIEMENT TPE CARREFOUR MARKET MA RABAT", "MA"));

        assertEquals("Carrefour Market", r.merchantName());
        assertEquals("Label'Vie", r.operatorName());
        assertEquals("Grande distribution", r.category());
        assertNull(r.refinedCategory());
        assertEquals("ICE", r.businessIdType());
        assertEquals("LABEL VIE SA", r.legalName());
        assertEquals("MA", r.country());
        assertEquals("DICTIONARY", r.source());
        assertEquals(0.96, r.confidence(), 1e-9);
        assertEquals(Boolean.FALSE, r.registryPending());
        assertEquals(Boolean.FALSE, r.display().get("overrideApplied"));

        MockGlyfServer.RecordedRequest sent = server.requests().get(0);
        assertEquals("POST", sent.method());
        assertEquals("/api/v1/enrich", sent.path());
        assertEquals("glyf_test_key", sent.apiKey());
        assertTrue(sent.body().contains("\"label\":\"PAIEMENT TPE CARREFOUR MARKET MA RABAT\""));
        assertTrue(sent.body().contains("\"country\":\"MA\""));
    }

    @Test
    void optionalRequestFieldsAreOmittedWhenNull() {
        server.enqueue(200, MockGlyfServer.enrichmentJson("Netflix", "Streaming", 0.97));
        client().enrich(EnrichmentRequest.of("PRLV NETFLIX.COM"));
        String body = server.requests().get(0).body();
        assertTrue(body.contains("\"label\""));
        assertEquals(-1, body.indexOf("country"));
        assertEquals(-1, body.indexOf("businessId"));
        assertEquals(-1, body.indexOf("includeExplanation"));
    }

    @Test
    void unknownResponseFieldsAreIgnoredButAvailableInRaw() {
        server.enqueue(200, "{\"merchantName\":\"Netflix\",\"someFutureField\":\"x\"}");
        EnrichmentResult r = client().enrich(EnrichmentRequest.of("PRLV NETFLIX.COM", "FR"));
        assertEquals("Netflix", r.merchantName());
        assertEquals("x", r.raw().get("someFutureField"));
    }

    // ── Errors ──────────────────────────────────────────────────────────

    @Test
    void apiErrorIsTypedWithStatusCodeAndMessage() {
        server.enqueue(401, MockGlyfServer.errorJson(401, "INVALID_API_KEY", "Clé API invalide."));
        GlyfApiException e = assertThrows(GlyfApiException.class,
                () -> client().enrich(EnrichmentRequest.of("X", "FR")));
        assertEquals(401, e.status());
        assertEquals("INVALID_API_KEY", e.code());
        assertEquals("Clé API invalide.", e.getMessage());
    }

    @Test
    void nonJsonErrorBodyStillProducesTypedException() {
        server.enqueue(403, "<html>Blocked by corporate proxy</html>");
        GlyfApiException e = assertThrows(GlyfApiException.class,
                () -> client().enrich(EnrichmentRequest.of("X", "FR")));
        assertEquals(403, e.status());
        assertNull(e.code());
    }

    @Test
    void clientErrorsAreNotRetried() {
        server.enqueue(400, MockGlyfServer.errorJson(400, "VALIDATION", "label obligatoire"));
        assertThrows(GlyfApiException.class, () -> client().enrich(EnrichmentRequest.of("X", "FR")));
        assertEquals(1, server.requests().size());
    }

    // ── Retries ─────────────────────────────────────────────────────────

    @Test
    void retriesOn429ThenSucceeds() {
        server.enqueue(429, MockGlyfServer.errorJson(429, "RATE_LIMIT", "Trop de requêtes"),
                java.util.Map.of("Retry-After", "0"));
        server.enqueue(200, MockGlyfServer.enrichmentJson("Netflix", "Streaming", 0.97));

        EnrichmentResult r = client().enrich(EnrichmentRequest.of("PRLV NETFLIX.COM", "FR"));

        assertEquals("Netflix", r.merchantName());
        assertEquals(2, server.requests().size());
    }

    @Test
    void retriesOn503ThenSucceeds() {
        server.enqueue(503, MockGlyfServer.errorJson(503, "UNAVAILABLE", "Maintenance"));
        server.enqueue(200, MockGlyfServer.enrichmentJson("EDF", "Énergie", 0.92));
        EnrichmentResult r = client(b -> b.maxRetries(1)).enrich(EnrichmentRequest.of("PRLV EDF", "FR"));
        assertEquals("EDF", r.merchantName());
        assertEquals(2, server.requests().size());
    }

    @Test
    void throwsApiExceptionAfterRetriesExhausted() {
        server.enqueue(503, MockGlyfServer.errorJson(503, "UNAVAILABLE", "Maintenance"),
                java.util.Map.of("Retry-After", "0"));
        server.enqueue(503, MockGlyfServer.errorJson(503, "UNAVAILABLE", "Maintenance"),
                java.util.Map.of("Retry-After", "0"));
        GlyfApiException e = assertThrows(GlyfApiException.class,
                () -> client(b -> b.maxRetries(1)).enrich(EnrichmentRequest.of("X", "FR")));
        assertEquals(503, e.status());
        assertEquals(2, server.requests().size());
    }

    @Test
    void unreachableServerThrowsNetworkException() {
        GlyfClient unreachable = GlyfClient.builder()
                .apiKey("k")
                .baseUrl("http://127.0.0.1:1") // port réservé, connexion refusée
                .maxRetries(0)
                .requestTimeout(Duration.ofMillis(500))
                .build();
        assertThrows(GlyfNetworkException.class,
                () -> unreachable.enrich(EnrichmentRequest.of("X", "FR")));
    }

    // ── Batch ───────────────────────────────────────────────────────────

    @Test
    void batchSplitsIntoChunksAndPreservesOrder() {
        server.enqueue(200, "[" + MockGlyfServer.enrichmentJson("A", "Cat", 0.9)
                + "," + MockGlyfServer.enrichmentJson("B", "Cat", 0.9) + "]");
        server.enqueue(200, "[" + MockGlyfServer.enrichmentJson("C", "Cat", 0.9) + "]");

        List<EnrichmentRequest> requests = IntStream.range(0, 3)
                .mapToObj(i -> EnrichmentRequest.of("LABEL " + i, "FR"))
                .toList();

        List<EnrichmentResult> results = client(b -> b.batchSize(2)).enrichBatch(requests);

        assertEquals(List.of("A", "B", "C"), results.stream().map(EnrichmentResult::merchantName).toList());
        assertEquals(2, server.requests().size());
        assertEquals("/api/v1/enrich/batch", server.requests().get(0).path());
        assertTrue(server.requests().get(0).body().contains("LABEL 1"));
        assertTrue(server.requests().get(1).body().contains("LABEL 2"));
    }

    // ── Builder guards ──────────────────────────────────────────────────

    @Test
    void builderRequiresApiKey() {
        assertThrows(IllegalStateException.class, () -> GlyfClient.builder().build());
    }

    @Test
    void builderRejectsOversizedBatch() {
        assertThrows(IllegalArgumentException.class,
                () -> GlyfClient.builder().apiKey("k").batchSize(1001));
    }

    @Test
    void requestRequiresNonBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> EnrichmentRequest.of("  ", "FR"));
        assertThrows(NullPointerException.class, () -> EnrichmentRequest.of(null, "FR"));
    }
}
