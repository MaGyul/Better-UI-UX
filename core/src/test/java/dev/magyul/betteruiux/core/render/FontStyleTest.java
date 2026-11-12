package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.TextStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

/** font-style: italic 과 font-family(마인크래프트 글꼴 id). 글꼴을 구분하는 측정기로 폭이 달라지는 것까지 본다. */
class FontStyleTest {
    /** 기본 글꼴은 글자당 6px, minecraft:uniform 은 글자당 8px. */
    private static final TextMetrics METRICS = new TextMetrics() {
        @Override public int width(String text) { return text.length() * 6; }

        @Override public int width(String text, String font) { return "minecraft:uniform".equals(font) ? text.length() * 8 : width(text); }

        @Override public int lineHeight() { return 9; }
    };

    private static UiDocument doc(String style, String text) throws Exception {
        UiDocument d = UiDocument.parse("<screen><label id='l' x='0' y='0' style='" + style + "'>" + text + "</label></screen>", METRICS);
        d.setAnimationsEnabled(false);
        d.layout(200, 100);
        return d;
    }

    private static List<DrawCommand> paint(UiDocument d) {
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands();
    }

    @Test
    void familyAliasesAndIds() {
        assertNull(TextStyle.fontId(null));
        assertNull(TextStyle.fontId("default"));
        assertNull(TextStyle.fontId("Arial, sans-serif"), "알 수 없는 이름만 있으면 기본 글꼴");
        assertEquals("minecraft:uniform", TextStyle.fontId("uniform"));
        assertEquals("minecraft:uniform", TextStyle.fontId("\"Arial\", Uniform"), "목록에서 처음 알아본 것");
        assertEquals("minecraft:alt", TextStyle.fontId("enchanting"));
        assertEquals("mymod:fancy/title", TextStyle.fontId("mymod:fancy/title"));
    }

    @Test
    void italicIsDrawnAsStyledText() throws Exception {
        List<DrawCommand> c = paint(doc("font-style: italic", "abc"));
        DrawCommand.DrawStyledText t = (DrawCommand.DrawStyledText) c.stream().filter(x -> x instanceof DrawCommand.DrawStyledText).findFirst().orElseThrow();
        assertEquals("abc", t.text());
        assertTrue(t.italic());
        assertNull(t.font());
        assertTrue(c.stream().noneMatch(x -> x instanceof DrawCommand.DrawText), "일반 글자 명령은 쓰지 않는다");
    }

    @Test
    void plainTextKeepsUsingDrawText() throws Exception {
        List<DrawCommand> c = paint(doc("", "abc"));
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText));
        assertTrue(c.stream().noneMatch(x -> x instanceof DrawCommand.DrawStyledText));
    }

    @Test
    void fontFamilyChangesWidthAndCommand() throws Exception {
        UiDocument d = doc("font-family: uniform", "abc");
        assertEquals(24, d.findById("l").w, "글꼴별 폭(8px x 3)으로 배치된다");
        DrawCommand.DrawStyledText t = (DrawCommand.DrawStyledText) paint(d).stream().filter(x -> x instanceof DrawCommand.DrawStyledText).findFirst().orElseThrow();
        assertEquals("minecraft:uniform", t.font());
        assertEquals(18, doc("", "abc").findById("l").w);
    }

    @Test
    void fontStyleAndFamilyAreInherited() throws Exception {
        UiDocument d = UiDocument.parse("<screen><panel style='font-style: italic; font-family: uniform'><label id='l' text='ab'/></panel></screen>", METRICS);
        d.layout(200, 100);
        assertEquals(16, d.findById("l").w);
        assertTrue(paint(d).stream().anyMatch(x -> x instanceof DrawCommand.DrawStyledText s && s.italic() && "minecraft:uniform".equals(s.font())));
    }

    @Test
    void worksTogetherWithSizeAndBold() throws Exception {
        List<DrawCommand> c = paint(doc("font-style: italic; font-weight: bold; font-size: 16px", "ab"));
        assertEquals(2, c.stream().filter(x -> x instanceof DrawCommand.DrawStyledText).count(), "굵게는 두 번 겹쳐 그린다");
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.PushTransform t && t.scale() == 2f));
    }
}
