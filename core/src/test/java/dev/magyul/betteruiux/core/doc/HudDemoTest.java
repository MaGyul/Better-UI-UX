package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.Golden;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 기본 제공 HUD 데모가 데이터 소스 바인딩으로 채워져 그려지는지. */
class HudDemoTest {
    private static UiDocument hud(int health) throws Exception {
        UiDocument d = UiDocument.parse(Files.readString(Path.of("../backend-mc26/src/main/resources/assets/better_uiux/ui/hud_demo.xml")), new FixedMetrics());
        d.setClock(() -> 0);
        d.set("fps", 60);
        d.set("player.health", health);
        d.set("player.maxHealth", 20);
        d.set("player.food", 18);
        d.set("player.xpProgress", 40);
        d.set("player.xpLevel", 7);
        d.set("player.x", 12);
        d.set("player.y", 64);
        d.set("player.z", -30);
        d.set("world.dimension", "minecraft:overworld");
        d.set("world.day", 3);
        d.set("world.time", 6000);
        d.layout(480, 270);
        return d;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    @Test
    void hudDemoShowsBoundValuesAndHasNoWarnings() throws Exception {
        UiDocument d = hud(14);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        assertEquals(java.util.List.of(), d.warnings());
        assertTrue(hasText(l, "60 FPS") && hasText(l, "HP 14/20") && hasText(l, "레벨 7") && hasText(l, "12, 64, -30"));
        assertTrue(hasText(l, "minecraft:overworld  3일차"));
    }

    @Test
    void hudFollowsChangingValuesEveryFrame() throws Exception {
        UiDocument d = hud(20);
        DrawList first = new DrawList();
        d.paint(first, -1, -1);
        d.set("player.health", 5);
        DrawList second = new DrawList();
        d.paint(second, -1, -1);
        assertTrue(hasText(first, "HP 20/20") && hasText(second, "HP 5/20"));
    }

    @Test
    void hudSnapshot() throws Exception {
        DrawList l = new DrawList();
        hud(14).paint(l, -1, -1);
        Golden.assertMatches("hud-demo-480x270", l);
    }
}
