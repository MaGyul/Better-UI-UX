package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.layout.Colors;
import java.util.List;
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

    private enum Type { LENGTH, NUMBER, COLOR, ENUM, BOX, RADIUS, BORDER, SHADOW, BACKGROUND, WEIGHT, FLEX, TRACKS, TRANSITION, ANIMATION }

    private static final Pattern LENGTH = Pattern.compile("-?([0-9]+[.]?[0-9]*|[.][0-9]+)(px|%|em|rem|vw|vh|vmin|vmax|pt|deg|s|ms)?");
    /** 단독 min()/max() 함수(minmax() 는 제외). */
    private static final Pattern MINMAX_FN = Pattern.compile("(^|[^a-z-])(min|max)[(]");
    private static final Set<String> GLOBAL = Set.of("inherit", "initial", "unset", "revert");
    private static final Set<String> LENGTH_WORDS = Set.of("auto", "fill", "none", "normal", "min-content", "max-content", "thin", "medium", "thick");
    private static final Set<String> BORDER_STYLES = Set.of("solid", "dashed", "dotted", "double", "hidden", "groove", "ridge", "inset", "outset");
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
        for (String p : new String[] {"margin", "padding"}) TYPES.put(p, Type.BOX);
        for (String p : new String[] {"margin-top", "margin-right", "margin-bottom", "margin-left", "padding-top", "padding-right", "padding-bottom", "padding-left"}) {
            TYPES.put(p, Type.LENGTH);
        }
        for (String p : new String[] {"border-radius", "border-top-left-radius", "border-top-right-radius", "border-bottom-right-radius", "border-bottom-left-radius"}) {
            TYPES.put(p, Type.RADIUS);
        }
        for (String p : new String[] {"border", "border-top", "border-right", "border-bottom", "border-left", "outline"}) TYPES.put(p, Type.BORDER);
        TYPES.put("box-shadow", Type.SHADOW);
        TYPES.put("text-shadow", Type.SHADOW);
        TYPES.put("background", Type.BACKGROUND);
        TYPES.put("font-weight", Type.WEIGHT);
        TYPES.put("flex", Type.FLEX);
        TYPES.put("grid-template-columns", Type.TRACKS);
        TYPES.put("grid-template-rows", Type.TRACKS);
        TYPES.put("transition", Type.TRANSITION);
        TYPES.put("animation", Type.ANIMATION);
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

    /** {@code grid-template-*} 의 트랙 목록에서 읽을 수 없는 항목을 찾는다(없으면 null): 길이, fr, %, auto, min-content, repeat(N, …), minmax(a, b). */
    private static String badTrack(String list) {
        for (String tok : CssMath.tokens(list)) {
            if (tok.startsWith("repeat(") && tok.endsWith(")")) {
                List<String> parts = Selector.splitList(tok.substring(7, tok.length() - 1));
                if (parts.size() != 2 || !parts.get(0).trim().matches("[0-9]+")) return "repeat(횟수, 트랙…) 형식이 아닙니다: " + tok;
                String inner = badTrack(parts.get(1).trim());
                if (inner != null) return inner;
            } else if (tok.startsWith("minmax(") && tok.endsWith(")")) {
                List<String> parts = Selector.splitList(tok.substring(7, tok.length() - 1));
                if (parts.size() != 2) return "minmax(최소, 최대) 형식이 아닙니다: " + tok;
                for (String p : parts) {
                    String bad = badTrack(p.trim());
                    if (bad != null) return bad;
                }
            } else if (tok.endsWith("fr") && LENGTH.matcher(tok.substring(0, tok.length() - 2)).matches()) {
                continue;
            } else if (!LENGTH.matcher(tok).matches() && !Set.of("auto", "min-content", "max-content").contains(tok) && !tok.startsWith("fit-content(")) {
                return "트랙 크기(예: 100px, 1fr, 20%, auto, repeat(3, 1fr))가 아닙니다: " + tok;
            }
        }
        return null;
    }

    /** 속성의 값 후보(열거형이면 가능한 값, 그 밖에는 흔한 키워드). 알 수 없는 속성이면 빈 목록. 자동 완성용. */
    static java.util.List<String> suggest(String prop) {
        Type t = TYPES.get(prop);
        if (t == null) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        switch (t) {
            case ENUM -> out.addAll(new java.util.TreeSet<>(ENUMS.get(prop)));
            case LENGTH -> out.addAll(java.util.List.of("auto", "fill", "0", "50%", "100%"));
            case COLOR -> out.addAll(java.util.List.of("transparent", "currentColor", "#FFFFFF", "#000000", "red", "green", "blue", "yellow", "white", "black"));
            case NUMBER -> out.addAll(java.util.List.of("0", "1"));
            case BOX -> out.addAll(java.util.List.of("0", "4", "8", "4 8", "auto"));
            case RADIUS -> out.addAll(java.util.List.of("0", "4", "8", "50%"));
            case BORDER -> out.addAll(java.util.List.of("1 solid #FFFFFF", "2 solid #000000", "1 dashed #888888", "none"));
            case SHADOW -> out.addAll(java.util.List.of("0 2 4 #000000AA", "0 0 8 #000000FF", "inset 0 0 6 #000000AA", "none"));
            case BACKGROUND -> out.addAll(java.util.List.of("transparent", "#000000", "#FFFFFF", "linear-gradient(#FF0000, #0000FF)",
                    "radial-gradient(#FF0000, #0000FF)", "conic-gradient(#FF0000, #00FF00, #0000FF)", "none"));
            case FLEX -> out.addAll(java.util.List.of("1", "0 0 auto", "1 1 0", "none", "auto"));
            case TRACKS -> out.addAll(java.util.List.of("1fr 1fr", "repeat(3, 1fr)", "100px 1fr", "auto 1fr", "minmax(50px, 1fr) 2fr"));
            case TRANSITION -> out.addAll(java.util.List.of("all 0.2s", "opacity 0.3s ease", "background 0.15s linear", "width 0.2s ease-out", "none"));
            case ANIMATION -> out.addAll(java.util.List.of("name 1s", "name 1s infinite", "name 0.5s ease-in-out alternate infinite", "none"));
            case WEIGHT -> out.addAll(java.util.List.of("normal", "bold", "bolder", "lighter", "400", "700"));
        }
        out.add("inherit");
        out.add("initial");
        return out;
    }

    /** 값이 명백히 잘못됐으면 사람이 읽을 사유, 아니면 null. */
    static String check(String prop, String rawValue) {
        Type t = TYPES.get(prop);
        if (t == null || rawValue == null) return null;
        String v = rawValue.trim();
        String low = v.toLowerCase(Locale.ROOT);
        if (low.endsWith("!important")) low = low.substring(0, low.length() - 10).trim();
        if (low.isEmpty() || GLOBAL.contains(low) || low.contains("var(") || low.contains("calc(") || MINMAX_FN.matcher(low).find() || low.contains("clamp(") || low.contains("attr(") || low.contains("{{")) {
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
            case BOX -> {
                List<String> toks = CssMath.tokens(low);
                if (toks.size() > 4) return "값은 1~4개(위 오른쪽 아래 왼쪽)입니다";
                for (String tok : toks) if (!LENGTH.matcher(tok).matches() && !tok.equals("auto")) return "길이(예: 8, 4px, auto)가 아닙니다: " + tok;
            }
            case RADIUS -> {
                List<String> toks = CssMath.tokens(low);
                int count = 0;
                for (String tok : toks) {
                    if (tok.equals("/")) continue;
                    count++;
                    if (!LENGTH.matcher(tok).matches()) return "길이(예: 4, 8px, 50%)가 아닙니다: " + tok;
                }
                if (count > 8) return "값이 너무 많습니다";
            }
            case BORDER -> {
                for (String tok : CssMath.tokens(low)) {
                    boolean ok = LENGTH.matcher(tok).matches() || LENGTH_WORDS.contains(tok) || BORDER_STYLES.contains(tok)
                            || COLOR_WORDS.contains(tok) || Colors.tryParse(tok) != null;
                    if (!ok) return "두께(4), 모양(solid dashed dotted), 색 중 하나가 아닙니다: " + tok;
                }
            }
            case SHADOW -> {
                if (low.equals("none")) return null;
                for (String one : Selector.splitList(low)) {
                    int numbers = 0;
                    for (String tok : CssMath.tokens(one.trim())) {
                        if (tok.equals("inset")) continue;
                        if (LENGTH.matcher(tok).matches()) numbers++;
                        else if (Colors.tryParse(tok) == null) return "그림자의 값이 아닙니다: " + tok + " (예: 0 2 4 #000000AA)";
                    }
                    if (numbers < 2 || numbers > 4) return "길이가 2~4개(가로 세로 [번짐 [퍼짐]])여야 합니다";
                }
            }
            case BACKGROUND -> {
                List<String> toks = CssMath.tokens(low);
                if (toks.size() == 1 && !COLOR_WORDS.contains(low) && Colors.tryParse(v) == null && !low.matches("(repeating-)?(linear|radial|conic)-gradient[(].*[)]") && !low.startsWith("url(")) {
                    return "색이나 그라디언트(linear-gradient(...))가 아닙니다";
                }
            }
            case FLEX -> {
                List<String> toks = CssMath.tokens(low);
                if (toks.size() > 3) return "값은 1~3개(grow shrink basis)입니다";
                for (String tok : toks) if (!LENGTH.matcher(tok).matches() && !Set.of("auto", "none", "initial", "fill", "content").contains(tok)) return "숫자나 길이가 아닙니다: " + tok;
            }
            case TRACKS -> {
                if (low.equals("none")) return null;
                String bad = badTrack(low);
                if (bad != null) return bad;
            }
            case TRANSITION -> {
                return dev.magyul.betteruiux.core.anim.AnimSyntax.checkTransition(low);
            }
            case ANIMATION -> {
                return dev.magyul.betteruiux.core.anim.AnimSyntax.checkAnimation(low);
            }
            case WEIGHT -> {
                if (!Set.of("normal", "bold", "bolder", "lighter").contains(low) && !LENGTH.matcher(low).matches()) return "normal, bold 또는 숫자(100~900)가 아닙니다";
            }
        }
        return null;
    }
}
