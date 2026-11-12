package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * label/button 글자 스타일: 크기, 줄 간격, 자간, 굵게, 공백 처리/줄바꿈, 대소문자 변환, 말줄임.
 * 레이아웃(크기 계산)과 페인터(그리기)가 같은 계산을 쓰도록 한 곳에 모은다.
 *
 * <p>게임 글꼴은 하나라서 글자 크기는 균등 배율({@code font-size / 8px})로, 굵게는 1px 겹쳐 그리기로 근사한다.
 * 기본값(8px, 줄 간격 보통, 자간 0, white-space: pre)에서는 {@link TextMetrics} 결과를 그대로 쓴다.
 */
public final class TextStyle {
    /** 기본 글자 크기(px): 마인크래프트 글꼴 높이. */
    public static final float BASE = 8f;

    private final TextMetrics tm;
    private final float scale;
    private final float fontPx;
    private final int letterSpacing;
    private final int lineHeight;
    private final boolean bold;
    private final String whiteSpace, transform;
    private final boolean ellipsis;
    private final boolean italic;
    private final String fontId;

    private TextStyle(TextMetrics tm, StyleMap s) {
        this.tm = tm;
        String fs = s.get("font-style", "normal").trim().toLowerCase(Locale.ROOT);
        this.italic = fs.equals("italic") || fs.startsWith("oblique");
        this.fontId = fontId(s.get("font-family"));
        float px = number(s.get("font-size"), BASE);
        this.fontPx = Math.max(2f, Math.min(64f, px));
        this.scale = fontPx / BASE;
        this.letterSpacing = Math.round(length(s.get("letter-spacing"), fontPx, 0f));
        this.lineHeight = lineHeightOf(s.get("line-height"), tm.lineHeight(), scale, fontPx);
        String w = s.get("font-weight", "normal").trim().toLowerCase(Locale.ROOT);
        boolean b = w.equals("bold") || w.equals("bolder");
        if (!b) {
            try {
                b = Integer.parseInt(w) >= 600;
            } catch (NumberFormatException ignored) {
                // normal / lighter 등
            }
        }
        this.bold = b;
        this.whiteSpace = s.get("white-space", "pre").trim().toLowerCase(Locale.ROOT);
        this.transform = s.get("text-transform", "none").trim().toLowerCase(Locale.ROOT);
        this.ellipsis = s.get("text-overflow", "clip").trim().equalsIgnoreCase("ellipsis");
    }

    public static TextStyle of(StyleMap s, TextMetrics tm) { return new TextStyle(tm, s); }

    public boolean italic() { return italic; }

    /** 쓰는 글꼴 id(기본 글꼴이면 null). */
    public String fontId() { return fontId; }

