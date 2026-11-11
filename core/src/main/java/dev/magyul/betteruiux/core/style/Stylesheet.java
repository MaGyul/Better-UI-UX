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

    /** {@code media}: 이 규칙을 감싼 {@code @media}(및 {@code @import ... 미디어}) 조건들. 모두 참일 때만 적용. */
    public record Rule(Selector selector, List<Declaration> declarations, List<MediaQuery> media) {
        public Rule(Selector selector, List<Declaration> declarations) { this(selector, declarations, List.of()); }

        public boolean appliesTo(int viewW, int viewH) {
            for (MediaQuery q : media) if (!q.matches(viewW, viewH)) return false;
            return true;
        }
    }

    /** {@code @import "x.uicss" [미디어]}: 로더가 파일을 찾아 이 시트보다 앞에 끼워 넣는다. */
    public record Import(String href, List<MediaQuery> media) {}

    private static final int MAX_NESTING = 8;

    /** {@code @supports} 가 "지원함"으로 보는 프로퍼티(엔진이 읽는 것들 + {@code --변수}). */
    private static final java.util.Set<String> KNOWN_PROPS = java.util.Set.of(
            "width", "height", "min-width", "max-width", "min-height", "max-height", "margin", "padding", "gap",
            "justify", "align", "align-self", "grow", "anchor", "x", "y", "visible", "display", "background",
            "background-color", "border", "border-width", "border-color", "color", "shadow", "text-align", "opacity",
            "overflow", "cursor", "translate-x", "translate-y", "scale", "rotate", "src", "uv", "tex", "tint",
            "transition", "animation", "columns", "backdrop");

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Keyframes> keyframes = new LinkedHashMap<>();
    private final List<Import> imports = new ArrayList<>();

    public List<Rule> rules() { return rules; }

    public List<Import> imports() { return imports; }

    public Map<String, Keyframes> keyframes() { return keyframes; }

    public List<String> warnings() { return warnings; }

    public static Stylesheet parse(String css) { return parse(css, List.of()); }

    /** @param baseMedia 이 시트 전체에 걸리는 조건(예: {@code @import "a.uicss" (min-width: 300px)}) */
    public static Stylesheet parse(String css, List<MediaQuery> baseMedia) {
        Stylesheet sheet = new Stylesheet();
        sheet.parseBlock(stripComments(css == null ? "" : css), baseMedia, 0);
        return sheet;
    }

    private void parseBlock(String s, List<MediaQuery> media, int depth) {
        Stylesheet sheet = this;
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= n) break;
            if (s.charAt(i) == '@') {                       // 블록이 없는 문장: @import / @charset / @namespace
                String word = s.substring(i, Math.min(n, i + 10)).toLowerCase();
                if (word.startsWith("@import") || word.startsWith("@charset") || word.startsWith("@namespace")) {
                    int semi = s.indexOf(';', i);
                    int brace = s.indexOf('{', i);
                    if (semi >= 0 && (brace < 0 || semi < brace)) {
                        sheet.statement(s.substring(i, semi).trim(), media);
                        i = semi + 1;
                        continue;
                    }
                }
            }
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
            if (selectorText.toLowerCase().startsWith("@media")) {
                String cond = selectorText.substring("@media".length());
                try {
                    List<MediaQuery> inner = new ArrayList<>(media);
                    inner.add(MediaQuery.parse(cond));
                    if (depth >= MAX_NESTING) sheet.warnings.add("스타일: @media 가 너무 깊이 중첩됐습니다");
                    else sheet.parseBlock(body, inner, depth + 1);
                } catch (IllegalArgumentException e) {
                    sheet.warnings.add("스타일: @media " + e.getMessage());
                }
                continue;
            }
            if (selectorText.toLowerCase().startsWith("@supports")) {
                if (depth >= MAX_NESTING) sheet.warnings.add("스타일: @supports 가 너무 깊이 중첩됐습니다");
                else if (supports(selectorText.substring("@supports".length()))) sheet.parseBlock(body, media, depth + 1);
                continue;
            }
            if (selectorText.startsWith("@")) {
                sheet.warnings.add("스타일: '" + selectorText.split("\s+")[0] + "' 은(는) 지원하지 않아 무시합니다");
                continue;
            }
            List<Declaration> decls = parseDeclarations(body);
            for (String single : Selector.splitList(selectorText)) {
                try {
                    sheet.rules.add(new Rule(Selector.parse(single.trim()), decls, media));
                } catch (IllegalArgumentException e) {
                    sheet.warnings.add("스타일: " + e.getMessage());
                }
            }
        }
    }

    /** {@code @import "a.uicss" [미디어];} 등 블록 없는 문장. */
    private void statement(String text, List<MediaQuery> media) {
        String low = text.toLowerCase();
        if (low.startsWith("@charset") || low.startsWith("@namespace")) return;
        String rest = text.substring("@import".length()).trim();
        String href;
        int end;
        if (rest.toLowerCase().startsWith("url(")) {
            int close = rest.indexOf(')');
            if (close < 0) { warnings.add("스타일: 잘못된 @import: " + text); return; }
            href = unquote(rest.substring(4, close).trim());
            end = close + 1;
        } else if (rest.startsWith("\"") || rest.startsWith("'")) {
            int close = rest.indexOf(rest.charAt(0), 1);
            if (close < 0) { warnings.add("스타일: 잘못된 @import: " + text); return; }
            href = rest.substring(1, close);
            end = close + 1;
        } else {
            warnings.add("스타일: 잘못된 @import: " + text);
            return;
        }
        List<MediaQuery> cond = new ArrayList<>(media);
        String cq = rest.substring(end).trim();
        if (!cq.isEmpty()) {
            try {
                cond.add(MediaQuery.parse(cq));
            } catch (IllegalArgumentException e) {
                warnings.add("스타일: @import " + e.getMessage());
                return;
            }
        }
        imports.add(new Import(href, cond));
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    // ---- @supports ----

    /** {@code (prop: value)}, {@code selector(S)}, {@code not}, {@code and}, {@code or} 를 지원한다. */
    static boolean supports(String condition) {
        List<String> t = CssMath.tokens(condition.trim());
        if (t.isEmpty()) return false;
        int[] pos = {0};
        boolean result = supportsTerm(t, pos);
        while (pos[0] < t.size()) {
            String op = t.get(pos[0]++).toLowerCase();
            if (pos[0] >= t.size()) return false;
            boolean next = supportsTerm(t, pos);
            if (op.equals("and")) result = result && next;
            else if (op.equals("or")) result = result || next;
            else return false;
        }
        return result;
    }

    private static boolean supportsTerm(List<String> t, int[] pos) {
        String tok = t.get(pos[0]++);
        if (tok.equalsIgnoreCase("not")) return pos[0] < t.size() && !supportsTerm(t, pos);
        String low = tok.toLowerCase();
        if (low.startsWith("selector(") && tok.endsWith(")")) {
            try {
                Selector.parse(tok.substring(9, tok.length() - 1));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (tok.startsWith("(") && tok.endsWith(")")) {
            String inner = tok.substring(1, tok.length() - 1).trim();
            int colon = topLevelColon(inner);
            if (colon > 0) {
                String prop = inner.substring(0, colon).trim().toLowerCase();
                String value = inner.substring(colon + 1).trim();
                return !value.isEmpty() && (KNOWN_PROPS.contains(prop) || prop.startsWith("--"));
            }
            return supports(inner);
        }
        return false;
    }

    private static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ':' && depth == 0) return i;
        }
        return -1;
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
