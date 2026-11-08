package dev.magyul.betteruiux.core.render;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import dev.magyul.betteruiux.core.layout.StyleMap;

/** 레이아웃 결과를 {@link UiGfx} 명령으로 그린다. 상태(hover/pressed)는 {@link PaintState} 로 받는다. */
public final class UiPainter {
    /** 호버/눌림 상태 조회. 상태 없는 정적 렌더는 {@link #NONE}. */
    public interface PaintState {
        PaintState NONE = new PaintState() {
            @Override public boolean hovered(LayoutBox b) { return false; }

            @Override public boolean pressed(LayoutBox b) { return false; }
        };

        boolean hovered(LayoutBox b);

        boolean pressed(LayoutBox b);
    }

    private final TextMetrics text;

    public UiPainter(TextMetrics text) { this.text = text; }

    public void paint(LayoutBox root, UiGfx g, PaintState state) {
        paintBox(root, g, state);
    }

    private void paintBox(LayoutBox b, UiGfx g, PaintState state) {
        if (!b.visible()) return;
        boolean hovered = state.hovered(b), pressed = state.pressed(b);
        // 스타일시트의 :hover/:active 규칙이 반영된 그리기용 스타일 (레이아웃은 상태와 무관한 b.style 사용)
        StyleMap s = b.effective(hovered, pressed);
        float opacity = 1f;
        if (s.has("opacity")) {
            try {
                opacity = Math.max(0f, Math.min(1f, Float.parseFloat(s.get("opacity"))));
            } catch (NumberFormatException ignored) {
                // 잘못된 값은 불투명으로 처리
            }
        }

        boolean button = b.tag().equals("button");
        // 버튼 기본색: 스타일시트/속성에 background 가 있으면 그것이 우선, 없으면 상태별 기본색
        int defaultBg = 0;
        if (button) {
            if (!b.enabled()) defaultBg = s.color("disabled-background", 0xFF333333);
            else if (pressed) defaultBg = s.color("pressed-background", 0xFF333333);
            else if (hovered) defaultBg = s.color("hover-background", 0xFF777777);
            else defaultBg = 0xFF555555;
        }
        fill(g, b.x, b.y, b.w, b.h, s.color("background", defaultBg), opacity);

        int borderW = s.integer("border", button ? 1 : 0);
        if (borderW > 0) {
            int bc = s.color("border-color", 0xFF000000);
            fill(g, b.x, b.y, b.w, borderW, bc, opacity);
            fill(g, b.x, b.y + b.h - borderW, b.w, borderW, bc, opacity);
            fill(g, b.x, b.y + borderW, borderW, b.h - 2 * borderW, bc, opacity);
            fill(g, b.x + b.w - borderW, b.y + borderW, borderW, b.h - 2 * borderW, bc, opacity);
        }

        switch (b.tag()) {
            case "label", "button" -> paintText(b, s, g, opacity, button && !b.enabled());
            case "image" -> paintImage(b, s, g, opacity);
            default -> { }
        }

        boolean clip = "hidden".equals(s.get("overflow"));
        if (clip) g.pushClip(b.x, b.y, b.w, b.h);
        for (LayoutBox c : b.children) paintBox(c, g, state);
        if (clip) g.popClip();
    }

    private void paintText(LayoutBox b, StyleMap s, UiGfx g, float opacity, boolean dim) {
        String t = LayoutEngine.textOf(b);
        if (t.isEmpty()) return;
        boolean button = b.tag().equals("button");
        int color = s.color("color", dim ? 0xFF888888 : 0xFFFFFFFF);
        boolean shadow = s.bool("shadow", true);
        String align = s.get("text-align", button ? "center" : "left");
        int lineH = text.lineHeight();
        String[] lines = t.split("\n", -1);
        int totalH = lines.length * lineH;
        int y = b.y + (button ? (b.h - totalH) / 2 : 0);
        for (String line : lines) {
            int w = text.width(line);
            int x = switch (align) {
                case "center" -> b.x + (b.w - w) / 2;
                case "right" -> b.x + b.w - w;
                default -> b.x;
            };
            g.drawText(line, x, y, withOpacity(color, opacity), shadow);
            y += lineH;
        }
    }

    private void paintImage(LayoutBox b, StyleMap s, UiGfx g, float opacity) {
        String src = s.get("src");
        if (src == null || src.isBlank()) return;
        float u = 0, v = 0;
        int rw = b.w, rh = b.h, tw = b.w, th = b.h;
        int[] uv = ints(s.get("uv"), 4);
        if (uv != null) {
            u = uv[0];
            v = uv[1];
            rw = uv[2];
            rh = uv[3];
        }
        int[] tex = ints(s.get("tex"), 2);
        if (tex != null) {
            tw = tex[0];
            th = tex[1];
        }
        g.drawTexture(src, b.x, b.y, b.w, b.h, u, v, rw, rh, tw, th, withOpacity(s.color("tint", 0xFFFFFFFF), opacity));
    }

    private static int[] ints(String s, int n) {
        if (s == null) return null;
        String[] p = s.trim().split("[\\s,]+");
        if (p.length != n) return null;
        int[] r = new int[n];
        try {
            for (int i = 0; i < n; i++) r[i] = Math.round(Float.parseFloat(p[i]));
        } catch (NumberFormatException e) {
            return null;
        }
        return r;
    }

    private static void fill(UiGfx g, int x, int y, int w, int h, int argb, float opacity) {
        int c = withOpacity(argb, opacity);
        if ((c >>> 24) == 0 || w <= 0 || h <= 0) return;
        g.fillRect(x, y, w, h, c);
    }

    private static int withOpacity(int argb, float opacity) {
        if (opacity >= 1f) return argb;
        int a = Math.round((argb >>> 24) * opacity);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 색상 파서 공개용(에디터 등). */
    public static int parseColor(String s, int def) { return Colors.parse(s, def); }
}
