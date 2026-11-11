package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.api.GradientSpec;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 셰이더로 넘어가는 그라디언트 매개변수: 각도, 모서리 방향, 반복, 방사형 모양·크기·위치, 원뿔 from/at. */
class GradientParamsTest {
    private static GradientSpec spec(String bg, int w, int h) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>#a { width: " + w + "; height: " + h + "; background: " + bg + "; border-radius: 4 }</style><panel id='a'/></screen>", new FixedMetrics());
        d.layout(400, 400);
        DrawList l = new DrawList().supportRoundRects(true);
        d.paint(l, -1, -1);
        List<DrawCommand> c = l.commands();
        return ((DrawCommand.FillRoundGradient) c.stream().filter(x -> x instanceof DrawCommand.FillRoundGradient).findFirst().orElseThrow()).spec();
    }

    @Test
    void anyAngleBecomesAnAngledLinear() throws Exception {
        GradientSpec s = spec("linear-gradient(30deg, #FF0000, #0000FF)", 80, 40);
        assertEquals(4, s.mode());
        assertEquals(30, s.angleDeg());
        assertEquals(0xFFFF0000, s.colors()[0], "방향이 각도에 있으니 정지점은 원래 순서");
    }

    @Test
    void axisKeywordsStayOnTheAxisModes() throws Exception {
        assertEquals(0, spec("linear-gradient(to bottom, #FF0000, #0000FF)", 80, 40).mode());
        assertEquals(1, spec("linear-gradient(to right, #FF0000, #0000FF)", 80, 40).mode());
    }

    @Test
    void toTopTurnsIntoAReversedVerticalWithoutAnAngle() throws Exception {
        GradientSpec s = spec("linear-gradient(to top, #FF0000, #0000FF)", 80, 40);
        assertEquals(0, s.mode());
        assertEquals(0xFF0000FF, s.colors()[0], "정지점이 뒤집힌다");
    }

    @Test
    void cornerDirectionDependsOnTheBoxShape() throws Exception {
        GradientSpec square = spec("linear-gradient(to top right, #FF0000, #0000FF)", 40, 40);
        assertEquals(4, square.mode());
        assertEquals(45, square.angleDeg());
        GradientSpec wide = spec("linear-gradient(to top right, #FF0000, #0000FF)", 200, 100);
        assertTrue(wide.angleDeg() > 20 && wide.angleDeg() < 45, "넓은 상자는 더 세로에 가깝다(위쪽 0도 쪽): " + wide.angleDeg());
        assertEquals(135, spec("linear-gradient(to bottom right, #FF0000, #0000FF)", 40, 40).angleDeg());
        assertEquals(315, spec("linear-gradient(to top left, #FF0000, #0000FF)", 40, 40).angleDeg());
    }

    @Test
    void repeatingStretchesTheStopRangeAndKeepsThePeriod() throws Exception {
        GradientSpec s = spec("repeating-linear-gradient(to right, #FF0000 0%, #0000FF 20%)", 100, 20);
        assertTrue(s.repeating());
        assertEquals(0f, s.repStart(), 1e-4);
        assertEquals(0.2f, s.repLen(), 1e-4);
        assertEquals(1f, s.stops()[s.stops().length - 1], 1e-4, "한 주기를 0..1 로 늘린다");
    }

    @Test
    void radialShapeSizeAndPosition() throws Exception {
        GradientSpec s = spec("radial-gradient(circle closest-side at 25% 75%, #FF0000, #0000FF)", 100, 60);
        assertEquals(2, s.mode());
        assertEquals(1, s.shape());
        assertEquals(3, s.size());
        assertEquals(0.25f, s.cx(), 1e-4);
        assertEquals(0.75f, s.cy(), 1e-4);
        GradientSpec d = spec("radial-gradient(#FF0000, #0000FF)", 100, 60);
        assertEquals(0, d.shape());
        assertEquals(0, d.size());
        assertEquals(0.5f, d.cx(), 1e-4);
    }

    @Test
    void positionKeywordsAndPixels() throws Exception {
        assertEquals(0f, BoxPainter.position("left top", 100, 50, true), 1e-4);
        assertEquals(1f, BoxPainter.position("right bottom", 100, 50, false), 1e-4);
        assertEquals(0.2f, BoxPainter.position("20px 10px", 100, 50, true), 1e-4);
        assertEquals(0.2f, BoxPainter.position("20px 10px", 100, 50, false), 1e-4);
        assertEquals(1f, BoxPainter.position("right", 100, 50, true), 1e-4);
        assertEquals(0.5f, BoxPainter.position("right", 100, 50, false), 1e-4, "축이 없으면 가운데");
        assertEquals(0.5f, BoxPainter.position(null, 100, 50, true), 1e-4);
    }

    @Test
    void conicFromAndAt() throws Exception {
        GradientSpec s = spec("conic-gradient(from 90deg at 30% 70%, #FF0000, #00FF00, #0000FF)", 60, 60);
        assertEquals(3, s.mode());
        assertEquals(90f, s.fromDeg(), 1e-4);
        assertEquals(0.3f, s.cx(), 1e-4);
        assertEquals(0.7f, s.cy(), 1e-4);
        assertEquals(3, s.colors().length);
    }

    @Test
    void conicAngleStops() throws Exception {
        GradientSpec s = spec("conic-gradient(#FF0000 0deg, #00FF00 90deg, #0000FF 270deg)", 60, 60);
        assertEquals(0f, s.stops()[0], 1e-4);
        assertEquals(0.25f, s.stops()[1], 1e-4);
        assertEquals(0.75f, s.stops()[2], 1e-4);
    }
}