    /**
     * {@code font-family} 목록에서 처음으로 알아볼 수 있는 글꼴: 마인크래프트 글꼴 별칭({@code uniform}, {@code alt}/{@code enchanting},
     * {@code illageralt}, {@code default}) 또는 {@code namespace:path} 형태의 글꼴 id. 그 밖의 이름(serif, Arial …)은 건너뛴다.
     * 기본 글꼴이거나 알 수 없으면 null.
     */
    public static String fontId(String family) {
        if (family == null || family.isBlank()) return null;
        for (String part : family.split(",")) {
            String t = part.trim().replace("\"", "").replace("'", "").toLowerCase(Locale.ROOT);
            switch (t) {
                case "default", "minecraft", "mc", "inherit", "initial" -> { return null; }
                case "uniform", "unifont" -> { return "minecraft:uniform"; }
                case "alt", "enchanting", "galactic" -> { return "minecraft:alt"; }
                case "illageralt", "illager" -> { return "minecraft:illageralt"; }
                default -> {
                    if (t.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return t;
                }
            }
        }
        return null;
    }

    private int rawWidth(String s) { return fontId == null ? tm.width(s) : tm.width(s, fontId); }

    private static float number(String v, float def) {
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim().toLowerCase(Locale.ROOT).replace("px", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** px / em / rem / % / 무단위(px) 길이. */
    static float length(String v, float emPx, float def) {
        if (v == null) return def;
        String t = v.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.equals("normal")) return def;
            if (t.endsWith("rem")) return Float.parseFloat(t.substring(0, t.length() - 3)) * BASE;
            if (t.endsWith("em")) return Float.parseFloat(t.substring(0, t.length() - 2)) * emPx;
            if (t.endsWith("%")) return Float.parseFloat(t.substring(0, t.length() - 1)) / 100f * emPx;
            if (t.endsWith("px")) return Float.parseFloat(t.substring(0, t.length() - 2));
            return Float.parseFloat(t);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int lineHeightOf(String v, int metric, float scale, float fontPx) {
        int normal = Math.max(1, Math.round(metric * scale));
        if (v == null || v.isBlank() || v.trim().equalsIgnoreCase("normal")) return normal;
        String t = v.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("px") || t.endsWith("em") || t.endsWith("%")) return Math.max(1, Math.round(length(t, fontPx, normal)));
            return Math.max(1, Math.round(Float.parseFloat(t) * fontPx));          // 무단위 = 글자 크기의 배수
        } catch (NumberFormatException e) {
            return normal;
        }
    }

    public float scale() { return scale; }

    public int lineHeight() { return lineHeight; }

    public boolean bold() { return bold; }

    public boolean ellipsis() { return ellipsis; }

    /** 폭에 맞춰 줄바꿈하는 white-space 값인가(normal, pre-wrap, pre-line). */
    public boolean wraps() { return whiteSpace.equals("normal") || whiteSpace.equals("pre-wrap") || whiteSpace.equals("pre-line"); }

    private boolean plain() { return scale == 1f && letterSpacing == 0 && !bold && !italic && fontId == null; }

    /** 글자를 그린다: 기울임/글꼴이 있으면 스타일 글자 명령으로, 아니면 기본 글자 명령으로. */
    private void put(UiGfx g, String text, int x, int y, int argb, boolean shadow) {
        if (italic || fontId != null) g.drawStyledText(text, x, y, argb, shadow, italic, fontId);
        else g.drawText(text, x, y, argb, shadow);
    }

    /** 한 줄의 폭(px): 배율, 자간, 굵게(+1px)를 반영. */
    public int width(String line) {
        if (plain()) return tm.width(line);
        int w;
        if (letterSpacing == 0) {
            w = Math.round(rawWidth(line) * scale);
        } else {
            float sum = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                sum += rawWidth(ch) * scale + letterSpacing;
                i += ch.length();
            }
            w = Math.round(sum);
        }
        return w + (bold ? Math.max(1, Math.round(scale)) : 0);
    }

    public String transform(String raw) {
        return switch (transform) {
            case "uppercase" -> raw.toUpperCase();
            case "lowercase" -> raw.toLowerCase();
            case "capitalize" -> {
                StringBuilder sb = new StringBuilder(raw.length());
                boolean start = true;
                for (int i = 0; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    sb.append(start && Character.isLetter(c) ? Character.toUpperCase(c) : c);
                    start = Character.isWhitespace(c);
                }
                yield sb.toString();
            }
            default -> raw;
        };
    }

    /**
     * 표시할 줄들. {@code maxWidth <= 0} 이거나 wrap 하지 않는 white-space 면 줄바꿈 없이(개행/공백 처리만) 나눈다.
     * white-space: pre(기본) = 개행 유지·공백 유지·줄바꿈 없음, nowrap = 공백 접기·줄바꿈 없음, normal = 공백 접기·줄바꿈,
     * pre-wrap = 개행·공백 유지·줄바꿈, pre-line = 개행 유지·공백 접기·줄바꿈.
     */
    public List<String> lines(String raw, int maxWidth) {
        String text = transform(raw);
        List<String> paragraphs = new ArrayList<>();
        switch (whiteSpace) {
            case "normal", "nowrap" -> paragraphs.add(text.replaceAll("\\s+", " ").strip());
            case "pre-line" -> {
                for (String p : text.split("\n", -1)) paragraphs.add(p.replaceAll("[ \\t\\r\\f]+", " ").strip());
            }
            default -> {
                for (String p : text.split("\n", -1)) paragraphs.add(p);
            }
        }
        if (!wraps() || maxWidth <= 0) return paragraphs;
        List<String> out = new ArrayList<>();
        for (String p : paragraphs) wrap(p, maxWidth, out);
        return out;
    }

