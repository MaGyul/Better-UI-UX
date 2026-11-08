package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.EditorColors.C_DIM;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_SEL;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_TEXT;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import java.util.List;

/** 문서의 XML 원문을 읽기 전용으로 보여 주는 오버레이(선택한 요소의 줄 강조, 휠 스크롤). */
final class XmlViewOverlay {
    boolean open;
    private int scroll;

    void scrollBy(double amount) { scroll = Math.max(0, scroll - (int) Math.signum(amount) * 3); }

    /** @param selStart 강조할 첫 줄(0부터, 없으면 -1), selEnd 마지막 줄 */
    void paint(UiGfx g, TextMetrics tm, int viewW, int viewH, int top, List<Region> regions, String text, int selStart, int selEnd) {
        if (!open) return;
        regions.add(new Region(new Rect(0, 0, viewW, viewH), () -> open = false));
        g.fillRect(0, 0, viewW, viewH, 0xC0000000);
        int w = Math.min(viewW - 16, 420), lh = tm.lineHeight() + 1;
        int y0 = top + 4, h = viewH - y0 - 10, x = (viewW - w) / 2;
        g.fillRect(x - 1, y0 - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y0, w, h, 0xFF101018);
        String[] lines = text.split("\n", -1);
        int rows = Math.max(1, (h - 16) / lh);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.length - rows)));
        g.drawText("XML (읽기 전용 · 휠로 스크롤 · 아무 곳이나 누르면 닫힘)", x + 4, y0 + 3, C_DIM, false);
        g.pushClip(x, y0 + 14, w, h - 14);
        for (int i = 0; i < rows && scroll + i < lines.length; i++) {
            int ln = scroll + i;
            int ly = y0 + 15 + i * lh;
            if (ln >= selStart && ln <= selEnd) g.fillRect(x + 1, ly - 1, w - 2, lh, 0xFF26304F);
            g.drawText(String.format("%3d", ln + 1), x + 3, ly, C_DIM, false);
            g.drawText(lines[ln].replace("\t", "  "), x + 26, ly, C_TEXT, false);
        }
        g.popClip();
    }
}
