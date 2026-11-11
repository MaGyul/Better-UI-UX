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

/** 팔레트의 "기타" 탭: video, stack 을 추가할 수 있다. */
class EditorNewTagsTest {
    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.uiml"; }
    }

    private static boolean paints(EditorController e, String text) {
        DrawList l = new DrawList();
        e.paint(l);
        return l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text));
    }

    @Test
    void videoAndStackSnippetsParse() {
        for (String tag : new String[] {"video", "stack"}) {
            String xml = "<screen>" + EditorOps.snippet(tag) + "</screen>";
            XmlSource.parse(xml);                                           // 올바른 XML
            assertTrue(xml.contains("<" + tag + " "), xml);
        }
    }

    @Test
    void thePaletteHasAnOtherTabWithBothTags() throws Exception {
        assertEquals(21, EditorController.PALETTE_TAGS.size());
        assertEquals(4, EditorController.PALETTE_TABS.length);
        assertEquals(19, EditorController.PALETTE_TAGS.indexOf("video"));
        assertEquals(20, EditorController.PALETTE_TAGS.indexOf("stack"));
        EditorController e = new EditorController(new Host(), new FixedMetrics(), "<screen/>", 700, 300);
        assertTrue(paints(e, "기타"), "탭 이름이 보여야 한다");
    }

    @Test
    void addingThemPutsThemInTheDocument() throws Exception {
        EditorController e = new EditorController(new Host(), new FixedMetrics(), "<screen/>", 700, 300);
        e.add("stack");
        assertTrue(e.text().contains("<stack "), e.text());
        e.select(0);
        e.add("video");
        assertTrue(e.text().contains("<video src=\"file:video.mp4\""), e.text());
        assertTrue(e.isDirty());
    }
}
