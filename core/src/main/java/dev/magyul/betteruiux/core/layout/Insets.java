package dev.magyul.betteruiux.core.layout;

/** margin/padding. CSS 순서: top right bottom left. */
public record Insets(int top, int right, int bottom, int left) {
    public static final Insets ZERO = new Insets(0, 0, 0, 0);

    public int horizontal() { return left + right; }

    public int vertical() { return top + bottom; }

    /** "8" / "4 8" / "4 8 4" / "4 8 4 8" (px 접미사 허용). 해석 불가면 {@code fallback}. */
    public static Insets parse(String s, Insets fallback) {
        if (s == null || s.isBlank()) return fallback;
        String[] p = s.trim().split("\s+");
        int[] v = new int[p.length];
        try {
            for (int i = 0; i < p.length; i++) {
                String t = p[i].toLowerCase();
                if (t.endsWith("px")) t = t.substring(0, t.length() - 2);
                v[i] = Math.round(Float.parseFloat(t));
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return switch (v.length) {
            case 1 -> new Insets(v[0], v[0], v[0], v[0]);
            case 2 -> new Insets(v[0], v[1], v[0], v[1]);
            case 3 -> new Insets(v[0], v[1], v[2], v[1]);
            case 4 -> new Insets(v[0], v[1], v[2], v[3]);
            default -> fallback;
        };
    }
}
