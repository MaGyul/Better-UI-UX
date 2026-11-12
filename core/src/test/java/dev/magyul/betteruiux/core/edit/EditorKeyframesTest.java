package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code @keyframes} 편집: 위치를 읽고, 지점의 값을 고치고, 지점을 더한다(문서 안 &lt;style&gt; 과 외부 파일 모두). */
class EditorKeyframesTest {
    private static final int W = 700, H = 700;
    private static final String XML = """
            <screen>
              <style>
                .p { animation: pulse 1s infinite; }
                @keyframes pulse {
                  from { opacity: 0; }
                  to { opacity: 1; }
                }
              </style>
              <panel id="a" class="p" width="80" height="40"/>
            </screen>
            """;

    private static XmlSource src(String t) { return XmlSource.parse(t); }

    @Test
    void readsKeyframesWithStopsAndKeepsThemOutOfTheRuleList() {
        List<StyleText.Kf> kfs = StyleText.keyframes(src(XML));
        assertEquals(1, kfs.size());
        assertEquals("pulse", kfs.get(0).name());
        assertEquals(List.of("from", "to"), kfs.get(0).stops().stream().map(StyleText.Rule::selector).toList());
        assertEquals(1, StyleText.rules(src(XML)).size(), "@keyframes 는 일반 규칙이 아니다");
        assertEquals("opacity", kfs.get(0).stops().get(1).decls().get(0).prop());
    }

    @Test
    void editsAStopValueKeepingTheRest() {
        String out = StyleText.apply(src(XML), "kf:~:0:1:opacity", "0.5");
        assertEquals(XML.replace("to { opacity: 1; }", "to { opacity: 0.5; }"), out);
    }

    @Test
    void addsADeclarationToAStopAndNewStops() {
        String more = StyleText.apply(src(XML), "kf+:~:0:0", "color: #FFFFFF");
        assertTrue(more.contains("from { opacity: 0; color: #FFFFFF; }"), more);

        String stop = StyleText.apply(src(XML), "kf*:~:0", "50% opacity: 0.4");
        assertTrue(stop.contains("    to { opacity: 1; }\n    50% { opacity: 0.4 }\n  }") || stop.contains("50% { opacity: 0.4 }"), stop);
        assertEquals(List.of("from", "to", "50%"), StyleText.keyframes(src(stop)).get(0).stops().stream().map(StyleText.Rule::selector).toList());

        String existing = StyleText.apply(src(XML), "kf*:~:0", "to color: #FF0000");     // 있는 지점이면 거기에 덧붙인다
        assertEquals(2, StyleText.keyframes(src(existing)).get(0).stops().get(1).decls().size());
        assertEquals(2, StyleText.keyframes(src(existing)).get(0).stops().size());

        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src(XML), "kf*:~:0", "엉터리"));
        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src(XML), "kf*:~:0", "abc opacity: 1"));
    }

    @Test
    void externalSheetKeyframesGoThroughTheFileKeys() {
        String css = "@keyframes spin { from { opacity: 0 } to { opacity: 1 } }\n";
        assertEquals("t.uicss", StyleText.externalHref("kf:t.uicss:0:1:opacity"));
        assertEquals(null, StyleText.externalHref("kf:~:0:1:opacity"));
        assertEquals("@keyframes spin { from { opacity: 0 } to { opacity: 0.2 } }\n", StyleText.applyExternal(css, "kf:t.uicss:0:1:opacity", "0.2"));
        String added = StyleText.applyExternal(css, "kf*:t.uicss:0", "50% opacity: 0.5");
        assertEquals(3, StyleText.cssKeyframes(added).get(0).stops().size());
    }

    // ---- 속성 패널 ----

    private static final class Host implements EditorHost {
        final Map<String, String> files = new HashMap<>();

        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.uiml"; }

        @Override public String readFile(String name) { return files.get(name); }

        @Override public void writeFile(String name, String text) { files.put(name, text); }
    }

    private static DrawList paint(EditorController e) {
        DrawList l = new DrawList();
        e.paint(l);
        return l;
    }

    private static DrawCommand.DrawText find(DrawList l, String text) {
        return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                .map(c -> (DrawCommand.DrawText) c).reduce((x, y) -> y).orElse(null);
    }

    private static void click(EditorController e, int x, int y) {
        e.mouseMoved(x, y);
        e.mousePressed(x, y, 0);
        e.mouseReleased(x, y, 0);
    }

    private static EditorController selected(Host host, String xml) throws Exception {
        EditorController e = new EditorController(host, new FixedMetrics(), xml, W, H);
        int idx = e.document().layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow().node.sourceIndex();
        e.select(idx);
        paint(e);
        return e;
    }

    @Test
    void panelShowsTheElementsKeyframesAndEditsAStop() throws Exception {
        EditorController e = selected(new Host(), XML);
        DrawList l = paint(e);
        assertTrue(find(l, "- 키프레임: pulse") != null, "키프레임 구역");
        assertTrue(find(l, "- from") != null && find(l, "- to") != null, "지점 머리글");
        DrawCommand.DrawText prop = find(l, "opacity");                       // 마지막(= to 지점) 의 선언
        click(e, W - EditorController.RIGHT_W + 60 + 5, prop.y() + 2);
        assertTrue(e.isEditingField());
        e.charTyped('0');
        e.charTyped('.');
        e.charTyped('5');
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("to { opacity: 0.5; }"), e.text());
        assertTrue(e.isDirty());
        e.undo();
        assertEquals(XML, e.text());
    }

    @Test
    void newStopFromThePanel() throws Exception {
        EditorController e = selected(new Host(), XML);
        DrawCommand.DrawText add = find(paint(e), "+ 새 지점");
        assertTrue(add != null);
        click(e, W - EditorController.RIGHT_W + 60 + 5, add.y() + 2);
        for (char c : "50% opacity: 0.4".toCharArray()) e.charTyped(c);
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(e.text().contains("50% { opacity: 0.4 }"), e.text());
    }

    @Test
    void keyframesInAnExternalSheetAreEditedInTheFile() throws Exception {
        Host host = new Host();
        host.files.put("k.uicss", "@keyframes spin {\n  from { opacity: 0; }\n  to { opacity: 1; }\n}\n");
        String xml = "<screen><style>.p { animation: spin 2s infinite; }</style><link rel=\"style\" href=\"k.uicss\"/><panel id=\"a\" class=\"p\" width=\"80\" height=\"40\"/></screen>";
        EditorController e = selected(host, xml);
        DrawList l = paint(e);
        assertTrue(find(l, "- 키프레임: spin (k.uicss)") != null, "외부 키프레임 구역");
        DrawCommand.DrawText prop = find(l, "opacity");
        click(e, W - EditorController.RIGHT_W + 60 + 5, prop.y() + 2);
        e.charTyped('0');
        e.charTyped('.');
        e.charTyped('7');
        e.keyPressed(EditorKey.ENTER, false, false);
        assertTrue(host.files.get("k.uicss").contains("to { opacity: 0.7; }"), host.files.get("k.uicss"));
        assertEquals(xml, e.text());
        assertFalse(e.isDirty());
        e.undo();
        assertTrue(host.files.get("k.uicss").contains("to { opacity: 1; }"));
    }
}
