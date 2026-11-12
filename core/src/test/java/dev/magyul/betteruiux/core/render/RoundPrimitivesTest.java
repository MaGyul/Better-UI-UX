package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 백엔드가 둥근 사각형 프리미티브(셰이더)를 지원하면 채움/테두리/그림자가 각각 명령 하나로 나가고, 아니면 사각형 여러 개로 그려진다. */
class RoundPrimitivesTest {
    private static List<DrawCommand> paint(String style, boolean supported) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>#a { width: 40; height: 30; background: #204060; " + style + " }</style><panel id='a'/></screen>", new FixedMetrics());
        d.layout(200, 200);
        DrawList l = new DrawList().supportRoundRects(supported);
        d.paint(l, -1, -1);
        return l.commands();
    }

    private static long count(List<DrawCommand> c, Class<?> t) { return c.stream().filter(t::isInstance).count(); }

    @Test
    void roundedBackgroundIsOneFillRoundRect() throws Exception {
        List<DrawCommand> c = paint("border-radius: 6", true);
        assertEquals(1, count(c, DrawCommand.FillRoundRect.class));
        assertEquals(0, count(c, DrawCommand.FillRect.class), "사각형 근사는 쓰지 않는다");
        DrawCommand.FillRoundRect f = (DrawCommand.FillRoundRect) c.stream().filter(x -> x instanceof DrawCommand.FillRoundRect).findFirst().orElseThrow();
        assertEquals(6, f.radius());
        assertEquals(40, f.w());
    }

    @Test
    void unsupportedBackendFallsBackToRects() throws Exception {
        List<DrawCommand> c = paint("border-radius: 6", false);
        assertEquals(0, count(c, DrawCommand.FillRoundRect.class));
        assertTrue(count(c, DrawCommand.FillRect.class) > 1);
    }

    @Test
    void uniformRoundedBorderIsOneStroke() throws Exception {
        List<DrawCommand> c = paint("border-radius: 6; border: 2 solid #FFFFFF", true);
        DrawCommand.StrokeRoundRect s = (DrawCommand.StrokeRoundRect) c.stream().filter(x -> x instanceof DrawCommand.StrokeRoundRect).findFirst().orElseThrow();
        assertEquals(2, s.thickness());
        assertEquals(6, s.radius());
        assertEquals(1, count(c, DrawCommand.StrokeRoundRect.class));
    }

    @Test
    void blurredShadowIsOneShadowQuad() throws Exception {
        List<DrawCommand> c = paint("border-radius: 6; box-shadow: 2 3 8 #000000FF", true);
        DrawCommand.ShadowRoundRect s = (DrawCommand.ShadowRoundRect) c.stream().filter(x -> x instanceof DrawCommand.ShadowRoundRect).findFirst().orElseThrow();
        assertEquals(8, s.blur());
        assertEquals(2, s.x(), "오프셋이 반영된 모양의 위치");
        assertEquals(3, s.y());
        assertEquals(40, s.w());
        assertEquals(1, count(c, DrawCommand.ShadowRoundRect.class));
    }

    @Test
    void squareBoxWithBlurredShadowAlsoUsesIt() throws Exception {
        List<DrawCommand> c = paint("box-shadow: 0 0 6 #000000FF", true);
        DrawCommand.ShadowRoundRect s = (DrawCommand.ShadowRoundRect) c.stream().filter(x -> x instanceof DrawCommand.ShadowRoundRect).findFirst().orElseThrow();
        assertEquals(0, s.radius());
    }

    @Test
    void differentCornerRadiiAreOnePrimitiveToo() throws Exception {
        List<DrawCommand> c = paint("border-radius: 8 2 6 0", true);
        DrawCommand.FillRoundRect f = (DrawCommand.FillRoundRect) c.stream().filter(x -> x instanceof DrawCommand.FillRoundRect).findFirst().orElseThrow();
        assertEquals(8, f.tl());
        assertEquals(2, f.tr());
        assertEquals(6, f.br());
        assertEquals(0, f.bl());
        assertEquals(0, count(c, DrawCommand.FillRect.class));
    }

    @Test
    void nonUniformRadiiFallBackWhenUnsupported() throws Exception {
        List<DrawCommand> c = paint("border-radius: 8 2 8 2", false);
        assertEquals(0, count(c, DrawCommand.FillRoundRect.class));
        assertTrue(count(c, DrawCommand.FillRect.class) > 1);
    }

    @Test
    void dashedBorderKeepsTheRectPath() throws Exception {
        List<DrawCommand> c = paint("border-radius: 6; border: 2 dashed #FFFFFF", true);
        assertEquals(0, count(c, DrawCommand.StrokeRoundRect.class));
    }
}
