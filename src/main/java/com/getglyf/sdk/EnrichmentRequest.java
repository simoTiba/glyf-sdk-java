package com.getglyf.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single bank-transaction label to enrich.
 *
 * <p>Only {@code label} is required. When {@code country} is omitted the server
 * defaults to {@code FR}. {@code businessId}/{@code businessIdType} (e.g. SIRET
 * in France, ICE in Morocco, KBO in Belgium, VAT in the EU) unlock registry
 * resolution when known.</p>
 *
 * @param label              raw bank statement label (required, max 512 chars)
 * @param country            ISO 3166-1 alpha-2 country code, e.g. {@code "FR"} (optional)
 * @param businessId         company identifier when known (optional, max 32 chars)
 * @param businessIdType     identifier type, e.g. {@code "SIRET"}, {@code "ICE"} (optional)
 * @param includeExplanation when {@code true}, the response carries an explanation object (optional)
 */
public record EnrichmentRequest(
        String label,
        String country,
        String businessId,
        String businessIdType,
        Boolean includeExplanation
) {

    public EnrichmentRequest {
        Objects.requireNonNull(label, "label is required");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    /** Shortcut for the common case: a label and its country. */
    public static EnrichmentRequest of(String label, String country) {
        return new EnrichmentRequest(label, country, null, null, null);
    }

    /** Label only — the server applies its default country (FR). */
    public static EnrichmentRequest of(String label) {
        return new EnrichmentRequest(label, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Serialises to the API's JSON shape, omitting null fields. */
    Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        if (country != null) {
            map.put("country", country);
        }
        if (businessId != null) {
            map.put("businessId", businessId);
        }
        if (businessIdType != null) {
            map.put("businessIdType", businessIdType);
        }
        if (includeExplanation != null) {
            map.put("includeExplanation", includeExplanation);
        }
        return map;
    }

    public static final class Builder {
        private String label;
        private String country;
        private String businessId;
        private String businessIdType;
        private Boolean includeExplanation;

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder businessId(String businessId) {
            this.businessId = businessId;
            return this;
        }

        public Builder businessIdType(String businessIdType) {
            this.businessIdType = businessIdType;
            return this;
        }

        public Builder includeExplanation(boolean includeExplanation) {
            this.includeExplanation = includeExplanation;
            return this;
        }

        public EnrichmentRequest build() {
            return new EnrichmentRequest(label, country, businessId, businessIdType, includeExplanation);
        }
    }
}
