package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

/** :hover / :active / :focus 규칙의 width·margin·gap 등 레이아웃 속성도 배치에 반영된다. */
class LayoutStateRulesTest {
    private long now;

    private UiDocument doc(String body) throws Exception {
        UiDocument d = UiDocument.parse("<screen>" + body + "</screen>", new FixedMetrics());
        d.setClock(() -> now);
        d.setAnimationsEnabled(false);
        d.layout(300, 200);
        return d;
    }

    private void paint(UiDocument d, int mx, int my) { d.paint(new DrawList(), mx, my); }

    @Test
    void hoverWidthChangesLayoutAndSiblingsMove() throws Exception {
        UiDocument d = doc("<style>#a { width: 40; height: 10 } #a:hover { width: 80 }</style>"
                + "<row><panel id='a'/><panel id='b' width='5' height='5'/></row>");
        paint(d, -1, -1);
        assertEquals(40, d.findById("a").w);
        assertEquals(40, d.findById("b").x);
        paint(d, 5, 5);
        assertEquals(80, d.findById("a").w, "호버하면 넓어진다");
        assertEquals(80, d.findById("b").x, "옆 요소가 밀린다");
        paint(d, 200, 150);
        assertEquals(40, d.findById("a").w, "벗어나면 원래대로");
        assertEquals(40, d.findById("b").x);
    }

    @Test
    void hoverAffectsDescendantsLayout() throws Exception {
        UiDocument d = doc("<style>.card { padding: 0 } .card:hover { padding: 6 } .card > panel { width: 10; height: 10 }</style>"
                + "<column class='card' id='c'><panel id='k'/></column>");
        paint(d, -1, -1);
        assertEquals(0, d.findById("k").x);
        paint(d, 2, 2);
        assertEquals(6, d.findById("k").x, "padding 이 늘어 자식이 안쪽으로 밀린다");
    }

    @Test
    void importantAndSpecificityOrderStillApply() throws Exception {
        UiDocument d = doc("<style>#a { width: 40; height: 10 } #a:hover { width: 60 } #a.x:hover { width: 90 }</style>"
                + "<panel id='a' class='x'/>");
        paint(d, 2, 2);
        assertEquals(90, d.findById("a").w, "특이도가 높은 규칙이 이긴다");
    }

    @Test
    void paintOnlyStateRulesDoNotRelayout() throws Exception {
        UiDocument d = doc("<style>#a { width: 40; height: 10; background: #FF0000 } #a:hover { background: #00FF00 }</style><panel id='a'/>");
        paint(d, -1, -1);
        var before = d.layout();
        paint(d, 2, 2);
        assertEquals(before, d.layout(), "그리기 속성만 바뀌면 레이아웃 트리를 새로 만들지 않는다");
    }

    @Test
    void forcedPreviewAlsoRelayouts() throws Exception {
        UiDocument d = doc("<style>#a { width: 40; height: 10 } #a:hover { width: 70 }</style><panel id='a'/>");
        paint(d, -1, -1);
        d.forcePseudo(d.findById("a").node.sourceIndex(), true, false);
        paint(d, -1, -1);
        assertEquals(70, d.findById("a").w);
    }
}
