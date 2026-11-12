package dev.magyul.betteruiux.core.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 길이 값 전처리: 단위(em rem vw vh vmin vmax pt pc in cm mm q)를 px 로 바꾸고
 * {@code calc() min() max() clamp()} 를 계산해 평범한 숫자 문자열로 만든다.
 * 퍼센트가 들어 있는 식은 부모 크기를 알아야 하므로 건드리지 않는다(레이아웃 단계에서 단독 {@code N%} 만 지원).
 */
public final class CssMath {
    private CssMath() {}

    /** 1em = 1rem = 8px (마인크래프트 글꼴 높이). */
    public static final double EM = 8;

    /** @param vw,vh 뷰포트 크기(알 수 없으면 0 이하 → vw/vh 계열 단위는 그대로 둔다) */
    public record Env(double vw, double vh) {}

    private static final class Unsupported extends RuntimeException {
        Unsupported() { super(null, null, false, false); }
    }

    /** 공백(괄호/따옴표 밖)으로 토큰을 나눈다. */
    public static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = -1;
        char quote = 0;
        for (int i = 0; i <= value.length(); i++) {
            char c = i < value.length() ? value.charAt(i) : ' ';
            boolean ws = Character.isWhitespace(c);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
                if (start < 0) start = i;
            } else if (c == '(') {
                depth++;
                if (start < 0) start = i;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (ws && depth == 0) {
                if (start >= 0) out.add(value.substring(start, i));
                start = -1;
            } else if (start < 0) {
                start = i;
            }
        }
        return out;
    }

    /** 값 전체를 토큰별로 변환해 다시 공백으로 잇는다. 바뀐 것이 없으면 원본 그대로. */
    public static String resolve(String value, Env env) {
        if (value == null) return null;
        String low = value.toLowerCase(Locale.ROOT);
        boolean candidate = low.contains("calc(") || low.contains("min(") || low.contains("max(") || low.contains("clamp(")
                || low.matches(".*\\d(em|rem|vw|vh|vmin|vmax|pt|pc|in|cm|mm|q)\\b.*");
        if (!candidate) return value;
        List<String> t = tokens(value);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (String tok : t) {
            String r = resolveToken(tok, env);
            if (!r.equals(tok)) changed = true;
            if (sb.length() > 0) sb.append(' ');
            sb.append(r);
        }
        return changed ? sb.toString() : value;
    }

    private static String resolveToken(String tok, Env env) {
        try {
            String low = tok.toLowerCase(Locale.ROOT);
            if (low.startsWith("calc(") || low.startsWith("min(") || low.startsWith("max(") || low.startsWith("clamp(")
                    || low.startsWith("(")) {
                Parser p = new Parser(low, env);
                double v = p.parseAll();
                return fmt(v);
            }
            Double px = unit(low, env);
            return px == null ? tok : fmt(px);
        } catch (Unsupported | NumberFormatException e) {
            return tok;
        }
    }

    /** "1.5em" → px. 숫자+지원 단위가 아니면 null (px 와 무단위 숫자도 null: 이미 평범한 값). */
    private static Double unit(String s, Env env) {
        int i = 0;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        int digits = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        if (i == digits || i == s.length()) return null;
        double n = Double.parseDouble(s.substring(0, i));
        double f = unitFactor(s.substring(i), env);
        return Double.isNaN(f) || f == 1 && s.substring(i).equals("px") ? null : n * f;
    }

    /** 단위 → px 배율. 모르는 단위는 NaN, 계산 불가(뷰포트 미정)는 Unsupported. */
    private static double unitFactor(String unit, Env env) {
        return switch (unit) {
            case "px" -> 1;
            case "em", "rem" -> EM;
            case "pt" -> 96.0 / 72;
            case "pc" -> 16;
            case "in" -> 96;
            case "cm" -> 96 / 2.54;
            case "mm" -> 96 / 25.4;
            case "q" -> 96 / 101.6;
            case "vw", "vh", "vmin", "vmax" -> {
                if (env.vw() <= 0 || env.vh() <= 0) throw new Unsupported();
                yield switch (unit) {
                    case "vw" -> env.vw() / 100;
                    case "vh" -> env.vh() / 100;
                    case "vmin" -> Math.min(env.vw(), env.vh()) / 100;
                    default -> Math.max(env.vw(), env.vh()) / 100;
                };
            }
            default -> Double.NaN;
        };
    }

    static String fmt(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String f = String.format(Locale.ROOT, "%.4f", v);
        return f.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** 재귀 하강 파서: expr = term (± term)*, term = factor (×÷ factor)*. 무단위 숫자는 px 로 본다. */
    private static final class Parser {
        private final String s;
        private final Env env;
        private int i;

        Parser(String s, Env env) {
            this.s = s;
            this.env = env;
        }

        double parseAll() {
            double v = factor();
            if (!atEnd()) throw new Unsupported();
            return v;
        }

        private boolean atEnd() {
            skipWs();
            return i >= s.length();
        }

        private void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private double expr() {
            double v = term();
            for (;;) {
                skipWs();
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    char op = s.charAt(i++);
                    double r = term();
                    v = op == '+' ? v + r : v - r;
                } else {
                    return v;
                }
            }
        }

        private double term() {
            double v = factor();
            for (;;) {
                skipWs();
                if (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                    char op = s.charAt(i++);
                    double r = factor();
                    if (op == '/' && r == 0) throw new Unsupported();
                    v = op == '*' ? v * r : v / r;
                } else {
                    return v;
                }
            }
        }

        private double factor() {
            skipWs();
            if (i >= s.length()) throw new Unsupported();
            char c = s.charAt(i);
            if (c == '-' ) { i++; return -factor(); }
            if (c == '+') { i++; return factor(); }
            if (c == '(') {
                i++;
                double v = expr();
                expect(')');
                return v;
            }
            if (Character.isLetter(c)) {
                int st = i;
                while (i < s.length() && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '-')) i++;
                String name = s.substring(st, i);
                skipWs();
                expect('(');
                List<Double> args = new ArrayList<>();
                args.add(expr());
                for (;;) {
                    skipWs();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                        args.add(expr());
                    } else {
                        break;
                    }
                }
                expect(')');
                return switch (name) {
                    case "calc" -> single(args);
                    case "min" -> args.stream().mapToDouble(d -> d).min().orElseThrow(Unsupported::new);
                    case "max" -> args.stream().mapToDouble(d -> d).max().orElseThrow(Unsupported::new);
                    case "clamp" -> {
                        if (args.size() != 3) throw new Unsupported();
                        yield Math.max(args.get(0), Math.min(args.get(1), args.get(2)));
                    }
                    default -> throw new Unsupported();
                };
            }
            return number();
        }

        private double single(List<Double> a) {
            if (a.size() != 1) throw new Unsupported();
            return a.get(0);
        }

        private double number() {
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new Unsupported();
            double n = Double.parseDouble(s.substring(st, i));
            int us = i;
            while (i < s.length() && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '%')) i++;
            String unit = s.substring(us, i);
            if (unit.isEmpty()) return n;
            if (unit.equals("%")) throw new Unsupported();     // 부모 크기가 필요한 식은 여기서 계산하지 않는다
            double f = unitFactor(unit, env);
            if (Double.isNaN(f)) throw new Unsupported();
            return n * f;
        }

        private void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) throw new Unsupported();
            i++;
        }
    }
}
