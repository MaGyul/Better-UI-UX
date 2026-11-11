package dev.magyul.betteruiux.core.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.layout.StyleMap;
import dev.magyul.betteruiux.core.style.Stylesheet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnimationBasicsTest {
    // ---- 이징 ----

    @Test
    void easingEndpointsAndShape() {
        for (String name : List.of("linear", "ease", "ease-in", "ease-out", "ease-in-out", "spring", "cubic-bezier(0.1,0.7,0.3,1)")) {
            Easing e = Easing.parse(name);
            assertEquals(0, e.apply(0), 1e-9, name);
            assertEquals(1, e.apply(1), 1e-9, name);
        }
        assertEquals(0.5, Easing.LINEAR.apply(0.5), 1e-9);
        assertTrue(Easing.parse("ease-in").apply(0.5) < 0.5);
        assertTrue(Easing.parse("ease-out").apply(0.5) > 0.5);
        assertTrue(Easing.SPRING.apply(0.3) > 1, "spring 은 목표를 넘었다가 돌아온다");
        assertEquals(0.5, Easing.parse("nonsense").apply(0.5), 1e-9, "알 수 없는 이름은 linear");
        assertEquals(0.5, Easing.parse("cubic-bezier(1,2)").apply(0.5), 1e-9, "잘못된 형식은 linear");
    }

    // ---- 보간 ----

    @Test
    void interpolatesNumbersColorsAndDiscreteValues() {
        assertEquals("0.5", Interpolator.lerp("0", "1", 0.5));
        assertEquals("10", Interpolator.lerp("0px", "20px", 0.5));
        assertEquals("#808080FF", Interpolator.lerp("#000000FF", "#FFFFFFFF", 0.5).toUpperCase());
        assertEquals("#FF000080", Interpolator.lerp("#FF000000", "#FF0000FF", 0.5).toUpperCase());
        assertEquals("a", Interpolator.lerp("a", "b", 0.4));
        assertEquals("b", Interpolator.lerp("a", "b", 0.6));
        assertEquals("1", Interpolator.lerp("1", "1", 0.3));
        assertEquals("100", Interpolator.lerp("0", "100", 5), "범위를 벗어난 t 는 0..1 로 제한");
    }

    // ---- 파싱 ----

    @Test
    void parsesTransitionList() {
        List<TransitionSpec> t = TransitionSpec.parse("opacity 200ms ease-out 50ms, background .15s, all 1s cubic-bezier(0,0,1,1)");
        assertEquals(3, t.size());
        assertEquals("opacity", t.get(0).property());
        assertEquals(200, t.get(0).durationMs());
        assertEquals(50, t.get(0).delayMs());
        assertEquals(150, t.get(1).durationMs());
        assertTrue(t.get(2).covers("anything"));
        assertTrue(TransitionSpec.parse("none").isEmpty());
        assertTrue(TransitionSpec.parse("opacity").isEmpty(), "지속시간이 없는 항목은 무시");
    }

    @Test
    void parsesAnimationShorthand() {
        AnimationSpec a = AnimationSpec.parse("pulse 1s ease-in-out 200ms infinite alternate forwards");
        assertEquals("pulse", a.name());
        assertEquals(1000, a.durationMs());
        assertEquals(200, a.delayMs());
        assertEquals(Double.POSITIVE_INFINITY, a.iterations());
        assertEquals(AnimationSpec.Direction.ALTERNATE, a.direction());
        assertEquals(AnimationSpec.Fill.FORWARDS, a.fill());
        assertNull(AnimationSpec.parse("none"));
        assertNull(AnimationSpec.parse("pulse"), "지속시간이 없으면 무시");
        assertEquals(3, AnimationSpec.parse("x 100ms 3").iterations());
    }

    @Test
    void animationProgressHandlesDelayIterationsDirectionAndFill() {
        AnimationSpec plain = AnimationSpec.parse("x 1s linear 500ms 2");
        assertEquals(-1, plain.progressAt(100), "지연 중 + fill 없음 → 적용 안 됨");
        assertEquals(0.5, plain.progressAt(1000), 1e-9);
        assertEquals(0.25, plain.progressAt(500 + 1250), 1e-9, "두 번째 반복의 25%");
        assertEquals(-1, plain.progressAt(5000), "끝난 뒤 fill 없음 → 적용 안 됨");
        assertTrue(plain.isFinishedAt(2500));

        AnimationSpec both = AnimationSpec.parse("x 1s linear 500ms 1 both");
        assertEquals(0, both.progressAt(0), 1e-9, "backwards: 지연 중에도 첫 프레임");
        assertEquals(1, both.progressAt(9999), 1e-9, "forwards: 끝난 뒤에도 마지막 프레임");

        AnimationSpec alt = AnimationSpec.parse("x 1s linear infinite alternate");
        assertEquals(0.25, alt.progressAt(250), 1e-9);
        assertEquals(0.75, alt.progressAt(1250), 1e-9, "홀수 번째 반복은 거꾸로");
        assertEquals(0.75, AnimationSpec.parse("x 1s linear reverse").progressAt(250), 1e-9);
        assertEquals(1.0, AnimationSpec.parse("x 1s linear 2 alternate forwards").progressAt(99999), 1e-9 + 1.0,
                "짝수 번 반복한 alternate 는 0 으로 끝난다");
        assertEquals(0.0, AnimationSpec.parse("x 1s linear 2 alternate forwards").progressAt(99999), 1e-9);
        assertEquals(0.5, AnimationSpec.parse("x 1s linear 0.5 forwards").progressAt(99999), 1e-9, "소수 반복은 그 지점에서 멈춘다");
    }

    @Test
    void parsesKeyframesIncludingPercentListsAndWarnings() {
        Stylesheet s = Stylesheet.parse("""
            @keyframes pulse { from { opacity: 0 } 50%, 75% { opacity: 1; scale: 2 } to { opacity: 0 } }
            @keyframes bad { 150% { opacity: 1 } }
            @keyframes 9x { to { opacity: 1 } }
            """);
        Keyframes k = s.keyframes().get("pulse");
        assertEquals(4, k.stops().size());
        assertEquals(0.5, k.stops().get(1).offset());
        assertEquals(0.75, k.stops().get(2).offset());
        // bad: 잘못된 지점 + 지점 없음(2건), 9x: 잘못된 이름(1건)
        assertEquals(3, s.warnings().size());
    }

    @Test
    void keyframesEvaluateWithImplicitEndpointsFromBaseStyle() {
        Keyframes k = Stylesheet.parse("@keyframes k { to { opacity: 0; translate-x: 10 } }").keyframes().get("k");
        StyleMap base = new StyleMap();
        base.put("opacity", "0.8");
        Map<String, String> half = k.evaluate(base, 0.5, Easing.LINEAR);
        assertEquals("0.4", half.get("opacity"), "암묵적 from 은 요소의 기본 스타일 값");
        assertEquals("5", half.get("translate-x"), "기본 스타일에 없으면 속성 기본값(0)에서 출발");
        assertEquals("10", k.evaluate(base, 1.0, Easing.LINEAR).get("translate-x"));
        assertTrue(Stylesheet.parse("@keyframes k { to { width: 5 } }").keyframes().get("k")
                .evaluate(base, 0.5, Easing.LINEAR).isEmpty(), "레이아웃 속성은 애니메이션하지 않는다");
    }
}
