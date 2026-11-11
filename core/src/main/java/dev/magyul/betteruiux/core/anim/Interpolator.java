package dev.magyul.betteruiux.core.anim;

import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.style.CssMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스타일 값(문자열) 보간. 색상은 채널별, 숫자는 선형, 공백으로 나뉜 여러 값({@code margin: 4 8}, {@code box-shadow})은 항목별,
 * 그 외는 50% 지점에서 값이 바뀐다(이산).
 */
public final class Interpolator {
    private Interpolator() {}

    /** 값이 없을 때 쓰는 기본값(전환/키프레임의 암묵적 시작·끝). 색상류는 기본값이 없다. */
    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("opacity", "1"), Map.entry("translate-x", "0"), Map.entry("translate-y", "0"),
            Map.entry("scale", "1"), Map.entry("rotate", "0"),
            Map.entry("margin", "0"), Map.entry("padding", "0"), Map.entry("gap", "0"), Map.entry("row-gap", "0"),
            Map.entry("column-gap", "0"), Map.entry("x", "0"), Map.entry("y", "0"), Map.entry("font-size", "8"),
            Map.entry("letter-spacing", "0"), Map.entry("border-radius", "0"), Map.entry("outline-width", "0"),
            Map.entry("outline-offset", "0"), Map.entry("min-width", "0"), Map.entry("min-height", "0"));

    /** 그리기 때 적용되는 전환/애니메이션 속성(레이아웃을 바꾸지 않는다). */
    public static final Set<String> ANIMATABLE = Set.of(
            "opacity", "translate-x", "translate-y", "scale", "rotate",
            "background", "color", "border-color", "tint", "border",
            "border-radius", "box-shadow", "outline-color", "outline-width", "outline-offset");

    /** 레이아웃을 바꾸는 전환/애니메이션 속성: 움직이는 동안 매 프레임 다시 배치한다. */
    public static final Set<String> LAYOUT = Set.of(
            "width", "height", "min-width", "max-width", "min-height", "max-height", "margin", "padding",
            "gap", "row-gap", "column-gap", "x", "y", "font-size", "letter-spacing", "line-height");

    public static String lerp(String a, String b, double t) {
        if (a.equals(b)) return b;
        t = Math.max(0, Math.min(1, t));
        Integer ca = Colors.tryParse(a), cb = Colors.tryParse(b);
        if (ca != null && cb != null) return color(ca, cb, t);
        Double na = number(a), nb = number(b);
        if (na != null && nb != null) return format(na + (nb - na) * t);
        String multi = multi(a, b, t);
        if (multi != null) return multi;
        return t < 0.5 ? a : b;
    }

    /** 공백으로 나뉜 값을 항목별로 보간한다(개수가 다르면 1~4값을 CSS 규칙대로 4개로 펼쳐 맞춘다). 못 하면 null. */
    private static String multi(String a, String b, double t) {
        List<String> ta = CssMath.tokens(a), tb = CssMath.tokens(b);
        if (ta.size() < 2 && tb.size() < 2) return null;
        if (ta.size() != tb.size()) {
            if (ta.size() > 4 || tb.size() > 4) return null;
            ta = expand4(ta);
            tb = expand4(tb);
            if (ta == null || tb == null) return null;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ta.size(); i++) {
            String x = ta.get(i), y = tb.get(i);
            Integer cx = Colors.tryParse(x), cy = Colors.tryParse(y);
            if (cx != null && cy != null) {
                out.add(color(cx, cy, t));
                continue;
            }
            Double nx = unitNumber(x), ny = unitNumber(y);
            String ux = unit(x), uy = unit(y);
            if (nx == null || ny == null || !ux.equals(uy)) {
                if (!x.equals(y)) return null;                       // 항목 하나라도 보간 불가면 전체를 이산으로
                out.add(x);
                continue;
            }
            out.add(format(nx + (ny - nx) * t) + ux);
        }
        return String.join(" ", out);
    }

    private static List<String> expand4(List<String> t) {
        for (String s : t) if (Colors.tryParse(s) != null || unitNumber(s) == null) return null;       // 색이 섞인 값은 펼치지 않는다
        return switch (t.size()) {
            case 1 -> List.of(t.get(0), t.get(0), t.get(0), t.get(0));
            case 2 -> List.of(t.get(0), t.get(1), t.get(0), t.get(1));
            case 3 -> List.of(t.get(0), t.get(1), t.get(2), t.get(1));
            case 4 -> t;
            default -> null;
        };
    }

    private static String color(int a, int b, double t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (int) Math.round(ca + (cb - ca) * t) << shift;
        }
        return Colors.toCssFull(out);
    }

    private static Double number(String s) {
        try {
            return Double.parseDouble(s.trim().replace("px", "").replace("deg", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 숫자 + 선택적 단위({@code px % deg}). */
    private static Double unitNumber(String s) {
        String u = unit(s);
        try {
            return Double.parseDouble(s.substring(0, s.length() - u.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unit(String s) {
        String t = s.trim();
        if (t.endsWith("px")) return "px";
        if (t.endsWith("deg")) return "deg";
        if (t.endsWith("%")) return "%";
        return "";
    }

    private static String format(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String f = String.format(java.util.Locale.ROOT, "%.4f", v);
        return f.replaceAll("0+$", "").replaceAll("\\.$", "");   // 0.5000 → 0.5
    }
}
