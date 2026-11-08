package dev.magyul.betteruiux.core.render;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import dev.magyul.betteruiux.core.layout.StyleMap;
import java.util.function.Function;

/** 레이아웃 결과를 {@link UiGfx} 명령으로 그린다. 입력/시간 상태는 {@link PaintState} 로 받는다. */
public final class UiPainter {
    /** 호버/눌림/마우스/시간 상태 조회. 상태 없는 정적 렌더는 {@link #NONE}. */
    public interface PaintState {
        PaintState NONE = new PaintState() { };

        default boolean hovered(LayoutBox b) { return false; }

        default boolean pressed(LayoutBox b) { return false; }

        /** 키보드 포커스가 있는 박스(Tab 으로 고른 버튼 등). */
        default boolean focused(LayoutBox b) { return false; }

        /** 이 박스를 그릴 때 쓸 스타일(호버/눌림 오버레이, 전환·애니메이션 적용 후). */
        default StyleMap style(LayoutBox b) { return b.effective(hovered(b), pressed(b)); }

        default int mouseX() { return -1; }

        default int mouseY() { return -1; }

        default long nowMillis() { return 0; }

        /** 커스텀 위젯에 넘길 요소 핸들(문서가 없으면 null). */
        default UiElement element(LayoutBox b) { return null; }
    }

    private final TextMetrics text;
    private final Function<String, String> interpolator;

    public UiPainter(TextMetrics text) { this(text, s -> s); }

    /** @param interpolator 텍스트의 {@code {이름}} 바인딩 치환 */
    public UiPainter(TextMetrics text, Function<String, String> interpolator) {
        this.text = text;
        this.interpolator = interpolator;
    }

    public void paint(LayoutBox root, UiGfx g, PaintState state) {
        paintBox(root, g, state, 1f);
    }

    private void paintBox(LayoutBox b, UiGfx g, PaintState state, float parentOpacity) {
        if (!b.visible()) return;
        boolean hovered = state.hovered(b), pressed = state.pressed(b);
        StyleMap s = state.style(b);

        // 불투명도는 자식에게 곱해서 전달한다(CSS 의 opacity 처럼 서브트리 전체가 함께 흐려진다).
        float opacity = parentOpacity * clamp01(number(s, "opacity", 1f));

        // transform: translate-x/translate-y/scale/rotate(도). 요소의 중심 기준이며 자식에게도 적용된다.
        float tx = number(s, "translate-x", 0f), ty = number(s, "translate-y", 0f);
        float scale = number(s, "scale", 1f), rotate = number(s, "rotate", 0f);
        boolean transformed = tx != 0f || ty != 0f || scale != 1f || rotate != 0f;
        if (transformed) g.pushTransform(tx, ty, scale, rotate, b.x + b.w / 2f, b.y + b.h / 2f);

        if (b.tag().equals("dialog")) {                      // 대화상자 뒤를 화면 전체에 걸쳐 어둡게
            LayoutBox r = b;
            while (r.parent != null) r = r.parent;
            fill(g, r.x, r.y, r.w, r.h, s.color("backdrop", 0x90000000), opacity);
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

        if (button && state.focused(b)) {                    // Tab 으로 고른 버튼 표시
            int fc = 0xFFFFFF55;
            fill(g, b.x - 1, b.y - 1, b.w + 2, 1, fc, opacity);
            fill(g, b.x - 1, b.y + b.h, b.w + 2, 1, fc, opacity);
            fill(g, b.x - 1, b.y, 1, b.h, fc, opacity);
            fill(g, b.x + b.w, b.y, 1, b.h, fc, opacity);
        }
        switch (b.tag()) {
            case "label", "button" -> paintText(b, s, g, opacity, button && !b.enabled());
            case "image", "video" -> paintImage(b, s, g, opacity);
            case "item" -> {
                String item = s.get("item");
                if (item != null && !item.isBlank()) {
                    // 상자 크기에 맞춰 키우고 가운데에 놓는다(아이콘 기본 크기 16)
                    float iconScale = Math.max(0.1f, Math.min(b.w, b.h) / 16f);
                    boolean scaled = Math.abs(iconScale - 1f) > 0.01f;
                    if (scaled) g.pushTransform((b.w - 16 * iconScale) / 2f, (b.h - 16 * iconScale) / 2f, iconScale, 0f, b.x, b.y);
                    g.drawItem(item.trim(), b.x, b.y, Math.max(1, s.integer("count", 1)));
                    if (scaled) g.popTransform();
                }
            }
            default -> { }
        }
        UiWidget.Frame frame = b.widget == null ? null : new UiWidget.Frame(
                state.mouseX(), state.mouseY(), hovered, state.nowMillis(), state.element(b));
        if (b.widget != null) b.widget.paint(g, b.x, b.y, b.w, b.h, frame);

        boolean clip = "hidden".equals(s.get("overflow")) || b.tag().equals("scroll");
        if (clip) g.pushClip(b.x, b.y, b.w, b.h);
        for (LayoutBox c : b.children) paintBox(c, g, state, opacity);
        if (b.widget != null) b.widget.paintOver(g, b.x, b.y, b.w, b.h, frame);
        if (clip) g.popClip();

        if (transformed) g.popTransform();
    }

    private void paintText(LayoutBox b, StyleMap s, UiGfx g, float opacity, boolean dim) {
        String t = interpolator.apply(LayoutEngine.rawText(b));
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

    private static float number(StyleMap s, String key, float def) {
        String v = s.get(key);
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim().replace("px", "").replace("deg", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

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
