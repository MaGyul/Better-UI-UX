package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.EditorColors.C_SEL;
import static dev.magyul.betteruiux.core.edit.EditorColors.C_TEXT;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import java.util.ArrayList;
import java.util.List;

/** F1 / ? 도움말 오버레이: 조작표를 상자 너비에 맞춰 줄바꿈하고, 작은 창에서는 휠로 스크롤한다. */
final class HelpOverlay {
    private final TextMetrics tm;

    HelpOverlay(TextMetrics tm) { this.tm = tm; }


    boolean open;

    private static final String[] HELP = {
        "[ 만들기 ]  왼쪽 블록을 끌어다 놓기 / 클릭하면 선택한 컨테이너에 추가",
        "[ 선택 ]  요소 클릭 · 구조 트리 행 클릭 · Esc 선택 해제(한 번 더 누르면 에디터 닫기)",
        "[ 이동·크기 ]  끌어서 이동 · 손잡이로 크기 · 방향키 1px / Shift+방향키 8px",
        "[ 복제·복사 ]  Ctrl+D 복제 · Ctrl+C / Ctrl+X / Ctrl+V 복사·잘라내기·붙여넣기",
        "[ 구조 ]  Ctrl+G 컨테이너로 감싸기(Wrap) · Ctrl+Shift+G 풀기(Unwrap) · Delete 삭제",
        "[ 순서 ]  [ 뒤로 / ] 앞으로(같은 부모 안) · Align 으로 부모 안에서 정렬",
        "[ 실행 취소 ]  Ctrl+Z / Ctrl+Y · 저장 Ctrl+S",
        "[ 속성 ]  값 클릭 후 입력(Enter 확정 · Esc 취소) · -/+ 로 숫자 조절 · 목록·체크 칸은 클릭",
        "[ 화면 ]  휠/가운데 버튼으로 화면 이동 · Home/Fit 원위치 · Tab 패널 숨기기",
        "[ 패널 ]  가장자리 탭으로 접기 · 접힌 패널/툴바는 가장자리에 마우스를 올려 두면 나타남",
        "[ 기타 ]  F1 또는 ? 이 도움말 · F8 / Exit 에디터 닫기",
    };

    private int scroll;

    /** 도움말 줄을 상자 너비에 맞춰 줄바꿈한다(이어지는 줄은 들여쓴다). */
    private List<String> wrapHelp(int maxW) {
        List<String> out = new ArrayList<>();
        for (String line : HELP) {
            StringBuilder cur = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < line.length(); i++) {
                cur.append(line.charAt(i));
                if (tm.width(cur.toString()) > maxW && cur.length() > 1) {
                    cur.setLength(cur.length() - 1);
                    out.add(first ? cur.toString() : "  " + cur);
                    cur.setLength(0);
                    cur.append(line.charAt(i));
                    first = false;
                }
            }
            if (cur.length() > 0) out.add(first ? cur.toString() : "  " + cur);
        }
        return out;
    }

    void scrollBy(double amount) { scroll = Math.max(0, scroll - (int) Math.signum(amount) * 2); }

    void paint(UiGfx g, int viewW, int viewH, List<Region> regions) {
        if (!open) return;
        regions.add(new Region(new Rect(0, 0, viewW, viewH), () -> open = false));
        g.fillRect(0, 0, viewW, viewH, 0xB0000000);
        int w = Math.min(viewW - 16, 380);
        int lh = tm.lineHeight() + 3;
        List<String> lines = wrapHelp(w - 14);
        int rows = Math.max(1, Math.min(lines.size(), (viewH - 16 - 26) / lh));
        int h = rows * lh + 26;
        scroll = Math.max(0, Math.min(scroll, lines.size() - rows));
        int x = (viewW - w) / 2, y = Math.max(4, (viewH - h) / 2);
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        String title = "에디터 도움말  (아무 곳이나 누르면 닫힘" + (lines.size() > rows ? " · 휠로 스크롤" : "") + ")";
        g.drawText(title, x + 6, y + 5, C_SEL, false);
        g.pushClip(x + 4, y + 16, w - 8, h - 18);
        for (int i = 0; i < rows; i++) g.drawText(lines.get(scroll + i), x + 6, y + 18 + i * lh, C_TEXT, false);
        g.popClip();
        if (lines.size() > rows) {                                   // 스크롤 막대
            int trackH = h - 20, thumbH = Math.max(8, trackH * rows / lines.size());
            int thumbY = y + 16 + (trackH - thumbH) * scroll / Math.max(1, lines.size() - rows);
            g.fillRect(x + w - 4, y + 16, 2, trackH, 0x30FFFFFF);
            g.fillRect(x + w - 4, thumbY, 2, thumbH, 0xA0FFFFFF);
        }
    }
}
