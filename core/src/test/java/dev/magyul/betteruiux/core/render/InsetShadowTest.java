package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsetShadowTest {
    private static List<DrawCommand.FillRect> rects(String style) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>#a { width: 40; height: 30; background: #202020; " + style + " }</style><panel id='a'/></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands().stream().filter(c -> c instanceof DrawCommand.FillRect).map(c -> (DrawCommand.FillRect) c).toList();
    }

    @Test
    void insetShadowDrawsInsideTheBoxOnly() throws Exception {
        List<DrawCommand.FillRect> r = rects("box-shadow: inset 0 0 4 #000000FF");
        assertTrue(r.size() > 2, "배경 + 안쪽 고리 층: " + r.size());
        for (DrawCommand.FillRect f : r) {
            assertTrue(f.x() >= 0 && f.y() >= 0 && f.x() + f.w() <= 40 && f.y() + f.h() <= 30, "상자 밖으로 나가지 않는다: " + f);
        }
    }

    @Test
    void offsetMakesTheTopEdgeThicker() throws Exception {
        List<DrawCommand.FillRect> r = rects("box-shadow: inset 0 3 0 #FF0000FF");
        int top = r.stream().filter(f -> f.argb() == 0xFFFF0000 && f.y() == 0 && f.w() == 40).mapToInt(DrawCommand.FillRect::h).sum();
        int bottom = r.stream().filter(f -> f.argb() == 0xFFFF0000 && f.y() + f.h() == 30 && f.w() == 40).mapToInt(DrawCommand.FillRect::h).sum();
        assertEquals(3, top);
        assertEquals(0, bottom);
    }

    @Test
    void outerShadowIsUnaffectedByInsetEntries() throws Exception {
        List<DrawCommand.FillRect> r = rects("box-shadow: 0 0 0 #00FF00FF");
        assertTrue(r.stream().anyMatch(f -> f.argb() == 0xFF00FF00 && f.w() == 40 && f.h() == 30), "일반 그림자는 그대로");
    }
}
