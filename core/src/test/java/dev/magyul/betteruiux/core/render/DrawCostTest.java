package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

/** 장식의 그리기 명령 수: 실제 게임에서는 명령 하나하나가 비용이라(상자 240 개에 8천 개면 9fps) 높이·번짐에 비례해 늘지 않아야 한다. */
class DrawCostTest {
    private static int commands(String css, int h) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>.c{width:40;height:" + h + ";" + css + "}</style><panel class=\"c\"/></screen>", new FixedMetrics());
        d.layout(400, 400);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return (int) l.commands().stream().filter(c -> !(c instanceof DrawCommand.RequestCursor)).count();
    }

    @Test
    void roundedGradientCostDoesNotGrowWithHeight() throws Exception {
        String css = "background:linear-gradient(#4488CC,#224466);border-radius:4";
        assertEquals(commands(css, 20), commands(css, 20));
        int tall = commands(css, 200);
        assertTrue(tall <= commands(css, 20) + 2, "키가 커져도 명령 수는 거의 같아야 한다: " + tall);
        assertTrue(tall <= 12, "둥근 그라디언트 한 상자는 12 개 이하: " + tall);
    }

    @Test
    void shadowStaysSmall() throws Exception {
        int n = commands("background:#444444;border-radius:4;box-shadow:0 1 3 #000000AA", 14);
        assertTrue(n <= 20, "번짐 3 그림자 + 둥근 상자는 20 개 이하: " + n);
    }

    @Test
    void multiStopGradientBandIsSplitAtStops() throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>.c{width:40;height:100;background:linear-gradient(#FF0000,#00FF00,#0000FF);border-radius:4}</style><panel class=\"c\"/></screen>", new FixedMetrics());
        d.layout(400, 400);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        long bands = l.commands().stream().filter(c -> c instanceof DrawCommand.FillGradient).count();
        assertEquals(2, bands, "멈춤 지점이 3개면 가운데 구간은 두 조각");
    }
}
