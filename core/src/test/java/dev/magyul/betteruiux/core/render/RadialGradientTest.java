package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadialGradientTest {
    private static List<DrawCommand.FillRect> rects(String style) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>#a { width: 40; height: 40; " + style + " }</style><panel id='a'/></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l.commands().stream().filter(c -> c instanceof DrawCommand.FillRect).map(c -> (DrawCommand.FillRect) c).toList();
    }

    @Test
    void parsesRadialWithAndWithoutShape() {
        BoxPainter.Gradient a = BoxPainter.parseGradient("radial-gradient(#FF0000, #0000FF)");
        BoxPainter.Gradient b = BoxPainter.parseGradient("radial-gradient(circle at center, #FF0000, #0000FF)");
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(a.radial() && b.radial());
        assertEquals(2, b.colors().length, "모양 인자는 색이 아니므로 건너뛴다");
    }

    @Test
    void centerIsTheFirstColorAndOutsideTheLast() throws Exception {
        List<DrawCommand.FillRect> r = rects("background: radial-gradient(#FF0000, #0000FF)");
        DrawCommand.FillRect last = r.get(r.size() - 1);                 // 가장 안쪽 띠
        assertTrue((last.argb() >> 16 & 0xFF) > 150 && (last.argb() & 0xFF) < 100, "가운데는 빨강 쪽: " + Integer.toHexString(last.argb()));
        DrawCommand.FillRect first = r.get(0);                           // 바깥 전체 사각형
        assertEquals(40, first.w());
        assertTrue((first.argb() & 0xFF) > 150, "바깥은 파랑 쪽: " + Integer.toHexString(first.argb()));
    }

    @Test
    void bandsStayInsideTheBoxAndCostIsBounded() throws Exception {
        List<DrawCommand.FillRect> r = rects("background: radial-gradient(#FF0000, #00FF00, #0000FF)");
        for (DrawCommand.FillRect f : r) assertTrue(f.x() >= 0 && f.y() >= 0 && f.x() + f.w() <= 40 && f.y() + f.h() <= 40, f.toString());
        assertTrue(r.size() <= 6 * 40, "명령 수 상한: " + r.size());
    }

    @Test
    void roundedBoxClipsTheBands() throws Exception {
        List<DrawCommand.FillRect> r = rects("background: radial-gradient(#FF0000, #0000FF); border-radius: 10");
        assertTrue(r.stream().noneMatch(f -> f.x() == 0 && f.y() == 0), "둥근 모서리 끝은 칠하지 않는다");
    }
}
