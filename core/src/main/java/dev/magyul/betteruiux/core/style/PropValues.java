package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.layout.Colors;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 속성 값의 간단한 타입 검사(경고용). 알려진 속성의 값이 명백히 잘못됐을 때만 알려 주고, 값은 그대로 저장한다.
 * {@code var() calc() min() max() clamp()} 처럼 해석을 미루는 값, {@code inherit/initial/unset} 은 검사하지 않는다.
 */
final class PropValues {
    private PropValues() {}

    private enum Type { LENGTH, NUMBER, COLOR, ENUM }

    private static final Pattern LENGTH = Pattern.compile("-?([0-9]+[.]?[0-9]*|[.][0-9]+)(px|%|em|rem|vw|vh|vmin|vmax|pt|deg|s|ms)?");
    private static final Set<String> GLOBAL = Set.of("inherit", "initial", "unset", "revert");
    private static final Set<String> LENGTH_WORDS = Set.of("auto", "fill", "none", "normal", "min-content", "max-content", "thin", "medium", "thick");
    private static final Set<String> COLOR_WORDS = Set.of("none", "transparent", "currentcolor");

    private static final Map<String, Type> TYPES = new java.util.HashMap<>();
    private static final Map<String, Set<String>> ENUMS = new java.util.HashMap<>();

    static {
        for (String p : new String[] {"width", "height", "min-width", "max-width", "min-height", "max-height", "gap", "row-gap", "column-gap",
                "top", "right", "bottom", "left", "x", "y", "flex-basis", "font-size", "line-height", "letter-spacing", "outline-width",
                "outline-offset", "translate-x", "translate-y", "border-top-width", "border-right-width", "border-bottom-width", "border-left-width"}) {
            TYPES.put(p, Type.LENGTH);
        }
        for (String p : new String[] {"opacity", "z-index", "order", "grow", "flex-grow", "flex-shrink", "scale", "rotate", "aspect-ratio"}) {
            TYPES.put(p, Type.NUMBER);
        }
        for (String p : new String[] {"color", "background-color", "border-color", "border-top-color", "border-right-color", "border-bottom-color",
                "border-left-color", "outline-color", "tint", "hover-background", "pressed-background", "disabled-background", "bar-color",
                "fill-color", "check-color"}) {
            TYPES.put(p, Type.COLOR);
        }
        enumOf("position", "static", "relative", "absolute", "fixed", "sticky");
        enumOf("display", "block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "none", "contents");
        enumOf("flex-wrap", "nowrap", "wrap", "wrap-reverse");
        enumOf("flex-direction", "row", "row-reverse", "column", "column-reverse");
        enumOf("overflow", "visible", "hidden", "scroll", "auto", "clip");
        enumOf("box-sizing", "border-box", "content-box");
        enumOf("text-align", "left", "right", "center", "start", "end", "justify");
        enumOf("white-space", "normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces");
        enumOf("text-transform", "none", "uppercase", "lowercase", "capitalize");
        enumOf("font-style", "normal", "italic", "oblique");
        enumOf("animation-play-state", "running", "paused");
        enumOf("vertical-align", "top", "middle", "center", "bottom", "baseline");
        enumOf("border-style", "none", "solid", "dashed", "dotted", "double", "hidden");
        enumOf("outline-style", "none", "solid", "dashed", "dotted", "double", "hidden");
        enumOf("text-overflow", "clip", "ellipsis");
        enumOf("align-content", "start", "end", "center", "flex-start", "flex-end", "stretch", "space-between", "space-around", "space-evenly", "normal");
    }

    private static void enumOf(String prop, String... values) {
        TYPES.put(prop, Type.ENUM);
        ENUMS.put(prop, Set.of(values));
    }

    /** 값이 명백히 잘못됐으면 사람이 읽을 사유, 아니면 null. */
    static String check(String prop, String rawValue) {
        Type t = TYPES.get(prop);
        if (t == null || rawValue == null) return null;
        String v = rawValue.trim();
        String low = v.toLowerCase(Locale.ROOT);
        if (low.endsWith("!important")) low = low.substring(0, low.length() - 10).trim();
        if (low.isEmpty() || GLOBAL.contains(low) || low.contains("var(") || low.contains("calc(") || low.contains("min(")
                || low.contains("max(") || low.contains("clamp(") || low.contains("attr(") || low.contains("{{")) {
            return null;
        }
        switch (t) {
            case LENGTH -> {
                for (String tok : low.split("[ \t]+")) {
                    if (!LENGTH.matcher(tok).matches() && !LENGTH_WORDS.contains(tok)) return "길이(예: 12px, 50%, auto)가 아닙니다";
                }
            }
            case NUMBER -> {
                if (!LENGTH.matcher(low).matches() || low.endsWith("%") && !prop.equals("opacity")) return "숫자가 아닙니다";
            }
            case COLOR -> {
                if (!COLOR_WORDS.contains(low) && Colors.tryParse(v) == null) return "색(예: #RRGGBB, red, rgb(...))이 아닙니다";
            }
            case ENUM -> {
                if (!ENUMS.get(prop).contains(low)) return "가능한 값: " + String.join(", ", new java.util.TreeSet<>(ENUMS.get(prop)));
            }
        }
        return null;
    }
}
