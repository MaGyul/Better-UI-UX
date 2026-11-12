package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.Colors;
import java.util.ArrayList;
import java.util.List;

/**
 * 속성 패널의 색 칸을 편집할 때 아래에 펼쳐지는 컬러피커. 마인크래프트를 모르는 순수 객체다.
 *
 * <ul>
 *   <li>채도/명도 사각형 + 색조 막대 + 투명도 막대 + 미리보기 + 마인크래프트 기본 16색/투명 프리셋</li>
 *   <li>값은 {@code #RRGGBB}(불투명) 또는 {@code #RRGGBBAA}(반투명) 문자열로 오가며, 칸의 텍스트와 항상 같이 움직인다
 *       (직접 입력하면 피커가 따라오고, 피커를 만지면 칸의 텍스트가 바뀐다).</li>
 * </ul>
 */
final class ColorPicker {
    static final int SV_W = 100, SV_H = 56, STRIP = 12, CELL = 4, AREA_H = 100;
    private static final int[] PRESETS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final int C_BG = 0xFF0E1018, C_BORDER = 0xFF55557A, C_CHECK = 0xFF2A2D3E;

    private enum Mode { NONE, SV, HUE, ALPHA }

    private float h, s = 1f, v = 1f, a = 1f;     // h: 0..360
    private String syncedText = "";
    private Mode mode = Mode.NONE;
    private Rect area = new Rect(0, 0, 0, 0), sv = area, hue = area, alpha = area, preview = area;
    private final List<Rect> presetRects = new ArrayList<>();
    private final List<Integer> presetColors = new ArrayList<>();   // 0xAARRGGBB

    /** 텍스트에서 색을 읽어 피커를 맞춘다. 읽을 수 없으면(예: var(--x)) 이전 값을 유지한다. */
    void open(String text) {
        syncedText = null;
        syncFrom(text);
        mode = Mode.NONE;
    }

    /** 칸의 텍스트가 피커가 마지막으로 낸 값과 다르면(직접 입력) 그 색으로 피커를 옮긴다. */
    void syncFrom(String text) {
        if (text.equals(syncedText)) return;
        syncedText = text;
        Integer c = Colors.tryParse(text);
        if (c == null) return;
        a = ((c >>> 24) & 0xFF) / 255f;
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        v = max;
        s = max == 0 ? 0 : d / max;
        if (d == 0) {
            // 회색이면 색조는 의미가 없으므로 지금 값을 유지한다
        } else if (max == r) {
            h = 60f * (((g - b) / d) % 6f);
        } else if (max == g) {
            h = 60f * ((b - r) / d + 2f);
        } else {
            h = 60f * ((r - g) / d + 4f);
        }
        if (h < 0) h += 360f;
    }

    /** 필드 칸 아래(공간이 없으면 위)에 놓일 영역을 계산한다. */
    void layout(Rect field, int panelX, int panelW, int minY, int viewH) {
        int top = field.bottom() + 2;
        if (top + AREA_H > viewH) top = Math.max(minY, field.y() - AREA_H - 2);
        area = new Rect(panelX + 3, top, panelW - 6, AREA_H);
        sv = new Rect(area.x() + 4, area.y() + 4, SV_W, SV_H);
        hue = new Rect(sv.right() + 6, sv.y(), STRIP, SV_H);
        alpha = new Rect(sv.x(), sv.bottom() + 6, SV_W, 8);
        preview = new Rect(hue.x(), alpha.y(), STRIP, 8);
        presetRects.clear();
        presetColors.clear();
        int y0 = alpha.bottom() + 6;
        // 기본 16색 + 마지막은 투명. 12px 칸을 14px 간격으로 9개씩 두 줄(영역 안에 딱 맞는다).
        for (int i = 0; i <= PRESETS.length; i++) {
            presetRects.add(new Rect(area.x() + 4 + (i % 9) * 14, y0 + (i / 9) * 10, 12, 8));
            presetColors.add(i < PRESETS.length ? 0xFF000000 | PRESETS[i] : 0x00000000);
        }
    }

    Rect area() { return area; }

    boolean contains(int x, int y) { return area.contains(x, y); }

    boolean dragging() { return mode != Mode.NONE; }

    void release() { mode = Mode.NONE; }

    /** 눌렀을 때: 어느 부분인지 정하고 값을 바꾼다. 바뀐 색 문자열을 돌려주고, 바뀌지 않았으면 null. */
    String press(int x, int y) {
        for (int i = 0; i < presetRects.size(); i++) {
            if (presetRects.get(i).contains(x, y)) {
                setArgb(presetColors.get(i));
                return publish();
            }
        }
        if (sv.contains(x, y)) mode = Mode.SV;
        else if (hue.contains(x, y)) mode = Mode.HUE;
        else if (alpha.contains(x, y)) mode = Mode.ALPHA;
        else return null;
        return drag(x, y);
    }

