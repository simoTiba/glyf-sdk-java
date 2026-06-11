package com.getglyf.sdk.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesFlatObject() {
        Object parsed = Json.parse("{\"merchantName\":\"Netflix\",\"confidence\":0.97,\"city\":null,\"ok\":true}");
        assertTrue(parsed instanceof Map);
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals("Netflix", map.get("merchantName"));
        assertEquals(0.97, (Double) map.get("confidence"), 1e-9);
        assertNull(map.get("city"));
        assertEquals(Boolean.TRUE, map.get("ok"));
    }

    @Test
    void parsesNestedObjectsAndArrays() {
        Object parsed = Json.parse("[{\"a\":{\"b\":[1,2,3]}},{\"c\":\"x\"}]");
        List<?> list = (List<?>) parsed;
        assertEquals(2, list.size());
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        Map<?, ?> a = (Map<?, ?>) first.get("a");
        assertEquals(List.of(1.0, 2.0, 3.0), a.get("b"));
    }

    @Test
    void parsesEscapesAndUnicode() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"label\":\"CARTE \\\"NETFLIX\\\"\\n\\t\\u00e9\\u20ac\"}");
        assertEquals("CARTE \"NETFLIX\"\n\té€", map.get("label"));
    }

    @Test
    void parsesNegativeAndExponentNumbers() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"a\":-12.5,\"b\":3e2,\"c\":0}");
        assertEquals(-12.5, (Double) map.get("a"), 1e-9);
        assertEquals(300.0, (Double) map.get("b"), 1e-9);
        assertEquals(0.0, (Double) map.get("c"), 1e-9);
    }

    @Test
    void writesAndReparsesRoundTrip() {
        Map<String, Object> input = Map.of(
                "label", "PAIEMENT \"TPE\" \\ CASA\né",
                "confidence", 0.97,
                "count", 42,
                "nested", Map.of("list", List.of("a", "b")));
        Object reparsed = Json.parse(Json.write(input));
        Map<?, ?> map = (Map<?, ?>) reparsed;
        assertEquals("PAIEMENT \"TPE\" \\ CASA\né", map.get("label"));
        assertEquals(0.97, (Double) map.get("confidence"), 1e-9);
        assertEquals(42.0, (Double) map.get("count"), 1e-9);
        assertEquals(List.of("a", "b"), ((Map<?, ?>) map.get("nested")).get("list"));
    }

    @Test
    void writesIntegersWithoutDecimalPoint() {
        assertEquals("{\"n\":42}", Json.write(Map.of("n", 42)));
        assertEquals("{\"n\":42}", Json.write(Map.of("n", 42.0)));
        assertEquals("{\"n\":0.5}", Json.write(Map.of("n", 0.5)));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
    }
}
