package dev.magyul.betteruiux.core.anim;

import dev.magyul.betteruiux.core.layout.Colors;
import java.util.Map;

/** 스타일 값(문자열) 보간. 색상은 채널별, 숫자는 선형, 그 외는 50% 지점에서 값이 바뀐다(이산). */
public final class Interpolator {
    private Interpolator() {}

    /** 값이 없을 때 쓰는 기본값(전환/키프레임의 암묵적 시작·끝). 색상류는 기본값이 없다. */
    public static final Map<String, String> DEFAULTS = Map.of(
            "opacity", "1", "translate-x", "0", "translate-y", "0", "scale", "1", "rotate", "0");

    /** 전환/애니메이션 대상이 될 수 있는 그리기 속성(레이아웃에 영향을 주는 속성은 제외). */
    public static final java.util.Set<String> ANIMATABLE = java.util.Set.of(
            "opacity", "translate-x", "translate-y", "scale", "rotate",
            "background", "color", "border-color", "tint", "border");

    public static String lerp(String a, String b, double t) {
        if (a.equals(b)) return b;
        t = Math.max(0, Math.min(1, t));
        Integer ca = Colors.tryParse(a), cb = Colors.tryParse(b);
        if (ca != null && cb != null) return color(ca, cb, t);
        Double na = number(a), nb = number(b);
        if (na != null && nb != null) return format(na + (nb - na) * t);
        return t < 0.5 ? a : b;
    }

    private static String color(int a, int b, double t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (int) Math.round(ca + (cb - ca) * t) << shift;
        }
        return String.format("#%08X", out);
    }

    private static Double number(String s) {
        try {
            return Double.parseDouble(s.trim().replace("px", "").replace("deg", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String f = String.format(java.util.Locale.ROOT, "%.4f", v);
        return f.replaceAll("0+$", "").replaceAll("\\.$", "");   // 0.5000 → 0.5
    }
}
