package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 자동 저장(편집 중 임시 보관)과 시작할 때 복구 여부 묻기. */
class EditorAutosaveTest {
    private static final String XML = "<screen><panel width=\"100\" height=\"80\"><label text=\"a\"/></panel></screen>";

    private static final class Host implements EditorHost {
        String auto;
        int clears;
        int exits;

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public void autosave(String text) { auto = text; }

        @Override public String readAutosave() { return auto; }

        @Override public void clearAutosave() {
            auto = null;
            clears++;
        }

        @Override public void requestExit() { exits++; }
    }

    private static final long[] now = {10_000_000_000L};

    private static EditorController editor(Host h) throws Exception {
        EditorController e = new EditorController(h, new FixedMetrics(), XML, 400, 240);
        now[0] = 10_000_000_000L;
        e.setClockForTests(() -> now[0]);
        return e;
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static boolean hasText(DrawList l, String t) {
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().contains(t));
    }

    @Test
    void dirtyEditsAreAutosavedAfterAWhileAndClearedOnSave() throws Exception {
        Host h = new Host();
        EditorController e = editor(h);
        paint(e);
        assertNull(h.auto, "편집 전에는 저장하지 않는다");
        e.add("label");
        paint(e);
        assertEquals(e.text(), h.auto, "첫 저장은 바로");
        e.add("label");
        now[0] += 1_000_000_000L;
        paint(e);
        assertNotEquals(e.text(), h.auto, "5초가 지나기 전에는 다시 쓰지 않는다");
        now[0] += 5_000_000_000L;
        paint(e);
        assertEquals(e.text(), h.auto);
        assertTrue(e.save());
        assertNull(h.auto, "저장하면 임시본을 지운다");
    }

    @Test
    void undoBackToCleanClearsAutosave() throws Exception {
        Host h = new Host();
        EditorController e = editor(h);
        e.add("label");
        paint(e);
        assertNotNull(h.auto);
        e.undo();
        paint(e);
        assertNull(h.auto);
    }

    @Test
    void leftoverAutosaveAsksAndRecoverApplies() throws Exception {
        Host h = new Host();
        String leftover = XML.replace("text=\"a\"", "text=\"recovered\"");
        h.auto = leftover;
        EditorController e = editor(h);
        assertTrue(hasText(paint(e), "복구할까요"));
        paint(e);
        assertEquals(leftover, h.auto, "묻는 동안 임시본을 덮어쓰지 않는다");
        assertTrue(e.keyPressed(EditorKey.ENTER, false, false));
        assertEquals(leftover, e.text());
        assertTrue(e.isDirty(), "복구한 내용은 저장해야 적용된다");
        assertFalse(hasText(paint(e), "복구할까요"));
    }

    @Test
    void escLeavesFileAndDiscardDeletesIt() throws Exception {
        Host h = new Host();
        h.auto = XML.replace("text=\"a\"", "text=\"x\"");
        EditorController e = editor(h);
        paint(e);
        e.keyPressed(EditorKey.ESCAPE, false, false);
        assertEquals(XML, e.text());
        assertNotNull(h.auto, "나중에 = 임시본 유지");
        assertFalse(hasText(paint(e), "복구할까요"));
    }

    @Test
    void sameAsCurrentTextIsNotOffered() throws Exception {
        Host h = new Host();
        h.auto = XML;
        EditorController e = editor(h);
        assertFalse(hasText(paint(e), "복구할까요"));
    }

    @Test
    void cleanExitAndDiscardOnExitRemoveAutosave() throws Exception {
        Host h = new Host();
        EditorController e = editor(h);
        e.requestExit();
        assertEquals(1, h.exits);
        assertTrue(h.clears > 0);

        Host h2 = new Host();
        EditorController e2 = editor(h2);
        e2.add("label");
        paint(e2);
        assertNotNull(h2.auto);
        e2.requestExit();                                    // 변경이 있으니 확인 창
        assertEquals(0, h2.exits);
        assertNotNull(h2.auto);
    }
}
