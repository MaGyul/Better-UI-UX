package dev.magyul.betteruiux.core.layout;

import java.util.Locale;

/**
 * 크기 지정값: auto / px / % / fill(남은 공간 분배).
 * {@code PERCENT} 는 {@code value * 기준 + offset} 이다(순수 {@code 50%} 는 offset 0, {@code calc(100% - 20px)} 는 value 1, offset -20).
 */
public record Size(Kind kind, float value, float offset) {
    public enum Kind { AUTO, PX, PERCENT, FILL }

    public static final Size AUTO = new Size(Kind.AUTO, 0);
    public static final Size FILL = new Size(Kind.FILL, 1);

    public Size(Kind kind, float value) { this(kind, value, 0f); }

    public static Size px(float v) { return new Size(Kind.PX, v); }

    public static Size percent(float v) { return new Size(Kind.PERCENT, v); }

    /** PERCENT 일 때 기준 크기 {@code avail} 에서의 px 값(소수). */
    public float percentOf(int avail) { return value * avail + offset; }

    /** "auto", "fill", "50%", "120", "120px", "calc(100% - 20px)". 해석 불가면 {@code fallback}. */
    public static Size parse(String s, Size fallback) {
        if (s == null) return fallback;
        s = s.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.isEmpty() || s.equals("auto")) return AUTO;
            if (s.equals("fill")) return FILL;
            if (s.startsWith("calc(") && s.endsWith(")")) {
                float[] lin = new Calc(s.substring(5, s.length() - 1)).run();
                return lin[0] == 0 ? px(lin[1]) : new Size(Kind.PERCENT, lin[0] / 100f, lin[1]);
            }
            if (s.endsWith("%")) return percent(Float.parseFloat(s.substring(0, s.length() - 1)) / 100f);
            if (s.endsWith("px")) s = s.substring(0, s.length() - 2);
            return px(Float.parseFloat(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 퍼센트 항과 px 항의 일차식 {@code a% + b px} 만 다루는 작은 계산기. 곱셈/나눗셈은 무단위 숫자와만. 실패하면 NumberFormatException. */
    private static final class Calc {
        /** {퍼센트, px, 무단위 숫자 여부(1/0)} */
        private final String s;
        private int i;

        Calc(String s) { this.s = s; }

        float[] run() {
            float[] v = expr();
            skip();
            if (i < s.length()) throw new NumberFormatException(s);
            return new float[] {v[0], v[1]};
        }

        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private float[] expr() {
            float[] v = term();
            for (;;) {
                skip();
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    char op = s.charAt(i++);
                    float[] r = term();
                    float sign = op == '+' ? 1 : -1;
                    v = new float[] {v[0] + sign * r[0], v[1] + sign * r[1], v[2] == 1 && r[2] == 1 ? 1 : 0};
                } else {
                    return v;
                }
            }
        }

        private float[] term() {
            float[] v = factor();
            for (;;) {
                skip();
                if (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                    char op = s.charAt(i++);
                    float[] r = factor();
                    if (op == '*') {
                        if (v[2] == 1) v = new float[] {r[0] * v[1], r[1] * v[1], r[2]};
                        else if (r[2] == 1) v = new float[] {v[0] * r[1], v[1] * r[1], 0};
                        else throw new NumberFormatException(s);
                    } else {
                        if (r[2] != 1 || r[1] == 0) throw new NumberFormatException(s);
                        v = new float[] {v[0] / r[1], v[1] / r[1], v[2]};
                    }
                } else {
                    return v;
                }
            }
        }

        private float[] factor() {
            skip();
            if (i >= s.length()) throw new NumberFormatException(s);
            char c = s.charAt(i);
            if (c == '-') { i++; float[] f = factor(); return new float[] {-f[0], -f[1], f[2]}; }
            if (c == '+') { i++; return factor(); }
            if (c == '(') {
                i++;
                float[] v = expr();
                skip();
                if (i >= s.length() || s.charAt(i) != ')') throw new NumberFormatException(s);
                i++;
                return v;
            }
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new NumberFormatException(s);
            float n = Float.parseFloat(s.substring(st, i));
            int us = i;
            while (i < s.length() && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '%')) i++;
            String unit = s.substring(us, i);
            if (unit.isEmpty()) return new float[] {0, n, 1};
            return switch (unit) {
                case "%" -> new float[] {n, 0, 0};
                case "px" -> new float[] {0, n, 0};
                case "em", "rem" -> new float[] {0, n * 8f, 0};
                case "pt" -> new float[] {0, n * 96f / 72f, 0};
                case "pc" -> new float[] {0, n * 16f, 0};
                case "in" -> new float[] {0, n * 96f, 0};
                case "cm" -> new float[] {0, n * 96f / 2.54f, 0};
                case "mm" -> new float[] {0, n * 96f / 25.4f, 0};
                default -> throw new NumberFormatException(unit);
            };
        }
    }
}
