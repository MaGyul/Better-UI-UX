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

    /** 엔진이 읽는 속성들(+ 위젯이 쓰는 속성). {@code @supports} 판정과 "알 수 없는 속성" 경고에 쓴다. */
    public static final java.util.Set<String> KNOWN_PROPS = java.util.Set.of(
            // 크기·배치
            "width", "height", "min-width", "max-width", "min-height", "max-height", "box-sizing", "aspect-ratio",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "gap", "row-gap", "column-gap", "grid-gap", "justify", "justify-content", "align", "align-items", "align-self",
            "justify-self", "grow", "flex-grow", "flex-shrink", "flex-basis", "flex", "flex-wrap", "flex-direction", "align-content", "order",
            "anchor", "x", "y", "position", "top", "right", "bottom", "left", "z-index", "display", "visible", "overflow",
            "columns", "grid-template-columns", "grid-template-rows", "grid-column", "grid-row",
            // 모양
            "background", "background-color", "background-image", "border", "border-width", "border-color", "border-style",
            "border-top", "border-right", "border-bottom", "border-left",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "border-radius", "border-top-left-radius", "border-top-right-radius", "border-bottom-right-radius",
            "border-bottom-left-radius", "box-shadow", "outline", "outline-width", "outline-color", "outline-style",
            "outline-offset", "opacity", "cursor", "backdrop", "tint", "src", "uv", "tex", "item", "count", "size",
            "translate-x", "translate-y", "scale", "rotate", "transition", "animation", "animation-play-state",
            // 글자
            "color", "shadow", "text-shadow", "text-align", "font-size", "font-style", "font-family", "font-weight", "line-height", "letter-spacing",
            "vertical-align", "white-space", "text-transform", "text-overflow", "text-decoration", "text-decoration-line",
            "text", "content",
            // 상태·위젯
            "hover-background", "pressed-background", "disabled-background", "bar-color", "fill-color", "check-color",
            "use", "block-size", "block-tint", "enabled", "checked", "value", "min", "max", "step", "maxlength",
            "placeholder", "options", "group", "title", "tooltip", "modal", "onclick", "onchange", "onsubmit");

    /** 속성 값의 자동 완성 후보(열거형 속성은 가능한 값 전부, 그 밖에는 흔한 값). 알 수 없는 속성이면 빈 목록. */
    public static java.util.List<String> valueSuggestions(String prop) { return PropValues.suggest(prop == null ? "" : prop.trim()); }

    /**
     * 선언 하나를 검사해 사람이 읽을 경고를 돌려준다(문제가 없으면 null): 모르는 속성(비슷한 이름 제안)이거나 알려진 속성의 값이 명백히 잘못된 경우.
     * 에디터가 규칙을 고칠 때 바로 알려 주는 데 쓴다.
     */
    public static String lint(String prop, String value) {
        if (prop == null || prop.isBlank()) return null;
        String p = prop.trim();
        String hint = unknownHint(p);
        if (hint == null) return "알 수 없는 속성 '" + p + "'";
        if (!hint.isEmpty()) return "알 수 없는 속성 '" + p + "' (혹시 '" + hint + "'?)";
        String bad = PropValues.check(p, value);
        return bad == null ? null : "'" + p + ": " + value.trim() + "' - " + bad;
    }

    /** 모르는 속성이면 가장 비슷한 알려진 이름(편집 거리 2 이하), 없으면 null; 알려진 속성이면 빈 문자열. */
    static String unknownHint(String prop) {
        if (prop.startsWith("--") || prop.startsWith("-") || KNOWN_PROPS.contains(prop)) return "";
        String best = null;
        int bestD = 3;
        for (String k : KNOWN_PROPS) {
            int d = distance(prop, k);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Keyframes> keyframes = new LinkedHashMap<>();
    private final List<Import> imports = new ArrayList<>();
    private final java.util.Set<String> warnedProps = new java.util.HashSet<>();
    private final java.util.Set<String> warnedValues = new java.util.HashSet<>();

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
                sheet.warnings.add("스타일: '" + selectorText.split("[ \t\r\n]+")[0] + "' 은(는) 지원하지 않아 무시합니다");
                continue;
            }
            List<Declaration> decls = parseDeclarations(body);
            for (Declaration d : decls) {
                String hint = unknownHint(d.prop());
                if (hint != null && hint.isEmpty()) {                    // 알려진 속성: 값이 명백히 잘못됐으면 경고(값은 그대로 둔다)
                    String bad = PropValues.check(d.prop(), d.value());
                    if (bad != null && sheet.warnedValues.add(d.prop() + "=" + d.value())) {
                        sheet.warnings.add("스타일: '" + d.prop() + ": " + d.value() + "' - " + bad);
                    }
                    continue;
                }
                if (sheet.warnedProps.add(d.prop())) {
                    sheet.warnings.add("스타일: 알 수 없는 속성 '" + d.prop() + "'" + (hint == null ? "" : " (혹시 '" + hint + "'?)"));
                }
            }
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
