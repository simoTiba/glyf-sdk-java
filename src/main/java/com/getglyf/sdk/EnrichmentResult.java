package com.getglyf.sdk;

import java.util.Collections;
import java.util.Map;

/**
 * Enriched view of a bank-transaction label, mirroring the public GLYF API response.
 *
 * <p>Enum-like fields ({@code source}, {@code businessIdType}, …) are exposed as
 * {@code String} on purpose: when the API introduces a new value, deployed SDKs
 * keep working. Unknown JSON fields are ignored for the same reason; the complete
 * parsed payload stays available through {@link #raw()}.</p>
 */
public final class EnrichmentResult {

    private final Map<String, Object> raw;

    EnrichmentResult(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(raw);
    }

    static EnrichmentResult fromJson(Map<String, Object> map) {
        return new EnrichmentResult(map);
    }

    // ── Merchant ────────────────────────────────────────────────────────

    /** Customer-facing merchant name, e.g. {@code "Carrefour Market"}. */
    public String merchantName() {
        return str("merchantName");
    }

    /** Local operator / franchise when it differs from the displayed brand. */
    public String operatorName() {
        return str("operatorName");
    }

    /** Default logo URL (aligned with {@link #merchantName()}). */
    public String logoUrl() {
        return str("logoUrl");
    }

    public String merchantLogoUrl() {
        return str("merchantLogoUrl");
    }

    public String operatorLogoUrl() {
        return str("operatorLogoUrl");
    }

    public String legalLogoUrl() {
        return str("legalLogoUrl");
    }

    // ── Categorisation ──────────────────────────────────────────────────

    /** Human-readable category label, e.g. {@code "Streaming"}. */
    public String category() {
        return str("category");
    }

    public String refinedCategory() {
        return str("refinedCategory");
    }

    // ── Label ───────────────────────────────────────────────────────────

    /** The raw label as submitted. */
    public String originalLabel() {
        return str("originalLabel");
    }

    /** Cleaned, display-ready label. */
    public String cleanLabel() {
        return str("cleanLabel");
    }

    /** Extra context extracted from the label, e.g. {@code "Trip Amsterdam"}. */
    public String complement() {
        return str("complement");
    }

    // ── Legal / registry ────────────────────────────────────────────────

    public String businessId() {
        return str("businessId");
    }

    /** e.g. {@code "SIRET"}, {@code "ICE"}, {@code "KBO"}, {@code "VAT"}. */
    public String businessIdType() {
        return str("businessIdType");
    }

    public String legalName() {
        return str("legalName");
    }

    public String address() {
        return str("address");
    }

    /** Official registry city. */
    public String city() {
        return str("city");
    }

    /** City detected from the label itself. */
    public String detectedCity() {
        return str("detectedCity");
    }

    public String postalCode() {
        return str("postalCode");
    }

    public String country() {
        return str("country");
    }

    /** French NAF activity code, when resolved through SIRENE. */
    public String nafCode() {
        return str("nafCode");
    }

    // ── Resolution metadata ─────────────────────────────────────────────

    /** Resolution source, e.g. {@code "DICTIONARY"}, {@code "SIRENE"}, {@code "LLM"}, {@code "NONE"}. */
    public String source() {
        return str("source");
    }

    /** Confidence score in [0, 1], or {@code null} when not applicable. */
    public Double confidence() {
        Object v = raw.get("confidence");
        return v instanceof Number n ? n.doubleValue() : null;
    }

    /** {@code true} when a registry lookup is deferred (rate limit, circuit open…). */
    public Boolean registryPending() {
        Object v = raw.get("registryPending");
        return v instanceof Boolean b ? b : null;
    }

    public String registryPendingReason() {
        return str("registryPendingReason");
    }

    public String reason() {
        return str("reason");
    }

    // ── Nested objects ──────────────────────────────────────────────────

    /**
     * Decision explanation, present when the request set {@code includeExplanation}.
     * Returns an empty map otherwise.
     */
    public Map<String, Object> explanation() {
        return mapField("explanation");
    }

    /** Tenant-level override applied to this result, when any. */
    public Map<String, Object> tenantOverride() {
        return mapField("tenantOverride");
    }

    /** Effective display values after overrides. */
    public Map<String, Object> display() {
        return mapField("display");
    }

    // ── Escape hatch ────────────────────────────────────────────────────

    /**
     * The full parsed JSON payload, including any field this SDK version does
     * not expose as a typed accessor yet. Read-only.
     */
    public Map<String, Object> raw() {
        return raw;
    }

    @Override
    public String toString() {
        return "EnrichmentResult{merchantName=" + merchantName()
                + ", category=" + category()
                + ", source=" + source()
                + ", confidence=" + confidence() + "}";
    }

    private String str(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapField(String key) {
        Object v = raw.get(key);
        return v instanceof Map ? Collections.unmodifiableMap((Map<String, Object>) v) : Map.of();
    }
}
