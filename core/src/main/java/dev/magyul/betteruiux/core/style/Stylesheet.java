package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.anim.Keyframes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSS 유사 스타일시트. 지원: {@code selector, selector { prop: value; }}, 주석 {@code /* *}{@code /}, {@code !important},
 * 변수({@code --name}, {@code var()}), {@code @keyframes}. 그 밖의 {@code @규칙}은 무시하고 경고만 남긴다.
 * 잘못된 규칙은 건너뛰고 {@link #warnings()} 에 기록하는 관대한 파서다(편집 중 깨진 CSS 한 줄이 화면 전체를 막지 않도록).
 */
public final class Stylesheet {
    public record Declaration(String prop, String value, boolean important) {}

    public record Rule(Selector selector, List<Declaration> declarations) {}

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Keyframes> keyframes = new LinkedHashMap<>();

    public List<Rule> rules() { return rules; }

    public Map<String, Keyframes> keyframes() { return keyframes; }

    public List<String> warnings() { return warnings; }

    public static Stylesheet parse(String css) {
        Stylesheet sheet = new Stylesheet();
        String s = stripComments(css == null ? "" : css);
        int i = 0, n = s.length();
        while (i < n) {
            int open = s.indexOf('{', i);
            if (open < 0) {
                if (!s.substring(i).isBlank()) sheet.warnings.add("스타일: 닫히지 않은 규칙 '" + s.substring(i).trim() + "'");
                break;
            }
            String selectorText = s.substring(i, open).trim();
            int close = matchingBrace(s, open);
            if (close < 0) {
                sheet.warnings.add("스타일: '}' 가 없습니다 (" + selectorText + ")");
                break;
            }
            String body = s.substring(open + 1, close);
            i = close + 1;
            if (selectorText.startsWith("@keyframes")) {
                sheet.parseKeyframes(selectorText.substring("@keyframes".length()).trim(), body);
                continue;
            }
            if (selectorText.startsWith("@")) {
                sheet.warnings.add("스타일: '" + selectorText.split("\\s+")[0] + "' 은(는) 지원하지 않아 무시합니다");
                continue;
            }
            List<Declaration> decls = parseDeclarations(body);
            for (String single : Selector.splitList(selectorText)) {
                try {
                    sheet.rules.add(new Rule(Selector.parse(single.trim()), decls));
                } catch (IllegalArgumentException e) {
                    sheet.warnings.add("스타일: " + e.getMessage());
                }
            }
        }
        return sheet;
    }

    /** {@code from {..} 50% {..} to {..}} 본문을 정지점 목록으로 읽는다. */
    private void parseKeyframes(String name, String body) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            warnings.add("스타일: 잘못된 @keyframes 이름 '" + name + "'");
            return;
        }
        List<Keyframes.Stop> stops = new ArrayList<>();
        int i = 0, n = body.length();
        while (i < n) {
            int open = body.indexOf('{', i);
            if (open < 0) break;
            int close = matchingBrace(body, open);
            if (close < 0) {
                warnings.add("스타일: @keyframes " + name + " 의 '}' 가 없습니다");
                break;
            }
            String stopList = body.substring(i, open).trim();
            Map<String, String> props = new LinkedHashMap<>();
            for (Declaration d : parseDeclarations(body.substring(open + 1, close))) props.put(d.prop(), d.value());
            for (String one : stopList.split(",")) {
                Double offset = stopOffset(one.trim().toLowerCase());
                if (offset == null) warnings.add("스타일: @keyframes " + name + " 의 잘못된 지점 '" + one.trim() + "'");
                else stops.add(new Keyframes.Stop(offset, props));
            }
            i = close + 1;
        }
        if (stops.isEmpty()) warnings.add("스타일: @keyframes " + name + " 에 지점이 없습니다");
        else keyframes.put(name, Keyframes.of(name, stops));
    }

    private static Double stopOffset(String s) {
        if (s.equals("from")) return 0.0;
        if (s.equals("to")) return 1.0;
        if (!s.endsWith("%")) return null;
        try {
            double v = Double.parseDouble(s.substring(0, s.length() - 1)) / 100.0;
            return v >= 0 && v <= 1 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code a: b; c: d !important} → 선언 목록. 괄호/따옴표 안의 ';' 는 구분자로 보지 않는다. */
    public static List<Declaration> parseDeclarations(String body) {
        List<Declaration> out = new ArrayList<>();
        if (body == null) return out;
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i <= body.length(); i++) {
            char c = i < body.length() ? body.charAt(i) : ';';
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ';' && depth == 0) {
                addDeclaration(out, body.substring(start, i));
                start = i + 1;
            }
        }
        return out;
    }

    private static void addDeclaration(List<Declaration> out, String text) {
        int colon = text.indexOf(':');
        if (colon <= 0) return;
        String prop = text.substring(0, colon).trim().toLowerCase();
        String value = text.substring(colon + 1).trim();
        boolean important = false;
        if (value.toLowerCase().endsWith("!important")) {
            important = true;
            value = value.substring(0, value.length() - "!important".length()).trim();
        }
        if (prop.isEmpty() || value.isEmpty()) return;
        out.add(new Declaration(prop, value, important));
    }

    private static String stripComments(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            if (css.startsWith("/*", i)) {
                int end = css.indexOf("*/", i + 2);
                if (end < 0) break;
                i = end + 2;
            } else {
                sb.append(css.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}' && --depth == 0) return i;
        }
        return -1;
    }
}
