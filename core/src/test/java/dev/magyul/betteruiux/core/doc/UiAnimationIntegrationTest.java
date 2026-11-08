package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class UiAnimationIntegrationTest {
    /** 테스트가 시간을 직접 움직이는 시계. */
    private static final class ManualClock implements UiClock {
        long now;

        @Override public long nowMillis() { return now; }
    }

    private final ManualClock clock = new ManualClock();

    private UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(clock);
        d.layout(200, 100);
        return d;
    }

    private List<DrawCommand> paint(UiDocument d, long t, int mx, int my) {
        clock.now = t;
        DrawList l = new DrawList();
        d.paint(l, mx, my);
        return l.commands();
    }

    private static int textColor(List<DrawCommand> c, String text) {
        return c.stream().filter(x -> x instanceof DrawCommand.DrawText t && t.text().equals(text))
                .map(x -> ((DrawCommand.DrawText) x).argb()).findFirst().orElseThrow();
    }

    // ---- transition ----

    @Test
    void hoverTransitionInterpolatesColorOverTime() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                button { background: #FF000000; transition: background 200ms linear }
                button:hover { background: #FFFFFFFF }
              </style>
              <button id='b' text='x' width='40' height='20'/>
            </screen>""");
        var b = d.query("#b");
        int hx = b.x() + 2, hy = b.y() + 2;
        DrawCommand.FillRect black = new DrawCommand.FillRect(b.x(), b.y(), b.width(), b.height(), 0xFF000000);

        assertTrue(paint(d, 0, -1, -1).contains(black), "첫 프레임은 전환 없이 기본값");
        assertTrue(paint(d, 100, hx, hy).contains(black), "호버 직후(경과 0)는 아직 시작 색");
        assertTrue(paint(d, 200, hx, hy).contains(
                new DrawCommand.FillRect(b.x(), b.y(), b.width(), b.height(), 0xFF808080)), "100ms 경과 = 중간 색");
        assertTrue(paint(d, 400, hx, hy).contains(
                new DrawCommand.FillRect(b.x(), b.y(), b.width(), b.height(), 0xFFFFFFFF)), "전환 완료");

        paint(d, 500, -1, -1);   // 호버 해제 → 흰색에서 검정으로 되돌아가기 시작
        assertTrue(paint(d, 600, -1, -1).contains(
                new DrawCommand.FillRect(b.x(), b.y(), b.width(), b.height(), 0xFF808080)));
    }

    @Test
    void transitionStartsFromCurrentValueWhenInterrupted() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                label { transition: opacity 100ms linear }
                label:hover { opacity: 0 }
              </style>
              <label id='l' text='hello' width='60' height='20'/>
            </screen>""");
        var l = d.query("#l");
        paint(d, 0, -1, -1);
        paint(d, 10, l.x() + 1, l.y() + 1);                                   // 호버 시작(1 → 0)
        assertEquals(0x80FFFFFF, textColor(paint(d, 60, l.x() + 1, l.y() + 1), "hello"), "절반(0.5)");
        paint(d, 60, -1, -1);                                                 // 절반에서 호버 해제
        int c = textColor(paint(d, 110, -1, -1), "hello");                    // 0.5 → 1 로 50ms 진행 = 0.75
        assertEquals(0xBFFFFFFF, c);
    }

    // ---- @keyframes ----

    @Test
    void keyframeAnimationRunsThenReleasesWithoutFill() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                @keyframes fade { from { opacity: 0 } to { opacity: 1 } }
                label { animation: fade 1s linear }
              </style>
              <label id='l' text='hello'/>
            </screen>""");
        assertEquals(0x00FFFFFF, textColor(paint(d, 0, -1, -1), "hello"));
        assertEquals(0x80FFFFFF, textColor(paint(d, 500, -1, -1), "hello"));
        assertEquals(0xFFFFFFFF, textColor(paint(d, 1500, -1, -1), "hello"), "끝난 뒤(fill 없음)는 원래 스타일");
    }

    @Test
    void infiniteAlternateAnimationLoopsAndUnknownKeyframesAreIgnored() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                @keyframes blink { from { opacity: 0 } to { opacity: 1 } }
                .a { animation: blink 1s linear infinite alternate }
                .b { animation: nothere 1s linear }
              </style>
              <label id='a' class='a' text='A'/>
              <label id='b' class='b' text='B' y='20'/>
            </screen>""");
        paint(d, 0, -1, -1);
        List<DrawCommand> c = paint(d, 1250, -1, -1);     // 두 번째 반복(역방향)의 25% → 진행도 0.75
        assertEquals(0xBFFFFFFF, textColor(c, "A"));
        assertEquals(0xFFFFFFFF, textColor(c, "B"), "없는 @keyframes 이름은 무시");
    }

    @Test
    void changingAnimationValueRestartsIt() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                @keyframes fade { from { opacity: 0 } to { opacity: 1 } }
                label { animation: fade 1s linear }
              </style>
              <label id='l' text='hello'/>
            </screen>""");
        paint(d, 0, -1, -1);
        paint(d, 800, -1, -1);
        d.query("#l").setStyle("animation", "fade 2s linear");           // 다른 값 → 처음부터 다시
        assertEquals(0x00FFFFFF, textColor(paint(d, 900, -1, -1), "hello"));
        assertEquals(0x80FFFFFF, textColor(paint(d, 1900, -1, -1), "hello"));
    }

    // ---- transform / opacity ----

    @Test
    void transformStylesBecomePushPopTransformAroundBoxAndChildren() throws Exception {
        UiDocument d = doc("<screen><panel width='20' height='10' style='scale: 2; translate-x: 5; rotate: 90'>"
                + "<panel width='4' height='4' style='background: #FFFFFFFF'/></panel></screen>");
        List<DrawCommand> c = paint(d, 0, -1, -1);
        assertEquals(new DrawCommand.PushTransform(5, 0, 2, 90, 10, 5), c.get(0));
        assertTrue(c.get(1) instanceof DrawCommand.FillRect, "자식도 변환 안에서 그려진다");
        assertEquals(new DrawCommand.PopTransform(), c.get(c.size() - 1));
        assertFalse(paint(d, 0, -1, -1).isEmpty());
    }

    @Test
    void opacityIsInheritedMultiplicatively() throws Exception {
        UiDocument d = doc("<screen><panel width='10' height='10' style='opacity: 0.5; background: #FFFFFFFF'>"
                + "<panel width='5' height='5' style='opacity: 0.5; background: #FFFFFFFF'/></panel></screen>");
        List<DrawCommand> c = paint(d, 0, -1, -1);
        assertEquals(0x80FFFFFF, ((DrawCommand.FillRect) c.get(0)).argb());
        assertEquals(0x40FFFFFF, ((DrawCommand.FillRect) c.get(1)).argb(), "0.5 × 0.5 = 0.25");
    }

    // ---- Java API ----

    @Test
    void imperativeAnimationRunsCommitsFinalStateAndCallsOnFinish() throws Exception {
        UiDocument d = doc("<screen><label id='l' text='hello'/></screen>");
        var l = d.query("#l");
        AtomicBoolean finished = new AtomicBoolean();
        paint(d, 0, -1, -1);
        var handle = l.animate().from("opacity", 1).to("opacity", 0).duration(200).easing("linear")
                .onFinish(() -> finished.set(true)).play();
        assertTrue(handle.isRunning());
        assertEquals(0x80FFFFFF, textColor(paint(d, 100, -1, -1), "hello"));
        assertFalse(finished.get());

        assertEquals(0x00FFFFFF, textColor(paint(d, 300, -1, -1), "hello"));
        assertTrue(finished.get());
        assertFalse(handle.isRunning());
        assertTrue(l.attr("style").contains("opacity: 0"), "끝 상태가 인라인 스타일로 확정");
        assertEquals(0x00FFFFFF, textColor(paint(d, 400, -1, -1), "hello"), "끝난 뒤에도 유지");
    }

    @Test
    void cancelledAnimationDoesNotCommitAndNewOneReplacesOldOnSameProperty() throws Exception {
        UiDocument d = doc("<screen><label id='l' text='hello'/></screen>");
        var l = d.query("#l");
        paint(d, 0, -1, -1);
        var first = l.animate().to("opacity", 0).duration(100).play();
        first.cancel();
        paint(d, 500, -1, -1);
        assertEquals(null, l.attr("style"), "취소된 애니메이션은 확정되지 않는다");

        var a = l.animate().to("opacity", 0).duration(1000).play();
        var b = l.animate().to("opacity", 0.5).duration(1000).play();
        assertFalse(a.isRunning(), "같은 속성의 이전 애니메이션은 대체된다");
        assertTrue(b.isRunning());
    }

    @Test
    void animateValidatesProperties() throws Exception {
        UiDocument d = doc("<screen><label id='l' text='x'/></screen>");
        var l = d.query("#l");
        assertThrows(IllegalArgumentException.class, () -> l.animate().to("width", 10));
        assertThrows(IllegalStateException.class, () -> l.animate().play());
    }
}
