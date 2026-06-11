import com.getglyf.sdk.EnrichmentRequest;
import com.getglyf.sdk.EnrichmentResult;
import com.getglyf.sdk.GlyfClient;

import java.util.List;

/**
 * GLYF SDK quickstart.
 *
 * 1. Get a free API key at https://getglyf.com/access (500 enrichments/day, no credit card)
 * 2. export GLYF_API_KEY=glyf_...
 * 3. Compile with the SDK jar on the classpath and run.
 */
public class Quickstart {

    public static void main(String[] args) {
        GlyfClient glyf = GlyfClient.builder()
                .apiKey(System.getenv("GLYF_API_KEY"))
                .build();

        // ── Single label ────────────────────────────────────────────
        EnrichmentResult single = glyf.enrich(
                EnrichmentRequest.of("CARTE 14/02/26 NETFLIX.COM CB*0000", "FR"));
        System.out.printf("%s · %s · %.0f%%%n",
                single.merchantName(), single.category(), single.confidence() * 100);

        // ── With a business identifier (registry resolution) ───────
        EnrichmentResult withRegistry = glyf.enrich(EnrichmentRequest.builder()
                .label("CARTE MONOPRIX BOULOGNE 55208329700382")
                .country("FR")
                .businessId("55208329700382")
                .businessIdType("SIRET")
                .build());
        System.out.printf("%s — %s (%s)%n",
                withRegistry.merchantName(), withRegistry.legalName(), withRegistry.nafCode());

        // ── Batch across countries ──────────────────────────────────
        List<EnrichmentResult> batch = glyf.enrichBatch(List.of(
                EnrichmentRequest.of("PRLV NETFLIX.COM NL AMSTERDAM", "FR"),
                EnrichmentRequest.of("PAIEMENT TPE MARJANE CASABLANCA", "MA"),
                EnrichmentRequest.of("PAIEMENT AVEC CARTE COLRUYT HALLE", "BE")));
        batch.forEach(r -> System.out.printf("  %-20s %s%n", r.merchantName(), r.category()));
    }
}
