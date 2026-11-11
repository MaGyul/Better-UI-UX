package dev.magyul.betteruiux.core;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.EditorController;
import dev.magyul.betteruiux.core.edit.EditorHost;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 화면 결과(드로우 리스트) 스냅샷: 레이아웃/그리기가 의도치 않게 바뀌면 여기서 걸린다. */
class GoldenSnapshotsTest {
    private static String demo(String name) throws IOException {
        return Files.readString(Path.of("../backend-mc26/src/main/resources/assets/better_uiux/ui/" + name + ".xml"));
    }

    private static DrawList paintDoc(String xml, int w, int h) throws UiParseException {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(w, h);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return l;
    }

    @Test
    void demoScreen() throws Exception {
        Golden.assertMatches("demo-480x270", paintDoc(demo("demo"), 480, 270));
    }

    @Test
    void widgetsScreen() throws Exception {
        Golden.assertMatches("widgets-480x270", paintDoc(demo("widgets"), 480, 270));
    }

    @Test
    void widgetsScreenOnASmallWindow() throws Exception {
        Golden.assertMatches("widgets-320x200", paintDoc(demo("widgets"), 320, 200));
    }

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException {
            UiDocument d = UiDocument.parse(text, new FixedMetrics());
            d.setClock(() -> 0);
            return d;
        }

        @Override public void save(String text) { }

        @Override public String title() { return "golden.xml"; }
    }

    private static final String EDIT_XML = """
        <screen>
          <panel id="card" anchor="center" width="120" height="80" background="#223344FF">
            <column gap="2" width="fill" height="fill">
              <label text="Title"/>
              <button id="ok" text="OK" onClick="close"/>
              <slider id="vol" width="fill" value="30"/>
            </column>
          </panel>
        </screen>""";

    private static DrawList editorFrame(int w, int h, int select) throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), EDIT_XML, w, h);
        long[] now = {0};
        e.setClockForTests(() -> now[0]);
        if (select >= 0) e.select(select);
        DrawList l = new DrawList();
        e.paintDocument(l);
        e.paint(l);
        return l;
    }

    @Test
    void editorWithNothingSelected() throws Exception {
        Golden.assertMatches("editor-empty-700x300", editorFrame(700, 300, -1));
    }

    @Test
    void editorWithAButtonSelected() throws Exception {
        Golden.assertMatches("editor-button-700x300", editorFrame(700, 300, 3));
    }

    @Test
    void editorToolbarOverflowsOnANarrowWindow() throws Exception {
        Golden.assertMatches("editor-narrow-260x200", editorFrame(260, 200, 1));
    }
}
