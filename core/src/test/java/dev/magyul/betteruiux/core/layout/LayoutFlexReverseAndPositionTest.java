package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import org.junit.jupiter.api.Test;

class LayoutFlexReverseAndPositionTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(400, 200);
        return d;
    }

    @Test
    void rowReverseReversesOrderAndPacksToTheEnd() throws Exception {
        UiDocument d = doc("<screen><row id='r' width='100' height='20' flex-direction='row-reverse'>"
                + "<panel id='a' width='10' height='5'/><panel id='b' width='10' height='5'/><panel id='c' width='10' height='5'/></row></screen>");
        assertTrue(d.findById("c").x < d.findById("b").x && d.findById("b").x < d.findById("a").x, "순서가 뒤집힌다");
        assertEquals(100, d.findById("a").x + d.findById("a").w, "끝(오른쪽)에 붙는다");
    }

    @Test
    void columnReverseStacksFromBottom() throws Exception {
        UiDocument d = doc("<screen><column id='r' width='20' height='100' flex-direction='column-reverse'>"
                + "<panel id='a' width='10' height='10'/><panel id='b' width='10' height='10'/></column></screen>");
        assertTrue(d.findById("b").y < d.findById("a").y);
        assertEquals(100, d.findById("a").y + d.findById("a").h - d.findById("r").y + 0);
    }

    @Test
    void alignContentDistributesWrappedLines() throws Exception {
        String items = "<panel id='a' width='40' height='10'/><panel id='b' width='40' height='10'/><panel id='c' width='40' height='10'/>";
        UiDocument start = doc("<screen><row id='r' width='50' height='100' flex-wrap='wrap'>" + items + "</row></screen>");
        UiDocument between = doc("<screen><row id='r' width='50' height='100' flex-wrap='wrap' align-content='space-between'>" + items + "</row></screen>");
        UiDocument center = doc("<screen><row id='r' width='50' height='100' flex-wrap='wrap' align-content='center'>" + items + "</row></screen>");
        assertEquals(0, start.findById("a").y);
        assertEquals(10, start.findById("b").y);
        assertEquals(0, between.findById("a").y);
        assertEquals(90, between.findById("c").y, "마지막 줄이 맨 아래에");
        assertEquals(35, center.findById("a").y, "가운데 정렬: (100-30)/2");
    }

    @Test
    void absoluteUsesNearestPositionedAncestor() throws Exception {
        UiDocument d = doc("<screen><style>#outer { position: relative; } #inner { padding: 0; } #abs { position: absolute; left: 5; top: 7; width: 10; height: 10; }</style>"
                + "<column anchor='top-left' x='50' y='40'><column id='outer' width='100' height='60'>"
                + "<column id='inner' width='30' height='20'><panel id='abs'/></column></column></column></screen>");
        LayoutBox outer = d.findById("outer"), abs = d.findById("abs");
        assertEquals(outer.x + 5, abs.x, "inner 는 positioned 가 아니므로 outer 기준");
        assertEquals(outer.y + 7, abs.y);
    }

    @Test
    void absoluteFallsBackToParentWhenNoPositionedAncestor() throws Exception {
        UiDocument d = doc("<screen><style>#abs { position: absolute; left: 5; top: 7; width: 10; height: 10; }</style>"
                + "<column anchor='top-left' x='50' y='40'><column id='p' width='30' height='20'><panel id='abs'/></column></column></screen>");
        assertEquals(d.findById("p").x + 5, d.findById("abs").x);
    }

    @Test
    void fixedIsRelativeToTheScreen() throws Exception {
        UiDocument d = doc("<screen><style>#f { position: fixed; right: 10; bottom: 10; width: 20; height: 8; }</style>"
                + "<column anchor='top-left' x='50' y='40'><column id='p' width='30' height='20'><panel id='f'/></column></column></screen>");
        LayoutBox f = d.findById("f");
        assertEquals(400 - 10 - 20, f.x);
        assertEquals(200 - 10 - 8, f.y);
    }
}
