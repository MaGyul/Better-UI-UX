package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.anim.Interpolator;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class LayoutAnimationTest {
    private long now;

    private UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> now);
        d.layout(300, 200);
        return d;
    }

    private void frame(UiDocument d, long t) {
        now = t;
        d.paint(new DrawList(), -1, -1);
    }

    @Test
    void widthTransitionMovesTheLayoutEveryFrame() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #a { width: 100; height: 10; transition: width 100ms linear }
                #a.big { width: 200 }
              </style>
              <panel id='a'/><panel id='after' x='0' y='50' width='5' height='5'/>
            </screen>""");
        frame(d, 0);
        assertEquals(100, d.findById("a").w);
        d.query("#a").addClass("big");
        frame(d, 0);
        assertEquals(100, d.findById("a").w);                     // 막 시작: 아직 그대로
        frame(d, 50);
        assertEquals(150, d.findById("a").w);                     // 레이아웃 자체가 움직인다
        frame(d, 75);
        assertEquals(175, d.findById("a").w);
        frame(d, 100);
        assertEquals(200, d.findById("a").w);
        frame(d, 500);
        assertEquals(200, d.findById("a").w);                     // 끝난 뒤에는 목표값 유지
    }

    @Test
    void siblingsFollowAnAnimatedSize() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #a { width: 40; height: 10; transition: width 100ms linear }
                #a.wide { width: 80 }
              </style>
              <row width='300' height='20'><panel id='a'/><panel id='b' width='5' height='5'/></row>
            </screen>""");
        frame(d, 0);
        d.query("#a").addClass("wide");
        frame(d, 0);
        frame(d, 50);
        assertEquals(60, d.findById("a").w);
        assertEquals(60, d.findById("b").x);                      // 옆 요소가 함께 밀린다
    }

    @Test
    void multiValueMarginAndPaddingInterpolate() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #a { margin: 0; width: 10; height: 10; transition: margin 100ms linear }
                #a.m { margin: 8 }
              </style>
              <column><panel id='a'/></column>
            </screen>""");
        frame(d, 0);
        d.query("#a").addClass("m");
        frame(d, 0);
        frame(d, 50);
        assertEquals("4", d.findById("a").style.get("margin"));
        assertEquals(4, d.findById("a").x);
    }

    @Test
    void keyframesCanAnimateLayoutProperties() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                @keyframes grow { from { width: 10 } to { width: 110 } }
                #a { height: 10; animation: grow 100ms linear forwards }
              </style>
              <panel id='a' width='1'/>
            </screen>""");
        frame(d, 0);
        frame(d, 50);
        assertEquals(60, d.findById("a").w);
        frame(d, 100);
        assertEquals(110, d.findById("a").w);
    }

    @Test
    void imperativeAnimationOfWidth() throws Exception {
        UiDocument d = doc("<screen><panel id='a' width='20' height='10'/></screen>");
        frame(d, 0);
        d.query("#a").animate().to("width", 120).duration(100).easing("linear").play();
        frame(d, 0);
        frame(d, 50);
        assertEquals(70, d.findById("a").w);
        frame(d, 100);
        frame(d, 150);
        assertEquals(120, d.findById("a").w);                     // 끝 상태가 확정된다
    }

    @Test
    void noAnimationMeansNoPerFrameRelayout() throws Exception {
        UiDocument d = doc("<screen><panel id='a' width='20' height='10'/></screen>");
        frame(d, 0);
        var box = d.findById("a");
        frame(d, 16);
        assertTrue(box == d.findById("a"), "움직이는 게 없으면 매 프레임 재배치하지 않는다");
    }

    @Test
    void lerpHandlesMultiValues() {
        assertEquals("4 6", Interpolator.lerp("0 4", "8 8", 0.5));
        assertEquals("2 4 2 4", Interpolator.lerp("0", "4 8", 0.5));
        assertEquals("50% 10px", Interpolator.lerp("0% 0px", "100% 20px", 0.5));
        assertEquals("0 0 4 #00000080", Interpolator.lerp("0 0 0 #00000000", "0 0 8 #000000FF", 0.5).replace("#00000080", "#00000080"));
        assertFalse(Interpolator.lerp("auto", "100", 0.2).equals("100"));
    }
}
