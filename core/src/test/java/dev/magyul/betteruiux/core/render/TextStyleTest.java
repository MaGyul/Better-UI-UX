package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FixedMetrics: 글자당 6px, 줄 높이 9px. */
class TextStyleTest {
    private record Shot(UiDocument doc, DrawList list) {
        List<DrawCommand.DrawText> texts() {
            return list.commands().stream().filter(c -> c instanceof DrawCommand.DrawText)
                    .map(c -> (DrawCommand.DrawText) c).toList();
        }
    }

    private static Shot shot(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(200, 120);
        DrawList l = new DrawList();
        d.paint(l, -1, -1);
        return new Shot(d, l);
    }

    private static Shot label(String attrs, String text) throws Exception {
        return shot("<screen><label id='l' x='10' y='10' " + attrs + ">" + text + "</label></screen>");
    }

    @Test
    void defaultsKeepPlainMetrics() throws Exception {
        Shot s = label("", "abc");
        assertEquals(18, s.doc().findById("l").w);
        assertEquals(9, s.doc().findById("l").h);
        assertEquals(new DrawCommand.DrawText("abc", 10, 10, 0xFFFFFFFF, true), s.texts().get(0));
    }

    @Test
    void fontSizeScalesMetricsAndDrawing() throws Exception {
        Shot s = label("style='font-size: 16px'", "abc");
        assertEquals(36, s.doc().findById("l").w);
        assertEquals(18, s.doc().findById("l").h);
        assertTrue(s.list().commands().contains(new DrawCommand.PushTransform(0, 0, 2f, 0, 10, 10)));
    }

    @Test
    void fontSizeEmInheritsFromTheParent() throws Exception {
        Shot s = shot("""
            <screen><panel id='p' style='font-size: 16px'>
              <label id='a' text='x' style='font-size: 0.5em'/><label id='b' text='x' style='font-size: 150%'/>
              <label id='c' text='x'/><label id='d' text='x' style='font-size: 2rem'/>
            </panel></screen>""");
        assertEquals("8", s.doc().findById("a").style.get("font-size"));
        assertEquals("24", s.doc().findById("b").style.get("font-size"));
        assertEquals("16", s.doc().findById("c").style.get("font-size"));        // 상속
        assertEquals("16", s.doc().findById("d").style.get("font-size"));        // rem = 8px
    }

    @Test
    void lineHeightAndLetterSpacing() throws Exception {
        assertEquals(16, label("style='line-height: 2'", "a").doc().findById("l").h);
        assertEquals(20, label("style='line-height: 20px'", "a").doc().findById("l").h);
        assertEquals(36, label("style='line-height: 1.5em'", "a\nb\nc").doc().findById("l").h);   // 12px * 3줄
        Shot s = label("style='letter-spacing: 2px'", "abc");
        assertEquals(24, s.doc().findById("l").w);                              // (6+2) * 3
        assertEquals(3, s.texts().size());                                      // 글자마다 따로 그린다
        assertEquals(10, s.texts().get(0).x());
        assertEquals(18, s.texts().get(1).x());
    }

    @Test
    void boldIsDrawnTwiceAndOnePixelWider() throws Exception {
        Shot s = label("style='font-weight: bold'", "abc");
        assertEquals(19, s.doc().findById("l").w);
        assertEquals(2, s.texts().size());
        assertEquals(10, s.texts().get(0).x());
        assertEquals(11, s.texts().get(1).x());
        assertEquals(2, label("style='font-weight: 700'", "a").texts().size());
        assertEquals(1, label("style='font-weight: 400'", "a").texts().size());
    }

    @Test
    void normalWhiteSpaceWrapsToTheBoxWidth() throws Exception {
        Shot s = label("width='36' style='white-space: normal'", "ab cd   ef\ngh");
        assertEquals(List.of("ab cd", "ef gh"), s.texts().stream().map(DrawCommand.DrawText::text).toList());
        assertEquals(18, s.doc().findById("l").h);                              // 줄바꿈 결과가 높이에 반영된다
        assertEquals(19, s.texts().get(1).y());
    }

    @Test
    void longWordsBreakAndPreWrapKeepsNewlines() throws Exception {
        Shot s = label("width='24' style='white-space: normal'", "abcdefghij");
        assertEquals(List.of("abcd", "efgh", "ij"), s.texts().stream().map(DrawCommand.DrawText::text).toList());
        Shot p = label("width='60' style='white-space: pre-wrap'", "a\n\nb");
        assertEquals(2, p.texts().size());                                      // 빈 줄은 그릴 글자가 없다
        assertEquals(27, p.doc().findById("l").h);                              // 그래도 3줄 높이
    }

    @Test
    void nowrapAndPreLineCollapseWhitespace() throws Exception {
        assertEquals(List.of("a b c"),
                label("style='white-space: nowrap'", "a\n  b   c").texts().stream().map(DrawCommand.DrawText::text).toList());
        assertEquals(List.of("a b", "c"),
                label("style='white-space: pre-line'", "a   b\nc").texts().stream().map(DrawCommand.DrawText::text).toList());
        assertEquals(List.of("a   b", "c"),
                label("", "a   b\nc").texts().stream().map(DrawCommand.DrawText::text).toList());      // 기본 pre
    }

    @Test
    void wrappedLabelInsideAColumn() throws Exception {
        Shot s = shot("""
            <screen><column x='0' y='0' width='36' height='100'>
              <label id='l' style='white-space: normal'>ab cd ef gh</label>
              <panel id='after' width='5' height='5'/>
            </column></screen>""");
        assertEquals(18, s.doc().findById("l").h);                              // "ab cd" / "ef gh"
        assertEquals(18, s.doc().findById("after").y);
    }

    @Test
    void textTransform() throws Exception {
        assertEquals("ABC DEF", label("style='text-transform: uppercase'", "abc dEF").texts().get(0).text());
        assertEquals("abc def", label("style='text-transform: lowercase'", "ABC Def").texts().get(0).text());
        assertEquals("Hello World", label("style='text-transform: capitalize'", "hello world").texts().get(0).text());
    }

    @Test
    void ellipsisCutsOverflowingText() throws Exception {
        Shot s = label("width='30' style='text-overflow: ellipsis'", "abcdefgh");
        assertEquals("ab...", s.texts().get(0).text());
        assertEquals("abc", label("width='30' style='text-overflow: ellipsis'", "abc").texts().get(0).text());
    }

    @Test
    void decorationsVerticalAlignAndShadow() throws Exception {
        Shot u = label("style='text-decoration: underline; color: #ff0000'", "abc");
        assertTrue(u.list().commands().contains(new DrawCommand.FillRect(10, 18, 18, 1, 0xFFFF0000)));
        Shot t = label("style='text-decoration: line-through; color: #ff0000'", "abc");
        assertTrue(t.list().commands().contains(new DrawCommand.FillRect(10, 14, 18, 1, 0xFFFF0000)));
        assertEquals(31, label("height='30' style='vertical-align: bottom'", "a").texts().get(0).y());
        assertEquals(20, label("height='30' style='vertical-align: middle'", "a").texts().get(0).y());
        assertEquals(false, label("style='text-shadow: none'", "a").texts().get(0).shadow());
        assertEquals(true, label("style='text-shadow: 1px 1px red'", "a").texts().get(0).shadow());
    }

    @Test
    void textAlignKeywords() throws Exception {
        assertEquals(10 + 100 - 18, label("width='100' style='text-align: end'", "abc").texts().get(0).x());
        assertEquals(10, label("width='100' style='text-align: start'", "abc").texts().get(0).x());
    }
}
