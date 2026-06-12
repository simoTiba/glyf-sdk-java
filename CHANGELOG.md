# Changelog

## 0.1.3 — 2026-06-12

- **Corporate proxy support**: `GlyfClient.builder().proxy(host, port)` (or a full
  `ProxySelector`) routes SDK traffic through an HTTP(S) proxy — Java's HttpClient
  ignores `https_proxy` environment variables, which broke bank-workstation setups.
- New README section: *Corporate proxy & TLS interception*.

> 0.1.1 / 0.1.2 were build-infrastructure tags (JitPack JDK/wrapper); no API change.

## 0.1.0 — 2026-06-11

First public release.

- `GlyfClient` : `enrich` (POST `/api/v1/enrich`) and `enrichBatch`
  (POST `/api/v1/enrich/batch`, automatic chunking ≤ 1000, input order preserved)
- Typed errors: `GlyfApiException` (status, code, `retryAfterSeconds`) and
  `GlyfNetworkException`
- Automatic retries on 429/502/503/504 and network errors, exponential backoff,
  honours `Retry-After`
- `MockGlyfServer` test fixture — test your integration with no network and no key
- Zero runtime dependencies (JDK only) ; Java 17+
- Forward-compatible deserialisation: unknown fields ignored, full payload via `raw()`
