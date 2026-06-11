# Changelog

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
