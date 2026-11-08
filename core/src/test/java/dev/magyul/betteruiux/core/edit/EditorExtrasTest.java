package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 정렬, 순서 이동, XML 보기, 상태 미리보기, 저장 충돌 확인, 툴바 툴팁. */
class EditorExtrasTest {
    private static final int W = 700, H = 300;

    private static final class Host implements EditorHost {
        String disk;
        int saves;

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException {
            disk = text;
            saves++;
        }

        @Override public String title() { return "t.xml"; }

        @Override public String diskText() { return disk; }
    }

    private Host host;

    private EditorController editor(String xml) throws Exception {
        host = new Host();
        return new EditorController(host, new FixedMetrics(), xml, W, H);
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    private static String attr(EditorController e, int idx, String n) {
        XmlSource s = XmlSource.parse(e.text());
        return s.attrValue(s.element(idx), n);
    }

    @Test
    void alignSetsTheAnchorAndClearsOffsetsInFreeContainers() throws Exception {
        EditorController e = editor("<screen><panel id=\"p\" x=\"150\" y=\"40\" width=\"50\" height=\"30\"/></screen>");
        e.select(1);
        e.alignSelected("bottom-right");
        assertEquals("bottom-right", attr(e, 1, "anchor"));
        assertNull(attr(e, 1, "x"));
        assertNull(attr(e, 1, "y"));
        e.alignSelected("top-left");
        assertNull(attr(e, 1, "anchor"), "기본 기준점이면 속성을 지운다");
    }

    @Test
    void alignInsideAColumnSetsCrossAxisAlignment() throws Exception {
        EditorController e = editor("<screen><column id=\"c\" x=\"150\" y=\"40\" width=\"100\"><label text=\"a\"/></column></screen>");
        e.select(2);
        e.alignSelected("bottom-right");
        assertEquals("end", attr(e, 2, "align-self"));
        e.alignSelected("left");
        assertEquals("start", attr(e, 2, "align-self"));
    }

    @Test
    void reorderMovesTheElementAmongItsSiblings() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"150\" y=\"40\"/><panel id=\"b\" x=\"150\" y=\"80\"/><panel id=\"c\" x=\"150\" y=\"120\"/></screen>");
        e.select(1);
        e.reorder(1);
        assertTrue(e.text().indexOf("id=\"a\"") > e.text().indexOf("id=\"b\""));
        assertEquals("a", attr(e, e.selectedIndex(), "id"), "옮겨도 같은 요소가 선택돼 있다");
        e.charTyped('[');
        assertTrue(e.text().indexOf("id=\"a\"") < e.text().indexOf("id=\"b\""));
        e.reorder(-1);                                                     // 맨 앞에서 더 앞으로는 아무 일 없음
        assertTrue(e.text().indexOf("id=\"a\"") < e.text().indexOf("id=\"b\""));
    }

    @Test
    void xmlViewShowsTheSourceAndCloses() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"150\" y=\"40\"/></screen>");
        Rects.clickText(e, "View");
        Rects.clickText(e, "XML 보기");
        DrawList l = paint(e);
        assertTrue(hasText(l, "XML (읽기 전용"));
        assertTrue(hasText(l, "<panel id=\"a\""));
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertFalse(hasText(paint(e), "XML (읽기 전용"));
    }

    @Test
    void forcedHoverStateShowsTheHoverStyleOfTheSelectedElement() throws Exception {
        EditorController e = editor("<screen><style>panel:hover { background: #FFFF0000 }</style>"
                + "<panel id=\"a\" x=\"150\" y=\"40\" width=\"50\" height=\"30\" background=\"#FF0000FF\"/></screen>");
        e.select(2);
        DrawList normal = new DrawList();
        e.paintDocument(normal);
        assertTrue(normal.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFF0000FF));
        Rects.clickText(e, "View");
        Rects.clickText(e, "상태 미리보기: hover");
        DrawList hover = new DrawList();
        e.paintDocument(hover);
        assertTrue(hover.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect f && f.argb() == 0xFFFF0000));
    }

    @Test
    void savingAfterAnExternalChangeNeedsASecondConfirmation() throws Exception {
        EditorController e = editor("<screen><panel id=\"a\" x=\"150\" y=\"40\"/></screen>");
        e.select(1);
        e.keyPressed(EditorKey.RIGHT, false, false);
        host.disk = "<screen/>";                                           // 다른 편집기가 파일을 바꿨다
        assertFalse(e.save());
        assertEquals(0, host.saves);
        assertTrue(e.status().contains("한 번 더"));
        assertTrue(e.save(), "한 번 더 저장하면 덮어쓴다");
        assertEquals(1, host.saves);
        e.keyPressed(EditorKey.RIGHT, false, false);
        assertTrue(e.save(), "자기가 쓴 파일은 충돌이 아니다");
    }

    @Test
    void toolbarButtonsShowATooltipAfterHovering() throws Exception {
        EditorController e = editor("<screen/>");
        long[] now = {0};
        e.clock = () -> now[0];
        DrawCommand.DrawText save = paint(e).commands().stream().filter(c -> c instanceof DrawCommand.DrawText t && t.text().equals("Save"))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow();
        e.mouseMoved(save.x() + 2, save.y() + 2);
        paint(e);
        assertFalse(hasText(paint(e), "저장 (Ctrl+S)"));
        now[0] += 600_000_000L;
        assertTrue(hasText(paint(e), "저장 (Ctrl+S)"));
        e.mouseMoved(300, 200);
        assertNotEquals(true, hasText(paint(e), "저장 (Ctrl+S)"));
    }

    /** 텍스트가 그려진 자리를 눌러 메뉴/버튼을 고르는 도우미. */
    private static final class Rects {
        static void clickText(EditorController e, String text) {
            DrawList l = new DrawList();
            e.paint(l);
            DrawCommand.DrawText t = l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                    .map(c -> (DrawCommand.DrawText) c).findFirst().orElseThrow(() -> new AssertionError("no text " + text));
            e.mouseMoved(t.x() + 2, t.y() + 2);
            e.mousePressed(t.x() + 2, t.y() + 2, 0);
            e.mouseReleased(t.x() + 2, t.y() + 2, 0);
        }
    }
}
