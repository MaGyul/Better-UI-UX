package dev.magyul.betteruiux.core.anim;

import java.util.ArrayList;
import java.util.List;

/** 애니메이션 값 파싱 공용 도구. */
final class Timing {
    private Timing() {}

    /** 공백으로 나누되 괄호 안(cubic-bezier(...))은 한 토큰으로 유지한다. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char c : s.trim().toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth = Math.max(0, depth - 1);
            if (Character.isWhitespace(c) && depth == 0) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 쉼표로 나누되 괄호 안의 쉼표는 무시한다. */
    static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    static boolean isTime(String t) {
        t = t.toLowerCase();
        return (t.endsWith("ms") && isNumber(t.substring(0, t.length() - 2)))
                || (t.endsWith("s") && isNumber(t.substring(0, t.length() - 1)));
    }

    /** "200ms" / "0.2s" → 밀리초. */
    static double millis(String t) {
        t = t.toLowerCase();
        return t.endsWith("ms") ? Double.parseDouble(t.substring(0, t.length() - 2))
                : Double.parseDouble(t.substring(0, t.length() - 1)) * 1000.0;
    }

    static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
