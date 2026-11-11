package dev.magyul.betteruiux.core.layout;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CSS 색상 문자열 → 0xAARRGGBB(내부 표현).
 * <ul>
 *   <li>{@code #RGB #RGBA #RRGGBB #RRGGBBAA} (CSS 표준: 알파가 뒤)</li>
 *   <li>{@code rgb() rgba() hsl() hsla() hwb()} — 쉼표/공백 문법, {@code / 알파}, 퍼센트 허용</li>
 *   <li>CSS 색 이름 148개, {@code transparent none}, 마인크래프트 팔레트 {@code mc-red mc-gold ...}</li>
 * </ul>
 * {@code currentColor} 는 문맥이 필요하므로 {@code StyleResolver} 가 미리 치환한다.
 */
public final class Colors {
    private Colors() {}

    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        String css = "aliceblue:F0F8FF antiquewhite:FAEBD7 aqua:00FFFF aquamarine:7FFFD4 azure:F0FFFF beige:F5F5DC "
                + "bisque:FFE4C4 black:000000 blanchedalmond:FFEBCD blue:0000FF blueviolet:8A2BE2 brown:A52A2A "
                + "burlywood:DEB887 cadetblue:5F9EA0 chartreuse:7FFF00 chocolate:D2691E coral:FF7F50 "
                + "cornflowerblue:6495ED cornsilk:FFF8DC crimson:DC143C cyan:00FFFF darkblue:00008B darkcyan:008B8B "
                + "darkgoldenrod:B8860B darkgray:A9A9A9 darkgreen:006400 darkgrey:A9A9A9 darkkhaki:BDB76B "
                + "darkmagenta:8B008B darkolivegreen:556B2F darkorange:FF8C00 darkorchid:9932CC darkred:8B0000 "
                + "darksalmon:E9967A darkseagreen:8FBC8F darkslateblue:483D8B darkslategray:2F4F4F "
                + "darkslategrey:2F4F4F darkturquoise:00CED1 darkviolet:9400D3 deeppink:FF1493 deepskyblue:00BFFF "
                + "dimgray:696969 dimgrey:696969 dodgerblue:1E90FF firebrick:B22222 floralwhite:FFFAF0 "
                + "forestgreen:228B22 fuchsia:FF00FF gainsboro:DCDCDC ghostwhite:F8F8FF gold:FFD700 "
                + "goldenrod:DAA520 gray:808080 green:008000 greenyellow:ADFF2F grey:808080 honeydew:F0FFF0 "
                + "hotpink:FF69B4 indianred:CD5C5C indigo:4B0082 ivory:FFFFF0 khaki:F0E68C lavender:E6E6FA "
                + "lavenderblush:FFF0F5 lawngreen:7CFC00 lemonchiffon:FFFACD lightblue:ADD8E6 lightcoral:F08080 "
                + "lightcyan:E0FFFF lightgoldenrodyellow:FAFAD2 lightgray:D3D3D3 lightgreen:90EE90 "
                + "lightgrey:D3D3D3 lightpink:FFB6C1 lightsalmon:FFA07A lightseagreen:20B2AA lightskyblue:87CEFA "
                + "lightslategray:778899 lightslategrey:778899 lightsteelblue:B0C4DE lightyellow:FFFFE0 "
                + "lime:00FF00 limegreen:32CD32 linen:FAF0E6 magenta:FF00FF maroon:800000 mediumaquamarine:66CDAA "
                + "mediumblue:0000CD mediumorchid:BA55D3 mediumpurple:9370DB mediumseagreen:3CB371 "
                + "mediumslateblue:7B68EE mediumspringgreen:00FA9A mediumturquoise:48D1CC mediumvioletred:C71585 "
                + "midnightblue:191970 mintcream:F5FFFA mistyrose:FFE4E1 moccasin:FFE4B5 navajowhite:FFDEAD "
                + "navy:000080 oldlace:FDF5E6 olive:808000 olivedrab:6B8E23 orange:FFA500 orangered:FF4500 "
                + "orchid:DA70D6 palegoldenrod:EEE8AA palegreen:98FB98 paleturquoise:AFEEEE palevioletred:DB7093 "
                + "papayawhip:FFEFD5 peachpuff:FFDAB9 peru:CD853F pink:FFC0CB plum:DDA0DD powderblue:B0E0E6 "
                + "purple:800080 rebeccapurple:663399 red:FF0000 rosybrown:BC8F8F royalblue:4169E1 "
                + "saddlebrown:8B4513 salmon:FA8072 sandybrown:F4A460 seagreen:2E8B57 seashell:FFF5EE "
                + "sienna:A0522D silver:C0C0C0 skyblue:87CEEB slateblue:6A5ACD slategray:708090 slategrey:708090 "
                + "snow:FFFAFA springgreen:00FF7F steelblue:4682B4 tan:D2B48C teal:008080 thistle:D8BFD8 "
                + "tomato:FF6347 turquoise:40E0D0 violet:EE82EE wheat:F5DEB3 white:FFFFFF whitesmoke:F5F5F5 "
                + "yellow:FFFF00 yellowgreen:9ACD32 "
                // 마인크래프트 채팅 팔레트
                + "mc-black:000000 mc-dark-blue:0000AA mc-dark-green:00AA00 mc-dark-aqua:00AAAA mc-dark-red:AA0000 "
                + "mc-dark-purple:AA00AA mc-gold:FFAA00 mc-gray:AAAAAA mc-dark-gray:555555 mc-blue:5555FF "
                + "mc-green:55FF55 mc-aqua:55FFFF mc-red:FF5555 mc-light-purple:FF55FF mc-yellow:FFFF55 "
                + "mc-white:FFFFFF";
        for (String e : css.split(" ")) {
            int k = e.indexOf(':');
            NAMED.put(e.substring(0, k), 0xFF000000 | Integer.parseInt(e.substring(k + 1), 16));
        }
        NAMED.put("transparent", 0x00000000);
        NAMED.put("none", 0x00000000);
    }

    /** 색상이 아니면 null (보간 시 색/숫자 구분용). */
    public static Integer tryParse(String s) {
        if (s == null) return null;
        final int sentinel = 0x7E57ED01;
        int a = parse(s, sentinel), b = parse(s, ~sentinel);
        return a == b ? Integer.valueOf(a) : null;
    }

    public static int parse(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(s);
        if (named != null) return named;
        try {
            if (s.startsWith("#")) return parseHex(s.substring(1), fallback);
            int open = s.indexOf('(');
            if (open > 0 && s.endsWith(")")) return parseFunction(s.substring(0, open).trim(), s.substring(open + 1, s.length() - 1), fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
        return fallback;
    }

    /** 0xAARRGGBB → CSS 문자열: 불투명이면 {@code #RRGGBB}, 아니면 {@code #RRGGBBAA}. */
    public static String toCss(int argb) {
        int rgb = argb & 0xFFFFFF;
        int a = argb >>> 24;
        return a == 0xFF ? String.format("#%06X", rgb) : String.format("#%06X%02X", rgb, a);
    }

    /** 0xAARRGGBB → 항상 {@code #RRGGBBAA}. */
    public static String toCssFull(int argb) { return String.format("#%06X%02X", argb & 0xFFFFFF, argb >>> 24); }

    private static int parseHex(String h, int fallback) {
        return switch (h.length()) {
            case 3 -> argb(255, hex(h.charAt(0)) * 17, hex(h.charAt(1)) * 17, hex(h.charAt(2)) * 17);
            case 4 -> argb(hex(h.charAt(3)) * 17, hex(h.charAt(0)) * 17, hex(h.charAt(1)) * 17, hex(h.charAt(2)) * 17);
            case 6 -> 0xFF000000 | (int) Long.parseLong(h, 16);
            case 8 -> {
                long v = Long.parseLong(h, 16);               // RRGGBBAA
                yield (int) (((v & 0xFF) << 24) | (v >>> 8));
            }
            default -> fallback;
        };
    }

    private static int parseFunction(String name, String body, int fallback) {
        // 구분자: 쉼표 또는 공백, 알파는 쉼표 마지막 또는 "/"
        String b = body.trim().replace("/", " / ").replace(",", " ");
        String[] t = b.trim().split("\\s+");
        double alpha = 1;
        int n = t.length;
        for (int i = 0; i < t.length; i++) {
            if (t[i].equals("/")) {
                if (i != t.length - 2) return fallback;
                alpha = fraction(t[i + 1]);
                n = i;
                break;
            }
        }
        if (n == 4 && b.indexOf('/') < 0) {                  // rgba(r, g, b, a)
            alpha = fraction(t[3]);
            n = 3;
        }
        if (n != 3) return fallback;
        int a8 = clamp((int) Math.round(alpha * 255));
        switch (name) {
            case "rgb", "rgba":
                return argb(a8, channel(t[0]), channel(t[1]), channel(t[2]));
            case "hsl", "hsla": {
                double h = hue(t[0]), s = percent(t[1]), l = percent(t[2]);
                return fromHsl(h, s, l, a8);
            }
            case "hwb": {
                double h = hue(t[0]), w = percent(t[1]), bl = percent(t[2]);
                if (w + bl >= 1) {
                    int g = clamp((int) Math.round(w / (w + bl) * 255));
                    return argb(a8, g, g, g);
                }
                int base = fromHsl(h, 1, 0.5, 255);
                double f = 1 - w - bl;
                return argb(a8, clamp((int) Math.round(((base >> 16 & 255) / 255.0 * f + w) * 255)),
                        clamp((int) Math.round(((base >> 8 & 255) / 255.0 * f + w) * 255)),
                        clamp((int) Math.round(((base & 255) / 255.0 * f + w) * 255)));
            }
            default:
                return fallback;
        }
    }

    private static int channel(String s) {
        if (s.endsWith("%")) return clamp((int) Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 2.55));
        return clamp((int) Math.round(Double.parseDouble(s)));
    }

    private static double fraction(String s) {
        double v = s.endsWith("%") ? Double.parseDouble(s.substring(0, s.length() - 1)) / 100 : Double.parseDouble(s);
        return Math.max(0, Math.min(1, v));
    }

    private static double percent(String s) {
        if (!s.endsWith("%")) throw new NumberFormatException(s);
        return Math.max(0, Math.min(1, Double.parseDouble(s.substring(0, s.length() - 1)) / 100));
    }

    /** deg(기본) / rad / grad / turn → 0..360 */
    private static double hue(String s) {
        double v;
        if (s.endsWith("deg")) v = Double.parseDouble(s.substring(0, s.length() - 3));
        else if (s.endsWith("grad")) v = Double.parseDouble(s.substring(0, s.length() - 4)) * 0.9;
        else if (s.endsWith("rad")) v = Math.toDegrees(Double.parseDouble(s.substring(0, s.length() - 3)));
        else if (s.endsWith("turn")) v = Double.parseDouble(s.substring(0, s.length() - 4)) * 360;
        else v = Double.parseDouble(s);
        v %= 360;
        return v < 0 ? v + 360 : v;
    }

    private static int fromHsl(double h, double s, double l, int a8) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        int sector = (int) (h / 60);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return argb(a8, clamp((int) Math.round((r + m) * 255)), clamp((int) Math.round((g + m) * 255)),
                clamp((int) Math.round((b + m) * 255)));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

    private static int hex(char c) {
        int d = Character.digit(c, 16);
        if (d < 0) throw new NumberFormatException();
        return d;
    }
}
