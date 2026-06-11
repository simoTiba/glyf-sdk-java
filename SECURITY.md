# Security Policy

## Supply chain

This SDK has **zero runtime dependencies** — it relies exclusively on the JDK
(`java.net.http`, `com.sun.net.httpserver` for the test mock). The dependency
tree your scanner sees is empty by design. Test-scope dependencies (JUnit) are
never shipped in the artifact.

Release artifacts published to Maven Central are **GPG-signed**.

The SDK performs no telemetry and opens no connection other than the
configured `baseUrl` (default `https://api.getglyf.com`).

## Reporting a vulnerability

Please report suspected vulnerabilities privately to
**contact@getglyf.com** — do not open a public issue. We aim to acknowledge
reports within 48 hours.

## Supported versions

| Version | Supported |
|---|---|
| latest 0.x | ✅ |
