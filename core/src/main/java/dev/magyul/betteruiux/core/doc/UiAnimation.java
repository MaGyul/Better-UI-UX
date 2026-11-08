package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.core.anim.AnimationEngine;
import dev.magyul.betteruiux.core.anim.Easing;
import dev.magyul.betteruiux.core.anim.Interpolator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java 에서 요소를 직접 움직이는 빌더. {@code element.animate().to("opacity", 1).duration(200).play()}.
 * 애니메이션이 끝나면 끝 상태가 요소의 인라인 스타일로 확정된다. 움직일 수 있는 속성은
 * {@code opacity translate-x translate-y scale rotate background color border-color tint border}.
 */
public final class UiAnimation {
    private final UiDocument doc;
    private final UiElement element;
    private final Map<String, String> from = new LinkedHashMap<>();
    private final Map<String, String> to = new LinkedHashMap<>();
    private double duration = 300, delay = 0;
    private Easing easing = Easing.parse("ease");
    private Runnable onFinish;

    UiAnimation(UiDocument doc, UiElement element) {
        this.doc = doc;
        this.element = element;
    }

    /** 끝 값. 숫자는 {@code 1}, {@code 0.5}, 색은 {@code "#FF8800"} 처럼 쓴다. */
    public UiAnimation to(String prop, Object value) {
        to.put(check(prop), text(value));
        return this;
    }

    /** 시작 값(생략하면 지금 값에서 출발). */
    public UiAnimation from(String prop, Object value) {
        from.put(check(prop), text(value));
        return this;
    }

    public UiAnimation duration(double millis) {
        this.duration = Math.max(0, millis);
        return this;
    }

    public UiAnimation delay(double millis) {
        this.delay = Math.max(0, millis);
        return this;
    }

    /** {@code linear ease ease-in ease-out ease-in-out cubic-bezier(a,b,c,d) spring}. */
    public UiAnimation easing(String name) {
        this.easing = Easing.parse(name);
        return this;
    }

    public UiAnimation onFinish(Runnable r) {
        this.onFinish = r;
        return this;
    }

    /** @return 취소/상태 조회용 핸들 */
    public AnimationEngine.Imperative play() {
        if (to.isEmpty()) throw new IllegalStateException("to(...) 로 끝 값을 하나 이상 지정하세요");
        return doc.startAnimation(element, from, to, duration, delay, easing, onFinish);
    }

    private static String check(String prop) {
        String p = prop.toLowerCase();
        if (!Interpolator.ANIMATABLE.contains(p)) {
            throw new IllegalArgumentException("애니메이션할 수 없는 속성: " + prop + " (가능: " + Interpolator.ANIMATABLE + ")");
        }
        return p;
    }

    private static String text(Object v) {
        if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
        }
        return String.valueOf(v);
    }
}
