package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class WidgetTextStyleTest {
    private static DrawList paint(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(300, 200);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l;
    }

    private static List<DrawCommand.DrawText> texts(DrawList l) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText).map(c -> (DrawCommand.DrawText) c).toList();
    }

    @Test
    void defaultsAreUnchanged() throws Exception {
        DrawList l = paint("<screen><checkbox id='c' x='0' y='0' text='ab'/></screen>");
        assertEquals(1, texts(l).size());
        assertEquals("ab", texts(l).get(0).text());
        assertTrue(l.commands().stream().noneMatch(c -> c instanceof DrawCommand.PushTransform));
    }

    @Test
    void checkboxLabelFollowsFontSizeColorAndTransform() throws Exception {
        DrawList l = paint("<screen><checkbox x='0' y='0' text='ab' style='font-size: 16px; color: #ff0000; text-transform: uppercase'/></screen>");
        assertEquals("AB", texts(l).get(0).text());
        assertEquals(0xFFFF0000, texts(l).get(0).argb());
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.PushTransform t && t.scale() == 2f));
    }

    @Test
    void boldSliderTextIsDrawnTwice() throws Exception {
        DrawList l = paint("<screen><slider x='0' y='0' width='100' height='14' value='3' text='v' style='font-weight: bold'/></screen>");
        assertEquals(2, texts(l).size());
        assertEquals(texts(l).get(0).x() + 1, texts(l).get(1).x());
    }

    @Test
    void progressAndTabTitlesAreStyled() throws Exception {
        DrawList p = paint("<screen><progress x='0' y='0' width='100' height='12' value='5' max='10' text='hp' style='letter-spacing: 2px'/></screen>");
        assertEquals(2, texts(p).size());                                     // 글자마다 따로
        DrawList t = paint("<screen><tabs x='0' y='0' width='100' height='60' style='font-size: 12px'><tab title='One'/></tabs></screen>");
        assertTrue(t.commands().stream().anyMatch(c -> c instanceof DrawCommand.PushTransform tr && tr.scale() == 1.5f));
    }
}