    private void wrap(String paragraph, int maxWidth, List<String> out) {
        if (paragraph.isEmpty() || width(paragraph) <= maxWidth) {
            out.add(paragraph);
            return;
        }
        String cur = "";
        boolean started = false;
        for (String tok : paragraph.split(" ", -1)) {
            String candidate = started ? cur + " " + tok : tok;
            if (width(candidate) <= maxWidth) {
                cur = candidate;
                started = true;
                continue;
            }
            if (started) out.add(cur);
            cur = tok;
            started = true;
            while (width(cur) > maxWidth && cur.length() > 1) {            // 한 단어가 너무 길면 글자 단위로 자른다
                int cut = 1;
                while (cut < cur.length() && width(cur.substring(0, cut + 1)) <= maxWidth) cut++;
                out.add(cur.substring(0, cut));
                cur = cur.substring(cut);
            }
        }
        out.add(cur);
    }

    /** 가장 긴 줄의 폭(줄바꿈 없이). */
    public int maxWidth(String raw) {
        int w = 0;
        for (String line : lines(raw, 0)) w = Math.max(w, width(line));
        return w;
    }

    public int height(int lineCount) { return Math.max(0, lineCount) * lineHeight; }

    /** {@code maxWidth} 를 넘으면 끝을 잘라 "..." 를 붙인다. */
    public String fit(String line, int maxWidth) {
        if (maxWidth <= 0 || width(line) <= maxWidth) return line;
        String dots = "...";
        if (width(dots) >= maxWidth) return dots;
        int end = line.length();
        while (end > 0 && width(line.substring(0, end) + dots) > maxWidth) end--;
        return line.substring(0, end) + dots;
    }

    /** 한 줄을 (x, y) 에 그린다. 배율은 (x, y) 기준 변환, 자간은 글자 단위, 굵게는 1px 겹쳐 그리기. */
    public void draw(UiGfx g, String line, int x, int y, int argb, boolean shadow) {
        if (line.isEmpty()) return;
        if (plain()) {
            g.drawText(line, x, y, argb, shadow);
            return;
        }
        if (scale == 1f && letterSpacing == 0 && !bold) {            // 기울임/글꼴만 바뀐 경우
            put(g, line, x, y, argb, shadow);
            return;
        }
        boolean scaled = scale != 1f;
        if (scaled) g.pushTransform(0, 0, scale, 0, x, y);
        int dx = bold ? 1 : 0;
        for (int pass = 0; pass <= dx; pass++) {
            if (letterSpacing == 0) {
                put(g, line, x + pass, y, argb, shadow && pass == 0);
            } else {
                int cx = x + pass;
                for (int i = 0; i < line.length(); ) {
                    int cp = line.codePointAt(i);
                    String ch = new String(Character.toChars(cp));
                    put(g, ch, cx, y, argb, shadow && pass == 0);
                    cx += rawWidth(ch) + Math.round(letterSpacing / scale);
                    i += ch.length();
                }
            }
        }
        if (scaled) g.popTransform();
    }

    /** {@code text-decoration} 의 밑줄/취소선/윗줄 위치와 두께: {y 오프셋, 두께}. */
    public int thickness() { return Math.max(1, Math.round(scale)); }

    public int underlineOffset() { return Math.max(1, Math.round(BASE * scale)); }

    public int strikeOffset() { return Math.max(0, Math.round(BASE * scale / 2f)); }

    /** 이 박스의 글자가 놓일 폭: 박스 폭에서 안쪽 여백(+테두리)을 뺀다. 버튼은 기본 가로 여백 8. */
    public static int availableWidth(StyleMap s, String tag, int boxW) {
        Insets def = tag.equals("button") ? new Insets(4, 8, 4, 8) : Insets.ZERO;
        Insets p = s.insets("padding", def);
        Insets bw = s.borderInsets(0);
        return boxW - p.horizontal() - bw.left() - bw.right();
    }
}
