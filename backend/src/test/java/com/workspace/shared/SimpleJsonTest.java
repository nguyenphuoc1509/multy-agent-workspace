package com.workspace.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.workspace.shared.published.SimpleJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleJsonTest {

    @Test
    void writesMapsListsAndScalarsDeterministically() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "DRAFT");
        m.put("n", 2);
        m.put("ok", true);
        m.put("none", null);
        m.put("list", List.of("a", 1));
        m.put("id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(SimpleJson.write(m)).isEqualTo(
                "{\"status\":\"DRAFT\",\"n\":2,\"ok\":true,\"none\":null,\"list\":[\"a\",1],"
                        + "\"id\":\"00000000-0000-0000-0000-000000000001\"}");
    }

    @Test
    void escapesQuotesBackslashesAndNewlines() {
        assertThat(SimpleJson.write(Map.of("k", "a\"b\\c\nd")))
                .isEqualTo("{\"k\":\"a\\\"b\\\\c\\nd\"}");
    }
}