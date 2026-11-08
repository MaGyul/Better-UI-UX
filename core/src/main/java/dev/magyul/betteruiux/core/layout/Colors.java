package dev.magyul.betteruiux.core.layout;

import java.util.Map;

/** 색상 문자열 → 0xAARRGGBB. #RGB #RRGGBB #AARRGGBB 및 기본 색 이름. */
public final class Colors {
    private Colors() {}

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("white", 0xFFFFFFFF), Map.entry("black", 0xFF000000),
            Map.entry("gray", 0xFF808080), Map.entry("grey", 0xFF808080),
            Map.entry("red", 0xFFFF5555), Map.entry("green", 0xFF55FF55),
            Map.entry("blue", 0xFF5555FF), Map.entry("yellow", 0xFFFFFF55),
            Map.entry("transparent", 0x00000000), Map.entry("none", 0x00000000));

    /** 색상이 아니면 null (보간 시 색/숫자 구분용). */
    public static Integer tryParse(String s) {
        if (s == null) return null;
        final int sentinel = 0x7E57ED01;
        int a = parse(s, sentinel), b = parse(s, ~sentinel);
        return a == b ? Integer.valueOf(a) : null;
    }

    public static int parse(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim().toLowerCase();
        Integer named = NAMED.get(s);
        if (named != null) return named;
        if (!s.startsWith("#")) return fallback;
        String h = s.substring(1);
        try {
            return switch (h.length()) {
                case 3 -> 0xFF000000
                        | (hex(h.charAt(0)) * 17 << 16) | (hex(h.charAt(1)) * 17 << 8) | (hex(h.charAt(2)) * 17);
                case 6 -> 0xFF000000 | (int) Long.parseLong(h, 16);
                case 8 -> (int) Long.parseLong(h, 16);
                default -> fallback;
            };
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int hex(char c) {
        int d = Character.digit(c, 16);
        if (d < 0) throw new NumberFormatException();
        return d;
    }
}
