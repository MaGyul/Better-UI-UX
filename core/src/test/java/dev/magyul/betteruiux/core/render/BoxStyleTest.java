package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoxStyleTest {
    private static final int W = 80, H = 60;

    /** 한 개의 panel(id=p, x=10 y=10 20x20 기본)을 그려 드로우 리스트와 래스터(마지막으로 칠한 색, 0 = 비어 있음)를 돌려준다. */
    private record Shot(DrawList list, int[][] px) {
        int at(int x, int y) { return px[x][y]; }
    }

    private static Shot shot(String panelAttrs) throws Exception {
        return shotXml("<screen><panel id='p' x='10' y='10' width='20' height='20' " + panelAttrs + "/></screen>");
    }

    private static Shot shotXml(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(W, H);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        int[][] px = new int[W][H];
        for (DrawCommand c : l.commands()) {
            if (c instanceof DrawCommand.FillRect f) {
                for (int x = Math.max(0, f.x()); x < Math.min(W, f.x() + f.w()); x++) {
                    for (int y = Math.max(0, f.y()); y < Math.min(H, f.y() + f.h()); y++) {
                        px[x][y] = (f.argb() >>> 24) == 0xFF ? f.argb() : blend(px[x][y], f.argb());
                    }
                }
            } else if (c instanceof DrawCommand.FillGradient g) {
                for (int x = Math.max(0, g.x()); x < Math.min(W, g.x() + g.w()); x++) {
                    for (int y = Math.max(0, g.y()); y < Math.min(H, g.y() + g.h()); y++) {
                        float t = (y - g.y() + 0.5f) / g.h();
                        px[x][y] = BoxPainter.lerp(g.topArgb(), g.bottomArgb(), t);
                    }
                }
            }
        }
        return new Shot(l, px);
    }

    private static int blend(int dst, int src) {
        int a = src >>> 24;
        if (a == 0) return dst;
        int da = dst >>> 24;
        int outA = a + da * (255 - a) / 255;
        return (Math.min(255, outA) << 24) | (src & 0xFFFFFF);
    }

    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF;

    @Test
    void plainBackgroundIsASingleRect() throws Exception {
        Shot s = shot("background='#ff0000'");
        List<DrawCommand> rects = s.list().commands().stream().filter(c -> c instanceof DrawCommand.FillRect).toList();
        assertEquals(List.of(new DrawCommand.FillRect(10, 10, 20, 20, RED)), rects);
    }

    @Test
    void roundedCornersCutTheCorners() throws Exception {
        Shot s = shot("background='#ff0000' style='border-radius: 6'");
        assertEquals(0, s.at(10, 10));                                // 모서리는 비어 있다
        assertEquals(0, s.at(29, 29));
        assertEquals(RED, s.at(20, 10));                              // 변의 가운데는 채워진다
        assertEquals(RED, s.at(10, 20));
        assertEquals(RED, s.at(20, 20));
        assertTrue(s.list().commands().size() > 1);
    }

    @Test
    void percentRadiusMakesACircle() throws Exception {
        Shot s = shot("background='#ff0000' style='border-radius: 50%'");
        assertEquals(0, s.at(10, 10));
        assertEquals(0, s.at(29, 10));
        assertEquals(RED, s.at(20, 20));
        assertEquals(RED, s.at(20, 10));
    }

    @Test
    void perCornerRadii() throws Exception {
        Shot s = shot("background='#ff0000' style='border-radius: 8 0 8 0'");
        assertEquals(0, s.at(10, 10));                                // 좌상 둥글다
        assertEquals(RED, s.at(29, 10));                              // 우상 각짐
        assertEquals(0, s.at(29, 29));                                // 우하 둥글다
        assertEquals(RED, s.at(10, 29));
    }

    @Test
    void roundedBorderRing() throws Exception {
        Shot s = shot("background='#0000ff' style='border: 2px solid #ff0000; border-radius: 6'");
        assertEquals(0, s.at(10, 10));
        assertEquals(RED, s.at(20, 10));                              // 위쪽 테두리
        assertEquals(RED, s.at(20, 11));
        assertEquals(BLUE, s.at(20, 12));                             // 테두리 안쪽은 배경
        assertEquals(BLUE, s.at(20, 20));
        assertEquals(RED, s.at(10, 20));
        assertEquals(RED, s.at(29, 20));
    }

    @Test
    void perSideBorderWidthsAndColors() throws Exception {
        Shot s = shot("style='border-width: 1 2 3 4; border-color: #ff0000 #00ff00 #0000ff #ffff00'");
        assertEquals(RED, s.at(20, 10));
        assertEquals(0xFF00FF00, s.at(28, 20));
        assertEquals(BLUE, s.at(20, 28));
        assertEquals(0xFFFFFF00, s.at(11, 20));
        assertEquals(0, s.at(20, 20));                                // 안쪽은 비어 있다
    }

    @Test
    void borderDoesNotShrinkTheBoxButTakesContentSpace() throws Exception {
        UiDocument d = UiDocument.parse("<screen><column id='c' x='0' y='0' width='40' height='40' style='border-width: 1 2 3 4'>"
                + "<panel id='k' width='fill' height='5'/></column></screen>", new FixedMetrics());
        d.layout(W, H);
        assertEquals(4, d.findById("k").x);
        assertEquals(1, d.findById("k").y);
        assertEquals(34, d.findById("k").w);
    }

    @Test
    void dashedBorderHasGaps() throws Exception {
        Shot s = shot("style='border: 1px dashed #ff0000'");
        int painted = 0;
        for (int x = 10; x < 30; x++) if (s.at(x, 10) == RED) painted++;
        assertTrue(painted > 0 && painted < 20, "dashed top edge: " + painted);
        Shot solid = shot("style='border: 1px solid #ff0000'");
        for (int x = 10; x < 30; x++) assertEquals(RED, solid.at(x, 10));
    }

    @Test
    void outlineDrawsOutsideWithoutTakingSpace() throws Exception {
        Shot s = shot("background='#0000ff' style='outline: 2px solid #ff0000; outline-offset: 1px'");
        assertEquals(RED, s.at(7, 20));                               // 상자 왼쪽 밖 1px 띄우고 2px
        assertEquals(RED, s.at(8, 20));
        assertEquals(0, s.at(9, 20));                                 // offset 틈
        assertEquals(BLUE, s.at(10, 20));
        UiDocument d = UiDocument.parse("<screen><panel id='p' width='20' height='20' style='outline: 3px solid red'/></screen>", new FixedMetrics());
        d.layout(W, H);
        assertEquals(20, d.findById("p").w);
    }

    @Test
    void horizontalGradient() throws Exception {
        Shot s = shot("style='background: linear-gradient(to right, #ff0000, #0000ff)'");
        int left = s.at(10, 20), right = s.at(29, 20), mid = s.at(20, 20);
        assertTrue(((left >> 16) & 0xFF) > 200 && (left & 0xFF) < 60, Integer.toHexString(left));
        assertTrue((right & 0xFF) > 200 && ((right >> 16) & 0xFF) < 60, Integer.toHexString(right));
        assertNotEquals(left, mid);
        assertNotEquals(right, mid);
    }

    @Test
    void verticalGradientUsesNativeCommand() throws Exception {
        Shot s = shot("style='background: linear-gradient(to bottom, #ff0000, #0000ff)'");
        assertTrue(s.list().commands().stream().anyMatch(c -> c instanceof DrawCommand.FillGradient));
        Shot up = shot("style='background: linear-gradient(0deg, #ff0000, #0000ff)'");   // 위쪽이 파랑
        assertTrue((up.at(20, 10) & 0xFF) > 150);
        assertTrue(((up.at(20, 29) >> 16) & 0xFF) > 150);
    }

    @Test
    void multiStopRoundedGradient() throws Exception {
        Shot s = shot("style='background: linear-gradient(to bottom, #ff0000 0%, #00ff00 50%, #0000ff 100%); border-radius: 8'");
        assertEquals(0, s.at(10, 10));
        assertTrue(((s.at(20, 10) >> 16) & 0xFF) > 150);
        assertTrue(((s.at(20, 20) >> 8) & 0xFF) > 150);
        assertTrue((s.at(20, 29) & 0xFF) > 150);
    }

    @Test
    void backgroundImageGradientOverBackgroundColor() throws Exception {
        Shot s = shot("style='background: #00ff00; background-image: linear-gradient(to right, #ff0000, #ff0000)'");
        assertEquals(RED, s.at(20, 20));
    }

    @Test
    void boxShadowFadesOutside() throws Exception {
        Shot s = shot("background='#ffffff' style='box-shadow: 0 0 6 #000000'");
        int near = s.at(9, 20) >>> 24, far = s.at(5, 20) >>> 24, edge = s.at(4, 20) >>> 24;
        assertTrue(near > 0, "그림자가 상자 바로 밖에 보인다");
        assertTrue(near >= far, "바깥으로 갈수록 옅어진다: " + near + " >= " + far);
        assertTrue(far >= edge);
        assertEquals(0, s.at(2, 20));                                 // 번짐 범위 밖
        assertEquals(0xFFFFFFFF, s.at(20, 20));
    }

    @Test
    void hardShadowOffsetAndSpread() throws Exception {
        Shot s = shot("background='#ffffff' style='box-shadow: 4px 4px 0 2px #ff0000'");
        assertEquals(RED, s.at(33, 33));                              // 오프셋 4 + 확장 2
        assertEquals(0, s.at(36, 36));
        assertEquals(0xFFFFFFFF, s.at(20, 20));
    }

    @Test
    void opacityScalesDecorations() throws Exception {
        Shot s = shot("background='#ff0000' style='opacity: 0.5; border-radius: 4'");
        assertEquals(0x80, s.at(20, 20) >>> 24);
    }

    @Test
    void parsers() {
        assertNotNull(BoxPainter.parseGradient("linear-gradient(90deg, red, blue)"));
        assertNull(BoxPainter.parseGradient("linear-gradient(to right, notacolor, blue)"));
        assertTrue(BoxPainter.isGradient("linear-gradient(red, blue)"));
        assertTrue(!BoxPainter.isGradient("#ff0000"));
        assertEquals(2, BoxPainter.parseShadows("0 0 4 red, 2px 2px #00000080").size());
        assertTrue(BoxPainter.parseShadows("inset 0 0 4 red").get(0).inset());
        assertEquals(0, BoxPainter.parseShadows("none").size());
        var g = BoxPainter.parseGradient("linear-gradient(to right, red 10%, green, blue 90%)");
        assertEquals(3, g.colors().length);
        assertEquals(0.5f, g.pos()[1], 1e-4);
    }
}
