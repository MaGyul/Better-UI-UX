package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class MultiAnimationTest {
    private long now;

    private UiDocument doc(String style) throws Exception {
        UiDocument d = UiDocument.parse("<screen><style>@keyframes slide { from { width: 100 } to { width: 200 } } "
                + "@keyframes tall { from { height: 10 } to { height: 50 } } #a { " + style + " }</style><panel id='a'/></screen>", new FixedMetrics());
        d.setClock(() -> now);
        d.layout(300, 200);
        return d;
    }

    private void frame(UiDocument d, long t) {
        now = t;
        d.paint(new DrawList(), -1, -1);
    }

    @Test
    void severalAnimationsRunTogether() throws Exception {
        UiDocument d = doc("animation: slide 100ms linear forwards, tall 200ms linear forwards;");
        frame(d, 0);
        frame(d, 50);
        assertEquals(150, d.findById("a").w);
        assertEquals(20, d.findById("a").h);
        frame(d, 100);
        assertEquals(200, d.findById("a").w);
        assertEquals(30, d.findById("a").h);
        frame(d, 200);
        assertEquals(200, d.findById("a").w, "forwards 라 끝값 유지");
        assertEquals(50, d.findById("a").h);
    }

    @Test
    void laterAnimationWinsOnTheSameProperty() throws Exception {
        UiDocument d = doc("animation: slide 100ms linear forwards, slide 100ms steps(1, jump-start) forwards;");
        frame(d, 0);
        frame(d, 10);
        assertEquals(200, d.findById("a").w, "뒤의 애니메이션(점프)이 덮어쓴다");
    }

    @Test
    void pausedFreezesAndResumesFromTheSamePoint() throws Exception {
        UiDocument d = doc("animation: slide 100ms linear forwards;");
        frame(d, 0);
        frame(d, 40);
        assertEquals(140, d.findById("a").w);
        d.query("#a").setStyle("animation-play-state", "paused");
        frame(d, 40);
        frame(d, 500);
        assertEquals(140, d.findById("a").w, "멈추면 시간이 흘러도 그대로");
        d.query("#a").setStyle("animation-play-state", "running");
        frame(d, 500);
        frame(d, 530);
        assertEquals(170, d.findById("a").w, "멈춘 자리(40ms)에서 30ms 더 진행");
    }
}
