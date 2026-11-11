package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.EditorColors.C_ROW_SEL;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_SEL;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_TEXT;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import java.util.List;
import java.util.function.Consumer;

/** 도구 모음/속성 칸에서 펼치는 간단한 드롭다운 메뉴. 항목을 고르면 닫히고 콜백에 항목 글자를 돌려준다. */
final class MenuOverlay {
    private Rect near;
    private List<String> items = List.of();
    private Consumer<String> pick;

    boolean isOpen() { return near != null; }

    /** {@code near} 바로 아래(공간이 모자라면 위)에 {@code items} 를 펼친다. */
    void open(Rect near, List<String> items, Consumer<String> pick) {
        this.near = near;
        this.items = items;
        this.pick = pick;
    }

    void close() {
        near = null;
        items = List.of();
        pick = null;
    }

    void paint(UiGfx g, TextMetrics tm, int viewW, int viewH, int rowH, int mouseX, int mouseY, List<Region> regions) {
        if (near == null || items.isEmpty()) return;
        regions.add(new Region(new Rect(0, 0, viewW, viewH), this::close));       // 바깥을 누르면 닫는다
        int w = near.w();
        for (String s : items) w = Math.max(w, tm.width(s) + 10);
        int h = items.size() * rowH + 2;
        int x = Math.max(2, Math.min(near.x(), viewW - w - 2));
        int y = near.bottom() + 1;
        if (y + h > viewH) y = Math.max(2, near.y() - h - 1);
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        regions.add(new Region(new Rect(x, y, w, h), null));
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            Rect row = new Rect(x, y + 1 + i * rowH, w, rowH);
            if (row.contains(mouseX, mouseY)) g.fillRect(row.x(), row.y(), row.w(), row.h(), C_ROW_SEL);
            g.drawText(item, x + 4, row.y() + 1, C_TEXT, false);
            regions.add(new Region(row, () -> {
                Consumer<String> chosen = pick;
                close();
                if (chosen != null) chosen.accept(item);
            }));
        }
    }
}
