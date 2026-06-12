# GLYF Java SDK

Official Java client for the [GLYF](https://getglyf.com) **bank-transaction enrichment API** —
turn raw bank statement labels like `CARTE 14/02 NETFLIX.COM CB*0000` into structured
merchant data: clean name, logo, category, city and legal/registry information
(SIRET, ICE, KBO, EU VAT).

**Zero runtime dependencies.** The SDK uses only the JDK (`java.net.http`) — an empty
dependency tree means nothing for your security scanner to flag.

- Coverage: European Union 🇫🇷 🇧🇪 🇪🇸 🇮🇹 🇳🇱 and North Africa 🇲🇦 🇹🇳 🇩🇿 🇪🇬
- Free tier: **500 enrichments/day, no credit card** → [get your API key](https://getglyf.com/access)
- API docs: [getglyf.com/docs](https://getglyf.com/docs) · OpenAPI: [Swagger UI](https://api.getglyf.com/swagger-ui.html)

## Installation

Maven:

```xml
<dependency>
    <groupId>com.getglyf</groupId>
    <artifactId>glyf-sdk</artifactId>
    <version>0.1.2</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.getglyf:glyf-sdk:0.1.0")
```

Requires **Java 17+**.

## Quickstart

1. Get a free API key at [getglyf.com/access](https://getglyf.com/access) (instant, no credit card).
2. Enrich your first label:

```java
import com.getglyf.sdk.EnrichmentRequest;
import com.getglyf.sdk.EnrichmentResult;
import com.getglyf.sdk.GlyfClient;

GlyfClient glyf = GlyfClient.builder()
        .apiKey(System.getenv("GLYF_API_KEY"))
        .build();

EnrichmentResult r = glyf.enrich(
        EnrichmentRequest.of("CARTE 14/02/26 NETFLIX.COM CB*0000", "FR"));

System.out.println(r.merchantName());  // Netflix
System.out.println(r.category());      // Streaming
System.out.println(r.confidence());    // 0.97
System.out.println(r.logoUrl());       // https://api.getglyf.com/logos/netflix.com.png
```

## Usage

### With a business identifier (registry resolution)

When you know the company identifier, GLYF resolves official legal data
(legal name, address, NAF code…) from the national registry:

```java
EnrichmentResult r = glyf.enrich(EnrichmentRequest.builder()
        .label("CARTE MONOPRIX BOULOGNE 55208329700382")
        .country("FR")
        .businessId("55208329700382")
        .businessIdType("SIRET")          // SIRET (FR), ICE (MA), KBO (BE), VAT (EU)
        .build());

r.legalName();   // MONOPRIX EXPLOITATION
r.nafCode();     // 47.11D
r.address();
```

### Batch

Lists of any size — the SDK splits into API-sized chunks (≤ 1000) automatically
and preserves input order:

```java
List<EnrichmentResult> results = glyf.enrichBatch(List.of(
        EnrichmentRequest.of("PRLV NETFLIX.COM NL AMSTERDAM", "FR"),
        EnrichmentRequest.of("PAIEMENT TPE MARJANE CASABLANCA", "MA"),
        EnrichmentRequest.of("PAIEMENT AVEC CARTE COLRUYT HALLE", "BE")));
```

### Error handling

```java
try {
    glyf.enrich(EnrichmentRequest.of(label, "FR"));
} catch (GlyfApiException e) {
    // The API answered with an error
    e.status();             // 401, 429, 500…
    e.code();               // "INVALID_API_KEY", "RATE_LIMIT_EXCEEDED"…
    e.retryAfterSeconds();  // suggested wait on 429
} catch (GlyfNetworkException e) {
    // No response at all (DNS, timeout…) after the configured retries
}
```

Transient failures (`429`, `502`, `503`, `504`, network errors) are retried
automatically with exponential backoff, honouring `Retry-After`. Configure or
disable via the builder:

```java
GlyfClient glyf = GlyfClient.builder()
        .apiKey(key)
        .maxRetries(5)                          // default 3, 0 disables
        .requestTimeout(Duration.ofSeconds(15)) // default 10 s
        .build();
```

### Testing your integration — no network, no key

The SDK ships an in-process fake API, `MockGlyfServer`, so your unit tests and
CI never call the real endpoint:

```java
import com.getglyf.sdk.testing.MockGlyfServer;

try (MockGlyfServer server = MockGlyfServer.start()) {
    server.enqueue(200, MockGlyfServer.enrichmentJson("Netflix", "Streaming", 0.97));

    GlyfClient glyf = GlyfClient.builder()
            .apiKey("test-key")
            .baseUrl(server.baseUrl())
            .build();

    EnrichmentResult r = glyf.enrich(EnrichmentRequest.of("PRLV NETFLIX.COM", "FR"));
    assertEquals("Netflix", r.merchantName());
    assertEquals("/api/v1/enrich", server.requests().get(0).path());
}
```

## `EnrichmentResult` fields

| Accessor | Description |
|---|---|
| `merchantName()` | Customer-facing merchant name (`"Carrefour Market"`) |
| `operatorName()` | Local operator/franchise when different from the brand |
| `category()` / `refinedCategory()` | Human-readable category (`"Streaming"`) |
| `cleanLabel()` / `originalLabel()` / `complement()` | Cleaned label, raw input, extracted context |
| `logoUrl()` / `merchantLogoUrl()` / `operatorLogoUrl()` / `legalLogoUrl()` | HD logo URLs |
| `legalName()` / `address()` / `city()` / `postalCode()` / `nafCode()` | Official registry data |
| `businessId()` / `businessIdType()` | Company identifier (SIRET, ICE, KBO, VAT…) |
| `detectedCity()` / `country()` | City detected in the label, business country |
| `source()` | Resolution source: `DICTIONARY`, `SIRENE`, `COMPANY_REGISTRY`, `LLM`, `NONE`… |
| `confidence()` | Score in `[0, 1]` |
| `registryPending()` / `registryPendingReason()` | Deferred registry lookups |
| `explanation()` / `tenantOverride()` / `display()` | Nested objects (maps) |
| `raw()` | The full parsed payload — future API fields are always reachable |

Forward compatibility: unknown JSON fields never break deserialisation, and
enum-like values are plain `String`s — your deployed app keeps working when the
API grows.

## Quotas & plans

The free Starter key includes 500 enrichments/day. `429` responses are the
normal signal that a quota is reached — the SDK retries with backoff, and
`GlyfApiException.retryAfterSeconds()` tells you how long to wait. Higher
volumes, batch throughput and SLA: see [pricing](https://getglyf.com/access).

## Support

- Issues: [GitHub issues](https://github.com/simoTiba/glyf-sdk-java/issues)
- E-mail: [contact@getglyf.com](mailto:contact@getglyf.com)
- Security reports: see [SECURITY.md](SECURITY.md)

## License

[Apache-2.0](LICENSE)

---

### 🇫🇷 En bref

GLYF enrichit les libellés bancaires bruts en données marchand structurées
(nom, logo, catégorie, ville, données légales SIRET/ICE/KBO/TVA) via une API REST.
Ce SDK Java officiel — **zéro dépendance** — couvre `enrich` et `enrichBatch`,
avec retries automatiques et un faux serveur (`MockGlyfServer`) pour vos tests.
Clé gratuite (500 enrichissements/jour, sans CB) sur
[getglyf.com/access](https://getglyf.com/access).
