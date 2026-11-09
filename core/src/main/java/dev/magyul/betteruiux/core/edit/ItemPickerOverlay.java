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
    static final int ICON_COLS = 8, ICON_CELL = 18, ICON_ROWS = 6, TEXT_ROW_H = 11, TEXT_ROWS = 12;

    /** true 면 아이콘 격자 대신 글자 목록(이미지/영상 파일 고르기). */
    private boolean textMode;
    private int cols = ICON_COLS, cell = ICON_CELL, cellH = ICON_CELL, rows = ICON_ROWS;

    private boolean open;
    private Rect near;
    private final StringBuilder filter = new StringBuilder();
    private int scrollRow;
    private List<String> ids = List.of();

    boolean isOpen() { return open; }

    void open(Rect near, List<String> ids) { open(near, ids, false); }

    void open(Rect near, List<String> ids, boolean textMode) {
        this.textMode = textMode;
        cols = textMode ? 1 : ICON_COLS;
        cell = textMode ? ICON_COLS * ICON_CELL : ICON_CELL;
        cellH = textMode ? TEXT_ROW_H : ICON_CELL;
        rows = textMode ? TEXT_ROWS : ICON_ROWS;
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
        int w = cols * cell + 10;
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
        int w = cols * cell + 10, h = rows * cellH + 30;
        int x = Math.max(2, Math.min((near == null ? viewW : near.x()) - w - 4, viewW - w - 2));
        int y = Math.max(toolbarH + 2, Math.min(near == null ? toolbarH : near.y(), viewH - h - 2));
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        regions.add(new Region(new Rect(x, y, w, h), null));
        int totalRows = (items.size() + cols - 1) / cols;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - rows)));
        String q = filter.toString();
        g.fillRect(x + 4, y + 4, w - 8, 11, C_FIELD);
        g.drawText(q.isEmpty() ? "검색... (글자를 입력)" : q + "_", x + 7, y + 6, q.isEmpty() ? C_DIM : C_TEXT, false);
        g.drawText(items.size() + "개", x + w - 6 - tm.width(items.size() + "개"), y + 6, C_DIM, false);
        int gx = x + 5, gy = y + 20;
        String hoverId = null;
        g.pushClip(gx, gy, cols * cell, rows * cellH);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scrollRow + r) * cols + c;
                if (i >= items.size()) break;
                String id = items.get(i);
                Rect box = new Rect(gx + c * cell, gy + r * cellH, cell, cellH);
                boolean hot = box.contains(mouseX, mouseY);
                if (hot && !textMode) hoverId = id;
                g.fillRect(box.x() + 1, box.y() + 1, cell - 2, cellH - 2, hot ? 0xFF3A4A80 : id.equals(current) ? 0xFF2E5A3A : 0xFF222536);
                if (textMode) g.drawText(id, box.x() + 4, box.y() + 2, C_TEXT, false);
                else g.drawItem(id, box.x() + 1, box.y() + 1, 1);
                regions.add(new Region(box, () -> pick.accept(id)));
            }
        }
        g.popClip();
        if (totalRows > rows) {                                                              // 스크롤 막대
            int trackH = rows * cellH, thumbH = Math.max(8, trackH * rows / totalRows);
            int thumbY = gy + (trackH - thumbH) * scrollRow / Math.max(1, totalRows - rows);
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
