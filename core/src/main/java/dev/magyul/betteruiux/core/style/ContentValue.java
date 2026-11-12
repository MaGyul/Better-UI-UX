package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.xml.UiNode;

/**
 * {@code content} 값 계산: 따옴표 문자열({@code "\201C"} 같은 16진 이스케이프와 {@code \A} 줄바꿈 포함)과
 * {@code attr(이름)} 을 이어 붙인다. {@code none}/{@code normal}/해석할 수 없는 값이면 null.
 */
public final class ContentValue {
    private ContentValue() {}

    public static String eval(String raw, UiNode owner) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("none") || v.equalsIgnoreCase("normal")) return null;
        StringBuilder out = new StringBuilder();
        boolean any = false;
        for (String tok : CssMath.tokens(v)) {
            String low = tok.toLowerCase();
            if ((tok.startsWith("\"") && tok.endsWith("\"") && tok.length() >= 2)
                    || (tok.startsWith("'") && tok.endsWith("'") && tok.length() >= 2)) {
                out.append(unescape(tok.substring(1, tok.length() - 1)));
                any = true;
            } else if (low.startsWith("attr(") && tok.endsWith(")")) {
                String a = owner == null ? null : owner.attr(tok.substring(5, tok.length() - 1).trim());
                if (a != null) out.append(a);
                any = true;
            } else if (low.equals("open-quote")) {
                out.append('\u201C');
                any = true;
            } else if (low.equals("close-quote")) {
                out.append('\u201D');
                any = true;
            }
        }
        return any ? out.toString() : null;
    }

    /** {@code \201C} / {@code \A } / {@code \"} 이스케이프를 풀어 준다. */
    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            int j = i + 1, end = j;
            while (end < s.length() && end - j < 6 && Character.digit(s.charAt(end), 16) >= 0) end++;
            if (end > j) {
                int cp = Integer.parseInt(s.substring(j, end), 16);
                sb.appendCodePoint(cp == 0xA ? '\n' : Character.isValidCodePoint(cp) ? cp : 0xFFFD);
                i = end - 1;
                if (end < s.length() && s.charAt(end) == ' ') i++;                   // 이스케이프 뒤 공백 하나는 구분자
            } else {
                sb.append(s.charAt(j));
                i = j;
            }
        }
        return sb.toString();
    }
}
