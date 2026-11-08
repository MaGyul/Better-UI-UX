package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.EditorColors.C_DIM;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_FIELD;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_SEL;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_TEXT;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 아이템 선택 상자: 아이콘 격자(8열) + 휠 스크롤 + id 검색. 고른 id 는 콜백으로 돌려준다. */
final class ItemPickerOverlay {
    static final int COLS = 8, CELL = 18, ROWS = 6;

    private boolean open;
    private Rect near;
    private final StringBuilder filter = new StringBuilder();
    private int scrollRow;
    private List<String> ids = List.of();

    boolean isOpen() { return open; }

    void open(Rect near, List<String> ids) {
        this.open = true;
        this.near = near;
        this.ids = ids;
        filter.setLength(0);
        scrollRow = 0;
    }

    void close() { open = false; }

    void type(char c) {
        if (c < 32 || c == 127) return;
        filter.append(c);
        scrollRow = 0;
    }

    void backspace() {
        if (filter.length() == 0) return;
        filter.setLength(filter.length() - 1);
        scrollRow = 0;
    }

    void scroll(double amount) { scrollRow = Math.max(0, scrollRow - (int) Math.signum(amount) * 2); }

    /** 검색창 자리(글자 입력/IME 를 켤 때 알려 주는 위치). */
    Rect searchArea(int viewW, int toolbarH) {
        int w = COLS * CELL + 10;
        return new Rect(Math.max(2, (near == null ? viewW : near.x()) - w - 4), near == null ? toolbarH : near.y(), w, 11);
    }

    private List<String> filtered() {
        String q = filter.toString().trim().toLowerCase();
        if (q.isEmpty()) return ids;
        List<String> out = new ArrayList<>();
        for (String id : ids) if (id.toLowerCase().contains(q)) out.add(id);
        return out;
    }

    void paint(UiGfx g, TextMetrics tm, int viewW, int viewH, int toolbarH, int mouseX, int mouseY, List<Region> regions,
               String current, Consumer<String> pick, Runnable onClose) {
        if (!open) return;
        regions.add(new Region(new Rect(0, 0, viewW, viewH), onClose));                       // 바깥을 누르면 닫는다
        List<String> items = filtered();
        int w = COLS * CELL + 10, h = ROWS * CELL + 30;
        int x = Math.max(2, Math.min((near == null ? viewW : near.x()) - w - 4, viewW - w - 2));
        int y = Math.max(toolbarH + 2, Math.min(near == null ? toolbarH : near.y(), viewH - h - 2));
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        regions.add(new Region(new Rect(x, y, w, h), null));
        int totalRows = (items.size() + COLS - 1) / COLS;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - ROWS)));
        String q = filter.toString();
        g.fillRect(x + 4, y + 4, w - 8, 11, C_FIELD);
        g.drawText(q.isEmpty() ? "검색... (글자를 입력)" : q + "_", x + 7, y + 6, q.isEmpty() ? C_DIM : C_TEXT, false);
        g.drawText(items.size() + "개", x + w - 6 - tm.width(items.size() + "개"), y + 6, C_DIM, false);
        int gx = x + 5, gy = y + 20;
        String hoverId = null;
        g.pushClip(gx, gy, COLS * CELL, ROWS * CELL);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (scrollRow + r) * COLS + c;
                if (i >= items.size()) break;
                String id = items.get(i);
                Rect cell = new Rect(gx + c * CELL, gy + r * CELL, CELL, CELL);
                boolean hot = cell.contains(mouseX, mouseY);
                if (hot) hoverId = id;
                g.fillRect(cell.x() + 1, cell.y() + 1, CELL - 2, CELL - 2, hot ? 0xFF3A4A80 : id.equals(current) ? 0xFF2E5A3A : 0xFF222536);
                g.drawItem(id, cell.x() + 1, cell.y() + 1, 1);
                regions.add(new Region(cell, () -> pick.accept(id)));
            }
        }
        g.popClip();
        if (totalRows > ROWS) {                                                              // 스크롤 막대
            int trackH = ROWS * CELL, thumbH = Math.max(8, trackH * ROWS / totalRows);
            int thumbY = gy + (trackH - thumbH) * scrollRow / Math.max(1, totalRows - ROWS);
            g.fillRect(x + w - 4, gy, 2, trackH, 0x30FFFFFF);
            g.fillRect(x + w - 4, thumbY, 2, thumbH, 0xA0FFFFFF);
        }
        if (hoverId != null) {                                                               // 마우스 아래 아이템 id
            int tw = tm.width(hoverId) + 8;
            int tx = Math.max(2, Math.min(mouseX + 8, viewW - tw - 2)), ty = Math.min(mouseY + 12, viewH - tm.lineHeight() - 8);
            g.fillRect(tx - 1, ty - 1, tw + 2, tm.lineHeight() + 6, 0xF0300070);
            g.fillRect(tx, ty, tw, tm.lineHeight() + 4, 0xF0100010);
            g.drawText(hoverId, tx + 4, ty + 2, C_TEXT, false);
        }
    }
}
