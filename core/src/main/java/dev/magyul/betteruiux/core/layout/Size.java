package dev.magyul.betteruiux.core.layout;

/** 크기 지정값: auto / px / % / fill(남은 공간 분배). */
public record Size(Kind kind, float value) {
    public enum Kind { AUTO, PX, PERCENT, FILL }

    public static final Size AUTO = new Size(Kind.AUTO, 0);
    public static final Size FILL = new Size(Kind.FILL, 1);

    public static Size px(float v) { return new Size(Kind.PX, v); }

    public static Size percent(float v) { return new Size(Kind.PERCENT, v); }

    /** "auto", "fill", "50%", "120", "120px". 해석 불가면 {@code fallback}. */
    public static Size parse(String s, Size fallback) {
        if (s == null) return fallback;
        s = s.trim().toLowerCase();
        try {
            if (s.isEmpty() || s.equals("auto")) return AUTO;
            if (s.equals("fill")) return FILL;
            if (s.endsWith("%")) return percent(Float.parseFloat(s.substring(0, s.length() - 1)) / 100f);
            if (s.endsWith("px")) s = s.substring(0, s.length() - 2);
            return px(Float.parseFloat(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
