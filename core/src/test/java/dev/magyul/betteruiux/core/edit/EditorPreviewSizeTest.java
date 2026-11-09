package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** 해상도(GUI 크기) 미리보기: 창과 다른 크기로 배치해 보고, 편집 중에도 그 크기를 유지한다. */
class EditorPreviewSizeTest {
    private static final String XML = "<screen><panel id=\"p\" anchor=\"bottom-right\" width=\"50\" height=\"30\"/></screen>";

    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }
    }

    private static int[] panelPos(EditorController e) {
        var b = e.document().query("#p");
        return new int[] {b.x(), b.y()};
    }

    @Test
    void layoutFollowsThePreviewSizeAndSurvivesEdits() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), XML, 400, 240);
        int[] normal = panelPos(e);
        assertEquals(350, normal[0]);
        assertEquals(210, normal[1]);

        e.setPreviewSize(200, 120);
        int[] small = panelPos(e);
        assertEquals(150, small[0]);
        assertEquals(90, small[1]);

        e.add("label");                                          // 편집으로 다시 배치돼도 미리보기 크기를 유지
        assertEquals(150, panelPos(e)[0]);

        e.setView(500, 300);                                     // 창 크기가 바뀌어도 미리보기 크기 유지
        assertEquals(150, panelPos(e)[0]);

        e.setPreviewSize(0, 0);
        assertEquals(450, panelPos(e)[0]);
    }

    @Test
    void frameAndSizeLabelAreDrawnWhilePreviewing() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), XML, 400, 240);
        e.setPreviewSize(200, 120);
        DrawList l = new DrawList();
        e.paint(l);
        assertTrue(l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().equals("200x120")));
    }
}
