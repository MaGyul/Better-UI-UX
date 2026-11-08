package dev.magyul.betteruiux.core.style;

import java.util.ArrayList;
import java.util.List;

/**
 * CSS 유사 스타일시트. 지원: {@code selector, selector { prop: value; }}, 주석 {@code /* *}{@code /}, {@code !important},
 * 변수({@code --name}, {@code var()}). {@code @규칙}(예: @keyframes)은 현재 무시하고 경고만 남긴다(M2.5에서 지원).
 * 잘못된 규칙은 건너뛰고 {@link #warnings()} 에 기록하는 관대한 파서다(편집 중 깨진 CSS 한 줄이 화면 전체를 막지 않도록).
 */
public final class Stylesheet {
    public record Declaration(String prop, String value, boolean important) {}

    public record Rule(Selector selector, List<Declaration> declarations) {}

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public List<Rule> rules() { return rules; }

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
            if (selectorText.startsWith("@")) {
                sheet.warnings.add("스타일: '" + selectorText.split("\\s+")[0] + "' 은(는) 아직 지원하지 않아 무시합니다");
                continue;
            }
            List<Declaration> decls = parseDeclarations(body);
            for (String single : selectorText.split(",")) {
                try {
                    sheet.rules.add(new Rule(Selector.parse(single.trim()), decls));
                } catch (IllegalArgumentException e) {
                    sheet.warnings.add("스타일: " + e.getMessage());
                }
            }
        }
        return sheet;
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
