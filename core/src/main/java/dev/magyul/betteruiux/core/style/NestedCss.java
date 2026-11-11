package dev.magyul.betteruiux.core.style;

import java.util.ArrayList;
import java.util.List;

/**
 * CSS 중첩({@code a { &:hover { … } .title { … } > .x { … } @media (…) { … } }}) 처리: 규칙 본문에서 중첩 규칙을 떼어 내고,
 * 중첩 선택자를 부모와 합쳐 평평한 선택자로 만든다. 스타일시트 파서와 에디터(규칙 편집)가 같은 규칙을 쓴다.
 */
public final class NestedCss {
    private NestedCss() {}

    /**
     * 중첩 규칙 하나.
     *
     * @param prelude      중첩 선택자(또는 {@code @media (…)} 같은 조건) 글자
     * @param body         중첩 규칙의 본문({@code { }} 안쪽)
     * @param preludeStart 선택자 글자의 시작 위치(본문 시작을 {@code baseOffset} 으로 준 절대 위치)
     * @param bodyStart    본문({@code { } 안쪽}) 시작, bodyEnd 는 닫는 {@code }} 의 위치(절대)
     */
    public record Nested(String prelude, String body, int preludeStart, int bodyStart, int bodyEnd) {}

    /** @param declarations 중첩 규칙을 뺀 선언 글자, @param nested 중첩 규칙들(나온 순서) */
    public record Split(String declarations, List<Nested> nested) {}

    /** 규칙 본문을 선언 부분과 중첩 규칙들로 나눈다. {@code baseOffset} 은 본문이 원문에서 시작하는 위치(절대 위치 계산용). */
    public static Split split(String body, int baseOffset) {
        StringBuilder decls = new StringBuilder();
        List<Nested> nested = new ArrayList<>();
        int segStart = 0, paren = 0;
        char quote = 0;
        int n = body.length();
        for (int i = 0; i < n; i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') paren++;
            else if (c == ')' && paren > 0) paren--;
            else if (c == ';' && paren == 0) {
                decls.append(body, segStart, i + 1);
                segStart = i + 1;
            } else if (c == '{' && paren == 0) {
                int close = matching(body, i);
                if (close < 0) break;                                   // 닫히지 않음: 남은 글자는 그대로 선언으로 둔다
                String prelude = body.substring(segStart, i).trim();
                int lead = 0;
                while (segStart + lead < i && Character.isWhitespace(body.charAt(segStart + lead))) lead++;
                nested.add(new Nested(prelude, body.substring(i + 1, close), baseOffset + segStart + lead, baseOffset + i + 1, baseOffset + close));
                i = close;
                segStart = close + 1;
            }
        }
        if (segStart < n) decls.append(body, segStart, n);
        return new Split(decls.toString(), nested);
    }

    private static int matching(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    /**
     * 부모 선택자 목록과 중첩 선택자 목록을 합친다(쉼표로 이은 하나의 글자).
     * {@code &} 는 부모, 없으면 부모의 후손({@code > + ~} 로 시작하면 그 관계). 부모가 단순한 하나가 아니면 {@code :is(부모)} 로 감싼다.
     */
    public static String combine(String parent, String nested) {
        List<String> parents = Selector.splitList(parent);
        boolean single = parents.size() == 1;
        String plain = single ? parents.get(0).trim() : null;
        String wrapped = ":is(" + parent.trim() + ")";
        List<String> out = new ArrayList<>();
        for (String item : Selector.splitList(nested)) {
            String t = item.trim();
            if (t.isEmpty()) continue;
            if (t.indexOf('&') >= 0) {
                // 부모가 하나이고 단순하거나, & 가 맨 앞에 한 번만 있으면(&:hover, &.x, &::before) 글자를 그대로 넣어도 :is() 와 같다
                boolean direct = single && (simple(plain) || (t.startsWith("&") && t.indexOf('&', 1) < 0));
                out.add(t.replace("&", direct ? plain : wrapped));
            } else {
                out.add((single ? plain : wrapped) + " " + t);                // "> .x" 도 "부모 > .x" 가 된다
            }
        }
        return String.join(", ", out);
    }

    /** 공백/결합자가 없는 하나의 복합 선택자인가(괄호·대괄호 안은 제외). */
    private static boolean simple(String sel) {
        int depth = 0;
        for (char c : sel.trim().toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (depth == 0 && (Character.isWhitespace(c) || c == '>' || c == '+' || c == '~' || c == ',')) return false;
        }
        return true;
    }
}
