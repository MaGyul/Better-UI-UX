package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiConfigUpdateTest {
    @Test
    void updatesExistingKeysInsertsNewOnesAndKeepsComments() {
        String out = UiConfig.applyChanges(UiConfig.DEFAULT_TEXT, Map.of("editor.leftCollapsed", "true", "editor.enabled", "true"));
        Map<String, String> m = UiConfig.parse(out);
        assertEquals("true", m.get("editor.leftCollapsed"));
        assertEquals("true", m.get("editor.enabled"));
        assertEquals("true", m.get("hotReload.enabled"), "다른 섹션은 그대로");
        assertTrue(out.contains("# 인게임 에디터"), "주석 유지");
    }

    @Test
    void createsMissingSectionsAndOverwritesOnRepeat() {
        String once = UiConfig.applyChanges("[a]\nx = 1\n", Map.of("b.y", "2"));
        assertEquals("2", UiConfig.parse(once).get("b.y"));
        assertEquals("1", UiConfig.parse(once).get("a.x"));
        Map<String, String> c = new LinkedHashMap<>();
        c.put("b.y", "3");
        String twice = UiConfig.applyChanges(once, c);
        assertEquals("3", UiConfig.parse(twice).get("b.y"));
        assertEquals(1, twice.split("y = ", -1).length - 1);
    }
}
