package dev.magyul.betteruiux.core.sync;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서버 액션의 인자 규칙. 서버는 클라이언트가 보낸 인자를 절대 믿지 않고 이 스키마로 형식/범위/길이를 확인한다.
 * 스키마에 없는 인자나 빠진 필수 인자는 거부한다.
 */
public final class ActionSchema {
    public enum Type { STRING, INT, DOUBLE, BOOL }

    /** 인자 하나의 규칙. 숫자는 min/max, 문자열은 maxLen 을 쓴다. */
    public record Arg(Type type, boolean required, double min, double max, int maxLen) {
        public static Arg string(int maxLen) { return new Arg(Type.STRING, true, 0, 0, Math.min(maxLen, SyncLimits.MAX_VALUE)); }

        public static Arg integer(long min, long max) { return new Arg(Type.INT, true, min, max, 0); }

        public static Arg number(double min, double max) { return new Arg(Type.DOUBLE, true, min, max, 0); }

        public static Arg bool() { return new Arg(Type.BOOL, true, 0, 0, 0); }

        public Arg optional() { return new Arg(type, false, min, max, maxLen); }
    }

    private final Map<String, Arg> args = new LinkedHashMap<>();

    public ActionSchema with(String name, Arg arg) {
        args.put(name, arg);
        return this;
    }

    public static ActionSchema none() { return new ActionSchema(); }

    /** @return 거부 사유(올바르면 null) */
    public String validate(Map<String, Object> given) {
        for (String key : given.keySet()) if (!args.containsKey(key)) return "알 수 없는 인자: " + key;
        for (Map.Entry<String, Arg> e : args.entrySet()) {
            Object v = given.get(e.getKey());
            Arg a = e.getValue();
            if (v == null) {
                if (a.required()) return "필수 인자가 없습니다: " + e.getKey();
                continue;
            }
            switch (a.type()) {
                case STRING -> {
                    if (!(v instanceof String s)) return "문자열이어야 합니다: " + e.getKey();
                    if (s.length() > a.maxLen()) return "너무 깁니다: " + e.getKey();
                }
                case INT -> {
                    if (!(v instanceof Long n)) return "정수여야 합니다: " + e.getKey();
                    if (n < a.min() || n > a.max()) return "범위를 벗어났습니다: " + e.getKey();
                }
                case DOUBLE -> {
                    if (!(v instanceof Double) && !(v instanceof Long)) return "숫자여야 합니다: " + e.getKey();
                    double d = ((Number) v).doubleValue();
                    if (d < a.min() || d > a.max()) return "범위를 벗어났습니다: " + e.getKey();
                }
                case BOOL -> {
                    if (!(v instanceof Boolean)) return "참/거짓이어야 합니다: " + e.getKey();
                }
            }
        }
        return null;
    }
}
