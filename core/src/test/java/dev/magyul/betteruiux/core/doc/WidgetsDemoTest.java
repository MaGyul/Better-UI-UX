package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 기본 제공 widgets.uiml 데모가 경고 없이 열리고 위젯이 모두 배치되는지. */
class WidgetsDemoTest {
    @Test
    void bundledWidgetsDemoLoadsWithoutWarnings() throws Exception {
        Path file = Path.of("../backend-mc26/src/main/resources/assets/better_uiux/ui/widgets.uiml");
        UiDocument d = UiDocument.parse(Files.readString(file), new FixedMetrics());
        d.layout(480, 270);
        assertEquals(java.util.List.of(), d.warnings());
        for (String id : new String[] {"sound", "r1", "volume", "name", "color", "hp"}) {
            UiElement el = d.query("#" + id);
            assertNotNull(el, id);
            assertTrue(el.width() > 0 && el.height() > 0, id + " 가 배치된다");
        }
        d.paint(new DrawList(), -1, -1);
    }
}
