package dev.magyul.betteruiux.core.anim;

import dev.magyul.betteruiux.core.layout.StyleMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code @keyframes 이름 { from {..} 50% {..} to {..} }}. */
public record Keyframes(String name, List<Stop> stops) {
    /** @param offset 0..1 */
    public record Stop(double offset, Map<String, String> props) {}

    /** 키프레임 이름이 가리키는 속성들의 진행도 p(0..1) 시점의 값. 구간마다 {@code easing} 을 적용한다. */
    public Map<String, String> evaluate(StyleMap base, double p, Easing easing) {
        return evaluate(base, p, easing, Interpolator.ANIMATABLE);
    }

    /** @param allowed 이 호출에서 계산할 속성들(그리기 속성 / 레이아웃 속성을 나누어 처리하려고) */
    public Map<String, String> evaluate(StyleMap base, double p, Easing easing, Set<String> allowed) {
        Set<String> props = new LinkedHashSet<>();
        for (Stop s : stops) props.addAll(s.props().keySet());
        Map<String, String> out = new LinkedHashMap<>();
        for (String prop : props) {
            if (!allowed.contains(prop)) continue;
            List<double[]> offsets = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (Stop s : stops) {
                String v = s.props().get(prop);
                if (v != null) {
                    offsets.add(new double[] {s.offset()});
                    values.add(v);
                }
            }
            String implicit = base.get(prop) != null ? base.get(prop) : Interpolator.DEFAULTS.get(prop);
            if (offsets.get(0)[0] > 0 && implicit != null) {      // 암묵적 시작(from)
                offsets.add(0, new double[] {0});
                values.add(0, implicit);
            }
            if (offsets.get(offsets.size() - 1)[0] < 1 && implicit != null) {   // 암묵적 끝(to)
                offsets.add(new double[] {1});
                values.add(implicit);
            }
            out.put(prop, sample(offsets, values, p, easing));
        }
        return out;
    }

    private static String sample(List<double[]> offsets, List<String> values, double p, Easing easing) {
        if (p <= offsets.get(0)[0]) return values.get(0);
        for (int i = 0; i < offsets.size() - 1; i++) {
            double o0 = offsets.get(i)[0], o1 = offsets.get(i + 1)[0];
            if (p <= o1) {
                double local = o1 == o0 ? 1 : (p - o0) / (o1 - o0);
                return Interpolator.lerp(values.get(i), values.get(i + 1), easing.apply(local));
            }
        }
        return values.get(values.size() - 1);
    }

    /** 파서용: 정렬된 정지점 목록으로 만든다. */
    public static Keyframes of(String name, List<Stop> stops) {
        List<Stop> sorted = new ArrayList<>(stops);
        sorted.sort(Comparator.comparingDouble(Stop::offset));
        return new Keyframes(name, List.copyOf(sorted));
    }
}
