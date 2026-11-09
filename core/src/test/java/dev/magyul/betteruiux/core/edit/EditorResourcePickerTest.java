package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 이미지/영상 src 칸의 파일 선택 목록. */
class EditorResourcePickerTest {
    private static class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public List<String> suggestions(String key) {
            return key.equals("src") ? List.of("file:a.png", "file:spin.gif", "file:clip.mp4") : List.of();
        }
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

    private void pickFor(String tag) throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(),
                "<screen><" + tag + " id=\"m\" src=\"file:a.png\" width=\"20\" height=\"20\"/></screen>", 600, 300);
        e.select(1);
        paint(e);
        DrawCommand.DrawText mark = find(paint(e), "▦");
        assertTrue(mark != null, tag + " 의 src 칸에 선택 표시가 있어야 한다");
        click(e, mark.x() + 1, mark.y() + 1);

        DrawList open = paint(e);
        assertTrue(find(open, "file:spin.gif") != null, "목록이 보여야 한다");
        DrawCommand.DrawText item = find(open, "file:clip.mp4");
        click(e, item.x() + 2, item.y() + 2);
        assertTrue(e.text().contains("src=\"file:clip.mp4\""), e.text());
        assertFalse(find(paint(e), "file:spin.gif") != null, "고르면 닫힌다");
    }

    @Test
    void emptyListExplainsWhyAndWhereToPutFiles() throws Exception {
        EditorController e = new EditorController(new Host() {
            @Override public List<String> suggestions(String key) { return List.of(); }
        }, new FixedMetrics(), "<screen><image id=\"m\" src=\"file:a.png\" width=\"20\" height=\"20\"/></screen>", 600, 300);
        e.select(1);
        paint(e);
        DrawCommand.DrawText mark = find(paint(e), "▦");
        click(e, mark.x() + 1, mark.y() + 1);
        DrawList open = paint(e);
        assertTrue(find(open, "파일이 없어요.") != null, "빈 목록 안내");
        assertTrue(find(open, "config/better-uiux/") != null);
    }

    @Test
    void imageSrcPickerSetsTheFile() throws Exception { pickFor("image"); }

    @Test
    void videoSrcPickerSetsTheFile() throws Exception { pickFor("video"); }
}
