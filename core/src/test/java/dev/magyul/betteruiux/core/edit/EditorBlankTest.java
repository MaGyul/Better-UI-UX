package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 빈 에디터(/uiux editor): 화면 없이 열려 목록에서 파일을 고른다. */
class EditorBlankTest {
    private static final class Host implements EditorHost {
        final List<String> files;
        final List<String> switched = new ArrayList<>();
        final List<Integer> created = new ArrayList<>();
        int saves;

        Host(List<String> files) { this.files = files; }

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { saves++; }

        @Override public String title() { return "(열린 화면 없음)"; }

        @Override public boolean isBlank() { return true; }

        @Override public List<String> screens() { return files; }

        @Override public void switchTo(String id) { switched.add(id); }

        @Override public String createScreen(int kind) { created.add(kind); return "new_1"; }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).findFirst().orElse(null);
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static EditorController open(Host h) throws Exception { return new EditorController(h, new FixedMetrics(), "<screen/>", 600, 300); }

    @Test
    void showsAPickerInsteadOfPanelsAndEditingTools() throws Exception {
        EditorController e = open(new Host(List.of("demo", "shop")));
        DrawList l = paint(e);
        assertTrue(find(l, "편집할 화면을 고르세요") != null, "안내 제목");
        assertTrue(find(l, "열기: demo") != null && find(l, "열기: shop") != null, "화면 목록");
        assertTrue(find(l, "새 화면: 빈 화면") != null && find(l, "새 화면: 대화상자") != null);
        assertTrue(find(l, "File") != null, "File 버튼은 있다");
        assertTrue(find(l, "Exit") != null);
        assertTrue(find(l, "Save") == null && find(l, "Undo") == null && find(l, "Dup") == null, "편집 도구는 없다");
        assertTrue(find(l, "라벨") == null, "블록 팔레트는 없다");
    }

    @Test
    void emptyFolderSaysSo() throws Exception {
        EditorController e = open(new Host(List.of()));
        assertTrue(find(paint(e), "저장된 화면이 없어요 — 아래에서 새로 만드세요") != null);
    }

    @Test
    void clickingAListedScreenOpensIt() throws Exception {
        Host h = new Host(List.of("demo", "shop"));
        EditorController e = open(h);
        DrawCommand.DrawText row = find(paint(e), "열기: shop");
        click(e, row.x() + 2, row.y() + 2);
        assertEquals(List.of("shop"), h.switched);
    }

    @Test
    void newScreenFromTheListCreatesAndOpensIt() throws Exception {
        Host h = new Host(List.of());
        EditorController e = open(h);
        DrawCommand.DrawText row = find(paint(e), "새 화면: 대화상자");
        click(e, row.x() + 2, row.y() + 2);
        assertEquals(List.of(1), h.created);
        assertEquals(List.of("new_1"), h.switched);
    }

    @Test
    void fileMenuWorksToo() throws Exception {
        Host h = new Host(List.of("demo"));
        EditorController e = open(h);
        DrawCommand.DrawText file = find(paint(e), "File");
        click(e, file.x() + 2, file.y() + 2);
        DrawList open = paint(e);
        // 메뉴의 "열기: demo" 와 가운데 목록의 같은 글자가 둘 다 그려진다
        long count = open.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals("열기: demo")).count();
        assertEquals(2, count);
    }

    @Test
    void savingDoesNothingAndNothingIsEverDirty() throws Exception {
        Host h = new Host(List.of("demo"));
        EditorController e = open(h);
        assertFalse(e.save());
        assertEquals(0, h.saves);
        assertFalse(e.isDirty());
        click(e, 580, 280);                                      // 목록 밖 빈 곳을 눌러도 아무것도 선택되지 않는다
        assertEquals(0, h.switched.size());
        assertFalse(e.isDirty());
        assertTrue(e.status().contains("저장할 화면이 없어요"));
    }
}