    /** 끄는 동안: 영역 밖으로 나가도 가장자리 값으로 고정한다. */
    String drag(int x, int y) {
        switch (mode) {
            case SV -> {
                s = clamp01((x - sv.x()) / (float) (sv.w() - 1));
                v = 1f - clamp01((y - sv.y()) / (float) (sv.h() - 1));
            }
            case HUE -> h = clamp01((y - hue.y()) / (float) (hue.h() - 1)) * 359.99f;
            case ALPHA -> a = clamp01((x - alpha.x()) / (float) (alpha.w() - 1));
            default -> { return null; }
        }
        return publish();
    }

    private void setArgb(int argb) {
        syncedText = null;
        syncFrom(Colors.toCssFull(argb));
        if ((argb & 0xFFFFFF) == 0 && (argb >>> 24) == 0) a = 0f;
    }

    private String publish() {
        String text = hex();
        syncedText = text;
        return text;
    }

    // ---- 값 ----

    int argb() {
        int[] rgb = hsvToRgb(h, s, v);
        return (Math.round(a * 255) << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /** 불투명이면 {@code #RRGGBB}, 아니면 {@code #RRGGBBAA}. */
    String hex() {
        int c = argb();
        return Colors.toCss(c);
    }

    static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s, hh = (h % 360f) / 60f, x = c * (1 - Math.abs(hh % 2 - 1)), m = v - c;
        float r, g, b;
        if (hh < 1) { r = c; g = x; b = 0; }
        else if (hh < 2) { r = x; g = c; b = 0; }
        else if (hh < 3) { r = 0; g = c; b = x; }
        else if (hh < 4) { r = 0; g = x; b = c; }
        else if (hh < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new int[] {Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)};
    }

    private static float clamp01(float f) { return Math.max(0f, Math.min(1f, f)); }

    // ---- 그리기 ----

    void paint(UiGfx g) {
        g.fillRect(area.x(), area.y(), area.w(), area.h(), C_BORDER);
        g.fillRect(area.x() + 1, area.y() + 1, area.w() - 2, area.h() - 2, C_BG);

        int cols = SV_W / CELL, rows = SV_H / CELL;
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                int[] rgb = hsvToRgb(h, (cx + 0.5f) / cols, 1f - (cy + 0.5f) / rows);
                g.fillRect(sv.x() + cx * CELL, sv.y() + cy * CELL, CELL, CELL, 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2]);
            }
        }
        for (int cy = 0; cy < rows; cy++) {                              // 색조 막대
            int[] rgb = hsvToRgb((cy + 0.5f) / rows * 360f, 1f, 1f);
            g.fillRect(hue.x(), hue.y() + cy * CELL, hue.w(), CELL, 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2]);
        }
        int[] rgb = hsvToRgb(h, s, v);
        int opaque = 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
        g.fillRect(alpha.x(), alpha.y(), alpha.w(), alpha.h(), C_CHECK);  // 투명도 막대(바탕 위에 겹쳐 그려 투명함이 보이게)
        for (int cx = 0; cx < cols; cx++) {
            int al = Math.round((cx + 0.5f) / cols * 255);
            g.fillRect(alpha.x() + cx * CELL, alpha.y(), CELL, alpha.h(), (al << 24) | (opaque & 0xFFFFFF));
        }
        g.fillRect(preview.x(), preview.y(), preview.w(), preview.h(), C_CHECK);
        g.fillRect(preview.x(), preview.y(), preview.w(), preview.h(), argb());

        // 표시(현재 위치)
        marker(g, sv.x() + Math.round(s * (sv.w() - 1)), sv.y() + Math.round((1f - v) * (sv.h() - 1)), 5);
        g.fillRect(hue.x() - 1, hue.y() + Math.round(h / 359.99f * (hue.h() - 1)), hue.w() + 2, 1, 0xFFFFFFFF);
        g.fillRect(alpha.x() + Math.round(a * (alpha.w() - 1)), alpha.y() - 1, 1, alpha.h() + 2, 0xFFFFFFFF);

        int current = argb();
        for (int i = 0; i < presetRects.size(); i++) {
            Rect r = presetRects.get(i);
            int c = presetColors.get(i);
            g.fillRect(r.x(), r.y(), r.w(), r.h(), C_CHECK);
            g.fillRect(r.x(), r.y(), r.w(), r.h(), c);
            if (c == current) {                                          // 지금 색과 같은 프리셋을 테두리로 표시
                g.fillRect(r.x(), r.y(), r.w(), 1, 0xFFFFFFFF);
                g.fillRect(r.x(), r.bottom() - 1, r.w(), 1, 0xFFFFFFFF);
            }
        }
    }

    /** 속이 빈 두 겹 사각형(흰 테두리 + 검은 바깥 테두리). 밑의 색이 가려지지 않는다. */
    private static void marker(UiGfx g, int cx, int cy, int size) {
        int r = size / 2;
        ring(g, cx - r - 1, cy - r - 1, size + 2, 0xFF000000);
        ring(g, cx - r, cy - r, size, 0xFFFFFFFF);
    }

    private static void ring(UiGfx g, int x, int y, int size, int color) {
        g.fillRect(x, y, size, 1, color);
        g.fillRect(x, y + size - 1, size, 1, color);
        g.fillRect(x, y, 1, size, color);
        g.fillRect(x + size - 1, y, 1, size, color);
    }
}
