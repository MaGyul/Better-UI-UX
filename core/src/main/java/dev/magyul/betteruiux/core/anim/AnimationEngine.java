package dev.magyul.betteruiux.core.anim;

import dev.magyul.betteruiux.core.layout.StyleMap;
import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 문서 하나의 애니메이션 상태. 그릴 때마다 {@link #apply}가 "스타일시트가 정한 목표 스타일"에
 * ① 전환(transition) ② {@code @keyframes} 애니메이션 ③ Java API 로 시작한 명령형 애니메이션을 차례로 덧입힌 스타일을 돌려준다.
 * 상태는 노드 단위로 보관하므로 재레이아웃/스타일 재계산 후에도 진행 중인 애니메이션이 이어진다.
 */
public final class AnimationEngine {
    /** 전환 한 건의 진행 상태. */
    private static final class Transition {
        String target;          // 마지막으로 본 목표값
        String from, to;
        long start;             // 지연 포함 시작 시각
        TransitionSpec spec;

        String valueAt(long now) {
            if (spec == null || from == null || to == null) return target;
            double elapsed = now - start;
            if (elapsed <= 0) return from;
            if (elapsed >= spec.durationMs()) return to;
            return Interpolator.lerp(from, to, spec.easing().apply(elapsed / spec.durationMs()));
        }
    }

    /** 노드의 {@code animation} 진행 상태. */
    private static final class KeyframeRun {
        String signature;       // animation 속성 값이 바뀌면 처음부터 다시 시작
        long start;
    }

    /** Java API 로 시작한 애니메이션. */
    public static final class Imperative {
        final UiNode node;
        final Map<String, String> from, to;
        final double durationMs, delayMs;
        final Easing easing;
        final Runnable onFinish;
        final java.util.function.BiConsumer<String, String> commit;
        long start;
        boolean cancelled, finished;

        Imperative(UiNode node, Map<String, String> from, Map<String, String> to, double durationMs, double delayMs,
                   Easing easing, Runnable onFinish, java.util.function.BiConsumer<String, String> commit) {
            this.node = node;
            this.from = from;
            this.to = to;
            this.durationMs = durationMs;
            this.delayMs = delayMs;
            this.easing = easing;
            this.onFinish = onFinish;
            this.commit = commit;
        }

        public void cancel() { cancelled = true; }

        public boolean isRunning() { return !cancelled && !finished; }
    }

    private final Function<String, Keyframes> keyframeLookup;
    private final Map<UiNode, Map<String, Transition>> transitions = new IdentityHashMap<>();
    private final Map<UiNode, KeyframeRun> runs = new IdentityHashMap<>();
    private final List<Imperative> imperatives = new ArrayList<>();
    private final Map<String, List<TransitionSpec>> transitionCache = new HashMap<>();
    private final Map<String, AnimationSpec> animationCache = new HashMap<>();

    public AnimationEngine(Function<String, Keyframes> keyframeLookup) { this.keyframeLookup = keyframeLookup; }

    /** 진행 중인 애니메이션/전환이 하나라도 있는지(가장 최근 {@link #apply} 기준이 아니라 현재 등록 상태). */
    public boolean hasActiveImperative() { return !imperatives.isEmpty(); }

    /** 명령형 애니메이션을 등록한다. {@code from} 에 없는 속성은 {@code currentValue} 에서 읽는다. */
    public Imperative start(UiNode node, Map<String, String> from, Map<String, String> to,
                            double durationMs, double delayMs, Easing easing, long now,
                            Function<String, String> currentValue, Runnable onFinish,
                            java.util.function.BiConsumer<String, String> commit) {
        Map<String, String> resolvedFrom = new HashMap<>();
        for (String prop : to.keySet()) {
            String f = from.get(prop);
            if (f == null) f = currentValue.apply(prop);
            if (f == null) f = Interpolator.DEFAULTS.get(prop);
            resolvedFrom.put(prop, f == null ? to.get(prop) : f);
        }
        Imperative a = new Imperative(node, resolvedFrom, new HashMap<>(to), durationMs, delayMs, easing, onFinish, commit);
        a.start = now;
        imperatives.removeIf(old -> {
            if (old.node == node && old.to.keySet().stream().anyMatch(to::containsKey)) {
                old.cancelled = true;   // 같은 속성을 움직이는 이전 애니메이션은 대체된다
                return true;
            }
            return false;
        });
        imperatives.add(a);
        return a;
    }

    private boolean layoutAnimating;

    /** 레이아웃 패스를 시작한다(움직이는 레이아웃 속성이 있는지 다시 모은다). */
    public void beginLayoutPass() { layoutAnimating = false; }

    /** 마지막 레이아웃 패스에서 레이아웃 속성이 아직 움직이는 중이었는지 → 다음 프레임에도 다시 배치해야 한다. */
    public boolean layoutActive() { return layoutAnimating; }

    /** 그리기 속성({@link Interpolator#ANIMATABLE})에 전환·애니메이션을 덧입힌다. @return 덧입혀지지 않았으면 {@code target} 그대로, 아니면 복사본. */
    public StyleMap apply(UiNode node, StyleMap target, long now) {
        return run(node, target, now, Interpolator.ANIMATABLE, false);
    }

    /** 레이아웃 속성({@link Interpolator#LAYOUT})에 전환·애니메이션을 덧입힌다(레이아웃 패스에서 호출). */
    public StyleMap applyLayout(UiNode node, StyleMap target, long now) {
        return run(node, target, now, Interpolator.LAYOUT, true);
    }

    private StyleMap run(UiNode node, StyleMap target, long now, Set<String> props, boolean layoutPass) {
        StyleMap out = target;
        out = applyTransitions(node, target, out, now, props, layoutPass);
        out = applyKeyframes(node, target, out, now, props, layoutPass);
        out = applyImperatives(node, out, now, props, layoutPass);
        return out;
    }

    // ---- ① 전환 ----

    private StyleMap applyTransitions(UiNode node, StyleMap target, StyleMap out, long now, Set<String> props, boolean layoutPass) {
        String spec = target.get("transition");
        if (spec == null) {
            transitions.remove(node);
            return out;
        }
        List<TransitionSpec> specs = transitionCache.computeIfAbsent(spec, TransitionSpec::parse);
        if (specs.isEmpty()) return out;
        Map<String, Transition> states = transitions.computeIfAbsent(node, n -> new HashMap<>());
        for (String prop : props) {
            TransitionSpec ts = null;
            for (TransitionSpec s : specs) if (s.covers(prop)) ts = s;   // 뒤에 오는 항목이 우선
            if (ts == null) continue;
            String tv = target.get(prop) != null ? target.get(prop) : Interpolator.DEFAULTS.get(prop);
            if (tv == null) continue;
            Transition t = states.get(prop);
            if (t == null) {
                t = new Transition();
                t.target = tv;
                states.put(prop, t);
                continue;
            }
            if (!tv.equals(t.target)) {
                String current = t.valueAt(now);
                t.from = current;
                t.to = tv;
                t.target = tv;
                t.spec = ts;
                t.start = now + (long) ts.delayMs();
            }
            String shown = t.valueAt(now);
            if (!shown.equals(tv)) {
                if (out == target) out = target.copy();
                out.put(prop, shown);
                if (layoutPass) layoutAnimating = true;
            }
        }
        return out;
    }

    // ---- ② @keyframes ----

    private StyleMap applyKeyframes(UiNode node, StyleMap target, StyleMap out, long now, Set<String> props, boolean layoutPass) {
        String value = target.get("animation");
        AnimationSpec spec = value == null ? null : animationCache.computeIfAbsent(value, v -> {
            AnimationSpec s = AnimationSpec.parse(v);
            return s == null ? NONE : s;
        });
        if (spec == null || spec == NONE) {
            runs.remove(node);
            return out;
        }
        Keyframes kf = keyframeLookup.apply(spec.name());
        if (kf == null) return out;
        KeyframeRun run = runs.get(node);
        if (run == null || !value.equals(run.signature)) {
            run = new KeyframeRun();
            run.signature = value;
            run.start = now;
            runs.put(node, run);
        }
        double p = spec.progressAt(now - run.start);
        if (p < 0) return out;
        Map<String, String> values = kf.evaluate(target, p, spec.easing(), props);
        if (values.isEmpty()) return out;
        if (out == target) out = target.copy();
        out.entries().putAll(values);
        if (layoutPass) layoutAnimating = true;
        return out;
    }

    private static final AnimationSpec NONE =
            new AnimationSpec("", 1, 0, Easing.LINEAR, 1, AnimationSpec.Direction.NORMAL, AnimationSpec.Fill.NONE);

    // ---- ③ 명령형 ----

    private StyleMap applyImperatives(UiNode node, StyleMap out, long now, Set<String> props, boolean layoutPass) {
        if (imperatives.isEmpty()) return out;
        StyleMap result = out;
        List<Imperative> done = null;
        for (Iterator<Imperative> it = imperatives.iterator(); it.hasNext();) {
            Imperative a = it.next();
            if (a.cancelled) {
                it.remove();
                continue;
            }
            if (a.node != node) continue;
            double elapsed = now - a.start - a.delayMs;
            double p = elapsed <= 0 ? 0 : a.durationMs <= 0 ? 1 : Math.min(1, elapsed / a.durationMs);
            for (Map.Entry<String, String> e : a.to.entrySet()) {
                if (!props.contains(e.getKey())) continue;
                if (result == out) result = out.copy();
                result.put(e.getKey(), Interpolator.lerp(a.from.get(e.getKey()), e.getValue(), a.easing.apply(p)));
                if (layoutPass && p < 1) layoutAnimating = true;
            }
            if (layoutPass) continue;                        // 끝내기(확정·콜백)는 매 프레임 도는 그리기 패스에서만
            if (p >= 1) {
                a.finished = true;
                if (done == null) done = new ArrayList<>();
                done.add(a);
                it.remove();
            }
        }
        if (done != null) {
            for (Imperative a : done) {
                // 끝 상태를 인라인 스타일에 확정해 애니메이션이 끝난 뒤에도 유지되게 한다.
                a.to.forEach(a.commit);
                if (a.onFinish != null) a.onFinish.run();
            }
        }
        return result;
    }
}
