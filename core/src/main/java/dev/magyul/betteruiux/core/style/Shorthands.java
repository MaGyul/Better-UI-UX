package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.layout.StyleMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSS 숏핸드/롱핸드 처리. 이 엔진의 {@code border} 는 "두께" 하나(+ {@code border-color}), {@code margin/padding} 은
 * 4값 문자열이므로 다음과 같이 접는다.
 * <ul>
 *   <li>{@code margin-top|right|bottom|left}, {@code padding-*} → 기존 {@code margin}/{@code padding} 4값에 해당 변만 교체</li>
 *   <li>{@code border: 1px solid red} → {@code border: 1} + {@code border-color: red} (스타일 키워드는 none/hidden 만 의미 있음)</li>
 *   <li>{@code border-width} → {@code border}</li>
 * </ul>
 */
final class Shorthands {
    private Shorthands() {}

    private static final Set<String> BORDER_STYLES = Set.of("none", "hidden", "solid", "dashed", "dotted", "double",
            "groove", "ridge", "inset", "outset");
    private static final List<String> SIDES = List.of("top", "right", "bottom", "left");

    /** 처리했으면 true(호출자는 일반 put 을 하지 않는다). */
    static boolean put(StyleMap m, String key, String value) {
        for (String box : new String[] {"margin", "padding"}) {
            if (key.startsWith(box + "-")) {
                int side = SIDES.indexOf(key.substring(box.length() + 1));
                if (side >= 0) {
                    List<String> v = new ArrayList<>(four(m.get(box)));
                    v.set(side, value.trim());
                    m.put(box, String.join(" ", v));
                    return true;
                }
            }
        }
        if (key.equals("flex") && !value.contains("var(")) {
            expandFlex(m, value);
            return true;
        }
        if (key.equals("border-width")) {
            m.put("border", value);
            return true;
        }
        if (key.equals("border") && !value.contains("var(")) {
            List<String> t = CssMath.tokens(value);
            if (t.size() > 1 || (t.size() == 1 && isColor(t.get(0)))) {
                expandBorder(m, t);
                return true;
            }
        }
        return false;
    }

    /** {@code var()} 치환이 끝난 뒤, 값이 여러 토큰인 {@code border} 를 마저 푼다(명시된 롱핸드가 있으면 그것을 존중). */
    static void finish(StyleMap m) {
        String b = m.get("border");
        if (b == null) return;
        List<String> t = CssMath.tokens(b);
        if (t.size() > 1 || (t.size() == 1 && isColor(t.get(0)))) {
            boolean hadColor = m.has("border-color");
            String keep = m.get("border-color");
            expandBorder(m, t);
            if (hadColor) m.put("border-color", keep);
        }
    }

    /** {@code flex: none | auto | N | N M | N basis | N M basis | basis} → grow / flex-shrink / flex-basis. */
    private static void expandFlex(StyleMap m, String value) {
        List<String> t = CssMath.tokens(value);
        String grow = "0", shrink = "1", basis = null;
        if (t.size() == 1 && t.get(0).equalsIgnoreCase("none")) {
            shrink = "0";
        } else if (t.size() == 1 && t.get(0).equalsIgnoreCase("auto")) {
            grow = "1";
        } else if (t.size() == 1 && isPlain(t.get(0))) {
            grow = t.get(0);                                  // flex: 1  ==  1 1 0
            basis = "0";
        } else if (t.size() == 1) {
            basis = t.get(0);                                 // flex: 100px
        } else if (t.size() == 2) {
            grow = t.get(0);
            if (isPlain(t.get(1))) shrink = t.get(1);
            else basis = t.get(1);
        } else if (t.size() >= 3) {
            grow = t.get(0);
            shrink = t.get(1);
            basis = t.get(2);
        }
        m.put("grow", grow);
        m.put("flex-shrink", shrink);
        if (basis != null) m.put("flex-basis", basis);
    }

    private static boolean isPlain(String tok) {
        try {
            Double.parseDouble(tok);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isColor(String tok) {
        return Colors.tryParse(tok) != null || tok.equalsIgnoreCase("currentcolor");
    }

    private static void expandBorder(StyleMap m, List<String> tokens) {
        String width = null, style = null, color = null;
        for (String tok : tokens) {
            String low = tok.toLowerCase(Locale.ROOT);
            if (BORDER_STYLES.contains(low)) style = low;
            else if (isColor(tok)) color = tok;
            else width = switch (low) {
                case "thin" -> "1";
                case "medium" -> "2";
                case "thick" -> "3";
                default -> tok;
            };
        }
        if (style != null && (style.equals("none") || style.equals("hidden"))) width = "0";
        m.put("border", width != null ? width : "1");
        if (color != null) m.put("border-color", color);
    }

    /** 1~4값을 top right bottom left 4값으로 펼친다(없으면 0). */
    private static List<String> four(String v) {
        if (v == null || v.isBlank()) return List.of("0", "0", "0", "0");
        List<String> t = CssMath.tokens(v);
        return switch (t.size()) {
            case 1 -> List.of(t.get(0), t.get(0), t.get(0), t.get(0));
            case 2 -> List.of(t.get(0), t.get(1), t.get(0), t.get(1));
            case 3 -> List.of(t.get(0), t.get(1), t.get(2), t.get(1));
            case 4 -> t;
            default -> List.of("0", "0", "0", "0");
        };
    }
}
