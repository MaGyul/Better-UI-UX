package dev.magyul.betteruiux.core.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스크립트와 오가는 메시지를 위한 작은 JSON 구현. 데이터 타입만 다룬다:
 * {@code Map<String,Object>}, {@code List<Object>}, {@code String}, {@code Double}, {@code Boolean}, {@code null}.
 * 입력 길이와 중첩 깊이에 상한이 있어 악의적인 메시지가 호스트를 괴롭히지 못한다.
 */
public final class Json {
    public static final int MAX_LENGTH = 1_000_000, MAX_DEPTH = 24;

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    /** @throws IllegalArgumentException 형식이 틀리거나 상한을 넘을 때 */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON 이 비어 있습니다");
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("JSON 이 너무 깁니다(" + text.length() + ")");
        Json p = new Json(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) throw p.error("끝에 남는 글자가 있습니다");
        return v;
    }

    private IllegalArgumentException error(String msg) { return new IllegalArgumentException("JSON 오류(" + i + "번째 글자): " + msg); }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw error("너무 깊게 중첩됨");
        if (i >= s.length()) throw error("값이 필요합니다");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw error("알 수 없는 글자 '" + c + "'");
        }
    }

    private Object literal(String word, Object v) {
        if (!s.startsWith(word, i)) throw error("'" + word + "' 이어야 합니다");
        i += word.length();
        return v;
    }

    private Double number() {
        int start = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        try {
            double d = Double.parseDouble(s.substring(start, i));
            if (Double.isNaN(d) || Double.isInfinite(d)) throw error("숫자가 올바르지 않습니다");
            return d;
        } catch (NumberFormatException e) {
            throw error("숫자가 올바르지 않습니다");
        }
    }

    private String string() {
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw error("문자열이 닫히지 않았습니다");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw error("문자열 안의 제어 문자");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) throw error("잘못된 이스케이프");
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("잘못된 \\u 이스케이프");
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("잘못된 \\u 이스케이프");
                    }
                    i += 4;
                }
                default -> throw error("잘못된 이스케이프 \\" + e);
            }
        }
    }

    private List<Object> array(int depth) {
        i++;
        List<Object> out = new ArrayList<>();
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return out;
        }
        while (true) {
            ws();
            out.add(value(depth + 1));
            ws();
            if (i >= s.length()) throw error("배열이 닫히지 않았습니다");
            char c = s.charAt(i++);
            if (c == ']') return out;
            if (c != ',') throw error("',' 또는 ']' 이어야 합니다");
        }
    }

    private Map<String, Object> object(int depth) {
        i++;
        Map<String, Object> out = new LinkedHashMap<>();
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return out;
        }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw error("키는 문자열이어야 합니다");
            String key = string();
            ws();
            if (i >= s.length() || s.charAt(i++) != ':') throw error("':' 이어야 합니다");
            ws();
            out.put(key, value(depth + 1));
            ws();
            if (i >= s.length()) throw error("객체가 닫히지 않았습니다");
            char c = s.charAt(i++);
            if (c == '}') return out;
            if (c != ',') throw error("',' 또는 '}' 이어야 합니다");
        }
    }

    // ---- 쓰기 ----

    /** 데이터 타입(Map/List/String/Number/Boolean/null)을 JSON 글자로. 그 밖의 타입은 문자열로 바꾼다. */
    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("너무 깊게 중첩됨");
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) sb.append(',');
                write(sb, l.get(k), depth + 1);
            }
            sb.append(']');
        } else {
            quote(sb, String.valueOf(v));
        }
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('"');
        for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
